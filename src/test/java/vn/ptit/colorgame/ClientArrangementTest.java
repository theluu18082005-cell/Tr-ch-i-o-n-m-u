package vn.ptit.colorgame;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.Arrays;

/** Turn handoff must prefill the latest opponent guess without undoing local edits. */
public final class ClientArrangementTest {
    private static int checks;
    private static Object field(GameClient c,String name) throws Exception {
        Field f=GameClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(c);
    }
    private static void check(boolean ok,String text) {
        if(!ok) throw new AssertionError(text); checks++; System.out.println("PASS ARRANGEMENT "+text);
    }
    private static int[] arrangement(GameClient c) throws Exception { return (int[])field(c,"arrangement"); }
    private static void matches(GameClient c,String sequence,String label) throws Exception {
        check(Arrays.equals(arrangement(c),sequence.chars().map(ch -> ch-'0').toArray()),label);
    }
    private static void turn(GameClient c,String room,int number,String player) {
        c.onMessage(new String[]{"TURN",room,""+number,player,"30000"});
    }
    private static void move(GameClient c,String room,int number,String player,String guess,String type) {
        c.onMessage(new String[]{"MOVE",room,""+number,player,guess,"2",type});
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameClient c=new GameClient(); String room="12345678-prefill";
            try {
                c.onMessage(new String[]{"AUTH","alice","Alice","0","0","0"});
                c.onMessage(new String[]{"ROOM",room,"alice","bob"}); turn(c,room,1,"alice");
                check(Arrays.stream(arrangement(c)).allMatch(color -> color==-1),"First turn starts empty");
                for(int i=0;i<6;i++) c.chooseColor(i);
                move(c,room,1,"alice","543210","GUESS");
                matches(c,"012345","Own MOVE does not replace the local board");
                c.clickSlot(0); turn(c,room,2,"bob");
                move(c,room,2,"bob","103254","GUESS"); turn(c,room,3,"alice");
                matches(c,"103254","New turn inherits latest opponent guess instead of own previous order");
                check((int)field(c,"selectedSlot")==-1 && ((JButton)field(c,"submit")).isEnabled(),"Selection reset and complete prefill can be submitted");
                c.clickSlot(0); c.clickSlot(1); matches(c,"013254","Prefilled orbs still swap normally");
                c.onMessage(new String[]{"TICK",room,"3","alice","29000"}); turn(c,room,3,"alice");
                matches(c,"013254","TICK and repeated TURN preserve player edits");
                move(c,room,2,"bob","103254","GUESS");
                matches(c,"013254","Late duplicate opponent MOVE cannot overwrite edits");
                move(c,"87654321-other",3,"bob","543210","GUESS");
                matches(c,"013254","Different room MOVE cannot change this board");
                turn(c,room,4,"bob"); move(c,room,2,"bob","543210","GUESS");
                matches(c,"013254","Old MOVE stays ignored even when the same opponent has the current turn");
                move(c,room,4,"bob","","TIMEOUT"); turn(c,room,5,"alice");
                matches(c,"013254","Opponent timeout with no guess leaves the current arrangement intact");
                turn(c,room,6,"bob"); move(c,room,6,"bob","001234","GUESS"); turn(c,room,7,"alice");
                matches(c,"013254","Malformed or duplicate-color sequence never fills the board");
                turn(c,room,8,"bob"); move(c,room,8,"carol","543210","GUESS");
                matches(c,"013254","A player outside the current turn cannot seed the board");
                move(c,room,8,"bob","543210","GUESS"); turn(c,room,9,"alice");
                matches(c,"543210","A later opponent guess replaces the earlier guess");
                ((JButton)field(c,"clear")).doClick();
                check(Arrays.stream(arrangement(c)).allMatch(color -> color==-1) && !((JButton)field(c,"submit")).isEnabled(),"Clear retains empty-board and submit validation behavior");
                c.chooseColor(4); c.clickSlot(0); ((JButton)field(c,"remove")).doClick();
                check(Arrays.stream(arrangement(c)).allMatch(color -> color==-1),"Removing a selected orb still works");
                c.onMessage(new String[]{"RESULT",room,"bob","SOLVED"});
                move(c,room,9,"bob","543210","GUESS");
                check(Arrays.stream(arrangement(c)).allMatch(color -> color==-1),"Ended match cannot receive a new prefill");
                c.onMessage(new String[]{"ROOM","abcdefgh-replay","alice","bob"}); turn(c,"abcdefgh-replay",1,"bob");
                check(Arrays.stream(arrangement(c)).allMatch(color -> color==-1),"Rematch resets the board instead of reusing old guesses");
                move(c,room,1,"bob","543210","GUESS");
                check(Arrays.stream(arrangement(c)).allMatch(color -> color==-1),"Old match message cannot seed the rematch");
                move(c,"abcdefgh-replay",1,"bob","230145","GUESS"); turn(c,"abcdefgh-replay",2,"alice");
                matches(c,"230145","Non-starting player inherits the first opponent guess");
            } catch(Exception e) { throw new RuntimeException(e); }
            finally { c.shutdown(); }
        });
        System.out.println("Arrangement checks passed: "+checks);
    }
}
