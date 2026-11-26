package dev.normalbias.client.car;

import com.codename1.car.*;
import com.codename1.ui.CN;
import com.codename1.ui.Image;
import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import dev.normalbias.client.player.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Timer;

/** System-template projection of the shared player; no separate playback policy or theme. */
public final class CarListening extends CarApplication {
    private final LibraryApi api;
    private final Player player;
    private CarContext context;

    public CarListening(LibraryApi api, Player player) {
        this.api = api;
        this.player = player;
    }

    @Override public CarScreen onCreateRootScreen(CarContext context) {
        this.context = context;
        return new Home();
    }
    @Override public void onCarDisconnected() { context = null; }

    /** Called by the phone's existing player change callback on the EDT. */
    public void refresh() {
        if (context != null && context.isConnected() && context.getTopScreen() != null) {
            context.getTopScreen().invalidate();
        }
    }

    private final class Home extends CarScreen {
        @Override protected void onResume() { invalidate(); }
        @Override protected CarTemplate onCreateTemplate() {
            Track track = player.queue.current();
            return new CarListTemplate().setTitle("Normal Bias")
                    .addRow(new CarRow("Library").setText("All songs · Albums · Artists").setBrowsable(true)
                            .setOnAction(c -> c.pushScreen(new Library())))
                    .addRow(new CarRow("Now Playing").setText(track == null ? "Choose a track from Library" : track.title())
                            .setBrowsable(true).setOnAction(c -> c.pushScreen(new Controls())));
        }
    }

    private final class Library extends CarScreen {
        @Override protected CarTemplate onCreateTemplate() {
            CarListTemplate list = new CarListTemplate().setTitle("Library");
            addCategory(list, "All songs", "songs", false);
            addCategory(list, "Albums", "albums", true);
            addCategory(list, "Artists", "artists", true);
            if (list.getSections().isEmpty()) list.addRow(new CarRow("Library unavailable")
                    .setText("Reconnect on your phone, then try again."));
            return list;
        }
        private void addCategory(CarListTemplate list, String title, String view, boolean grouped) {
            final String path;
            try { path = api.browsePath(view, "", null, null, true); }
            catch (IllegalStateException unavailable) { return; } // No advertised collection affordance.
            list.addRow(new CarRow(title).setBrowsable(true).setOnAction(c ->
                    c.pushScreen(new Browse(title, path, grouped, view.equals("artists")))));
        }
    }

    /** One server page, split into head-unit-sized windows; Back retains the parent's window. */
    private final class Browse extends CarScreen {
        private String title;
        private boolean grouped, artists;
        private record Parent(String title, String path, boolean grouped, boolean artists, LibraryApi.BrowsePage page, int start) {}
        private final List<Parent> parents = new ArrayList<>();
        private String path;
        private LibraryApi.BrowsePage page;
        private int start, version;
        private boolean loading;
        private String error;
        private Timer deadline;
        private final Map<String, Image> covers = new HashMap<>();
        private final List<Runnable> cancelCovers = new ArrayList<>();

