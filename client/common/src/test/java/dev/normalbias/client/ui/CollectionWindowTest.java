package dev.normalbias.client.ui;

import dev.normalbias.client.net.LibraryApi.*;
import dev.normalbias.client.Track;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionWindowTest {
    BrowsePage page(int offset) {
        List<Item> rows = new ArrayList<>();
        for (int i = offset; i < offset + 25; i++) rows.add(new Item(new Track("" + i, "Song " + i, "Artist", "Album", 1000), null, "S", null));
        return new BrowsePage(rows, offset, 14000, "page-" + offset, "page-" + (offset + 25), offset == 0 ? null : "page-" + (offset - 25), "index");
    }
    @Test void longForwardAndReverseTraversalStaysBoundedAndUsesServerLinks() {
        var window = new CollectionWindow();
        for (int i = 0; i < 1000; i += 25) {
            window.accept(page(i), i == 0 ? 0 : 1);
            assertTrue(window.size() <= 125);
            assertEquals("page-" + (i + 25), window.next());
        }
        assertEquals(875, window.pages().get(0).offset());
        for (int i = 850; i >= 0; i -= 25) {
            window.accept(page(i), -1);
            assertEquals(125, window.size());
        }
        assertNull(window.previous()); assertEquals(0, window.pages().get(0).offset());
    }
    @Test void arbitraryLetterJumpReplacesTheWindowAndRetainsUnalignedServerOffsets() {
        var window = new CollectionWindow(); window.accept(page(100), 0); window.accept(page(125), 1);
        window.accept(page(9137), 0);
        assertEquals(25, window.size()); assertEquals("page-9112", window.previous());
        assertEquals("page-9162", window.next()); window.clear(); assertEquals(0, window.size());
    }
}
