package vn.ptit.colorgame;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Dùng database tạm riêng, không đọc/ghi database thật trong cấu hình. */
public final class MySqlIntegrationTest {
    private static int passed;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        passed++; System.out.println("PASS " + label);
    }

    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final BlockingQueue<String[]> messages = new LinkedBlockingQueue<>();
        Peer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            Thread reader = new Thread(() -> {
                try (InputStream in = socket.getInputStream()) {
                    String[] m; while ((m = Wire.read(in)) != null) messages.add(m);
                } catch (IOException ignored) { }
            });
            reader.setDaemon(true); reader.start(); await("HELLO");
        }
        void send(String command, String... fields) throws Exception {
            socket.getOutputStream().write(Wire.encode(command, fields).getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
        }
        String[] await(String command) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                String[] m = messages.poll(100, TimeUnit.MILLISECONDS);
                if (m == null) continue;
                if (m[0].equals("ERROR")) throw new AssertionError(Arrays.toString(m));
                if (m[0].equals(command)) return m;
            }
            throw new AssertionError("Timed out: " + command);
        }
        public void close() throws IOException { socket.close(); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Specify data/mysql.properties; test creates its own temporary database");
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) { p.load(r); }
        String testDatabase = "color_duel_test_" + UUID.randomUUID().toString().replace("-", "");
        p.setProperty("db.name", testDatabase);
        Path data = Files.createTempDirectory("color-duel-mysql-test-");
        Path config = data.resolve("mysql.properties");
        String adminUrl = "jdbc:mysql://" + p.getProperty("db.host", "127.0.0.1") + ":" + p.getProperty("db.port", "3306") + "/";
        Properties connection = new Properties();
        connection.setProperty("user", p.getProperty("db.user", "root"));
        connection.setProperty("password", p.getProperty("db.password", ""));
        connection.setProperty("sslMode", p.getProperty("db.sslMode", "PREFERRED"));
        connection.setProperty("allowPublicKeyRetrieval", p.getProperty("db.allowPublicKeyRetrieval", "false"));
        connection.setProperty("connectTimeout", "5000"); connection.setProperty("socketTimeout", "10000");
        try {
            Store old = new Store(data);
            String salt = Passwords.salt(), password = "test-pass-123";
            Store.Account a = new Store.Account("alpha", "Người chơi Đỏ", salt, Passwords.hash(password, salt, Passwords.ITERATIONS));
            Store.Account b = new Store.Account("beta", "Người chơi Xanh", salt, a.hash);
            a.points = a.wins = 1; a.played = b.played = 1;
            old.accounts.put(a.username, a); old.accounts.put(b.username, b);
            Store.Match m = new Store.Match("test-match", "alpha", "beta", "012345", "alpha");
            m.reason = "SOLVED"; m.winner = "alpha"; m.ended = System.currentTimeMillis();
            m.moves.add(new Store.Move(1,"beta","",-1,"TIMEOUT"));
            m.moves.add(new Store.Move(2,"alpha","012345",6,"GUESS")); old.matches.add(m); old.save();
            byte[] snapshot = Files.readAllBytes(data.resolve("state.bin"));
            try (Writer w = Files.newBufferedWriter(config, StandardCharsets.UTF_8)) { p.store(w, "Temporary MySQL test config"); }
            Store db = new Store(data);
            check(db.accounts.size() == 2 && db.matches.size() == 1 && db.matches.get(0).moves.size() == 2, "Nhập toàn bộ tài khoản, trận và lượt từ state.bin");
            check(db.accounts.get("alpha").displayName.equals("Người chơi Đỏ") && Passwords.verify(password,db.accounts.get("alpha")), "Giữ tiếng Việt và băm mật khẩu cũ");
            db.accounts.get("alpha").points = 3; db.save(); db.save();
            Store reloaded = new Store(data);
            check(reloaded.accounts.get("alpha").points == 3 && reloaded.matches.get(0).moves.size() == 2, "Lưu nhiều lần không trùng dữ liệu, đọc MySQL khi khởi động lại");
            check(Arrays.equals(snapshot,Files.readAllBytes(data.resolve("state.bin"))), "Giữ nguyên snapshot gốc sau chuyển đổi");
            db.accounts.get("alpha").points = 99;
            db.matches.get(0).moves.add(new Store.Move(3,"missing_user","012345",0,"GUESS"));
            boolean failed = false;
            try { db.save(); } catch (IOException expected) { failed = true; }
            Store rolledBack = new Store(data);
            check(failed && rolledBack.accounts.get("alpha").points == 3 && rolledBack.matches.get(0).moves.size() == 2,
                    "Lỗi khóa ngoại rollback cả điểm và lịch sử trong cùng transaction");
            try (GameServer server = new GameServer(0,data)) {
                server.start();
                try (Peer peer = new Peer(server.port())) {
                    peer.send("REGISTER","gamma","test-pass-456","Người chơi Tím"); peer.await("AUTH");
                    check(new Store(data).accounts.containsKey("gamma"), "Đăng ký qua TCP ghi tài khoản vào MySQL");
                }
            }
            try (GameServer server = new GameServer(0,data)) {
                server.start();
                try (Peer peer = new Peer(server.port())) {
                    peer.send("LOGIN","gamma","test-pass-456"); peer.await("AUTH");
                    check(true,"Đăng nhập tài khoản MySQL sau khi khởi động lại server");
                }
            }
            reloaded.exportCsv(data.resolve("reports"));
            check(Files.readString(data.resolve("reports/turns.csv")).contains("TIMEOUT"),"Xuất CSV từ dữ liệu MySQL");
            try (Connection sql = DriverManager.getConnection(adminUrl + testDatabase, connection);
                 Statement s = sql.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM players")) {
                r.next(); check(r.getInt(1) == 3,"Đối chiếu trực tiếp bảng players qua JDBC");
            }
            System.out.println("MYSQL ALL PASSED: " + passed + " checks");
        } finally {
            Files.deleteIfExists(config);
            if (!testDatabase.matches("color_duel_test_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test database name");
            try (Connection sql = DriverManager.getConnection(adminUrl,connection); Statement s = sql.createStatement()) {
                s.executeUpdate("DROP DATABASE IF EXISTS `" + testDatabase + "`");
            }
        }
    }
}
