package vn.ptit.colorgame;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Render Swing thành ảnh trong môi trường headless và kiểm tra trạng thái tương tác. */
public final class UiSmokeTest {
    private static Object field(GameClient c, String name) throws Exception {
        Field f = GameClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(c);
    }
    private static void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); System.out.println("PASS UI " + name); }

    private static boolean visibleWithin(Component component, GameClient client) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
            if (current == client) return true;
        }
        return false;
    }

    private static void layout(Component c) {
        if (c instanceof Container p) { p.invalidate(); p.doLayout(); for (Component child : p.getComponents()) layout(child); }
    }
    private static void screenshot(GameClient client, Path path) throws Exception {
        screenshot(client, path, 1200, 840);
    }
    private static void screenshot(GameClient client, Path path, int width, int height) throws Exception {
        client.setSize(width, height); layout(client); layout(client);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics(); client.printAll(graphics); graphics.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length == 0 ? "build/ui-check" : args[0]); Files.createDirectories(out);
        SwingUtilities.invokeAndWait(() -> {
            GameClient client = null;
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                client = new GameClient();
                check(GameTheme.hasArtwork(), "Hình minh họa tải được từ resource trong chương trình");
                screenshot(client, out.resolve("login.png"));
                screenshot(client, out.resolve("login-small.png"), 1040, 680);
                JTextField username = (JTextField) field(client, "username");
                JTextField registerUsername = (JTextField) field(client, "registerUsername");
                JTextField displayName = (JTextField) field(client, "displayName");
                JPasswordField password = (JPasswordField) field(client, "password");
                JPasswordField registerPassword = (JPasswordField) field(client, "registerPassword");
                JLabel loginStatus = (JLabel) field(client, "loginStatus");
                JLabel registerStatus = (JLabel) field(client, "registerStatus");
                JTextField port = (JTextField) field(client, "port");
                check(visibleWithin(username, client) && visibleWithin(password, client)
                        && !visibleWithin(registerUsername, client) && !visibleWithin(displayName, client),
                        "Màn đăng nhập không hiển thị biểu mẫu đăng ký");
                String registrationNotice = registerStatus.getText();
                port.setText("0"); password.postActionEvent();
                check(loginStatus.getText().contains("Cổng") && registerStatus.getText().equals(registrationNotice)
                        && visibleWithin(password, client) && field(client, "connection") == null,
                        "Enter ở đăng nhập báo cổng không hợp lệ ngay trên màn đăng nhập");
                port.setText("5000");
                ((JButton) field(client, "showRegister")).doClick();
                check(visibleWithin(registerUsername, client) && visibleWithin(registerPassword, client)
                        && visibleWithin(displayName, client) && !visibleWithin(username, client),
                        "Nút tạo tài khoản mở riêng biểu mẫu đăng ký");
                screenshot(client, out.resolve("register.png"));
                screenshot(client, out.resolve("register-small.png"), 1040, 680);
                String loginNotice = loginStatus.getText();
                port.setText("0"); registerPassword.postActionEvent();
                check(registerStatus.getText().contains("Cổng") && loginStatus.getText().equals(loginNotice)
                        && visibleWithin(registerPassword, client) && field(client, "connection") == null,
                        "Enter ở đăng ký báo cổng không hợp lệ ngay trên màn đăng ký");
                port.setText("5000");
                ((JButton) field(client, "showLogin")).doClick();
                check(visibleWithin(username, client) && !visibleWithin(displayName, client),
                        "Có thể trở về màn đăng nhập từ đăng ký");
                client.onMessage(new String[]{"AUTH", "alice", "Thành viên 1", "5", "5", "8"});
                client.onMessage(new String[]{"ONLINE_BEGIN"});
                client.onMessage(new String[]{"ONLINE_ROW", "alice", "Thành viên 1", "5", "5", "8", "Đang rỗi"});
                client.onMessage(new String[]{"ONLINE_ROW", "bob", "Thành viên 2", "3", "3", "8", "Đang rỗi"});
                client.onMessage(new String[]{"ONLINE_ROW", "carol", "Thành viên 3", "2", "2", "4", "Đang rỗi"});
                client.onMessage(new String[]{"ONLINE_END"});
                screenshot(client, out.resolve("lobby.png"));
                String id = "12345678-demo";
                client.onMessage(new String[]{"ROOM", id, "alice", "bob"});
                client.onMessage(new String[]{"TURN", id, "1", "alice", "15000"});
                JButton submit = (JButton) field(client, "submit");
                check(!submit.isEnabled(), "Chưa chọn đủ màu thì chưa được gửi");
                for (int i = 0; i < 6; i++) client.chooseColor(i);
                check(submit.isEnabled(), "Đủ sáu màu và đúng lượt thì được gửi");
                client.clickSlot(0); client.clickSlot(1);
                int[] arrangement = (int[]) field(client, "arrangement");
                check(arrangement[0] == 1 && arrangement[1] == 0, "Bấm hai ô đổi thứ tự màu");
                client.onMessage(new String[]{"MOVE", id, "1", "alice", "012345", "2", "GUESS"});
                client.onMessage(new String[]{"TURN", id, "2", "bob", "15000"});
                check(!submit.isEnabled() && !((JButton[]) field(client, "slots"))[0].isEnabled(), "Khóa chọn màu và gửi khi đối thủ đến lượt");
                client.onMessage(new String[]{"MOVE", id, "2", "bob", "103254", "4", "GUESS"});
                client.onMessage(new String[]{"TURN", id, "3", "alice", "12000"});
                screenshot(client, out.resolve("game.png"));
                screenshot(client, out.resolve("game-small.png"), 1040, 680);
                ((JButton) field(client, "clear")).doClick();
                for (char color : "013254".toCharArray()) client.chooseColor(color-'0');
                client.onMessage(new String[]{"MOVE", id, "3", "alice", "013254", "6", "GUESS"});
                client.onMessage(new String[]{"RESULT", id, "alice", "SOLVED"});
                client.onMessage(new String[]{"PROFILE", "alice", "Thành viên 1", "6", "6", "9"});
                client.onMessage(new String[]{"PROFILE", "bob", "Thành viên 2", "3", "3", "9"});
                check(!submit.isEnabled() && ((JButton) field(client, "replay")).isEnabled(), "Kết thúc khóa bàn và mở nút chơi lại");
                screenshot(client, out.resolve("result.png"));
                client.onMessage(new String[]{"ROOM", "abcdefgh-demo", "alice", "bob"});
                check(((DefaultTableModel) field(client, "movesModel")).getRowCount() == 0, "Ván mới xóa lịch sử hiện tại");
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { if (client != null) client.shutdown(); }
        });
        System.out.println("UI checks passed; images: " + out.toAbsolutePath());
    }
}
