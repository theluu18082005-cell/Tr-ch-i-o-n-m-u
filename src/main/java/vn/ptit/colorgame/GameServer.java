package vn.ptit.colorgame;

import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** I/O từng kết nối chạy riêng; mọi thay đổi trận đấu được tuần tự hóa trong event loop. */
public final class GameServer implements AutoCloseable {
    private final Store store;
    private final ServerSocket listener;
    private final FileChannel lockChannel;
    private final FileLock dataLock;
    private final ScheduledExecutorService loop = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService passwordWorkers = Executors.newFixedThreadPool(2);
    private final Set<Session> sessions = new HashSet<>();
    private final Map<String, Session> online = new LinkedHashMap<>();
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<String, Invitation> invitations = new HashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final CountDownLatch terminated = new CountDownLatch(1);
    private final AtomicBoolean closing = new AtomicBoolean();
    private final int turnMillis, heartbeatMillis;
    private volatile boolean fatal;

    private static final class Room {
        final Store.Match match;
        final Set<String> replay = new HashSet<>();
        long deadline;
        int lastSecond = -1;
        Room(Store.Match match) { this.match = match; }
    }

    private record Invitation(String id, Session from, Session to, long deadline) {}

    public GameServer(int port, Path directory) throws IOException {
        this(port, directory, Rules.TURN_MILLIS, Rules.HEARTBEAT_MILLIS);
    }

