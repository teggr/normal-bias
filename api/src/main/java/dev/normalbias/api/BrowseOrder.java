package dev.normalbias.api;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared by H2's generated keys, collection ordering and alphabet indexes. */
public final class BrowseOrder {
    private BrowseOrder() { }
    public static String sortKey(String name) {
        String text = Normalizer.normalize(name == null ? "" : name.strip(), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return !text.isEmpty() && text.charAt(0) >= 'a' && text.charAt(0) <= 'z' ? text : "~" + text;
    }
    public static String initial(String name) {
        String key = sortKey(name);
        return key.charAt(0) == '~' ? "#" : key.substring(0, 1).toUpperCase(Locale.ROOT);
    }
    static boolean alphabetical(String sort) {
        if (!sort.isEmpty() && !sort.equals("alphabetical"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid library sort");
        return sort.equals("alphabetical");
    }
}
