package dev.normalbias.client.player;

import com.codename1.system.NativeInterface;

/** Narrow iOS system-controls bridge; playback and queue policy remain in Java. */
public interface SystemAudio extends NativeInterface {
    void update(String title, String artist, String album, int position, int duration,
                boolean playing, boolean previous, boolean next);
    void artwork(byte[] png);
    void clear();
}
