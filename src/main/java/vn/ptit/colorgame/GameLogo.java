package vn.ptit.colorgame;

import javax.swing.Icon;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** ColorDuel identity: six puzzle slots held between opposing neon arena brackets. */
final class GameLogo implements Icon {
    private static final Map<Integer,BufferedImage> IMAGES=new ConcurrentHashMap<>();
    private final BufferedImage image;
    GameLogo(int size) { image=image(size); }
    @Override public int getIconWidth() { return image.getWidth(); }
    @Override public int getIconHeight() { return image.getHeight(); }
    @Override public void paintIcon(Component c,Graphics g,int x,int y) { g.drawImage(image,x,y,null); }
    static List<Image> windowIcons() { return List.of(image(16),image(24),image(32),image(48),image(64),image(128),image(256)); }
    static BufferedImage image(int size) {
        if(size<16 || size>512) throw new IllegalArgumentException("Logo size must be 16..512");
        return IMAGES.computeIfAbsent(size,GameLogo::render);
    }
    private static BufferedImage render(int size) {
        int pixels=size<=64 ? size*4 : size<=256 ? size*2 : size;
        BufferedImage source=new BufferedImage(pixels,pixels,BufferedImage.TYPE_INT_ARGB);
        Graphics2D p=source.createGraphics();
        p.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); p.scale(pixels/100.0,pixels/100.0);
        Path2D plate=new Path2D.Float(); plate.moveTo(20,6); plate.lineTo(80,6); plate.lineTo(94,20); plate.lineTo(94,80); plate.lineTo(80,94); plate.lineTo(20,94); plate.lineTo(6,80); plate.lineTo(6,20); plate.closePath();
        p.setPaint(new GradientPaint(14,10,new Color(29,48,77),78,92,new Color(5,11,25))); p.fill(plate);
        p.setColor(new Color(242,245,255,32)); p.setStroke(new BasicStroke(1.5f)); p.draw(plate);
        Path2D left=new Path2D.Float(); left.moveTo(46,6); left.lineTo(20,6); left.lineTo(6,20); left.lineTo(6,80); left.lineTo(20,94); left.lineTo(46,94);
        Path2D right=new Path2D.Float(); right.moveTo(54,6); right.lineTo(80,6); right.lineTo(94,20); right.lineTo(94,80); right.lineTo(80,94); right.lineTo(54,94);
        bracket(p,left,GameTheme.ACCENT); bracket(p,right,GameTheme.PINK);
        for(int i=0;i<6;i++) {
            float x=21+(i%3)*21,y=27+(i/3)*28; Color color=GameTheme.COLORS[i];
            p.setColor(new Color(2,6,18)); p.fill(new RoundRectangle2D.Float(x-2,y-2,20,20,5,5));
            p.setPaint(new LinearGradientPaint(x,y,x+14,y+16,new float[]{0,.42f,1},new Color[]{color.brighter(),color,color.darker()}));
            p.fill(new RoundRectangle2D.Float(x,y,16,16,3,3));
            p.setColor(new Color(255,255,255,165)); p.setStroke(new BasicStroke(.9f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); p.draw(new Line2D.Float(x+3,y+2.5f,x+12,y+2.5f));
        }
        p.setStroke(new BasicStroke(1)); p.setColor(new Color(107,145,186,75)); p.draw(new Line2D.Float(20,49,80,49));
        if(size>=32) {
            Path2D spark=new Path2D.Float(); spark.moveTo(54,43); spark.lineTo(44,51); spark.lineTo(49,51); spark.lineTo(46,57); spark.lineTo(57,48); spark.lineTo(52,48); spark.closePath();
            p.setColor(new Color(231,251,255)); p.fill(spark);
        }
        p.dispose();
        if(pixels==size) return source;
        BufferedImage result=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB); Graphics2D down=result.createGraphics();
        down.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC); down.drawImage(source,0,0,size,size,null); down.dispose(); return result;
    }
    private static void bracket(Graphics2D p,Shape shape,Color color) {
        p.setStroke(new BasicStroke(10,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); p.setColor(new Color(color.getRed(),color.getGreen(),color.getBlue(),24)); p.draw(shape);
        p.setStroke(new BasicStroke(4,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); p.setColor(color); p.draw(shape);
    }
}
