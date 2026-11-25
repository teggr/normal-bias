package dev.normalbias.client.player;

import dev.normalbias.client.Track;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Session-local queue, independent of the currently displayed catalogue page. */
public final class PlaybackQueue {
    public static final int MAX_TRACKS = 200;
    private final List<Track> tracks = new ArrayList<>();
    private int index = -1;

    public int add(Track track) {
        for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).id().equals(track.id())) return i;
        if (tracks.size() >= MAX_TRACKS) return -1;
        tracks.add(track);
        return tracks.size() - 1;
    }
    /** Append a complete album atomically, retaining existing order and selection. */
    public int addAll(List<Track> album) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Track track : tracks) ids.add(track.id());
        int added = 0;
        for (Track track : album) if (ids.add(track.id())) added++;
        if (tracks.size() + added > MAX_TRACKS) return -1;
        for (Track track : album) add(track);
        return added;
    }
    public boolean select(int value) {
        if (value < 0 || value >= tracks.size()) return false;
        index = value;
        return true;
    }
    public boolean next() { return select(index + 1); }
    public boolean previous() { return select(index - 1); }
    public Track current() { return index < 0 ? null : tracks.get(index); }
    public int index() { return index; }
    public int size() { return tracks.size(); }
    public Track get(int i) { return tracks.get(i); }
    /** Reorder the entire session queue without changing which track is selected. */
    public void shuffle() { shuffle(new Random()); }
    void shuffle(Random random) {
        Track current = current();
        for (int i = tracks.size() - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            Track track = tracks.get(i);
            tracks.set(i, tracks.get(other));
            tracks.set(other, track);
        }
        index = current == null ? -1 : tracks.indexOf(current);
    }
    public void clear() { tracks.clear(); index = -1; }
}
