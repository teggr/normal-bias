package dev.normalbias.client.ui;

import com.codename1.ui.Button;
import com.codename1.ui.Graphics;

/** Text-sized version of the player's moulded graphite hardware button. */
final class LibraryViewButton extends Button {
    LibraryViewButton(String title) {
        super(title);
        setActive(false);
    }
    void setActive(boolean active) {
        setUIID(active ? "LibraryViewActive" : "LibraryView");
    }
    @Override public void paintBackground(Graphics g) {
        HardwareButton.paintSurface(this, g, true);
    }
}
