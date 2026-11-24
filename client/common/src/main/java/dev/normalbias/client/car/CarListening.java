package dev.normalbias.client.car;

import com.codename1.car.*;
import com.codename1.ui.CN;
import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import dev.normalbias.client.player.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

/** A projection of the phone's existing player, not a second playback session or theme. */
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
                    .addRow(new CarRow("Library").setBrowsable(true)
                            .setOnAction(c -> c.pushScreen(new Library())))
                    .addRow(new CarRow("Queue (" + player.queue.size() + ")").setBrowsable(true)
                            .setOnAction(c -> c.pushScreen(new QueueScreen(0))))
                    .addRow(new CarRow("Playback controls").setText(track == null ? "Nothing playing" : track.title())
                            .setBrowsable(true).setOnAction(c -> c.pushScreen(new Controls())))
                    .addRow(new CarRow("Now Playing").setBrowsable(true)
                            .setOnAction(c -> c.pushScreen(new NowPlaying())));
        }
    }

    private final class Library extends CarScreen {
        private List<Track> tracks = new ArrayList<>();
        private int offset, total, version;
        private boolean loading;
        private String error;
        private Timer deadline;

        @Override protected void onCreate() { load(0); }
        @Override protected void onDestroy() { version++; cancelDeadline(); }

        private void cancelDeadline() {
            if (deadline != null) { deadline.cancel(); deadline = null; }
        }

        private void load(int start) {
            int request = ++version;
            offset = start;
            loading = true;
            error = null;
            cancelDeadline();
            // The desktop port did not honour the request read timeout when its server
            // stopped responding. Keep the car surface retryable and ignore late replies.
            deadline = CN.setTimeout(16000, () -> {
                if (request != version || !loading) return;
                version++;
                cancelDeadline();
                loading = false;
                error = "Library request timed out. Check the API connection and retry.";
                invalidate();
            });
            invalidate();
            api.list("", start, page -> {
                if (request != version) return;
                cancelDeadline();
                tracks = page.items();
                offset = page.offset();
                total = page.total();
                loading = false;
                invalidate();
            }, message -> {
                if (request != version) return;
                cancelDeadline();
                loading = false;
                error = message;
                invalidate();
            });
        }

        @Override protected CarTemplate onCreateTemplate() {
            CarListTemplate list = new CarListTemplate().setTitle("Library").setLoading(loading);
            if (loading) return list;
            if (error != null) return list.addRow(new CarRow("Retry library").setText(error)
                    .setOnAction(c -> load(offset)));
            if (tracks.isEmpty()) return list.addRow(new CarRow("Library is empty"));
            // Reserve rows for paging even on head units with a small template limit.
            int pageSize = Math.min(LibraryApi.PAGE_SIZE, Math.max(1, getContext().getListRowLimit() - 2));
            int count = Math.min(tracks.size(), pageSize);
            for (int i = 0; i < count; i++) {
                Track track = tracks.get(i);
                list.addRow(new CarRow(track.title()).setText(track.description()).setOnAction(c -> {
                    player.playTrack(track);
                    c.pushScreen(new Controls());
                }));
            }
            if (offset > 0) list.addRow(new CarRow("Previous page")
                    .setOnAction(c -> load(Math.max(0, offset - pageSize))));
            if (offset + count < total) list.addRow(new CarRow("More tracks")
                    .setOnAction(c -> load(offset + pageSize)));
            return list;
        }
    }

    private final class QueueScreen extends CarScreen {
        private int offset;
        QueueScreen(int offset) { this.offset = offset; }
        @Override protected void onResume() { invalidate(); }
        @Override protected CarTemplate onCreateTemplate() {
            CarListTemplate list = new CarListTemplate().setTitle("Queue (" + player.queue.size() + ")");
            if (player.queue.size() == 0) return list.addRow(new CarRow("Queue is empty")
                    .setText("Choose a track from Library or add tracks on your phone."));
            int count = Math.max(1, getContext().getListRowLimit() - 3);
            offset = Math.min(offset, Math.max(0, player.queue.size() - 1));
            for (int i = offset; i < Math.min(player.queue.size(), offset + count); i++) {
                Track track = player.queue.get(i);
                int index = i;
                list.addRow(new CarRow(track.title())
                        .setText((i == player.queue.index() ? "Selected · " : "") + track.description())
                        .setOnAction(c -> { player.select(index); c.pushScreen(new Controls()); }));
            }
            if (offset > 0) list.addRow(new CarRow("Previous page").setOnAction(c -> {
                offset = Math.max(0, offset - count); invalidate();
            }));
            if (offset + count < player.queue.size()) list.addRow(new CarRow("More queued tracks").setOnAction(c -> {
                offset += count; invalidate();
            }));
            return list.addRow(new CarRow("Clear queue").setOnAction(c -> player.clear()));
        }
    }

    private final class Controls extends CarScreen {
        @Override protected void onResume() { invalidate(); }
        @Override protected CarTemplate onCreateTemplate() {
            Track track = player.queue.current();
            CarListTemplate list = new CarListTemplate().setTitle(track == null ? "Nothing playing" : track.title());
            if (track == null) return list.addRow(new CarRow("Choose a track from Library"));
            list.addRow(new CarRow(player.isPlaying() ? "Pause" : "Play").setText(player.status())
                    .setOnAction(c -> player.toggle()));
            if (player.queue.index() > 0) list.addRow(new CarRow("Previous").setOnAction(c -> player.previous()));
            if (player.queue.index() + 1 < player.queue.size()) {
                list.addRow(new CarRow("Next").setOnAction(c -> player.next()));
            }
            return list.addRow(new CarRow(player.hasFailed() ? "Retry track" : "Restart track")
                    .setOnAction(c -> { if (player.hasFailed()) player.retry(); else player.restart(); }))
                    .addRow(new CarRow("Now Playing").setBrowsable(true)
                            .setOnAction(c -> c.pushScreen(new NowPlaying())));
        }
    }

    private static final class NowPlaying extends CarScreen {
        @Override protected CarTemplate onCreateTemplate() {
            // iOS uses the existing SystemAudio metadata/remote commands. The desktop
            // head unit displays a placeholder; it does not emulate Apple's media UI.
            return new CarNowPlayingTemplate();
        }
    }
}
