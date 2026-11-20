package dev.normalbias.client.ui;

import com.codename1.ui.*;
import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import com.codename1.ui.geom.Dimension;

/** Embedded cover with an original deterministic sleeve when art is absent or unavailable. */
final class RecordArtwork extends Component {
    private final boolean thumbnail;
    private int variation = 1;
    private Image cached;
    private int cachedSize;
    RecordArtwork(boolean thumbnail) { this.thumbnail = thumbnail; setUIID("Artwork"); setFocusable(false); }
    private String trackId;
    private Image embedded;
    private Track track;
    private LibraryApi api;
    private boolean awaiting;
    private long requestSerial, requestStarted, retryAt;
    void setTrack(Track track, LibraryApi api) {
        this.track = track; this.api = api;
        setIdentity(track == null ? null : track.title());
        refresh();
    }
    /** Called by the shared form timer; failures/missing images cannot freeze a view forever. */
    void refresh() {
        String nextId = track == null ? null : api.artworkKey(track);
        if (!(nextId == null ? trackId == null : nextId.equals(trackId))) {
            trackId = nextId; embedded = null; cached = null; awaiting = false; retryAt = 0;
            requestSerial++;
            repaint();
        }
        if (track == null || embedded != null) return;
        long now = System.currentTimeMillis();
        if ((awaiting && now - requestStarted < 25000) || now < retryAt) return;
        awaiting = true; requestStarted = now;
        long token = ++requestSerial;
        Track requested = track;
        api.artwork(requested, image -> {
            if (token != requestSerial || !nextId.equals(trackId) || !nextId.equals(api.artworkKey(requested))) return;
            awaiting = false; retryAt = System.currentTimeMillis() + 1000;
            if (embedded != image) { embedded = image; cached = null; repaint(); }
        });
    }
    void setIdentity(String identity) {
        int next = identity == null ? 1 : (identity.hashCode() & 0x7fffffff) % 4;
        if (next != variation) { variation = next; cached = null; repaint(); }
    }
    @Override protected Dimension calcPreferredSize() {
        int size = CN.convertToPixels(thumbnail ? 7.5f : 43);
        return new Dimension(size, size);
    }
    @Override public void paint(Graphics g) {
        if (!thumbnail) { g.setColor(0x141414); g.fillRect(getX(), getY(), getWidth(), getHeight()); }
        int size = thumbnail ? Math.min(getWidth(), getHeight()) : Math.min(getWidth() * 70 / 100, getHeight() * 94 / 100);
        if (size <= 0) return;
        if (cached == null || cachedSize != size) {
            cachedSize = size;
            Image sleeve = Image.createImage(size, size, 0xffeee8db);
            Graphics art = sleeve.getGraphics();
            art.setAntiAliased(true);
            art.setColor(0xeee8db); art.fillRect(0, 0, size, size);
            int[] colors = {0x79919c, 0xeebd40, 0xdf754c, 0xa3a878};
            art.setColor(colors[variation]);
            art.fillArc(size / 5, size / 8, size * 3 / 5, size * 3 / 5, 0, 360);
            art.setColor(0x202323);
            for (int row = 0; row < 10; row++) {
                int lastY = 0;
                for (int x = size / 8; x < size * 7 / 8; x++) {
                    int y = (int) (size * (0.45 + row * 0.032 + 0.018 * Math.sin(x * 9.0 / size + row * 0.2)));
                    if (x > size / 8) for (int stroke = 0; stroke < Math.max(1, size / 120); stroke++)
                        art.drawLine(x - 1, lastY + stroke, x, y + stroke);
                    lastY = y;
                }
            }
            art.fillRect(size / 8, size * 84 / 100, size / 5, Math.max(1, size / 150));
            art.fillRect(size * 69 / 100, size * 84 / 100, size / 6, Math.max(1, size / 150));
            if (embedded != null) {
                // Preserve aspect ratio: center-crop to the square sleeve, never stretch.
                float scale = (float) size / Math.min(embedded.getWidth(), embedded.getHeight());
                int width = Math.max(size, Math.round(embedded.getWidth() * scale));
                int height = Math.max(size, Math.round(embedded.getHeight() * scale));
                art.drawImage(embedded, (size - width) / 2, (size - height) / 2, width, height);
            }
            Image mask = Image.createImage(size, size, 0);
            Graphics mg = mask.getGraphics(); mg.setAntiAliased(true); mg.setColor(0xffffff);
            if (thumbnail) mg.fillArc(0, 0, size, size, 0, 360);
            else mg.fillRoundRect(0, 0, size, size, size / 20, size / 20);
            cached = sleeve.applyMask(mask.createMask());
        }
        g.drawImage(cached, getX() + (getWidth() - size) / 2, getY() + (getHeight() - size) / 2);
    }
}
