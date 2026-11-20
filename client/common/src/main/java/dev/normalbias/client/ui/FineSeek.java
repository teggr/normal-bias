package dev.normalbias.client.ui;

import com.codename1.ui.*;

/** A thin visual rail with a full-height, keyboard-accessible slider hit target. */
final class FineSeek extends Slider {
    private boolean interacting;
    boolean isInteracting() { return interacting; }
    @Override public void pointerPressed(int x, int y) { interacting = true; super.pointerPressed(x, y); }
    @Override public void pointerReleased(int x, int y) { super.pointerReleased(x, y); interacting = false; }
    FineSeek() {
        setUIID("FineSeek");
        // Slider inherits Label's text-selection support, which otherwise consumes taps.
        setTextSelectionEnabled(false);
        setAccessibilityText("Seek");
        getSemantics().setLabel("Seek");
    }
    @Override public void paintComponentBackground(Graphics g) { /* Slider defaults paint an oversized fill. */ }
    @Override public void paint(Graphics g) {
        boolean aa = g.isAntiAliased(); g.setAntiAliased(true);
        int h = Math.max(3, CN.convertToPixels(0.7f));
        int x = getX(), y = getY() + (getHeight() - h) / 2;
        g.setColor(0x111111); g.fillRoundRect(x, y, getWidth(), h, h, h);
        int progress = getWidth() * getProgress() / 100;
        if (progress > 0) { g.setColor(0xf3f3f1); g.fillRoundRect(x, y, progress, h, h, h); }
        if (hasFocus() && !Display.getInstance().isTouchScreenDevice()) { g.setColor(0xeac23f); g.drawRoundRect(x, y - 2, getWidth() - 1, h + 4, h, h); }
        g.setAntiAliased(aa);
    }
}
