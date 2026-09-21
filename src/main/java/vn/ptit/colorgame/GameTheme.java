package vn.ptit.colorgame;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** Bộ thành phần giao diện; không chứa luật chơi hoặc thao tác mạng. */
public final class GameTheme {
    public static final Color BACKGROUND = new Color(11, 18, 32), PANEL = new Color(19, 30, 48);
    public static final Color RAISED = new Color(26, 40, 60), LINE = new Color(44, 61, 83);
    public static final Color TEXT = new Color(235, 243, 255), MUTED = new Color(161, 180, 204);
    public static final Color ACCENT = new Color(77, 226, 200), GOLD = new Color(255, 199, 95);
    public static final Color RED = new Color(255, 112, 133), SELECTED = new Color(29, 65, 75);
    public static final Color[] COLORS = {
        new Color(247, 73, 102), new Color(52, 211, 135), new Color(75, 152, 255),
        new Color(255, 213, 83), new Color(173, 119, 255), new Color(255, 150, 68)
    };
    private static final String FONT = System.getProperty("os.name", "").startsWith("Windows") ? "Segoe UI" : "SansSerif";
    private static final BufferedImage ARENA = loadArena();
    private GameTheme() {}

    public static Font font(int size, boolean bold) { return new Font(FONT, bold ? Font.BOLD : Font.PLAIN, size); }
    public static Graphics2D graphics(Graphics g) {
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return p;
    }
    public static void install() {
        try { UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel"); } catch (Exception ignored) { }
        for (String key : new String[]{"Panel.background", "OptionPane.background", "Viewport.background"}) UIManager.put(key, PANEL);
        for (String key : new String[]{"Label.foreground", "OptionPane.messageForeground", "Button.foreground"}) UIManager.put(key, TEXT);
        UIManager.put("Button.background", RAISED); UIManager.put("Button.font", font(13, true));
        UIManager.put("Label.font", font(13, false)); UIManager.put("OptionPane.messageFont", font(14, false));
        UIManager.put("ToolTip.background", RAISED); UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", new LineBorder(LINE)); UIManager.put("ScrollBar.width", 9);
    }
    private static BufferedImage loadArena() {
        try (var input = GameTheme.class.getResourceAsStream("/images/color-arena.png")) {
            return input == null ? null : ImageIO.read(input);
        } catch (IOException e) { return null; }
    }
    public static boolean hasArtwork() { return ARENA != null; }

    public static class RoundPanel extends JPanel {
        protected Color fill = PANEL, edge = LINE;
        protected final int radius;
        public RoundPanel(int radius) { this.radius = radius; setOpaque(false); }
        public void setEdge(Color color) { edge = color; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g); p.setColor(fill); p.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            p.setColor(edge); p.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, radius, radius); p.dispose();
        }
    }

    public static final class Backdrop extends JPanel {
        public Backdrop() { setBackground(BACKGROUND); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g);
            p.setPaint(new GradientPaint(0, 0, new Color(17, 29, 48), getWidth(), getHeight(), BACKGROUND));
            p.fillRect(0, 0, getWidth(), getHeight());
            for (int i = 0; i < 44; i++) {
                int x = (i * 197 + 31) % Math.max(1, getWidth()), y = (i * 113 + 59) % Math.max(1, getHeight());
                p.setColor(new Color(158, 209, 255, i % 3 == 0 ? 35 : 15)); p.fillOval(x, y, 2, 2);
            }
            p.dispose();
        }
    }

    /** Cùng hình minh họa được trình bày khác nhau ở màn hình đăng nhập và banner. */
    public static final class ArtPanel extends RoundPanel {
        private final boolean portrait;
        public ArtPanel(boolean portrait) { super(24); this.portrait = portrait; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g); int w = getWidth(), h = getHeight();
            p.clip(new RoundRectangle2D.Double(0, 0, w, h, radius, radius));
            p.setColor(new Color(10, 20, 37)); p.fillRect(0, 0, w, h);
            if (ARENA != null) {
                if (portrait) {
                    double scale = Math.max((double) w / ARENA.getWidth(), (double) h / ARENA.getHeight());
                    int iw = (int) (ARENA.getWidth()*scale), ih = (int) (ARENA.getHeight()*scale);
                    p.drawImage(ARENA, w-iw, (h-ih)/2-35, iw, ih, null);
                    p.setPaint(new GradientPaint(0, h*.44f, new Color(10, 20, 37, 0), 0, h, new Color(10, 20, 37, 250)));
                    p.fillRect(0, 0, w, h);
                } else {
                    int iw = Math.max(400, w*2/3), ih = (int) ((double) iw * ARENA.getHeight()/ARENA.getWidth());
                    p.drawImage(ARENA, w-iw, (h-ih)/2+20, iw, ih, null);
                    p.setPaint(new GradientPaint(w*.30f, 0, new Color(10, 20, 37, 255), w*.85f, 0, new Color(10, 20, 37, 25)));
                    p.fillRect(0, 0, w, h);
                }
            }
            p.setColor(LINE); p.drawRoundRect(0, 0, w-1, h-1, radius, radius); p.dispose();
        }
    }

    public static final class ActionButton extends JButton {
        private boolean primary, danger;
        public ActionButton(String text) {
            super(text); setFont(font(13, true)); setForeground(TEXT); setFocusPainted(false);
            setOpaque(false); setContentAreaFilled(false); setBorder(new EmptyBorder(11, 17, 11, 17));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setRolloverEnabled(true);
        }
        public ActionButton primary() { primary = true; repaint(); return this; }
        public ActionButton danger() { danger = true; repaint(); return this; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g); int w = getWidth(), h = getHeight();
            Color background = primary ? ACCENT : RAISED;
            if (!isEnabled()) background = new Color(24, 35, 51);
            else if (getModel().isPressed()) background = background.darker();
            else if (getModel().isRollover()) background = primary ? new Color(114, 243, 220) : new Color(37, 58, 79);
            p.setColor(background); p.fillRoundRect(0, 0, w, h, 12, 12);
            p.setColor(hasFocus() ? ACCENT : danger && isEnabled() ? new Color(131, 74, 90) : LINE);
            if (!primary || hasFocus()) p.drawRoundRect(0, 0, w-1, h-1, 12, 12);
            p.setFont(getFont()); FontMetrics fm = p.getFontMetrics();
            p.setColor(!isEnabled() ? new Color(112, 132, 155) : primary ? BACKGROUND : danger ? RED : TEXT);
            p.drawString(getText(), Math.max(5, (w-fm.stringWidth(getText()))/2), (h-fm.getHeight())/2+fm.getAscent());
            p.dispose();
        }
    }

    public static void field(JTextField field) {
        if (field instanceof JPasswordField password) { password.setUI(new BasicPasswordFieldUI()); password.setEchoChar('\u2022'); }
        else field.setUI(new BasicTextFieldUI());
        field.setFont(font(14, false)); field.setBackground(new Color(13, 23, 39));
        field.setForeground(TEXT); field.setCaretColor(ACCENT); field.setSelectionColor(SELECTED);
        field.setSelectedTextColor(TEXT); field.setPreferredSize(new Dimension(220, 40));
        field.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(7, 12, 7, 12)));
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { set(ACCENT); }
            @Override public void focusLost(FocusEvent e) { set(LINE); }
            private void set(Color color) { field.setBorder(new CompoundBorder(new LineBorder(color), new EmptyBorder(7, 12, 7, 12))); }
        });
    }

    public static void orb(Graphics2D p, int color, float x, float y, float size) {
        Color base = COLORS[color];
        p.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 28));
        p.fill(new Ellipse2D.Float(x-3, y-3, size+6, size+6));
        p.setPaint(new RadialGradientPaint(new Point2D.Float(x+size*.32f, y+size*.27f), size*.9f,
                new float[]{0, .43f, 1}, new Color[]{base.brighter(), base, base.darker().darker()}));
        p.fill(new Ellipse2D.Float(x, y, size, size));
        p.setColor(new Color(255, 255, 255, 140));
        p.fill(new Ellipse2D.Float(x+size*.2f, y+size*.14f, size*.30f, size*.14f));
        p.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 180));
        p.draw(new Ellipse2D.Float(x, y, size, size));
    }

    public static final class ColorSlot extends JButton {
        private final int position;
        private int color = -1;
        private boolean selected;
        public ColorSlot(int position) {
            this.position = position; setOpaque(false); setContentAreaFilled(false); setBorderPainted(false);
            setFocusPainted(false); setPreferredSize(new Dimension(125, 85)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        public void state(int color, boolean selected, boolean enabled) {
            this.color = color; this.selected = selected; setEnabled(enabled);
            String description = "Vị trí " + (position+1) + ": " + (color < 0 ? "Chưa chọn" : Rules.COLOR_NAMES[color]);
            getAccessibleContext().setAccessibleName(description); setToolTipText(description); repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g); int w = getWidth(), h = getHeight();
            p.setColor(selected ? new Color(24, 63, 70) : new Color(13, 24, 42));
            p.fillRoundRect(1, 1, w-2, h-2, 16, 16);
            p.setColor(selected || (isEnabled() && getModel().isRollover()) || hasFocus() ? ACCENT : LINE);
            p.setStroke(new BasicStroke(selected ? 2 : 1)); p.drawRoundRect(1, 1, w-3, h-3, 16, 16);
            p.setFont(font(10, true)); p.setColor(MUTED); p.drawString("0"+(position+1), 12, 17);
            if (color >= 0) orb(p, color, (w-32)/2f, 15, 32);
            else {
                p.setColor(new Color(44, 62, 85)); p.drawOval((w-30)/2, 17, 30, 30);
                p.setFont(font(19, false)); p.setColor(MUTED); p.drawString("?", w/2-5, 39);
            }
            String caption = color < 0 ? "Chọn màu" : Rules.COLOR_NAMES[color];
            p.setFont(font(12, color >= 0)); p.setColor(color < 0 ? MUTED : TEXT);
            p.drawString(caption, (w-p.getFontMetrics().stringWidth(caption))/2, h-11); p.dispose();
        }
    }

    public static final class PaletteButton extends JButton {
        private final int color;
        private boolean used;
        public PaletteButton(int color) {
            super(Rules.COLOR_NAMES[color]); this.color = color;
            setOpaque(false); setContentAreaFilled(false); setBorder(new EmptyBorder(10, 6, 10, 6));
            setFont(font(12, true)); setFocusPainted(false); setPreferredSize(new Dimension(120, 40));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        public void setUsed(boolean used) { this.used = used; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g); int w = getWidth(), h = getHeight();
            p.setColor(isEnabled() && getModel().isRollover() ? new Color(40, 58, 79) : RAISED);
            p.fillRoundRect(0, 0, w, h, 11, 11);
            if (hasFocus()) { p.setColor(ACCENT); p.drawRoundRect(0, 0, w-1, h-1, 11, 11); }
            p.setFont(getFont()); String caption = (color+1)+" "+Rules.COLOR_NAMES[color];
            int total = 24+p.getFontMetrics().stringWidth(caption), x = Math.max(5, (w-total)/2);
            orb(p, color, x, (h-16)/2f, 16); p.setColor(isEnabled() ? TEXT : MUTED);
            p.drawString(caption, x+24, (h-p.getFontMetrics().getHeight())/2+p.getFontMetrics().getAscent());
            if (used) { p.setColor(ACCENT); p.fillOval(w-9, 4, 4, 4); } p.dispose();
        }
    }

    public static final class Avatar implements Icon {
        private final String user;
        private final int size;
        public Avatar(String user, int size) { this.user = user; this.size = size; }
        public int getIconWidth() { return size; }
        public int getIconHeight() { return size; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D p = graphics(g); Color color = COLORS[Math.floorMod(user.hashCode(), COLORS.length)];
            p.setPaint(new GradientPaint(x, y, color.darker(), x+size, y+size, new Color(27, 42, 66)));
            p.fillRoundRect(x, y, size, size, size/3, size/3); p.setColor(color);
            p.drawRoundRect(x, y, size-1, size-1, size/3, size/3);
            String initial = user.isBlank() ? "?" : user.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            p.setColor(TEXT); p.setFont(font(size/2, true)); FontMetrics fm = p.getFontMetrics();
            p.drawString(initial, x+(size-fm.stringWidth(initial))/2, y+(size-fm.getHeight())/2+fm.getAscent());
            p.dispose();
        }
    }

    public static final class Logo implements Icon {
        public int getIconWidth() { return 36; }
        public int getIconHeight() { return 36; }
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D p = graphics(g);
            for (int i = 0; i < 6; i++) {
                double angle = i*Math.PI/3;
                orb(p, i, x+13+(float)Math.cos(angle)*10, y+13+(float)Math.sin(angle)*10, 9);
            }
            p.dispose();
        }
    }

    public static final class Badge extends JLabel {
        private final Color color;
        public Badge(String text, Color color) {
            super(text); this.color = color; setForeground(color); setFont(font(11, true));
            setBorder(new EmptyBorder(6, 10, 6, 10)); putClientProperty("html.disable", true);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g); p.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 22));
            p.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18); p.dispose(); super.paintComponent(g);
        }
    }

    /** Thông báo kết nối dài tự xuống dòng thay vì bị cắt ở cuối ô đăng nhập. */
    public static final class Notice extends JLabel {
        public Notice(String text) {
            super(text); setForeground(MUTED); setFont(font(12, false));
            setPreferredSize(new Dimension(330, 44)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g); p.setFont(getFont()); p.setColor(getForeground());
            FontMetrics fm=p.getFontMetrics(); int y=fm.getAscent()+2; String line="";
            for (String word : getText().split(" ")) {
                String next=line.isEmpty() ? word : line+" "+word;
                if (fm.stringWidth(next)>getWidth() && !line.isEmpty()) {
                    p.drawString(line,0,y); y+=fm.getHeight()+2; line=word;
                } else line=next;
            }
            p.drawString(line,0,y); p.dispose();
        }
    }

    public static final class PlayerCard extends RoundPanel {
        private final JLabel avatar = new JLabel(), title = new JLabel("Người chơi"), stats = new JLabel(" "), account = new JLabel(" ");
        private final Badge state = new Badge("Đang chờ", ACCENT);
        public PlayerCard() {
            super(20); setLayout(new BorderLayout(15, 0)); setBorder(new EmptyBorder(14, 16, 14, 16));
            avatar.setIcon(new Avatar("?", 48)); add(avatar, BorderLayout.WEST);
            JPanel lines = new JPanel(new GridLayout(3, 1, 0, 2)); lines.setOpaque(false);
            title.setFont(font(17, true)); title.setForeground(TEXT); title.putClientProperty("html.disable", true);
            account.setFont(font(11, false)); account.setForeground(MUTED); account.putClientProperty("html.disable", true);
            stats.setFont(font(12, false)); stats.setForeground(GOLD);
            lines.add(title); lines.add(account); lines.add(stats); add(lines, BorderLayout.CENTER);
            JPanel badge = new JPanel(new BorderLayout()); badge.setOpaque(false); badge.add(state, BorderLayout.NORTH);
            add(badge, BorderLayout.EAST); setPreferredSize(new Dimension(400, 96));
        }
        public void update(String user, String name, String points, String wins, boolean mine, boolean turn, boolean active) {
            title.setText(name); title.setToolTipText(name); account.setText("@"+user+(mine ? "  ·  Bạn" : "  ·  Đối thủ"));
            stats.setText(points+" điểm   /   "+wins+" trận thắng"); avatar.setIcon(new Avatar(user, 48));
            state.setText(active ? turn ? "ĐẾN LƯỢT" : "ĐANG CHỜ" : "KẾT THÚC");
            setEdge(active && turn ? ACCENT.darker() : LINE);
        }
    }

    public static final class Countdown extends JPanel {
        private final JProgressBar progress;
        public Countdown(JProgressBar progress, JLabel label) {
            this.progress = progress; setOpaque(false); setLayout(new BorderLayout());
            setBorder(new EmptyBorder(14, 5, 14, 5)); setPreferredSize(new Dimension(106, 96));
            label.setHorizontalAlignment(SwingConstants.CENTER); add(label, BorderLayout.CENTER);
            progress.addChangeListener(e -> repaint());
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = graphics(g); int s = Math.min(getWidth(), getHeight())-12, x=(getWidth()-s)/2, y=(getHeight()-s)/2;
            p.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            p.setColor(LINE); p.drawOval(x, y, s, s);
            p.setColor(progress.getValue() <= 5000 ? RED : ACCENT);
            p.draw(new Arc2D.Double(x, y, s, s, 90, -360.0*progress.getValue()/Rules.TURN_MILLIS, Arc2D.OPEN)); p.dispose();
        }
    }

    public static void tabs(JTabbedPane tabs) {
        tabs.setOpaque(false); tabs.setFont(font(13, true)); tabs.setForeground(MUTED); tabs.setBackground(BACKGROUND);
        tabs.setUI(new BasicTabbedPaneUI() {
            @Override protected void installDefaults() { super.installDefaults(); tabInsets = new Insets(12, 20, 12, 20); contentBorderInsets = new Insets(12, 0, 0, 0); tabAreaInsets = new Insets(0, 0, 0, 0); }
            @Override protected void paintTabBackground(Graphics g, int placement, int i, int x, int y, int w, int h, boolean selected) {
                if (selected) { Graphics2D p=graphics(g); p.setColor(RAISED); p.fillRoundRect(x, y, w, h, 12, 12); p.dispose(); }
            }
            @Override protected void paintText(Graphics g, int placement, Font font, FontMetrics metrics, int index, String title, Rectangle rect, boolean selected) {
                g.setFont(font); g.setColor(selected ? ACCENT : MUTED); g.drawString(title, rect.x, rect.y+metrics.getAscent());
            }
            @Override protected void paintTabBorder(Graphics g, int placement, int i, int x, int y, int w, int h, boolean selected) { }
            @Override protected void paintContentBorder(Graphics g, int placement, int selectedIndex) { }
            @Override protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int i, Rectangle icon, Rectangle text, boolean selected) {
                if (tabs.hasFocus() && selected) { g.setColor(ACCENT); g.drawLine(rects[i].x+15, rects[i].y+rects[i].height-4, rects[i].x+rects[i].width-15, rects[i].y+rects[i].height-4); }
            }
        });
    }

    public static JScrollPane scroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table); scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(new LineBorder(LINE)); scroll.getViewport().setBackground(PANEL);
        scroll.setBackground(PANEL); scroll.getVerticalScrollBar().setUnitIncrement(42);
        for (JScrollBar bar : new JScrollBar[]{scroll.getVerticalScrollBar(), scroll.getHorizontalScrollBar()}) {
            bar.setUI(new BasicScrollBarUI() {
                @Override protected void configureScrollBarColors() { thumbColor=LINE; trackColor=PANEL; }
                @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
                @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }
                private JButton zeroButton() { JButton b=new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
            });
        }
        return scroll;
    }

    public static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int freeHeight = getHeight()-getRowCount()*getRowHeight();
                if (ARENA != null && Boolean.TRUE.equals(getClientProperty("arenaBackdrop")) && freeHeight>90) {
                    Graphics2D p=graphics(g); int h=Math.min(230,freeHeight-12), w=h*3/2;
                    p.clipRect(0,getRowCount()*getRowHeight(),getWidth(),freeHeight);
                    p.setComposite(AlphaComposite.SrcOver.derive(.32f));
                    p.drawImage(ARENA,getWidth()-w-8,getHeight()-h-6,w,h,null); p.dispose();
                }
                if (getRowCount() == 0 && getHeight() > 70) {
                    Graphics2D p=graphics(g); p.setFont(font(13, false)); p.setColor(MUTED);
                    String text = String.valueOf(getClientProperty("emptyText"));
                    if (text.equals("null")) text="Chưa có dữ liệu";
                    p.drawString(text, Math.max(12, (getWidth()-p.getFontMetrics().stringWidth(text))/2), Math.min(getHeight()-15, 52)); p.dispose();
                }
            }
        };
        table.setBackground(PANEL); table.setForeground(TEXT); table.setSelectionBackground(SELECTED);
        table.setSelectionForeground(TEXT); table.setRowHeight(43); table.setFont(font(13, false));
        table.setGridColor(new Color(30, 46, 66)); table.setShowVerticalLines(false); table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                putClientProperty("html.disable", true); setFont(font(13, false)); setBorder(new EmptyBorder(0, 14, 0, 10));
                setBackground(selected ? SELECTED : row%2==0 ? PANEL : new Color(22, 34, 52));
                String text=value==null ? "" : value.toString();
                setForeground(text.equals("Thắng") || text.equals("Đang rỗi") ? ACCENT : text.equals("Thua") ? RED : TEXT);
                setHorizontalAlignment(text.matches("[0-9]+(/6)?") ? CENTER : LEFT);
                setToolTipText(text); return this;
            }
        });
        JTableHeader header = table.getTableHeader(); header.setReorderingAllowed(false); header.setPreferredSize(new Dimension(1, 36));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, value, false, false, row, col);
                setBackground(RAISED); setForeground(MUTED); setFont(font(11, true)); setBorder(new EmptyBorder(0, 14, 0, 10));
                return this;
            }
        });
        return table;
    }
}
