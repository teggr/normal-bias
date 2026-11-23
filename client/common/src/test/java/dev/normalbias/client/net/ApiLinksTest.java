package dev.normalbias.client.net;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ApiLinksTest {
    @Test void rejectsCredentialExfiltrationAndAmbiguousPaths() {
        for (String href : new String[]{"https://other.test/api", "//other.test/api", "/api/../other", "/api/%2e%2e/other", "/api/a\\b", "/api/a\n", "/api/a#ignored", "/api//other"})
            assertThrows(IllegalArgumentException.class, () -> ApiLinks.checked(href), href);
        assertEquals("/api/tracks?offset=25", ApiLinks.checked("/api/tracks?offset=25"));
    }
    @Test void actionsMustBeAdvertisedWithTheExpectedMethod() {
        Map<String, Object> resource = Map.of("_links", Map.of("playback-grant", Map.of("href", "/api/tracks/a/playback-grants", "method", "POST")));
        assertNull(ApiLinks.href(resource, "delete", "DELETE"));
        assertNull(ApiLinks.href(resource, "playback-grant", "GET"));
        assertEquals("/api/tracks/a/playback-grants", ApiLinks.href(resource, "playback-grant", "POST"));
    }
}
