package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.ui.geom.Dimension;
import com.codename1.ui.layouts.Layout;
import java.util.function.Consumer;

/** Full alphabet, with a shared wide touch/slide surface and individual keyboard actions. */
final class AlphabetRail extends Container {
    static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
    private final Button[] letters = new Button[27];
    private final Consumer<String> jump;
    private final Consumer<String> preview;
    private String current = "";
    private int touching = -1;
    AlphabetRail(Consumer<String> jump, Consumer<String> preview) {
        super(new RailLayout());
        this.jump = jump; this.preview = preview;
        setUIID("AlphabetRail"); setName("Library alphabet");
        setGrabsPointerEvents(true);
        for (int i = 0; i < letters.length; i++) {
            final String letter = LETTERS.substring(i, i + 1);
            Button button = new Button(letter, "AlphabetLetter") {
                // Keep the whole gesture with the rail even when the toolkit's
                // original pointer target is an individual semantic button.
                @Override public void pointerPressed(int x, int y) {
                    if (isEnabled()) { requestFocus(); AlphabetRail.this.pointerPressed(x, y); }
                }
                @Override public void pointerDragged(int x, int y) { if (isEnabled()) AlphabetRail.this.pointerDragged(x, y); }
                @Override public void pointerReleased(int x, int y) { AlphabetRail.this.pointerReleased(x, y); }
            };
            button.setName("Jump to " + letter); button.setAccessibilityText("Jump to " + letter);
            button.getSemantics().setLabel("Jump to " + letter);
            button.addActionListener(e -> jump.accept(letter));
            letters[i] = button; add(button);
        }
    }
    void active(String initial) {
        String next = initial == null ? "" : initial;
        if (current.equals(next)) return;
        current = next;
        for (int i = 0; i < letters.length; i++) {
            boolean selected = LETTERS.substring(i, i + 1).equals(current);
            letters[i].setUIID(selected ? "AlphabetCurrent" : "AlphabetLetter");
            letters[i].getSemantics().setDescription(selected ? "Current section" : "");
        }
        fitLetterFonts();
        repaint();
    }
    void available(boolean enabled) { for (Button button : letters) button.setEnabled(enabled); }
    private void touch(int y) {
        if (!letters[0].isEnabled()) return;
        int index = Math.min(26, Math.max(0, (y - getAbsoluteY()) * 27 / Math.max(1, getHeight())));
        if (index == touching) return;
        touching = index;
        String initial = LETTERS.substring(index, index + 1);
        preview.accept(initial); jump.accept(initial);
    }
    @Override public void pointerPressed(int x, int y) { touching = -1; touch(y); }
    @Override public void pointerDragged(int x, int y) { touch(y); }
    @Override public void pointerReleased(int x, int y) { touch(y); touching = -1; preview.accept(null); }
    private int fittedHeight = -1;
    private Font fittedRegular, fittedCurrent;
    private void fitLetterFonts() {
        int rowHeight = Math.max(1, getHeight() / letters.length);
        if (rowHeight != fittedHeight) {
            fittedHeight = rowHeight;
            fittedRegular = fittedFont("AlphabetLetter", rowHeight);
            fittedCurrent = fittedFont("AlphabetCurrent", rowHeight);
        }
        for (int i = 0; i < letters.length; i++) {
            letters[i].getAllStyles().setFont(LETTERS.substring(i, i + 1).equals(current) ? fittedCurrent : fittedRegular);
        }
    }
    private static Font fittedFont(String uiid, int height) {
        Font base = com.codename1.ui.plaf.UIManager.getInstance().getComponentStyle(uiid).getFont();
        if (base.getHeight() <= height || !base.isTTFNativeFont()) return base;
        // Native font size and measured line height differ. Fit the line, not only the point size.
        float pixels = base.getPixelSize() * Math.max(1, height - 1) / base.getHeight();
        return base.derive(Math.max(1, pixels), base.getStyle());
    }
    private static final class RailLayout extends Layout {
        @Override public void layoutContainer(Container parent) {
            int height = parent.getHeight(), width = parent.getWidth();
            ((AlphabetRail) parent).fitLetterFonts();
            for (int i = 0; i < parent.getComponentCount(); i++) {
                Component child = parent.getComponentAt(i);
                child.setX(0); child.setY(i * height / 27);
                child.setWidth(width); child.setHeight((i + 1) * height / 27 - child.getY());
            }
        }
        @Override public Dimension getPreferredSize(Container parent) {
            return new Dimension(CN.convertToPixels(7), CN.convertToPixels(100));
        }
    }
}
