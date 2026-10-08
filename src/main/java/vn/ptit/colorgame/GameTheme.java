package vn.ptit.colorgame;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

/** Hallmark · pre-emit critique: P5 H4 E4 S5 R4 V4.
 * Macrostructure: native arena workbench; dual HUD, energy deck, compact combat log.
 * Native Swing design system. Chassis: clipped corners; controls: 10px; badges: pill.
 * Colors describe roles. The six game colors are semantic, independent of the UI accent. */
public final class GameTheme {
    public static final Color BACKGROUND=hex(0x080B18), PANEL=hex(0x121A31), RAISED=hex(0x1B2846), LINE=hex(0x6984AD);
    public static final Color TEXT=hex(0xF2F5FF), MUTED=hex(0xAAB8D3), ACCENT=hex(0x39E7FF), GOLD=hex(0xFFD380), RED=hex(0xFF9AA4);
    public static final Color SELECTED=hex(0x203D57), PRIMARY=ACCENT, ON_PRIMARY=hex(0x081A2B), INPUT=hex(0x0B1226);
    public static final Color BLUE=hex(0x477BFF), PINK=hex(0xFF70B6), GLARE=hex(0xFFFFFF), INK=hex(0x172016), PAPER=hex(0xF9FCF7);
    public static final Color[] COLORS = {
        new Color(204,45,71), new Color(26,156,100), new Color(49,112,221),
        new Color(234,185,43), new Color(135,76,196), new Color(229,116,40)
    };
    private static final Map<Integer, BufferedImage> ORBS = new HashMap<>();
    private static boolean reducedMotion = Boolean.getBoolean("colorduel.reduceMotion");
    private static final Set<Container> ROOTS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final javax.swing.Timer EFFECTS = new javax.swing.Timer(33, e -> {
        for (Container root : new ArrayList<>(ROOTS)) if (root.isShowing()) root.repaint();
    });
    private static final BufferedImage ARENA = loadArena();
    private static final String FONT = System.getProperty("os.name", "").startsWith("Windows") ? "Segoe UI" : "SansSerif";
    private GameTheme() {}

