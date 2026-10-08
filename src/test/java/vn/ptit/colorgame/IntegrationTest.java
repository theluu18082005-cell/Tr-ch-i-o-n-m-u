package vn.ptit.colorgame;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/** Kiểm thử bằng TCP socket thật, không dùng thư viện kiểm thử bên ngoài. */
public final class IntegrationTest {
    private static int passed;

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        passed++; System.out.println("PASS " + label);
    }

    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final OutputStream output;
        final BlockingQueue<String[]> input = new LinkedBlockingQueue<>();
        final List<String[]> backlog = new ArrayList<>();
        final ScheduledExecutorService pings = Executors.newSingleThreadScheduledExecutor();
        String user;

        Peer(int port, String user) throws Exception {
            this.user = user;
            socket = new Socket("127.0.0.1", port); socket.setTcpNoDelay(true); output = socket.getOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream in = new BufferedInputStream(socket.getInputStream())) {
                    String[] m; while ((m = Wire.read(in)) != null) input.add(m);
                } catch (IOException ignored) { }
            });
            reader.setDaemon(true); reader.start();
            pings.scheduleAtFixedRate(() -> { try { send("PING"); } catch (Exception ignored) { } }, 100, 200, TimeUnit.MILLISECONDS);
            await("HELLO");
        }

        synchronized void send(String command, String... fields) throws IOException {
            output.write(Wire.encode(command, fields).getBytes(StandardCharsets.US_ASCII)); output.flush();
        }

        String[] await(String type) throws Exception { return await(m -> m[0].equals(type), 8000); }
        String[] await(Predicate<String[]> filter, long timeout) throws Exception {
            long until = System.nanoTime() + timeout * 1_000_000L;
            for (;;) {
                for (int i = 0; i < backlog.size(); i++) if (filter.test(backlog.get(i))) return backlog.remove(i);
                long left = until - System.nanoTime();
                if (left <= 0) throw new AssertionError(user + " timed out; received " + backlog.stream().map(m -> m[0]).toList());
                String[] m = input.poll(left, TimeUnit.NANOSECONDS);
                if (m != null && !m[0].equals("PONG") && !m[0].equals("TICK")) backlog.add(m);
            }
        }

        void register() throws Exception { send("REGISTER", user, "matkhau123", "Người chơi " + user); await("AUTH"); }
        void login() throws Exception { send("LOGIN", user, "matkhau123"); await("AUTH"); }
        void error(String command) throws Exception { await(m -> m[0].equals("ERROR") && m[1].equals(command), 5000); }
        void clearMessages() { backlog.clear(); input.clear(); }
        public void close() { pings.shutdownNow(); try { socket.close(); } catch (IOException ignored) { } }
    }

    private static String[] start(Peer a, Peer b) throws Exception {
        a.send("CHALLENGE", b.user); String[] invite = b.await("INVITE");
        a.await("INVITE_SENT"); b.send("RESPOND", invite[1], "YES");
        String[] ar = a.await("ROOM"), br = b.await("ROOM");
        check(Arrays.equals(ar, br), "Hai client nhận cùng phòng, không chứa dãy bí mật");
        check(ar.length == 4, "ROOM chỉ có mã phòng và hai tài khoản");
        return ar;
    }

    private static String[] turn(Peer a, Peer b) throws Exception {
        String[] t1 = a.await("TURN"), t2 = b.await("TURN");
        check(Arrays.equals(t1, t2), "Số lượt, người đến lượt và thời gian giống nhau");
        return t1;
    }

    private static List<String> permutations() {
        List<String> all = new ArrayList<>(); permutation("", "012345", all); return all;
    }
    private static void permutation(String prefix, String rest, List<String> all) {
        if (rest.isEmpty()) { all.add(prefix); return; }
        for (int i = 0; i < rest.length(); i++) permutation(prefix + rest.charAt(i), rest.substring(0, i) + rest.substring(i + 1), all);
    }

    private static void wireChecks() throws Exception {
        String wire = Wire.encode("TEST", "Tên có dấu\tva\nxuống dòng", "") + Wire.encode("PING");
        InputStream stream = new ByteArrayInputStream(wire.getBytes(StandardCharsets.US_ASCII));
        String[] m = Wire.read(stream);
        check(m.length == 3 && m[1].equals("Tên có dấu\tva\nxuống dòng") && m[2].isEmpty(), "Wire giữ đúng UTF-8 và ranh giới trường");
        check(Wire.read(stream)[0].equals("PING") && Wire.read(stream) == null, "Đọc được nhiều thông điệp ghép chung TCP");
        boolean refused = false;
        try { Wire.read(new ByteArrayInputStream(("X".repeat(Wire.MAX_LINE + 1)).getBytes())); }
        catch (IOException e) { refused = true; }
        check(refused, "Từ chối thông điệp quá dài");
        check(Rules.validGuess("012345") && !Rules.validGuess("001234") && !Rules.validGuess("012346"), "Kiểm tra đủ sáu màu, không lặp và không có màu lạ");
        check(Rules.score("023451", "013542") == 2, "Chấm đúng ví dụ 2 vị trí trong đề");
        check(permutations().size() == 720, "Đủ 720 hoán vị của sáu màu");
    }

    public static void main(String[] args) throws Exception {
        wireChecks();
        Path data = Files.createTempDirectory("color-duel-test-"), recovery = Files.createTempDirectory("color-duel-recovery-");
        // Giữ đúng thời gian lượt của bản chạy thật; chỉ rút heartbeat để kiểm tra đứt mạng nhanh.
        GameServer server = new GameServer(0, data, Rules.TURN_MILLIS, 1800);
        server.start();
        try (Peer a = new Peer(server.port(), "alice"); Peer b = new Peer(server.port(), "bob"); Peer c = new Peer(server.port(), "carol")) {
            a.register(); b.register(); c.register();
            check(new Store(data).accounts.size() == 3, "Ba client đăng ký và lưu tài khoản");
            Store.Account saved = new Store(data).accounts.get("alice");
            check(!saved.hash.equals("matkhau123") && Passwords.verify("matkhau123", saved), "Mật khẩu được lưu bằng PBKDF2 với salt");
            try (Peer duplicate = new Peer(server.port(), "alice")) {
                duplicate.send("LOGIN", "alice", "matkhau123"); duplicate.error("LOGIN");
                check(true, "Chặn đăng nhập trùng tài khoản trên hai máy");
            }
            try (Peer bad = new Peer(server.port(), "someone")) {
                bad.send("LOGIN", "someone", "wrong123"); bad.error("LOGIN"); check(true, "Từ chối sai tài khoản hoặc mật khẩu");
            }
            boolean locked = false;
            try (GameServer ignored = new GameServer(0, data)) { }
            catch (IOException e) { locked = true; }
            check(locked, "Ngăn hai server cùng ghi một thư mục dữ liệu");
            a.send("CHALLENGE", a.user); a.error("CHALLENGE");
            a.send("CHALLENGE", b.user); String[] invitation = b.await("INVITE"); a.await("INVITE_SENT");
            c.send("CHALLENGE", b.user); c.error("CHALLENGE");
            b.send("RESPOND", invitation[1], "NO"); b.await("INVITE_CLOSED"); a.await("INVITE_CLOSED");
            check(true, "Mời, từ chối và chặn lời mời chồng chéo");
            String[] room = start(a, b), t = turn(a, b);
            check(Integer.parseInt(t[4]) > Rules.TURN_MILLIS - 1000 && Integer.parseInt(t[4]) <= Rules.TURN_MILLIS,
                    "Lượt thật bắt đầu với thời lượng " + Rules.TURN_MILLIS / 1000 + " giây");
            Peer playing = t[3].equals(a.user) ? a : b, waiting = playing == a ? b : a;
            c.send("GUESS", room[1], t[2], "012345"); c.error("GUESS");
            waiting.send("GUESS", room[1], t[2], "012345"); waiting.error("GUESS");
            playing.send("GUESS", room[1], t[2], "001234"); playing.error("GUESS");
            check(new Store(data).matches.get(0).moves.isEmpty(), "Không ghi nhận dự đoán sai lượt, sai phòng hoặc lặp màu");
            String secret = new Store(data).matches.get(0).secret;
            String wrong = secret.equals("012345") ? "102345" : "012345";
            playing.send("GUESS", room[1], t[2], wrong); playing.send("GUESS", room[1], t[2], wrong);
            String[] move1 = a.await("MOVE"), move2 = b.await("MOVE");
            check(Arrays.equals(move1, move2) && Integer.parseInt(move1[5]) == Rules.score(secret, wrong), "Hai máy nhận cùng dự đoán và đúng số vị trí");
            playing.error("GUESS"); t = turn(a, b);
            check(new Store(data).matches.get(0).moves.size() == 1 && t[3].equals(waiting.user), "Gửi trùng không tính hai lượt, sau đó đổi người chơi");
            long before = System.nanoTime();
            String[] timeout1 = a.await(m -> m[0].equals("MOVE") && m[6].equals("TIMEOUT"), Rules.TURN_MILLIS + 5000);
            String[] timeout2 = b.await(m -> m[0].equals("MOVE") && m[6].equals("TIMEOUT"), 2000);
            check(Arrays.equals(timeout1, timeout2) && System.nanoTime() - before > TimeUnit.MILLISECONDS.toNanos(Rules.TURN_MILLIS - 2000),
                    "Hết " + Rules.TURN_MILLIS / 1000 + " giây tự động ghi lượt bỏ qua và đồng bộ hai máy");
            t = turn(a, b);
            List<String> candidates = permutations();
            int initialScore = Integer.parseInt(move1[5]); candidates.removeIf(p -> Rules.score(p, wrong) != initialScore);
            String winner = "";
            for (int i = 0; i < 20; i++) {
                Peer player = t[3].equals(a.user) ? a : b;
                String guess = candidates.get(0); player.send("GUESS", room[1], t[2], guess);
                String[] am = a.await("MOVE"), bm = b.await("MOVE");
                check(Arrays.equals(am, bm), "Lịch sử đồng bộ trong quá trình suy luận");
                int score = Integer.parseInt(am[5]);
                if (score == 6) {
                    String[] ra = a.await("RESULT"), rb = b.await("RESULT");
                    check(Arrays.equals(ra, rb) && ra[2].equals(player.user), "Đoán đúng sáu vị trí kết thúc trận và công bố đúng người thắng");
                    winner = ra[2]; break;
                }
                candidates.removeIf(p -> Rules.score(p, guess) != score); t = turn(a, b);
            }
            check(!winner.isEmpty(), "Có thể giải trận chỉ bằng các phản hồi công khai");
            Store state = new Store(data);
            check(state.accounts.get(winner).points == 1 && state.accounts.get("alice").played == 1
                    && state.accounts.get("bob").played == 1, "Cộng đúng 1 điểm và cập nhật số trận cho cả hai");
            a.clearMessages(); a.send("RANK"); a.await("RANK_BEGIN");
            check(a.await("RANK_ROW")[1].equals(winner), "Bảng xếp hạng ưu tiên điểm giảm dần");
            a.send("REMATCH", room[1]); a.await("REMATCH_STATUS"); b.await("REMATCH_STATUS");
            check(new Store(data).matches.size() == 1, "Chỉ một người đồng ý chưa bắt đầu chơi lại");
            b.send("REMATCH", room[1]); String[] next = a.await("ROOM"); b.await("ROOM"); t = turn(a, b);
            state = new Store(data);
            check(!next[1].equals(room[1]) && state.matches.size() == 2 && state.matches.get(1).moves.isEmpty(), "Hai người đồng ý tạo ván mới và lịch sử mới");
            a.send("LEAVE", next[1]); String[] res = b.await("RESULT"); a.await("RESULT"); a.await("LOBBY");
            check(res[2].equals(b.user) && res[3].equals("LEFT"), "Chủ động thoát bị tính thua");
            b.await("REMATCH_UNAVAILABLE"); b.send("BACK", next[1]); b.await("LOBBY");
            a.clearMessages(); b.clearMessages(); c.clearMessages();
            String[] disconnectRoom = start(a, c); turn(a, c); c.close();
            String[] disconnected = a.await("RESULT");
            check(disconnected[2].equals(a.user) && disconnected[3].equals("DISCONNECTED"), "Đóng socket xử thua người mất kết nối");
            a.send("BACK", disconnectRoom[1]); a.await("LOBBY");
            try (Peer again = new Peer(server.port(), "carol")) {
                again.login(); a.clearMessages(); again.clearMessages();
                String[] silentRoom = start(a, again); turn(a, again); again.pings.shutdownNow();
                String[] silent = a.await("RESULT");
                check(silent[2].equals(a.user) && silent[3].equals("DISCONNECTED"), "Heartbeat phát hiện kết nối im lặng dù chưa có TCP FIN");
                a.send("BACK", silentRoom[1]); a.await("LOBBY");
            }
            a.clearMessages(); b.clearMessages();
            String[] raceRoom = start(a, b); t = turn(a, b);
            String raceSecret = new Store(data).matches.get(4).secret;
            Peer racePlayer = t[3].equals(a.user) ? a : b, raceOther = racePlayer == a ? b : a;
            String turnNumber = t[2];
            Thread guessing = new Thread(() -> { try { racePlayer.send("GUESS", raceRoom[1], turnNumber, raceSecret); } catch (Exception e) { throw new RuntimeException(e); } });
            Thread leaving = new Thread(() -> { try { raceOther.send("LEAVE", raceRoom[1]); } catch (Exception e) { throw new RuntimeException(e); } });
            guessing.start(); leaving.start(); guessing.join(); leaving.join();
            a.await("RESULT"); b.await("RESULT"); raceOther.await("LOBBY");
            racePlayer.send("BACK", raceRoom[1]); racePlayer.await("LOBBY");
            state = new Store(data);
            check(state.accounts.get("alice").played == 5 && state.accounts.get("bob").played == 3,
                    "Đoán thắng và thoát đồng thời vẫn chỉ ghi một kết quả");
            a.clearMessages(); b.clearMessages(); start(a, b); turn(a, b);
            Files.copy(data.resolve("state.bin"), recovery.resolve("state.bin"));
            a.send("HISTORY"); a.await("HISTORY_BEGIN");
            check(a.await("HISTORY_ROW").length == 8, "Xem được lịch sử trận từ server");
            state.exportCsv(data.resolve("reports"));
            check(Files.readString(data.resolve("reports/turns.csv")).contains("TIMEOUT"), "Xuất CSV gồm tài khoản, trận đấu và lịch sử hết giờ");
        } finally { server.close(); }

        Store snapshot = new Store(recovery);
        int beforeRecovery = snapshot.accounts.get("alice").played;
        try (GameServer restarted = new GameServer(0, recovery)) {
            restarted.start();
            Store restored = new Store(recovery);
            check(restored.matches.get(restored.matches.size() - 1).reason.equals("SERVER_RESTARTED")
                    && restored.accounts.get("alice").played == beforeRecovery, "Khôi phục sau server sự cố: giữ dữ liệu, hủy ván dở, không cộng điểm oan");
            try (Peer a = new Peer(restarted.port(), "alice")) { a.login(); check(true, "Đăng nhập lại bằng tài khoản đã lưu sau khởi động server"); }
        }
        rankingTieCheck();
        System.out.println("ALL PASSED: " + passed + " checks");
        System.out.println("Temporary test data: " + data);
    }

    private static void rankingTieCheck() throws Exception {
        Store state = new Store(Files.createTempDirectory("rank-test-"));
        Store.Account a = new Store.Account("a", "A", "", ""), b = new Store.Account("b", "B", "", ""), c = new Store.Account("c", "C", "", "");
        a.points = b.points = c.points = 5; a.wins = b.wins = 5; c.wins = 4; a.played = 7; b.played = 9; c.played = 15;
        state.accounts.put(a.username, a); state.accounts.put(b.username, b); state.accounts.put(c.username, c);
        check(state.ranking().stream().map(x -> x.username).toList().equals(List.of("b", "a", "c")), "Thứ tự phụ: trận thắng giảm dần, sau đó tổng số trận giảm dần");
    }
}
