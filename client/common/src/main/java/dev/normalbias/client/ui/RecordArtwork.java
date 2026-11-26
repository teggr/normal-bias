package dev.normalbias.client.ui;

import com.codename1.ui.*;
import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import com.codename1.ui.geom.Dimension;

/** Embedded cover with an original deterministic sleeve when art is absent or unavailable. */
final class RecordArtwork extends Component {
    private boolean thumbnail;
    private boolean compactCover;
    private int variation = 1;
    private Image cached;
    private int cachedSize;
    private boolean preparing;
    private boolean coverPixelsPending;
    RecordArtwork(boolean thumbnail) { this(thumbnail, false); }
    RecordArtwork(boolean thumbnail, boolean compactCover) {
        this.thumbnail = thumbnail; this.compactCover = compactCover;
        setUIID("Artwork"); setFocusable(false);
    }
    void thumbnail(boolean value) { thumbnail = value; cached = null; repaint(); }
    void compact(boolean value) { if (compactCover != value) { compactCover = value; cached = null; repaint(); } }
    private String trackId;
    private Image embedded;
    private Track track;
    private LibraryApi api;
    private boolean awaiting;
    private Runnable cancel = () -> {};
    private long requestSerial, requestStarted, retryAt;
    void setTrack(Track track, LibraryApi api) {
        setTrack(track, api, true);
    }
    void setTrack(Track track, LibraryApi api, boolean load) {
        this.track = track; this.api = api;
        setIdentity(track == null ? null : track.title());
        if (load) refresh();
    }
    void suspend() {
        cancel.run(); cancel = () -> {}; awaiting = false; requestSerial++;
        // Distant retained rows keep metadata only, not decoded covers or rendered bitmaps.
        embedded = null; cached = null;
    }
    void release() { suspend(); track = null; embedded = null; cached = null; api = null; }
    /** Called by the shared form timer; failures/missing images cannot freeze a view forever. */
    void refresh() {
        if (coverPixelsPending && embedded != null) {
            coverPixelsPending = false; cached = null; repaint();
        }
        String nextId = track == null ? null : api.artworkKey(track);
        if (!(nextId == null ? trackId == null : nextId.equals(trackId))) {
            cancel.run();
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
        cancel = api.artwork(requested, image -> {
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
        int clipX = g.getClipX(), clipY = g.getClipY(), clipW = g.getClipWidth(), clipH = g.getClipHeight();
        // Native cached-image drawing does not consistently inherit an ancestor's
        // scroll clip. Intersect explicitly in this painter's parent coordinates.
        int originX = getAbsoluteX() - getX(), originY = getAbsoluteY() - getY();
        for (Container parent = getParent(); parent != null; parent = parent.getParent()) {
            if (parent.isScrollableY() || parent.isScrollableX()) {
                // Absolute coordinates locate the scrolled content origin. Add this
                // ancestor's own offset back to locate its stationary viewport.
                g.clipRect(parent.getAbsoluteX() + parent.getScrollX() - originX,
                        parent.getAbsoluteY() + parent.getScrollY() - originY,
                        parent.getWidth(), parent.getHeight());
            }
        }
        int visibleX = g.getClipX(), visibleY = g.getClipY(), visibleW = g.getClipWidth(), visibleH = g.getClipHeight();
        try { if (visibleW > 0 && visibleH > 0) paintSleeve(g, visibleX, visibleY, visibleW, visibleH); }
        finally { g.setClip(clipX, clipY, clipW, clipH); }
    }
    private void paintSleeve(Graphics g, int clipX, int clipY, int clipW, int clipH) {
        if (!thumbnail && !compactCover) { g.setColor(0x141414); g.fillRect(getX(), getY(), getWidth(), getHeight()); }
        int size = thumbnail || compactCover ? Math.min(getWidth(), getHeight()) : Math.min(getWidth() * 70 / 100, getHeight() * 94 / 100);
        if (size <= 0) return;
        if (cached == null || cachedSize != size) {
            if (!preparing) {
                preparing = true;
                // Prepare the immutable sleeve outside active screen drawing.
                CN.callSerially(() -> {
                    preparing = false;
                    if (getWidth() <= 0 || getHeight() <= 0) return;
                    prepareArtwork(size);
                    repaint();
                });
            }
            return;
        }
        int x = getX() + (getWidth() - size) / 2, y = getY() + (getHeight() - size) / 2;
        int left = Math.max(x, clipX), top = Math.max(y, clipY);
        int width = Math.min(x + size, clipX + clipW) - left, height = Math.min(y + size, clipY + clipH) - top;
        if (width <= 0 || height <= 0) return;
        // Reapply the viewport clip and crop the actual bitmap at partial edges.
        g.setClip(clipX, clipY, clipW, clipH);
        if (width == size && height == size) g.drawImage(cached, x, y);
        else g.drawImage(cached.subImage(left - x, top - y, width, height, true), left, top);
    }
    private void prepareArtwork(int size) {
        cachedSize = size;
        int[] coverPixels = null;
        coverPixelsPending = false;
        if (embedded != null) {
            try { coverPixels = embedded.getRGB(); }
            catch (RuntimeException notDecodedYet) { coverPixelsPending = true; }
        }
        int[] pixels = SleeveRaster.create(size, thumbnail, variation,
                coverPixels,
                embedded == null ? 0 : embedded.getWidth(), embedded == null ? 0 : embedded.getHeight());
        // Immutable pixels avoid mutable offscreen graphics/mask state in the native port.
        cached = new RGBImage(pixels, size, size);
    }
}
