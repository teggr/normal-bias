package dev.normalbias.client.net;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceCacheTest {
    @Test void queueCapabilitiesSurviveBrowseEvictionAndAreReleasedWhenQueueClears() {
        var cache = new ResourceCache();
        var grant = Map.<String,Object>of("_links", Map.of("playback-grant", Map.of("href", "/api/tracks/one/playback-grants", "method", "POST")));
        cache.put("queued", grant); cache.retain(List.of("queued"));
        for (int i = 0; i < 14000; i++) cache.put("track-" + i, Map.of("id", i));
        assertEquals(151, cache.size()); assertSame(grant, cache.get("queued"));
        assertEquals("/api/tracks/one/playback-grants", ApiLinks.href(cache.get("queued"), "playback-grant", "POST"));
        cache.retain(List.of()); assertEquals(150, cache.size()); assertNull(cache.get("queued"));
    }
}
