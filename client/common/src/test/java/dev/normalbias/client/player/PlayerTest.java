package dev.normalbias.client.player;

import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {
    private static final class Audio implements BrowserAudio {
        int reportedDuration;
        int opened, closed, played;
        int sought = -1;
        public boolean isSupported() { return true; }
        public void open(String url) { opened++; }
        public void play() { played++; }
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

    @Test void albumQueuePreservesActiveMediaAndDeduplicatesInCatalogueOrder() {
        Audio audio = new Audio(); Player player = player(audio);
        Track first = track("a", 42000), second = track("b", 42000), third = track("c", 42000);
        player.playTrack(first); player.seekTime(17000);
        int opened = audio.opened, closed = audio.closed;
        assertEquals(2, player.enqueueAlbum(java.util.List.of(first, second, third)));
        assertEquals(opened, audio.opened); assertEquals(closed, audio.closed);
        assertEquals(17000, player.time()); assertEquals(first, player.queue.current());
        assertEquals(second, player.queue.get(1)); assertEquals(third, player.queue.get(2));
        assertEquals(0, player.enqueueAlbum(java.util.List.of(first, second, third)));
        assertEquals(3, player.queue.size());
    }
    @Test void albumQueueDoesNotStartPlaybackOrPartiallyAppendBeyondCapacity() {
        Audio audio = new Audio(); Player player = player(audio);
        assertEquals(2, player.enqueueAlbum(java.util.List.of(track("a", 42000), track("b", 42000))));
        assertEquals("a".repeat(64), player.queue.current().id()); assertEquals(1, audio.opened); assertEquals(0, audio.played); assertFalse(player.isPlaying());
        java.util.List<Track> album = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) album.add(new Track("id-" + i, "Track " + i, "Fixture", "Album", 42000));
        assertEquals(-1, player.enqueueAlbum(album));
        assertEquals(2, player.queue.size()); assertEquals("a".repeat(64), player.queue.current().id()); assertEquals(1, audio.opened); assertEquals(0, audio.played);
        assertTrue(player.status().contains("200-track"));
    }

    @Test void firstQueuedTrackIsPreparedPausedAndPlayCanStartItImmediately() {
        Audio audio = new Audio(); Player player = player(audio); Track first = track("a", 42000);
        player.enqueue(first);
        assertSame(first, player.queue.current()); assertEquals(0, player.queue.index());
        assertEquals(1, audio.opened); assertEquals(0, audio.played); assertEquals(0, audio.sought);
        assertFalse(player.isPlaying());
        player.resume(); assertEquals(1, audio.played); assertEquals(1, audio.opened);
    }
    @Test void clearUnloadsPreparedOrPlayingMediaAndRemovesSelection() {
        for (boolean playing : new boolean[] {false, true}) {
            Audio audio = new Audio(); Player player = player(audio);
            player.enqueue(track("a", 42000)); if (playing) player.resume();
            int closed = audio.closed; player.clear();
            assertTrue(audio.closed > closed); assertNull(player.queue.current()); assertEquals(-1, player.queue.index());
            assertEquals(0, player.queue.size()); assertEquals(0, player.time()); assertEquals(0, player.duration());
            assertFalse(player.isPlaying()); assertFalse(player.isLoading());
            int opened = audio.opened, played = audio.played; player.resume(); player.poll();
            assertEquals(opened, audio.opened); assertEquals(played, audio.played);
        }
    }
    @Test void clearWhilePreparingIgnoresALatePlaybackGrant() {
        java.util.List<java.util.function.Consumer<String>> grants = new java.util.ArrayList<>();
        LibraryApi api = new LibraryApi("http://127.0.0.1:8080") {
            @Override public void playback(Track track, java.util.function.Consumer<String> ready, java.util.function.Consumer<String> failure) {
                grants.add(ready);
            }
        };
        Audio audio = new Audio(); Player player = new Player(api, () -> {}, audio);
        player.enqueue(track("a", 42000)); assertTrue(player.isLoading());
        player.clear(); grants.get(0).accept("http://127.0.0.1:8080/late-audio");
        assertEquals(0, audio.opened); assertEquals(0, audio.played); assertNull(player.queue.current());
        assertFalse(player.isPlaying()); assertFalse(player.isLoading());
    }
}
