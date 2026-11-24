package dev.normalbias.client.ui;

import java.util.*;
import dev.normalbias.client.net.LibraryApi.BrowsePage;

/** A bounded, bidirectional collection window. Offsets and navigation come from the server. */
final class CollectionWindow {
    private final List<BrowsePage> pages = new ArrayList<>();
    void clear() { pages.clear(); }
    void accept(BrowsePage page, int direction) {
        if (direction == 0) clear();
        if (direction < 0) pages.add(0, page); else pages.add(page);
        while (pages.size() > 5) pages.remove(direction < 0 ? pages.size() - 1 : 0);
    }
    List<BrowsePage> pages() { return pages; }
    String next() { return pages.isEmpty() ? null : pages.get(pages.size() - 1).next(); }
    String previous() { return pages.isEmpty() ? null : pages.get(0).previous(); }
    int size() { int count = 0; for (BrowsePage page : pages) count += page.items().size(); return count; }
}
