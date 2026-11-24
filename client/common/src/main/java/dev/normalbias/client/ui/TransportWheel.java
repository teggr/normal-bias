package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.ui.geom.Dimension;
import com.codename1.ui.layouts.Layout;

/** Physical transport proportions: central wheel and four separate hardware keys. */
final class TransportWheel extends Container {
    TransportWheel(Button previous, Button play, Button next, Button information, Button queue, Button retry, Button shuffle) {
        setUIID("TransportWheel");
        setLayout(new Layout() {
            @Override public Dimension getPreferredSize(Container parent) {
                int w = parent.getWidth() > 0 ? parent.getWidth() : CN.convertToPixels(60);
                return new Dimension(w, w * 70 / 100);
            }
            @Override public void layoutContainer(Container parent) {
                int w = parent.getWidth(), h = parent.getHeight();
                int d = Math.min(w * 55 / 100, h * 88 / 100);
                int x = (w - d) / 2, y = (h - d) / 2;
                int key = w * 18 / 100, pad = w * 4 / 100;
                place(information, pad, pad, key, key);
                place(queue, w - pad - key, pad, key, key);
                place(retry, pad, h - pad - key, key, key);
                place(shuffle, w - pad - key, h - pad - key, key, key);
                int center = d * 41 / 100;
                place(play, x + (d - center) / 2, y + (d - center) / 2, center, center);
                int side = d * 27 / 100;
                place(previous, x + d / 25, y + (d - side) / 2, side, side);
                place(next, x + d - side - d / 25, y + (d - side) / 2, side, side);
            }
            private void place(Component c, int x, int y, int w, int h) {
                c.setX(x); c.setY(y); c.setWidth(w); c.setHeight(h);
            }
        });
        previous.setUIID("WheelAction");
        next.setUIID("WheelAction");
        add(previous).add(play).add(next).add(information).add(queue).add(retry).add(shuffle);
    }
    @Override public void paintBackground(Graphics g) {
        super.paintBackground(g);
        boolean aa = g.isAntiAliased(); g.setAntiAliased(true);
        g.setColor(0x1b1b1b); g.drawLine(getX(), getY(), getX() + getWidth(), getY());
        int d = Math.min(getWidth() * 55 / 100, getHeight() * 88 / 100);
        int x = getX() + (getWidth() - d) / 2, y = getY() + (getHeight() - d) / 2;
        for (int i = 0; i < 32; i++) {
            int inset = i * d / 100;
            int shade = 20 + i / 3;
            g.setColor((shade << 16) | (shade << 8) | shade);
            g.fillArc(x + inset, y + inset, d - inset * 2, d - inset * 2, 0, 360);
        }
        g.setColor(0x303030); g.drawArc(x, y, d, d, 180, 180);
        g.setAntiAliased(aa);
    }
}
