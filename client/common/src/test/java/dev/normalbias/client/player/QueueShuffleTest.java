package dev.normalbias.client.player;

import dev.normalbias.client.Track;
import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QueueShuffleTest {
    private Track track(int i) { return new Track("track-" + i, "Track " + i, null, null, 90000); }

    @Test void shuffleIsAPermutationAndKeepsSelectionByIdentity() {
        for (int selected = -1; selected < 8; selected++) {
            PlaybackQueue queue = new PlaybackQueue();
            for (int i = 0; i < 8; i++) queue.add(track(i));
            queue.select(selected);
            Track current = queue.current();
            queue.shuffle(new Random(42));
            assertSame(current, queue.current());
            assertEquals("track-2", queue.get(0).id()); // seeded permutation really changed
            var ids = new HashSet<String>();
            for (int i = 0; i < queue.size(); i++) assertTrue(ids.add(queue.get(i).id()));
            assertEquals(8, ids.size());
            for (int i = 0; i < 8; i++) assertTrue(ids.contains("track-" + i));
            int index = queue.index();
            if (current == null) assertEquals(-1, index);
            else {
                if (index < 7) { Track next = queue.get(index + 1); assertTrue(queue.next()); assertSame(next, queue.current()); }
                queue.select(index);
                if (index > 0) { Track previous = queue.get(index - 1); assertTrue(queue.previous()); assertSame(previous, queue.current()); }
            }
        }
    }

    @Test void emptyAndSingletonQueuesRetainSelectionAndBoundaries() {
        PlaybackQueue queue = new PlaybackQueue();
        queue.shuffle(); assertNull(queue.current()); assertEquals(0, queue.size());
        queue.add(track(0)); queue.shuffle(); assertNull(queue.current());
        queue.select(0); Track current = queue.current(); queue.shuffle();
        assertSame(current, queue.current()); assertFalse(queue.next()); assertFalse(queue.previous());
    }

    @Test void shuffleDuringPendingGrantKeepsCurrentRequestAndPlayIntent() {
        PlaybackRenewalTest.Api api = new PlaybackRenewalTest.Api();
        PlaybackRenewalTest.Audio audio = new PlaybackRenewalTest.Audio();
        Player player = new Player(api, () -> {}, audio);
        player.playTrack(track(0)); player.enqueue(track(1)); player.enqueue(track(2));
        player.shuffle();
        assertEquals("track-0", player.queue.current().id()); assertEquals(1, api.grants.size());
        api.deliver(0);
        assertEquals(1, audio.opens); assertEquals(1, audio.plays);
        player.pause(); player.seekTime(18000);
        int opens = audio.opens, plays = audio.plays, pauses = audio.pauses;
        player.shuffle();
        assertEquals(opens, audio.opens); assertEquals(plays, audio.plays); assertEquals(pauses, audio.pauses);
        assertEquals(18000, player.time()); assertEquals(18000, audio.seek); assertFalse(player.isPlaying());
        assertEquals("track-0", player.queue.current().id()); assertEquals(1, api.grants.size());
    }
}