        Browse(String title, String path, boolean grouped, boolean artists) {
            this.title = title; this.path = path; this.grouped = grouped; this.artists = artists;
        }
        @Override protected void onCreate() { load(path, 0); }
        @Override protected void onResume() { invalidate(); }
        @Override protected void onDestroy() { version++; cancelDeadline(); clearCovers(); }
        private void clearCovers() {
            for (Runnable cancel : cancelCovers) cancel.run();
            cancelCovers.clear(); covers.clear();
        }
        private void loadCovers(int request) {
            for (LibraryApi.Item item : page.items()) {
                Track track = item.track() != null ? item.track() : new Track(item.group().trackId(), "", "", "", 0);
                cancelCovers.add(api.artwork(track, image -> {
                    if (request != version || image == null) return;
                    covers.put(track.id(), image.scaledSmallerRatio(96, 96));
                    invalidate();
                }));
            }
        }
        private void cancelDeadline() {
            if (deadline != null) { deadline.cancel(); deadline = null; }
        }
        private void load(String target, int window) {
            int request = ++version;
            path = target; start = window; loading = true; error = null;
            cancelDeadline(); clearCovers();
            deadline = CN.setTimeout(16000, () -> {
                if (request != version || !loading) return;
                version++; cancelDeadline(); loading = false;
                error = "Check the service connection and try again.";
                invalidate();
            });
            invalidate();
            api.browse(target, grouped, loaded -> {
                if (request != version) return;
                cancelDeadline(); api.remember(loaded); page = loaded;
                start = window < 0 ? Math.max(0, ((page.items().size() - 1) / windowSize()) * windowSize()) : window;
                loading = false; loadCovers(request); invalidate();
            }, message -> {
                if (request != version) return;
                cancelDeadline(); loading = false; error = message; invalidate();
            });
        }
        private void openGroup(String name, String contents) {
            parents.add(new Parent(title, path, grouped, artists, page, start));
            title = name; grouped = artists; artists = false;
            load(contents, 0);
        }
        private void backToCollection() {
            Parent parent = parents.remove(parents.size() - 1);
            version++; cancelDeadline(); clearCovers();
            title = parent.title(); path = parent.path(); grouped = parent.grouped(); artists = parent.artists();
            page = parent.page(); start = parent.start(); loading = false; error = null;
            api.remember(page); loadCovers(version); invalidate();
        }
        private int windowSize() { return Math.max(1, getContext().getListRowLimit() - (parents.isEmpty() ? 2 : 3)); }
        @Override protected CarTemplate onCreateTemplate() {
            CarListTemplate list = new CarListTemplate().setTitle(title).setLoading(loading);
            if (!parents.isEmpty()) list.addRow(new CarRow("Back to " + parents.get(parents.size() - 1).title())
                    .setOnAction(c -> backToCollection()));
            if (loading) return list.addRow(new CarRow("Loading library…"));
            if (error != null) return list.addRow(new CarRow("Retry library").setText(error).setOnAction(c -> load(path, start)));
            if (page != null) api.remember(page);
            if (page == null || page.items().isEmpty()) return list.addRow(new CarRow("No songs found")
                    .setText("Choose another collection or add music on your phone."));
            int count = windowSize();
            for (int i = start; i < Math.min(page.items().size(), start + count); i++) {
                LibraryApi.Item item = page.items().get(i);
                if (item.track() != null) {
                    Track track = item.track();
                    CarRow row = new CarRow(track.title()).setText(track.description()).setImage(covers.get(track.id()));
                    if (api.canPlay(track)) row.setOnAction(c -> { api.remember(page); player.playTrack(track); c.pushScreen(new Controls()); });
                    list.addRow(row);
                } else {
                    LibraryApi.Group group = item.group();
                    String name = artists ? label(group.artist(), "Unknown artist") : label(group.album(), "Unknown album");
                    String detail = (artists ? "" : label(group.artist(), "Unknown artist") + " · ")
                            + group.trackCount() + (group.trackCount() == 1 ? " song" : " songs");
                    CarRow row = new CarRow(name).setText(detail).setImage(covers.get(group.trackId()));
                    if (item.contents() != null) row.setBrowsable(true).setOnAction(c ->
                            openGroup(name, item.contents()));
                    list.addRow(row);
                }
            }
            if (start > 0 || page.previous() != null) list.addRow(new CarRow("Earlier")
                    .setOnAction(c -> {
                        if (start > 0) { start = Math.max(0, start - count); invalidate(); }
                        else load(page.previous(), -1);
                    }));
            if (start + count < page.items().size() || page.next() != null) list.addRow(new CarRow("More")
                    .setOnAction(c -> {
                        if (start + count < page.items().size()) { start += count; invalidate(); }
                        else load(page.next(), 0);
                    }));
            return list;
        }
    }
    private static String label(String value, String fallback) { return value == null || value.isEmpty() ? fallback : value; }

