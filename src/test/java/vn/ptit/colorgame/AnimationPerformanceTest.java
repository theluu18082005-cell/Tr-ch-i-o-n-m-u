package vn.ptit.colorgame;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Measures real component paints and queued EDT response; no gameplay timing changes. */
public final class AnimationPerformanceTest {
    private static void layout(Component c) {
        if(c instanceof Container p) { p.doLayout(); for(Component child:p.getComponents()) layout(child); }
    }
    private static void require(boolean value,String text) { if(!value) throw new AssertionError(text); System.out.println("PASS PERF "+text); }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length==0 ? "build/ui-neon" : args[0]); Files.createDirectories(out);
        boolean nativeWindow=args.length>1 && args[1].equals("--native");
        if(nativeWindow && GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Native desktop unavailable");
        int width=nativeWindow ? 800 : 1200, height=nativeWindow ? 640 : 840;
        AtomicReference<GameClient> reference=new AtomicReference<>(); AtomicReference<JFrame> window=new AtomicReference<>();
        CountDownLatch complete=new CountDownLatch(1); List<Double> paints=new ArrayList<>(), queue=new ArrayList<>();
        AtomicReference<Throwable> failure=new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            GameClient c=new GameClient(); reference.set(c);
            c.onMessage(new String[]{"AUTH","qa_alpha","Người chơi Đỏ","7","7","12"});
            c.onMessage(new String[]{"PROFILE","qa_beta","Người chơi Xanh","5","5","12"});
            c.onMessage(new String[]{"ROOM","qa-arena-123","qa_alpha","qa_beta"});
            c.onMessage(new String[]{"TURN","qa-arena-123","1","qa_alpha","30000"});
            for(int i=0;i<6;i++) c.chooseColor(i);
            c.setSize(width,height); layout(c); layout(c);
            if(nativeWindow) {
                JFrame frame=new JFrame("ColorDuel — Neon Arcade Arena / QA"); window.set(frame); frame.setContentPane(c);
                c.setPreferredSize(new Dimension(800,640)); frame.pack(); frame.setLocation(40,24); frame.setVisible(true);
            }
            BufferedImage canvas=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
            final int[] frame={0};
            javax.swing.Timer timer=new javax.swing.Timer(33,e -> {
                try {
                    long start=System.nanoTime(); Graphics2D p=canvas.createGraphics(); c.paint(p); p.dispose();
                    if(frame[0]>=12) paints.add((System.nanoTime()-start)/1e6);
                    long posted=System.nanoTime(); SwingUtilities.invokeLater(() -> queue.add((System.nanoTime()-posted)/1e6));
                    if(++frame[0]==90) { ((javax.swing.Timer)e.getSource()).stop(); complete.countDown(); }
                } catch(Throwable t) { failure.set(t); ((javax.swing.Timer)e.getSource()).stop(); complete.countDown(); }
            }); timer.start();
        });
        try {
            require(complete.await(15,TimeUnit.SECONDS),"90 scheduled paints complete"); if(failure.get()!=null) throw new AssertionError(failure.get());
            SwingUtilities.invokeAndWait(() -> {}); Collections.sort(paints); Collections.sort(queue);
            double median=paints.get(paints.size()/2), p95=paints.get((int)(paints.size()*.95)), q95=queue.get((int)(queue.size()*.95));
            String report=String.format(Locale.ROOT,"mode=%s size=%dx%d frames=%d paint_median_ms=%.2f paint_p95_ms=%.2f edt_queue_p95_ms=%.2f orb_cache=%d%n",
                nativeWindow ? "native" : "headless",width,height,paints.size(),median,p95,q95,GameTheme.orbCacheSize());
            System.out.print(report); Files.writeString(out.resolve(nativeWindow ? "performance-native.txt" : "performance.txt"),report);
            require(p95<100 && q95<100,"No severe paint/EDT stalls over 100ms after warmup");
            require(GameTheme.orbCacheSize()<=60,"Orb sprite cache bounded to 60 entries");
            SwingUtilities.invokeAndWait(() -> {
                GameTheme.setReducedMotion(true); require(!GameTheme.effectsRunning(),"Reduced motion stops ambient scheduler");
                GameTheme.setReducedMotion(false);
            });
            if(nativeWindow) {
                Thread.sleep(200);
                BufferedImage nativeRender=new BufferedImage(800,640,BufferedImage.TYPE_INT_RGB);
                SwingUtilities.invokeAndWait(() -> { GameClient c=reference.get(); require(c.isShowing(),"Native client is showing"); Graphics2D p=nativeRender.createGraphics(); c.printAll(p); p.dispose(); });
                ImageIO.write(nativeRender,"png",out.resolve("game-native-800x640.png").toFile());
                SwingUtilities.invokeAndWait(() -> {
                    try { java.lang.reflect.Field field=GameClient.class.getDeclaredField("slots"); field.setAccessible(true);
                        for(JButton slot:(JButton[])field.get(reference.get())) require(slot.getWidth()>=76 && slot.getHeight()>=96,"Native orb deck stays at least 76x96");
                    } catch(ReflectiveOperationException ex) { throw new RuntimeException(ex); }
                });
                require(reference.get().getWidth()==800 && reference.get().getHeight()==640,"Native content size 800x640");
            }
        } finally { SwingUtilities.invokeAndWait(() -> { reference.get().shutdown(); if(window.get()!=null) window.get().dispose(); }); }
        require(GameTheme.registeredRoots()==0 && !GameTheme.effectsRunning(),"Window disposal releases scheduler");
    }
}
