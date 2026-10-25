package vn.ptit.colorgame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.beans.*;
import java.util.Arrays;

/** In-window modal: cached blurred backdrop, pointer shield and per-client focus trap. */
final class InvitationOverlay extends JPanel {
    private final JComponent background;
    private JComponent card;
    private BufferedImage snapshot;
    private int snapshotWidth,snapshotHeight;
    private Component previousFocus;
    private boolean guardsInstalled;
    private final KeyEventDispatcher keys;
    private final VetoableChangeListener focus;
    private Window focusWindow;
    private final WindowAdapter wakeFocus=new WindowAdapter() {
        @Override public void windowGainedFocus(WindowEvent e) { focusModal(); }
    };

    InvitationOverlay(JComponent background,JComponent card) {
        this.background=background; this.card=card;
        setName("invitation-overlay"); setOpaque(false); setLayout(null); add(card);
        setFocusable(true); setFocusCycleRoot(true); setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
        getAccessibleContext().setAccessibleName("Hộp thoại lời mời thi đấu");
        getAccessibleContext().setAccessibleDescription("Chọn phản hồi trong hộp thoại. Giao diện phía sau đang bị khóa.");
        MouseAdapter shield=new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { e.consume(); requestFocusInWindow(); }
            @Override public void mouseReleased(MouseEvent e) { e.consume(); }
            @Override public void mouseClicked(MouseEvent e) { e.consume(); }
            @Override public void mouseDragged(MouseEvent e) { e.consume(); }
            @Override public void mouseMoved(MouseEvent e) { e.consume(); }
        };
        addMouseListener(shield); addMouseMotionListener(shield); addMouseWheelListener(MouseEvent::consume);
        keys=event -> {
            if(!isVisible() || !(event.getSource() instanceof Component source) || !backgroundTarget(source)) return false;
            event.consume(); requestFocusInWindow(); return true;
        };
        focus=event -> {
            if(isVisible() && event.getNewValue() instanceof Component target && backgroundTarget(target))
                throw new PropertyVetoException("Invitation owns focus until resolved",event);
        };
        setVisible(false);
    }
    private boolean backgroundTarget(Component target) {
        Container client=background.getParent();
        return client!=null && SwingUtilities.isDescendingFrom(target,client) && !SwingUtilities.isDescendingFrom(target,this);
    }
    void showCard(JComponent next) {
        if(card!=next) { card.setVisible(false); remove(card); card=next; add(card); }
        card.setVisible(true);
        String name=card.getAccessibleContext().getAccessibleName();
        getAccessibleContext().setAccessibleName(name==null ? "Hộp thoại lời mời thi đấu" : name);
        open(); revalidate(); repaint(); SwingUtilities.invokeLater(this::focusModal);
    }
    boolean shows(JComponent candidate) { return isVisible() && card==candidate; }
    private void focusModal() {
        if(!isShowing()) return;
        Component current=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if(current==null || !SwingUtilities.isDescendingFrom(current,this)) requestFocusInWindow();
    }
    void open() {
        if(isVisible()) return;
        Component current=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        previousFocus=current!=null && SwingUtilities.isDescendingFrom(current,background) ? current : null;
        snapshot=null; snapshotWidth=0; snapshotHeight=0; card.setVisible(true); setVisible(true);
        KeyboardFocusManager manager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(keys); manager.addVetoableChangeListener("focusOwner",focus); guardsInstalled=true;
        focusWindow=SwingUtilities.getWindowAncestor(this); if(focusWindow!=null) focusWindow.addWindowFocusListener(wakeFocus);
        revalidate(); repaint(); focusModal(); SwingUtilities.invokeLater(this::focusModal);
    }
    void close() {
        setVisible(false); card.setVisible(false); snapshot=null;
        if(guardsInstalled) {
            KeyboardFocusManager manager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
            manager.removeKeyEventDispatcher(keys); manager.removeVetoableChangeListener("focusOwner",focus); guardsInstalled=false;
        }
        if(focusWindow!=null) { focusWindow.removeWindowFocusListener(wakeFocus); focusWindow=null; }
        Component restore=previousFocus; previousFocus=null;
        if(restore!=null) SwingUtilities.invokeLater(() -> { if(!isVisible() && restore.isShowing() && restore.isEnabled()) restore.requestFocusInWindow(); });
    }
    boolean guardsInstalled() { return guardsInstalled; }
    @Override public void doLayout() {
        int width=Math.min(540,Math.max(1,getWidth()-40));
        int height=Math.min(card.getPreferredSize().height,Math.max(1,getHeight()-40));
        card.setBounds((getWidth()-width)/2,(getHeight()-height)/2,width,height);
    }
    @Override protected void paintComponent(Graphics g) {
        if(getWidth()<=0 || getHeight()<=0) return;
        if(snapshot==null || snapshotWidth!=getWidth() || snapshotHeight!=getHeight()) captureBackdrop();
        Graphics2D p=GameTheme.graphics(g);
        if(snapshot!=null) p.drawImage(snapshot,0,0,getWidth(),getHeight(),null);
        p.setColor(new Color(2,5,15,155)); p.fillRect(0,0,getWidth(),getHeight());
        Rectangle r=card.getBounds(); p.setColor(new Color(0,0,0,110)); p.fillRoundRect(r.x-10,r.y-6,r.width+20,r.height+22,28,28); p.dispose();
    }
    private void captureBackdrop() {
        int width=Math.max(3,getWidth()/5),height=Math.max(3,getHeight()/5);
        BufferedImage small=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB); Graphics2D p=small.createGraphics();
        p.setColor(GameTheme.BACKGROUND); p.fillRect(0,0,width,height);
        p.scale(width/(double)Math.max(1,background.getWidth()),height/(double)Math.max(1,background.getHeight())); background.printAll(p); p.dispose();
        float[] weights=new float[9]; Arrays.fill(weights,1f/9);
        snapshot=new ConvolveOp(new Kernel(3,3,weights),ConvolveOp.EDGE_NO_OP,null).filter(small,null);
        snapshotWidth=getWidth(); snapshotHeight=getHeight();
    }
}
