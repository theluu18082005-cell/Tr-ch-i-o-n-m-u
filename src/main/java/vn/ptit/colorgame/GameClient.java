package vn.ptit.colorgame;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public final class GameClient extends JPanel {
    static { GameTheme.install(); }
    private static final Color NAVY = GameTheme.TEXT, BG = GameTheme.BACKGROUND;
    private static final Color TEAL = GameTheme.ACCENT, MUTED = GameTheme.MUTED;
    private final CardLayout cards = new CardLayout();
    private final JTextField host = new JTextField("127.0.0.1"), port = new JTextField("5000");
    private final JTextField username = new JTextField(), registerUsername = new JTextField(), displayName = new JTextField();
    private final JPasswordField password = new JPasswordField(), registerPassword = new JPasswordField();
    private final JButton login = button("Đăng nhập"), register = button("Đăng ký tài khoản");
    private final JButton showRegister = button("Chưa có tài khoản? Đăng ký"), showLogin = button("Đã có tài khoản? Đăng nhập");
    private final JLabel loginStatus = new GameTheme.Notice("Nhập IP server để kết nối.");
    private final JLabel registerStatus = new GameTheme.Notice("Mật khẩu cần ít nhất 6 ký tự.");
    private final JLabel profileLabel = label("", 14, NAVY);
    private final JLabel lobbyStatus = label("Chọn một người đang rỗi để mời thi đấu.", 14, MUTED);
    private final JButton inviteButton = button("Mời thi đấu"), cancelInvite = button("Hủy lời mời");
    private final JButton acceptInvite = button("Chấp nhận"), rejectInvite = button("Từ chối");
    private final JPanel invitationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
    private final JLabel invitationLabel = label("", 14, NAVY);
    private final DefaultTableModel onlineModel = model("Tài khoản", "Tên người chơi", "Điểm", "Trận thắng", "Đã chơi", "Trạng thái");
    private final DefaultTableModel rankModel = model("Hạng", "Tài khoản", "Tên người chơi", "Điểm", "Trận thắng", "Đã chơi");
    private final DefaultTableModel pastModel = model("Mã trận", "Thời gian", "Người chơi 1", "Người chơi 2", "Kết quả", "Lượt", "Lý do");
    private final JTable onlineTable = table(onlineModel), rankTable = table(rankModel), pastTable = table(pastModel);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel roomLabel = label("Phòng chơi", 12, MUTED);
    private final GameTheme.PlayerCard playerA = new GameTheme.PlayerCard(), playerB = new GameTheme.PlayerCard();
    private final JLabel turnLabel = label("Đang chờ đối thủ", 17, TEAL), timerLabel = label("15 s", 23, NAVY);
    private final JLabel fillLabel = label("0 / 6 màu", 12, MUTED);
    private final GameTheme.Badge onlineCount = new GameTheme.Badge("ĐANG KẾT NỐI", TEAL);
    private final JLabel resultLabel = label("", 14, MUTED), replayLabel = label("", 13, MUTED);
    private final JProgressBar clock = new JProgressBar(0, 15_000);
    private final JButton[] palette = new JButton[6], slots = new JButton[6];
    private final int[] arrangement = {-1, -1, -1, -1, -1, -1};
    private final JButton submit = button("Gửi dự đoán"), clear = button("Xóa tất cả"), remove = button("Bỏ ô đang chọn");
    private final JButton leave = button("Thoát phòng"), replay = button("Chơi lại"), back = button("Trở về danh sách online");
    private final DefaultTableModel movesModel = model("Lượt", "Người chơi", "Dãy dự đoán", "Đúng", "Trạng thái");
    private final JTable movesTable = table(movesModel);
    private final Map<String, String[]> profiles = new HashMap<>();
    private final List<String[]> onlineRows = new ArrayList<>();
    private final javax.swing.Timer uiTimer;
    private NetworkClient connection;
    private String me = "", myName = "", room = "", first = "", second = "", current = "", invitation = "";
    private int generation, turn, selectedSlot = -1;
    private long deadline;
    private boolean active, submitted, replayRequested, registering;

    public GameClient() {
        setLayout(cards); setBackground(BG);
        add(authView(false), "login"); add(authView(true), "register");
        add(lobbyView(), "lobby"); add(gameView(), "game");
        cards.show(this, "login");
        login.addActionListener(e -> authenticate(false)); register.addActionListener(e -> authenticate(true));
        password.addActionListener(e -> authenticate(false));
        registerPassword.addActionListener(e -> authenticate(true));
        displayName.addActionListener(e -> authenticate(true));
        showRegister.addActionListener(e -> showAuthentication(true));
        showLogin.addActionListener(e -> showAuthentication(false));
        inviteButton.addActionListener(e -> challengeSelected());
        cancelInvite.addActionListener(e -> { if (!invitation.isEmpty()) send("CANCEL_INVITE", invitation); });
        acceptInvite.addActionListener(e -> respond("YES")); rejectInvite.addActionListener(e -> respond("NO"));
        submit.addActionListener(e -> submitGuess());
        clear.addActionListener(e -> { Arrays.fill(arrangement, -1); selectedSlot = -1; updateBoard(); });
        remove.addActionListener(e -> { if (selectedSlot >= 0) arrangement[selectedSlot] = -1; selectedSlot = -1; updateBoard(); });
        leave.addActionListener(e -> leaveRoom());
        replay.addActionListener(e -> { send("REMATCH", room); replayRequested = true; replay.setEnabled(false); replayLabel.setText("Đang chờ đối thủ đồng ý chơi lại…"); });
        back.addActionListener(e -> send("BACK", room));
        tabs.addChangeListener(e -> refreshTab());
        uiTimer = new javax.swing.Timer(100, e -> updateClock()); uiTimer.start();
    }

    public void setServer(String address, int number) { host.setText(address); port.setText("" + number); }

    @Override public void paint(Graphics g) {
        Graphics2D p = GameTheme.graphics(g); super.paint(p); p.dispose();
    }

    private JPanel authView(boolean creating) {
        JPanel page = base(); page.setLayout(new BorderLayout(0, 16));
        page.add(brandBar("SÁU MÀU. MỘT CUỘC ĐẤU TRÍ."), BorderLayout.NORTH);
        JPanel columns = new JPanel(new GridBagLayout()); columns.setOpaque(false);
        GameTheme.ArtPanel hero = new GameTheme.ArtPanel(true);
        hero.setLayout(new BorderLayout()); hero.setBorder(new EmptyBorder(28, 28, 28, 28));
        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); badgeRow.setOpaque(false);
        badgeRow.add(new GameTheme.Badge("1 VS 1  /  TRỰC TUYẾN", GameTheme.ACCENT)); hero.add(badgeRow, BorderLayout.NORTH);
        JPanel copy = vertical();
        copy.add(label("Đấu trí", 43, NAVY)); copy.add(label("bằng sắc màu.", 43, GameTheme.ACCENT));
        copy.add(Box.createVerticalStrut(12));
        copy.add(label("Mỗi lượt đoán là một bước gần hơn", 14, NAVY));
        copy.add(label("đến dãy màu bí mật.", 14, NAVY)); copy.add(Box.createVerticalStrut(18));
        JPanel facts = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); facts.setOpaque(false);
        facts.add(new GameTheme.Badge("6 MÀU", GameTheme.GOLD)); facts.add(Box.createHorizontalStrut(9));
        facts.add(new GameTheme.Badge("15 GIÂY / LƯỢT", GameTheme.ACCENT)); hero.add(copy, BorderLayout.SOUTH); copy.add(facts);
        hero.setPreferredSize(new Dimension(530, 590));

        GameTheme.RoundPanel form = new GameTheme.RoundPanel(24); form.setLayout(new BorderLayout());
        form.setBorder(new EmptyBorder(26, 28, 22, 28));
        JPanel fields = vertical(); fields.add(label(creating ? "Đăng ký tài khoản" : "Đăng nhập", 28, NAVY)); fields.add(Box.createVerticalStrut(6));
        fields.add(label(creating ? "Tạo tài khoản để bắt đầu cuộc đấu." : "Đăng nhập để thách đấu bạn bè.", 13, MUTED)); fields.add(Box.createVerticalStrut(22));
        JTextField addressField = creating ? new JTextField(host.getDocument(), null, 0) : host;
        JTextField portField = creating ? new JTextField(port.getDocument(), null, 0) : port;
        JPanel address = new JPanel(new GridBagLayout()); address.setOpaque(false);
        GridBagConstraints a = new GridBagConstraints(); a.fill=GridBagConstraints.HORIZONTAL; a.weightx=0.75;
        a.gridx=0; a.insets=new Insets(0,0,0,12); address.add(fieldGroup("ĐỊA CHỈ SERVER", addressField),a);
        a.gridx=1; a.weightx=0.25; a.insets=new Insets(0,0,0,0); address.add(fieldGroup("CỔNG", portField),a);
        address.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62)); fields.add(address); fields.add(Box.createVerticalStrut(12));
        fields.add(fieldGroup("TÀI KHOẢN", creating ? registerUsername : username)); fields.add(Box.createVerticalStrut(12));
        fields.add(fieldGroup("MẬT KHẨU", creating ? registerPassword : password));
        if (creating) { fields.add(Box.createVerticalStrut(12)); fields.add(fieldGroup("TÊN HIỂN THỊ", displayName)); }
        fields.add(Box.createVerticalStrut(18));
        JButton action = creating ? register : login, navigation = creating ? showLogin : showRegister;
        ((GameTheme.ActionButton) action).primary(); action.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        navigation.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        fields.add(action); fields.add(Box.createVerticalStrut(10)); fields.add(navigation); fields.add(Box.createVerticalStrut(12));
        JLabel status = creating ? registerStatus : loginStatus;
        status.setFont(GameTheme.font(12, false)); status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        fields.add(status); form.add(fields, BorderLayout.CENTER); form.setPreferredSize(new Dimension(410, 590));
        GridBagConstraints c = new GridBagConstraints(); c.fill=GridBagConstraints.BOTH; c.weighty=1;
        c.gridx=0; c.weightx=.57; c.insets=new Insets(0,0,0,20); columns.add(hero,c);
        c.gridx=1; c.weightx=.43; c.insets=new Insets(0,0,0,0); columns.add(form,c);
        page.add(columns, BorderLayout.CENTER); return page;
    }

    private void showAuthentication(boolean creating) {
        if (!login.isEnabled()) return;
        generation++; if (connection != null) connection.close(); connection = null;
        registering = creating; password.setText(""); registerPassword.setText("");
        (creating ? registerStatus : loginStatus).setText(creating ? "Mật khẩu cần ít nhất 6 ký tự." : "Nhập IP server để kết nối.");
        cards.show(this, creating ? "register" : "login");
        (creating ? registerUsername : username).requestFocusInWindow();
    }

    private void setAuthenticationEnabled(boolean enabled) {
        login.setEnabled(enabled); register.setEnabled(enabled);
        showRegister.setEnabled(enabled); showLogin.setEnabled(enabled);
    }

    private static JPanel fieldGroup(String caption, JTextField field) {
        GameTheme.field(field); JPanel group = new JPanel(new BorderLayout(0, 7)); group.setOpaque(false);
        group.add(label(caption, 11, MUTED), BorderLayout.NORTH); group.add(field, BorderLayout.CENTER);
        group.setPreferredSize(new Dimension(230, 60)); group.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60)); return group;
    }

    private static JPanel brandBar(String caption) {
        JPanel bar = new JPanel(new BorderLayout()); bar.setOpaque(false);
        JLabel brand = label("COLOR DUEL", 19, NAVY); brand.setIcon(new GameTheme.Logo()); brand.setIconTextGap(12);
        bar.add(brand, BorderLayout.WEST); JLabel description = label(caption, 11, MUTED);
        description.setHorizontalAlignment(SwingConstants.RIGHT); bar.add(description, BorderLayout.CENTER);
        bar.setPreferredSize(new Dimension(1, 38)); return bar;
    }

    private JPanel lobbyView() {
        JPanel page = base(); page.setLayout(new BorderLayout(0, 15));
        JPanel top = new JPanel(new BorderLayout(0, 14)); top.setOpaque(false);
        top.add(brandBar("SẢNH NGƯỜI CHƠI"), BorderLayout.NORTH);
        GameTheme.ArtPanel hero = new GameTheme.ArtPanel(false); hero.setLayout(new BorderLayout());
        hero.setBorder(new EmptyBorder(22, 25, 20, 25)); hero.setPreferredSize(new Dimension(1, 156));
        JPanel copy = vertical(); copy.add(onlineCount); copy.add(Box.createVerticalStrut(10));
        copy.add(label("Sẵn sàng thách đấu?", 28, NAVY)); copy.add(Box.createVerticalStrut(8));
        copy.add(profileLabel); hero.add(copy, BorderLayout.CENTER); top.add(hero, BorderLayout.CENTER);
        page.add(top, BorderLayout.NORTH);
        onlineTable.putClientProperty("emptyText", "Đang chờ người chơi kết nối…");
        rankTable.putClientProperty("emptyText", "Chưa có người chơi trên bảng xếp hạng.");
        pastTable.putClientProperty("emptyText", "Trận đấu đầu tiên của bạn đang chờ ở phía trước.");
        tabs.addTab("Người chơi online", scrollTable(onlineTable));
        tabs.addTab("Bảng xếp hạng", scrollTable(rankTable));
        tabs.addTab("Các trận của tôi", scrollTable(pastTable));
        GameTheme.tabs(tabs); page.add(tabs, BorderLayout.CENTER);
        GameTheme.RoundPanel bottom = new GameTheme.RoundPanel(18); bottom.setLayout(new BorderLayout(0, 9));
        bottom.setBorder(new EmptyBorder(13, 17, 13, 17));
        JPanel primary = new JPanel(new BorderLayout(15, 0)); primary.setOpaque(false);
        JPanel status = vertical(); status.add(label("TÌM ĐỐI THỦ CỦA BẠN", 10, TEAL)); status.add(Box.createVerticalStrut(4)); status.add(lobbyStatus);
        primary.add(status, BorderLayout.CENTER);
        ((GameTheme.ActionButton) inviteButton).primary(); primary.add(inviteButton, BorderLayout.EAST); bottom.add(primary, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); actions.setOpaque(false);
        actions.add(cancelInvite); actions.add(Box.createHorizontalStrut(9));
        JButton refresh = button("Làm mới"), logout = button("Đăng xuất");
        actions.add(refresh); actions.add(Box.createHorizontalStrut(9)); actions.add(logout);
        refresh.addActionListener(e -> { send("LIST"); refreshTab(); }); logout.addActionListener(e -> logout());
        cancelInvite.setEnabled(false); bottom.add(actions, BorderLayout.CENTER);
        invitationPanel.setOpaque(false); invitationPanel.add(invitationLabel);
        ((GameTheme.ActionButton) acceptInvite).primary(); invitationPanel.add(acceptInvite); invitationPanel.add(rejectInvite);
        invitationPanel.setVisible(false); bottom.add(invitationPanel, BorderLayout.SOUTH); page.add(bottom, BorderLayout.SOUTH);
        onlineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineTable.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) challengeSelected(); }
        });
        onlineTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        onlineTable.getColumnModel().getColumn(1).setPreferredWidth(230);
        onlineTable.getColumnModel().getColumn(5).setPreferredWidth(145);
        pastTable.getColumnModel().getColumn(0).setPreferredWidth(85);
        pastTable.getColumnModel().getColumn(1).setPreferredWidth(145);
        pastTable.getColumnModel().getColumn(6).setPreferredWidth(175);
        return page;
    }

    private JPanel gameView() {
        JPanel page = base(); page.setLayout(new BorderLayout(0, 12));
        JPanel header = new JPanel(new BorderLayout(0, 9)); header.setOpaque(false);
        JPanel branding = brandBar("ĐẤU TRƯỜNG SẮC MÀU");
        branding.remove(((BorderLayout) branding.getLayout()).getLayoutComponent(BorderLayout.CENTER));
        roomLabel.setHorizontalAlignment(SwingConstants.RIGHT); branding.add(roomLabel, BorderLayout.CENTER); header.add(branding, BorderLayout.NORTH);
        JPanel duel = new JPanel(new GridBagLayout()); duel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints(); c.fill=GridBagConstraints.BOTH; c.weighty=1;
        c.gridx=0; c.weightx=1; duel.add(playerA,c);
        c.gridx=1; c.weightx=0; c.insets=new Insets(0,12,0,12); duel.add(new GameTheme.Countdown(clock, timerLabel),c);
        c.gridx=2; c.weightx=1; c.insets=new Insets(0,0,0,0); duel.add(playerB,c);
        header.add(duel, BorderLayout.CENTER); page.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 12)); center.setOpaque(false);
        GameTheme.RoundPanel board = new GameTheme.RoundPanel(20); board.setLayout(new BorderLayout(0, 7));
        board.setBorder(new EmptyBorder(13,16,12,16));
        JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false);
        heading.add(turnLabel, BorderLayout.CENTER); fillLabel.setHorizontalAlignment(SwingConstants.RIGHT); heading.add(fillLabel, BorderLayout.EAST);
        board.add(heading, BorderLayout.NORTH);
        JPanel choices = vertical();
        choices.add(label("Chọn đủ 6 màu. Bấm hai ô để đổi chỗ; bấm lại ô đã chọn để bỏ màu.", 12, MUTED));
        choices.add(Box.createVerticalStrut(8));
        JPanel row = new JPanel(new GridLayout(1, 6, 10, 0)); row.setOpaque(false);
        for (int i = 0; i < 6; i++) {
            final int pos = i; slots[i] = new GameTheme.ColorSlot(i);
            slots[i].addActionListener(e -> clickSlot(pos)); row.add(slots[i]);
        }
        row.setPreferredSize(new Dimension(1, 79)); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 79));
        choices.add(row); choices.add(Box.createVerticalStrut(8));
        JPanel colors = new JPanel(new GridLayout(1, 6, 10, 0)); colors.setOpaque(false);
        for (int i = 0; i < 6; i++) {
            final int color = i; palette[i] = new GameTheme.PaletteButton(i);
            palette[i].addActionListener(e -> chooseColor(color)); colors.add(palette[i]);
        }
        colors.setPreferredSize(new Dimension(1, 37)); colors.setMaximumSize(new Dimension(Integer.MAX_VALUE, 37));
        choices.add(colors); choices.add(Box.createVerticalStrut(10));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); actions.setOpaque(false);
        ((GameTheme.ActionButton) submit).primary();
        actions.add(submit); actions.add(Box.createHorizontalStrut(10)); actions.add(clear);
        actions.add(Box.createHorizontalStrut(10)); actions.add(remove); choices.add(actions);
        choices.add(Box.createVerticalStrut(7)); choices.add(resultLabel); board.add(choices, BorderLayout.CENTER);
        center.add(board, BorderLayout.NORTH);

        JPanel history = new JPanel(new BorderLayout(0, 8)); history.setOpaque(false);
        JPanel historyHead = new JPanel(new BorderLayout()); historyHead.setOpaque(false);
        historyHead.add(label("LỊCH SỬ LƯỢT ĐOÁN", 12, NAVY), BorderLayout.WEST);
        JLabel sync = label("Cùng nhìn lại. Cùng suy luận.", 12, MUTED); sync.setHorizontalAlignment(SwingConstants.RIGHT);
        historyHead.add(sync, BorderLayout.CENTER); history.add(historyHead, BorderLayout.NORTH);
        movesTable.putClientProperty("emptyText", "Lượt đoán đầu tiên sẽ xuất hiện tại đây.");
        movesTable.putClientProperty("arenaBackdrop", Boolean.TRUE);
        movesTable.getColumnModel().getColumn(2).setCellRenderer(new SequenceRenderer());
        movesTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        movesTable.getColumnModel().getColumn(1).setPreferredWidth(205);
        movesTable.getColumnModel().getColumn(2).setPreferredWidth(350);
        movesTable.getColumnModel().getColumn(3).setPreferredWidth(75);
        movesTable.getColumnModel().getColumn(4).setPreferredWidth(150);
        history.add(scrollTable(movesTable), BorderLayout.CENTER); center.add(history, BorderLayout.CENTER);
        page.add(center, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 4)); bottom.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); buttons.setOpaque(false);
        ((GameTheme.ActionButton) leave).danger(); ((GameTheme.ActionButton) replay).primary();
        buttons.add(leave); buttons.add(Box.createHorizontalStrut(10)); buttons.add(replay);
        buttons.add(Box.createHorizontalStrut(10)); buttons.add(back);
        bottom.add(buttons, BorderLayout.CENTER); bottom.add(replayLabel, BorderLayout.SOUTH);
        replay.setEnabled(false); back.setEnabled(false); page.add(bottom, BorderLayout.SOUTH);
        updateBoard(); return page;
    }

    private void authenticate(boolean creating) {
        if (!login.isEnabled() || registering != creating || !me.isEmpty()) return;
        JLabel status = creating ? registerStatus : loginStatus;
        String address = host.getText().trim(), user = (creating ? registerUsername : username).getText().trim();
        String pass = new String((creating ? registerPassword : password).getPassword());
        final int number;
        try { number = Integer.parseInt(port.getText().trim()); if (number < 1 || number > 65535) throw new NumberFormatException(); }
        catch (NumberFormatException e) { status.setText("Cổng phải là số từ 1 đến 65535."); return; }
        if (address.isEmpty() || user.isEmpty() || pass.isEmpty()) { status.setText("Bạn cần nhập IP, tài khoản và mật khẩu."); return; }
        if (connection != null) connection.close();
        setAuthenticationEnabled(false); status.setText("Đang kết nối đến " + address + ":" + number + "…");
        int attempt = ++generation;
        NetworkClient client = new NetworkClient(message -> SwingUtilities.invokeLater(() -> {
            if (generation == attempt) onMessage(message);
        }), error -> SwingUtilities.invokeLater(() -> { if (generation == attempt) disconnected(error); }));
        connection = client;
        String name = displayName.getText().trim();
        Thread connector = new Thread(() -> {
            try {
                client.connect(address, number);
                if (creating) client.send("REGISTER", user, pass, name); else client.send("LOGIN", user, pass);
            } catch (Exception e) {
                client.close();
                SwingUtilities.invokeLater(() -> { if (generation == attempt) disconnected("Không kết nối được. Kiểm tra IP, cổng và server đã chạy."); });
            }
        }, "client-connect");
        connector.setDaemon(true); connector.start();
    }

    void onMessage(String[] m) {
        switch (m[0]) {
            case "HELLO", "PONG" -> { }
            case "AUTH" -> {
                me = m[1]; myName = m[2]; profiles.clear(); profiles.put(me, Arrays.copyOfRange(m, 1, m.length));
                profileLabel.setText(profileText(m)); setAuthenticationEnabled(true);
                registering = false; username.setText(me); password.setText(""); registerPassword.setText("");
                cards.show(this, "lobby"); send("RANK"); send("HISTORY");
            }
            case "PROFILE" -> {
                profiles.put(m[1], Arrays.copyOfRange(m, 1, m.length));
                if (m[1].equals(me)) profileLabel.setText(profileText(m)); updatePlayers();
            }
            case "ONLINE_BEGIN" -> onlineRows.clear();
            case "ONLINE_ROW" -> onlineRows.add(Arrays.copyOfRange(m, 1, m.length));
            case "ONLINE_END" -> {
                String selected = selectedUser(); onlineModel.setRowCount(0);
                for (String[] row : onlineRows) {
                    profiles.put(row[0], Arrays.copyOf(row, 5)); onlineModel.addRow(row);
                    if (row[0].equals(selected)) onlineTable.setRowSelectionInterval(onlineModel.getRowCount() - 1, onlineModel.getRowCount() - 1);
                }
                onlineCount.setText(onlineRows.size() + " NGƯỜI ĐANG ONLINE"); updatePlayers();
            }
            case "RANK_BEGIN" -> rankModel.setRowCount(0);
            case "RANK_ROW" -> rankModel.addRow(new Object[]{rankModel.getRowCount() + 1, m[1], m[2], m[3], m[4], m[5]});
            case "RANK_END", "HISTORY_END" -> { }
            case "HISTORY_BEGIN" -> pastModel.setRowCount(0);
            case "HISTORY_ROW" -> {
                String when = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(Instant.ofEpochMilli(Long.parseLong(m[2])).atZone(ZoneId.systemDefault()));
                String result = m[5].isEmpty() ? "—" : m[5].equals(me) ? "Thắng" : "Thua";
                pastModel.addRow(new Object[]{m[1].substring(0, 8), when, m[3], m[4], result, m[7], Rules.reasonText(m[6])});
            }
            case "INVITE_SENT" -> {
                invitation = m[1]; cancelInvite.setEnabled(true); inviteButton.setEnabled(false);
                lobbyStatus.setText("Đã mời " + name(m[2]) + ". Chờ phản hồi trong " + m[3] + " giây.");
            }
            case "INVITE" -> {
                invitation = m[1]; invitationLabel.setText(name(m[2]) + " mời bạn thi đấu (" + m[3] + " giây).");
                invitationPanel.setVisible(true); acceptInvite.setEnabled(true); rejectInvite.setEnabled(true);
                inviteButton.setEnabled(false); lobbyStatus.setText("Bạn có lời mời thi đấu mới.");
                if (!active) cards.show(this, "lobby");
            }
            case "INVITE_CLOSED" -> {
                if (invitation.equals(m[1])) clearInvitation(); lobbyStatus.setText(m[2]);
            }
            case "ROOM" -> {
                room = m[1]; first = m[2]; second = m[3]; current = ""; active = true; submitted = false;
                replayRequested = false; turn = 0; selectedSlot = -1; Arrays.fill(arrangement, -1);
                movesModel.setRowCount(0); resultLabel.setText("Mỗi dãy cần đủ 6 màu, không lặp màu."); replayLabel.setText(" ");
                roomLabel.setText("PHÒNG " + room.substring(0, 8).toUpperCase(Locale.ROOT));
                timerLabel.setFont(GameTheme.font(23, true)); turnLabel.setForeground(TEAL);
                replay.setEnabled(false); back.setEnabled(false); leave.setEnabled(true);
                clearInvitation(); updatePlayers(); cards.show(this, "game"); updateBoard();
            }
            case "TURN", "TICK" -> {
                if (!room.equals(m[1]) || !active) break;
                int incomingTurn = Integer.parseInt(m[2]);
                if (incomingTurn < turn) break;
                if (incomingTurn != turn) { turn = incomingTurn; submitted = false; selectedSlot = -1; }
                current = m[3]; deadline = System.nanoTime() + Long.parseLong(m[4]) * 1_000_000L;
                turnLabel.setText("Lượt " + turn + "  ·  " + (current.equals(me) ? "Đến lượt bạn" : "Đang chờ " + name(current)));
                turnLabel.setForeground(current.equals(me) ? TEAL : MUTED);
                updateClock(); updatePlayers();
            }
            case "MOVE" -> {
                if (!room.equals(m[1])) break;
                boolean timeout = m[6].equals("TIMEOUT");
                movesModel.addRow(new Object[]{m[2], name(m[3]), m[4], timeout ? "—" : m[5] + "/6", timeout ? "Hết 15 giây" : "Đã gửi"});
                resultLabel.setText(timeout ? (m[3].equals(me) ? "Bạn" : "Đối thủ") + " đã hết thời gian."
                        : name(m[3]) + ": Có " + m[5] + "/6 vị trí đúng.");
                movesTable.scrollRectToVisible(movesTable.getCellRect(movesModel.getRowCount() - 1, 0, true));
            }
            case "RESULT" -> {
                if (!room.equals(m[1])) break;
                active = false; submitted = false;
                turnLabel.setText(m[2].equals(me) ? "BẠN CHIẾN THẮNG  ·  +1 điểm" : "BẠN THUA  ·  +0 điểm");
                turnLabel.setForeground(m[2].equals(me) ? GameTheme.GOLD : GameTheme.RED);
                resultLabel.setText(m[3].equals("SOLVED") ? name(m[2]) + " đã đoán đúng cả 6 vị trí."
                        : "Trận kết thúc: " + (m[3].equals("LEFT") ? "một người chơi đã rời phòng." : "một người chơi mất kết nối."));
                timerLabel.setText("XONG"); timerLabel.setFont(GameTheme.font(15, true)); clock.setValue(0);
                replay.setEnabled(true); back.setEnabled(true); leave.setEnabled(false);
                updateBoard(); updatePlayers(); send("RANK"); send("HISTORY");
            }
            case "REMATCH_STATUS" -> {
                if (!room.equals(m[1])) break;
                if (m[2].equals(me)) { replayRequested = true; replay.setEnabled(false); replayLabel.setText("Đang chờ đối thủ đồng ý chơi lại…"); }
                else replayLabel.setText("Đối thủ muốn chơi lại. Bấm Chơi lại để đồng ý.");
            }
            case "REMATCH_UNAVAILABLE" -> {
                if (room.equals(m[1])) { replay.setEnabled(false); replayLabel.setText(m[2]); }
            }
            case "LOBBY" -> {
                room = ""; active = false; current = ""; cards.show(this, "lobby");
                lobbyStatus.setText("Chọn một người đang rỗi để mời thi đấu."); send("LIST"); refreshTab();
            }
            case "ERROR" -> {
                if (m[1].equals("LOGIN") || m[1].equals("REGISTER")) {
                    (m[1].equals("REGISTER") ? registerStatus : loginStatus).setText(m[2]); setAuthenticationEnabled(true);
                } else if (m[1].equals("GUESS")) { submitted = false; resultLabel.setText(m[2]); updateBoard(); }
                else if (m[1].equals("REMATCH")) { replayLabel.setText(m[2]); }
                else { lobbyStatus.setText(m[2]); if (!room.isEmpty()) resultLabel.setText(m[2]); }
            }
            default -> { }
        }
    }

    private static String profileText(String[] m) {
        return "Xin chào " + m[2] + " (" + m[1] + ")  ·  " + m[3] + " điểm  ·  " + m[4] + " trận thắng  ·  " + m[5] + " trận đã chơi";
    }

    private String name(String user) { String[] p = profiles.get(user); return p == null ? user : p[1] + " (" + user + ")"; }

    private void updatePlayers() { updatePlayer(playerA, first); updatePlayer(playerB, second); }

    private void updatePlayer(GameTheme.PlayerCard card, String user) {
        if (user.isEmpty()) return;
        String[] p = profiles.get(user);
        card.update(user, p == null ? user : p[1], p == null ? "0" : p[2], p == null ? "0" : p[3],
                user.equals(me), user.equals(current), active);
    }

    private String selectedUser() {
        int row = onlineTable.getSelectedRow();
        return row < 0 ? "" : onlineModel.getValueAt(onlineTable.convertRowIndexToModel(row), 0).toString();
    }

    private void challengeSelected() {
        String target = selectedUser();
        if (target.isEmpty() || target.equals(me)) { lobbyStatus.setText("Hãy chọn một người chơi khác trong danh sách."); return; }
        send("CHALLENGE", target);
    }

    private void respond(String answer) {
        if (invitation.isEmpty()) return;
        acceptInvite.setEnabled(false); rejectInvite.setEnabled(false); send("RESPOND", invitation, answer);
    }

    private void clearInvitation() {
        invitation = ""; invitationPanel.setVisible(false); cancelInvite.setEnabled(false); inviteButton.setEnabled(true);
    }

    private void refreshTab() {
        if (me.isEmpty()) return;
        if (tabs.getSelectedIndex() == 1) send("RANK"); else if (tabs.getSelectedIndex() == 2) send("HISTORY");
    }

    private boolean myTurn() { return active && current.equals(me) && !submitted && System.nanoTime() < deadline; }

    void chooseColor(int color) {
        if (!myTurn()) return;
        for (int used : arrangement) if (used == color) return;
        for (int i = 0; i < 6; i++) if (arrangement[i] < 0) { arrangement[i] = color; break; }
        selectedSlot = -1; updateBoard();
    }

    void clickSlot(int position) {
        if (!myTurn()) return;
        if (selectedSlot == position) { arrangement[position] = -1; selectedSlot = -1; }
        else if (selectedSlot >= 0) {
            int temp = arrangement[position]; arrangement[position] = arrangement[selectedSlot]; arrangement[selectedSlot] = temp; selectedSlot = -1;
        } else selectedSlot = position;
        updateBoard();
    }

    private void submitGuess() {
        if (!myTurn()) return;
        StringBuilder guess = new StringBuilder();
        for (int color : arrangement) { if (color < 0) return; guess.append(color); }
        if (!Rules.validGuess(guess.toString())) return;
        send("GUESS", room, "" + turn, guess.toString()); submitted = true;
        turnLabel.setText("Đã gửi dự đoán  ·  Chờ server xác nhận"); updateBoard();
    }

    private void updateBoard() {
        boolean enabled = myTurn(), full = true;
        int filled = 0;
        for (int i = 0; i < 6; i++) {
            if (slots[i] == null) continue;
            int color = arrangement[i]; if (color < 0) full = false; else filled++;
            ((GameTheme.ColorSlot) slots[i]).state(color, selectedSlot == i, enabled);
            boolean used = false; for (int item : arrangement) if (item == i) used = true;
            ((GameTheme.PaletteButton) palette[i]).setUsed(used);
            palette[i].setEnabled(enabled && !used);
        }
        fillLabel.setText(filled + " / 6 màu đã chọn"); fillLabel.setForeground(full ? TEAL : MUTED);
        submit.setEnabled(enabled && full); clear.setEnabled(enabled); remove.setEnabled(enabled && selectedSlot >= 0);
    }

    private void updateClock() {
        if (!active) return;
        long ms = Math.max(0, (deadline - System.nanoTime()) / 1_000_000L);
        timerLabel.setText(((ms + 999) / 1000) + " s"); timerLabel.setForeground(ms <= 5000 ? GameTheme.RED : NAVY);
        clock.setValue((int) Math.min(Rules.TURN_MILLIS, ms)); updateBoard();
    }

    private void leaveRoom() {
        if (JOptionPane.showConfirmDialog(this, "Thoát khi trận đang diễn ra sẽ bị tính thua. Bạn muốn thoát?", "Xác nhận thoát", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
            send("LEAVE", room);
    }

    private void send(String command, String... fields) { if (connection != null) connection.send(command, fields); }

    private void disconnected(String message) {
        generation++; if (connection != null) connection.close(); connection = null;
        me = ""; active = false; room = ""; clearInvitation(); cards.show(this, registering ? "register" : "login");
        setAuthenticationEnabled(true); (registering ? registerStatus : loginStatus).setText(message);
    }

    private void logout() { disconnected("Đã đăng xuất. Bạn có thể đăng nhập tài khoản khác."); }

    public void requestClose(JFrame frame) {
        if (active && JOptionPane.showConfirmDialog(this, "Đóng ứng dụng khi đang chơi sẽ bị tính thua. Tiếp tục?", "Xác nhận", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        shutdown(); frame.dispose();
    }

    void shutdown() { generation++; uiTimer.stop(); if (connection != null) connection.close(); }

    private static JPanel base() { JPanel p = new GameTheme.Backdrop(); p.setBorder(new EmptyBorder(20, 24, 20, 24)); return p; }
    private static JPanel vertical() {
        JPanel p = new JPanel() {
            @Override protected void addImpl(Component child, Object constraints, int index) {
                if (child instanceof JComponent component) component.setAlignmentX(Component.LEFT_ALIGNMENT);
                super.addImpl(child, constraints, index);
            }
        };
        p.setOpaque(false); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p;
    }
    private static JLabel label(String text, int size, Color color) {
        JLabel l = new JLabel(text.isEmpty() ? " " : text);
        l.setFont(GameTheme.font(size, size >= 17)); l.setForeground(color); l.putClientProperty("html.disable", true);
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, size + 12)); return l;
    }
    private static JButton button(String text) {
        return new GameTheme.ActionButton(text);
    }
    private static DefaultTableModel model(String... names) { return new DefaultTableModel(names, 0) { @Override public boolean isCellEditable(int row, int col) { return false; } }; }
    private static JScrollPane scrollTable(JTable table) {
        return GameTheme.scroll(table);
    }
    private static JTable table(DefaultTableModel m) {
        return GameTheme.table(m);
    }

    private static final class SequenceRenderer extends JPanel implements TableCellRenderer {
        private String sequence = "";
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            sequence = value == null ? "" : value.toString(); setToolTipText(Rules.colorText(sequence));
            setBackground(selected ? table.getSelectionBackground() : row % 2 == 0 ? GameTheme.PANEL : new Color(22, 34, 52)); return this;
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!Rules.validGuess(sequence)) { g.setColor(MUTED); g.drawString("Không gửi dự đoán", 12, 22); return; }
            Graphics2D g2 = GameTheme.graphics(g);
            int width = Math.max(18, Math.min(42, (getWidth() - 20) / 6));
            for (int i = 0; i < 6; i++) {
                int color = sequence.charAt(i) - '0', x = 15 + i * width;
                GameTheme.orb(g2, color, x, (getHeight()-24)/2f, 24);
                g2.setFont(GameTheme.font(10, true)); g2.setColor(color == 3 || color == 5 ? GameTheme.BACKGROUND : Color.WHITE);
                g2.drawString("" + (color + 1), x + 9, getHeight()/2+4);
            }
            g2.dispose();
        }
    }

    public static void launch(String host, int port) {
        SwingUtilities.invokeLater(() -> {
            GameTheme.install();
            JFrame frame = new JFrame("Color Duel — Game đoán dãy màu đối kháng");
            GameClient client = new GameClient(); client.setServer(host, port); frame.setContentPane(client);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { client.requestClose(frame); } });
            frame.setMinimumSize(new Dimension(1040, 720));
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(Math.min(1280, screen.width-50), Math.min(880, screen.height-65));
            frame.setLocationRelativeTo(null); frame.setVisible(true);
        });
    }
}