    // Thời gian ngắn chỉ được truyền bởi bộ kiểm thử; bản chạy thật dùng Rules.TURN_MILLIS.
    GameServer(int port, Path directory, int turnMillis, int heartbeatMillis) throws IOException {
        this.turnMillis = turnMillis; this.heartbeatMillis = heartbeatMillis;
        Files.createDirectories(directory);
        lockChannel = FileChannel.open(directory.resolve("server.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (OverlappingFileLockException e) { lockChannel.close(); throw new IOException("Thư mục dữ liệu đang được server khác sử dụng", e); }
        if (acquired == null) { lockChannel.close(); throw new IOException("Thư mục dữ liệu đang được server khác sử dụng"); }
        dataLock = acquired;
        ServerSocket socket = null;
        try {
            store = new Store(directory);
            socket = new ServerSocket(); socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("0.0.0.0", port));
            if (store.abortActive("SERVER_RESTARTED")) store.save();
            listener = socket;
        } catch (IOException | RuntimeException e) {
            if (socket != null) socket.close();
            dataLock.release(); lockChannel.close(); throw e;
        }
    }

    public int port() { return listener.getLocalPort(); }
    public void awaitTermination() throws InterruptedException { terminated.await(); }
    public boolean failed() { return fatal; }

    public void start() {
        log("Server TCP đang nghe 0.0.0.0:" + port());
        log("Dữ liệu: " + store.directory);
        log("Lưu trữ: " + store.storageDescription());
        log("Mỗi lượt " + turnMillis / 1000 + " giây; không giới hạn số lượt. Ctrl+C để dừng server.");
        Thread accept = new Thread(() -> {
            while (!closing.get()) {
                try {
                    Socket socket = listener.accept();
                    socket.setTcpNoDelay(true); socket.setKeepAlive(true); socket.setSoTimeout(heartbeatMillis);
                    Session s = new Session(socket);
                    event(() -> {
                        if (sessions.size() >= 64) { s.closeSocket(); return; }
                        sessions.add(s); s.start();
                        s.send("HELLO", "1", Integer.toString(turnMillis), Integer.toString(heartbeatMillis));
                    });
                } catch (IOException e) { if (!closing.get()) log("Lỗi accept: " + e.getMessage()); }
            }
        }, "tcp-accept");
        accept.setDaemon(true); accept.start();
        loop.scheduleAtFixedRate(() -> {
            if (!closing.get()) try { tick(); } catch (Exception e) { fail(e); }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }

    private void event(Action action) {
        if (closing.get()) return;
        try { loop.execute(() -> {
            if (closing.get()) return;
            try { action.run(); } catch (Exception e) { fail(e); }
        }); } catch (RejectedExecutionException ignored) { }
    }

    private void fail(Exception e) {
        if (fatal || closing.get()) return;
        fatal = true;
        log("SERVER DỪNG DO LỖI: " + e.getMessage());
        e.printStackTrace(System.err);
        // Không báo thắng / cập nhật thành công nếu dữ liệu chưa ghi được xuống đĩa.
        new Thread(this::close, "fatal-shutdown").start();
    }

    private final class Session {
        final Socket socket;
        final BlockingQueue<String> output = new ArrayBlockingQueue<>(4096);
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicInteger pending = new AtomicInteger();
        volatile long lastSeen = System.nanoTime();
        final long created = System.nanoTime();
        String user, roomId, invitationId;
        boolean authenticating;
        long nextAuth;

        Session(Socket socket) { this.socket = socket; }

        void start() {
            Thread writer = new Thread(() -> {
                try (OutputStream out = new BufferedOutputStream(socket.getOutputStream())) {
                    while (!closed.get()) {
                        String message = output.poll(1, TimeUnit.SECONDS);
                        if (message != null) { out.write(message.getBytes(StandardCharsets.US_ASCII)); out.flush(); }
                    }
                } catch (IOException | InterruptedException ignored) { }
                finally { closeSocket(); }
            }, "tcp-write");
            Thread reader = new Thread(() -> {
                try (InputStream in = new BufferedInputStream(socket.getInputStream())) {
                    String[] message;
                    while (!closed.get() && (message = Wire.read(in)) != null) {
                        lastSeen = System.nanoTime();
                        if (pending.incrementAndGet() > 64) break;
                        String[] command = message;
                        event(() -> {
                            pending.decrementAndGet();
                            if (sessions.contains(this) && !closed.get()) handle(this, command);
                        });
                    }
                } catch (IOException ignored) { }
                finally { closeSocket(); }
            }, "tcp-read");
            writer.setDaemon(true); reader.setDaemon(true); writer.start(); reader.start();
        }

        void send(String command, String... fields) {
            if (!closed.get() && !output.offer(Wire.encode(command, fields))) closeSocket();
        }

        void closeSocket() {
            if (closed.compareAndSet(false, true)) {
                try { socket.close(); } catch (IOException ignored) { }
                event(() -> disconnected(this));
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static void fields(String[] message, int length) {
        require(message.length == length, "Thông điệp thiếu hoặc thừa tham số");
    }

    private void handle(Session s, String[] c) throws IOException {
        try {
            if (c[0].equals("PING")) { fields(c, 1); s.send("PONG"); return; }
            if (c[0].equals("LOGIN") || c[0].equals("REGISTER")) { authenticate(s, c); return; }
            require(s.user != null, "Bạn cần đăng nhập trước");
            switch (c[0]) {
                case "LIST" -> { fields(c, 1); onlineList(s); }
                case "RANK" -> { fields(c, 1); ranking(s); }
                case "HISTORY" -> { fields(c, 1); history(s); }
                case "CHALLENGE" -> { fields(c, 2); challenge(s, c[1]); }
                case "RESPOND" -> { fields(c, 3); respond(s, c[1], c[2]); }
                case "CANCEL_INVITE" -> {
                    fields(c, 2);
                    Invitation invitation = invitations.get(c[1]);
                    require(invitation != null && (invitation.from == s || invitation.to == s), "Lời mời không còn hiệu lực");
                    closeInvitation(invitation, "Lời mời đã được hủy"); broadcastOnline();
                }
                case "GUESS" -> { fields(c, 4); guess(s, c[1], Integer.parseInt(c[2]), c[3]); }
                case "REMATCH" -> { fields(c, 2); rematch(s, c[1]); }
                case "BACK", "LEAVE" -> {
                    fields(c, 2);
                    if (s.roomId != null) {
                        require(s.roomId.equals(c[1]), "Bạn không ở phòng này");
                        Room r = rooms.get(s.roomId);
                        require(c[0].equals("LEAVE") || !r.match.active(), "Trận đang diễn ra, hãy dùng nút Thoát");
                        leaveRoom(s, "LEFT");
                    }
                    s.send("LOBBY"); broadcastOnline();
                }
                case "LOGOUT" -> { fields(c, 1); s.closeSocket(); }
                default -> throw new IllegalArgumentException("Lệnh không được hỗ trợ");
            }
        } catch (IllegalArgumentException e) {
            s.send("ERROR", c[0], e instanceof NumberFormatException ? "Số lượt không hợp lệ" : e.getMessage());
        }
    }

    private void authenticate(Session s, String[] c) {
        boolean register = c[0].equals("REGISTER");
        fields(c, register ? 4 : 3);
        require(s.user == null && !s.authenticating, "Đã đăng nhập hoặc đang xác thực");
        require(System.nanoTime() >= s.nextAuth, "Vui lòng đợi một giây rồi thử lại");
        String user = c[1].trim().toLowerCase(Locale.ROOT), password = c[2];
        require(user.matches("[a-z0-9_]{3,20}"), "Tài khoản gồm 3–20 chữ không dấu, số hoặc dấu gạch dưới");
        require(password.length() >= 6 && password.length() <= 128, "Mật khẩu cần từ 6 đến 128 ký tự");
        String name = register ? c[3].trim() : user;
        if (name.isEmpty()) name = user;
        require(name.length() <= 40 && name.chars().noneMatch(Character::isISOControl), "Tên hiển thị tối đa 40 ký tự, không chứa ký tự điều khiển");
        Store.Account existing = store.accounts.get(user);
        require(!register || existing == null, "Tài khoản đã tồn tại");
        require(!online.containsKey(user), "Tài khoản này đang đăng nhập ở máy khác");
        s.authenticating = true; s.nextAuth = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        String finalName = name;
        passwordWorkers.execute(() -> {
            try {
                String salt = register ? Passwords.salt() : "AAAAAAAAAAAAAAAAAAAAAA==";
                String hash = register ? Passwords.hash(password, salt, Passwords.ITERATIONS) : "";
                boolean valid = register || (existing != null && Passwords.verify(password, existing));
                if (!register && existing == null) Passwords.hash(password, salt, Passwords.ITERATIONS);
                event(() -> {
                    s.authenticating = false;
                    if (!sessions.contains(s) || s.closed.get()) return;
                    if (!valid || (register && store.accounts.containsKey(user)) || online.containsKey(user)) {
                        s.send("ERROR", c[0], !valid ? "Sai tài khoản hoặc mật khẩu" : "Tài khoản đã tồn tại hoặc đang đăng nhập"); return;
                    }
                    if (register) { store.accounts.put(user, new Store.Account(user, finalName, salt, hash)); store.save(); }
                    s.user = user; online.put(user, s); profile(s, "AUTH"); broadcastOnline();
                    log(user + " đã đăng nhập");
                });
            } catch (Exception e) { event(() -> { throw e; }); }
        });
    }

    private void profile(Session s, String command) {
        Store.Account a = store.accounts.get(s.user);
        s.send(command, a.username, a.displayName, "" + a.points, "" + a.wins, "" + a.played);
    }

    private String status(Session s) {
        if (s.roomId != null) return rooms.get(s.roomId).match.active() ? "Đang chơi" : "Chờ chơi lại";
        return s.invitationId != null ? "Chờ lời mời" : "Đang rỗi";
    }

    private void onlineList(Session receiver) {
        receiver.send("ONLINE_BEGIN");
        for (Session s : online.values()) {
            Store.Account a = store.accounts.get(s.user);
            receiver.send("ONLINE_ROW", a.username, a.displayName, "" + a.points, "" + a.wins, "" + a.played, status(s));
        }
        receiver.send("ONLINE_END");
    }

    private void broadcastOnline() { for (Session s : online.values()) onlineList(s); }

    private void ranking(Session s) {
        s.send("RANK_BEGIN");
        for (Store.Account a : store.ranking()) s.send("RANK_ROW", a.username, a.displayName, "" + a.points, "" + a.wins, "" + a.played);
        s.send("RANK_END");
    }

    private void history(Session s) {
        s.send("HISTORY_BEGIN");
        int sent = 0;
        for (int i = store.matches.size() - 1; i >= 0 && sent < 100; i--) {
            Store.Match m = store.matches.get(i);
            if (!m.player1.equals(s.user) && !m.player2.equals(s.user)) continue;
            s.send("HISTORY_ROW", m.id, "" + m.started, m.player1, m.player2, m.winner, m.reason, "" + m.moves.size());
            sent++;
        }
        s.send("HISTORY_END");
    }

    private void challenge(Session from, String target) {
        Session to = online.get(target);
        require(to != null && to != from && !to.closed.get(), "Đối thủ không trực tuyến hoặc không hợp lệ");
        require(from.roomId == null && from.invitationId == null, "Bạn đang bận");
        require(to.roomId == null && to.invitationId == null, "Đối thủ đang bận");
        String id = UUID.randomUUID().toString();
        Invitation invitation = new Invitation(id, from, to, System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
        invitations.put(id, invitation); from.invitationId = id; to.invitationId = id;
        from.send("INVITE_SENT", id, to.user, "30"); to.send("INVITE", id, from.user, "30");
        broadcastOnline();
    }

    private void respond(Session s, String id, String answer) throws IOException {
        Invitation invitation = invitations.get(id);
        require(invitation != null && invitation.to == s, "Lời mời không còn hiệu lực");
        require(answer.equals("YES") || answer.equals("NO"), "Phản hồi không hợp lệ");
        if (System.nanoTime() >= invitation.deadline) {
            closeInvitation(invitation, "Lời mời đã hết hạn"); broadcastOnline(); return;
        }
        boolean yes = answer.equals("YES") && !invitation.from.closed.get() && !invitation.to.closed.get();
        closeInvitation(invitation, yes ? "Đã chấp nhận lời mời" : "Lời mời bị từ chối");
        if (yes) startRoom(invitation.from, invitation.to);
        else broadcastOnline();
    }

    private void closeInvitation(Invitation invitation, String reason) {
        invitations.remove(invitation.id);
        invitation.from.invitationId = null; invitation.to.invitationId = null;
        invitation.from.send("INVITE_CLOSED", invitation.id, reason);
        invitation.to.send("INVITE_CLOSED", invitation.id, reason);
    }

    private void startRoom(Session a, Session b) throws IOException {
        String first = random.nextBoolean() ? a.user : b.user;
        Store.Match match = new Store.Match(UUID.randomUUID().toString(), a.user, b.user, Rules.randomSequence(random), first);
        Room room = new Room(match);
        rooms.put(match.id, room); store.matches.add(match); a.roomId = match.id; b.roomId = match.id;
        setDeadline(room); store.save();
        both(room, "ROOM", match.id, a.user, b.user);
        profile(a, "PROFILE"); profile(b, "PROFILE"); sendTurn(room, "TURN"); broadcastOnline();
        log("Bắt đầu trận " + match.id + " | " + a.user + " - " + b.user);
    }

    private Room requireRoom(Session s, String id) {
        require(id.equals(s.roomId) && rooms.containsKey(id), "Bạn không ở phòng này");
        return rooms.get(id);
    }

    private void guess(Session s, String id, int turn, String guess) throws IOException {
        Room r = requireRoom(s, id); Store.Match m = r.match;
        require(m.active(), "Trận đấu đã kết thúc");
        require(m.turn == turn && m.current.equals(s.user), "Không đến lượt bạn hoặc lượt này đã kết thúc");
        if (System.nanoTime() >= r.deadline) { timeout(r); throw new IllegalArgumentException("Bạn đã hết thời gian"); }
        require(Rules.validGuess(guess), "Cần dùng đủ 6 màu, mỗi màu đúng một lần");
        Store.Move move = new Store.Move(m.turn, s.user, guess, Rules.score(m.secret, guess), "GUESS");
        m.moves.add(move);
        if (move.correct == 6) {
            applyResult(r, s.user, "SOLVED"); store.save(); sendMove(r, move); sendResult(r);
        } else {
            advance(r); store.save(); sendMove(r, move); sendTurn(r, "TURN");
        }
    }

    private void setDeadline(Room room) {
        room.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(turnMillis);
        room.lastSecond = -1;
    }

    private void advance(Room r) {
        r.match.turn++; r.match.current = r.match.other(r.match.current); setDeadline(r);
    }

    private void timeout(Room r) throws IOException {
        Store.Move move = new Store.Move(r.match.turn, r.match.current, "", -1, "TIMEOUT");
        r.match.moves.add(move); advance(r); store.save(); sendMove(r, move); sendTurn(r, "TURN");
    }

    private void sendTurn(Room r, String type) {
        long remaining = Math.max(0, TimeUnit.NANOSECONDS.toMillis(r.deadline - System.nanoTime()));
        both(r, type, r.match.id, "" + r.match.turn, r.match.current, "" + remaining);
    }

    private void sendMove(Room r, Store.Move move) {
        both(r, "MOVE", r.match.id, "" + move.number, move.player, move.guess, "" + move.correct, move.type);
    }

    private void applyResult(Room r, String winner, String reason) {
        Store.Match m = r.match;
        if (!m.active()) return;
        m.winner = winner; m.reason = reason; m.ended = System.currentTimeMillis();
        Store.Account win = store.accounts.get(winner);
        win.points++; win.wins++;
        store.accounts.get(m.player1).played++; store.accounts.get(m.player2).played++;
    }

    private void sendResult(Room r) {
        Store.Match m = r.match;
        both(r, "RESULT", m.id, m.winner, m.reason);
        for (String user : List.of(m.player1, m.player2)) {
            Session s = online.get(user); if (s != null) profile(s, "PROFILE");
        }
        broadcastOnline();
        log("Kết thúc trận " + m.id + " | thắng: " + m.winner + " | " + m.reason);
    }

    private void rematch(Session s, String id) throws IOException {
        Room r = requireRoom(s, id);
        require(!r.match.active(), "Trận đấu chưa kết thúc");
        Session other = online.get(r.match.other(s.user));
        require(other != null && !other.closed.get() && id.equals(other.roomId), "Đối thủ đã rời phòng");
        r.replay.add(s.user);
        both(r, "REMATCH_STATUS", id, s.user);
        if (r.replay.size() == 2) {
            rooms.remove(id); s.roomId = null; other.roomId = null; startRoom(s, other);
        }
    }

    private void leaveRoom(Session s, String reason) throws IOException {
        if (s.roomId == null) return;
        Room r = rooms.get(s.roomId);
        if (r == null) { s.roomId = null; return; }
        Session other = online.get(r.match.other(s.user));
        if (r.match.active()) {
            applyResult(r, r.match.other(s.user), reason); store.save(); sendResult(r);
        }
        s.roomId = null;
        if (other != null) { other.roomId = null; other.send("REMATCH_UNAVAILABLE", r.match.id, "Đối thủ đã rời phòng. Bạn có thể trở về sảnh."); }
        rooms.remove(r.match.id);
    }

    private void both(Room room, String type, String... fields) {
        for (String user : List.of(room.match.player1, room.match.player2)) {
            Session session = online.get(user); if (session != null) session.send(type, fields);
        }
    }

    private void disconnected(Session s) throws IOException {
        if (!sessions.remove(s)) return;
        if (s.invitationId != null) {
            Invitation invitation = invitations.get(s.invitationId);
            if (invitation != null) closeInvitation(invitation, "Một người chơi đã mất kết nối");
        }
        if (s.user != null) {
            leaveRoom(s, "DISCONNECTED"); online.remove(s.user, s); broadcastOnline();
            log(s.user + " đã ngắt kết nối");
        }
    }

    private void tick() throws IOException {
        long now = System.nanoTime();
        for (Session s : new ArrayList<>(sessions)) {
            if (s.closed.get() || now - s.lastSeen > TimeUnit.MILLISECONDS.toNanos(heartbeatMillis)
                    || (s.user == null && now - s.created > TimeUnit.MINUTES.toNanos(5))) {
                s.closeSocket(); disconnected(s);
            }
        }
        for (Invitation invitation : new ArrayList<>(invitations.values())) {
            if (now >= invitation.deadline) { closeInvitation(invitation, "Lời mời đã hết hạn"); broadcastOnline(); }
        }
        for (Room r : new ArrayList<>(rooms.values())) if (r.match.active()) {
            if (now >= r.deadline) timeout(r);
            int second = (int) Math.max(0, (r.deadline - now + 999_999_999L) / 1_000_000_000L);
            if (second != r.lastSecond) { r.lastSecond = second; sendTurn(r, "TICK"); }
        }
    }

    private static void log(String text) { System.out.println("[" + java.time.LocalTime.now().withNano(0) + "] " + text); }

    @Override public void close() {
        if (!closing.compareAndSet(false, true)) return;
        try { listener.close(); } catch (IOException ignored) { }
        try {
            loop.submit(() -> {
                if (!fatal) try { if (store.abortActive("SERVER_STOPPED")) store.save(); }
                catch (IOException e) { System.err.println("Không lưu được trạng thái dừng: " + e.getMessage()); }
                for (Session s : sessions) s.closeSocket();
                sessions.clear(); online.clear(); rooms.clear();
            }).get(5, TimeUnit.SECONDS);
        } catch (Exception e) { System.err.println("Đóng server: " + e.getMessage()); }
        finally {
            loop.shutdownNow(); passwordWorkers.shutdownNow();
            try { dataLock.release(); lockChannel.close(); } catch (IOException ignored) { }
            terminated.countDown();
        }
    }
}
