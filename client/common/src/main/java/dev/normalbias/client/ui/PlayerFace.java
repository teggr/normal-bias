package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.ui.geom.Dimension;
import com.codename1.ui.layouts.Layout;

/** Proportions of the reference player; dimensions follow the available width. */
final class PlayerFace extends Container {
    PlayerFace(Component artwork, Component metadata, Component timeline, Component controls, Component status) {
        setUIID("PlayerFace");
        setLayout(new Layout() {
            @Override public Dimension getPreferredSize(Container p) {
                int w = p.getWidth() > 0 ? p.getWidth() : Math.min(CN.getDisplayWidth(), CN.convertToPixels(62));
                return new Dimension(w, w * 184 / 100 + (status.isHidden() ? 0 : status.getPreferredH()));
            }
            @Override public void layoutContainer(Container p) {
                int w = p.getWidth(), inset = w * 4 / 100, y = 0;
                place(artwork, 0, y, w, w * 78 / 100); y += w * 78 / 100;
                place(metadata, inset, y + w * 3 / 100, w - 2 * inset, w * 17 / 100); y += w * 23 / 100;
                place(timeline, inset, y, w - 2 * inset, w * 13 / 100); y += w * 13 / 100;
                place(controls, 0, y, w, w * 70 / 100); y += w * 70 / 100;
                place(status, inset, y, w - 2 * inset, status.isHidden() ? 0 : status.getPreferredH());
            }
            private void place(Component c, int x, int y, int w, int h) {
                c.setX(x); c.setY(y); c.setWidth(w); c.setHeight(h);
            }
        });
        add(artwork).add(metadata).add(timeline).add(controls).add(status);
    }
}
