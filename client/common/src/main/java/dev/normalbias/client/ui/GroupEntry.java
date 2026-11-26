package dev.normalbias.client.ui;

import com.codename1.components.SpanLabel;
import com.codename1.ui.*;
import com.codename1.ui.geom.Dimension;
import com.codename1.ui.layouts.*;
import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;

/** One group and action, presented as the original row or an artwork-led tile. */
final class GroupEntry extends Container {
    private final Button open;
    private final String name;
    private final RecordArtwork art = new RecordArtwork(true);
    private final Container rowCopy;
    private final SpanLabel tileTitle;
    private final SpanLabel tileSubtitle;
    private boolean tiled;

    GroupEntry(Button open, String name, String subtitle, Track representative, LibraryApi api) {
        this.open = open; this.name = name;
        rowCopy = BoxLayout.encloseY(open, new Label(subtitle, "Muted"));
        tileTitle = new SpanLabel(name, "TileTitle");
        tileSubtitle = new SpanLabel(subtitle, "Muted");
        art.setTrack(representative, api, false);
        putClientProperty("artwork", art);
        setLeadComponent(open);
        row();
    }
    void tiled(boolean value) {
        if (tiled == value) return;
        tiled = value;
        open.remove(); art.remove(); removeAll();
        art.thumbnail(!value); art.compact(value);
        if (!value) { rowCopy.addComponent(0, open); row(); return; }
        open.setText(""); open.setUIID("TileAction");
        setUIID("LibraryTile");
        setLayout(new Layout() {
            @Override public Dimension getPreferredSize(Container parent) {
                int width = parent.getWidth() > 0 ? parent.getWidth() : CN.convertToPixels(28);
                fitCopy(width);
                return new Dimension(width, width + CN.convertToPixels(2) + tileTitle.getPreferredH() + tileSubtitle.getPreferredH());
            }
            @Override public void layoutContainer(Container parent) {
                int w = parent.getWidth(); fitCopy(w);
                place(open, 0, 0, w, parent.getHeight());
                place(art, 0, 0, w, w);
                int y = w + CN.convertToPixels(1);
                place(tileTitle, 0, y, w, tileTitle.getPreferredH());
                y += tileTitle.getPreferredH();
                place(tileSubtitle, 0, y, w, tileSubtitle.getPreferredH());
            }
        });
        add(open).add(art).add(tileTitle).add(tileSubtitle);
        setShouldCalcPreferredSize(true);
    }
    private void fitCopy(int width) {
        for (SpanLabel label : new SpanLabel[]{tileTitle, tileSubtitle}) {
            if (label.getWidth() != width) {
                label.setPreferredSize(null); label.setWidth(width); label.setPreferredW(width); label.setShouldCalcPreferredSize(true);
            }
        }
    }
    private void row() {
        open.setText(name); open.setUIID("TrackTitle");
        setUIID("TrackRow"); setLayout(new BorderLayout());
        add(BorderLayout.CENTER, rowCopy).add(BorderLayout.WEST, art);
        setShouldCalcPreferredSize(true);
    }
    private static void place(Component c, int x, int y, int w, int h) {
        c.setX(x); c.setY(y); c.setWidth(w); c.setHeight(h);
    }
}
