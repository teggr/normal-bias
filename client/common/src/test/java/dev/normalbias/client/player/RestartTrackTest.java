package dev.normalbias.client.player;

import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RestartTrackTest {
    private static class Audio implements BrowserAudio {
        int opens, closes, plays, pauses, time;
        boolean playing, ended, waiting;
        String error = "";
        public boolean isSupported() { return true; }
        public void open(String url) { opens++; error = ""; ended = false; playing = false; }
        public void close() { closes++; playing = false; }
        public void play() { plays++; playing = true; }
        public void pause() { pauses++; playing = false; }
        public void seek(int value) { time = value; }
        public String snapshot() { return "{\"time\":" + time + ",\"duration\":90000,\"playing\":" + playing
                + ",\"loading\":false,\"waiting\":" + waiting + ",\"ended\":" + ended + ",\"error\":\"" + error + "\"}"; }
    }
    private Track track() { return new Track("a".repeat(64), "Synthetic", "Fixture", "Test", 90000); }
    private LibraryApi api() { return new LibraryApi("http://127.0.0.1:8080"); }

    @Test void restartSeeksSameTrackWithoutReopeningOrChangingPlayingAndPausedIntent() {
        Audio audio = new Audio(); Player player = new Player(api(), () -> {}, audio);
        player.restart(); assertEquals(0, audio.opens);
        player.playTrack(track()); audio.time = 25000; player.poll();
        int opens = audio.opens, closes = audio.closes, plays = audio.plays, pauses = audio.pauses;
        player.restart();
        assertEquals(0, audio.time); assertEquals(0, player.time()); assertTrue(player.isPlaying());
        assertEquals(opens, audio.opens); assertEquals(closes, audio.closes); assertEquals(plays, audio.plays); assertEquals(pauses, audio.pauses);
        player.pause(); audio.time = 45000; player.poll(); pauses = audio.pauses;
        player.restart();
        assertEquals(0, audio.time); assertEquals(0, player.time()); assertFalse(player.isPlaying());
        assertEquals(opens, audio.opens); assertEquals(plays, audio.plays); assertEquals(pauses, audio.pauses);
        assertEquals(track().id(), player.queue.current().id()); assertEquals(1, player.queue.size());
    }

    @Test void restartDuringGrantRenewalKeepsZeroAsLatestPositionAndPreservesPausedIntent() {
        PlaybackRenewalTest.Api api = new PlaybackRenewalTest.Api(); Audio audio = new Audio();
        long[] now = {1000}; Player player = new Player(api, () -> {}, audio); player.clock(() -> now[0]);
        player.playTrack(track()); api.deliver(0); player.pause(); now[0] += 121000;
        player.seekTime(40000); player.restart(); assertEquals(2, api.grants.size());
        int plays = audio.plays; api.deliver(1);
        assertEquals(0, audio.time); assertEquals(0, player.time()); assertEquals(plays, audio.plays); assertFalse(player.isPlaying());
    }

    @Test void completedTrackRestartsPausedAndConsumesStaleCompletion() {
        Audio audio = new Audio(); Player player = new Player(api(), () -> {}, audio);
        player.playTrack(track()); audio.time = 90000; audio.ended = true; audio.playing = false; player.poll();
        int plays = audio.plays; player.restart(); player.poll();
        assertEquals(0, audio.time); assertFalse(player.isPlaying()); assertFalse(player.hasFailed());
        assertEquals(plays, audio.plays); assertEquals("Paused", player.status()); assertEquals(2, audio.opens);
    }

    @Test void terminalMediaFailureShowsRetryAndSuccessfulRecoveryRestoresRestart() {
        Audio audio = new Audio(); Player player = new Player(api(), () -> {}, audio);
        player.playTrack(track()); audio.time = 23000; player.poll();
        audio.waiting = true; player.poll(); assertFalse(player.hasFailed());
        audio.waiting = false; audio.error = "Cannot play"; player.poll(); assertTrue(player.hasFailed());
        int opens = audio.opens; player.restart(); assertEquals(opens, audio.opens);
        player.enqueue(new Track("b".repeat(64), "Other", null, null, 90000)); assertTrue(player.hasFailed());
        player.retry(); player.poll(); assertFalse(player.hasFailed()); assertTrue(player.isPlaying()); assertEquals(23000, player.time());
        player.clear(); assertFalse(player.hasFailed());
    }

    @Test void retryAfterPausedMediaFailureKeepsPausedIntent() {
        Audio audio = new Audio(); Player player = new Player(api(), () -> {}, audio);
        player.playTrack(track()); player.pause(); player.seekTime(21000);
        audio.error = "Failed while paused"; player.poll(); assertTrue(player.hasFailed());
        int plays = audio.plays; player.retry(); player.poll();
        assertFalse(player.hasFailed()); assertFalse(player.isPlaying());
        assertEquals(plays, audio.plays); assertEquals(21000, player.time());
    }

    @Test void grantFailureRemainsVisibleUntilExplicitRetryEvenWithAnOldCleanSnapshot() {
        class Api extends LibraryApi {
            Consumer<String> ready, failure;
            Api() { super("http://127.0.0.1:8080"); }
            @Override public void playback(Track track, Consumer<String> ready, Consumer<String> failure) { this.ready = ready; this.failure = failure; }
        }
        Api api = new Api(); Audio audio = new Audio(); Player player = new Player(api, () -> {}, audio);
        player.playTrack(track()); assertFalse(player.hasFailed()); api.failure.accept("Stream unavailable");
        player.poll(); assertTrue(player.hasFailed()); assertEquals("Stream unavailable", player.status());
        player.retry(); assertFalse(player.hasFailed()); assertTrue(player.isLoading());
        api.ready.accept("http://127.0.0.1/audio"); player.poll(); assertFalse(player.hasFailed()); assertTrue(player.isPlaying());
    }
}
