package dev.normalbias.client.net;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServiceAddressTest {
    @Test void normalizesHttpsOriginsWithoutTrialBuildRestrictions() {
        assertEquals("https://music.example.com", ServiceAddress.normalize("  https://Music.Example.com:443/  "));
        assertEquals("https://8.8.8.8:8443", ServiceAddress.normalize("https://8.8.8.8:08443"));
        assertEquals("https://[2001:db8::1]:8443", ServiceAddress.normalize("https://[2001:DB8::1]:8443/"));
        assertEquals("https://[::ffff:192.168.1.1]", ServiceAddress.normalize("https://[::ffff:192.168.1.1]"));
        assertEquals("https://localhost", ServiceAddress.normalize("https://localhost"));
        assertEquals("https://192.168.1.2:80", ServiceAddress.normalize("https://192.168.1.2:80"));
    }
    @Test void rejectsAmbiguousAddressesAndCredentialBearingUrls() {
        for (String value : new String[]{"https://[::1", "https://[:::1]", "https://[1::2::3]", "https://[12345::1]", "https://[a]", "https://[::1]x", "https://[1:2:3:4:5:6:7:8::]", "", "https://", "https://user:pass@music.example.com", "https://music.example.com/api", "https://music.example.com//", "https://music.example.com?x", "https://music.example.com#invite=secret", "https://music.example.com\\evil", "https://music.example.com:0", "https://music.example.com:65536", "https://music.example.com:", "https://music.example.com:abc", "https://music.example.com:443:443", "https://-bad.example", "https://a..example", "https://music.example.com\n.evil", "https://127.1", "https://192.168.001.1", "https://256.1.1.1", "https://999999999999.1.1.1"})
            assertThrows(IllegalArgumentException.class, () -> ServiceAddress.normalize(value), value);
        assertThrows(IllegalArgumentException.class, () -> ServiceAddress.normalize(null));
    }
    @Test void httpOriginsSupportTailscaleAndUseTheirOwnDefaultPort() {
        assertEquals("http://127.0.0.1:8080", ServiceAddress.selected("http://127.0.0.1:8080", "http://127.0.0.1:8080"));
        assertEquals("http://127.0.0.1:8080", ServiceAddress.selected("http://127.0.0.1:8080/", "http://127.0.0.1:8080/"));
        assertEquals("http://100.64.1.2:9090", ServiceAddress.selected("http://100.64.1.2:9090", null));
        assertEquals("http://music.tailnet.ts.net", ServiceAddress.normalize("http://Music.Tailnet.ts.net:80/"));
        assertEquals("http://music:443", ServiceAddress.normalize("http://music:443"));
        assertThrows(IllegalArgumentException.class, () -> ServiceAddress.normalize("http://music/path"));
        assertThrows(IllegalArgumentException.class, () -> ServiceAddress.normalize("http://user:pass@music"));
    }
}
