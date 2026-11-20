package dev.normalbias.client.player;

import com.codename1.ui.CN;

/** Native commands always re-enter the same Java player on the EDT. */
public final class SystemAudioEvents {
    private static Player player;
    static void bind(Player value) { player = value; command(0, 0); }
    public static void command(int command, int value) {
        if (command == 0) return; // Also keeps the native callback entry point reachable.
        CN.callSerially(() -> {
            if (player == null) return;
            switch (command) {
                case 1: player.resume(); break;
                case 2: player.pause(); break;
                case 3: player.toggle(); break;
                case 4: player.next(); break;
                case 5: player.previous(); break;
                case 6: player.seekTime(value); break;
                case 7: player.pause(); break;
                default: break;
            }
        });
    }
}
