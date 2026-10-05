package vn.ptit.colorgame;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** JDBC chỉ chạy trên server. Mỗi lần lưu tài khoản và trận đấu dùng cùng transaction. */
final class MySqlStore {
    private final String url, database;
    private final Properties connection = new Properties();

    MySqlStore(Path config) throws IOException {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) { p.load(reader); }
        database = p.getProperty("db.name", "color_duel").trim();
        if (!database.matches("[A-Za-z0-9_]{1,64}")) throw new IOException("db.name không hợp lệ");
        String host = p.getProperty("db.host", "127.0.0.1").trim();
        int port;
        try { port = Integer.parseInt(p.getProperty("db.port", "3306").trim()); }
        catch (NumberFormatException e) { throw new IOException("db.port không hợp lệ", e); }
        if (port < 1 || port > 65535 || !host.matches("[A-Za-z0-9_.-]+"))
            throw new IOException("db.host hoặc db.port không hợp lệ");
        url = "jdbc:mysql://" + host + ":" + port + "/";
        connection.setProperty("user", p.getProperty("db.user", "root"));
        connection.setProperty("password", p.getProperty("db.password", ""));
        connection.setProperty("sslMode", p.getProperty("db.sslMode", "PREFERRED"));
        connection.setProperty("allowPublicKeyRetrieval", p.getProperty("db.allowPublicKeyRetrieval", "false"));
        connection.setProperty("characterEncoding", "UTF-8");
        connection.setProperty("connectTimeout", "5000");
        connection.setProperty("socketTimeout", "15000");
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (ClassNotFoundException e) {
            throw new IOException("Thiếu MySQL Connector/J. Đặt mysql-connector-j-8.3.0.jar trong dist/lib rồi build lại.", e);
        }
        try (Connection db = DriverManager.getConnection(url, connection)) {
            boolean exists;
            try (PreparedStatement q = db.prepareStatement("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?")) {
                q.setString(1, database);
                try (ResultSet rows = q.executeQuery()) { exists = rows.next(); }
            }
            if (!exists) try (Statement s = db.createStatement()) {
                s.executeUpdate("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            }
            db.setCatalog(database);
            try (Statement s = db.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS color_duel_meta (meta_key VARCHAR(64) PRIMARY KEY, meta_value VARCHAR(255) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS players (username VARCHAR(32) PRIMARY KEY, display_name VARCHAR(255) NOT NULL, password_salt VARCHAR(128) NOT NULL, password_hash VARCHAR(128) NOT NULL, password_iterations INT NOT NULL, points INT NOT NULL, wins INT NOT NULL, played INT NOT NULL, account_index INT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS matches (match_id VARCHAR(64) PRIMARY KEY, player1 VARCHAR(32) NOT NULL, player2 VARCHAR(32) NOT NULL, secret VARCHAR(6) NOT NULL, current_player VARCHAR(32) NOT NULL, winner VARCHAR(32) NOT NULL, reason VARCHAR(64) NOT NULL, turn_number INT NOT NULL, started_ms BIGINT NOT NULL, ended_ms BIGINT NOT NULL, match_index INT NOT NULL, FOREIGN KEY (player1) REFERENCES players(username), FOREIGN KEY (player2) REFERENCES players(username)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS turns (match_id VARCHAR(64) NOT NULL, move_index INT NOT NULL, turn_number INT NOT NULL, player VARCHAR(32) NOT NULL, guess VARCHAR(6) NOT NULL, correct_positions INT NOT NULL, move_type VARCHAR(32) NOT NULL, time_ms BIGINT NOT NULL, PRIMARY KEY (match_id,move_index), FOREIGN KEY (match_id) REFERENCES matches(match_id), FOREIGN KEY (player) REFERENCES players(username)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin");
            }
        } catch (SQLException e) { throw failure(e); }
    }

    String description() { return url + database; }

    private Connection open() throws SQLException { return DriverManager.getConnection(url + database, connection); }

    /** false chỉ khi database chưa được khởi tạo và không chứa dữ liệu game. */
    boolean load(Store store) throws IOException {
        try (Connection db = open()) {
            db.setAutoCommit(false);
            boolean initialized;
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT meta_value FROM color_duel_meta WHERE meta_key='initialized'")) {
                initialized = r.next();
            }
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM players ORDER BY account_index,username")) {
                while (r.next()) {
                    Store.Account a = new Store.Account(r.getString("username"), r.getString("display_name"), r.getString("password_salt"), r.getString("password_hash"));
                    a.iterations = r.getInt("password_iterations"); a.points = r.getInt("points");
                    a.wins = r.getInt("wins"); a.played = r.getInt("played");
                    if (a.iterations < 10_000 || a.iterations > 2_000_000) throw new IOException("Invalid password parameters in MySQL");
                    store.accounts.put(a.username, a);
                }
            }
            Map<String, Store.Match> byId = new LinkedHashMap<>();
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM matches ORDER BY match_index,match_id")) {
                while (r.next()) {
                    Store.Match m = new Store.Match(r.getString("match_id"), r.getString("player1"), r.getString("player2"), r.getString("secret"), r.getString("current_player"));
                    m.winner = r.getString("winner"); m.reason = r.getString("reason"); m.turn = r.getInt("turn_number");
                    m.started = r.getLong("started_ms"); m.ended = r.getLong("ended_ms");
                    byId.put(m.id, m); store.matches.add(m);
                }
            }
            try (Statement s = db.createStatement(); ResultSet r = s.executeQuery("SELECT * FROM turns ORDER BY match_id,move_index")) {
                while (r.next()) {
                    Store.Match m = byId.get(r.getString("match_id"));
                    if (m == null) throw new IOException("Lượt đoán không có trận đấu trong MySQL");
                    Store.Move move = new Store.Move(r.getInt("turn_number"), r.getString("player"), r.getString("guess"), r.getInt("correct_positions"), r.getString("move_type"));
                    move.at = r.getLong("time_ms"); m.moves.add(move);
                }
            }
            db.commit();
            return initialized || !store.accounts.isEmpty() || !store.matches.isEmpty();
        } catch (SQLException e) { throw failure(e); }
    }

    void save(Store store) throws IOException {
        try (Connection db = open()) {
            db.setAutoCommit(false);
            try {
                try (PreparedStatement q = db.prepareStatement("INSERT INTO players (username,display_name,password_salt,password_hash,password_iterations,points,wins,played,account_index) VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE display_name=VALUES(display_name),password_salt=VALUES(password_salt),password_hash=VALUES(password_hash),password_iterations=VALUES(password_iterations),points=VALUES(points),wins=VALUES(wins),played=VALUES(played),account_index=VALUES(account_index)")) {
                    int index = 0;
                    for (Store.Account a : store.accounts.values()) {
                        q.setString(1,a.username); q.setString(2,a.displayName); q.setString(3,a.salt); q.setString(4,a.hash);
                        q.setInt(5,a.iterations); q.setInt(6,a.points); q.setInt(7,a.wins); q.setInt(8,a.played); q.setInt(9,index++); q.addBatch();
                    }
                    q.executeBatch();
                }
                try (PreparedStatement q = db.prepareStatement("INSERT INTO matches (match_id,player1,player2,secret,current_player,winner,reason,turn_number,started_ms,ended_ms,match_index) VALUES (?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE current_player=VALUES(current_player),winner=VALUES(winner),reason=VALUES(reason),turn_number=VALUES(turn_number),ended_ms=VALUES(ended_ms),match_index=VALUES(match_index)")) {
                    int index = 0;
                    for (Store.Match m : store.matches) {
                        q.setString(1,m.id); q.setString(2,m.player1); q.setString(3,m.player2); q.setString(4,m.secret);
                        q.setString(5,m.current); q.setString(6,m.winner); q.setString(7,m.reason); q.setInt(8,m.turn);
                        q.setLong(9,m.started); q.setLong(10,m.ended); q.setInt(11,index++); q.addBatch();
                    }
                    q.executeBatch();
                }
                try (PreparedStatement q = db.prepareStatement("INSERT INTO turns (match_id,move_index,turn_number,player,guess,correct_positions,move_type,time_ms) VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE turn_number=VALUES(turn_number),player=VALUES(player),guess=VALUES(guess),correct_positions=VALUES(correct_positions),move_type=VALUES(move_type),time_ms=VALUES(time_ms)")) {
                    for (Store.Match m : store.matches) {
                        int index = 0;
                        for (Store.Move move : m.moves) {
                            q.setString(1,m.id); q.setInt(2,index++); q.setInt(3,move.number); q.setString(4,move.player);
                            q.setString(5,move.guess); q.setInt(6,move.correct); q.setString(7,move.type); q.setLong(8,move.at); q.addBatch();
                        }
                    }
                    q.executeBatch();
                }
                try (Statement s = db.createStatement()) {
                    s.executeUpdate("INSERT INTO color_duel_meta VALUES ('initialized','1') ON DUPLICATE KEY UPDATE meta_value='1'");
                }
                db.commit();
            } catch (SQLException e) {
                try { db.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException e) { throw failure(e); }
    }

    private IOException failure(SQLException e) {
        return new IOException("Không kết nối/lưu được MySQL " + database + ": " + e.getMessage()
                + ". Kiểm tra MySQL80 và data/mysql.properties; server không chuyển sang dữ liệu file khi MySQL lỗi.", e);
    }
}
