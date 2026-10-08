package vn.ptit.colorgame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.util.function.Supplier;

/** Thành phần trình bày của sảnh; trạng thái và lời mời vẫn do server quyết định. */
final class LobbyStyle {
    private LobbyStyle() {}

    static final class StatusPill extends JLabel {
        StatusPill() {
            setFont(GameTheme.font(11, true));
            setBorder(new EmptyBorder(5, 9, 5, 9));
            putClientProperty("html.disable", true);
        }
        void update(String text, Color color) { setText(text); setForeground(color); repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p = GameTheme.graphics(g);
            p.setColor(GameTheme.SELECTED);
            p.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16); p.dispose(); super.paintComponent(g);
        }
    }

    static final class OpponentCard extends GameTheme.RoundPanel {
        private final JLabel avatar = new JLabel(), name = text("Chọn đối thủ", 20, GameTheme.TEXT);
        private final JLabel account = text("Từ danh sách bên trái", 12, GameTheme.MUTED);
        private final JLabel points = text("-", 22, GameTheme.TEXT), wins = text("-", 22, GameTheme.TEXT), played = text("-", 22, GameTheme.TEXT);
        private final StatusPill status = new StatusPill();

        OpponentCard(JButton invite) {
            super(20); setBorder(new EmptyBorder(12,12,12,12));
            name.setMinimumSize(new Dimension(0,26)); account.setMinimumSize(new Dimension(0,18));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            JPanel heading = transparent(new BorderLayout());
            heading.add(text("Đối thủ đã chọn", 12, GameTheme.MUTED), BorderLayout.WEST);
            heading.add(status, BorderLayout.EAST); heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
            add(heading); add(Box.createVerticalStrut(8));
            JPanel identity = transparent(new BorderLayout(12, 0));
            avatar.setPreferredSize(new Dimension(48, 48)); identity.add(avatar, BorderLayout.WEST);
            JPanel copy = transparent(new GridLayout(2, 1, 0, 3)); copy.add(name); copy.add(account);
            identity.add(copy, BorderLayout.CENTER); identity.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            add(identity); add(Box.createVerticalStrut(8));
            JPanel stats = transparent(new GridLayout(1, 3, 10, 0));
            stats.add(metric(points, "Điểm")); stats.add(metric(wins, "Thắng")); stats.add(metric(played, "Đã chơi"));
            stats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44)); add(stats); add(Box.createVerticalStrut(8));
            ((GameTheme.ActionButton) invite).primary(); invite.setAlignmentX(Component.LEFT_ALIGNMENT);
            invite.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44)); invite.setPreferredSize(new Dimension(260,44));
            add(invite); setPreferredSize(new Dimension(308,208)); setMaximumSize(new Dimension(Integer.MAX_VALUE,208));
            display("", null, "", false);
        }

        void display(String user, String[] profile, String state, boolean mine) {
            boolean selected = !user.isEmpty();
            name.setText(selected ? profile == null ? user : profile[1] : "Chọn đối thủ");
            name.setToolTipText(selected ? name.getText() : null);
            account.setText(selected ? "@" + user + (mine ? "  ·  Bạn" : "") : "Từ danh sách bên trái");
            avatar.setIcon(selected ? new GameTheme.Avatar(user, 48) : new GameTheme.Logo());
            points.setText(profile == null ? "-" : profile[2]); wins.setText(profile == null ? "-" : profile[3]);
            played.setText(profile == null ? "-" : profile[4]);
            status.update(!selected ? "Chưa chọn" : mine ? "Bạn" : state.equals("Đang rỗi") ? "Sẵn sàng" : "Đang bận",
                    !selected || mine ? GameTheme.MUTED : state.equals("Đang rỗi") ? GameTheme.ACCENT : GameTheme.GOLD);
            setEdge(selected && !mine && state.equals("Đang rỗi") ? GameTheme.ACCENT : GameTheme.LINE);
        }
    }

    static void configurePlayers(JTable table,Supplier<String> currentUser) {
        table.setRowHeight(88); table.setShowGrid(false); table.setIntercellSpacing(new Dimension(0,0)); table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        for(int column=1;column<6;column++) hide(table,column);
        TableColumn identity=table.getColumnModel().getColumn(0); identity.setMinWidth(1); identity.setPreferredWidth(500); identity.setCellRenderer(new RosterRenderer(currentUser));
        table.setTableHeader(null);
        if(table.getParent() instanceof JViewport view && view.getParent() instanceof JScrollPane pane) pane.setColumnHeaderView(null); table.getAccessibleContext().setAccessibleName("Đối thủ trực tuyến. Chọn hoặc bấm hai lần để mời thi đấu; dùng menu sắp xếp.");
    }
    private static final class RosterRenderer extends JPanel implements TableCellRenderer {
        private final Supplier<String> currentUser; private String user="",name="",stats="",state=""; private boolean selected,mine,ready;
        RosterRenderer(Supplier<String> user) { currentUser=user; setOpaque(false); }
        public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column) {
            int r=table.convertRowIndexToModel(row); TableModel m=table.getModel(); user=value.toString(); name=m.getValueAt(r,1).toString();
            stats=m.getValueAt(r,2)+" ĐIỂM   /   "+m.getValueAt(r,3)+" THẮNG   /   "+m.getValueAt(r,4)+" TRẬN";
            mine=user.equals(currentUser.get()); ready=m.getValueAt(r,5).equals("Đang rỗi"); state=mine ? "BẠN" : ready ? "SẴN SÀNG" : "ĐANG BẬN"; this.selected=table.getSelectionModel().isSelectedIndex(row);
            setToolTipText(name+" (@"+user+") / "+stats+" / "+state); return this;
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p=GameTheme.graphics(g); int w=getWidth(),h=getHeight(); Color tone=selected ? GameTheme.ACCENT : GameTheme.LINE;
            p.setColor(selected ? GameTheme.SELECTED : GameTheme.INPUT); p.fillRoundRect(4,4,w-8,h-8,12,12); p.setColor(tone); p.drawRoundRect(4,4,w-9,h-9,12,12);
            new GameTheme.Avatar(user,44).paintIcon(this,p,16,18);
            p.setFont(GameTheme.display(17)); p.setColor(GameTheme.TEXT); p.drawString(GameTheme.fit(name,p.getFontMetrics(),w-180),72,29);
            p.setFont(GameTheme.font(11,false)); p.setColor(GameTheme.MUTED); p.drawString(GameTheme.fit("@"+user,p.getFontMetrics(),w-180),72,47);
            p.setFont(GameTheme.font(11,true)); p.setColor(selected ? GameTheme.ACCENT : GameTheme.MUTED); p.drawString(GameTheme.fit(stats,p.getFontMetrics(),w-90),72,69);
            p.setFont(GameTheme.font(10,true)); p.setColor(mine ? GameTheme.MUTED : ready ? GameTheme.ACCENT : GameTheme.GOLD); p.drawString(state,w-p.getFontMetrics().stringWidth(state)-16,29);
            if(selected) { p.setColor(GameTheme.ACCENT); p.fillOval(w-24,h-24,6,6); } p.dispose();
        }
    }

    static void configureRanking(JTable table) {
        table.setRowHeight(68); table.getColumnModel().getColumn(0).setHeaderValue("Hạng");
        table.getColumnModel().getColumn(0).setMinWidth(44); table.getColumnModel().getColumn(0).setMaxWidth(48);
        table.getColumnModel().getColumn(0).setCellRenderer(new RankRenderer());
        table.getColumnModel().getColumn(1).setHeaderValue("Người chơi");
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        table.getColumnModel().getColumn(1).setCellRenderer(new IdentityRenderer(() -> "", 2));
        hide(table, 2);
        for (int i=3; i<6; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(new StatRenderer(i==3));
            table.getColumnModel().getColumn(i).setPreferredWidth(64);
            table.getColumnModel().getColumn(i).setMinWidth(60);
            table.getColumnModel().getColumn(i).setHeaderValue(new String[]{"Điểm","Thắng","Đã chơi"}[i-3]);
        }
    }

    static void configureHistory(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS); table.setRowHeight(84);
        for(int index:new int[]{1,2,3,5,6}) hide(table,index);
        TableColumn match=table.getColumnModel().getColumn(0); match.setHeaderValue("Trận đấu"); match.setMinWidth(240); match.setPreferredWidth(450); match.setCellRenderer(new MatchRenderer());
        TableColumn result=table.getColumnModel().getColumn(4); result.setHeaderValue("Kết quả"); result.setMinWidth(144); result.setPreferredWidth(160); result.setCellRenderer(new ResultRenderer());
    }
    private static final class MatchRenderer extends JPanel implements TableCellRenderer {
        private final JLabel players=text("",14,GameTheme.TEXT), time=text("",12,GameTheme.MUTED), id=text("",11,GameTheme.MUTED);
        MatchRenderer() { setLayout(new GridLayout(3,1,0,2)); setBorder(new EmptyBorder(10,12,10,8)); add(players); add(time); add(id); }
        public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column) {
            int r=table.convertRowIndexToModel(row); TableModel m=table.getModel();
            players.setText(m.getValueAt(r,2)+"  vs  "+m.getValueAt(r,3)); time.setText(m.getValueAt(r,1).toString()); id.setText("#"+value);
            setToolTipText(players.getText()+" / "+time.getText()+" / "+id.getText()); setBackground(rowColor(table,selected,row)); return this;
        }
        @Override protected void paintComponent(Graphics g) { doLayout(); super.paintComponent(g); }
    }
    private static final class ResultRenderer extends JPanel implements TableCellRenderer {
        private final JLabel outcome=text("",16,GameTheme.TEXT), detail=text("",11,GameTheme.MUTED), causeLabel=text("",11,GameTheme.MUTED);
        ResultRenderer() { setLayout(new GridLayout(3,1,0,2)); setBorder(new EmptyBorder(8,8,8,8)); add(outcome); add(detail); add(causeLabel); }
        public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column) {
            int r=table.convertRowIndexToModel(row); TableModel m=table.getModel(); String result=value.toString(),reason=m.getValueAt(r,6).toString();
            outcome.setText(result.toUpperCase(java.util.Locale.ROOT)); outcome.setForeground(result.equals("Thắng") ? GameTheme.ACCENT : result.equals("Thua") ? GameTheme.PINK : GameTheme.MUTED);
            String cause=reason.equals(Rules.reasonText("SOLVED")) ? "Đoán đúng" : reason.equals(Rules.reasonText("LEFT")) ? "Rời phòng" : reason.equals(Rules.reasonText("DISCONNECT")) ? "Mất kết nối" : reason;
            detail.setText(m.getValueAt(r,5)+" lượt"); causeLabel.setText(cause); setToolTipText(result+" / "+detail.getText()+" / "+reason); setBackground(rowColor(table,selected,row)); return this;
        }
        @Override protected void paintComponent(Graphics g) { doLayout(); super.paintComponent(g); }
    }
    static void configureFeed(JTable table,Supplier<String> currentUser) {
        table.setRowHeight(72); table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        for(int column=1;column<5;column++) hide(table,column);
        TableColumn entry=table.getColumnModel().getColumn(0); entry.setMinWidth(1); entry.setPreferredWidth(370); entry.setCellRenderer(new FeedRenderer(currentUser)); table.setTableHeader(null);
        if(table.getParent() instanceof JViewport view && view.getParent() instanceof JScrollPane pane) pane.setColumnHeaderView(null);
        table.getAccessibleContext().setAccessibleName("Battle feed: mỗi lượt hiển thị người chơi, dãy màu, số đúng và trạng thái.");
    }
    private static final class FeedRenderer extends JPanel implements TableCellRenderer {
        private final Supplier<String> currentUser; private String turn="",name="",sequence="",score="",state=""; private boolean mine;
        FeedRenderer(Supplier<String> user) { currentUser=user; setOpaque(false); }
        public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column) {
            int r=table.convertRowIndexToModel(row); TableModel m=table.getModel(); turn=value.toString(); name=m.getValueAt(r,1).toString(); sequence=m.getValueAt(r,2).toString(); score=m.getValueAt(r,3).toString(); state=m.getValueAt(r,4).toString();
            mine=name.endsWith("("+currentUser.get()+")"); setToolTipText("Lượt "+turn+" / "+name+" / "+Rules.colorText(sequence)+" / "+score+" / "+state); return this;
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p=GameTheme.graphics(g); int w=getWidth(),h=getHeight(); Color tone=mine ? GameTheme.ACCENT : GameTheme.PINK;
            p.setColor(GameTheme.INPUT); p.fillRoundRect(3,3,w-6,h-6,10,10); p.setColor(GameTheme.LINE); p.drawRoundRect(3,3,w-7,h-7,10,10);
            p.setColor(tone); p.fillOval(13,13,5,5); p.setFont(GameTheme.font(11,true)); p.drawString(GameTheme.fit("LƯỢT "+turn+" / "+name,p.getFontMetrics(),w-90),26,19);
            p.setFont(GameTheme.mono(16)); p.drawString(score,w-p.getFontMetrics().stringWidth(score)-16,24);
            if(Rules.validGuess(sequence)) for(int i=0;i<6;i++) GameTheme.orb(p,sequence.charAt(i)-'0',14+i*30,34,24);
            else { p.setColor(GameTheme.RED); p.setFont(GameTheme.font(12,true)); p.drawString("Hết thời gian",14,51); }
            p.setFont(GameTheme.font(10,false)); p.setColor(GameTheme.MUTED); p.drawString(GameTheme.fit(state,p.getFontMetrics(),Math.max(20,w-210)),Math.max(202,w-95),53); p.dispose();
        }
    }

    static void configureMoves(JTable table) {
        table.setRowHeight(56); table.getTableHeader().setPreferredSize(new Dimension(1,24)); hide(table,1); hide(table,4);
        table.getColumnModel().getColumn(0).setHeaderValue("Người chơi");
        table.getColumnModel().getColumn(0).setPreferredWidth(115);
        table.getColumnModel().getColumn(0).setMinWidth(80);
        table.getColumnModel().getColumn(0).setCellRenderer(new MoveIdentityRenderer());
        table.getColumnModel().getColumn(2).setHeaderValue("Dãy màu");
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        table.getColumnModel().getColumn(2).setMinWidth(142);
        table.getColumnModel().getColumn(3).setHeaderValue("Đúng");
        table.getColumnModel().getColumn(3).setPreferredWidth(55);
        table.getColumnModel().getColumn(3).setMinWidth(48);
        table.getColumnModel().getColumn(3).setCellRenderer(new StatRenderer(true));
    }
    private static void hide(JTable table, int column) {
        TableColumn c=table.getColumnModel().getColumn(column); c.setMinWidth(0); c.setMaxWidth(0); c.setPreferredWidth(0);
    }
    private static final class MoveIdentityRenderer extends JPanel implements TableCellRenderer {
        private final JLabel name=text("",12,GameTheme.TEXT), state=text("",11,GameTheme.MUTED);
        MoveIdentityRenderer() { setLayout(new GridLayout(2,1,0,4)); setBorder(new EmptyBorder(14,8,14,4)); add(name); add(state); }
        public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column) {
            int modelRow=table.convertRowIndexToModel(row);
            name.setText(table.getModel().getValueAt(modelRow,1).toString());
            name.setForeground(GameTheme.TEXT); state.setForeground(GameTheme.MUTED);
            state.setText("Lượt "+value+" / "+table.getModel().getValueAt(modelRow,4));
            setToolTipText(name.getText()+": "+state.getText()); setBackground(rowColor(table,selected,row)); return this;
        }
        @Override protected void paintComponent(Graphics g) { doLayout(); super.paintComponent(g); }
    }

    private static Color rowColor(JTable table, boolean selected, int row) {
        return selected ? table.getSelectionBackground() : GameTheme.rowColor(row);
    }

    private static final class IdentityRenderer extends JPanel implements TableCellRenderer {
        private final Supplier<String> currentUser;
        private final int nameColumn;
        private final JLabel avatar = new JLabel(), name = text("", 14, GameTheme.TEXT), account = text("", 11, GameTheme.MUTED);
        IdentityRenderer(Supplier<String> currentUser) {
            this(currentUser, 1);
        }
        IdentityRenderer(Supplier<String> currentUser, int nameColumn) {
            this.nameColumn = nameColumn;
            this.currentUser = currentUser; setLayout(new BorderLayout(11, 0)); setBorder(new EmptyBorder(9, 12, 9, 10));
            avatar.setPreferredSize(new Dimension(42, 42)); add(avatar, BorderLayout.WEST);
            JPanel copy = transparent(new GridLayout(2, 1, 0, 3)); copy.add(name); copy.add(account); add(copy, BorderLayout.CENTER);
        }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            int modelRow = table.convertRowIndexToModel(row); String user = value.toString();
            String displayName = table.getModel().getValueAt(modelRow, nameColumn).toString();
            name.setForeground(GameTheme.TEXT); account.setForeground(GameTheme.MUTED);
            name.setText(displayName); account.setText("@" + user + (user.equals(currentUser.get()) ? "  ·  Bạn" : ""));
            avatar.setIcon(new GameTheme.Avatar(user, 42)); setToolTipText(displayName + " (@" + user + ")");
            setBackground(rowColor(table, selected, row)); return this;
        }
        @Override protected void paintComponent(Graphics g) {
            // Renderer panels also need child layout when painted without a native window.
            doLayout();
            for (Component child : getComponents()) if (child instanceof Container container) container.doLayout();
            super.paintComponent(g);
        }
    }

    private static final class RankRenderer extends DefaultTableCellRenderer {
        private int rank;
        public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int column) {
            super.getTableCellRendererComponent(table,value,selected,focus,row,column);
            rank=Integer.parseInt(value.toString()); setHorizontalAlignment(CENTER); setFont(GameTheme.display(rank<=3 ? 24 : 16));
            setForeground(rank==1 ? GameTheme.GOLD : rank==2 ? GameTheme.ACCENT : rank==3 ? GameTheme.PINK : GameTheme.MUTED);
            setBackground(rowColor(table,selected,row)); setBorder(new EmptyBorder(0,0,0,0)); return this;
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g); if(rank<=3) { Graphics2D p=GameTheme.graphics(g); p.setColor(getForeground()); p.setStroke(new BasicStroke(2));
                int x=getWidth()/2; p.drawLine(x-12,getHeight()-13,x,getHeight()-7); p.drawLine(x,getHeight()-7,x+12,getHeight()-13); p.dispose(); }
        }
    }
    private static final class StatRenderer extends DefaultTableCellRenderer {
        private final boolean points;
        StatRenderer(boolean points) { this.points = points; setHorizontalAlignment(SwingConstants.CENTER); }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            setBorder(new EmptyBorder(6, 4, 6, 4)); setBackground(rowColor(table, selected, row));
            setForeground(points ? GameTheme.ACCENT : GameTheme.TEXT); setFont(GameTheme.mono(14)); return this;
        }
    }

    private static final class StateRenderer extends JPanel implements TableCellRenderer {
        private final StatusPill state = new StatusPill();
        StateRenderer() { setLayout(new GridBagLayout()); add(state); }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            String text = value.toString();
            state.update(text.equals("Đang rỗi") ? "Sẵn sàng" : text,
                    text.equals("Đang rỗi") ? GameTheme.ACCENT : text.equals("Đang chơi") ? GameTheme.GOLD : GameTheme.MUTED);
            setBackground(rowColor(table, selected, row)); return this;
        }
        @Override protected void paintComponent(Graphics g) { doLayout(); super.paintComponent(g); }
    }

    private static JPanel metric(JLabel value, String caption) {
        JPanel group = transparent(new BorderLayout(0, 3)); group.add(value, BorderLayout.CENTER);
        value.setFont(GameTheme.mono(22)); group.add(text(caption, 11, GameTheme.MUTED), BorderLayout.SOUTH); return group;
    }
    private static JPanel transparent(LayoutManager layout) {
        JPanel p = new JPanel(layout); p.setOpaque(false); p.setAlignmentX(Component.LEFT_ALIGNMENT); return p;
    }
    private static JLabel text(String text, int size, Color color) {
        JLabel l = new JLabel(text); l.setFont(GameTheme.font(size, size >= 14)); l.setForeground(color);
        l.putClientProperty("html.disable", true); return l;
    }
}
