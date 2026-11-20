package dev.normalbias.client.player;

import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {
    private static final class Audio implements BrowserAudio {
        int reportedDuration;
        int opened, closed;
        int sought = -1;
        public boolean isSupported() { return true; }
        public void open(String url) { opened++; }
        public void play() { }
        public void pause() { }
        public void close() { closed++; }
        public void seek(int milliseconds) { sought = milliseconds; }
        public String snapshot() {
            return "{\"time\":0,\"duration\":" + reportedDuration
                    + ",\"playing\":true,\"loading\":false,\"waiting\":false,\"ended\":false,\"error\":\"\"}";
        }
    }
    private Track track(String id, int duration) { return new Track(id.repeat(64), "Synthetic", "Fixture", "Test", duration); }
    private Player player(Audio audio) { return new Player(new LibraryApi("http://127.0.0.1:8080"), () -> {}, audio); }

    @Test void artworkRefreshLeavesMediaPositionAndQueueUntouched() {
        Audio audio = new Audio(); Player player = player(audio);
        player.playTrack(track("a", 42000)); player.enqueue(track("b", 42000)); player.seekTime(17000);
        int opened = audio.opened, closed = audio.closed;
        player.refreshArtwork();
        assertEquals(opened, audio.opened); assertEquals(closed, audio.closed);
        assertEquals(17000, player.time()); assertEquals(2, player.queue.size());
        assertEquals("a".repeat(64), player.queue.current().id());
    }
    @Test void systemSeekUsesCatalogueDurationWhenNativeDurationIsNotReady() {
        Audio audio = new Audio();
        Player player = player(audio);
        player.playTrack(track("a", 42000));
        player.poll(); // Mirrors the first native callback after opening in the background.
        assertEquals(42000, player.duration());
        player.seekTime(21000);
        assertEquals(21000, audio.sought);
        player.seekTime(90000);
        assertEquals(42000, audio.sought);
    }

    @Test void trackChangesResetFallbackAndLaterNativeDurationRefinesIt() {
        Audio audio = new Audio();
        Player player = player(audio);
        player.playTrack(track("a", 42000));
        player.playTrack(track("b", 30000));
        player.poll();
        assertEquals(30000, player.duration());
        audio.reportedDuration = 29500;
        player.poll();
        assertEquals(29500, player.duration());
        player.seekTime(99999);
        assertEquals(29500, audio.sought);
        player.clear();
        audio.sought = -1;
        player.seekTime(5000);
        assertEquals(-1, audio.sought);
        assertEquals(0, player.duration());
    }
}
