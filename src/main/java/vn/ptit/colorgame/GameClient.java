package vn.ptit.colorgame;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public final class GameClient extends JPanel {
    static { GameTheme.install(); }
    private final AudioManager audio;
    private final List<JCheckBox> soundSettings=new ArrayList<>(), musicSettings=new ArrayList<>();
    private final List<JButton> settingsButtons=new ArrayList<>();
    private final JPopupMenu settingsMenu=new JPopupMenu();
    private final GameTheme.RoundPanel settingsPanel=new GameTheme.RoundPanel(16);
    private final JButton soundPreview=button("Thử hiệu ứng"), musicPreview=button("Nghe nhạc nền");
    private final JLabel audioStatus=new GameTheme.Notice("Đang chuẩn bị âm thanh…");
    private JCheckBox soundToggle,musicToggle,motionToggle;
    private JSlider effectsVolume,musicVolume;
    private final javax.swing.Timer settingsFeedback=new javax.swing.Timer(150,e -> refreshAudioStatus());
    private String audioTurn="";
    private int warningSecond=6;
    private final Set<String> audioTimeouts=new HashSet<>();
    private final CardLayout cards = new CardLayout();
    private final JTextField host = new JTextField("127.0.0.1"), port = new JTextField("5000");
    private final JTextField username = new JTextField(), registerUsername = new JTextField(), displayName = new JTextField();
    private final JPasswordField password = new JPasswordField(), registerPassword = new JPasswordField();
    private final JButton login = button("Đăng nhập"), register = button("Đăng ký tài khoản");
    private final JButton showRegister = button("Chưa có tài khoản? Đăng ký"), showLogin = button("Đã có tài khoản? Đăng nhập");
    private final JLabel loginStatus = new GameTheme.Notice("Nhập IP server để kết nối.");
    private final JLabel registerStatus = new GameTheme.Notice("Mật khẩu cần ít nhất 6 ký tự.");
    private final GameTheme.Metric myPoints = new GameTheme.Metric("Điểm tích lũy"), myWins = new GameTheme.Metric("Trận thắng"), myPlayed = new GameTheme.Metric("Trận đã chơi");
    private final JLabel profileLabel = label("", 14, GameTheme.TEXT);
    private final JLabel lobbyStatus = new GameTheme.Notice("Chọn một đối thủ sẵn sàng trong danh sách để gửi lời mời.");
    private final JButton inviteButton = button("Mời thi đấu"), cancelInvite = button("Hủy lời mời");
    private final JButton acceptInvite = button("Chấp nhận"), rejectInvite = button("Từ chối");
    private final GameTheme.RoundPanel invitationPanel = new GameTheme.RoundPanel(20);
    private final JLabel invitationLabel = label("", 17, GameTheme.TEXT), invitationTitle = label("Lời mời thi đấu", 13, GameTheme.ACCENT);
    private final JLabel invitationAvatar = new JLabel(), invitationTimer = label("30 s", 19, GameTheme.GOLD);
    private final JLabel invitationDetail = new GameTheme.Notice(" ");
    private final JProgressBar invitationProgress = new JProgressBar(0, 1000);
    private final JPanel invitationActions = new JPanel(new CardLayout());
    private final LobbyStyle.OpponentCard opponentCard = new LobbyStyle.OpponentCard(inviteButton);
    private final JTextField opponentSearch = new JTextField();
    private final JLabel availableCount = label("Đang chờ người chơi kết nối", 12, GameTheme.MUTED);
    private final DefaultTableModel onlineModel = model("Tài khoản", "Tên người chơi", "Điểm", "Trận thắng", "Đã chơi", "Trạng thái");
    private final DefaultTableModel rankModel = model("Hạng", "Tài khoản", "Tên người chơi", "Điểm", "Trận thắng", "Đã chơi");
    private final DefaultTableModel pastModel = model("Mã trận", "Thời gian", "Người chơi 1", "Người chơi 2", "Kết quả", "Lượt", "Lý do");
    private final JTable onlineTable = table(onlineModel), rankTable = table(rankModel), pastTable = table(pastModel);
    private final TableRowSorter<DefaultTableModel> opponentSorter = new TableRowSorter<>(onlineModel);
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel roomLabel = label("Phòng chơi", 12, GameTheme.MUTED);
    private final GameTheme.PlayerCard playerA = new GameTheme.PlayerCard(), playerB = new GameTheme.PlayerCard();
    private final JLabel turnLabel = label("Đang chờ đối thủ", 17, GameTheme.ACCENT), timerLabel = label(Rules.TURN_MILLIS / 1000 + " s", 23, GameTheme.TEXT);
    private final JLabel fillLabel = label("0 / 6 màu", 12, GameTheme.MUTED);
    private final GameTheme.Badge onlineCount = new GameTheme.Badge("ĐANG KẾT NỐI", GameTheme.ACCENT);
    private final JLabel resultLabel = new GameTheme.Notice(" "), replayLabel = label("", 13, GameTheme.MUTED);
    private final JProgressBar clock = new JProgressBar(0, Rules.TURN_MILLIS);
    private final JButton[] palette = new JButton[6], slots = new JButton[6];
    private final int[] arrangement = {-1, -1, -1, -1, -1, -1};
    private final JButton submit = button("Gửi dự đoán"), clear = button("Xóa tất cả"), remove = button("Bỏ ô đang chọn");
    private final JButton leave = button("Thoát phòng"), replay = button("Chơi lại"), back = button("Trở về danh sách online");
    private final DefaultTableModel movesModel = model("Lượt", "Người chơi", "Dãy dự đoán", "Đúng", "Trạng thái");
    private final JTable movesTable = table(movesModel);
    private final Map<String, String[]> profiles = new HashMap<>();
    private final List<String[]> onlineRows = new ArrayList<>();
    private final javax.swing.Timer uiTimer;
    private GameTheme.RoundPanel guessBoard;
    private final JPanel arenaCards = new JPanel(new CardLayout());
    private final GameTheme.ResultPanel resultScreen = new GameTheme.ResultPanel();
    private final GameTheme.Celebration celebration = new GameTheme.Celebration();
    private NetworkClient connection;
    private String me = "", myName = "", room = "", first = "", second = "", current = "", invitation = "";
    private int generation, turn, selectedSlot = -1;
    private long deadline;
    private long invitationDeadline, invitationDuration;
    private boolean invitationOutgoing, invitationResponding, challengePending;
    private boolean active, submitted, replayRequested, registering;

    public GameClient() { this(new AudioManager()); }
    GameClient(AudioManager audio) {
        this.audio=audio;
        buildSettings();
        setLayout(cards); setBackground(GameTheme.BACKGROUND);
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
        cancelInvite.addActionListener(e -> {
            if (!invitation.isEmpty() && invitationOutgoing && !invitationResponding) {
                invitationResponding = true; cancelInvite.setEnabled(false);
                invitationDetail.setText("Đang hủy lời mời…"); send("CANCEL_INVITE", invitation);
            }
        });
        acceptInvite.addActionListener(e -> respond("YES")); rejectInvite.addActionListener(e -> respond("NO"));
        submit.addActionListener(e -> submitGuess());
        clear.addActionListener(e -> { Arrays.fill(arrangement, -1); selectedSlot = -1; updateBoard(); });
        remove.addActionListener(e -> { if (selectedSlot >= 0) arrangement[selectedSlot] = -1; selectedSlot = -1; updateBoard(); });
        leave.addActionListener(e -> leaveRoom());
        replay.addActionListener(e -> { send("REMATCH", room); replayRequested = true; replay.setEnabled(false); replayLabel.setText("Đang chờ đối thủ đồng ý chơi lại…"); });
        back.addActionListener(e -> send("BACK", room));
        tabs.addChangeListener(e -> refreshTab());
        wireClickSounds(this);
        addHierarchyListener(e -> { if((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED)!=0) audio.setSuspended(!isShowing()); });
        uiTimer = new javax.swing.Timer(100, e -> { updateClock(); updateInvitationClock(); }); uiTimer.start(); GameTheme.registerRoot(this);
    }

    public void setServer(String address, int number) { host.setText(address); port.setText("" + number); }

    @Override public void paint(Graphics g) {
        Graphics2D p = GameTheme.graphics(g); super.paint(p); celebration.paint(p,getWidth(),getHeight()); p.dispose();
    }

    private JPanel authView(boolean creating) {
        JPanel page = base(); page.setLayout(new BorderLayout(0, 16));
        page.add(brandBar("Đấu trí bằng sắc màu"), BorderLayout.NORTH);
        GameTheme.ArtPanel story=new GameTheme.ArtPanel(true); story.setLayout(new BorderLayout()); story.setBorder(new EmptyBorder(28,28,28,28));
        JPanel hero=vertical(); hero.add(new GameTheme.Badge("NEON ARCADE ARENA  /  1 VS 1",GameTheme.ACCENT)); hero.add(Box.createVerticalStrut(20));
        JLabel headline=label("SÁU SẮC MÀU.",40,GameTheme.TEXT); headline.setFont(GameTheme.display(40)); hero.add(headline);
        JLabel challenge=label("MỘT TRẬN ĐẤU TRÍ.",32,GameTheme.ACCENT); challenge.setFont(GameTheme.display(32)); hero.add(challenge);
        hero.add(Box.createVerticalStrut(12)); hero.add(label("Thách đấu bạn bè. Giải mã dãy màu bí mật.",14,GameTheme.TEXT)); story.add(hero,BorderLayout.NORTH);
        JPanel facts=new JPanel(new GridLayout(1,2,24,0)); facts.setOpaque(false);
        GameTheme.Metric colors=new GameTheme.Metric("Màu trong mỗi dãy"); colors.value("06");
        GameTheme.Metric seconds=new GameTheme.Metric("Giây cho mỗi lượt"); seconds.value(""+Rules.TURN_MILLIS/1000); facts.add(colors); facts.add(seconds); story.add(facts,BorderLayout.SOUTH);

        GameTheme.RoundPanel form = new GameTheme.RoundPanel(16); form.setLayout(new BorderLayout());
        form.setBorder(new EmptyBorder(20,24,16,24)); form.putClientProperty("maxHeight",creating ? 560 : 500);
        JPanel fields = vertical(); fields.add(new GameTheme.Badge(creating ? "NEW CHALLENGER" : "PLAYER ACCESS",GameTheme.ACCENT)); fields.add(Box.createVerticalStrut(12));
        fields.add(label(creating ? "Tạo người chơi" : "Vào đấu trường", 30, GameTheme.TEXT));
        fields.add(Box.createVerticalStrut(8));
        fields.add(label(creating ? "Tạo tài khoản để bắt đầu cuộc đấu." : "Sẵn sàng cho cuộc đấu tiếp theo?", 14, GameTheme.MUTED));
        fields.add(Box.createVerticalStrut(12));
        JTextField addressField = creating ? new JTextField(host.getDocument(), null, 0) : host;
        JTextField portField = creating ? new JTextField(port.getDocument(), null, 0) : port;
        JPanel address = new JPanel(new GridBagLayout()); address.setOpaque(false);
        GridBagConstraints a = new GridBagConstraints(); a.fill = GridBagConstraints.HORIZONTAL;
        a.gridx = 0; a.weightx = .75; a.insets = new Insets(0, 0, 0, 12);
        address.add(fieldGroup("Địa chỉ server", addressField), a);
        a.gridx = 1; a.weightx = .25; a.insets = new Insets(0, 0, 0, 0); address.add(fieldGroup("Cổng", portField), a);
        address.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58)); fields.add(address); fields.add(Box.createVerticalStrut(6));
        fields.add(fieldGroup("Tài khoản", creating ? registerUsername : username)); fields.add(Box.createVerticalStrut(6));
        fields.add(fieldGroup("Mật khẩu", creating ? registerPassword : password));
        if (creating) { fields.add(Box.createVerticalStrut(6)); fields.add(fieldGroup("Tên hiển thị", displayName)); }
        fields.add(Box.createVerticalStrut(12));
        JButton action = creating ? register : login, navigation = creating ? showLogin : showRegister;
        ((GameTheme.ActionButton) action).primary(); action.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        ((GameTheme.ActionButton) navigation).secondary(); navigation.setMaximumSize(new Dimension(Integer.MAX_VALUE,40));
        navigation.setText(creating ? "Trở về đăng nhập" : "Đăng ký người chơi mới");
        fields.add(action); fields.add(Box.createVerticalStrut(8)); fields.add(navigation); fields.add(Box.createVerticalStrut(6));
        JLabel status = creating ? registerStatus : loginStatus;
        status.setFont(GameTheme.font(13, false)); status.setPreferredSize(new Dimension(1,28)); status.setMaximumSize(new Dimension(Integer.MAX_VALUE,28)); fields.add(status);
        fields.setMinimumSize(new Dimension(0,0)); JScrollPane formScroll = GameTheme.scroll(fields); formScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setOpaque(false); formScroll.getViewport().setOpaque(false);
        form.add(formScroll, BorderLayout.CENTER);
        GameTheme.ResponsiveSplit columns = new GameTheme.ResponsiveSplit(story, form, 420, 870, 0, true);
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
        login.setText(enabled ? "Đăng nhập" : "Đang kết nối…"); register.setText(enabled ? "Đăng ký tài khoản" : "Đang tạo tài khoản…");
        showRegister.setEnabled(enabled); showLogin.setEnabled(enabled);
    }

    private static JPanel fieldGroup(String caption, JTextField field) {
        GameTheme.field(field); JPanel group = new JPanel(new BorderLayout(0, 4)); group.setOpaque(false);
        group.add(label(caption, 12, GameTheme.MUTED), BorderLayout.NORTH); group.add(field, BorderLayout.CENTER);
        group.setPreferredSize(new Dimension(230, 58)); group.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58)); return group;
    }

    private JPanel brandBar(String caption) {
        JPanel bar=new JPanel(new BorderLayout(12,0)); bar.setOpaque(false);
        JLabel brand=label("COLOR DUEL",20,GameTheme.TEXT); brand.setFont(GameTheme.display(20)); brand.setIcon(new GameTheme.Logo()); brand.setIconTextGap(8); bar.add(brand,BorderLayout.WEST);
        bar.add(label(caption,11,GameTheme.MUTED),BorderLayout.CENTER);
        GameTheme.ActionButton settings=new GameTheme.ActionButton("Cài đặt").secondary();
        settings.setName("settings-button"); settings.setFont(GameTheme.font(12,true)); settings.setBorder(new EmptyBorder(5,14,5,14));
        settings.getAccessibleContext().setAccessibleName("Mở cài đặt âm thanh và hiệu ứng"); settings.addActionListener(e -> showSettings(settings)); settingsButtons.add(settings);
        bar.add(settings,BorderLayout.EAST); bar.setPreferredSize(new Dimension(1,32)); return bar;
    }
    private static JCheckBox setting(String caption,String name,boolean selected) {
        JCheckBox box=new JCheckBox(caption,selected); box.setName(name); box.setOpaque(false); box.setFont(GameTheme.font(14,true)); box.setForeground(GameTheme.TEXT);
        box.setBorder(new EmptyBorder(4,0,4,0)); box.setMaximumSize(new Dimension(Integer.MAX_VALUE,32)); box.setAlignmentX(LEFT_ALIGNMENT); return box;
    }
    private void buildSettings() {
        settingsPanel.setName("client-settings"); settingsPanel.setLayout(new BoxLayout(settingsPanel,BoxLayout.Y_AXIS)); settingsPanel.setBorder(new EmptyBorder(16,16,16,16));
        settingsPanel.setPreferredSize(new Dimension(340,410));
        JLabel title=label("CÀI ĐẶT",20,GameTheme.TEXT); title.setAlignmentX(LEFT_ALIGNMENT); settingsPanel.add(title); settingsPanel.add(Box.createVerticalStrut(6));
        JLabel hint=label("Âm thanh và hiệu ứng của cửa sổ này",12,GameTheme.MUTED); hint.setAlignmentX(LEFT_ALIGNMENT); settingsPanel.add(hint); settingsPanel.add(Box.createVerticalStrut(12));
        soundToggle=setting("Hiệu ứng âm thanh","sound-toggle",audio.effectsEnabled()); musicToggle=setting("Nhạc nền","music-toggle",audio.musicEnabled());
        motionToggle=setting("Giảm hiệu ứng","motion-toggle",GameTheme.isReducedMotion()); soundSettings.add(soundToggle); musicSettings.add(musicToggle);
        effectsVolume=new JSlider(0,100,audio.effectsVolume()); effectsVolume.setName("effects-volume");
        musicVolume=new JSlider(0,100,audio.musicVolume()); musicVolume.setName("music-volume");
        settingsPanel.add(soundToggle); settingsPanel.add(volumeGroup("Âm lượng hiệu ứng",effectsVolume)); settingsPanel.add(Box.createVerticalStrut(6));
        settingsPanel.add(musicToggle); settingsPanel.add(volumeGroup("Âm lượng nhạc nền",musicVolume)); settingsPanel.add(Box.createVerticalStrut(8)); settingsPanel.add(motionToggle);
        soundToggle.addActionListener(e -> { audio.setEffectsEnabled(soundToggle.isSelected()); syncAudioSettings(); scheduleAudioStatus(); });
        musicToggle.addActionListener(e -> { audio.setMusicEnabled(musicToggle.isSelected()); syncAudioSettings(); scheduleAudioStatus(); });
        motionToggle.addActionListener(e -> GameTheme.setReducedMotion(motionToggle.isSelected()));
        effectsVolume.addChangeListener(e -> { audio.setEffectsVolume(effectsVolume.getValue()); syncAudioSettings(); if(!effectsVolume.getValueIsAdjusting()) scheduleAudioStatus(); });
        musicVolume.addChangeListener(e -> { audio.setMusicVolume(musicVolume.getValue()); syncAudioSettings(); if(!musicVolume.getValueIsAdjusting()) scheduleAudioStatus(); });
        settingsPanel.add(Box.createVerticalStrut(10)); JPanel previews=new JPanel(new GridLayout(1,2,8,0)); previews.setOpaque(false); previews.setAlignmentX(LEFT_ALIGNMENT);
        ((GameTheme.ActionButton)soundPreview).secondary(); ((GameTheme.ActionButton)musicPreview).primary(); previews.add(soundPreview); previews.add(musicPreview); previews.setMaximumSize(new Dimension(Integer.MAX_VALUE,38)); settingsPanel.add(previews);
        soundPreview.setName("sound-preview"); musicPreview.setName("music-preview");
        soundPreview.addActionListener(e -> { audio.play(AudioManager.Sound.SUBMIT); scheduleAudioStatus(); });
        musicPreview.setToolTipText("Nghe nhạc của màn hình hiện tại; tự chuyển khi vào hoặc kết thúc trận");
        musicPreview.addActionListener(e -> { audio.setMusicEnabled(true); syncAudioSettings(); scheduleAudioStatus(); });
        settingsPanel.add(Box.createVerticalStrut(12)); audioStatus.setAlignmentX(LEFT_ALIGNMENT); audioStatus.setFont(GameTheme.font(12,false)); settingsPanel.add(audioStatus);
        settingsMenu.setBackground(GameTheme.PANEL); settingsMenu.setBorder(new LineBorder(GameTheme.LINE)); settingsMenu.add(settingsPanel); settingsFeedback.setRepeats(false); syncAudioSettings();
    }
    private void setMusicScene(AudioManager.MusicScene scene) {
        audio.setMusicScene(scene); scheduleAudioStatus();
    }
    private JPanel volumeGroup(String caption,JSlider slider) {
        GameTheme.slider(slider); slider.getAccessibleContext().setAccessibleName(caption);
        JPanel group=new JPanel(new BorderLayout(0,0)); group.setOpaque(false); group.setAlignmentX(LEFT_ALIGNMENT); group.setMaximumSize(new Dimension(Integer.MAX_VALUE,52));
        JPanel heading=new JPanel(new BorderLayout()); heading.setOpaque(false); heading.add(label(caption,12,GameTheme.MUTED),BorderLayout.WEST);
        JLabel percent=label(slider.getValue()+"%",12,GameTheme.ACCENT); percent.setFont(GameTheme.mono(12)); heading.add(percent,BorderLayout.EAST);
        slider.addChangeListener(e -> percent.setText(slider.getValue()+"%")); group.add(heading,BorderLayout.NORTH); group.add(slider,BorderLayout.CENTER); return group;
    }
    JPanel settingsComponent() { return settingsPanel; }
    private void showSettings(JButton button) {
        syncAudioSettings(); motionToggle.setSelected(GameTheme.isReducedMotion()); scheduleAudioStatus();
        if(button.isShowing()) settingsMenu.show(button,button.getWidth()-settingsPanel.getPreferredSize().width,button.getHeight()+6);
    }
    private void syncAudioSettings() {
        for(JCheckBox box:soundSettings) box.setSelected(audio.effectsEnabled()); for(JCheckBox box:musicSettings) box.setSelected(audio.musicEnabled());
        soundPreview.setEnabled(audio.effectsEnabled() && audio.effectsVolume()>0); musicPreview.setEnabled(audio.musicVolume()>0);
    }
    private void scheduleAudioStatus() { audioStatus.setText("Đang cập nhật âm thanh…"); settingsFeedback.restart(); }
    private void refreshAudioStatus() {
        audio.report().whenComplete((report,error) -> SwingUtilities.invokeLater(() -> {
            if(error!=null || report.loaded()==0) { audioStatus.setForeground(GameTheme.RED); audioStatus.setText("Chưa mở được âm thanh. Kiểm tra thiết bị phát của Windows."); return; }
            audioStatus.setForeground(GameTheme.MUTED);
            String effects=audio.effectsEnabled() && audio.effectsVolume()>0 ? "Hiệu ứng bật" : "Hiệu ứng tắt";
            String music=report.musicRunning() ? (audio.musicScene()==AudioManager.MusicScene.BATTLE ? "Nhạc thi đấu đang phát" : "Nhạc sảnh đang phát") : !audio.musicEnabled() || audio.musicVolume()==0 ? "Nhạc nền tắt" : "Nhạc nền chưa phát được";
            audioStatus.setText(effects+" · "+music);
        }));
    }
    private void wireClickSounds(Container parent) {
        for(Component component:parent.getComponents()) {
            if(component instanceof JButton button && !(button instanceof GameTheme.ColorSlot) && !(button instanceof GameTheme.PaletteButton)
                    && button!=submit && button!=acceptInvite && button!=rejectInvite) button.addActionListener(e -> audio.play(AudioManager.Sound.CLICK));
            if(component instanceof Container children) wireClickSounds(children);
        }
    }

    private JPanel lobbyView() {
        JPanel page = base(); page.setLayout(new BorderLayout(0, 12));
        JPanel top=new JPanel(new BorderLayout(0,8)); top.setOpaque(false); top.add(brandBar("Sảnh thách đấu"),BorderLayout.NORTH);
        JPanel heading=new GameTheme.ArenaBanner(); heading.setLayout(new GridBagLayout()); heading.setBorder(new EmptyBorder(12,16,12,16));
        JPanel copy=vertical(); JLabel arenaTitle=label("MATCHMAKING",22,GameTheme.TEXT); arenaTitle.setFont(GameTheme.display(22)); copy.add(arenaTitle); copy.add(Box.createVerticalStrut(4)); profileLabel.setFont(GameTheme.font(12,false)); copy.add(profileLabel);
        GridBagConstraints header=new GridBagConstraints(); header.gridx=0; header.weightx=1; header.fill=GridBagConstraints.HORIZONTAL; heading.add(copy,header);
        JPanel personal=new JPanel(new GridLayout(1,3,12,0)); personal.setOpaque(false); personal.add(myPoints); personal.add(myWins); personal.add(myPlayed); personal.setPreferredSize(new Dimension(268,56));
        header.gridx=1; header.weightx=0; header.insets=new Insets(0,12,0,12); heading.add(personal,header);
        JPanel session=new JPanel(new BorderLayout(0,4)); session.setOpaque(false); session.add(onlineCount,BorderLayout.NORTH);
        GameTheme.ActionButton logout=new GameTheme.ActionButton("Đăng xuất").quiet(); logout.setBorder(new EmptyBorder(6,12,6,12)); logout.addActionListener(e -> logout()); session.add(logout,BorderLayout.SOUTH);
        header.gridx=2; header.insets=new Insets(0,0,0,0); heading.add(session,header); top.add(heading,BorderLayout.CENTER);
        buildInvitationCard(); top.add(invitationPanel,BorderLayout.SOUTH); page.add(top,BorderLayout.NORTH);
        onlineTable.putClientProperty("emptyText", "Chờ người chơi khác kết nối vào cùng server.");
        rankTable.putClientProperty("emptyText", "Hoàn thành trận đấu để tích lũy điểm.");
        pastTable.putClientProperty("emptyText", "Các trận hoàn thành của bạn sẽ xuất hiện tại đây.");

        GameTheme.RoundPanel players = new GameTheme.RoundPanel(16); players.setLayout(new BorderLayout(0, 16));
        players.setBorder(new EmptyBorder(12, 16, 12, 16));
        JPanel toolbar = new JPanel(new BorderLayout(16, 0)); toolbar.setOpaque(false);
        JPanel title = vertical(); title.add(label("Đối thủ trực tuyến", 20, GameTheme.TEXT));
        title.add(Box.createVerticalStrut(6)); title.add(availableCount); toolbar.add(title, BorderLayout.CENTER);
        JPanel tools = new JPanel(new BorderLayout(8, 0)); tools.setOpaque(false);
        GameTheme.field(opponentSearch); opponentSearch.setPreferredSize(new Dimension(140, 36));
        opponentSearch.setToolTipText("Tìm theo tên hiển thị hoặc tài khoản");
        opponentSearch.getAccessibleContext().setAccessibleName("Tìm người chơi theo tên hoặc tài khoản");
        JPanel search = new JPanel(new BorderLayout(0, 6)); search.setOpaque(false);
        search.add(label("Tìm người chơi", 12, GameTheme.MUTED), BorderLayout.NORTH); search.add(opponentSearch, BorderLayout.CENTER);
        tools.add(search, BorderLayout.CENTER);
        JButton refresh = button("Làm mới"); refresh.setFont(GameTheme.font(12, true)); refresh.setBorder(new EmptyBorder(8, 12, 8, 12));
        refresh.addActionListener(e -> { send("LIST"); refreshTab(); }); tools.add(refresh, BorderLayout.EAST);
        toolbar.add(tools, BorderLayout.EAST); players.add(toolbar, BorderLayout.NORTH);
        players.add(scrollTable(onlineTable), BorderLayout.CENTER);
        JComboBox<String> sorting=new JComboBox<>(new String[]{"Thứ tự server","Tài khoản","Tên người chơi","Điểm cao nhất","Thắng nhiều nhất","Số trận đã chơi","Trạng thái"});
        sorting.setName("opponent-sort"); sorting.setFont(GameTheme.font(11,false)); sorting.setBackground(GameTheme.INPUT); sorting.setForeground(GameTheme.TEXT);
        sorting.addActionListener(e -> { int index=sorting.getSelectedIndex(); opponentSorter.setSortKeys(index==0 ? null : List.of(new RowSorter.SortKey(index-1,index>=3 && index<=5 ? SortOrder.DESCENDING : SortOrder.ASCENDING))); });
        JPanel sortBar=new JPanel(new BorderLayout(12,0)); sortBar.setOpaque(false); sortBar.add(label("Chọn một người chơi sẵn sàng",11,GameTheme.MUTED),BorderLayout.CENTER); sortBar.add(sorting,BorderLayout.EAST); players.add(sortBar,BorderLayout.SOUTH);
        tabs.addTab("Thách đấu", players);
        tabs.addTab("Bảng xếp hạng", tableSection("Bảng xếp hạng", "Điểm được cộng khi bạn thắng một trận đấu.", rankTable));
        tabs.addTab("Các trận của tôi", tableSection("Các trận của tôi", "Kết quả và lịch sử thi đấu từ tài khoản của bạn.", pastTable));
        GameTheme.tabs(tabs);

        JPanel sidebar = vertical(); sidebar.add(opponentCard); sidebar.add(Box.createVerticalStrut(16));

        JPanel notice = new GameTheme.RoundPanel(16); notice.setLayout(new BoxLayout(notice,BoxLayout.Y_AXIS)); notice.setBorder(new EmptyBorder(12,12,12,12));
        notice.add(label("Thông báo", 14, GameTheme.TEXT)); notice.add(Box.createVerticalStrut(8));
        lobbyStatus.setFont(GameTheme.font(13, false)); lobbyStatus.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        notice.add(lobbyStatus); notice.setMaximumSize(new Dimension(Integer.MAX_VALUE,112)); sidebar.add(notice);
        JScrollPane sideScroll = GameTheme.scroll(sidebar); sideScroll.setOpaque(false); sideScroll.getViewport().setOpaque(false);
        sideScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        GameTheme.ResponsiveSplit body = new GameTheme.ResponsiveSplit(tabs, sideScroll, 290, 730, 330, false);
        JScrollPane bodyScroll=GameTheme.scroll(body); bodyScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); bodyScroll.setOpaque(false); bodyScroll.getViewport().setOpaque(false); page.add(bodyScroll, BorderLayout.CENTER);
        onlineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); onlineTable.setRowSorter(opponentSorter);
        for (int i=2; i<5; i++) opponentSorter.setComparator(i, (a,b) -> Integer.compare(Integer.parseInt(a.toString()), Integer.parseInt(b.toString())));
        LobbyStyle.configurePlayers(onlineTable, () -> me);
        LobbyStyle.configureRanking(rankTable);
        LobbyStyle.configureHistory(pastTable);
        onlineTable.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updateSelectedOpponent(); });
        onlineTable.addMouseListener(new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { if(e.getClickCount()==2) challengeSelected(); } });
        opponentSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterOpponents(); }
            public void removeUpdate(DocumentEvent e) { filterOpponents(); }
            public void changedUpdate(DocumentEvent e) { filterOpponents(); }
        });
        updateSelectedOpponent(); return page;
    }

    private static JPanel tableSection(String title, String detail, JTable table) {
        GameTheme.RoundPanel panel = new GameTheme.RoundPanel(16); panel.setLayout(new BorderLayout(0, 18)); panel.setBorder(new EmptyBorder(12, 16, 12, 16));
        JPanel heading = vertical(); heading.add(label(title, 22, GameTheme.TEXT)); heading.add(Box.createVerticalStrut(8)); heading.add(label(detail, 13, GameTheme.MUTED));
        panel.add(heading, BorderLayout.NORTH); panel.add(scrollTable(table), BorderLayout.CENTER); return panel;
    }

    private void buildInvitationCard() {
        invitationPanel.setName("invitation-priority"); invitationPanel.setLayout(new BorderLayout(20,4)); invitationPanel.setBorder(new EmptyBorder(10,16,10,16)); invitationPanel.setEdge(GameTheme.PINK);
        JPanel copy=new JPanel(new BorderLayout(10,4)); copy.setOpaque(false);
        invitationAvatar.setPreferredSize(new Dimension(38,38)); copy.add(invitationAvatar,BorderLayout.WEST);
        JPanel identity=new JPanel(new GridLayout(2,1)); identity.setOpaque(false); identity.add(invitationTitle); identity.add(invitationLabel); copy.add(identity,BorderLayout.CENTER); copy.add(invitationTimer,BorderLayout.EAST);
        invitationPanel.add(copy,BorderLayout.CENTER);
        JPanel incoming=new JPanel(new GridLayout(1,2,8,0)); incoming.setOpaque(false); ((GameTheme.ActionButton)acceptInvite).primary(); ((GameTheme.ActionButton)rejectInvite).danger(); incoming.add(acceptInvite); incoming.add(rejectInvite);
        JPanel outgoing=new JPanel(new BorderLayout()); outgoing.setOpaque(false); outgoing.add(cancelInvite,BorderLayout.CENTER);
        invitationActions.setOpaque(false); invitationActions.add(incoming,"incoming"); invitationActions.add(outgoing,"outgoing"); invitationActions.setPreferredSize(new Dimension(260,44)); invitationPanel.add(invitationActions,BorderLayout.EAST);
        JPanel lower=new JPanel(new BorderLayout(0,4)); lower.setOpaque(false); invitationDetail.setFont(GameTheme.font(12,false)); invitationDetail.setPreferredSize(new Dimension(1,18)); lower.add(invitationDetail,BorderLayout.CENTER);
        invitationProgress.setUI(new javax.swing.plaf.basic.BasicProgressBarUI()); invitationProgress.setBorderPainted(false); invitationProgress.setBackground(GameTheme.RAISED); invitationProgress.setForeground(GameTheme.ACCENT); invitationProgress.setPreferredSize(new Dimension(1,3)); lower.add(invitationProgress,BorderLayout.SOUTH); invitationPanel.add(lower,BorderLayout.SOUTH);
        invitationPanel.setPreferredSize(new Dimension(1,90)); invitationPanel.setVisible(false); cancelInvite.setEnabled(false);
    }

    private JPanel gameView() {
        JPanel page=base(); page.setLayout(new BorderLayout(0,12));
        JPanel header=new JPanel(new BorderLayout(0,8)); header.setOpaque(false);
        JPanel branding=brandBar("Đấu trường"); branding.remove(((BorderLayout)branding.getLayout()).getLayoutComponent(BorderLayout.CENTER)); branding.add(roomLabel,BorderLayout.CENTER);
        header.add(branding,BorderLayout.NORTH);
        JPanel duel=new JPanel(new GridBagLayout()); duel.setOpaque(false);
        playerA.team(GameTheme.ACCENT); playerB.team(GameTheme.PINK);
        GridBagConstraints c=new GridBagConstraints(); c.fill=GridBagConstraints.BOTH; c.weighty=1;
        c.gridx=0; c.weightx=1; duel.add(playerA,c);
        c.gridx=1; c.weightx=0; c.insets=new Insets(0,12,0,12); duel.add(new GameTheme.Countdown(clock,timerLabel),c);
        c.gridx=2; c.weightx=1; c.insets=new Insets(0,0,0,0); duel.add(playerB,c);
        header.add(duel,BorderLayout.CENTER); page.add(header,BorderLayout.NORTH);

        GameTheme.RoundPanel board=new GameTheme.BattlePanel(); guessBoard=board; board.setName("guess-board");
        board.setEdge(GameTheme.ACCENT); board.setLayout(new BorderLayout(0,8)); board.setBorder(new EmptyBorder(12,16,12,16));
        JPanel heading=new JPanel(new BorderLayout()); heading.setOpaque(false);
        turnLabel.setFont(GameTheme.display(20)); heading.add(turnLabel,BorderLayout.CENTER); heading.add(fillLabel,BorderLayout.EAST);
        JPanel boardHead=new JPanel(new BorderLayout(0,4)); boardHead.setOpaque(false); boardHead.add(heading,BorderLayout.NORTH);
        boardHead.add(label("Phím 1–6 chọn màu  /  Chọn hai ô để đổi vị trí",12,GameTheme.MUTED),BorderLayout.SOUTH); board.add(boardHead,BorderLayout.NORTH);
        JPanel choices=new JPanel(new GridBagLayout()); choices.setOpaque(false);
        JPanel row=new JPanel(new GridLayout(1,6,8,0)); row.setOpaque(false);
        for(int i=0;i<6;i++) { final int position=i; slots[i]=new GameTheme.ColorSlot(i); slots[i].addActionListener(e -> clickSlot(position)); row.add(slots[i]); }
        GridBagConstraints grid=new GridBagConstraints(); grid.gridx=0; grid.gridy=0; grid.weightx=1; grid.weighty=1; grid.fill=GridBagConstraints.BOTH; choices.add(new GameTheme.OrbRack(row),grid);
        JPanel colors=new JPanel(new GridLayout(1,6,8,0)); colors.setOpaque(false);
        for(int i=0;i<6;i++) { final int color=i; palette[i]=new GameTheme.PaletteButton(i); palette[i].addActionListener(e -> chooseColor(color)); colors.add(palette[i]);
            board.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke((char)('1'+i)),"color-"+i);
            board.getActionMap().put("color-"+i,new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { chooseColor(color); } });
        }
        colors.setPreferredSize(new Dimension(1,44)); grid.gridy=1; grid.weighty=0; grid.insets=new Insets(8,0,0,0); choices.add(colors,grid);
        resultLabel.setPreferredSize(new Dimension(1,24)); grid.gridy=2; grid.insets=new Insets(4,0,0,0); choices.add(resultLabel,grid); board.add(choices,BorderLayout.CENTER);
        JPanel actions=new JPanel(new BorderLayout(12,0)); actions.setOpaque(false);
        ((GameTheme.ActionButton)submit).primary().arena(); submit.setFont(GameTheme.display(16)); submit.setPreferredSize(new Dimension(212,48)); actions.add(submit,BorderLayout.EAST);
        JPanel edits=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0)); edits.setOpaque(false);
        ((GameTheme.ActionButton)clear).quiet(); ((GameTheme.ActionButton)remove).quiet(); edits.add(clear); edits.add(remove); actions.add(edits,BorderLayout.CENTER); board.add(actions,BorderLayout.SOUTH);
        arenaCards.setOpaque(false); arenaCards.add(board,"board"); arenaCards.add(resultScreen,"result");

        GameTheme.RoundPanel history=new GameTheme.RoundPanel(16); history.setName("guess-history"); history.setLayout(new BorderLayout(0,8)); history.setBorder(new EmptyBorder(6,12,6,12));
        history.add(label("COMBAT LOG  /  Lịch sử lượt đoán",14,GameTheme.TEXT),BorderLayout.NORTH);
        movesTable.putClientProperty("emptyHeading","Chưa có lượt đoán");
        movesTable.putClientProperty("emptyText","Dự đoán của cả hai người sẽ xuất hiện tại đây.");
        movesTable.getColumnModel().getColumn(2).setCellRenderer(new SequenceRenderer()); LobbyStyle.configureFeed(movesTable,() -> me);
        history.add(scrollTable(movesTable),BorderLayout.CENTER); page.add(new GameTheme.ArenaLayout(arenaCards,history),BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(0,4)); bottom.setOpaque(false);
        replayLabel.setVisible(false); replayLabel.addPropertyChangeListener("text",e -> replayLabel.setVisible(!replayLabel.getText().isBlank()));
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0)); buttons.setOpaque(false);
        ((GameTheme.ActionButton)leave).danger().quiet(); ((GameTheme.ActionButton)replay).primary().arena(); ((GameTheme.ActionButton)back).secondary();
        back.setText("Trở về sảnh");
        leave.setBorder(new EmptyBorder(10,16,10,16)); replay.setBorder(new EmptyBorder(10,24,10,24)); back.setBorder(new EmptyBorder(10,20,10,20));
        buttons.setLayout(new BorderLayout(12,0)); buttons.add(leave,BorderLayout.WEST); JPanel postMatch=new JPanel(new FlowLayout(FlowLayout.RIGHT,12,0)); postMatch.setOpaque(false); postMatch.add(back); postMatch.add(replay); buttons.add(postMatch,BorderLayout.EAST); bottom.add(buttons,BorderLayout.CENTER); bottom.add(replayLabel,BorderLayout.SOUTH);
        replay.setEnabled(false); back.setEnabled(false); page.add(bottom,BorderLayout.SOUTH); updateBoard(); return page;
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
                setMusicScene(AudioManager.MusicScene.LOBBY);
                me = m[1]; myName = m[2]; profiles.clear(); profiles.put(me, Arrays.copyOfRange(m, 1, m.length));
                onlineRows.clear(); onlineModel.setRowCount(0); opponentSearch.setText(""); clearInvitation();
                updateProfileSummary(m); setAuthenticationEnabled(true);
                registering = false; username.setText(me); password.setText(""); registerPassword.setText("");
                cards.show(this, "lobby"); send("RANK"); send("HISTORY");
            }
            case "PROFILE" -> {
                profiles.put(m[1], Arrays.copyOfRange(m, 1, m.length));
                if (m[1].equals(me)) updateProfileSummary(m); updatePlayers(); updateSelectedOpponent();
            }
            case "ONLINE_BEGIN" -> onlineRows.clear();
            case "ONLINE_ROW" -> onlineRows.add(Arrays.copyOfRange(m, 1, m.length));
            case "ONLINE_END" -> {
                String selected = selectedUser(); onlineModel.setRowCount(0);
                int selectedModelRow = -1;
                for (String[] row : onlineRows) {
                    profiles.put(row[0], Arrays.copyOf(row, 5)); onlineModel.addRow(row);
                    if (row[0].equals(selected)) selectedModelRow = onlineModel.getRowCount() - 1;
                }
                if (selectedModelRow >= 0) {
                    int selectedViewRow = onlineTable.convertRowIndexToView(selectedModelRow);
                    if (selectedViewRow >= 0) onlineTable.setRowSelectionInterval(selectedViewRow, selectedViewRow);
                }
                long opponents = onlineRows.stream().filter(row -> !row[0].equals(me)).count();
                long ready = onlineRows.stream().filter(row -> !row[0].equals(me) && row[5].equals("Đang rỗi")).count();
                availableCount.setText(opponents + " đối thủ trực tuyến  ·  " + ready + " sẵn sàng");
                onlineCount.setText(onlineRows.size() + " người trực tuyến"); updatePlayers(); updateSelectedOpponent();
            }
            case "RANK_BEGIN" -> rankModel.setRowCount(0);
            case "RANK_ROW" -> rankModel.addRow(new Object[]{rankModel.getRowCount() + 1, m[1], m[2], m[3], m[4], m[5]});
            case "RANK_END", "HISTORY_END" -> { }
            case "HISTORY_BEGIN" -> pastModel.setRowCount(0);
            case "HISTORY_ROW" -> {
                String when = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(Instant.ofEpochMilli(Long.parseLong(m[2])).atZone(ZoneId.systemDefault()));
                String result = m[5].isEmpty() ? "-" : m[5].equals(me) ? "Thắng" : "Thua";
                pastModel.addRow(new Object[]{m[1].substring(0, 8), when, m[3], m[4], result, m[7], Rules.reasonText(m[6])});
            }
            case "INVITE_SENT" -> {
                showInvitation(m[1], m[2], m[3], true);
                lobbyStatus.setText("Đã mời " + name(m[2]) + ". Chờ phản hồi trong " + m[3] + " giây.");
            }
            case "INVITE" -> {
                showInvitation(m[1], m[2], m[3], false);
                lobbyStatus.setText(name(m[2]) + " đang mời bạn thi đấu. Chấp nhận để vào phòng.");
                if (!active) cards.show(this, "lobby");
            }
            case "INVITE_CLOSED" -> {
                if (invitation.equals(m[1])) clearInvitation(); lobbyStatus.setText(m[2]);
            }
            case "ROOM" -> {
                setMusicScene(AudioManager.MusicScene.BATTLE);
                celebration.clear(); audio.stopTransient(); audioTurn=""; warningSecond=6; audioTimeouts.clear();
                ((CardLayout)arenaCards.getLayout()).show(arenaCards,"board");
                room = m[1]; first = m[2]; second = m[3]; current = ""; active = true; submitted = false;
                replayRequested = false; turn = 0; selectedSlot = -1; Arrays.fill(arrangement, -1);
                movesModel.setRowCount(0); resultLabel.setText("Mỗi dãy cần đủ 6 màu, không lặp màu."); replayLabel.setText(" ");
                roomLabel.setText("Phòng " + room.substring(0, 8).toUpperCase(Locale.ROOT));
                timerLabel.setFont(GameTheme.mono(23)); turnLabel.setForeground(GameTheme.ACCENT);
                replay.setEnabled(false); back.setEnabled(false); leave.setEnabled(true);
                clearInvitation(); updatePlayers(); cards.show(this, "game"); updateBoard();
            }
            case "TURN", "TICK" -> {
                if (!room.equals(m[1]) || !active) break;
                int incomingTurn = Integer.parseInt(m[2]);
                if (incomingTurn < turn) break;
                if (incomingTurn != turn) { turn = incomingTurn; submitted = false; selectedSlot = -1; }
                String soundKey=room+":"+incomingTurn+":"+m[3];
                if(m[3].equals(me) && !soundKey.equals(audioTurn)) { audioTurn=soundKey; warningSecond=6; audio.play(AudioManager.Sound.TURN); }
                current = m[3]; deadline = System.nanoTime() + Long.parseLong(m[4]) * 1_000_000L;
                turnLabel.setText((current.equals(me) ? "YOUR TURN" : "OPPONENT TURN")+"  /  LƯỢT "+turn);
                turnLabel.setToolTipText(current.equals(me) ? "Đến lượt bạn" : "Đang chờ "+name(current));
                turnLabel.setForeground(current.equals(me) ? GameTheme.ACCENT : GameTheme.PINK);
                updateClock(); updatePlayers();
            }
            case "MOVE" -> {
                if (!room.equals(m[1])) break;
                boolean timeout = m[6].equals("TIMEOUT");
                if(timeout && audioTimeouts.add(room+":"+m[2]+":"+m[3])) audio.play(AudioManager.Sound.TIMEOUT);
                movesModel.addRow(new Object[]{m[2], name(m[3]), m[4], timeout ? "-" : m[5] + "/6", timeout ? "Hết " + Rules.TURN_MILLIS / 1000 + " giây" : "Đã gửi"});
                resultLabel.setText(timeout ? (m[3].equals(me) ? "Bạn" : "Đối thủ") + " đã hết thời gian."
                        : name(m[3]) + ": Có " + m[5] + "/6 vị trí đúng.");
                movesTable.scrollRectToVisible(movesTable.getCellRect(movesModel.getRowCount() - 1, 0, true));
            }
            case "RESULT" -> {
                if (!room.equals(m[1])) break;
                setMusicScene(AudioManager.MusicScene.LOBBY);
                if(active) {
                    if(m[2].equals(me) && (m[3].equals("LEFT") || m[3].equals("DISCONNECT"))) audio.play(AudioManager.Sound.PEER_LEFT);
                    audio.play(m[2].equals(me) ? AudioManager.Sound.VICTORY : AudioManager.Sound.DEFEAT);
                }
                if(active && m[2].equals(me)) celebration.start();
                active = false; submitted = false;
                turnLabel.setText(m[2].equals(me) ? "BẠN CHIẾN THẮNG  ·  +1 điểm" : "BẠN THUA  ·  +0 điểm");
                turnLabel.setForeground(m[2].equals(me) ? GameTheme.GOLD : GameTheme.RED);
                resultLabel.setText(m[3].equals("SOLVED") ? name(m[2]) + " đã đoán đúng cả 6 vị trí."
                        : "Trận kết thúc: " + (m[3].equals("LEFT") ? "một người chơi đã rời phòng." : "một người chơi mất kết nối."));
                timerLabel.setText("XONG"); timerLabel.setFont(GameTheme.font(15, true)); clock.setValue(0);
                replay.setEnabled(true); back.setEnabled(true); leave.setEnabled(false);
                String winningSequence=m[3].equals("SOLVED") && movesModel.getRowCount()>0 ? movesModel.getValueAt(movesModel.getRowCount()-1,2).toString() : "";
                resultScreen.showResult(m[2].equals(me),m[3].equals("SOLVED") ? m[2].equals(me) ? "Đã tìm được dãy màu bí mật." : "Đối thủ đã giải mã dãy màu." : resultLabel.getText(),movesModel.getRowCount(),winningSequence);
                ((CardLayout)arenaCards.getLayout()).show(arenaCards,"result");
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
                setMusicScene(AudioManager.MusicScene.LOBBY);
                celebration.clear();
                room = ""; active = false; current = ""; cards.show(this, "lobby");
                lobbyStatus.setText("Chọn một đối thủ sẵn sàng trong danh sách để gửi lời mời.");
                updateSelectedOpponent(); send("LIST"); refreshTab();
            }
            case "ERROR" -> {
                if (m[1].equals("LOGIN") || m[1].equals("REGISTER")) {
                    (m[1].equals("REGISTER") ? registerStatus : loginStatus).setText(m[2]); setAuthenticationEnabled(true);
                } else if (m[1].equals("GUESS")) { submitted = false; resultLabel.setText(m[2]); updateBoard(); }
                else if (m[1].equals("REMATCH")) { replayLabel.setText(m[2]); }
                else {
                    if (m[1].equals("CHALLENGE")) { challengePending = false; updateSelectedOpponent(); }
                    if (m[1].equals("RESPOND") || m[1].equals("CANCEL_INVITE")) { clearInvitation(); send("LIST"); }
                    lobbyStatus.setText(m[2]); if (!room.isEmpty()) resultLabel.setText(m[2]);
                }
            }
            default -> { }
        }
    }

    private void updateProfileSummary(String[] m) {
        profileLabel.setText("Xin chào " + m[2] + " (@" + m[1] + ")");
        profileLabel.setToolTipText(profileLabel.getText());
        myPoints.value(m[3]); myWins.value(m[4]); myPlayed.value(m[5]);
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
        return row < 0 || row >= onlineTable.getRowCount() ? "" : onlineModel.getValueAt(onlineTable.convertRowIndexToModel(row), 0).toString();
    }

    private String onlineState(String user) {
        for (String[] row : onlineRows) if (row[0].equals(user)) return row[5];
        return "Không trực tuyến";
    }

    private void updateSelectedOpponent() {
        String target = selectedUser(), state = onlineState(target);
        opponentCard.display(target, profiles.get(target), state, target.equals(me));
        boolean ready = !me.isEmpty() && !target.isEmpty() && !target.equals(me) && state.equals("Đang rỗi")
                && room.isEmpty() && !active && invitation.isEmpty() && !challengePending;
        inviteButton.setEnabled(ready);
        inviteButton.setText(challengePending ? "Đang gửi lời mời…" : !invitation.isEmpty() ? invitationOutgoing ? "Đã gửi lời mời" : "Đang có lời mời"
                : target.isEmpty() ? "Chọn đối thủ để mời" : target.equals(me) ? "Đây là tài khoản của bạn"
                : !state.equals("Đang rỗi") ? "Đối thủ đang bận" : "Mời thi đấu");
        inviteButton.setToolTipText(ready ? "Gửi lời mời thi đấu cho " + name(target) : "Chọn một đối thủ sẵn sàng và hoàn tất lời mời hiện tại.");
    }

    private void filterOpponents() {
        String query = opponentSearch.getText().trim().toLowerCase(Locale.ROOT);
        opponentSorter.setRowFilter(query.isEmpty() ? null : new RowFilter<DefaultTableModel, Integer>() {
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                return entry.getStringValue(0).toLowerCase(Locale.ROOT).contains(query)
                        || entry.getStringValue(1).toLowerCase(Locale.ROOT).contains(query);
            }
        });
        onlineTable.putClientProperty("emptyText", query.isEmpty() ? "Đang chờ người chơi kết nối…" : "Không tìm thấy người chơi phù hợp.");
        updateSelectedOpponent();
    }

    private void challengeSelected() {
        String target = selectedUser();
        if (target.isEmpty() || target.equals(me)) { lobbyStatus.setText("Hãy chọn một người chơi khác trong danh sách."); return; }
        if (!room.isEmpty() || active || !invitation.isEmpty() || challengePending) { lobbyStatus.setText("Hoàn tất lời mời hoặc trận đấu hiện tại trước khi mời đối thủ mới."); return; }
        if (!onlineState(target).equals("Đang rỗi")) { lobbyStatus.setText("Đối thủ này đang bận. Hãy chọn người có trạng thái Sẵn sàng."); return; }
        challengePending = true; updateSelectedOpponent(); lobbyStatus.setText("Đang gửi lời mời cho " + name(target) + "…");
        send("CHALLENGE", target);
    }

    private void showInvitation(String id, String peer, String seconds, boolean outgoing) {
        if(!outgoing && !invitation.equals(id)) audio.play(AudioManager.Sound.INVITE);
        invitation = id; invitationOutgoing = outgoing; invitationResponding = false; challengePending = false;
        invitationDuration = Long.parseLong(seconds) * 1_000_000_000L;
        invitationDeadline = System.nanoTime() + invitationDuration;
        String[] profile = profiles.get(peer);
        invitationLabel.setText(profile == null ? peer : profile[1]); invitationLabel.setToolTipText(name(peer));
        invitationAvatar.setIcon(new GameTheme.Avatar(peer, 38));
        invitationTitle.setText(outgoing ? "Lời mời đã gửi" : "Bạn được mời thi đấu");
        invitationDetail.setText("@" + peer + "  ·  " + (outgoing ? "Đang chờ đối thủ chấp nhận." : "1 vs 1  ·  " + Rules.TURN_MILLIS / 1000 + " giây mỗi lượt."));
        invitationPanel.setEdge(GameTheme.ACCENT);
        ((CardLayout) invitationActions.getLayout()).show(invitationActions, outgoing ? "outgoing" : "incoming");
        acceptInvite.setEnabled(!outgoing); rejectInvite.setEnabled(!outgoing); cancelInvite.setEnabled(outgoing);
        invitationPanel.setVisible(true); invitationPanel.pulse(); updateInvitationClock(); updateSelectedOpponent();
    }

    private void updateInvitationClock() {
        if (invitation.isEmpty()) return;
        long remaining = Math.max(0, invitationDeadline - System.nanoTime());
        invitationTimer.setText((remaining + 999_999_999L) / 1_000_000_000L + " s");
        Color color = remaining <= 5_000_000_000L ? GameTheme.RED : GameTheme.GOLD;
        invitationTimer.setForeground(color);
        invitationProgress.setForeground(remaining <= 5_000_000_000L ? GameTheme.RED : GameTheme.ACCENT);
        invitationProgress.setValue((int) Math.min(1000, remaining * 1000.0 / Math.max(1, invitationDuration)));
        invitationProgress.getAccessibleContext().setAccessibleName("Thời gian còn lại của lời mời: " + invitationTimer.getText());
        if (remaining == 0 && !invitationResponding) {
            acceptInvite.setEnabled(false); rejectInvite.setEnabled(false); cancelInvite.setEnabled(false);
            invitationTitle.setText("Lời mời đã hết hạn"); invitationPanel.setEdge(GameTheme.RED.darker());
            invitationDetail.setText("Đang chờ server cập nhật trạng thái.");
        }
    }

    private void respond(String answer) {
        if (invitation.isEmpty() || invitationOutgoing || invitationResponding) return;
        if (System.nanoTime() >= invitationDeadline) { updateInvitationClock(); return; }
        audio.play(answer.equals("YES") ? AudioManager.Sound.ACCEPT : AudioManager.Sound.REJECT);
        invitationResponding = true; invitationDetail.setText(answer.equals("YES") ? "Đang xác nhận vào phòng…" : "Đang từ chối lời mời…");
        acceptInvite.setEnabled(false); rejectInvite.setEnabled(false); send("RESPOND", invitation, answer);
    }

    private void clearInvitation() {
        invitation = ""; invitationDeadline = 0; invitationDuration = 0;
        invitationOutgoing = false; invitationResponding = false; challengePending = false;
        invitationPanel.setVisible(false); cancelInvite.setEnabled(false); acceptInvite.setEnabled(false); rejectInvite.setEnabled(false);
        updateSelectedOpponent();
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
        audio.play(AudioManager.Sound.ORB); selectedSlot = -1; updateBoard();
    }

    void clickSlot(int position) {
        if (!myTurn()) return;
        audio.play(AudioManager.Sound.SWAP);
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
        send("GUESS", room, "" + turn, guess.toString()); submitted = true; guessBoard.pulse(); audio.play(AudioManager.Sound.SUBMIT);
        turnLabel.setText("Đã gửi dự đoán  ·  Chờ server xác nhận"); updateBoard();
    }

    private void updateBoard() {
        boolean enabled = myTurn(), full = true;
        if(guessBoard!=null) guessBoard.setEdge(submitted ? GameTheme.GOLD : current.equals(me) ? GameTheme.ACCENT : GameTheme.PINK);
        int filled = 0;
        for (int i = 0; i < 6; i++) {
            if (slots[i] == null) continue;
            int color = arrangement[i]; if (color < 0) full = false; else filled++;
            ((GameTheme.ColorSlot) slots[i]).state(color, selectedSlot == i, enabled);
            boolean used = false; for (int item : arrangement) if (item == i) used = true;
            ((GameTheme.PaletteButton) palette[i]).setUsed(used);
            palette[i].setEnabled(enabled && !used);
        }
        fillLabel.setText(filled + " / 6 màu đã chọn"); fillLabel.setForeground(full ? GameTheme.ACCENT : GameTheme.MUTED);
        submit.setEnabled(enabled && full); clear.setEnabled(enabled); remove.setEnabled(enabled && selectedSlot >= 0);
    }

    private void updateClock() {
        if (!active) return;
        long ms = Math.max(0, (deadline - System.nanoTime()) / 1_000_000L);
        timerLabel.setText(((ms + 999) / 1000) + " s"); timerLabel.setForeground(ms <= 5000 ? GameTheme.RED : GameTheme.TEXT);
        clock.setValue((int) Math.min(Rules.TURN_MILLIS, ms));
        int seconds=(int)((ms+999)/1000);
        if(current.equals(me) && !submitted && seconds>0 && seconds<=5 && seconds<warningSecond) { warningSecond=seconds; audio.play(AudioManager.Sound.WARNING); }
        updateBoard();
    }

    private void leaveRoom() {
        if (JOptionPane.showConfirmDialog(this, "Thoát khi trận đang diễn ra sẽ bị tính thua. Bạn muốn thoát?", "Xác nhận thoát", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
            send("LEAVE", room);
    }

    private void send(String command, String... fields) { if (connection != null) connection.send(command, fields); }

    private void disconnected(String message) {
        setMusicScene(AudioManager.MusicScene.LOBBY);
        generation++; if (connection != null) connection.close(); connection = null;
        celebration.clear(); audio.stopTransient(); me = ""; active = false; room = ""; clearInvitation(); cards.show(this, registering ? "register" : "login");
        setAuthenticationEnabled(true); (registering ? registerStatus : loginStatus).setText(message);
    }

    private void logout() { disconnected("Đã đăng xuất. Bạn có thể đăng nhập tài khoản khác."); }

    public void requestClose(JFrame frame) {
        if (active && JOptionPane.showConfirmDialog(this, "Đóng ứng dụng khi đang chơi sẽ bị tính thua. Tiếp tục?", "Xác nhận", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        shutdown(); frame.dispose();
    }

    void shutdown() { generation++; settingsMenu.setVisible(false); settingsFeedback.stop(); uiTimer.stop(); celebration.clear(); audio.close(); GameTheme.unregisterRoot(this); if (connection != null) connection.close(); }

    private static JPanel base() { JPanel p = new GameTheme.Backdrop(); p.setBorder(new EmptyBorder(12, 16, 12, 16)); return p; }
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
            setBackground(selected ? table.getSelectionBackground() : row % 2 == 0 ? GameTheme.PANEL : GameTheme.INPUT); return this;
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!Rules.validGuess(sequence)) { g.setColor(GameTheme.MUTED); g.drawString("Không gửi dự đoán", 12, 22); return; }
            Graphics2D g2 = GameTheme.graphics(g);
            int width = Math.max(18, Math.min(42, (getWidth() - 12) / 6));
            int size = Math.min(24, width - 2);
            for (int i = 0; i < 6; i++) {
                int color = sequence.charAt(i) - '0', x = 6 + i * width;
                GameTheme.orb(g2, color, x, (getHeight()-size)/2f, size);
                g2.setFont(GameTheme.font(10, true)); g2.setColor(GameTheme.inkOn(GameTheme.COLORS[color]));
                String number = "" + (color + 1);
                g2.drawString(number, x + (size-g2.getFontMetrics().stringWidth(number))/2, getHeight()/2+4);
            }
            g2.dispose();
        }
    }

    public static void launch(String host, int port) {
        SwingUtilities.invokeLater(() -> {
            GameTheme.install();
            JFrame frame = new JFrame("Color Duel / Game đoán dãy màu đối kháng");
            GameClient client = new GameClient(); client.setServer(host, port); frame.setContentPane(client);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { client.requestClose(frame); } });
            frame.setMinimumSize(new Dimension(800, 640));
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            frame.setSize(Math.min(1280, screen.width-50), Math.min(880, screen.height-65));
            frame.setLocationRelativeTo(null); frame.setVisible(true);
        });
    }
}
