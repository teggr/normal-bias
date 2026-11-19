package dev.normalbias.client;

import dev.normalbias.client.player.PlaybackQueue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackQueueTest {
    private Track track(int id) { return new Track(String.valueOf(id), "Track " + id, null, null, 1000); }

    @Test void navigationHasFiniteBoundariesAndAddingDoesNotChangePlayback() {
        PlaybackQueue queue = new PlaybackQueue();
        assertFalse(queue.next());
        assertFalse(queue.previous());
        assertNull(queue.current());
        queue.add(track(1));
        queue.add(track(2));
        assertNull(queue.current());
        assertTrue(queue.select(0));
        queue.add(track(3));
        assertEquals("1", queue.current().id());
        assertFalse(queue.previous());
        assertTrue(queue.next());
        assertTrue(queue.next());
        assertFalse(queue.next());
        assertEquals("3", queue.current().id());
        assertFalse(queue.select(-1));
        assertFalse(queue.select(99));
        assertEquals(2, queue.index());
        queue.clear();
        assertEquals(0, queue.size());
        assertEquals(-1, queue.index());
        assertNull(queue.current());
    }

    @Test void duplicatesReuseTheirPositionAndQueueMemoryIsBounded() {
        PlaybackQueue queue = new PlaybackQueue();
        for (int i = 0; i < PlaybackQueue.MAX_TRACKS; i++) assertEquals(i, queue.add(track(i)));
        assertEquals(PlaybackQueue.MAX_TRACKS, queue.size());
        assertEquals(0, queue.add(track(0)));
        assertEquals(-1, queue.add(track(201)));
        assertEquals(PlaybackQueue.MAX_TRACKS, queue.size());
    }
}
