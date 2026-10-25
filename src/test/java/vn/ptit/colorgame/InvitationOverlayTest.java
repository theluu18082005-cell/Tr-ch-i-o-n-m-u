package vn.ptit.colorgame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/** Invitation modality, backdrop, lifecycle and native focus without server dependencies. */
public final class InvitationOverlayTest {
    private static int checks;
    private static Object field(GameClient c,String name) { try { Field f=GameClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(c); } catch(Exception e) { throw new RuntimeException(e); } }
    private static void check(boolean ok,String text) { if(!ok) throw new AssertionError(text); checks++; System.out.println("PASS INVITATION "+text); }
    private static void layout(Component c) { if(c instanceof Container p) { p.doLayout(); for(Component child:p.getComponents()) layout(child); } }
    private static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
    private static void message(GameClient c,String... values) { c.onMessage(values); }
    private static void render(GameClient c,Path path) {
        BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB); Graphics2D p=image.createGraphics(); c.printAll(p); p.dispose();
        try { ImageIO.write(image,"png",path.toFile()); } catch(Exception e) { throw new RuntimeException(e); }
    }
    private static void centered(GameClient c) {
        InvitationOverlay overlay=(InvitationOverlay)field(c,"invitationOverlay"); JPanel panel=(JPanel)field(c,"invitationPanel");
        Rectangle r=panel.getBounds();
        check(overlay.isVisible() && overlay.guardsInstalled() && overlay.getWidth()==c.getWidth() && overlay.getHeight()==c.getHeight(),"Full-client input shield installed");
        check(Math.abs(r.getCenterX()-c.getWidth()/2.0)<=1 && Math.abs(r.getCenterY()-c.getHeight()/2.0)<=1,"Card stays centered");
        check(new Rectangle(0,0,c.getWidth(),c.getHeight()).contains(r) && r.width>=500 && r.height>=280,"Card fits and remains readable");
        for(String name:new String[]{"acceptInvite","rejectInvite","cancelInvite"}) {
            JButton b=(JButton)field(c,name); if(!b.getParent().isVisible()) continue;
            Rectangle bounds=SwingUtilities.convertRectangle(b.getParent(),b.getBounds(),panel);
            check(new Rectangle(0,0,panel.getWidth(),panel.getHeight()).contains(bounds) && bounds.height>=40,"Visible action reachable: "+name);
        }
    }
    public static void main(String[] args) throws Exception {
        boolean nativeWindow=args.length>1 && args[1].equals("--native"); Path out=Path.of(args.length==0 ? "build/ui-invitation" : args[0]); Files.createDirectories(out);
        GameClient[] ref=new GameClient[1]; JFrame[] frame=new JFrame[1];
        try {
            edt(() -> {
                GameClient c=new GameClient(new AudioManager(s -> null)); ref[0]=c;
                check(!c.isOptimizedDrawingEnabled(),"Overlapping screens and modal use correct repaint clipping");
                message(c,"AUTH","alice","Alice","0","0","0"); message(c,"PROFILE","bob","Người chơi Bob","3","3","5");
                if(nativeWindow) { frame[0]=new JFrame("ColorDuel / Invitation modal QA"); frame[0].setContentPane(c); c.setPreferredSize(new Dimension(800,640)); frame[0].pack(); frame[0].setLocation(40,24); frame[0].setVisible(true); }
            });
            edt(() -> {
                GameClient c=ref[0]; InvitationOverlay overlay=(InvitationOverlay)field(c,"invitationOverlay");
                for(int[] size:new int[][]{{1200,840},{1040,680},{800,640}}) {
                    message(c,"INVITE_CLOSED","incoming","Closed");
                    if(nativeWindow) { c.setPreferredSize(new Dimension(size[0],size[1])); frame[0].pack(); } else c.setSize(size[0],size[1]);
                    layout(c); layout(c); message(c,"INVITE","incoming","bob","30"); layout(c); layout(c); centered(c);
                    AtomicInteger backgroundActions=new AtomicInteger(); JButton help=(JButton)field(c,"helpButton"); ActionListener listener=e -> backgroundActions.incrementAndGet(); help.addActionListener(listener);
                    Point point=SwingUtilities.convertPoint(help,help.getWidth()/2,help.getHeight()/2,c);
                    Component hit=SwingUtilities.getDeepestComponentAt(c,point.x,point.y);
                    check(hit==overlay,"Background help button is covered by pointer shield");
                    Point local=SwingUtilities.convertPoint(c,point,hit);
                    hit.dispatchEvent(new MouseEvent(hit,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),InputEvent.BUTTON1_DOWN_MASK,local.x,local.y,1,false,MouseEvent.BUTTON1));
                    hit.dispatchEvent(new MouseEvent(hit,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,local.x,local.y,1,false,MouseEvent.BUTTON1));
                    check(backgroundActions.get()==0 && overlay.isVisible(),"Backdrop click neither activates background nor dismisses invitation"); help.removeActionListener(listener);
                    JTextField search=(JTextField)field(c,"opponentSearch"); String before=search.getText();
                    KeyEvent key=new KeyEvent(search,KeyEvent.KEY_TYPED,System.currentTimeMillis(),0,KeyEvent.VK_UNDEFINED,'x');
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchEvent(key);
                    check(key.isConsumed() && search.getText().equals(before),"Background keyboard input is consumed");
                    render(c,out.resolve((nativeWindow ? "native-" : "")+"received-"+size[0]+".png"));
                    message(c,"INVITE_CLOSED","wrong-id","Unrelated close"); check(overlay.isVisible(),"Unrelated close cannot dismiss current invitation");
                    message(c,"INVITE_CLOSED","incoming","Closed"); check(!overlay.isVisible() && !overlay.guardsInstalled(),"Server close restores input");
                    message(c,"INVITE_SENT","outgoing","bob","30"); layout(c); centered(c);
                    check(((JButton)field(c,"cancelInvite")).isEnabled(),"Outgoing modal exposes cancel action");
                    render(c,out.resolve((nativeWindow ? "native-" : "")+"sent-"+size[0]+".png"));
                    message(c,"INVITE_CLOSED","outgoing","Closed");
                }
                message(c,"INVITE","pending","bob","30"); ((JButton)field(c,"acceptInvite")).doClick(0);
                check(overlay.isVisible() && !((JButton)field(c,"acceptInvite")).isEnabled() && !((JButton)field(c,"rejectInvite")).isEnabled(),"Acceptance stays locked until server response");
                message(c,"INVITE","pending","bob","29"); check(!((JButton)field(c,"acceptInvite")).isEnabled(),"Duplicate invite does not re-enable pending response");
                message(c,"ERROR","RESPOND","Expired"); check(!overlay.isVisible() && !overlay.guardsInstalled(),"Error response releases modal");
                message(c,"INVITE","expired","bob","30");
                try { Field deadline=GameClient.class.getDeclaredField("invitationDeadline"); deadline.setAccessible(true); deadline.setLong(c,System.nanoTime()-1); Method update=GameClient.class.getDeclaredMethod("updateInvitationClock"); update.setAccessible(true); update.invoke(c); } catch(Exception e) { throw new RuntimeException(e); }
                check(overlay.isVisible() && !((JButton)field(c,"acceptInvite")).isEnabled() && ((JProgressBar)field(c,"invitationProgress")).getValue()==0,"Expired countdown blocks late response while waiting for server");
                message(c,"INVITE_CLOSED","expired","Expired"); message(c,"INVITE","starting","bob","30"); message(c,"ROOM","12345678-modal","alice","bob");
                check(!overlay.isVisible() && !overlay.guardsInstalled() && (boolean)field(c,"active"),"ROOM closes modal and permits match updates");
                message(c,"LOBBY"); message(c,"INVITE","focus-test","bob","30");
            });
            if(nativeWindow) {
                edt(() -> { ((JTextField)field(ref[0],"opponentSearch")).requestFocusInWindow(); }); edt(() -> {});
                edt(() -> {
                    GameClient c=ref[0]; InvitationOverlay overlay=(InvitationOverlay)field(c,"invitationOverlay"); Component focus=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    check(focus!=null && SwingUtilities.isDescendingFrom(focus,overlay),"Native background focus request cannot escape modal");
                    Component next=overlay.getFocusTraversalPolicy().getComponentAfter(overlay,(JButton)field(c,"rejectInvite"));
                    check(next!=null && SwingUtilities.isDescendingFrom(next,overlay),"Native Tab cycle stays in invitation");
                });
            }
            edt(() -> {
                GameClient c=ref[0]; InvitationOverlay overlay=(InvitationOverlay)field(c,"invitationOverlay");
                try { Method disconnected=GameClient.class.getDeclaredMethod("disconnected",String.class); disconnected.setAccessible(true); disconnected.invoke(c,"QA disconnect"); } catch(Exception e) { throw new RuntimeException(e); }
                check(!overlay.isVisible() && !overlay.guardsInstalled(),"Disconnect releases all modality guards");
                message(c,"AUTH","alice","Alice","0","0","0"); message(c,"INVITE","shutdown-test","bob","30");
                c.shutdown(); check(!overlay.isVisible() && !overlay.guardsInstalled(),"Shutdown removes pointer, keyboard and focus guards"); ref[0]=null;
            });
        } finally { edt(() -> { if(ref[0]!=null) ref[0].shutdown(); if(frame[0]!=null) frame[0].dispose(); }); }
        System.out.println("Invitation checks passed: "+checks+" native="+nativeWindow);
    }
}
