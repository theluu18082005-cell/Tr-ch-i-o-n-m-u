package vn.ptit.colorgame;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** History is a local editing aid; recalling must not submit, change notes or reset time. */
public final class HistoryRecallTest {
    private static int checks;
    private static Object field(GameClient c,String name) throws Exception { Field f=GameClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(c); }
    private static void check(boolean ok,String name) { if(!ok) throw new AssertionError(name); checks++; System.out.println("PASS RECALL "+name); }
    private static int[] order(GameClient c) throws Exception { return (int[])field(c,"arrangement"); }
    private static boolean matches(GameClient c,String sequence) throws Exception { return Arrays.equals(order(c),sequence.chars().map(ch -> ch-'0').toArray()); }
    private static void click(JTable table,int row,int button) {
        if(row>=0 && row<table.getRowCount()) table.scrollRectToVisible(table.getCellRect(row,0,true));
        table.dispatchEvent(new MouseEvent(table,MouseEvent.MOUSE_CLICKED,System.currentTimeMillis(),0,12,row*table.getRowHeight()+16,1,false,button));
    }
    private static void enter(JTable table) { table.getActionMap().get("recall-guess").actionPerformed(new ActionEvent(table,0,"recall-guess")); }
    private static void turn(GameClient c,int n,String who) { c.onMessage(new String[]{"TURN","12345678-recall",""+n,who,"30000"}); }
    public static void main(String[] args) throws Exception {
        boolean nativeWindow=args.length>1 && args[1].equals("--native"); Path out=Path.of(args.length==0 ? "build/ui-recall" : args[0]); Files.createDirectories(out);
        SwingUtilities.invokeAndWait(() -> {
            GameClient c=new GameClient(new AudioManager(sound -> null)); JFrame frame=null;
            try {
                c.onMessage(new String[]{"AUTH","alice","Alice","0","0","0"});
                c.onMessage(new String[]{"ROOM","12345678-recall","alice","bob"}); turn(c,1,"alice");
                for(int i=0;i<6;i++) c.chooseColor(i);
                c.onMessage(new String[]{"MOVE","12345678-recall","1","alice","012345","2","GUESS"}); turn(c,2,"bob");
                c.onMessage(new String[]{"MOVE","12345678-recall","2","bob","103254","4","GUESS"}); turn(c,3,"alice");
                JTable table=(JTable)field(c,"movesTable"); DefaultTableModel model=(DefaultTableModel)field(c,"movesModel"); table.setSize(360,216);
                if(nativeWindow) { frame=new JFrame("ColorDuel / History recall QA"); frame.setContentPane(c); c.setPreferredSize(new Dimension(1040,680)); frame.pack(); frame.setLocation(40,24); frame.setVisible(true); }
                JButton[] notes=(JButton[])field(c,"noteSlots"); notes[2].doClick(0); ((JMenuItem)((JPopupMenu)field(c,"noteMenu")).getComponent(5)).doClick(0); ((JPopupMenu)field(c,"noteMenu")).setVisible(false);
                int[] personal=((int[])field(c,"positionNotes")).clone(); long deadline=(long)field(c,"deadline");
                c.clickSlot(0); click(table,0,MouseEvent.BUTTON1);
                check(matches(c,"012345"),"Click own earlier guess replaces current board");
                Component row=table.prepareRenderer(table.getCellRenderer(0,0),0,0); row.setSize(350,72);
                BufferedImage selectedImage=new BufferedImage(350,72,BufferedImage.TYPE_INT_RGB); Graphics2D ink=selectedImage.createGraphics(); row.paint(ink); ink.dispose();
                check(table.isCellSelected(0,0) && selectedImage.getRGB(220,40)==GameTheme.SELECTED.getRGB(),"Selected recalled row is visually highlighted in live renderer");
                check((int)field(c,"selectedSlot")==-1 && ((JButton)field(c,"submit")).isEnabled(),"Recall clears slot selection and enables a complete guess");
                check(!(boolean)field(c,"submitted") && model.getRowCount()==2 && model.getValueAt(0,3).equals("2/6"),"Recall does not submit or rewrite history/scoring");
                check(deadline==(long)field(c,"deadline") && Arrays.equals(personal,(int[])field(c,"positionNotes")),"Recall preserves turn deadline and private notes");
                table.setRowSelectionInterval(1,1); enter(table); check(matches(c,"103254"),"Enter recalls selected opponent guess");
                c.clickSlot(0); c.clickSlot(1); check(matches(c,"013254") && model.getValueAt(1,2).equals("103254"),"Editing recalled colors does not mutate historical sequence");
                click(table,0,MouseEvent.BUTTON3); check(matches(c,"013254"),"Right click cannot replace arrangement");
                click(table,10,MouseEvent.BUTTON1); check(matches(c,"013254"),"Click outside actual rows is harmless");
                TableRowSorter<DefaultTableModel> sorter=new TableRowSorter<>(model); table.setRowSorter(sorter); sorter.setSortKeys(java.util.List.of(new RowSorter.SortKey(0,SortOrder.DESCENDING)));
                click(table,0,MouseEvent.BUTTON1); check(matches(c,"103254"),"Displayed row maps correctly through sorting");
                table.setRowSorter(null);
                model.addRow(new Object[]{"3","Alice","","-","Hết 30 giây"}); model.addRow(new Object[]{"4","Bob","001234","2/6","Đã gửi"}); model.addRow(new Object[]{"5","Bob",null,"-","Không có dãy"});
                int[] before=order(c).clone(); click(table,2,MouseEvent.BUTTON1); click(table,3,MouseEvent.BUTTON1); click(table,4,MouseEvent.BUTTON1);
                check(Arrays.equals(before,order(c)),"Timeout, malformed and missing sequences cannot be recalled");
                table.clearSelection(); enter(table); check(Arrays.equals(before,order(c)),"Enter without selected row is harmless");
                turn(c,4,"bob"); click(table,0,MouseEvent.BUTTON1); table.setRowSelectionInterval(0,0); enter(table);
                check(Arrays.equals(before,order(c)) && !Boolean.TRUE.equals(table.getClientProperty("guessRecallEnabled")),"Opponent turn blocks mouse and Enter recall");
                c.onMessage(new String[]{"MOVE","12345678-recall","4","bob","230145","1","GUESS"});
                check(matches(c,"230145") && table.getSelectedRow()==-1,"New opponent prefill clears obsolete recalled-row selection");
                turn(c,5,"alice"); before=order(c).clone();
                Field time=GameClient.class.getDeclaredField("deadline"); time.setAccessible(true); time.setLong(c,System.nanoTime()-1); click(table,0,MouseEvent.BUTTON1);
                check(Arrays.equals(before,order(c)),"Expired turn blocks recall immediately");
                turn(c,5,"alice"); click(table,0,MouseEvent.BUTTON1); ((JButton)field(c,"submit")).doClick(0); click(table,1,MouseEvent.BUTTON1);
                check((boolean)field(c,"submitted") && matches(c,"012345"),"Explicit submit works and locks later recall");
                c.onMessage(new String[]{"RESULT","12345678-recall","bob","SOLVED"}); click(table,1,MouseEvent.BUTTON1); check(matches(c,"012345"),"Ended match keeps history read-only");
                c.onMessage(new String[]{"ROOM","abcdefgh-rematch","alice","bob"}); c.onMessage(new String[]{"TURN","abcdefgh-rematch","1","alice","30000"}); click(table,0,MouseEvent.BUTTON1);
                check(model.getRowCount()==0 && Arrays.stream(order(c)).allMatch(n -> n==-1),"Rematch cannot recall previous match rows");
                if(nativeWindow) check(frame.isShowing(),"Native client handled recall event path without opening extra windows");
            } catch(Exception e) { throw new RuntimeException(e); }
            finally { c.shutdown(); if(frame!=null) frame.dispose(); }
        });
        System.out.println("History recall checks passed: "+checks+" native="+nativeWindow);
    }
}
