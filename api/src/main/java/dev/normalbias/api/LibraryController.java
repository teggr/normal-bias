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
                       @RequestParam(required=false) String artist,
                       @RequestParam(required=false) String album,
                       @RequestParam(defaultValue="") String sort) {
        boolean alphabetical = BrowseOrder.alphabetical(sort);
        if (offset < 0 || limit < 1 || limit > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid library query");
        boolean albums = view.equals("albums");
        var selection = LibraryBrowse.selection(view, q, artist, album);
        String projection = selection.sql();
        var args = new ArrayList<>(selection.args());
        long total = db.queryForObject("SELECT COUNT(*) FROM (" + projection + ") library_groups", Long.class, args.toArray());
        args.add(limit); args.add(offset);
        String order = !alphabetical ? "LOWER(artist), artist, LOWER(album), album" : albums
                ? "NB_BROWSE_KEY(album), album, NB_BROWSE_KEY(artist), artist" : "NB_BROWSE_KEY(artist), artist";
        var rows = db.query("SELECT * FROM (" + projection + ") library_groups ORDER BY " + order + " LIMIT ? OFFSET ?",
                (r, n) -> new Group(r.getString("artist"), r.getString("album"), r.getLong("track_count"), r.getString("track_id")), args.toArray());
        return new Page(rows, offset, limit, total);
    }
}
