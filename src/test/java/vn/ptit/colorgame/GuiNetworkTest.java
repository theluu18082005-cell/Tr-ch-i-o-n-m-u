package vn.ptit.colorgame;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.Arrays;
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
            edt(() -> { java.lang.reflect.Method logout=GameClient.class.getDeclaredMethod("logout"); logout.setAccessible(true); logout.invoke(carol); return null; });
            until(() -> ((DefaultTableModel)field(alice,"onlineModel")).getRowCount()==2,"Logout removed test account from online list");
            edt(() -> {
                ((JButton)field(carol,"showLogin")).doClick(); ((JTextField)field(carol,"username")).setText("carol");
                ((JPasswordField)field(carol,"password")).setText("matkhau123"); ((JButton)field(carol,"login")).doClick(); return null;
            });
            until(() -> field(carol,"me").equals("carol"),"Existing account logs in through redesigned form");
            until(() -> ((DefaultTableModel)field(alice,"onlineModel")).getRowCount()==3,"Login synchronized with lobby");
            edt(() -> {
                JTable table = (JTable) field(alice, "onlineTable");
                for (int i = 0; i < table.getRowCount(); i++) if (table.getValueAt(i, 0).equals("bob")) table.setRowSelectionInterval(i, i);
                ((JButton) field(alice, "inviteButton")).doClick(); return null;
            });
            until(() -> ((JPanel) field(bob, "invitationPanel")).isVisible(), "Đối thủ hiển thị lời mời");
            until(() -> ((InvitationOverlay)field(alice,"invitationOverlay")).isVisible() && ((InvitationOverlay)field(bob,"invitationOverlay")).isVisible()
                    && ((InvitationOverlay)field(alice,"invitationOverlay")).guardsInstalled() && ((InvitationOverlay)field(bob,"invitationOverlay")).guardsInstalled(),
                    "Both TCP clients lock their backgrounds while invitation is pending");
            until(() -> ((JPanel) field(alice, "invitationPanel")).isVisible()
                    && ((JButton) field(alice, "cancelInvite")).isEnabled()
                    && !((JButton) field(alice, "inviteButton")).isEnabled(), "Người gửi có thẻ lời mời và không thể mời trùng");
            edt(() -> { ((JButton) field(alice, "cancelInvite")).doClick(); return null; });
            until(() -> !((JPanel) field(alice, "invitationPanel")).isVisible()
                    && !((JPanel) field(bob, "invitationPanel")).isVisible()
                    && ((JButton) field(alice, "inviteButton")).isEnabled(), "Hủy lời mời đồng bộ trên cả hai client");
            edt(() -> { ((JButton) field(alice, "inviteButton")).doClick(); return null; });
            until(() -> ((JPanel) field(bob, "invitationPanel")).isVisible(), "Đối thủ nhận được lời mời mới sau khi hủy");
            edt(() -> { ((JButton) field(bob, "rejectInvite")).doClick(); return null; });
            until(() -> !((JPanel) field(alice, "invitationPanel")).isVisible()
                    && !((JPanel) field(bob, "invitationPanel")).isVisible()
                    && ((JButton) field(alice, "inviteButton")).isEnabled(), "Từ chối lời mời đóng thẻ và mở lại thao tác mời");
            edt(() -> { ((JButton) field(alice, "inviteButton")).doClick(); return null; });
            until(() -> ((JPanel) field(bob, "invitationPanel")).isVisible(), "Lời mời sẵn sàng để chấp nhận vào trận");
            edt(() -> { ((JButton) field(bob, "acceptInvite")).doClick(); return null; });
            until(() -> (int) field(alice, "turn") == 1 && (int) field(bob, "turn") == 1, "Hai cửa sổ vào phòng và nhận lượt");
            edt(() -> {
                ((JButton[])field(alice,"noteSlots"))[2].doClick(0);
                ((JMenuItem)((JPopupMenu)field(alice,"noteMenu")).getComponent(6)).doClick(0); return null;
            });
            until(() -> ((int[])field(alice,"positionNotes"))[2]==5
                    && Arrays.stream((int[])field(bob,"positionNotes")).allMatch(n -> n==-1)
                    && (int)field(alice,"turn")==1 && (int)field(bob,"turn")==1,
                    "Personal note stays on its own TCP client without changing the turn");
            if(!new Store(data).matches.get(0).moves.isEmpty()) throw new AssertionError("Note must not submit a guess to server");
            until(() -> !((InvitationOverlay)field(alice,"invitationOverlay")).isVisible() && !((InvitationOverlay)field(bob,"invitationOverlay")).isVisible()
                    && !((InvitationOverlay)field(alice,"invitationOverlay")).guardsInstalled() && !((InvitationOverlay)field(bob,"invitationOverlay")).guardsInstalled(),
                    "Both clients release modality when match starts");
            String secret = new Store(data).matches.get(0).secret;
            GameClient starter = edt(() -> field(alice,"current").equals("alice") ? alice : bob);
            GameClient next = starter==alice ? bob : alice;
            char[] wrong = secret.toCharArray(); char swap=wrong[0]; wrong[0]=wrong[1]; wrong[1]=swap;
            String firstGuess=new String(wrong);
            edt(() -> {
                // Use actual Swing transfer handlers, starting from position six.
                for(int i=5;i>=0;i--) {
                    JButton from=((JButton[])field(starter,"palette"))[firstGuess.charAt(i)-'0'];
                    java.lang.reflect.Method create=from.getTransferHandler().getClass().getDeclaredMethod("createTransferable",JComponent.class); create.setAccessible(true);
                    java.awt.datatransfer.Transferable payload=(java.awt.datatransfer.Transferable)create.invoke(from.getTransferHandler(),from);
                    JButton to=((JButton[])field(starter,"slots"))[i];
                    if(!to.getTransferHandler().importData(to,payload)) throw new AssertionError("Unable to drag into slot "+i);
                }
                ((JButton)field(starter,"submit")).doClick(); return null;
            });
            until(() -> (int)field(next,"turn")==2 && Arrays.equals((int[])field(next,"arrangement"),firstGuess.chars().map(c1 -> c1-'0').toArray())
                    && ((JButton)field(next,"submit")).isEnabled(),"Opponent guess prefilled into next client with submit enabled");
            swap=wrong[1]; wrong[1]=wrong[2]; wrong[2]=swap; String secondGuess=new String(wrong);
            edt(() -> { next.clickSlot(1); next.clickSlot(2); ((JButton)field(next,"submit")).doClick(); return null; });
            until(() -> (int)field(starter,"turn")==3 && Arrays.equals((int[])field(starter,"arrangement"),secondGuess.chars().map(c1 -> c1-'0').toArray())
                    && ((JButton)field(starter,"submit")).isEnabled(),"Returning player receives latest opponent order instead of their older guess");
            edt(() -> {
                JTable feed=(JTable)field(starter,"movesTable"); feed.setSize(370,216);
                feed.dispatchEvent(new java.awt.event.MouseEvent(feed,java.awt.event.MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,16,16,1,false,java.awt.event.MouseEvent.BUTTON1)); return null;
            });
            until(() -> Arrays.equals((int[])field(starter,"arrangement"),firstGuess.chars().map(ch -> ch-'0').toArray()) && !(boolean)field(starter,"submitted"),
                    "History click restores own old guess without submitting");
            if(new Store(data).matches.get(0).moves.size()!=2) throw new AssertionError("Recall must not send a GUESS to server");
            edt(() -> {
                JTable feed=(JTable)field(starter,"movesTable"); feed.setRowSelectionInterval(1,1);
                feed.getActionMap().get("recall-guess").actionPerformed(new java.awt.event.ActionEvent(feed,0,"recall-guess")); return null;
            });
            until(() -> Arrays.equals((int[])field(starter,"arrangement"),secondGuess.chars().map(ch -> ch-'0').toArray()),"Enter restores opponent history guess on connected client");
            edt(() -> {
                GameClient player = field(alice, "current").equals("alice") ? alice : bob;
                ((JButton)field(player,"clear")).doClick();
                for (char color : secret.toCharArray()) player.chooseColor(color - '0');
                ((JButton) field(player, "submit")).doClick(); return null;
            });
            until(() -> !(boolean) field(alice, "active") && !(boolean) field(bob, "active"), "Gửi bằng nút thật cập nhật kết quả thắng thua trên hai cửa sổ");
            until(() -> ((DefaultTableModel) field(alice, "movesModel")).getRowCount() == 3
                    && ((DefaultTableModel) field(bob, "movesModel")).getValueAt(2, 3).equals("6/6"), "Lịch sử hiển thị dự đoán chính xác 6/6");
            until(() -> ((JPanel)field(alice,"resultScreen")).isVisible() && ((JPanel)field(bob,"resultScreen")).isVisible(),"Victory and Defeat screens shown from real server results");
            edt(() -> { ((JButton) field(alice, "replay")).doClick(); ((JButton) field(bob, "replay")).doClick(); return null; });
            until(() -> (boolean) field(alice, "active") && (boolean) field(bob, "active")
                    && ((DefaultTableModel) field(alice, "movesModel")).getRowCount() == 0, "Nút Chơi lại tạo trận mới và xóa bảng lượt cũ");
            until(() -> ((DefaultTableModel) field(carol, "onlineModel")).getRowCount() == 3, "Client thứ ba vẫn kết nối tại sảnh");
            until(() -> !((JPanel)field(alice,"resultScreen")).isVisible() && !((JPanel)field(bob,"resultScreen")).isVisible(),"Rematch restores orb deck instead of stale results");
        } finally {
            GameClient ca = a, cb = b, cc = c;
            edt(() -> { if (ca != null) ca.shutdown(); if (cb != null) cb.shutdown(); if (cc != null) cc.shutdown(); return null; });
        }
        System.out.println("GUI TCP checks passed");
    }
}
