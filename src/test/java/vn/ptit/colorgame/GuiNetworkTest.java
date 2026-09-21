package vn.ptit.colorgame;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/** Nối chính giao diện và NetworkClient vào server thật, thử một trận hoàn chỉnh. */
public final class GuiNetworkTest {
    private static Object field(GameClient c, String name) throws Exception {
        Field f = GameClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(c);
    }
    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); SwingUtilities.invokeAndWait(task); return task.get();
    }
    private static void until(Callable<Boolean> condition, String description) throws Exception {
        long end = System.nanoTime() + 8_000_000_000L;
        while (!edt(condition)) {
            if (System.nanoTime() > end) throw new AssertionError(description);
            Thread.sleep(20);
        }
        System.out.println("PASS GUI TCP " + description);
    }
    private static GameClient create(int port, String user) throws Exception {
        return edt(() -> {
            GameClient c = new GameClient(); c.setServer("127.0.0.1", port);
            ((JButton) field(c, "showRegister")).doClick();
            ((JTextField) field(c, "registerUsername")).setText(user);
            ((JPasswordField) field(c, "registerPassword")).setText("matkhau123");
            ((JTextField) field(c, "displayName")).setText("Thành viên " + user);
            ((JButton) field(c, "register")).doClick(); return c;
        });
    }

    public static void main(String[] args) throws Exception {
        Path data = Files.createTempDirectory("gui-network-test-");
        GameClient a = null, b = null, c = null;
        try (GameServer server = new GameServer(0, data)) {
            server.start();
            a = create(server.port(), "alice"); b = create(server.port(), "bob"); c = create(server.port(), "carol");
            GameClient alice = a, bob = b, carol = c;
            until(() -> ((DefaultTableModel) field(alice, "onlineModel")).getRowCount() == 3
                    && ((DefaultTableModel) field(bob, "onlineModel")).getRowCount() == 3
                    && ((DefaultTableModel) field(carol, "onlineModel")).getRowCount() == 3, "Ba cửa sổ đăng ký và cùng thấy danh sách online");
            edt(() -> {
                JTable table = (JTable) field(alice, "onlineTable");
                for (int i = 0; i < table.getRowCount(); i++) if (table.getValueAt(i, 0).equals("bob")) table.setRowSelectionInterval(i, i);
                ((JButton) field(alice, "inviteButton")).doClick(); return null;
            });
            until(() -> ((JPanel) field(bob, "invitationPanel")).isVisible(), "Đối thủ hiển thị lời mời");
            edt(() -> { ((JButton) field(bob, "acceptInvite")).doClick(); return null; });
            until(() -> (int) field(alice, "turn") == 1 && (int) field(bob, "turn") == 1, "Hai cửa sổ vào phòng và nhận lượt");
            String secret = new Store(data).matches.get(0).secret;
            edt(() -> {
                GameClient player = field(alice, "current").equals("alice") ? alice : bob;
                for (char color : secret.toCharArray()) player.chooseColor(color - '0');
                ((JButton) field(player, "submit")).doClick(); return null;
            });
            until(() -> !(boolean) field(alice, "active") && !(boolean) field(bob, "active"), "Gửi bằng nút thật cập nhật kết quả thắng thua trên hai cửa sổ");
            until(() -> ((DefaultTableModel) field(alice, "movesModel")).getRowCount() == 1
                    && ((DefaultTableModel) field(bob, "movesModel")).getValueAt(0, 3).equals("6/6"), "Lịch sử hiển thị dự đoán chính xác 6/6");
            edt(() -> { ((JButton) field(alice, "replay")).doClick(); ((JButton) field(bob, "replay")).doClick(); return null; });
            until(() -> (boolean) field(alice, "active") && (boolean) field(bob, "active")
                    && ((DefaultTableModel) field(alice, "movesModel")).getRowCount() == 0, "Nút Chơi lại tạo trận mới và xóa bảng lượt cũ");
            until(() -> ((DefaultTableModel) field(carol, "onlineModel")).getRowCount() == 3, "Client thứ ba vẫn kết nối tại sảnh");
        } finally {
            GameClient ca = a, cb = b, cc = c;
            edt(() -> { if (ca != null) ca.shutdown(); if (cb != null) cb.shutdown(); if (cc != null) cc.shutdown(); return null; });
        }
        System.out.println("GUI TCP checks passed");
    }
}
