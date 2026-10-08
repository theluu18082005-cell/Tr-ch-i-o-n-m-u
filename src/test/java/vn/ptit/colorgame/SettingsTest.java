package vn.ptit.colorgame;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Exercises the shared settings panel, including native client visibility and mixer toggles. */
public final class SettingsTest {
    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    private static Object field(GameClient c,String name) { try { Field f=GameClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(c); } catch(Exception e) { throw new RuntimeException(e); } }
    private static void check(boolean ok,String label) { if(!ok) throw new AssertionError(label); System.out.println("PASS SETTINGS "+label); }
    private static void layout(Component c) { if(c instanceof Container p) { p.doLayout(); for(Component child:p.getComponents()) layout(child); } }
    private static void bounds(Component c,JPanel root) {
        if(c instanceof JCheckBox || c instanceof JButton || c instanceof JSlider) {
            Rectangle r=SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),root);
            check(r.height>=14 && new Rectangle(0,0,root.getWidth(),root.getHeight()).contains(r),"Settings control is reachable: "+c.getName());
            if(c instanceof GameTheme.ActionButton b) check(b.getFontMetrics(b.getFont()).stringWidth(b.getText())<=b.captionWidth()-16,"Preview caption fits");
        }
        if(c instanceof Container p) for(Component child:p.getComponents()) bounds(child,root);
    }
    public static void main(String[] args) throws Exception {
        boolean nativeWindow=args.length>1 && args[1].equals("--native"); Path out=Path.of(args.length==0 ? "build/ui-settings" : args[0]); Files.createDirectories(out);
        AudioManager audio=nativeWindow ? new AudioManager() : new AudioManager(new AudioManagerTest.FakeLoader(false));
        AudioManager.Report ready=AudioManagerTest.settled(audio);
        GameClient[] client=new GameClient[1]; JFrame[] frame=new JFrame[1];
        try {
            edt(() -> { client[0]=new GameClient(audio);
                if(nativeWindow) { frame[0]=new JFrame("ColorDuel / Audio settings QA"); frame[0].setContentPane(client[0]); client[0].setPreferredSize(new Dimension(800,640)); frame[0].pack(); frame[0].setLocation(40,24); frame[0].setVisible(true); }
            });
            GameClient c=client[0];
            edt(() -> {
                JPanel panel=c.settingsComponent(); panel.setSize(340,410); layout(panel); layout(panel); bounds(panel,panel);
                BufferedImage image=new BufferedImage(340,410,BufferedImage.TYPE_INT_RGB); Graphics2D g=image.createGraphics(); panel.printAll(g); g.dispose();
                try { ImageIO.write(image,"png",out.resolve("settings.png").toFile()); } catch(Exception e) { throw new RuntimeException(e); }
                check(((List<?>)field(c,"settingsButtons")).size()==4,"All four headers open a single settings panel");
            });
            edt(() -> { if(nativeWindow) ((JButton)((List<?>)field(c,"settingsButtons")).get(0)).doClick(0); ((JCheckBox)field(c,"musicToggle")).doClick(0); });
            Thread.sleep(250); check(audio.musicEnabled() && AudioManagerTest.settled(audio).musicRunning()==(ready.loaded()>0),"Music checkbox starts cached loop in client");
            if(nativeWindow) edt(() -> {
                JPopupMenu popup=(JPopupMenu)field(c,"settingsMenu"); check(popup.isVisible(),"Actual settings popup is visible");
                check(c.settingsComponent().isShowing(),"Actual settings controls are showing");
                Rectangle r=SwingUtilities.convertRectangle(c.settingsComponent().getParent(),c.settingsComponent().getBounds(),frame[0].getLayeredPane());
                check(r.width==340 && r.height==410,"Native settings uses the verified panel dimensions");
                if(ready.loaded()==15) check(((JLabel)field(c,"audioStatus")).getText().contains("Nhạc sảnh đang phát"),"Native settings confirms active music");
                BufferedImage shot=new BufferedImage(frame[0].getLayeredPane().getWidth(),frame[0].getLayeredPane().getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=shot.createGraphics(); frame[0].getLayeredPane().printAll(g); g.dispose();
                try { ImageIO.write(shot,"png",out.resolve("settings-native.png").toFile()); } catch(Exception e) { throw new RuntimeException(e); }
                popup.setVisible(false);
            });
            edt(() -> { c.onMessage(new String[]{"AUTH","alice","Alice","0","0","0"}); });
            Thread.sleep(100); check(AudioManagerTest.settled(audio).musicRunning()==(ready.loaded()>0),"Switching to lobby keeps background music playing");
            edt(() -> c.onMessage(new String[]{"ROOM","12345678-music","alice","bob"}));
            Thread.sleep(300);
            if(ready.loaded()==15) edt(() -> check(((JLabel)field(c,"audioStatus")).getText().contains("Nhạc thi đấu đang phát"),"Settings status follows current battle track"));
            check(audio.musicScene()==AudioManager.MusicScene.BATTLE && AudioManagerTest.settled(audio).musicRunning()==(ready.loaded()>0),"Client switches to battle loop without touching settings");
            edt(() -> { ((JCheckBox)field(c,"musicToggle")).doClick(0); });
            check(!AudioManagerTest.settled(audio).musicRunning(),"Music checkbox actually stops battle loop");
            edt(() -> { c.onMessage(new String[]{"RESULT","12345678-music","bob","SOLVED"}); c.onMessage(new String[]{"ROOM","12345678-replay","alice","bob"}); });
            check(audio.musicScene()==AudioManager.MusicScene.BATTLE && !AudioManagerTest.settled(audio).musicRunning(),"Result/rematch preserve muted preference");
            edt(() -> { ((JButton)field(c,"musicPreview")).doClick(0); });
            check(audio.musicEnabled() && AudioManagerTest.settled(audio).musicRunning()==(ready.loaded()>0),"Listen button enables current battle track and synchronizes checkbox");
            edt(() -> { ((JSlider)field(c,"musicVolume")).setValue(0); }); check(!AudioManagerTest.settled(audio).musicRunning(),"Music slider zero is silent");
            edt(() -> { ((JSlider)field(c,"musicVolume")).setValue(45); }); check(AudioManagerTest.settled(audio).musicRunning()==(ready.loaded()>0),"Music slider restores playback");
            edt(() -> { ((JCheckBox)field(c,"soundToggle")).doClick(0); }); int played=AudioManagerTest.settled(audio).played(); audio.play(AudioManager.Sound.SUBMIT);
            check(!audio.effectsEnabled() && AudioManagerTest.settled(audio).played()==played && !((JButton)field(c,"soundPreview")).isEnabled(),"Effect checkbox mutes cues and disables preview");
            edt(() -> { ((JCheckBox)field(c,"soundToggle")).doClick(0); ((JButton)field(c,"soundPreview")).doClick(0); });
            check(AudioManagerTest.settled(audio).played()>played || ready.loaded()==0,"Effect preview works after unmuting");
            Thread.sleep(350); edt(() -> { check(!((JLabel)field(c,"audioStatus")).getText().contains("Đang cập nhật"),"Settings feedback resolves without blocking UI"); });
            edt(() -> c.onMessage(new String[]{"RESULT","12345678-replay","alice","LEFT"}));
            Thread.sleep(100); check(audio.musicScene()==AudioManager.MusicScene.LOBBY && AudioManagerTest.settled(audio).musicRunning()==(ready.loaded()>0),"Actual client result returns to lobby music");
            // Prove banner artwork exists left and right; previous implementation left x<width-420 flat.
            edt(() -> { GameTheme.ArenaBanner banner=new GameTheme.ArenaBanner(); banner.setSize(1200,90);
                BufferedImage image=new BufferedImage(1200,90,BufferedImage.TYPE_INT_RGB); Graphics2D g=image.createGraphics(); banner.paint(g); g.dispose();
                GameTheme.RoundPanel plain=new GameTheme.RoundPanel(16); plain.setSize(1200,90);
                BufferedImage oldLeft=new BufferedImage(1200,90,BufferedImage.TYPE_INT_RGB); Graphics2D p=oldLeft.createGraphics(); plain.paint(p); p.dispose();
                check(image.getRGB(60,40)!=oldLeft.getRGB(60,40) && image.getRGB(400,40)!=oldLeft.getRGB(400,40) && image.getRGB(1100,40)!=oldLeft.getRGB(1100,40),"Artwork covers both ends instead of leaving a plain left panel");
            });
            System.out.println("SETTINGS loaded="+ready.loaded()+" native="+nativeWindow+"; audible timbre requires listening review");
        } finally { edt(() -> { if(client[0]!=null) client[0].shutdown(); if(frame[0]!=null) frame[0].dispose(); }); audio.whenClosed().get(6,TimeUnit.SECONDS); }
        System.out.println("Settings checks passed");
    }
}
