package dev.normalbias.api;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AuthAdminOriginTest {
    @Test void shareOriginsCannotSmugglePathsCredentialsOrFragments() {
        assertEquals("https://localhost:8443", AuthAdmin.checkedAppOrigin("https://localhost:8443"));
        assertEquals("https://[::1]:8443", AuthAdmin.checkedAppOrigin("https://[::1]:8443"));
        for (String value : new String[]{"http://localhost", "https://user@localhost", "https://localhost/", "https://localhost/path",
                "https://localhost?token=x", "https://localhost#reset=x", "https://localhost:0", "https://localhost:99999", "https://localhost:", "https://localhost\\evil"})
            assertThrows(IllegalArgumentException.class, () -> AuthAdmin.checkedAppOrigin(value), value);
    }
}
