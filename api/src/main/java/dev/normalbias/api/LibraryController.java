package dev.normalbias.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;

/** Live catalogue projections: grouping never changes track identity or storage. */
@RestController
@RequestMapping("/api/library")
public class LibraryController {
    private final JdbcTemplate db;
    public LibraryController(JdbcTemplate db) { this.db = db; }
    public record Group(String artist, String album, long trackCount, String trackId) {}
    public record Page(List<Group> items, int offset, int limit, long total) {}

    @GetMapping("/{view:albums|artists}")
    public Page groups(@PathVariable String view, @RequestParam(defaultValue="0") int offset,
                       @RequestParam(defaultValue="50") int limit, @RequestParam(defaultValue="") String q,
                       @RequestParam(required=false) String artist) {
        if (offset < 0 || limit < 1 || limit > 200 || q.length() > 1024 || (artist != null && artist.length() > 1024))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid library query");
        boolean albums = view.equals("albums");
        var args = new ArrayList<Object>();
        String filter = " WHERE 1=1";
        if (artist != null) { filter += " AND COALESCE(artist, '') = ?"; args.add(artist); }
        String term = q.strip().toLowerCase(Locale.ROOT);
        if (!term.isEmpty()) {
            filter += albums ? " AND (LOCATE(?, LOWER(COALESCE(album, ''))) > 0 OR LOCATE(?, LOWER(COALESCE(artist, ''))) > 0)"
                    : " AND LOCATE(?, LOWER(COALESCE(artist, ''))) > 0";
            args.add(term); if (albums) args.add(term);
        }
        String projection = "SELECT COALESCE(artist, '') AS artist, " + (albums ? "COALESCE(album, '')" : "''")
                + " AS album, COUNT(*) AS track_count, MIN(id) AS track_id FROM tracks" + filter
                + " GROUP BY COALESCE(artist, '')" + (albums ? ", COALESCE(album, '')" : "");
        long total = db.queryForObject("SELECT COUNT(*) FROM (" + projection + ") library_groups", Long.class, args.toArray());
        args.add(limit); args.add(offset);
        var rows = db.query("SELECT * FROM (" + projection + ") library_groups ORDER BY LOWER(artist), artist, LOWER(album), album LIMIT ? OFFSET ?",
                (r, n) -> new Group(r.getString("artist"), r.getString("album"), r.getLong("track_count"), r.getString("track_id")), args.toArray());
        return new Page(rows, offset, limit, total);
    }
}
