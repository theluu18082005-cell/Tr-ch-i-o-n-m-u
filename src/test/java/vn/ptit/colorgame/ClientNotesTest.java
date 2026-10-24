package vn.ptit.colorgame;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Notes are private hypotheses, independent of guesses, turns and network state. */
public final class ClientNotesTest {
    private static int checks;
    private static Object field(GameClient c,String name) throws Exception { Field f=GameClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(c); }
    private static void check(boolean ok,String name) { if(!ok) throw new AssertionError(name); checks++; System.out.println("PASS NOTES "+name); }
    private static int[] notes(GameClient c) throws Exception { return (int[])field(c,"positionNotes"); }
    private static void choose(GameClient c,int position,int color) throws Exception {
        ((JButton[])field(c,"noteSlots"))[position].doClick(0);
        ((JMenuItem)((JPopupMenu)field(c,"noteMenu")).getComponent(color+1)).doClick(0);
        ((JPopupMenu)field(c,"noteMenu")).setVisible(false);
    }
    private static void room(GameClient c,String id) {
        c.onMessage(new String[]{"ROOM",id,"alice","bob"});
        c.onMessage(new String[]{"TURN",id,"1","alice","30000"});
    }
    public static void main(String[] args) throws Exception {
        boolean nativeWindow=Arrays.asList(args).contains("--native");
        Path out=Path.of(args.length==0 ? "build/ui-notes" : args[0]); Files.createDirectories(out);
        GameClient[] clients=new GameClient[2]; JFrame[] frame=new JFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                clients[0]=new GameClient(); clients[1]=new GameClient();
                clients[0].onMessage(new String[]{"AUTH","alice","Alice","0","0","0"});
                clients[1].onMessage(new String[]{"AUTH","bob","Bob","0","0","0"});
                for(GameClient c:clients) room(c,"12345678-notes");
                if(nativeWindow) { frame[0]=new JFrame("ColorDuel / Private notes QA"); frame[0].setContentPane(clients[0]); clients[0].setPreferredSize(new Dimension(800,640)); frame[0].pack(); frame[0].setLocation(40,24); frame[0].setVisible(true); }
            });
            SwingUtilities.invokeAndWait(() -> {
                try {
                    GameClient c=clients[0];
                    check(Arrays.stream(notes(c)).allMatch(n -> n==-1),"New match notes are unknown");
                    for(int i=0;i<6;i++) c.chooseColor(i);
                    int[] guess=((int[])field(c,"arrangement")).clone(); long deadline=(long)field(c,"deadline");
                    choose(c,0,4); choose(c,1,4);
                    check(notes(c)[0]==4 && notes(c)[1]==4,"Each position accepts an independent hypothesis, including duplicate colors");
                    check(Arrays.equals(guess,(int[])field(c,"arrangement")) && deadline==(long)field(c,"deadline") && !(boolean)field(c,"submitted"),"Notes do not alter guess, deadline or submission");
                    check(Arrays.stream(notes(clients[1])).allMatch(n -> n==-1),"Opponent client does not receive private notes");
                    choose(c,1,-1); check(notes(c)[1]==-1 && notes(c)[0]==4,"Unknown clears only its own note");
                    c.onMessage(new String[]{"TURN","12345678-notes","2","bob","30000"}); choose(c,2,2);
                    check(notes(c)[2]==2 && !((JButton)field(c,"submit")).isEnabled(),"Notes editable during opponent turn while guess remains locked");
                    c.onMessage(new String[]{"MOVE","12345678-notes","2","bob","543210","2","GUESS"});
                    c.onMessage(new String[]{"TURN","12345678-notes","3","alice","30000"});
                    c.onMessage(new String[]{"TICK","12345678-notes","3","alice","29000"});
                    check(notes(c)[0]==4 && notes(c)[2]==2,"Opponent prefill and clock updates preserve personal notes");
                    ((JButton)field(c,"clear")).doClick(0); check(notes(c)[0]==4,"Clear guess keeps notes");
                    c.chooseColor(1); ((JButton)field(c,"clearNotes")).doClick(0);
                    check(Arrays.stream(notes(c)).allMatch(n -> n==-1) && ((int[])field(c,"arrangement"))[0]==1,"Clear notes leaves game arrangement alone");
                    choose(c,0,3);
                    ((JButton[])field(c,"noteSlots"))[0].doClick(0);
                    JMenuItem oldItem=(JMenuItem)((JPopupMenu)field(c,"noteMenu")).getComponent(6);
                    room(c,"abcdefgh-replay"); oldItem.doClick(0);
                    check(Arrays.stream(notes(c)).allMatch(n -> n==-1),"Rematch resets notes and rejects stale popup actions");
                    choose(c,5,5); c.onMessage(new String[]{"RESULT","abcdefgh-replay","bob","SOLVED"}); oldItem.doClick(0);
                    check(notes(c)[5]==5 && notes(c)[0]==-1 && !((JButton[])field(c,"noteSlots"))[0].isEnabled(),"Result preserves notes internally and disables editing");
                    c.onMessage(new String[]{"LOBBY"}); check(Arrays.stream(notes(c)).allMatch(n -> n==-1),"Leaving room clears notes");
                    room(c,"ijklmnop-final"); choose(c,3,1);
                    Method logout=GameClient.class.getDeclaredMethod("logout"); logout.setAccessible(true); logout.invoke(c);
                    check(Arrays.stream(notes(c)).allMatch(n -> n==-1),"Logout clears private notes");
                    c.onMessage(new String[]{"AUTH","alice","Alice","0","0","0"}); room(c,"qrstuvwx-demo");
                    for(int i=0;i<6;i++) c.chooseColor(i);
                    choose(c,0,0); choose(c,2,2); choose(c,5,5);
                    if(nativeWindow) {
                        ((JButton[])field(c,"noteSlots"))[2].doClick(0);
                        check(((JPopupMenu)field(c,"noteMenu")).isVisible(),"Native note chooser opens inside actual client");
                        BufferedImage image=new BufferedImage(frame[0].getLayeredPane().getWidth(),frame[0].getLayeredPane().getHeight(),BufferedImage.TYPE_INT_RGB);
                        Graphics2D g=image.createGraphics(); frame[0].getLayeredPane().printAll(g); g.dispose(); ImageIO.write(image,"png",out.resolve("notes-native.png").toFile());
                        ((JPopupMenu)field(c,"noteMenu")).setVisible(false);
                    }
                } catch(Exception e) { throw new RuntimeException(e); }
            });
        } finally { SwingUtilities.invokeAndWait(() -> { for(GameClient c:clients) if(c!=null) c.shutdown(); if(frame[0]!=null) frame[0].dispose(); }); }
        System.out.println("Private notes checks passed: "+checks);
    }
}
