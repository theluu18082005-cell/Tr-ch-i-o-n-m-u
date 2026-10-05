package vn.ptit.colorgame;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** MySQL khi có mysql.properties; nếu không có cấu hình thì dùng snapshot file. */
public final class Store {
    public static final class Account {
        public String username, displayName, salt, hash;
        public int iterations = Passwords.ITERATIONS, points, wins, played;
        Account(String username, String displayName, String salt, String hash) {
            this.username = username; this.displayName = displayName; this.salt = salt; this.hash = hash;
        }
    }

    public static final class Move {
        public int number, correct;
        public String player, guess, type;
        public long at;
        Move(int number, String player, String guess, int correct, String type) {
            this.number = number; this.player = player; this.guess = guess;
            this.correct = correct; this.type = type; this.at = System.currentTimeMillis();
        }
    }

    public static final class Match {
        public String id, player1, player2, secret, current, winner = "", reason = "ACTIVE";
        public int turn = 1;
        public long started = System.currentTimeMillis(), ended;
        public final List<Move> moves = new ArrayList<>();
        Match(String id, String player1, String player2, String secret, String current) {
            this.id = id; this.player1 = player1; this.player2 = player2;
            this.secret = secret; this.current = current;
        }
        public boolean active() { return reason.equals("ACTIVE"); }
        public String other(String user) { return user.equals(player1) ? player2 : player1; }
    }

    private static final int MAGIC = 0x43444731;
    public final Map<String, Account> accounts = new LinkedHashMap<>();
    public final List<Match> matches = new ArrayList<>();
    public final Path directory;
    private final MySqlStore mysql;

    public Store(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath();
        Files.createDirectories(this.directory);
        Path file = this.directory.resolve("state.bin");
        Path config = this.directory.resolve("mysql.properties");
        mysql = Files.exists(config) ? new MySqlStore(config) : null;
        if (mysql == null) {
            if (Files.exists(file)) load(file);
        } else if (!mysql.load(this)) {
            if (Files.exists(file)) load(file);
            mysql.save(this);
            System.out.println("Đã khởi tạo MySQL: " + accounts.size() + " tài khoản, " + matches.size()
                    + " trận đấu. Giữ nguyên state.bin làm bản lưu dữ liệu cũ.");
        }
    }

    public String storageDescription() { return mysql == null ? directory.resolve("state.bin").toString() : mysql.description(); }

    private static int count(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 10_000_000) throw new IOException("Invalid record count");
        return n;
    }

    private void load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) throw new IOException("Định dạng dữ liệu không hợp lệ");
            int n = count(in);
            for (int i = 0; i < n; i++) {
                Account a = new Account(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF());
                a.iterations = in.readInt(); a.points = in.readInt(); a.wins = in.readInt(); a.played = in.readInt();
                if (a.iterations < 10_000 || a.iterations > 2_000_000) throw new IOException("Invalid password parameters");
                accounts.put(a.username, a);
            }
            int k = count(in);
            for (int i = 0; i < k; i++) {
                Match m = new Match(in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF());
                m.winner = in.readUTF(); m.reason = in.readUTF(); m.turn = in.readInt();
                m.started = in.readLong(); m.ended = in.readLong();
                int moves = count(in);
                for (int j = 0; j < moves; j++) {
                    Move move = new Move(in.readInt(), in.readUTF(), in.readUTF(), in.readInt(), in.readUTF());
                    move.at = in.readLong(); m.moves.add(move);
                }
                matches.add(m);
            }
            if (in.read() != -1) throw new IOException("Unexpected data after snapshot");
        } catch (RuntimeException e) { throw new IOException("Không đọc được dữ liệu server", e); }
    }

    public void save() throws IOException {
        if (mysql != null) { mysql.save(this); return; }
        Path temp = directory.resolve("state.bin.tmp"), target = directory.resolve("state.bin");
        try (FileOutputStream raw = new FileOutputStream(temp.toFile());
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(raw))) {
            out.writeInt(MAGIC); out.writeInt(accounts.size());
            for (Account a : accounts.values()) {
                out.writeUTF(a.username); out.writeUTF(a.displayName); out.writeUTF(a.salt); out.writeUTF(a.hash);
                out.writeInt(a.iterations); out.writeInt(a.points); out.writeInt(a.wins); out.writeInt(a.played);
            }
            out.writeInt(matches.size());
            for (Match m : matches) {
                out.writeUTF(m.id); out.writeUTF(m.player1); out.writeUTF(m.player2); out.writeUTF(m.secret);
                out.writeUTF(m.current); out.writeUTF(m.winner); out.writeUTF(m.reason); out.writeInt(m.turn);
                out.writeLong(m.started); out.writeLong(m.ended); out.writeInt(m.moves.size());
                for (Move move : m.moves) {
                    out.writeInt(move.number); out.writeUTF(move.player); out.writeUTF(move.guess);
                    out.writeInt(move.correct); out.writeUTF(move.type); out.writeLong(move.at);
                }
            }
            out.flush(); raw.getFD().sync();
        }
        // Nếu ổ đĩa không hỗ trợ atomic move, báo lỗi thay vì ghi đè bản dữ liệu đang dùng.
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public List<Account> ranking() {
        return accounts.values().stream().sorted(Comparator.comparingInt((Account a) -> a.points).reversed()
                .thenComparing(Comparator.comparingInt((Account a) -> a.wins).reversed())
                .thenComparing(Comparator.comparingInt((Account a) -> a.played).reversed())
                .thenComparing(a -> a.username)).toList();
    }

    public boolean abortActive(String reason) {
        boolean changed = false;
        for (Match m : matches) if (m.active()) {
            m.reason = reason; m.ended = System.currentTimeMillis(); changed = true;
        }
        return changed;
    }

    public void exportCsv(Path destination) throws IOException {
        Files.createDirectories(destination);
        try (Writer out = csvWriter(destination.resolve("players.csv"))) {
            row(out, "username", "display_name", "points", "wins", "played");
            for (Account a : ranking()) row(out, a.username, a.displayName, a.points, a.wins, a.played);
        }
        try (Writer out = csvWriter(destination.resolve("matches.csv"));
             Writer turns = csvWriter(destination.resolve("turns.csv"))) {
            row(out, "match_id", "player1", "player2", "secret_server_only", "winner", "reason", "started_ms", "ended_ms", "turns");
            row(turns, "match_id", "turn", "player", "guess", "correct", "type", "time_ms");
            for (Match m : matches) {
                row(out, m.id, m.player1, m.player2, Rules.colorText(m.secret), m.winner, m.reason, m.started, m.ended, m.moves.size());
                for (Move move : m.moves)
                    row(turns, m.id, move.number, move.player, Rules.colorText(move.guess), move.correct, move.type, move.at);
            }
        }
    }

    private static Writer csvWriter(Path path) throws IOException {
        Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        writer.write('\uFEFF'); return writer;
    }

    private static void row(Writer out, Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.write(',');
            String s = String.valueOf(values[i]);
            // Tránh Excel hiểu tên hiển thị do người dùng nhập là công thức.
            if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
            out.write('"'); out.write(s.replace("\"", "\"\"")); out.write('"');
        }
        out.write("\r\n");
    }
}
