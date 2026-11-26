package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.ui.geom.Dimension;
import com.codename1.ui.layouts.Layout;

/** Direct item children keep collection anchors, artwork and batch recycling independent of rows. */
final class TileGrid extends Layout {
    private final int columns;
    TileGrid(int columns) { this.columns = columns; }
    @Override public Dimension getPreferredSize(Container parent) {
        return new Dimension(parent.getWidth(), arrange(parent, false));
    }
    @Override public void layoutContainer(Container parent) { arrange(parent, true); }
    private int arrange(Container parent, boolean place) {
        int gap = CN.convertToPixels(3), inset = CN.convertToPixels(4);
        int width = Math.max(1, parent.getWidth() - inset * 2);
        int tile = Math.max(1, (width - gap * (columns - 1)) / columns), y = gap;
        for (int start = 0; start < parent.getComponentCount(); start += columns) {
            int height = 0, end = Math.min(start + columns, parent.getComponentCount());
            for (int i = start; i < end; i++) {
                Component child = parent.getComponentAt(i);
                if (child.getWidth() != tile) { child.setWidth(tile); child.setShouldCalcPreferredSize(true); }
                height = Math.max(height, child.getPreferredH());
            }
            for (int i = start; i < end; i++) {
                Component child = parent.getComponentAt(i);
                if (place) {
                    child.setX(inset + (i - start) * (tile + gap)); child.setY(y);
                    child.setWidth(tile); child.setHeight(height);
                }
            }
            y += height + gap;
        }
        return y;
    }
}
