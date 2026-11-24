package dev.normalbias.api;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;
import static dev.normalbias.api.AuthPolicy.*;

/** Alphabet destinations are live projections of exactly the same filtered collection. */
@RestController
final class LibraryBrowse {
    private final JdbcTemplate db;
    LibraryBrowse(JdbcTemplate db) { this.db = db; }
    record Selection(String sql, List<Object> args) { }
    record Section(String initial, long count, long offset, Map<String, Link> _links) { }
    record Index(List<Section> items, long total) { }

    static Selection selection(String view, String q, String artist, String album) {
        if (!Set.of("songs", "albums", "artists").contains(view) || q.length() > 1024
                || (artist != null && artist.length() > 1024) || (album != null && album.length() > 1024))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid library query");
        var args = new ArrayList<Object>();
        String filter = " WHERE 1=1";
        if (artist != null) { filter += " AND COALESCE(artist, '') = ?"; args.add(artist); }
        if (album != null) { filter += " AND COALESCE(album, '') = ?"; args.add(album); }
        String term = q.strip().toLowerCase(Locale.ROOT);
        String[] columns = view.equals("songs") ? new String[]{"title", "artist", "album", "filename"}
                : view.equals("albums") ? new String[]{"album", "artist"} : new String[]{"artist"};
        if (!term.isEmpty()) {
            var predicates = new ArrayList<String>();
            for (String column : columns) { predicates.add("LOCATE(?, LOWER(COALESCE(" + column + ", ''))) > 0"); args.add(term); }
            filter += " AND (" + String.join(" OR ", predicates) + ")";
        }
        if (view.equals("songs")) return new Selection("SELECT * FROM tracks" + filter, args);
        boolean albums = view.equals("albums");
        return new Selection("SELECT COALESCE(artist, '') AS artist, " + (albums ? "COALESCE(album, '')" : "''")
                + " AS album, COUNT(*) AS track_count, MIN(id) AS track_id FROM tracks" + filter
                + " GROUP BY COALESCE(artist, '')" + (albums ? ", COALESCE(album, '')" : ""), args);
    }

    @GetMapping("/api/library/index")
    Index index(@RequestParam String view, @RequestParam(defaultValue="") String q,
                @RequestParam(required=false) String artist, @RequestParam(required=false) String album,
                @RequestParam(defaultValue="25") int limit) {
        if (limit < 1 || limit > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination");
        var selection = selection(view, q, artist, album);
        String name = view.equals("songs") ? "title" : view.equals("albums") ? "album" : "artist";
        String key = "SUBSTRING(NB_BROWSE_KEY(" + name + "), 1, 1)";
        Map<String, Long> counts = new LinkedHashMap<>();
        db.query("SELECT " + key + " AS initial, COUNT(*) AS amount FROM (" + selection.sql()
                + ") browse GROUP BY " + key + " ORDER BY initial", r -> {
                    counts.put(r.getString("initial").equals("~") ? "#" : r.getString("initial").toUpperCase(Locale.ROOT), r.getLong("amount"));
                }, selection.args().toArray());
        Map<String, Long> offsets = new LinkedHashMap<>();
        long total = 0;
        for (var entry : counts.entrySet()) { offsets.put(entry.getKey(), total); total += entry.getValue(); }
        var sections = new ArrayList<Section>();
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
        for (int i = 0; i < alphabet.length(); i++) {
            String initial = alphabet.substring(i, i + 1), destination = initial;
            if (!offsets.containsKey(destination)) {
                destination = null;
                for (int j = i + 1; j < alphabet.length(); j++) if (offsets.containsKey(alphabet.substring(j, j + 1))) {
                    destination = alphabet.substring(j, j + 1); break;
                }
                if (destination == null && !offsets.isEmpty()) destination = new ArrayList<>(offsets.keySet()).get(offsets.size() - 1);
            }
            long offset = destination == null ? 0 : offsets.get(destination);
            var uri = UriComponentsBuilder.fromPath(view.equals("songs") ? "/api/tracks" : "/api/library/" + view)
                    .queryParam("sort", "alphabetical").queryParam("q", q).queryParam("limit", limit).queryParam("offset", offset);
            if (artist != null) uri.queryParam("artist", artist);
            if (album != null) uri.queryParam("album", album);
            sections.add(new Section(initial, counts.getOrDefault(initial, 0L), offset,
                    total == 0 ? Map.of() : Map.of("jump", link(uri.build().encode().toUriString(), "GET"))));
        }
        return new Index(sections, total);
    }
}
