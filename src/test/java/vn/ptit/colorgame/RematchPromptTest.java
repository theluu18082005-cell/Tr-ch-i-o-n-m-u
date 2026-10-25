package vn.ptit.colorgame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Rematch handshake remains server-owned; the shared modal cannot leak focus or get stuck. */
public final class RematchPromptTest {
    private static int checks;
    private static Object field(GameClient c,String name) { try { Field f=GameClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(c); } catch(Exception e) { throw new RuntimeException(e); } }
    private static JButton button(GameClient c,String name) { return (JButton)field(c,name); }
    private static void check(boolean ok,String label) { if(!ok) throw new AssertionError(label); checks++; System.out.println("PASS REMATCH "+label); }
    private static void message(GameClient c,String... m) { c.onMessage(m); }
    private static void result(GameClient c,String room) {
        message(c,"ROOM",room,"alice","bob"); message(c,"TURN",room,"1","alice","30000");
        message(c,"MOVE",room,"1","alice","012345","6","GUESS"); message(c,"RESULT",room,"alice","SOLVED");
    }
    private static boolean open(GameClient c) { return ((InvitationOverlay)field(c,"invitationOverlay")).shows((JPanel)field(c,"rematchPanel")); }
    private static void layout(Component c) { if(c instanceof Container p) { p.doLayout(); for(Component child:p.getComponents()) layout(child); } }
    private static void snapshot(GameClient c,Path path) {
        layout(c); layout(c); InvitationOverlay overlay=(InvitationOverlay)field(c,"invitationOverlay"); JPanel panel=(JPanel)field(c,"rematchPanel"); Rectangle r=panel.getBounds();
        check(open(c) && overlay.guardsInstalled() && overlay.getComponentCount()==1,"Exactly one modal card and one input shield");
        check(Math.abs(r.getCenterX()-c.getWidth()/2.0)<=1 && Math.abs(r.getCenterY()-c.getHeight()/2.0)<=1 && new Rectangle(0,0,c.getWidth(),c.getHeight()).contains(r),"Rematch card centered within client");
        for(String name:new String[]{"acceptRematch","returnRematch"}) {
            JButton b=button(c,name); if(!b.isVisible()) continue;
            Rectangle area=SwingUtilities.convertRectangle(b.getParent(),b.getBounds(),panel);
            check(area.height>=40 && new Rectangle(0,0,panel.getWidth(),panel.getHeight()).contains(area),"Rematch action remains reachable: "+name);
        }
        BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB); Graphics2D p=image.createGraphics(); c.paint(p); p.dispose();
        try { ImageIO.write(image,"png",path.toFile()); } catch(Exception e) { throw new RuntimeException(e); }
    }
    public static void main(String[] args) throws Exception {
        boolean nativeWindow=args.length>1 && args[1].equals("--native"); Path out=Path.of(args.length==0 ? "build/ui-rematch" : args[0]); Files.createDirectories(out);
        GameClient[] ref=new GameClient[1]; JFrame[] frame=new JFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                GameClient c=new GameClient(new AudioManager(s -> null)); ref[0]=c;
                message(c,"AUTH","alice","Alice","0","0","0"); message(c,"PROFILE","bob","Bob","2","2","4");
                if(nativeWindow) { frame[0]=new JFrame("ColorDuel / Rematch modal QA"); frame[0].setContentPane(c); c.setPreferredSize(new Dimension(800,640)); frame[0].pack(); frame[0].setLocation(40,24); frame[0].setVisible(true); }
            });
            SwingUtilities.invokeAndWait(() -> {
                GameClient c=ref[0]; InvitationOverlay overlay=(InvitationOverlay)field(c,"invitationOverlay");
                result(c,"12345678-first"); check(!open(c) && button(c,"replay").isEnabled(),"Result stays visible until somebody requests another match");
                button(c,"replay").doClick(0); check(open(c) && (boolean)field(c,"replayRequested") && !button(c,"acceptRematch").isEnabled() && button(c,"returnRematch").isEnabled(),"Requester gets waiting modal with exit option");
                message(c,"RESULT","12345678-first","alice","SOLVED"); check(open(c) && !button(c,"replay").isEnabled(),"Duplicate result cannot re-enable duplicate rematch request");
                message(c,"REMATCH_STATUS","12345678-first","bob"); check(open(c) && !(boolean)field(c,"active"),"Both agree state still waits for server ROOM");
                message(c,"ROOM","abcdefgh-second","alice","bob"); check(!overlay.isVisible() && !overlay.guardsInstalled() && !(boolean)field(c,"replayRequested"),"New ROOM closes prompt and clears waiting state");
                message(c,"REMATCH_STATUS","12345678-first","bob"); message(c,"REMATCH_STATUS","abcdefgh-second","bob"); check(!overlay.isVisible(),"Stale request and requests during active game stay ignored");
                result(c,"ijklmnop-incoming"); message(c,"REMATCH_STATUS","ijklmnop-incoming","carol"); check(!overlay.isVisible(),"Third player cannot create rematch prompt");
                message(c,"REMATCH_STATUS","ijklmnop-incoming","bob"); message(c,"REMATCH_STATUS","ijklmnop-incoming","bob");
                check(open(c) && button(c,"acceptRematch").isEnabled() && overlay.getComponentCount()==1,"Opponent request shows one actionable invitation despite duplicates");
                button(c,"acceptRematch").doClick(0); check(open(c) && (boolean)field(c,"replayRequested") && !button(c,"acceptRematch").isEnabled(),"Agree sends existing rematch flow and waits for server");
                button(c,"returnRematch").doClick(0); message(c,"REMATCH_STATUS","ijklmnop-incoming","bob"); check(open(c) && !button(c,"returnRematch").isEnabled(),"Returning to lobby blocks duplicate actions and late status reopening");
                message(c,"LOBBY"); check(!overlay.isVisible() && !overlay.guardsInstalled() && ((String)field(c,"room")).isEmpty(),"LOBBY releases shared modality");
                result(c,"qrstuvwx-unavailable"); button(c,"replay").doClick(0); message(c,"REMATCH_UNAVAILABLE","qrstuvwx-unavailable","Đối thủ đã rời phòng.");
                check(open(c) && !button(c,"acceptRematch").isVisible() && button(c,"returnRematch").isEnabled(),"Opponent departure switches pending prompt to return-only state");
                button(c,"returnRematch").doClick(0); message(c,"ERROR","BACK","Chưa trở về được."); check(open(c) && button(c,"returnRematch").isEnabled(),"BACK error leaves an actionable recovery instead of frozen modal");
                message(c,"LOBBY"); result(c,"zyxwvuts-error"); button(c,"replay").doClick(0); message(c,"ERROR","REMATCH","Đối thủ không còn trong phòng.");
                check(open(c) && !button(c,"acceptRematch").isVisible() && button(c,"returnRematch").isEnabled(),"REMATCH error exposes return-to-lobby action");
                for(int[] size:new int[][]{{1200,840},{1040,680},{800,640}}) {
                    if(nativeWindow) { c.setPreferredSize(new Dimension(size[0],size[1])); frame[0].pack(); } else c.setSize(size[0],size[1]);
                    String id="demo-"+size[0]+"-rematch"; result(c,id); layout(c); message(c,"REMATCH_STATUS",id,"bob"); snapshot(c,out.resolve((nativeWindow ? "native-" : "")+"incoming-"+size[0]+".png"));
                    button(c,"acceptRematch").doClick(0); snapshot(c,out.resolve((nativeWindow ? "native-" : "")+"waiting-"+size[0]+".png"));
                    message(c,"REMATCH_UNAVAILABLE",id,"Đối thủ đã rời phòng. Bạn có thể trở về sảnh."); snapshot(c,out.resolve((nativeWindow ? "native-" : "")+"unavailable-"+size[0]+".png"));
                }
                message(c,"LOBBY"); message(c,"INVITE","lobby-again","bob","30");
                check(overlay.shows((JPanel)field(c,"invitationPanel")) && button(c,"acceptInvite").isEnabled(),"Normal challenge modal still works after swapping rematch card");
                message(c,"INVITE_CLOSED","lobby-again","Closed"); result(c,"final-12345678"); message(c,"REMATCH_STATUS","final-12345678","bob");
            });
            if(nativeWindow) {
                // Focus assertions require an active app window, as after the user returns to it.
                SwingUtilities.invokeAndWait(() -> { frame[0].toFront(); frame[0].requestFocus(); });
                java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean();
                for(int attempt=0;attempt<30;attempt++) {
                    SwingUtilities.invokeAndWait(() -> active.set(frame[0].isActive())); if(active.get()) break; Thread.sleep(50);
                }
                SwingUtilities.invokeAndWait(() -> check(frame[0].isActive(),"Native QA window is active before testing focus isolation"));
                SwingUtilities.invokeAndWait(() -> button(ref[0],"back").requestFocusInWindow()); SwingUtilities.invokeAndWait(() -> {});
                SwingUtilities.invokeAndWait(() -> {
                    InvitationOverlay overlay=(InvitationOverlay)field(ref[0],"invitationOverlay"); Component focus=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                    System.out.println("NATIVE FOCUS="+(focus==null ? "null" : focus.getClass().getSimpleName()+"/"+focus.getName())+" active="+frame[0].isActive()+" showing="+overlay.isShowing());
                    check(focus!=null && SwingUtilities.isDescendingFrom(focus,overlay),"Native focus cannot escape rematch prompt");
                    Component next=overlay.getFocusTraversalPolicy().getComponentAfter(overlay,button(ref[0],"returnRematch"));
                    check(next!=null && SwingUtilities.isDescendingFrom(next,overlay),"Native Tab cycle stays in rematch prompt");
                });
            }
            SwingUtilities.invokeAndWait(() -> {
                GameClient c=ref[0]; InvitationOverlay overlay=(InvitationOverlay)field(c,"invitationOverlay");
                try { Method disconnected=GameClient.class.getDeclaredMethod("disconnected",String.class); disconnected.setAccessible(true); disconnected.invoke(c,"QA disconnect"); } catch(Exception e) { throw new RuntimeException(e); }
                check(!overlay.isVisible() && !overlay.guardsInstalled(),"Disconnect closes rematch prompt and removes guards");
                message(c,"AUTH","alice","Alice","0","0","0"); result(c,"shutdown-12345678"); button(c,"replay").doClick(0); c.shutdown();
                check(!overlay.isVisible() && !overlay.guardsInstalled(),"Shutdown cleans up rematch modal"); ref[0]=null;
            });
        } finally { SwingUtilities.invokeAndWait(() -> { if(ref[0]!=null) ref[0].shutdown(); if(frame[0]!=null) frame[0].dispose(); }); }
        System.out.println("Rematch prompt checks passed: "+checks+" native="+nativeWindow);
    }
}
