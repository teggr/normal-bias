package dev.normalbias.client.net;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionErrorTest {
    @Test void separatesTimeoutCertificateAndHostnameFailuresWithoutShowingRawExceptions() {
        assertEquals(-2, AuthSession.connectionError(new IOException("Connection timed out")));
        assertEquals(-3, AuthSession.connectionError(new IOException("SSL certificate expired")));
        assertEquals(-4, AuthSession.connectionError(new IOException("UnknownHostException: music.example.com")));
        assertEquals(-1, AuthSession.connectionError(new IOException("https://user:secret@example.com private details")));
        assertTrue(AuthSession.message(-2).contains("Local Network permission"));
        assertTrue(AuthSession.message(-3).contains("certificate trust"));
        assertTrue(AuthSession.message(-4).contains("hostname"));
        assertFalse(AuthSession.message(-1).contains("secret"));
        assertTrue(AuthSession.message(426).contains("HTTPS"));
    }
}
