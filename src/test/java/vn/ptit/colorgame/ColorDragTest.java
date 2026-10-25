package vn.ptit.colorgame;

import javax.swing.*;
import java.awt.datatransfer.*;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.lang.reflect.*;
import java.util.Arrays;

/** Tests real TransferHandler payloads, state guards and submission without a desktop. */
public final class ColorDragTest {
    private static int checks;
    private static Object field(GameClient c,String name) throws Exception { Field f=GameClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(c); }
    private static void set(GameClient c,String name,Object value) throws Exception { Field f=GameClient.class.getDeclaredField(name); f.setAccessible(true); f.set(c,value); }
    private static JButton slot(GameClient c,int i) throws Exception { return ((JButton[])field(c,"slots"))[i]; }
    private static JButton palette(GameClient c,int i) throws Exception { return ((JButton[])field(c,"palette"))[i]; }
    private static int[] order(GameClient c) throws Exception { return (int[])field(c,"arrangement"); }
    private static void check(boolean ok,String name) { if(!ok) throw new AssertionError(name); checks++; System.out.println("PASS DRAG "+name); }
    private static Transferable drag(JButton source) throws Exception {
        TransferHandler h=source.getTransferHandler(); Method m=h.getClass().getDeclaredMethod("createTransferable",JComponent.class); m.setAccessible(true); return (Transferable)m.invoke(h,source);
    }
    private static boolean drop(GameClient c,Transferable payload,int target) throws Exception { JButton b=slot(c,target); return b.getTransferHandler().importData(b,payload); }
    private static void room(GameClient c,String id,String current) {
        c.onMessage(new String[]{"ROOM",id,"alice","bob"}); c.onMessage(new String[]{"TURN",id,"1",current,"30000"});
    }
    private static void mouse(JButton button,int id,int x,int y) {
        button.dispatchEvent(new MouseEvent(button,id,System.currentTimeMillis(),id==MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK,x,y,1,false,id==MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1));
    }
    private static final class DragSpy extends TransferHandler {
        int exports; boolean disarmed;
        @Override public void exportAsDrag(JComponent source,InputEvent event,int action) {
            exports++; JButton b=(JButton)source; disarmed=!b.getModel().isArmed() && !b.getModel().isPressed();
        }
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GameClient c=new GameClient(new AudioManager(sound -> null)),other=new GameClient(new AudioManager(sound -> null));
            try {
                c.onMessage(new String[]{"AUTH","alice","Alice","0","0","0"}); other.onMessage(new String[]{"AUTH","alice","Alice","0","0","0"});
                room(c,"gesture0-test","alice");
                JButton click=palette(c,0); click.setSize(110,48); DragSpy clickSpy=new DragSpy(); TransferHandler original=click.getTransferHandler(); click.setTransferHandler(clickSpy);
                mouse(click,MouseEvent.MOUSE_PRESSED,15,15); mouse(click,MouseEvent.MOUSE_DRAGGED,16,16); mouse(click,MouseEvent.MOUSE_RELEASED,16,16);
                check(clickSpy.exports==0 && order(c)[0]==0,"Small mouse motion remains a normal color click"); click.setTransferHandler(original);
                JButton moving=palette(c,3); moving.setSize(110,48); DragSpy moveSpy=new DragSpy(); original=moving.getTransferHandler(); moving.setTransferHandler(moveSpy);
                int[] unchanged=order(c).clone(); mouse(moving,MouseEvent.MOUSE_PRESSED,15,15); mouse(moving,MouseEvent.MOUSE_DRAGGED,55,30); mouse(moving,MouseEvent.MOUSE_DRAGGED,70,35); mouse(moving,MouseEvent.MOUSE_RELEASED,70,35);
                check(moveSpy.exports==1 && moveSpy.disarmed && Arrays.equals(order(c),unchanged),"Crossing threshold exports once and release cannot trigger extra color click"); moving.setTransferHandler(original);
                room(c,"12345678-drag","alice");
                check(drag(slot(c,0))==null,"Empty slot cannot start a drag");
                check(drop(c,drag(palette(c,3)),4) && order(c)[4]==3 && order(c)[0]==-1,"First color can go directly to fifth slot");
                check(drop(c,drag(palette(c,2)),1) && order(c)[1]==2,"Second color can go to another arbitrary position");
                check(drop(c,drag(slot(c,4)),5) && order(c)[4]==-1 && order(c)[5]==3,"Dragging into an empty slot moves instead of copying");
                check(drop(c,drag(slot(c,1)),5) && order(c)[1]==3 && order(c)[5]==2,"Dragging occupied slots swaps their colors");
                check(drop(c,drag(palette(c,4)),5) && order(c)[5]==4 && palette(c,2).isEnabled(),"Palette replacement returns displaced color to available palette");
                check(drag(palette(c,4))==null,"Used color cannot be duplicated from palette");
                int[] before=order(c).clone(); Transferable cancel=drag(slot(c,1));
                Method finish=slot(c,1).getTransferHandler().getClass().getDeclaredMethod("exportDone",JComponent.class,Transferable.class,int.class); finish.setAccessible(true); finish.invoke(slot(c,1).getTransferHandler(),slot(c,1),cancel,TransferHandler.NONE);
                check(Arrays.equals(before,order(c)),"Cancelled drag does not remove or reorder colors");
                check(!drop(c,drag(slot(c,1)),1) && Arrays.equals(before,order(c)),"Dropping onto same slot is a no-op");
                check(!drop(c,new StringSelection("012345"),0),"Foreign clipboard/text data cannot enter board");
                Transferable ticking=drag(palette(c,0)); c.onMessage(new String[]{"TICK","12345678-drag","1","alice","29000"});
                check(drop(c,ticking,0),"Clock tick alone does not invalidate a drag");
                check(!drop(c,ticking,2),"Reusing completed drag cannot duplicate a color");
                Transferable edited=drag(slot(c,0)); c.chooseColor(1);
                check(!drop(c,edited,3),"Local edit while dragging rejects stale snapshot");
                Transferable oldTurn=drag(slot(c,0)); c.onMessage(new String[]{"TURN","12345678-drag","2","bob","30000"});
                check(!drop(c,oldTurn,3) && drag(palette(c,5))==null,"Opponent turn blocks both drag start and drop");
                c.onMessage(new String[]{"TURN","12345678-drag","3","alice","30000"});
                Transferable expired=drag(slot(c,0)); set(c,"deadline",System.nanoTime()-1);
                check(!drop(c,expired,3),"Expired deadline rejects in-flight drop");
                room(c,"abcdefgh-new","alice"); check(!drop(c,expired,3),"Old room drag cannot modify a new match");
                room(other,"abcdefgh-new","alice"); check(!drop(c,drag(palette(other,0)),0),"Other client cannot drag into this client even with same room and turn");
                Transferable ended=drag(palette(c,0)); c.onMessage(new String[]{"RESULT","abcdefgh-new","bob","SOLVED"});
                check(!drop(c,ended,0),"Result locks pending drag");
                room(c,"ijklmnop-full","alice");
                int[] target={0,3,1,2,5,4}; for(int i=0;i<6;i++) check(drop(c,drag(palette(c,target[i])),i),"Fill arbitrary slot "+(i+1));
                check(Rules.validGuess(Arrays.stream(order(c)).mapToObj(Integer::toString).reduce("",String::concat)) && ((JButton)field(c,"submit")).isEnabled(),"Six distinct dragged colors enable valid submit");
                int[] notes=((int[])field(c,"positionNotes")).clone(); Transferable pending=drag(slot(c,0)); ((JButton)field(c,"submit")).doClick(0);
                check((boolean)field(c,"submitted") && !drop(c,pending,1),"Submitted guess cannot be changed by late drop");
                check(Arrays.equals(notes,(int[])field(c,"positionNotes")),"Drag and submit leave private notes untouched");
            } catch(Exception e) { throw new RuntimeException(e); }
            finally { c.shutdown(); other.shutdown(); }
        });
        System.out.println("Color drag checks passed: "+checks);
    }
}
