package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.ui.layouts.*;
import com.codename1.ui.util.UITimer;
import dev.normalbias.client.net.LibraryApi;
import dev.normalbias.client.net.LibraryApi.*;
import java.util.*;
import java.util.function.Function;

/** EDT-owned loading, recycling and navigation; the media player is independent. */
final class LibraryCollection extends Container {
    record Anchor(String path, String identity, int pixel) { }
    private final LibraryApi api;
    private final Function<Item, Component> renderer;
    private final ScrollingRows rows = new ScrollingRows();
    private final AlphabetRail rail;
    private final CollectionWindow window = new CollectionWindow();
    private final Map<String, Component> rendered = new LinkedHashMap<>();
    private final Map<String, String> initials = new HashMap<>();
    private final Map<String, String> paths = new HashMap<>();
    private final Label progress = new Label("Loading library…", "PageInfo");
    private final Button retry = new Button("Retry library", "PagingButton");
    private final Label preview = new Label("", "AlphabetPreview");
    private final Map<String, Section> sections = new HashMap<>();
    private int generation, requestSerial;
    private boolean grouped, alphabetical, loading, changing, failed, shown;
    private String emptyMessage, initialPath, indexPath, pendingJump;
    private Runnable retryAction;
    private UITimer jumpTimer;

    LibraryCollection(LibraryApi api, Function<Item, Component> renderer) {
        super(new BorderLayout()); this.api = api; this.renderer = renderer;
        setUIID("LibraryPanel");
        rows.setUIID("LibraryPanel"); rows.setScrollableY(true); rows.setScrollVisible(false);
        rail = new AlphabetRail(this::scheduleJump, this::showPreview);
        Container list = new Container(new LayeredLayout());
        list.add(rows).add(preview);
        LayeredLayout layout = (LayeredLayout) list.getLayout();
        layout.setInsets(rows, "0");
        layout.setInsets(preview, "auto 2mm auto auto");
        preview.setHidden(true); preview.setVisible(false);
        add(BorderLayout.CENTER, list).add(BorderLayout.EAST, rail);
        Container state = BorderLayout.center(progress).add(BorderLayout.EAST, retry);
        state.setUIID("LibraryPanel"); add(BorderLayout.SOUTH, state);
        retry.addActionListener(e -> { if (retryAction != null) retryAction.run(); });
        rows.addScrollListener((x, y, oldX, oldY) -> {
            if (changing) return;
            highlight();
            if (y != oldY) prefetch(y < oldY ? -1 : 1);
        });
        state(null, null);
    }
    void shown(boolean value) { shown = value; if (value) CN.callSerially(() -> { highlight(); prefetch(1); }); }
    void load(String path, boolean grouped, boolean alphabetical, String emptyMessage, Anchor anchor) {
        generation++; requestSerial++; loading = false; failed = false; pendingJump = null;
        if (jumpTimer != null) jumpTimer.cancel();
        releaseRows(); window.clear(); rows.removeAll(); sections.clear(); rail.available(false);
        this.grouped = grouped; this.alphabetical = alphabetical; this.emptyMessage = emptyMessage;
        initialPath = path; indexPath = null;
        rail.setHidden(!alphabetical); rail.setVisible(alphabetical); rail.active(null);
        showPreview(null);
        fetch(anchor == null ? path : anchor.path(), 0, anchor);
    }
    private void state(String message, Runnable action) {
        progress.setText(message == null ? "" : message);
        retryAction = action;
        progress.getParent().setHidden(message == null && action == null);
        progress.getParent().setVisible(message != null || action != null);
        retry.setHidden(action == null); retry.setVisible(action != null);
        revalidate();
    }
    Anchor anchor() {
        for (Map.Entry<String, Component> entry : rendered.entrySet()) {
            Component row = entry.getValue();
            if (row.getY() + row.getHeight() > rows.getScrollY())
                return new Anchor(paths.get(entry.getKey()), entry.getKey(), row.getY() - rows.getScrollY());
        }
        return null;
    }
    private void fetch(String path, int direction, Anchor restore) {
        if (loading || path == null) return;
        loading = true; failed = false;
        int version = generation, serial = ++requestSerial;
        state("Loading library…", null);
        api.browse(path, grouped, page -> {
            if (version != generation || serial != requestSerial) return;
            loading = false;
            if (pendingJump != null) { performJump(); return; }
            // A deletion can move an old anchor beyond the end. Recover from the live root.
            if (page.items().isEmpty() && page.offset() > 0) { fetch(initialPath, 0, null); return; }
            Anchor before = direction == 0 ? restore : anchor();
            window.accept(page, direction);
            reconcile(before);
            state(null, null);
            if (rendered.isEmpty()) { rows.add(new Label(emptyMessage, "PageInfo")); rows.revalidate(); }
            if (alphabetical && page.index() != null && (indexPath == null || direction == 0)) {
                indexPath = page.index(); loadIndex(version);
            }
            highlight();
            // Fill a tall viewport automatically, without fetching the entire collection.
            CN.callSerially(() -> { if (version == generation) prefetch(direction < 0 ? -1 : 1); });
        }, message -> {
            if (version != generation || serial != requestSerial) return;
            loading = false;
            if (pendingJump != null) { performJump(); return; }
            failed = true;
            state("Library unavailable · " + message, () -> fetch(path, direction, restore));
        });
    }
    private void loadIndex(int version) {
        String path = indexPath;
        api.alphabet(path, result -> {
            if (version != generation || !path.equals(indexPath)) return;
            sections.clear(); for (Section section : result) sections.put(section.initial(), section);
            rail.available(!rendered.isEmpty());
        }, message -> {
            if (version == generation) state(message, () -> loadIndex(version));
        });
    }
    private void reconcile(Anchor anchor) {
        changing = true;
        Map<String, Component> next = new LinkedHashMap<>();
        initials.clear(); paths.clear();
        // Responses near zero can overlap after an unaligned letter jump; identity deduplicates them.
        for (BrowsePage page : window.pages()) {
            api.remember(page);
            for (Item item : page.items()) {
                String id = item.identity();
                if (next.containsKey(id)) continue;
                Component row = rendered.get(id);
                if (row == null) row = renderer.apply(item);
                next.put(id, row); initials.put(id, item.initial()); paths.put(id, page.self());
            }
        }
        for (Map.Entry<String, Component> old : rendered.entrySet()) if (!next.containsKey(old.getKey())) release(old.getValue());
        Component focused = getComponentForm() == null ? null : getComponentForm().getFocused();
        rows.removeAll(); rendered.clear(); rendered.putAll(next);
        for (Component row : rendered.values()) rows.add(row);
        rows.revalidate(); rows.layoutContainer();
        Component target = anchor == null ? null : rendered.get(anchor.identity());
        rows.position(target == null ? 0 : Math.max(0, target.getY() - anchor.pixel()));
        if (focused != null && rows.contains(focused)) focused.requestFocus();
        changing = false;
        putClientProperty("retainedRows", rendered.size());
    }
    private void highlight() {
        Anchor anchor = anchor();
        rail.active(anchor == null ? null : initials.get(anchor.identity()));
        getSemantics().setDescription("Library section " + (anchor == null ? "" : initials.get(anchor.identity())));
    }
    private void prefetch(int direction) {
        if (!shown || changing || loading || failed || rendered.isEmpty() || pendingJump != null) return;
        int y = rows.getScrollY(), height = rows.getHeight();
        Component last = rows.getComponentAt(rows.getComponentCount() - 1);
        if (direction < 0 && y < height && window.previous() != null) fetch(window.previous(), -1, null);
        else if (direction >= 0 && last.getY() + last.getHeight() - y < height * 2 && window.next() != null)
            fetch(window.next(), 1, null);
    }
    private void scheduleJump(String initial) {
        Section section = sections.get(initial);
        if (section == null || section.jump() == null) return;
        pendingJump = section.jump();
        if (jumpTimer != null) jumpTimer.cancel();
        jumpTimer = new UITimer(this::performJump);
        jumpTimer.schedule(130, false, getComponentForm());
    }
    private void performJump() {
        if (pendingJump == null || loading) return;
        String path = pendingJump; pendingJump = null;
        rows.stopScrollMomentum(); fetch(path, 0, null);
    }
    private void showPreview(String initial) {
        preview.setText(initial == null ? "" : initial);
        preview.setHidden(initial == null); preview.setVisible(initial != null); revalidate();
    }
    void refreshArtwork() {
        for (Component row : rendered.values()) {
            RecordArtwork artwork = (RecordArtwork) row.getClientProperty("artwork");
            // Only visible/nearby rows may start speculative artwork work.
            boolean near = row.getY() + row.getHeight() >= rows.getScrollY() - rows.getHeight()
                    && row.getY() <= rows.getScrollY() + rows.getHeight() * 2;
            if (artwork != null) { if (shown && near) artwork.refresh(); else artwork.suspend(); }
        }
    }
    private void release(Component row) {
        RecordArtwork artwork = (RecordArtwork) row.getClientProperty("artwork");
        if (artwork != null) artwork.release();
    }
    private void releaseRows() { for (Component row : rendered.values()) release(row); rendered.clear(); initials.clear(); paths.clear(); }
    void dispose() {
        generation++; requestSerial++; shown = false;
        if (jumpTimer != null) jumpTimer.cancel();
        releaseRows(); window.clear(); rows.removeAll(); sections.clear();
    }
    private static final class ScrollingRows extends Container {
        ScrollingRows() { super(BoxLayout.y()); }
        void position(int y) { if (y != getScrollY()) { stopScrollMomentum(); setScrollY(y); } }
    }
}
