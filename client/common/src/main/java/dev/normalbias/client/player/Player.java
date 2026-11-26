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
    private int openVersion;
    private boolean grantPending, wantPlay, recoveryAttempted;
    private long openedAt;
    private java.util.function.LongSupplier clock = System::currentTimeMillis;
    void clock(java.util.function.LongSupplier value) { clock = value; }
    private boolean failed;
    private boolean loading;
    private boolean playing;
    private boolean active;
    private int position;
    private int duration;
    private String status = "Choose a track to start listening";

    public Player(LibraryApi api, Runnable changed) {
        this.api = api; this.changed = changed;
        boolean nativeApple = "ios".equals(CN.getPlatformName()) || dev.normalbias.client.net.TrialOrigin.bundled() != null;
        audio = CN.isSimulator() || nativeApple
                ? new FrameworkAudio(this::poll) : NativeLookup.create(BrowserAudio.class);
        system = nativeApple
                ? NativeLookup.create(SystemAudio.class) : null;
        if (system != null && system.isSupported()) SystemAudioEvents.bind(this);
        api.onArtworkLoaded(id -> {
            if (active && queue.current() != null && id.equals(queue.current().id())) refreshArtwork();
        });
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
    public boolean hasFailed() { return failed; }
    public boolean isLoading() { return loading; }
    public boolean isPlaying() { return playing; }
    public int time() { return position; }
    public int duration() { return duration; }

    public void playTrack(Track track) {
        int index = queue.insertAfterCurrent(track);
        if (index < 0) { status = "Queue full (200 tracks). Clear it to start a new queue."; changed.run(); return; }
        select(index);
    }
    public void enqueue(Track track) {
        boolean wasEmpty = queue.size() == 0;
        int added = queue.add(track);
        if (wasEmpty && added >= 0) { queue.select(0); load(false); return; }
        if (!failed) status = added < 0 ? "Queue full (200 tracks)" : "Added to queue: " + track.title();
        syncSystem();
        changed.run();
    }
    /** Prepare the first queued track paused when empty; otherwise preserve current media. */
    public int enqueueAlbum(java.util.List<Track> tracks) {
        boolean wasEmpty = queue.size() == 0;
        int added = queue.addAll(tracks);
        if (wasEmpty && added > 0) { queue.select(0); load(false); return added; }
        if (!failed) status = added < 0 ? "Album does not fit in the 200-track queue. Clear space and try again."
                : "Album queued: " + added + (added == 1 ? " track added" : " tracks added");
        syncSystem(); changed.run();
        return added;
    }
    public void select(int index) { if (queue.select(index)) load(); }
    public void shuffle() {
        if (queue.size() < 2) return;
        queue.shuffle();
        syncSystem();
        changed.run();
    }
    public void next() { if (queue.next()) load(); }
    public void previous() { if (queue.previous()) load(); }

    private void load() { load(true); }
    private void load(boolean startPlaying) {
        release();
        if (audio == null || !audio.isSupported()) {
            failed = true;
            status = "Audio is unavailable on this platform.";
            changed.run();
            return;
        }
        active = true;
        loading = true;
        duration = Math.max(0, queue.current().durationMs());
        status = "Loading " + queue.current().title() + "…";
        wantPlay = startPlaying; recoveryAttempted = false;
        openGranted();
        if (system != null && system.isSupported()) system.artwork(new byte[0]);
        loadSystemArtwork(queue.current());
        syncSystem();
        changed.run();
    }
    private boolean needsGrant() { return api.scopedPlayback() && clock.getAsLong() - openedAt >= 90000; }
    private void openGranted() {
        final int version = ++openVersion;
        grantPending = true; loading = true; failed = false;
        api.playback(queue.current(), url -> {
            if (!active || version != openVersion) return;
            grantPending = false;
            audio.open(url);
            openedAt = clock.getAsLong();
            audio.seek(position);
            if (wantPlay) audio.play(); // A newly opened stream is already paused.
            syncSystem(true); changed.run();
        }, error -> {
            if (!active || version != openVersion) return;
            grantPending = false; loading = false; playing = false; failed = true; audio.pause();
            status = error; syncSystem(); changed.run();
        });
    }
    public void toggle() {
        if (playing) pause(); else resume();
    }
    public void resume() {
        if (failed) { wantPlay = true; retry(); return; }
        if (!active) { if (queue.current() != null) load(); return; }
        wantPlay = true;
        if (!grantPending) {
            if (needsGrant()) openGranted(); else audio.play();
        }
        playing = true;
        status = "Starting playback…";
        syncSystem();
        changed.run();
    }
    public void pause() {
        if (!active) return;
        wantPlay = false;
        audio.pause();
        playing = false;
        loading = false;
        if (!failed) status = "Paused";
        syncSystem();
        changed.run();
    }
    public void seekTime(int milliseconds) {
        if (active && duration > 0) {
            position = Math.max(0, Math.min(duration, milliseconds));
            if (!grantPending) { if (needsGrant()) openGranted(); else audio.seek(position); }
            syncSystem(true);
            changed.run();
        }
    }
    /** Restart the selected track without changing play/pause intent. */
    public void restart() {
        if (queue.current() == null || failed) return;
        if (!active) { load(false); return; } // Completion has consumed the native ended event.
        position = 0;
        if (!grantPending) {
            if (needsGrant()) openGranted(); else audio.seek(0);
        }
        syncSystem(true);
        changed.run();
    }
    public void retry() {
        if (queue.current() == null) return;
        if (!active) { load(); return; }
        recoveryAttempted = false; status = "Reconnecting playback…"; openGranted(); changed.run();
    }
    public void seek(int percent) {
        if (active && duration > 0) {
            position = (int) ((long) duration * Math.max(0, Math.min(100, percent)) / 100);
            if (!grantPending) { if (needsGrant()) openGranted(); else audio.seek(position); }
            syncSystem(true);
            changed.run();
        }
    }
    /** Called by the form timer and native completion callback on the EDT. */
    public void poll() {
        if (!active || grantPending || failed) return;
        try {
            JSONParser parser = new JSONParser();
            parser.setUseBooleanInstance(true);
            Map<String, Object> snapshot = parser.parseJSON(new StringReader(audio.snapshot()));
            boolean beforePlaying = playing;
            boolean beforeLoading = loading;
            String beforeStatus = status;
            String error = (String) snapshot.get("error");
            if (error.isEmpty()) position = ((Number) snapshot.get("time")).intValue();
            int reportedDuration = ((Number) snapshot.get("duration")).intValue();
            // iOS can report zero while opening; keep the known catalogue duration in the background.
            if (reportedDuration > 0) duration = reportedDuration;
            playing = Boolean.TRUE.equals(snapshot.get("playing"));
            loading = Boolean.TRUE.equals(snapshot.get("loading"));
            if (!error.isEmpty()) {
                if (api.scopedPlayback() && !recoveryAttempted) {
                    recoveryAttempted = true;
                    status = "Reconnecting playback…"; openGranted(); return;
                }
                loading = false; playing = false; failed = true;
                status = error;
            } else if (Boolean.TRUE.equals(snapshot.get("ended"))) {
                active = false; // consume completion once, including at the queue boundary
                if (queue.next()) { load(); return; }
                playing = false;
                loading = false;
                status = "Queue finished";
            } else if (Boolean.TRUE.equals(snapshot.get("waiting"))) {
                status = "Buffering… Check your connection.";
            } else if (playing) status = "Playing";
            else if (!loading) status = "Paused";
            syncSystem();
            if (beforePlaying != playing || beforeLoading != loading || !beforeStatus.equals(status)) changed.run();
        } catch (Exception ex) {
            release();
            failed = true;
            status = "Cannot read playback state. Press Retry track.";
            changed.run();
        }
    }
    public void dispose() { clear(); SystemAudioEvents.unbind(this); }
    public void clear() { release(); queue.clear(); status = "Queue cleared"; changed.run(); }
    public void release() {
        openVersion++; grantPending = false; wantPlay = false;
        if (audio != null && audio.isSupported()) audio.close();
        active = false;
        failed = false;
        playing = false;
        loading = false;
        position = 0;
        duration = 0;
        syncSystem();
    }
}
