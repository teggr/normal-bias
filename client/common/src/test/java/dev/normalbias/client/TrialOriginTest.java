package dev.normalbias.client;

import dev.normalbias.client.net.TrialOrigin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrialOriginTest {
    @Test void permitsOnlyExplicitPrivateIpv4AndUnprivilegedPorts() {
        for (String origin : new String[]{"http://10.0.0.2:1024", "http://172.16.0.1:8080", "http://172.31.255.254:65535", "http://192.168.1.205:18082"})
            assertTrue(TrialOrigin.valid(origin), origin);
        for (String origin : new String[]{"http://127.0.0.1:8080", "http://0.0.0.0:8080", "http://8.8.8.8:8080", "http://172.15.1.1:8080", "http://172.32.1.1:8080", "http://192.168.256.1:8080", "http://192.168.01.1:8080", "http://192.168.1.1:80", "http://192.168.1.1:65536", "http://192.168.1.1:8080/", "http://user@192.168.1.1:8080", "http://192.168.1.1:8080?x", "https://192.168.1.1:8080", "http://192.168.1.1.2:8080", "http://localhost:8080", ""})
            assertFalse(TrialOrigin.valid(origin), origin);
        assertFalse(TrialOrigin.valid(null));
    }
}