    /** Queue/current-track art is bounded to visible rows and loaded outside template rendering. */
    private abstract class IllustratedScreen extends CarScreen {
        private List<String> identities = new ArrayList<>();
        private final Map<String, Image> images = new HashMap<>();
        private final List<Runnable> subscriptions = new ArrayList<>();
        private int artworkVersion;
        protected void showCovers(List<Track> tracks) {
            List<String> ids = new ArrayList<>();
            for (Track track : tracks) ids.add(track.id());
            if (identities.equals(ids)) return;
            releaseCovers(); identities = ids;
            int request = artworkVersion;
            CN.callSerially(() -> {
                if (request != artworkVersion) return;
                for (Track track : tracks) subscriptions.add(api.artwork(track, image -> {
                    if (request != artworkVersion || image == null) return;
                    images.put(track.id(), image.scaledSmallerRatio(96, 96)); invalidate();
                }));
            });
        }
        protected Image cover(Track track) { return images.get(track.id()); }
        private void releaseCovers() {
            artworkVersion++;
            for (Runnable cancel : subscriptions) cancel.run();
            subscriptions.clear(); images.clear(); identities = new ArrayList<>();
        }
        @Override protected void onDestroy() { releaseCovers(); }
    }

    // The pinned desktop head unit omits header-action strips. Keep its actions visible.
    private static boolean usesRowActions() {
        return Boolean.parseBoolean(System.getProperty("normalbias.carRowActions", "false"));
    }

    private final class QueueScreen extends IllustratedScreen {
        private int offset;
        @Override protected void onResume() { invalidate(); }
        @Override protected CarTemplate onCreateTemplate() {
            CarListTemplate list = new CarListTemplate().setTitle("Queue · " + player.queue.size() + (player.queue.size() == 1 ? " song" : " songs"));
            if (player.queue.size() == 0) { showCovers(new ArrayList<>()); return list.addRow(new CarRow("Queue is empty")
                    .setText("Choose a track from Library or add tracks on your phone.")); }

            if (player.queue.size() > 1 && !usesRowActions()) list.setHeaderActions(new CarActionStrip()
                    .addAction("Shuffle queue", c -> player.shuffle()));
            int count = Math.max(1, getContext().getListRowLimit() - (usesRowActions() && player.queue.size() > 1 ? 4 : 3));
            offset = Math.min(offset, Math.max(0, player.queue.size() - 1));
            List<Track> visible = new ArrayList<>();
            for (int i = offset; i < Math.min(player.queue.size(), offset + count); i++) visible.add(player.queue.get(i));
            showCovers(visible);
            for (int i = offset; i < Math.min(player.queue.size(), offset + count); i++) {
                Track track = player.queue.get(i);
                int index = i;
                String state = i != player.queue.index() ? "" : player.hasFailed() ? "Playback failed · "
                        : player.isLoading() ? "Loading · " : player.isPlaying() ? "Playing · " : "Paused · ";
                list.addRow(new CarRow(track.title()).setText(state + track.description()).setImage(cover(track))
                        .setOnAction(c -> player.select(index)));
            }
            if (offset > 0) list.addRow(new CarRow("Earlier").setOnAction(c -> { offset = Math.max(0, offset - count); invalidate(); }));
            if (offset + count < player.queue.size()) list.addRow(new CarRow("More").setOnAction(c -> { offset += count; invalidate(); }));
            if (usesRowActions() && player.queue.size() > 1) list.addRow(new CarRow("Shuffle queue").setOnAction(c -> player.shuffle()));
            return list.addRow(new CarRow("Clear queue").setOnAction(c -> player.clear()));
        }
    }

