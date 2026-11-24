package dev.normalbias.api;

import com.mpatric.mp3agic.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class TrackStore {
    private final JdbcTemplate db;
    private final Path music;
    private final Path staging;
    private final StorageOperations operations;
    private final Set<String> activeUploads = new HashSet<>();
    private final Map<String, Long> audioVersions = new HashMap<>();
    private long mutationSequence;
    private final long maxBytes;
    static final RowMapper<Track> ROW = (r, n) -> new Track(r.getString("id"),
            r.getString("filename"), r.getString("title"), r.getString("artist"), r.getString("album"),
            r.getLong("duration_ms"), r.getLong("size_bytes"), r.getObject("created_at", OffsetDateTime.class), r.getString("artwork_revision"));

    public TrackStore(JdbcTemplate db, StorageOperations operations,
                      @Value("${normalbias.max-upload-bytes}") long maxBytes) throws IOException {
        this.db = db;
        this.maxBytes = maxBytes;
        this.operations = operations;
        staging = operations.staging();
        music = staging.getParent();
    }

    public synchronized TrackPage list(int offset, int limit, String query) {
        return list(offset, limit, query, null, null);
    }

    public synchronized TrackPage list(int offset, int limit, String query, String artist, String album) {
        return list(offset, limit, query, artist, album, "");
    }

    public synchronized TrackPage list(int offset, int limit, String query, String artist, String album, String sort) {
        boolean alphabetical = BrowseOrder.alphabetical(sort);
        var selection = LibraryBrowse.selection("songs", query, artist, album);
        if (offset < 0 || limit < 1 || limit > 200) throw error(HttpStatus.BAD_REQUEST, "Invalid pagination");
        long total = db.queryForObject("SELECT COUNT(*) FROM (" + selection.sql() + ") browse", Long.class, selection.args().toArray());
        var parameters = new ArrayList<>(selection.args());
        Collections.addAll(parameters, limit, offset);
        var items = db.query(selection.sql() + (alphabetical ? " ORDER BY title_sort, title, id" : " ORDER BY created_at, id") + " LIMIT ? OFFSET ?",
                ROW, parameters.toArray());
        return new TrackPage(items, offset, limit, total);
    }

    public synchronized Track edit(String id, JsonNode changes) {
        Track existing = get(id);
        if (!changes.isObject() || changes.isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "Provide a nonempty metadata object");
        }
        var fields = changes.fieldNames();
        while (fields.hasNext()) {
            if (!Set.of("title", "artist", "album").contains(fields.next())) {
                throw error(HttpStatus.BAD_REQUEST, "Only title, artist and album can be edited");
            }
        }
        String title = editedText(changes, "title", existing.title());
        String artist = editedText(changes, "artist", existing.artist());
        String album = editedText(changes, "album", existing.album());
        db.update("UPDATE tracks SET title = ?, artist = ?, album = ?, metadata_revision = metadata_revision + 1 WHERE id = ?", title, artist, album, id);
        return get(id);
    }

    private static String editedText(JsonNode changes, String name, String previous) {
        if (!changes.has(name)) return previous;
        JsonNode value = changes.get(name);
        if (!value.isNull() && !value.isTextual()) throw error(HttpStatus.BAD_REQUEST, "Metadata must be text or null");
        String text = value.isNull() ? null : value.textValue().strip();
        if (text != null && (text.length() > 1024 || text.chars().anyMatch(Character::isISOControl))) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid metadata text");
        }
        if (text != null && text.isBlank()) text = null;
        if (name.equals("title") && text == null) throw error(HttpStatus.BAD_REQUEST, "Title must not be blank or null");
        return text;
    }

    // Share the upload commit monitor so deleting cannot remove a newly re-uploaded file.
    public synchronized void delete(String id) throws IOException {
        Track track = get(id);
        try { operations.delete(track); }
        finally { audioVersions.put(id, ++mutationSequence); }
    }

    public Track get(String id) {
        if (!id.matches("[a-f0-9]{64}")) throw error(HttpStatus.NOT_FOUND, "Track not found");
        return find(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Track not found"));
    }

    private Optional<Track> find(String id) {
        return db.query("SELECT * FROM tracks WHERE id = ?", ROW, id).stream().findFirst();
    }

    public Path path(Track track) { return music.resolve(track.id() + ".mp3"); }

    public UploadResult upload(InputStream input, String filename, long declaredSize) throws IOException {
        if (declaredSize > maxBytes) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Upload too large");
        String safeName = filename.replace('\\', '/');
        safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
        if (safeName.isBlank() || safeName.length() > 1024 || safeName.chars().anyMatch(Character::isISOControl)) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid filename");
        }
        Path temp = beginUpload();
        boolean handedToCommit = false;
        try {
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            long size = 0;
            try (OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > maxBytes) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Upload too large");
                    digest.update(buffer, 0, count);
                    out.write(buffer, 0, count);
                }
            }
            if (declaredSize >= 0 && declaredSize != size) throw error(HttpStatus.BAD_REQUEST, "Incomplete upload");
            if (size == 0) throw error(HttpStatus.BAD_REQUEST, "Empty upload");
            String id = HexFormat.of().formatHex(digest.digest());
            Mp3File mp3;
            try {
                mp3 = new Mp3File(temp);
                if (mp3.getFrameCount() < 2) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid MP3");
            } catch (InvalidDataException | UnsupportedTagException | IllegalArgumentException e) {
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid MP3");
            }
            ID3v1 tag = mp3.hasId3v2Tag() ? mp3.getId3v2Tag() : mp3.getId3v1Tag();
            String title = tag == null ? null : text(tag.getTitle());
            Track track = new Track(id, safeName, title == null ? safeName : title,
                    tag == null ? null : text(tag.getArtist()), tag == null ? null : text(tag.getAlbum()),
                    mp3.getLengthInMilliseconds(), size, OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            handedToCommit = true;
            return commit(temp, track);
        } finally {
            // A failed commit may have a durable intent; preserve its source bytes for replay.
            synchronized (this) {
                try { if (!handedToCommit) Files.deleteIfExists(temp); }
                finally { activeUploads.remove(temp.getFileName().toString()); }
            }
        }
    }

    private synchronized Path beginUpload() throws IOException {
        Path file = Files.createTempFile(staging, "upload-", ".part");
        activeUploads.add(file.getFileName().toString());
        return file;
    }

    synchronized Set<String> activeUploadNames() { return Set.copyOf(activeUploads); }

    synchronized long audioVersion(String id) { return audioVersions.getOrDefault(id, 0L); }

    synchronized List<Track> inspectionSnapshot() { return db.query("SELECT * FROM tracks ORDER BY id", ROW); }

    // Serialize the file/row commit and duplicate check within the single service instance.
    private synchronized UploadResult commit(Path temp, Track track) throws IOException {
        try {
            Optional<Track> existing = find(track.id());
            if (existing.isPresent()) {
                if (StorageOperations.matches(path(existing.get()), existing.get())) Files.deleteIfExists(temp);
                else operations.install(existing.get(), temp); // Restore audio without losing edited metadata.
                return new UploadResult(existing.get(), false);
            }
            operations.install(track, temp);
            return new UploadResult(track, true);
        } finally { audioVersions.put(track.id(), ++mutationSequence); }
    }

    private static String text(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.strip();
        return s.substring(0, Math.min(s.length(), 1024));
    }

    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    public record UploadResult(Track track, boolean created) {}
}
