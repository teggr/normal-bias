package dev.normalbias.client.player;

import com.codename1.io.JSONParser;
import com.codename1.media.Media;
import com.codename1.media.MediaManager;
import com.codename1.ui.CN;
import java.util.HashMap;
import java.util.Map;

/** Uses the framework media implementation in the desktop simulator and the iPhone background playback. */
final class FrameworkAudio implements BrowserAudio {
    private final Runnable stateChanged;
    FrameworkAudio(Runnable stateChanged) { this.stateChanged = stateChanged; }
    private Media media;
    private int generation;
    private boolean loading;
    private boolean ended;
    private boolean wantPlay;
    private long loadingSince;
    private String error = "";

    public boolean isSupported() { return true; }

    public void open(String url) {
        close();
        final int request = generation;
        loading = true;
        loadingSince = System.currentTimeMillis();
        CN.startThread(() -> {
            try {
                MediaManager.createMediaAsync(url, false, () -> CN.callSerially(() -> {
                    if (request == generation) { ended = true; wantPlay = false; stateChanged.run(); }
                })).ready(created -> CN.callSerially(() -> {
                    if (request != generation) { created.cleanup(); return; }
                    media = created;
                    loading = false;
                    MediaManager.getAsyncMedia(media).addMediaErrorListener(event -> CN.callSerially(() -> {
                        if (request == generation) fail();
                    }));
                    if (wantPlay) play();
                })).except(failure -> CN.callSerially(() -> {
                    if (request == generation) fail();
                }));
            } catch (Exception failure) {
                CN.callSerially(() -> { if (request == generation) fail(); });
            }
        }, "framework-media-open").start();
    }

    public void play() {
        wantPlay = true;
        ended = false;
        if (media == null) return;
        final int request = generation;
        loading = true;
        loadingSince = System.currentTimeMillis();
        MediaManager.getAsyncMedia(media).playAsync()
                .ready(value -> CN.callSerially(() -> {
                    if (request == generation) {
                        loading = false;
                        if (!wantPlay) media.pause();
                        stateChanged.run();
                    }
                })).except(failure -> CN.callSerially(() -> {
                    if (request == generation) fail();
                }));
    }

    public void pause() {
        wantPlay = false;
        if (media != null) media.pause();
        loading = false;
    }

    public void seek(int milliseconds) { if (media != null) media.setTime(milliseconds); }

    public String snapshot() {
        if (loading && System.currentTimeMillis() - loadingSince > 15000) fail();
        Map<String, Object> state = new HashMap<>();
        state.put("time", media == null ? 0 : Math.max(0, media.getTime()));
        state.put("duration", media == null ? 0 : Math.max(0, media.getDuration()));
        state.put("playing", media != null && media.isPlaying());
        state.put("loading", loading);
        state.put("waiting", false);
        state.put("ended", ended);
        state.put("error", error);
        return JSONParser.mapToJson(state);
    }

    public void close() {
        generation++;
        Media previous = media;
        media = null;
        loading = false;
        ended = false;
        wantPlay = false;
        error = "";
        if (previous != null) previous.cleanup();
    }

    private void fail() {
        // Invalidate pending callbacks and stop/release a failed or timed-out stream.
        close();
        error = "Cannot play this track. Check the API connection and press Retry track.";
    }
}