    private static Color hex(int rgb) { return new Color(rgb); }
    public static boolean isReducedMotion() { return reducedMotion; }
    public static void setReducedMotion(boolean reduce) {
        reducedMotion=reduce;
        for(Container root : new ArrayList<>(ROOTS)) updateMotion(root);
        syncEffects();
        for(Container root : new ArrayList<>(ROOTS)) root.repaint();
    }
    private static void updateMotion(Component c) {
        if(c instanceof JCheckBox box && "motion-toggle".equals(box.getName())) box.setSelected(reducedMotion);
        if(c instanceof ActionButton button && reducedMotion) { button.motion.stop(); button.repaint(); }
        if(c instanceof Container parent) for(Component child : parent.getComponents()) updateMotion(child);
    }
    public static void registerRoot(Container root) {
        ROOTS.add(root);
        root.addHierarchyListener(e -> { if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED)!=0) syncEffects(); });
        syncEffects();
    }
    public static void unregisterRoot(Container root) { ROOTS.remove(root); syncEffects(); }
    private static void syncEffects() {
        boolean showing=ROOTS.stream().anyMatch(Component::isShowing);
        if (!reducedMotion && showing) EFFECTS.start(); else EFFECTS.stop();
    }
    static boolean effectsRunning() { return EFFECTS.isRunning(); }
    static int registeredRoots() { return ROOTS.size(); }
    private static double seconds() { return reducedMotion ? 0 : System.nanoTime()/1_000_000_000.0; }
    private static Color alpha(Color c,int a) { return new Color(c.getRed(),c.getGreen(),c.getBlue(),Math.max(0,Math.min(255,a))); }
    private static void glow(Graphics2D p,Color c,float x,float y,float size,int opacity) {
        p.setPaint(new RadialGradientPaint(new Point2D.Float(x,y),Math.max(1,size),new float[]{0,.4f,1},new Color[]{alpha(c,opacity),alpha(c,opacity/3),alpha(c,0)}));
        p.fill(new Ellipse2D.Float(x-size,y-size,size*2,size*2));
    }
    public static Font font(int size, boolean bold) { return new Font(FONT, bold ? Font.BOLD : Font.PLAIN, size); }
    public static Font display(int size) {
        Font candidate = new Font("Bahnschrift", Font.BOLD, size);
        return candidate.canDisplayUpTo("Đấu trường chiến thắng sắc màu") < 0 ? candidate : font(size,true);
    }
    public static Font mono(int size) { return new Font(Font.MONOSPACED, Font.BOLD, size); }
    public static Graphics2D graphics(Graphics g) {
        Graphics2D p = (Graphics2D) g.create();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        p.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        p.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); return p;
    }
    public static void install() {
        try { if (!(UIManager.getLookAndFeel() instanceof javax.swing.plaf.metal.MetalLookAndFeel)) UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel"); }
        catch (Exception ignored) { }
        for (String key : new String[]{"Panel.background","OptionPane.background","Viewport.background"}) UIManager.put(key, PANEL);
        for (String key : new String[]{"Label.foreground","OptionPane.messageForeground","Button.foreground","CheckBox.foreground"}) UIManager.put(key, TEXT);
        UIManager.put("ComboBox.background",INPUT); UIManager.put("ComboBox.foreground",TEXT); UIManager.put("ComboBox.selectionBackground",SELECTED); UIManager.put("ComboBox.selectionForeground",TEXT);
        UIManager.put("Button.background", RAISED); UIManager.put("Button.font", font(13,true));
        UIManager.put("Label.font", font(13,false)); UIManager.put("OptionPane.messageFont", font(14,false));
        UIManager.put("ToolTip.background", PANEL); UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", new LineBorder(LINE)); UIManager.put("ScrollBar.width", 8);
        UIManager.put("CheckBox.icon", new Icon() {
            public int getIconWidth() { return 16; }
            public int getIconHeight() { return 16; }
            public void paintIcon(Component c,Graphics g,int x,int y) {
                Graphics2D p=graphics(g); boolean selected=c instanceof AbstractButton button && button.isSelected();
                p.setColor(selected ? PRIMARY : INPUT); p.fillRoundRect(x,y,15,15,4,4);
                p.setColor(selected ? ACCENT : LINE); p.drawRoundRect(x,y,15,15,4,4);
                if(selected) { p.setColor(ON_PRIMARY); p.setStroke(new BasicStroke(2)); p.drawLine(x+3,y+8,x+6,y+11); p.drawLine(x+6,y+11,x+12,y+4); }
                p.dispose();
            }
        });
    }
    private static BufferedImage loadArena() {
        try (var input = GameTheme.class.getResourceAsStream("/images/color-arena.png")) { return input == null ? null : ImageIO.read(input); }
        catch (IOException e) { return null; }
    }
    public static boolean hasArtwork() { return ARENA != null; }

    /** Clipped arcade chassis. All children remain ordinary Swing components. */
    public static class RoundPanel extends JPanel {
        protected Color edge;
        private long pulseAt;
        protected final int radius = 16;
        public RoundPanel(int ignoredRadius) { setOpaque(false); }
        public void setEdge(Color color) { if(!Objects.equals(edge,color)) { edge=color; repaint(); } }
        public void pulse() { pulseAt=System.nanoTime(); repaint(); }
        protected final Shape chassis(int w,int h) {
            Path2D path=new Path2D.Float(); int cut=12;
            path.moveTo(1,1); path.lineTo(w-cut,1); path.lineTo(w-1,cut);
            path.lineTo(w-1,h-1); path.lineTo(cut,h-1); path.lineTo(1,h-cut); path.closePath(); return path;
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p=graphics(g); int w=getWidth(),h=getHeight(); Shape body=chassis(w,h);
            p.setPaint(new GradientPaint(0,0,alpha(RAISED,248),w,h,alpha(PANEL,248))); p.fill(body);
            Color rim=edge==null ? LINE : edge;
            p.setStroke(new BasicStroke(1)); p.setColor(alpha(rim,edge==null ? 100 : 200)); p.draw(body);
            p.setColor(alpha(rim,220)); p.fillRect(1,16,2,Math.min(36,Math.max(0,h-32)));
            p.setColor(alpha(rim,130)); p.drawLine(w-46,1,w-13,1);
            double age=(System.nanoTime()-pulseAt)/1e9;
            if(!reducedMotion && age<.8) { p.setColor(alpha(rim,(int)(110*(1-age/.8)))); p.setStroke(new BasicStroke(3)); p.draw(body); }
            p.dispose();
        }
    }
    public static final class Backdrop extends JPanel {
        private BufferedImage scaled;
        public Backdrop() { setBackground(BACKGROUND); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p=graphics(g); int w=getWidth(),h=getHeight();
            if(w<=0 || h<=0) { p.dispose(); return; }
            if(scaled==null || scaled.getWidth()!=w || scaled.getHeight()!=h) {
                scaled=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB); Graphics2D b=scaled.createGraphics();
                b.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                b.setColor(BACKGROUND); b.fillRect(0,0,w,h);
                if(ARENA!=null) {
                    double scale=Math.max((double)w/ARENA.getWidth(),(double)h/ARENA.getHeight());
                    int iw=(int)Math.ceil(ARENA.getWidth()*scale),ih=(int)Math.ceil(ARENA.getHeight()*scale);
                    b.drawImage(ARENA,(w-iw)/2,(h-ih)/2,iw,ih,null);
                }
                b.setPaint(new GradientPaint(0,0,alpha(BACKGROUND,190),w,0,alpha(BACKGROUND,228))); b.fillRect(0,0,w,h);
                b.setPaint(new GradientPaint(0,0,alpha(BACKGROUND,60),0,h,alpha(BACKGROUND,195))); b.fillRect(0,0,w,h); b.dispose();
            }
            p.drawImage(scaled,0,0,null);
            // Perspective grid is decorative; image scaling is cached above.
            p.setColor(alpha(BLUE,25));
            for(int i=-6;i<=6;i++) p.drawLine(w/2+i*48,h*2/3,w/2+i*220,h);
            for(int i=0;i<7;i++) { int y=h*2/3+(int)(Math.pow(i/6.0,2)*h/3); p.drawLine(0,y,w,y); }
            double time=seconds();
            glow(p,ACCENT,w*.10f,h*.28f,220,25); glow(p,PINK,w*.8f,h*.6f,250,30);
            for(int i=0;i<24;i++) {
                float x=(float)((i*.61803398875%1)*w+Math.sin(time*.22+i)*14);
                float y=(float)((((i*.381966%1)-time*(.007+i%3*.002))%1+1)%1*h);
                int a=(int)(60+65*(.5+.5*Math.sin(time*1.3+i)));
                float s=i%5==0 ? 3 : 1.5f; p.setColor(alpha(i%3==0 ? GOLD : ACCENT,a)); p.fill(new Ellipse2D.Float(x,y,s,s));
                if(i%7==0) glow(p,ACCENT,x,y,10,a/2);
            }
            p.dispose();
        }
    }
    private static BufferedImage cover(int w,int h) {
        BufferedImage result=new BufferedImage(Math.max(1,w),Math.max(1,h),BufferedImage.TYPE_INT_RGB);
        Graphics2D raw=result.createGraphics(); Graphics2D p=graphics(raw); raw.dispose(); p.setColor(BACKGROUND); p.fillRect(0,0,w,h);
        if(ARENA!=null) { double scale=Math.max((double)w/ARENA.getWidth(),(double)h/ARENA.getHeight());
            int iw=(int)Math.ceil(ARENA.getWidth()*scale),ih=(int)Math.ceil(ARENA.getHeight()*scale); p.drawImage(ARENA,(w-iw)/2,(h-ih)/2,iw,ih,null); }
        p.dispose(); return result;
    }
    public static final class ArenaBanner extends RoundPanel {
        private BufferedImage art;
        public ArenaBanner() { super(16); setName("matchmaking-banner"); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g); Graphics2D p=graphics(g); int w=getWidth(),h=getHeight();
            Shape body=chassis(w,h); Shape clip=p.getClip(); p.clip(body);
            if(w>0 && h>0) {
                if(art==null || art.getWidth()!=w || art.getHeight()!=h) art=cover(w,h);
                p.drawImage(art,0,0,null);
                // Full-width artwork, with a continuous scrim rather than an opaque left block.
                p.setPaint(new GradientPaint(0,0,alpha(BACKGROUND,185),w,0,alpha(BACKGROUND,110))); p.fillRect(0,0,w,h);
                p.setPaint(new GradientPaint(0,0,alpha(PANEL,5),0,h,alpha(PANEL,90))); p.fillRect(0,0,w,h);
            }
            p.setClip(clip); p.setColor(alpha(LINE,140)); p.draw(body); p.dispose();
        }
    }
    public static void slider(JSlider slider) {
        slider.setOpaque(false); slider.setForeground(ACCENT);
        slider.setUI(new BasicSliderUI(slider) {
            @Override protected Dimension getThumbSize() { return new Dimension(14,14); }
            @Override public void paintTrack(Graphics g) {
                Graphics2D p=graphics(g); int y=trackRect.y+trackRect.height/2-2;
                p.setColor(LINE); p.fillRoundRect(trackRect.x,y,trackRect.width,4,4,4);
                p.setColor(ACCENT); p.fillRoundRect(trackRect.x,y,Math.max(0,xPositionForValue(slider.getValue())-trackRect.x),4,4,4); p.dispose();
            }
            @Override public void paintThumb(Graphics g) {
                Graphics2D p=graphics(g); p.setColor(slider.isEnabled() ? ACCENT : MUTED); p.fillOval(thumbRect.x,thumbRect.y,13,13);
                if(slider.hasFocus()) { p.setColor(TEXT); p.drawOval(thumbRect.x-2,thumbRect.y-2,17,17); } p.dispose();
            }
            @Override public void paintFocus(Graphics g) {}
        });
    }
    /** Cached launch poster, with a quiet upper scrim for readable game typography. */
    public static final class ArtPanel extends RoundPanel {
        private BufferedImage art;
        public ArtPanel(boolean portrait) { super(16); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p=graphics(g); int w=getWidth(),h=getHeight(); if(w<=0 || h<=0) { p.dispose(); return; }
            p.clip(new RoundRectangle2D.Double(0,0,w,h,16,16));
            if(art==null || art.getWidth()!=w || art.getHeight()!=h) art=cover(w,h); p.drawImage(art,0,0,null);
            p.setPaint(new GradientPaint(0,0,alpha(BACKGROUND,145),0,h*.45f,alpha(BACKGROUND,0))); p.fillRect(0,0,w,h/2);
            p.setPaint(new GradientPaint(0,h*.65f,alpha(BACKGROUND,0),0,h,alpha(BACKGROUND,235))); p.fillRect(0,h/2,w,h/2);
            double t=seconds(); for(int i=0;i<8;i++) { double a=i*Math.PI/4+t*.12;
                float x=w*.5f+(float)Math.cos(a)*w*.38f,y=h*.48f+(float)Math.sin(a)*h*.28f; glow(p,i%2==0 ? ACCENT : PINK,x,y,12,85); }
            p.dispose();
        }
    }
    public static final class BattlePanel extends RoundPanel {
        private BufferedImage art;
        public BattlePanel() { super(16); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g); Graphics2D p=graphics(g); int w=getWidth(),h=getHeight();
            if(w<=0 || h<=0) { p.dispose(); return; }
            if(art==null || art.getWidth()!=w || art.getHeight()!=h) art=cover(w,h);
            p.clipRect(5,5,w-10,h-10); p.setComposite(AlphaComposite.SrcOver.derive(.13f)); p.drawImage(art,0,0,null); p.dispose();
        }
    }

    public static final class ActionButton extends JButton {
        private boolean primary, danger, quiet, secondary, arena;
        private float hover;
        private long pressedAt;
        private final javax.swing.Timer motion;
        public ActionButton(String text) {
            super(text); setFont(font(13,true)); setForeground(TEXT); setFocusPainted(false);
            setOpaque(false); setContentAreaFilled(false); setBorder(new EmptyBorder(12,18,12,18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setRolloverEnabled(true);
            motion = new javax.swing.Timer(16,e -> {
                float target=getModel().isRollover() && isEnabled() ? 1 : 0;
                hover+=(target-hover)*.3f;
                if (Math.abs(target-hover)<.025f) { hover=target; ((javax.swing.Timer)e.getSource()).stop(); }
                repaint();
            });
            getModel().addChangeListener(e -> {
                if (reducedMotion || !isShowing()) { motion.stop(); hover=getModel().isRollover() ? 1 : 0; repaint(); }
                else if (!motion.isRunning()) motion.start();
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { if(isEnabled()) { pressedAt=System.nanoTime(); repaint(); } }
            });
        }
        public ActionButton primary() { primary=true; repaint(); return this; }
        public ActionButton danger() { danger=true; repaint(); return this; }
        public ActionButton quiet() { quiet=true; repaint(); return this; }
        public ActionButton secondary() { secondary=true; repaint(); return this; }
        public ActionButton arena() { arena=true; repaint(); return this; }
        @Override public Dimension getPreferredSize() {
            Dimension size=super.getPreferredSize();
            return arena && !isPreferredSizeSet() ? new Dimension(size.width+32,size.height) : size;
        }
        int captionWidth() {
            int width=getWidth();
            return width-(arena && isEnabled() && width>=getFontMetrics(getFont()).stringWidth(getText())+58 ? 42 : 0);
        }
        @Override public void removeNotify() { motion.stop(); super.removeNotify(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p=graphics(g); int w=getWidth(),h=getHeight();
            Shape originalClip=p.getClip();
            Color base=primary && isEnabled() ? PRIMARY : quiet ? PANEL : RAISED;
            Color target=primary ? PRIMARY.darker() : SELECTED;
            if (!isEnabled()) { base=RAISED; target=RAISED; }
            Color surface=blend(base,target,hover*.38f);
            p.setPaint(new GradientPaint(0,0,blend(surface,GLARE,primary && isEnabled() ? .22f : .06f),0,h,surface));
            p.fillRoundRect(0,0,w,h,10,10);
            p.clip(new RoundRectangle2D.Float(0,0,w,h,10,10));
            if(hover>0) glow(p,primary ? GLARE : ACCENT,w*.5f,h*.2f,Math.max(w,h)*.65f,(int)(hover*40));
            double age=(System.nanoTime()-pressedAt)/1e9;
            if(!reducedMotion && age<.55) {
                p.setColor(alpha(primary ? ON_PRIMARY : ACCENT,(int)(65*(1-age/.55))));
                float r=(float)(age/.55*Math.max(w,h)); p.fill(new Ellipse2D.Float(w/2f-r,h/2f-r,r*2,r*2));
            }
            if(captionWidth()!=w) {
                p.setColor(alpha(primary ? ON_PRIMARY : ACCENT,40)); p.setStroke(new BasicStroke(1));
                for(int i=0;i<3;i++) { int x=w-34+i*9; p.drawLine(x,8,x+10,h/2); p.drawLine(x+10,h/2,x,h-8); }
                p.setColor(alpha(GLARE,90)); p.drawLine(12,2,w-40,2);
            }
            p.setClip(originalClip);
            p.setColor(hasFocus() ? ACCENT : danger && isEnabled() ? RED : secondary ? ACCENT : LINE);
            p.setStroke(new BasicStroke(hasFocus() ? 2 : 1));
            if (!quiet || hasFocus()) p.drawRoundRect(1,1,w-3,h-3,10,10);
            p.setFont(getFont()); FontMetrics fm=p.getFontMetrics();
            p.setColor(!isEnabled() ? MUTED : primary ? ON_PRIMARY : danger ? RED : secondary ? ACCENT : TEXT);
            int captionWidth=captionWidth();
            String caption=fit(getText(),fm,captionWidth-16);
            p.drawString(caption,Math.max(8,(captionWidth-fm.stringWidth(caption))/2),(h-fm.getHeight())/2+fm.getAscent()+(getModel().isPressed() ? 1 : 0)); p.dispose();
        }
    }
    private static Color blend(Color a,Color b,float t) {
        return new Color((int)(a.getRed()+(b.getRed()-a.getRed())*t),(int)(a.getGreen()+(b.getGreen()-a.getGreen())*t),(int)(a.getBlue()+(b.getBlue()-a.getBlue())*t));
    }
    public static String fit(String text,FontMetrics fm,int width) {
        if (fm.stringWidth(text)<=width) return text;
        int end=text.length(); while(end>0 && fm.stringWidth(text.substring(0,end)+"…")>width) end--;
        return text.substring(0,end)+"…";
    }
    private static double luminance(Color c) {
        double[] channels={c.getRed()/255.0,c.getGreen()/255.0,c.getBlue()/255.0};
        for(int i=0;i<3;i++) channels[i]=channels[i]<=.04045 ? channels[i]/12.92 : Math.pow((channels[i]+.055)/1.055,2.4);
        return channels[0]*.2126+channels[1]*.7152+channels[2]*.0722;
    }
    public static double contrast(Color a,Color b) { double x=luminance(a),y=luminance(b); return (Math.max(x,y)+.05)/(Math.min(x,y)+.05); }
    public static Color inkOn(Color color) { return contrast(INK,color)>contrast(PAPER,color) ? INK : PAPER; }
    private static Border inputBorder(boolean focus) {
        return new CompoundBorder(new LineBorder(focus ? ACCENT : LINE,focus ? 2 : 1,true),new EmptyBorder(focus ? 8 : 9,focus ? 12 : 13,focus ? 8 : 9,focus ? 12 : 13));
    }
    public static void field(JTextField field) {
        if (field instanceof JPasswordField password) { password.setUI(new BasicPasswordFieldUI()); password.setEchoChar('\u2022'); }
        else field.setUI(new BasicTextFieldUI());
        field.setFont(font(14,false)); field.setBackground(INPUT); field.setForeground(TEXT); field.setCaretColor(ACCENT);
        field.setSelectionColor(SELECTED); field.setSelectedTextColor(TEXT); field.setPreferredSize(new Dimension(220,44));
        field.setBorder(inputBorder(false));
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { field.setBorder(inputBorder(true)); }
            @Override public void focusLost(FocusEvent e) { field.setBorder(inputBorder(false)); }
        });
    }
    /** Cached 2x sprites, ten fixed sizes per color; no per-frame gradient allocation. */
    public static void orb(Graphics2D p,int color,float x,float y,float size) {
        if(color<0 || color>=6 || size<=0) return;
        int bucket=Math.min(80,Math.max(12,((int)Math.ceil(size)+7)/8*8));
        int key=color*100+bucket;
        BufferedImage sprite=ORBS.get(key);
        if(sprite==null) {
            int d=bucket*2, pad=d/2; sprite=new BufferedImage(d+pad*2,d+pad*2,BufferedImage.TYPE_INT_ARGB);
            Graphics2D raw=sprite.createGraphics(); Graphics2D q=graphics(raw); raw.dispose(); Color base=COLORS[color];
            glow(q,base,pad+d/2f,pad+d/2f,d*.85f,110);
            q.setPaint(new RadialGradientPaint(new Point2D.Float(pad+d*.32f,pad+d*.25f),d*.85f,
                new float[]{0,.3f,.7f,1},new Color[]{blend(base,GLARE,.8f),blend(base,GLARE,.18f),base.darker(),base.darker().darker()}));
            q.fill(new Ellipse2D.Float(pad,pad,d,d));
            Shape before=q.getClip(); q.clip(new Ellipse2D.Float(pad,pad,d,d));
            q.setStroke(new BasicStroke(Math.max(1,d*.028f),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            for(int i=0;i<3;i++) {
                q.setColor(alpha(blend(base,GLARE,.7f),75-i*15));
                q.draw(new CubicCurve2D.Float(pad+d*.08f,pad+d*(.3f+i*.13f),pad+d*.72f,pad-d*.18f+i*d*.2f,pad+d*.12f,pad+d*1.18f-i*d*.18f,pad+d*.92f,pad+d*(.75f-i*.16f)));
            }
            q.setClip(before);
            q.setColor(alpha(blend(base,GLARE,.7f),220)); q.setStroke(new BasicStroke(2)); q.draw(new Ellipse2D.Float(pad+1,pad+1,d-2,d-2));
            q.setColor(alpha(GLARE,100)); q.setStroke(new BasicStroke(2));
            q.draw(new Arc2D.Float(pad+d*.12f,pad+d*.12f,d*.76f,d*.76f,20,130,Arc2D.OPEN));
            q.setColor(alpha(GLARE,165)); q.fill(new Ellipse2D.Float(pad+d*.24f,pad+d*.19f,d*.27f,d*.12f));
            q.setColor(alpha(base,170)); q.draw(new Arc2D.Float(pad+d*.17f,pad+d*.17f,d*.67f,d*.67f,205,95,Arc2D.OPEN));
            q.dispose(); ORBS.put(key,sprite);
        }
        float pad=size*.5f; p.drawImage(sprite,Math.round(x-pad),Math.round(y-pad),Math.round(size*2),Math.round(size*2),null);
    }
    static int orbCacheSize() { return ORBS.size(); }
    public static final class ColorSlot extends JButton {
        private final int position;
        private int color=-1;
        private boolean selected;
        private long changedAt;
        public ColorSlot(int position) {
            this.position=position; setOpaque(false); setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false);
            setPreferredSize(new Dimension(110,136)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        public void state(int color,boolean selected,boolean enabled) {
            boolean changed=this.color!=color || this.selected!=selected;
            if(!changed && isEnabled()==enabled) return;
            if(changed) changedAt=System.nanoTime();
            this.color=color; this.selected=selected; setEnabled(enabled);
            String description="Vị trí "+(position+1)+": "+(color<0 ? "Chưa chọn" : Rules.COLOR_NAMES[color]);
            getAccessibleContext().setAccessibleName(description); setToolTipText(description); repaint();
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p=graphics(g); int w=getWidth(),h=getHeight();
            p.setColor(selected ? SELECTED : INPUT); p.fillRoundRect(1,1,w-2,h-2,10,10);
            p.setColor(selected || hasFocus() || (isEnabled() && getModel().isRollover()) ? ACCENT : LINE);
            p.setStroke(new BasicStroke(selected || hasFocus() ? 2 : 1)); p.drawRoundRect(1,1,w-3,h-3,10,10);
            p.setFont(font(10,true)); p.setColor(MUTED); p.drawString("0"+(position+1),10,18);
            double age=(System.nanoTime()-changedAt)/1e9;
            int size=Math.min(96,Math.min(w-24,Math.max(36,h-48)));
            if(!reducedMotion && age<.45 && color>=0) {
                size=(int)(size*(.8+.2*Math.min(1,age/.25)));
                float r=(float)(20+age*42); p.setColor(alpha(COLORS[color],(int)(190*(1-age/.45))));
                p.setStroke(new BasicStroke(2)); p.draw(new Ellipse2D.Float(w/2f-r,h/2f-r-1,r*2,r*2));
            }
            if(selected) glow(p,ACCENT,w/2f,h/2f,Math.min(w,h)*.6f,45);
            if (color>=0) orb(p,color,(w-size)/2f,(h-size)/2f-1,size);
            else { p.setColor(LINE); p.setStroke(new BasicStroke(1.5f)); p.drawOval((w-size)/2,(h-size)/2-1,size,size); }
            String caption=color<0 ? "Chọn màu" : Rules.COLOR_NAMES[color]; p.setFont(font(12,color>=0)); p.setColor(color<0 ? MUTED : TEXT);
            p.drawString(caption,(w-p.getFontMetrics().stringWidth(caption))/2,h-11); p.dispose();
        }
    }
    public static final class PaletteButton extends JButton {
        private final int color;
        private boolean used;
        public PaletteButton(int color) {
            super(Rules.COLOR_NAMES[color]); this.color=color; setOpaque(false); setContentAreaFilled(false); setBorder(new EmptyBorder(10,6,10,6));
            setFont(font(12,true)); setFocusPainted(false); setPreferredSize(new Dimension(110,48)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getAccessibleContext().setAccessibleName("Chọn màu "+Rules.COLOR_NAMES[color]+", phím "+(color+1));
        }
        public void setUsed(boolean used) { if(this.used!=used) { this.used=used; repaint(); } }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p=graphics(g); int w=getWidth(),h=getHeight(); p.setColor(used ? SELECTED : getModel().isRollover() && isEnabled() ? RAISED : PANEL);
            p.fillRoundRect(0,0,w,h,10,10); p.setColor(hasFocus() ? ACCENT : LINE); p.drawRoundRect(1,1,w-3,h-3,10,10);
            String caption=Rules.COLOR_NAMES[color]; p.setFont(getFont()); int total=28+p.getFontMetrics().stringWidth(caption),x=Math.max(5,(w-total)/2);
            orb(p,color,x,(h-20)/2f,20); p.setColor(isEnabled() ? TEXT : MUTED); p.drawString(caption,x+28,(h-p.getFontMetrics().getHeight())/2+p.getFontMetrics().getAscent());
            p.setFont(font(10,true)); p.setColor(used ? ACCENT : MUTED);
            if(used) { p.setStroke(new BasicStroke(1.5f)); p.drawLine(w-14,8,w-11,11); p.drawLine(w-11,11,w-7,5); }
            else p.drawString(""+(color+1),w-13,12); p.dispose();
        }
    }
    public static final class Avatar implements Icon {
        private final String user;
        private final int size;
        private final Color tone;
        public Avatar(String user,int size) { this(user,size,new Color[]{ACCENT,PINK,GOLD}[Math.floorMod(user.hashCode(),3)]); }
        public Avatar(String user,int size,Color tone) { this.user=user; this.size=size; this.tone=tone; }
        public int getIconWidth() { return size; }
        public int getIconHeight() { return size; }
        public void paintIcon(Component c,Graphics g,int x,int y) {
            Graphics2D p=graphics(g); p.setColor(blend(INPUT,tone,.18f)); p.fillRoundRect(x,y,size,size,10,10); p.setColor(alpha(tone,100)); p.drawRoundRect(x,y,size-1,size-1,10,10);
            String initial=user.isBlank() ? "?" : user.substring(0,1).toUpperCase(Locale.ROOT);
            p.setColor(tone); p.setFont(display(size/2)); FontMetrics fm=p.getFontMetrics();
            p.drawString(initial,x+(size-fm.stringWidth(initial))/2,y+(size-fm.getHeight())/2+fm.getAscent()); p.dispose();
        }
    }
    public static final class Logo implements Icon {
        public int getIconWidth() { return 36; }
        public int getIconHeight() { return 36; }
        public void paintIcon(Component c,Graphics g,int x,int y) {
            Graphics2D p=graphics(g); for(int i=0;i<6;i++) { double a=i*Math.PI/3; orb(p,i,x+13+(float)Math.cos(a)*10,y+13+(float)Math.sin(a)*10,9); } p.dispose();
        }
    }
    public static final class Badge extends JLabel {
        private Color color;
        public Badge(String text,Color color) { super(text); this.color=color; setForeground(color); setFont(font(12,true)); setBorder(new EmptyBorder(6,10,6,10)); putClientProperty("html.disable",true); }
        @Override protected void paintComponent(Graphics g) { Graphics2D p=graphics(g); p.setColor(SELECTED); p.fillRoundRect(0,0,getWidth(),getHeight(),24,24); p.dispose(); super.paintComponent(g); }
    }
    public static final class Notice extends JLabel {
        public Notice(String text) { super(text); setForeground(MUTED); setFont(font(13,false)); setPreferredSize(new Dimension(330,46)); setMaximumSize(new Dimension(Integer.MAX_VALUE,46)); putClientProperty("html.disable",true); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p=graphics(g); p.setFont(getFont()); p.setColor(getForeground()); FontMetrics fm=p.getFontMetrics();
            int y=fm.getAscent()+1; String line="";
            for(String word : getText().split(" ")) { String next=line.isEmpty() ? word : line+" "+word;
                if(fm.stringWidth(next)>getWidth() && !line.isEmpty()) { p.drawString(line,0,y); y+=fm.getHeight()+3; line=word; } else line=next;
            }
            p.drawString(line,0,y); p.dispose();
        }
    }
    public static final class Metric extends JPanel {
        private final JLabel value=new JLabel("0");
        public Metric(String caption) {
            setOpaque(false); setLayout(new BorderLayout(0,3)); value.setFont(mono(24)); value.setForeground(TEXT);
            JLabel label=new JLabel(caption); label.setFont(font(12,false)); label.setForeground(MUTED); add(value,BorderLayout.CENTER); add(label,BorderLayout.SOUTH);
            setPreferredSize(new Dimension(105,56));
        }
        public void value(String number) { value.setText(number); }
    }
    public static final class PlayerCard extends RoundPanel {
        private boolean turnActive;
        private Color team=ACCENT;
        private final JLabel avatar=new JLabel(),title=new JLabel("Người chơi"),account=new JLabel(" "),stats=new JLabel(" ");
        private final Badge state=new Badge("Đang chờ",ACCENT);
        public PlayerCard() {
            super(16); setLayout(new BorderLayout(12,0)); setBorder(new EmptyBorder(12,16,12,16));
            avatar.setIcon(new Avatar("?",44)); add(avatar,BorderLayout.WEST);
            JPanel lines=new JPanel(new GridLayout(3,1,0,2)); lines.setOpaque(false);
            title.setFont(display(20)); title.setForeground(TEXT); account.setFont(font(11,false)); account.setForeground(MUTED);
            stats.setFont(font(12,false)); stats.setForeground(MUTED);
            title.setMinimumSize(new Dimension(0,24)); account.setMinimumSize(new Dimension(0,17)); stats.setMinimumSize(new Dimension(0,17));
            title.putClientProperty("html.disable",true); account.putClientProperty("html.disable",true);
            lines.add(account); lines.add(title); lines.add(stats); add(lines,BorderLayout.CENTER);
            state.setFont(font(10,true)); state.setBorder(new EmptyBorder(3,6,3,6));
            setPreferredSize(new Dimension(400,96)); setMinimumSize(new Dimension(220,96));
        }
        public void team(Color color) { team=color; setEdge(color); }
        public void update(String user,String name,String points,String wins,boolean mine,boolean turn,boolean active) {
            if(active && turn && !turnActive) pulse(); turnActive=active && turn;
            title.setText(name); title.setToolTipText(name); account.setText((mine ? "PLAYER / BẠN" : "RIVAL / ĐỐI THỦ")+"   @"+user);
            stats.setText(points+" ĐIỂM   /   "+wins+" THẮNG"+(active && turn ? "   • ĐẾN LƯỢT" : ""));
            stats.setForeground(active && turn ? team : MUTED); avatar.setIcon(new Avatar(user,44,team)); setEdge(team);
        }
    }
    public static final class Countdown extends JPanel {
        private final JProgressBar progress;
        public Countdown(JProgressBar progress,JLabel label) {
            this.progress=progress; setOpaque(false); setLayout(new BorderLayout()); setPreferredSize(new Dimension(112,96)); setMinimumSize(new Dimension(96,96));
            label.setHorizontalAlignment(SwingConstants.CENTER); add(label,BorderLayout.CENTER); progress.addChangeListener(e -> repaint());
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p=graphics(g); int s=Math.min(getWidth(),getHeight())-16,x=(getWidth()-s)/2,y=(getHeight()-s)/2;
            Color tone=progress.getValue()<=5000 ? RED : ACCENT;
            if(progress.getValue()>0) glow(p,tone,getWidth()/2f,getHeight()/2f,s*.65f,40);
            p.setColor(INPUT); p.fillOval(x,y,s,s); p.setStroke(new BasicStroke(5,BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND));
            double ratio=(double)progress.getValue()/Rules.TURN_MILLIS;
            for(int i=0;i<30;i++) { p.setColor(i<Math.ceil(ratio*30) ? tone : alpha(LINE,65)); p.draw(new Arc2D.Double(x,y,s,s,90-i*12,-9,Arc2D.OPEN)); }
            p.setStroke(new BasicStroke(1)); p.setColor(alpha(BLUE,140)); p.drawOval(x+7,y+7,s-14,s-14);
            p.setFont(font(9,true)); p.setColor(MUTED); String caption="TURN / 30"; p.drawString(caption,(getWidth()-p.getFontMetrics().stringWidth(caption))/2,y+s-18); p.dispose();
        }
    }
    /** Result occupies the same arena footprint; controls remain reachable below. */
    public static final class ResultPanel extends RoundPanel {
        private boolean winner;
        private String solution="";
        private final JLabel title=new JLabel("VICTORY",SwingConstants.CENTER), detail=new JLabel(" ",SwingConstants.CENTER), score=new JLabel(" ",SwingConstants.CENTER);
        public ResultPanel() {
            super(16); setName("match-result"); setLayout(new GridBagLayout()); setBorder(new EmptyBorder(16,16,92,16));
            JPanel copy=new JPanel(); copy.setOpaque(false); copy.setLayout(new BoxLayout(copy,BoxLayout.Y_AXIS));
            Badge badge=new Badge("MATCH COMPLETE",MUTED); badge.setAlignmentX(.5f); copy.add(badge); copy.add(Box.createVerticalStrut(8));
            title.setFont(display(54)); title.setAlignmentX(.5f); detail.setFont(font(14,false)); detail.setForeground(TEXT); detail.setAlignmentX(.5f);
            score.setFont(mono(20)); score.setAlignmentX(.5f); score.setBorder(new CompoundBorder(new MatteBorder(1,0,0,0,LINE),new EmptyBorder(12,20,0,20)));
            copy.add(title); copy.add(Box.createVerticalStrut(8)); copy.add(detail); copy.add(Box.createVerticalStrut(8)); copy.add(score); add(copy);
        }
        public void showResult(boolean won,String reason,int turns,String sequence) {
            solution=Rules.validGuess(sequence) ? sequence : ""; winner=won; title.setText(won ? "VICTORY" : "DEFEAT"); title.setForeground(won ? GOLD : PINK);
            detail.setText(reason); detail.setToolTipText(reason); score.setForeground(won ? ACCENT : MUTED);
            score.setText((won ? "+1 ĐIỂM" : "+0 ĐIỂM")+"   /   "+turns+" LƯỢT"); setEdge(won ? GOLD : PINK); pulse();
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g); Graphics2D p=graphics(g); Color tone=winner ? GOLD : PINK;
            glow(p,tone,getWidth()/2f,getHeight()*.4f,Math.min(getWidth(),getHeight())*.8f,65);
            int size=Math.min(240,getHeight()-50),x=(getWidth()-size)/2,y=(getHeight()-size)/2-18;
            p.setStroke(new BasicStroke(2)); p.setColor(alpha(tone,50));
            for(int i=0;i<12;i++) p.draw(new Arc2D.Double(x,y,size,size,i*30+seconds()*3,17,Arc2D.OPEN));
            p.setColor(alpha(tone,100));
            for(int i=0;i<3;i++) { int left=24+i*12,right=getWidth()-left; p.drawLine(left,35,left+12,47); p.drawLine(left+12,47,left,59); p.drawLine(right,35,right-12,47); p.drawLine(right-12,47,right,59); }
            if(!solution.isEmpty()) {
                p.setFont(font(11,true)); p.setColor(MUTED); String caption="DÃY MÀU CHIẾN THẮNG";
                p.drawString(caption,(getWidth()-p.getFontMetrics().stringWidth(caption))/2,getHeight()-70);
                for(int i=0;i<6;i++) orb(p,solution.charAt(i)-'0',getWidth()/2f-135+i*48,getHeight()-52,30);
            } p.dispose();
        }
    }
    /** A centered energy deck instead of six stretched form fields. */
    public static final class OrbRack extends JPanel {
        public OrbRack(Component slots) { setOpaque(false); setLayout(null); add(slots); }
        @Override public void doLayout() { int height=Math.min(192,getHeight()); getComponent(0).setBounds(0,(getHeight()-height)/2,getWidth(),height); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D p=graphics(g); int w=getWidth(),h=getHeight();
            glow(p,BLUE,w*.5f,h*.5f,Math.max(1,w*.5f),25); p.setColor(alpha(BLUE,55)); p.setStroke(new BasicStroke(1));
            for(int i=0;i<3;i++) p.draw(new Ellipse2D.Float(w*.1f-i*15,h*.5f-38-i*12,w*.8f+i*30,76+i*24));
            p.setColor(alpha(ACCENT,35)); p.drawLine(12,h/2+66,w-12,h/2+66); p.dispose();
        }
    }
    /** Battle controls stay above history at small widths; neither pane owns outer scrolling. */
    public static final class ArenaLayout extends JPanel {
        public ArenaLayout(Component board,Component history) { setOpaque(false); setLayout(null); add(board); add(history); }
        @Override public void doLayout() {
            int w=getWidth(),h=getHeight();
            Component history=getComponent(1);
            if(history instanceof JPanel panel && panel.getLayout() instanceof BorderLayout border) {
                Component heading=border.getLayoutComponent(BorderLayout.NORTH); if(heading!=null) heading.setVisible(w>=1050);
            }
            if(w>=1050) { int side=Math.min(370,w/3); getComponent(0).setBounds(0,0,w-side-16,Math.min(h,480)); getComponent(1).setBounds(w-side,0,side,h); }
            else { int main=Math.min(360,Math.max(320,h-120)); getComponent(0).setBounds(0,0,w,main); getComponent(1).setBounds(0,main+12,w,Math.max(0,h-main-12)); }
        }
    }
    /** Painted above the client; never creates input-blocking components or changes game state. */
    public static final class Celebration {
        private long started;
        public void start() { started=System.nanoTime(); }
        public void clear() { started=0; }
        public boolean active() { return !reducedMotion && started!=0 && System.nanoTime()-started<2_800_000_000L; }
        public void paint(Graphics2D p,int width,int height) {
            if(!active()) return;
            double t=(System.nanoTime()-started)/1e9,fade=Math.min(1,(2.8-t)/.8);
            for(int i=0;i<68;i++) {
                double angle=(i%2==0 ? -.6 : Math.PI+.6)+(i*.618033%1)*1.2,speed=100+i%9*27;
                float x=(float)(width*(i%2==0 ? .12 : .88)+Math.cos(angle)*speed*t),y=(float)(height*.2+Math.sin(angle)*speed*t+75*t*t);
                Graphics2D q=(Graphics2D)p.create(); q.translate(x,y); q.rotate(angle+t*(i%2==0 ? 3 : -3));
                q.setColor(alpha(i%3==0 ? GOLD : i%3==1 ? ACCENT : TEXT,(int)(210*fade))); q.fillRoundRect(-3,-5,6,10,2,2); q.dispose();
            }
        }
    }
    /** Content stays usable below the desktop breakpoint; no reparenting during live updates. */
    public static final class ResponsiveSplit extends JPanel implements Scrollable {
        private final int sideWidth,breakpoint,compactMainHeight;
        private final boolean hideMainCompact;
        public ResponsiveSplit(Component main,Component side,int sideWidth,int breakpoint,int compactMainHeight,boolean hideMainCompact) {
            this.sideWidth=sideWidth; this.breakpoint=breakpoint; this.compactMainHeight=compactMainHeight; this.hideMainCompact=hideMainCompact;
            setOpaque(false); setLayout(null); add(main); add(side);
        }
        @Override public void doLayout() {
            int w=getWidth(),h=getHeight(),gap=24; Component main=getComponent(0),side=getComponent(1);
            int sh=side instanceof JComponent component && component.getClientProperty("maxHeight") instanceof Integer max ? Math.min(h,max) : h;
            boolean compact=w<breakpoint; main.setVisible(!compact || !hideMainCompact);
            if(compact && hideMainCompact) { int fw=Math.min(500,w); side.setBounds((w-fw)/2,(h-sh)/2,fw,sh); }
            else if(compact) { int mh=Math.min(compactMainHeight,Math.max(200,h-210)); main.setBounds(0,0,w,mh); side.setBounds(0,mh+gap,w,Math.max(0,h-mh-gap)); }
            else { int sw=Math.min(sideWidth,w/2); main.setBounds(0,0,w-sw-gap,h); side.setBounds(w-sw,(h-sh)/2,sw,sh); }
        }
        @Override public Dimension getPreferredSize() {
            int width=getParent() instanceof JViewport viewport ? viewport.getWidth() : getWidth();
            return new Dimension(Math.max(1,width),width>0 && width<breakpoint && !hideMainCompact ? compactMainHeight+540 : 640);
        }
        public Dimension getPreferredScrollableViewportSize() { return new Dimension(1050,640); }
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction) { return 32; }
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction) { return Math.max(32,r.height-32); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return hideMainCompact || getParent()==null || getParent().getWidth()>=breakpoint; }
    }
    public static void tabs(JTabbedPane tabs) {
        tabs.setOpaque(false); tabs.setFont(font(14,true)); tabs.setForeground(MUTED); tabs.setBackground(BACKGROUND);
        tabs.setUI(new BasicTabbedPaneUI() {
            @Override protected void installDefaults() { super.installDefaults(); tabInsets=new Insets(12,18,12,18); contentBorderInsets=new Insets(16,0,0,0); tabAreaInsets=new Insets(0,0,0,0); }
            @Override protected void paintTabBackground(Graphics g,int placement,int i,int x,int y,int w,int h,boolean selected) {
                if(selected) { Graphics2D p=graphics(g); p.setColor(SELECTED); p.fillRoundRect(x,y,w,h,10,10); p.dispose(); }
            }
            @Override protected void paintText(Graphics g,int placement,Font font,FontMetrics fm,int i,String title,Rectangle rect,boolean selected) { g.setFont(font); g.setColor(selected ? ACCENT : MUTED); g.drawString(title,rect.x,rect.y+fm.getAscent()); }
            @Override protected void paintTabBorder(Graphics g,int placement,int i,int x,int y,int w,int h,boolean selected) { }
            @Override protected void paintContentBorder(Graphics g,int placement,int i) { }
            @Override protected void paintFocusIndicator(Graphics g,int placement,Rectangle[] rects,int i,Rectangle icon,Rectangle text,boolean selected) {
                if(tabs.hasFocus() && selected) { g.setColor(ACCENT); g.drawRect(rects[i].x+2,rects[i].y+2,rects[i].width-4,rects[i].height-4); }
            }
        });
    }
    public static JScrollPane scroll(JTable table) { JScrollPane s=scroll((JComponent)table); s.setColumnHeaderView(table.getTableHeader()); return s; }
    public static JScrollPane scroll(JComponent content) {
        JComponent view=content instanceof Scrollable ? content : new ViewportPanel(content);
        JScrollPane s=new JScrollPane(view); s.setBorder(new EmptyBorder(0,0,0,0)); s.getViewport().setBackground(PANEL); s.setBackground(PANEL); s.getVerticalScrollBar().setUnitIncrement(32);
        for(JScrollBar bar : new JScrollBar[]{s.getVerticalScrollBar(),s.getHorizontalScrollBar()}) {
            bar.setUI(new BasicScrollBarUI() {
                @Override protected void paintTrack(Graphics g,JComponent c,Rectangle r) { g.setColor(PANEL); g.fillRect(r.x,r.y,r.width,r.height); }
                @Override protected void paintThumb(Graphics g,JComponent c,Rectangle r) { Graphics2D p=graphics(g); p.setColor(LINE); p.fillRoundRect(r.x,r.y,r.width,r.height,8,8); p.dispose(); }
                @Override protected JButton createDecreaseButton(int o) { return zero(); }
                @Override protected JButton createIncreaseButton(int o) { return zero(); }
                private JButton zero() { JButton b=new JButton(); b.setPreferredSize(new Dimension(0,0)); return b; }
            });
        }
        return s;
    }
    private static final class ViewportPanel extends JPanel implements Scrollable {
        ViewportPanel(JComponent content) { super(new BorderLayout()); setOpaque(false); add(content,BorderLayout.CENTER); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r,int o,int d) { return 32; }
        public int getScrollableBlockIncrement(Rectangle r,int o,int d) { return Math.max(32,r.height-32); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return getParent()!=null && getPreferredSize().height<getParent().getHeight(); }
    }
    public static Color rowColor(int row) { return row%2==0 ? PANEL : INPUT; }
    public static JTable table(DefaultTableModel model) {
        JTable table=new JTable(model) {
            @Override public void doLayout() {
                Object value=getClientProperty("compactColumns");
                if(value instanceof int[] columns) for(int index : columns) {
                    TableColumn column=getColumnModel().getColumn(index); boolean compact=getWidth()<540;
                    column.setMinWidth(compact ? 0 : 44); column.setMaxWidth(compact ? 0 : Integer.MAX_VALUE); column.setPreferredWidth(compact ? 0 : 62);
                }
                super.doLayout();
            }
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if(getRowCount()==0 && getHeight()>44) {
                    Graphics2D p=graphics(g); p.setFont(font(14,true)); p.setColor(TEXT); String heading=String.valueOf(getClientProperty("emptyHeading")); if(heading.equals("null")) heading="Chưa có dữ liệu";
                    p.drawString(heading,Math.max(16,(getWidth()-p.getFontMetrics().stringWidth(heading))/2),getHeight()>85 ? 48 : 24);
                    String text=String.valueOf(getClientProperty("emptyText")); if(text.equals("null")) text="Dữ liệu sẽ xuất hiện tại đây.";
                    p.setFont(font(12,false)); p.setColor(MUTED); text=fit(text,p.getFontMetrics(),getWidth()-32);
                    if(getHeight()>56) p.drawString(text,Math.max(16,(getWidth()-p.getFontMetrics().stringWidth(text))/2),getHeight()>85 ? 72 : 48); p.dispose();
                }
            }
        };
        table.setBackground(PANEL); table.setForeground(TEXT); table.setSelectionBackground(SELECTED); table.setSelectionForeground(TEXT);
        table.setRowHeight(56); table.setFont(font(14,false)); table.setShowGrid(false); table.setIntercellSpacing(new Dimension(0,0)); table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col) {
                super.getTableCellRendererComponent(t,value,selected,focus,row,col); putClientProperty("html.disable",true); setBorder(new EmptyBorder(0,14,0,14));
                String text=value==null ? "" : value.toString(); boolean number=text.matches("[0-9]+(/6)?"); setFont(number ? mono(13) : font(13,false));
                setBackground(selected ? SELECTED : rowColor(row)); setForeground(text.equals("Thắng") || text.equals("Đang rỗi") ? ACCENT : text.equals("Thua") ? RED : TEXT);
                setHorizontalAlignment(number ? CENTER : LEFT); setToolTipText(text); return this;
            }
        });
        JTableHeader header=table.getTableHeader(); header.setBackground(RAISED); header.setReorderingAllowed(false); header.setPreferredSize(new Dimension(1,42));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col) {
                super.getTableCellRendererComponent(t,value,false,false,row,col); setFont(font(12,true)); setBackground(RAISED); setForeground(MUTED); setBorder(new EmptyBorder(0,6,0,6)); return this;
            }
        });
        return table;
    }
}
