package dev.normalbias.client.net;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AccountLinkTest {
    private final String token = "a".repeat(64), origin = "https://127.0.0.1:18444";
    @Test void readsOnlyCompleteSameOriginFragmentLinks() {
        assertTrue(AccountLink.parse(origin + "/#invite=" + token, origin).invitation);
        assertFalse(AccountLink.parse(origin + "/#reset=" + token, origin).invitation);
        for (String value : new String[]{"https://other.test/#invite="+token, origin+".evil/#invite="+token,
                origin+"/?token="+token, origin+"/#reset="+token+"&extra=1", origin+"/#invite=short",
                origin+"/other#invite="+token, origin+"/#reset="+"Z".repeat(64)})
            assertThrows(IllegalArgumentException.class, () -> AccountLink.parse(value, origin));
        assertThrows(IllegalArgumentException.class, () -> AccountLink.parse("http://localhost/#invite="+token, "http://localhost"));
    }
}
