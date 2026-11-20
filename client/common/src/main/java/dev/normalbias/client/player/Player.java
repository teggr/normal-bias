package dev.normalbias.client.player;

import com.codename1.io.JSONParser;
import com.codename1.system.NativeLookup;
import com.codename1.ui.CN;
import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import java.io.StringReader;
import java.util.Map;

/** Owns platform audio and a finite session queue. Polling reads one atomic native snapshot. */
public final class Player {
    private final LibraryApi api;
    private final Runnable changed;
    private final BrowserAudio audio = CN.isSimulator() || "ios".equals(CN.getPlatformName())
            ? new FrameworkAudio() : NativeLookup.create(BrowserAudio.class);
    public final PlaybackQueue queue = new PlaybackQueue();
    private boolean loading;
    private boolean playing;
    private boolean active;
    private int position;
    private int duration;
    private String status = "Choose a track to start listening";

    public Player(LibraryApi api, Runnable changed) { this.api = api; this.changed = changed; }
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
        status = "Loading " + queue.current().title() + "…";
        audio.open(api.audioUrl(queue.current()));
        audio.play();
        changed.run();
    }
    public void toggle() {
        if (!active) { if (queue.current() != null) load(); return; }
        if (playing) { audio.pause(); playing = false; status = "Paused"; }
        else { audio.play(); status = "Starting playback…"; }
        changed.run();
    }
    public void retry() { if (queue.current() != null) load(); }
    public void seek(int percent) {
        if (active && duration > 0) {
            position = (int) ((long) duration * Math.max(0, Math.min(100, percent)) / 100);
            audio.seek(position);
            changed.run();
        }
    }
    /** Called by the form timer, on the EDT. Network stalls never advance the queue. */
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
            duration = ((Number) snapshot.get("duration")).intValue();
            playing = Boolean.TRUE.equals(snapshot.get("playing"));
            loading = Boolean.TRUE.equals(snapshot.get("loading"));
            String error = (String) snapshot.get("error");
            if (!error.isEmpty()) {
                loading = false;
                status = error;
            } else if (Boolean.TRUE.equals(snapshot.get("ended"))) {
                active = false; // consume completion once, including at the queue boundary
                if (queue.next()) { load(); return; }
                status = "Queue finished";
            } else if (Boolean.TRUE.equals(snapshot.get("waiting"))) {
                status = "Buffering… Check your connection, or Retry track.";
            } else if (playing) status = "Playing";
            else if (!loading) status = "Paused";
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
    }
}
