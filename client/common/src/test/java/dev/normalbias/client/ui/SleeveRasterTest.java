package dev.normalbias.client.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SleeveRasterTest {
    @Test void rectangularCoverIsCentredWithoutStretchingAndGetsTransparentCircularCorners() {
        int[] source = {0xffff0000,0xffff0000,0xff00ff00,0xff00ff00,0xff0000ff,0xff0000ff,
                        0xffff0000,0xffff0000,0xff00ff00,0xff00ff00,0xff0000ff,0xff0000ff};
        int[] pixels = SleeveRaster.create(40, true, 1, source, 6, 2);
        assertEquals(0, pixels[0] >>> 24);
        assertEquals(0, pixels[39] >>> 24);
        assertEquals(0xff00ff00, pixels[20 * 40 + 20]);
        assertEquals(0xff00ff00, pixels[10 * 40 + 20]);
    }
    @Test void fallbackKeepsTheEstablishedAmberAndBlueSleevesAndRoundedPlayerCorners() {
        int[] amber = SleeveRaster.create(80, true, 1, null, 0, 0);
        int[] blue = SleeveRaster.create(80, true, 0, null, 0, 0);
        assertEquals(0xffeebd40, amber[20 * 80 + 40]);
        assertEquals(0xff79919c, blue[20 * 80 + 40]);
        int[] player = SleeveRaster.create(80, false, 1, null, 0, 0);
        assertTrue((player[40] >>> 24) > 0);
        assertTrue((player[0] >>> 24) < 128);
    }
}
