package vn.ptit.colorgame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

/** Exercises offline guide, scrolling, ownership and real modeless dialog lifecycle. */
public final class GameGuideTest {
    private static int checks;
    private static void check(boolean ok,String label) { if(!ok) throw new AssertionError(label); checks++; System.out.println("PASS GUIDE "+label); }
    private static Object field(GameClient c,String name) { try { Field f=GameClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(c); } catch(Exception e) { throw new RuntimeException(e); } }
    private static void layout(Component c) { if(c instanceof Container p) { p.doLayout(); for(Component child:p.getComponents()) layout(child); } }
    private static void render(JComponent c,Path path) throws Exception {
        BufferedImage image=new BufferedImage(c.getWidth(),c.getHeight(),BufferedImage.TYPE_INT_RGB); Graphics2D p=image.createGraphics(); c.printAll(p); p.dispose(); ImageIO.write(image,"png",path.toFile());
    }
    public static void main(String[] args) throws Exception {
        boolean nativeWindow=args.length>1 && args[1].equals("--native"); Path out=Path.of(args.length==0 ? "build/ui-guide" : args[0]); Files.createDirectories(out);
        SwingUtilities.invokeAndWait(() -> {
            GameClient client=null; JFrame frame=null;
            try {
                AtomicInteger dismissed=new AtomicInteger(); GameGuide guide=new GameGuide(dismissed::incrementAndGet);
                String text=guide.content();
                check(text.contains("2/6") && text.contains("không cho biết cụ thể vị trí nào"),"Guide explains count-only scoring instead of revealing positions");
                check(text.contains("Ghi chú riêng") && text.contains("Chỉ bạn thấy") && text.contains("30 giây"),"Guide covers private notes and current turn duration");
                check(text.contains("Chơi lại") && text.contains("phím 1–6") && text.contains("+1 điểm"),"Guide covers controls, scoring and rematch");
                for(int height:new int[]{640,616,576}) {
                    guide.setSize(680,height); layout(guide); layout(guide);
                    Rectangle button=SwingUtilities.convertRectangle(guide.closeButton().getParent(),guide.closeButton().getBounds(),guide);
                    check(new Rectangle(0,0,680,height).contains(button) && button.height>=32,"Dismiss action remains reachable at guide height "+height);
                    JScrollPane scroll=guide.scrollPane();
                    check(scroll.getViewport().getViewSize().height>scroll.getViewport().getHeight(),"Long guide scrolls instead of clipping text");
                    scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
                    check(scroll.getVerticalScrollBar().getValue()>0,"Final sections can be reached by scrolling");
                    scroll.getVerticalScrollBar().setValue(0); render(guide,out.resolve("guide-"+height+".png"));
                }
                guide.closeButton().doClick(0); guide.getActionMap().get("close-guide").actionPerformed(new ActionEvent(guide,0,"close-guide"));
                check(dismissed.get()==2,"Button and Escape both invoke close action");
                client=new GameClient(new AudioManager(sound -> null)); final GameClient c=client;
                client.onMessage(new String[]{"AUTH","alice","Alice","0","0","0"}); JButton help=(JButton)field(client,"helpButton");
                check(help.getText().equals("Hướng dẫn chơi") && help.isVisible(),"Lobby provides visible guide entry");
                if(nativeWindow) {
                    frame=new JFrame("ColorDuel / Lobby guide QA"); frame.setContentPane(client); frame.setLocation(40,24);
                    for(int[] size:new int[][]{{1200,840},{1040,680},{800,640}}) {
                        client.setPreferredSize(new Dimension(size[0],size[1])); frame.pack(); frame.setVisible(true); help.doClick(0);
                        JDialog dialog=(JDialog)field(client,"guideDialog");
                        check(dialog!=null && dialog.isShowing() && !dialog.isModal(),"Actual guide opens modelessly at "+size[0]+"x"+size[1]);
                        check(dialog.getOwner()==frame && frame.getBounds().contains(dialog.getBounds()),"Guide fits inside owner window");
                        help.doClick(0); check(field(client,"guideDialog")==dialog,"Repeated help click reuses one dialog");
                        check(field(client,"room").equals("") && !(boolean)field(client,"active"),"Reading guide leaves match state unchanged");
                        render(dialog.getRootPane(),out.resolve("guide-native-"+size[0]+".png"));
                        GameGuide panel=(GameGuide)dialog.getContentPane(); panel.closeButton().doClick(0);
                        check(!dialog.isDisplayable(),"Dismiss button disposes native guide");
                    }
                    help.doClick(0); JDialog escaped=(JDialog)field(client,"guideDialog");
                    GameGuide panel=(GameGuide)escaped.getContentPane(); panel.getActionMap().get("close-guide").actionPerformed(new ActionEvent(panel,0,"close-guide"));
                    check(!escaped.isDisplayable(),"Escape disposes real guide dialog");
                    help.doClick(0); JDialog entering=(JDialog)field(client,"guideDialog"); client.onMessage(new String[]{"ROOM","12345678-guide","alice","bob"});
                    check(!entering.isDisplayable() && (boolean)field(client,"active"),"Entering match closes guide without blocking ROOM handling");
                    client.onMessage(new String[]{"LOBBY"}); help.doClick(0); JDialog closing=(JDialog)field(client,"guideDialog");
                    client.shutdown(); check(!closing.isDisplayable(),"Client shutdown disposes guide window"); client=null;
                }
            } catch(Exception e) { throw new RuntimeException(e); }
            finally { if(client!=null) client.shutdown(); if(frame!=null) frame.dispose(); }
        });
        System.out.println("Guide checks passed: "+checks+" native="+nativeWindow);
    }
}