    private final class Controls extends IllustratedScreen {
        private boolean more;
        @Override protected void onResume() { invalidate(); }
        @Override protected CarTemplate onCreateTemplate() {
            Track track = player.queue.current();
            if (usesRowActions()) return rowActions(track);
            CarListTemplate list = new CarListTemplate().setTitle("Now Playing")
                    .setHeaderActions(new CarActionStrip().addAction("Queue", c -> c.pushScreen(new QueueScreen()))
                            .addAction("System player", c -> c.pushScreen(new NowPlaying())));
            List<Track> visible = new ArrayList<>();
            if (track != null) visible.add(track);
            showCovers(visible);
            if (track == null) return list.addRow(new CarRow("Choose a track from Library"));
            list.addRow(new CarRow(track.title()).setText(track.description()).setImage(cover(track)));
            if (!player.isLoading()) list.addRow(new CarRow(player.hasFailed() ? "Retry track" : player.isPlaying() ? "Pause" : "Play")
                    .setText(player.status()).setOnAction(c -> { if (player.hasFailed()) player.retry(); else player.toggle(); }));
            else list.addRow(new CarRow("Loading track…").setText(player.status()));
            if (player.queue.index() > 0) list.addRow(new CarRow("Previous track").setOnAction(c -> player.previous()));
            if (player.queue.index() + 1 < player.queue.size()) list.addRow(new CarRow("Next track").setOnAction(c -> player.next()));
            if (!player.isLoading() && !player.hasFailed()) list.addRow(new CarRow("Restart track").setOnAction(c -> player.restart()));
            return list;
        }
        private CarTemplate rowActions(Track track) {
            CarListTemplate list = new CarListTemplate().setTitle("Now Playing");
            List<Track> visible = new ArrayList<>();
            if (track != null) visible.add(track);
            showCovers(visible);
            if (track == null) {
                more = false;
                return list.addRow(new CarRow("Choose a track from Library"))
                        .addRow(new CarRow("Queue").setBrowsable(true).setOnAction(c -> c.pushScreen(new QueueScreen())))
                        .addRow(new CarRow("System player").setBrowsable(true).setOnAction(c -> c.pushScreen(new NowPlaying())));
            }
            if (more) {
                list.addRow(new CarRow("Back to Now Playing").setOnAction(c -> { more = false; invalidate(); }));
                if (player.queue.index() > 0) list.addRow(new CarRow("Previous track").setOnAction(c -> player.previous()));
                if (player.queue.index() + 1 < player.queue.size()) list.addRow(new CarRow("Next track").setOnAction(c -> player.next()));
                if (!player.isLoading() && !player.hasFailed()) list.addRow(new CarRow("Restart track").setOnAction(c -> player.restart()));
                return list.addRow(new CarRow("System player").setBrowsable(true).setOnAction(c -> c.pushScreen(new NowPlaying())));
            }
            list.addRow(new CarRow(track.title()).setText(track.description()).setImage(cover(track)));
            if (player.isLoading()) list.addRow(new CarRow("Loading track…").setText(player.status()));
            else list.addRow(new CarRow(player.hasFailed() ? "Retry track" : player.isPlaying() ? "Pause" : "Play")
                    .setText(player.status()).setOnAction(c -> { if (player.hasFailed()) player.retry(); else player.toggle(); }));
            if (player.queue.index() + 1 < player.queue.size()) list.addRow(new CarRow("Next track").setOnAction(c -> player.next()));
            return list.addRow(new CarRow("Queue").setBrowsable(true).setOnAction(c -> c.pushScreen(new QueueScreen())))
                    .addRow(new CarRow("More controls").setOnAction(c -> { more = true; invalidate(); }));
        }
    }

    private static final class NowPlaying extends CarScreen {
        @Override protected CarTemplate onCreateTemplate() {
            // iOS reuses SystemAudio metadata/commands/artwork. Desktop is a placeholder.
            return new CarNowPlayingTemplate();
        }
    }
}
