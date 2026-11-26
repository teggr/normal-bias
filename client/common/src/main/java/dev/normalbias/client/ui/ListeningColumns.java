package dev.normalbias.client.ui;

import com.codename1.ui.CN;
import com.codename1.ui.Component;
import com.codename1.ui.Container;
import com.codename1.ui.geom.Dimension;
import com.codename1.ui.layouts.Layout;

/** Density-aware composition of the existing views; each pane owns its scrolling. */
final class ListeningColumns extends Layout {
    static final int MIN_WIDTH_MM = 180;

    static int playerWidth(int width) {
        return Math.min(CN.convertToPixels(62), width * 32 / 100);
    }
    static int libraryWidth(int width) {
        return width - playerWidth(width) - Math.max(CN.convertToPixels(48), width * 23 / 100);
    }
    @Override public Dimension getPreferredSize(Container parent) {
        return new Dimension(CN.convertToPixels(MIN_WIDTH_MM), CN.convertToPixels(100));
    }
    @Override public void layoutContainer(Container parent) {
        // A port may retain the old parent extent during live resizing. Bound the
        // panes to the current window even before the outer layout catches up.
        int width = Math.min(parent.getWidth(), parent.getPreferredW()), height = parent.getHeight();
        int library = libraryWidth(width), player = playerWidth(width);
        place(parent.getComponentAt(0), 0, library, height);
        place(parent.getComponentAt(1), library, player, height);
        place(parent.getComponentAt(2), library + player, width - library - player, height);
    }
    private void place(Component component, int x, int width, int height) {
        component.setX(x); component.setY(0); component.setWidth(width); component.setHeight(height);
    }
}
