package dev.normalbias.client.player;

import dev.normalbias.client.Track;
import java.util.ArrayList;
import java.util.List;

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
    public void clear() { tracks.clear(); index = -1; }
}
