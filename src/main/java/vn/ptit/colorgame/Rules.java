package vn.ptit.colorgame;

import java.security.SecureRandom;

/** Luật dùng chung; server luôn kiểm tra lại mọi dữ liệu từ client. */
public final class Rules {
    public static final int PORT = 5000;
    public static final int TURN_MILLIS = 30_000;
    public static final int HEARTBEAT_MILLIS = 35_000;
    public static final String[] COLOR_NAMES = {"Đỏ", "Xanh lá", "Xanh dương", "Vàng", "Tím", "Cam"};

    private Rules() {}

    public static boolean validGuess(String s) {
        if (s == null || s.length() != 6) return false;
        int mask = 0;
        for (char c : s.toCharArray()) {
            if (c < '0' || c > '5' || (mask & (1 << (c - '0'))) != 0) return false;
            mask |= 1 << (c - '0');
        }
        return mask == 63;
    }

    public static int score(String secret, String guess) {
        if (!validGuess(secret) || !validGuess(guess)) throw new IllegalArgumentException("Dãy màu không hợp lệ");
        int n = 0;
        for (int i = 0; i < 6; i++) if (secret.charAt(i) == guess.charAt(i)) n++;
        return n;
    }

    public static String randomSequence(SecureRandom random) {
        char[] colors = "012345".toCharArray();
        for (int i = 5; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = colors[i]; colors[i] = colors[j]; colors[j] = tmp;
        }
        return new String(colors);
    }

    public static String colorText(String sequence) {
        if (!validGuess(sequence)) return "—";
        StringBuilder b = new StringBuilder();
        for (char c : sequence.toCharArray()) {
            if (b.length() > 0) b.append(" • ");
            b.append(COLOR_NAMES[c - '0']);
        }
        return b.toString();
    }

    public static String reasonText(String reason) {
        return switch (reason) {
            case "SOLVED" -> "Đoán đúng 6 vị trí";
            case "LEFT" -> "Đối thủ rời phòng";
            case "DISCONNECTED" -> "Đối thủ mất kết nối";
            case "SERVER_STOPPED" -> "Hủy do server dừng";
            case "SERVER_RESTARTED" -> "Hủy do server khởi động lại";
            case "ACTIVE" -> "Đang chơi";
            default -> reason;
        };
    }
}
