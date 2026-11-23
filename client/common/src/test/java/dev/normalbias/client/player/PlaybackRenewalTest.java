package dev.normalbias.client.player;

import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackRenewalTest {
    static class Api extends LibraryApi {
        List<Consumer<String>> grants = new ArrayList<>();
        Api() { super("https://127.0.0.1:18443"); }
        @Override public boolean scopedPlayback() { return true; }
        @Override public void playback(Track track, Consumer<String> ready, Consumer<String> failure) { grants.add(ready); }
        void deliver(int i) { grants.get(i).accept("https://127.0.0.1:18443/api/tracks/a/audio?grant=" + i); }
    }
    static class Audio implements BrowserAudio {
        int opens, seek, plays, pauses, time;
        String error = "";
        public boolean isSupported() { return true; }
        public void open(String url) { opens++; error = ""; }
        public void seek(int value) { seek = value; }
        public void play() { plays++; }
        public void pause() { pauses++; }
        public void close() { }
        public String snapshot() { return "{\"time\":" + time + ",\"duration\":180000,\"playing\":false,\"loading\":false,\"waiting\":false,\"ended\":false,\"error\":\"" + error + "\"}"; }
    }
    Track track(String id) { return new Track(id.repeat(64), "Original synthetic", "Fixture", "Test", 180000); }
    @Test void expiredPausedSeekRestoresLatestPositionWithoutPlaying() {
        Api api = new Api(); Audio audio = new Audio(); long[] now = {1000};
        Player player = new Player(api, () -> {}, audio); player.clock(() -> now[0]);
        player.playTrack(track("a")); api.deliver(0); player.pause();
        now[0] += 121000; player.seekTime(57000);
        assertEquals(2, api.grants.size()); assertEquals(1, audio.opens);
        player.seekTime(64000); // A scrub while renewal is in flight must win.
        int plays = audio.plays; api.deliver(1);
        assertEquals(64000, audio.seek); assertEquals(plays, audio.plays); assertFalse(player.isPlaying());
        assertEquals(1, player.queue.size());
    }
    @Test void logoutAndTrackChangesDiscardLateGrants() {
        Api api = new Api(); Audio audio = new Audio(); Player player = new Player(api, () -> {}, audio);
        player.playTrack(track("a")); player.playTrack(track("b")); api.deliver(0);
        assertEquals(0, audio.opens); api.deliver(1); assertEquals(1, audio.opens);
        player.retry(); player.clear(); api.deliver(2);
        assertEquals(1, audio.opens); assertEquals(0, player.queue.size()); assertEquals(0, player.time());
    }
    @Test void transportErrorRenewsOnceAndKeepsLastGoodPosition() {
        Api api = new Api(); Audio audio = new Audio(); Player player = new Player(api, () -> {}, audio);
        player.playTrack(track("a")); api.deliver(0);
        audio.time = 34000; player.poll(); audio.time = 0; audio.error = "Stream failed"; player.poll();
        assertEquals(2, api.grants.size()); api.deliver(1); assertEquals(34000, audio.seek);
        audio.error = "Stream failed again"; player.poll(); player.poll();
        assertEquals(2, api.grants.size()); assertEquals("Stream failed again", player.status());
    }
}
