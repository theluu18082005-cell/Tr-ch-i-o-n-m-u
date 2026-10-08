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

    private static Component named(Component component, String name) {
        if (name.equals(component.getName())) return component;
        if (component instanceof Container parent) for (Component child : parent.getComponents()) {
            Component match = named(child, name); if (match != null) return match;
        }
        if(component instanceof GameClient client) return named(client.settingsComponent(),name);
        return null;
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
        ImageIO.write(image, "png", path.toFile()); criticalBounds(client);
    }

    private static void criticalBounds(GameClient client) throws Exception {
        String[] controls={"username","password","login","showRegister","registerUsername","registerPassword","displayName","register","showLogin","loginStatus","registerStatus","inviteButton","acceptInvite","rejectInvite","cancelInvite","submit","clear","remove","leave","replay","back"};
        for(String name:controls) {
            Component c=(Component)field(client,name); if(!visibleWithin(c,client)) continue;
            Rectangle area=SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),client);
            check(area.width>0 && area.height>=16 && new Rectangle(0,0,client.getWidth(),client.getHeight()).contains(area),"Visible control "+name+" at "+client.getWidth()+"x"+client.getHeight());
            if(c instanceof GameTheme.ActionButton action) check(action.getFontMetrics(action.getFont()).stringWidth(action.getText())<=action.captionWidth()-16,"Full action caption fits: "+name);
            for(Container ancestor=c.getParent(); ancestor!=client && ancestor!=null; ancestor=ancestor.getParent()) {
                Rectangle clip=SwingUtilities.convertRectangle(ancestor,new Rectangle(0,0,ancestor.getWidth(),ancestor.getHeight()),client);
                check(clip.contains(area),"Unclipped control "+name+" within "+ancestor.getClass().getSimpleName());
            }
        }
        settingBounds(client,client);
        JButton[] slots=(JButton[])field(client,"slots");
        if(slots[0]!=null && visibleWithin(slots[0],client)) for(JButton slot:slots) check(slot.getWidth()>=76 && slot.getHeight()>=96,"Six-orb deck remains legible");
    }
    private static void settingBounds(Component component,GameClient client) {
        if(component instanceof JCheckBox && visibleWithin(component,client)) {
            Rectangle area=SwingUtilities.convertRectangle(component.getParent(),component.getBounds(),client);
            check(area.width>=component.getPreferredSize().width && new Rectangle(0,0,client.getWidth(),client.getHeight()).contains(area),"Audio/effects setting fits: "+component.getName());
        }
        if(component instanceof Container parent) for(Component child:parent.getComponents()) settingBounds(child,client);
    }
    private static void resolutions(GameClient client,Path out,String screen) throws Exception {
        screenshot(client,out.resolve(screen+".png"));
        screenshot(client,out.resolve(screen+"-small.png"),1040,680);
        screenshot(client,out.resolve(screen+"-compact.png"),800,640);
    }

    private static void contrastChecks() {
        check(GameTheme.contrast(GameTheme.TEXT, GameTheme.PANEL) >= 7
                && GameTheme.contrast(GameTheme.MUTED, GameTheme.PANEL) >= 4.5,
                "Chữ chính và chữ phụ đủ tương phản trên nền đấu trường");
        check(GameTheme.contrast(GameTheme.ON_PRIMARY, GameTheme.PRIMARY) >= 4.5
                && GameTheme.contrast(GameTheme.ACCENT, GameTheme.PANEL) >= 4.5
                && GameTheme.contrast(GameTheme.RED, GameTheme.PANEL) >= 4.5,
                "Nút chính, màu nhấn và thông báo lỗi đủ tương phản");
        check(GameTheme.contrast(GameTheme.LINE, GameTheme.INPUT) >= 3,
                "Đường viền input có thể nhận biết rõ");
        for (Color color : GameTheme.COLORS) check(GameTheme.contrast(GameTheme.inkOn(color), color) >= 4.5,
                "Số trên màu " + color + " đủ tương phản");
    }

    private static void effectScreens(GameClient client, Path out, String screen, int width, int height) throws Exception {
        GameTheme.setReducedMotion(true); screenshot(client, out.resolve(screen + "-still.png"), width, height);
        GameTheme.setReducedMotion(false);
        check(!GameTheme.isReducedMotion(), "Khôi phục hiệu ứng sau khi kiểm tra " + screen);
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length == 0 ? "build/ui-check" : args[0]); Files.createDirectories(out);
        SwingUtilities.invokeAndWait(() -> {
            GameClient client = null;
            try {
                contrastChecks();
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                client = new GameClient();
                check(GameTheme.hasArtwork(), "Hình minh họa tải được từ resource trong chương trình");
                screenshot(client, out.resolve("login.png"));
                screenshot(client, out.resolve("login-small.png"), 1040, 680);
                screenshot(client, out.resolve("login-compact.png"), 800, 640);
                effectScreens(client, out, "login", 1200, 840);
                JTextField savedHost = (JTextField) field(client, "host");
                JPasswordField savedPassword = (JPasswordField) field(client, "password");
                savedPassword.setText("test-effect-secret");
                check(named(client,"theme-toggle")==null,"Giao diện game thống nhất, không còn nút sáng/tối");
                ((JCheckBox) named(client, "motion-toggle")).doClick();
                check(GameTheme.isReducedMotion(), "Lựa chọn giảm chuyển động bật đúng trạng thái");
                check(savedHost.getText().equals("127.0.0.1") && new String(savedPassword.getPassword()).equals("test-effect-secret"),
                        "Giảm hiệu ứng giữ nguyên dữ liệu nhập"); savedPassword.setText("");
                ((JCheckBox) named(client, "motion-toggle")).doClick();
                check(!GameTheme.isReducedMotion(), "Có thể bật lại hiệu ứng tương tác");
                JPanel showingRoot=new JPanel() { @Override public boolean isShowing() { return true; } };
                GameTheme.registerRoot(showingRoot);
                check(GameTheme.effectsRunning(),"Bộ hiệu ứng chạy khi có cửa sổ hiển thị");
                GameTheme.setReducedMotion(true);
                check(!GameTheme.effectsRunning(),"Giảm hiệu ứng dừng bộ hẹn giờ nền");
                GameTheme.setReducedMotion(false); GameTheme.unregisterRoot(showingRoot);
                check(!GameTheme.effectsRunning(),"Bộ hiệu ứng dừng khi không còn cửa sổ hiển thị");
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
                screenshot(client, out.resolve("register-compact.png"), 800, 640);
                effectScreens(client, out, "register", 1200, 840);
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
                JTable online = (JTable) field(client, "onlineTable");
                JButton invite = (JButton) field(client, "inviteButton");
                online.setRowSelectionInterval(0, 0);
                check(!invite.isEnabled(), "Không thể mời chính mình");
                online.setRowSelectionInterval(1, 1);
                check(invite.isEnabled(), "Chọn đối thủ sẵn sàng mở nút mời thi đấu");
                JTextField search = (JTextField) field(client, "opponentSearch");
                search.setText("bob");
                check(online.getRowCount() == 1 && online.getValueAt(0, 0).equals("bob"), "Tìm đối thủ theo tài khoản");
                search.setText("Thành viên 3");
                check(online.getRowCount() == 1 && online.getValueAt(0, 0).equals("carol"), "Tìm đối thủ theo tên hiển thị");
                search.setText(""); online.setRowSelectionInterval(1, 1);
                screenshot(client, out.resolve("lobby.png"));
                screenshot(client, out.resolve("lobby-small.png"), 1040, 680);
                screenshot(client, out.resolve("lobby-compact.png"), 800, 640);
                effectScreens(client, out, "lobby", 1200, 840);
                JTabbedPane tabs = (JTabbedPane) field(client, "tabs"); tabs.setSelectedIndex(1);
                screenshot(client, out.resolve("ranking-empty.png"));
                client.onMessage(new String[]{"RANK_BEGIN"});
                client.onMessage(new String[]{"RANK_ROW", "alice", "Thành viên 1", "5", "5", "8"});
                client.onMessage(new String[]{"RANK_ROW", "bob", "Thành viên 2", "3", "3", "8"});
                client.onMessage(new String[]{"RANK_END"});
                client.onMessage(new String[]{"RANK_ROW", "carol", "Thành viên 3", "2", "2", "4"}); resolutions(client,out,"ranking"); effectScreens(client, out, "ranking", 1200, 840);
                tabs.setSelectedIndex(2); screenshot(client, out.resolve("history-empty.png"));
                client.onMessage(new String[]{"HISTORY_BEGIN"});
                client.onMessage(new String[]{"HISTORY_ROW", "12345678-demo", "1791171000000", "alice", "bob", "alice", "SOLVED", "12"});
                client.onMessage(new String[]{"HISTORY_ROW", "abcdefgh-demo", "1791172000000", "alice", "carol", "carol", "SOLVED", "8"});
                client.onMessage(new String[]{"HISTORY_END"});
                resolutions(client,out,"history"); effectScreens(client, out, "history", 1200, 840); tabs.setSelectedIndex(0);
                client.onMessage(new String[]{"INVITE_SENT", "demo-invite", "bob", "30"});
                check(((JPanel) field(client, "invitationPanel")).isVisible()
                        && ((JButton) field(client, "cancelInvite")).isEnabled() && !invite.isEnabled(),
                        "Lời mời đã gửi hiện đếm ngược, có thể hủy và không gửi trùng");
                resolutions(client,out,"invitation-sent");
                client.onMessage(new String[]{"INVITE_CLOSED", "demo-invite", "Đã hủy lời mời"});
                check(!((JPanel) field(client, "invitationPanel")).isVisible() && invite.isEnabled(), "Đóng lời mời khôi phục nút mời đối thủ");
                client.onMessage(new String[]{"INVITE", "demo-received", "carol", "30"});
                resolutions(client,out,"invitation-received");
                screenshot(client, out.resolve("invitation-received-small.png"), 1040, 680);
                String pendingInvite = (String) field(client, "invitation");
                effectScreens(client, out, "invitation-received", 1200, 840);
                check(pendingInvite.equals(field(client, "invitation")) && ((JButton) field(client, "acceptInvite")).isEnabled(),
                        "Giảm hiệu ứng giữ nguyên lời mời và quyền chấp nhận");
                ((JButton) field(client, "acceptInvite")).doClick();
                check(!((JButton) field(client, "acceptInvite")).isEnabled() && !((JButton) field(client, "rejectInvite")).isEnabled(),
                        "Chấp nhận khóa phản hồi trong khi chờ server xác nhận");
                client.onMessage(new String[]{"INVITE_CLOSED", "demo-received", "Đã chấp nhận lời mời"});
                client.onMessage(new String[]{"ONLINE_BEGIN"});
                client.onMessage(new String[]{"ONLINE_ROW", "alice", "Thành viên 1", "5", "5", "8", "Đang rỗi"});
                client.onMessage(new String[]{"ONLINE_ROW", "bob", "Thành viên 2", "3", "3", "8", "Đang chơi"});
                client.onMessage(new String[]{"ONLINE_ROW", "carol", "Thành viên 3", "2", "2", "4", "Đang rỗi"});
                client.onMessage(new String[]{"ONLINE_END"});
                check(!invite.isEnabled(), "Đối thủ chuyển sang đang chơi thì không thể mời");
                client.onMessage(new String[]{"INVITE", "demo-expired", "carol", "30"});
                Field inviteDeadline = GameClient.class.getDeclaredField("invitationDeadline"); inviteDeadline.setAccessible(true);
                inviteDeadline.setLong(client, System.nanoTime() - 1);
                java.lang.reflect.Method updateInvitation = GameClient.class.getDeclaredMethod("updateInvitationClock");
                updateInvitation.setAccessible(true); updateInvitation.invoke(client);
                check(!((JButton) field(client, "acceptInvite")).isEnabled()
                        && ((JProgressBar) field(client, "invitationProgress")).getValue() == 0,
                        "Lời mời hết hạn khóa chấp nhận và đếm ngược về 0");
                client.onMessage(new String[]{"INVITE_CLOSED", "demo-expired", "Lời mời đã hết hạn"});
                String id = "12345678-demo";
                client.onMessage(new String[]{"ROOM", id, "alice", "bob"});
                client.onMessage(new String[]{"TURN", id, "1", "alice", Integer.toString(Rules.TURN_MILLIS)});
                JButton submit = (JButton) field(client, "submit");
                check(!submit.isEnabled(), "Chưa chọn đủ màu thì chưa được gửi");
                for (int i = 0; i < 6; i++) client.chooseColor(i);
                check(submit.isEnabled(), "Đủ sáu màu và đúng lượt thì được gửi");
                client.clickSlot(0); client.clickSlot(1);
                int[] arrangement = (int[]) field(client, "arrangement");
                check(arrangement[0] == 1 && arrangement[1] == 0, "Bấm hai ô đổi thứ tự màu");
                client.onMessage(new String[]{"MOVE", id, "1", "alice", "012345", "2", "GUESS"});
                client.onMessage(new String[]{"TURN", id, "2", "bob", Integer.toString(Rules.TURN_MILLIS)});
                check(!submit.isEnabled() && !((JButton[]) field(client, "slots"))[0].isEnabled(), "Khóa chọn màu và gửi khi đối thủ đến lượt");
                client.onMessage(new String[]{"MOVE", id, "2", "bob", "103254", "4", "GUESS"});
                client.onMessage(new String[]{"TURN", id, "3", "alice", "12000"});
                screenshot(client, out.resolve("game.png"));
                screenshot(client, out.resolve("game-small.png"), 1040, 680);
                screenshot(client, out.resolve("game-compact.png"), 800, 640);
                JLabel timer = (JLabel) field(client, "timerLabel");
                check(timer.getParent().getWidth() >= 96 && timer.getParent().getHeight() >= 96,
                        "Đồng hồ không bị co nhỏ ở cửa sổ 800x640");
                int[] beforeEffects = arrangement.clone();
                effectScreens(client, out, "game", 1200, 840);
                check(java.util.Arrays.equals(beforeEffects, arrangement) && submit.isEnabled(), "Giảm hiệu ứng không mất dãy màu và lượt đang chơi");
                client.onMessage(new String[]{"TICK",id,"3","alice","4000"});
                screenshot(client,out.resolve("game-warning.png"));
                client.onMessage(new String[]{"TICK",id,"3","alice","12000"});
                GameTheme.ColorSlot firstSlot=(GameTheme.ColorSlot)((JButton[])field(client,"slots"))[0];
                Field changed=GameTheme.ColorSlot.class.getDeclaredField("changedAt"); changed.setAccessible(true);
                long beforeTick=changed.getLong(firstSlot); firstSlot.state(arrangement[0],false,true);
                check(changed.getLong(firstSlot)==beforeTick,"Cập nhật đồng hồ không khởi động lại hiệu ứng chọn màu");
                ((JButton) field(client, "clear")).doClick();
                JPanel board = (JPanel) named(client, "guess-board");
                board.getActionMap().get("color-0").actionPerformed(new java.awt.event.ActionEvent(board, 0, "color-0"));
                check(arrangement[0] == 0, "Phím chọn màu dùng cùng luật kiểm tra lượt");
                ((JButton) field(client, "clear")).doClick();
                for (char color : "013254".toCharArray()) client.chooseColor(color-'0');
                client.onMessage(new String[]{"MOVE", id, "3", "alice", "013254", "6", "GUESS"});
                client.onMessage(new String[]{"RESULT", id, "alice", "SOLVED"});
                client.onMessage(new String[]{"PROFILE", "alice", "Thành viên 1", "6", "6", "9"});
                client.onMessage(new String[]{"PROFILE", "bob", "Thành viên 2", "3", "3", "9"});
                check(!submit.isEnabled() && ((JButton) field(client, "replay")).isEnabled(), "Kết thúc khóa bàn và mở nút chơi lại");
                GameTheme.Celebration celebration=(GameTheme.Celebration)field(client,"celebration");
                check(celebration.active(),"Thắng trận kích hoạt hiệu ứng chúc mừng");
                Field celebrationStart=GameTheme.Celebration.class.getDeclaredField("started"); celebrationStart.setAccessible(true);
                celebrationStart.setLong(celebration,System.nanoTime()-700_000_000L);
                resolutions(client,out,"result");
                effectScreens(client, out, "result", 1200, 840);
                client.onMessage(new String[]{"ROOM", "abcdefgh-demo", "alice", "bob"});
                check(!celebration.active(),"Ván mới xóa hiệu ứng chiến thắng của ván trước");
                client.onMessage(new String[]{"TURN","abcdefgh-demo","1","bob","30000"});
                client.onMessage(new String[]{"RESULT","abcdefgh-demo","bob","SOLVED"});
                check(!celebration.active(),"Defeat does not celebrate"); resolutions(client,out,"defeat");
                check(((DefaultTableModel) field(client, "movesModel")).getRowCount() == 0, "Ván mới xóa lịch sử hiện tại");
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { if (client != null) client.shutdown(); }
            check(GameTheme.registeredRoots()==0 && !GameTheme.effectsRunning(),"Đóng client giải phóng bộ hiệu ứng");
        });
        System.out.println("UI checks passed; images: " + out.toAbsolutePath());
    }
}
