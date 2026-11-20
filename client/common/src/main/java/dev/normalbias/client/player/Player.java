package dev.normalbias.client.player;

import com.codename1.io.JSONParser;
import com.codename1.system.NativeLookup;
import com.codename1.ui.CN;
import com.codename1.ui.EncodedImage;
import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import java.io.StringReader;
import java.util.Map;

/** Owns platform audio and a finite session queue. Polling reads one atomic native snapshot. */
public final class Player {
    private final LibraryApi api;
    private final Runnable changed;
    private final BrowserAudio audio;
    private final SystemAudio system;
    private String publishedTrack;
    private boolean publishedPlaying, publishedPrevious, publishedNext;
    private int publishedDuration;
    public final PlaybackQueue queue = new PlaybackQueue();
    private boolean loading;
    private boolean playing;
    private boolean active;
    private int position;
    private int duration;
    private String status = "Choose a track to start listening";

    public Player(LibraryApi api, Runnable changed) {
        this.api = api; this.changed = changed;
        audio = CN.isSimulator() || "ios".equals(CN.getPlatformName())
                ? new FrameworkAudio(this::poll) : NativeLookup.create(BrowserAudio.class);
        system = !CN.isSimulator() && "ios".equals(CN.getPlatformName())
                ? NativeLookup.create(SystemAudio.class) : null;
        if (system != null && system.isSupported()) SystemAudioEvents.bind(this);
    }
    // Platform-independent regression checks use the same player policy with a fake audio adapter.
    Player(LibraryApi api, Runnable changed, BrowserAudio audio) {
        this.api = api; this.changed = changed; this.audio = audio; this.system = null;
    }
    private void syncSystem() { syncSystem(false); }
    private void syncSystem(boolean seeked) {
        if (system == null || !system.isSupported()) return;
        Track track = queue.current();
        if (!active || track == null) {
            if (publishedTrack != null) system.clear();
            publishedTrack = null;
            return;
        }
        int length = duration > 0 ? duration : track.durationMs();
        boolean previous = queue.index() > 0;
        boolean next = queue.index() + 1 < queue.size();
        // iOS advances elapsed time from the published rate. Replacing its timeline on every
        // form poll resets the system scrubber during a user's drag, before the seek event.
        if (!seeked && track.id().equals(publishedTrack) && playing == publishedPlaying
                && length == publishedDuration && previous == publishedPrevious && next == publishedNext) return;
        system.update(track.title(), track.artist() == null ? "" : track.artist(),
                track.album() == null ? "" : track.album(), position, length, playing, previous, next);
        publishedTrack = track.id();
        publishedPlaying = playing;
        publishedDuration = length;
        publishedPrevious = previous;
        publishedNext = next;
    }
    public void refreshArtwork() {
        if (queue.current() != null) loadSystemArtwork(queue.current());
        changed.run();
    }
    private void loadSystemArtwork(Track track) {
        if (system == null || !system.isSupported()) return;
        String artworkKey = api.artworkKey(track);
        api.artwork(track, image -> {
            if (active && queue.current() != null && track.id().equals(queue.current().id()) && artworkKey.equals(api.artworkKey(track))) {
                system.artwork(image instanceof EncodedImage ? ((EncodedImage) image).getImageData() : new byte[0]);
                syncSystem();
            }
        });
    }
    public String status() { return status; }
    public boolean isLoading() { return loading; }
    public boolean isPlaying() { return playing; }
    public int time() { return position; }
    public int duration() { return duration; }

    public void playTrack(Track track) {
        int index = queue.add(track);
        if (index < 0) { status = "Queue full (200 tracks). Clear it to start a new queue."; changed.run(); return; }
        select(index);
    }
    public void enqueue(Track track) {
        status = queue.add(track) < 0 ? "Queue full (200 tracks)" : "Added to queue: " + track.title();
        syncSystem();
        changed.run();
    }
    public void select(int index) { if (queue.select(index)) load(); }
    public void next() { if (queue.next()) load(); }
    public void previous() { if (queue.previous()) load(); }

    private void load() {
        release();
        if (audio == null || !audio.isSupported()) {
            status = "Audio is unavailable on this platform.";
            changed.run();
            return;
        }
        active = true;
        loading = true;
        duration = Math.max(0, queue.current().durationMs());
        status = "Loading " + queue.current().title() + "…";
        audio.open(api.audioUrl(queue.current()));
        if (system != null && system.isSupported()) system.artwork(new byte[0]);
        loadSystemArtwork(queue.current());
        audio.play();
        syncSystem();
        changed.run();
    }
    public void toggle() {
        if (playing) pause(); else resume();
    }
    public void resume() {
        if (!active) { if (queue.current() != null) load(); return; }
        audio.play();
        playing = true;
        status = "Starting playback…";
        syncSystem();
        changed.run();
    }
    public void pause() {
        if (!active) return;
        audio.pause();
        playing = false;
        loading = false;
        status = "Paused";
        syncSystem();
        changed.run();
    }
    public void seekTime(int milliseconds) {
        if (active && duration > 0) {
            position = Math.max(0, Math.min(duration, milliseconds));
            audio.seek(position);
            syncSystem(true);
            changed.run();
        }
    }
    public void retry() { if (queue.current() != null) load(); }
    public void seek(int percent) {
        if (active && duration > 0) {
            position = (int) ((long) duration * Math.max(0, Math.min(100, percent)) / 100);
            audio.seek(position);
            syncSystem(true);
            changed.run();
        }
    }
    /** Called by the form timer and native completion callback on the EDT. */
    public void poll() {
        if (!active) return;
        try {
            JSONParser parser = new JSONParser();
            parser.setUseBooleanInstance(true);
            Map<String, Object> snapshot = parser.parseJSON(new StringReader(audio.snapshot()));
            boolean beforePlaying = playing;
            boolean beforeLoading = loading;
            String beforeStatus = status;
            position = ((Number) snapshot.get("time")).intValue();
            int reportedDuration = ((Number) snapshot.get("duration")).intValue();
            // iOS can report zero while opening; keep the known catalogue duration in the background.
            if (reportedDuration > 0) duration = reportedDuration;
            playing = Boolean.TRUE.equals(snapshot.get("playing"));
            loading = Boolean.TRUE.equals(snapshot.get("loading"));
            String error = (String) snapshot.get("error");
            if (!error.isEmpty()) {
                loading = false;
                status = error;
            } else if (Boolean.TRUE.equals(snapshot.get("ended"))) {
                active = false; // consume completion once, including at the queue boundary
                if (queue.next()) { load(); return; }
                playing = false;
                loading = false;
                status = "Queue finished";
            } else if (Boolean.TRUE.equals(snapshot.get("waiting"))) {
                status = "Buffering… Check your connection, or Retry track.";
            } else if (playing) status = "Playing";
            else if (!loading) status = "Paused";
            syncSystem();
            if (beforePlaying != playing || beforeLoading != loading || !beforeStatus.equals(status)) changed.run();
        } catch (Exception ex) {
            release();
            status = "Cannot read playback state. Press Retry track.";
            changed.run();
        }
    }
    public void clear() { release(); queue.clear(); status = "Queue cleared"; changed.run(); }
    public void release() {
        if (audio != null && audio.isSupported()) audio.close();
        active = false;
        playing = false;
        loading = false;
        position = 0;
        duration = 0;
        syncSystem();
    }
}
