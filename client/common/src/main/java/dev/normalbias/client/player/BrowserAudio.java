package dev.normalbias.client.player;

import com.codename1.system.NativeInterface;

/** Narrow browser-only escape hatch for CN1 7.0.282's broken HTML5Media play bridge. */
public interface BrowserAudio extends NativeInterface {
    void open(String url);
    void play();
    void pause();
    void seek(int milliseconds);
    String snapshot();
    void close();
}
