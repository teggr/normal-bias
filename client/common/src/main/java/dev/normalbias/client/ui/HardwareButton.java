package dev.normalbias.client.ui;

import com.codename1.ui.*;

/** Icon-only native-focusable control with a restrained moulded graphite edge. */
final class HardwareButton extends Button {
    private final boolean round;
    HardwareButton(String name, char glyph, boolean round) {
        this.round = round;
        setUIID("HardwareButton");
        setGlyph(name, glyph);
    }
    void setGlyph(String name, char glyph) {
        setText("");
        setName(name);
        setAccessibilityText(name);
        getSemantics().setLabel(name);
        FontImage.setMaterialIcon(this, glyph, 3.8f);
    }
    @Override protected com.codename1.ui.geom.Dimension calcPreferredSize() {
        return new com.codename1.ui.geom.Dimension(CN.convertToPixels(9), CN.convertToPixels(8));
    }
    @Override public void paintBackground(Graphics g) {
        boolean aa = g.isAntiAliased();
        g.setAntiAliased(true);
        int x = getX() + 2, y = getY() + 2, w = getWidth() - 4, h = getHeight() - 4;
        int arc = round ? Math.min(w, h) : CN.convertToPixels(3);
        g.setColor(0x111111);
        g.fillRoundRect(x, y, w, h, arc, arc);
        g.setColor((getState() == STATE_PRESSED) ? 0x1d1d1d : 0x383838);
        g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, arc, arc);
        g.setColor((getState() == STATE_PRESSED) ? 0x222222 : 0x272727);
        g.fillRoundRect(x + 3, y + 4, w - 6, h - 7, arc, arc);
        if (hasFocus() && !Display.getInstance().isTouchScreenDevice()) {
            g.setColor(0xeac23f);
            g.drawRoundRect(x, y, w, h, arc, arc);
        }
        g.setAntiAliased(aa);
    }
}
