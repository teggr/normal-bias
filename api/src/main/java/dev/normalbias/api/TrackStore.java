package dev.normalbias.api;

import com.mpatric.mp3agic.*;
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
    private final long maxBytes;
    private static final RowMapper<Track> ROW = (r, n) -> new Track(r.getString("id"),
            r.getString("filename"), r.getString("title"), r.getString("artist"), r.getString("album"),
            r.getLong("duration_ms"), r.getLong("size_bytes"), r.getObject("created_at", OffsetDateTime.class));

    public TrackStore(JdbcTemplate db, @Value("${normalbias.data-dir}") String directory,
                      @Value("${normalbias.max-upload-bytes}") long maxBytes) throws IOException {
        this.db = db;
        this.maxBytes = maxBytes;
        music = Files.createDirectories(Path.of(directory).toAbsolutePath().resolve("music"));
        staging = Files.createDirectories(music.resolve("staging"));
        // This directory belongs to the single service instance; clear interrupted uploads on startup.
        try (var paths = Files.list(staging)) {
            for (Path p : paths.filter(p -> p.getFileName().toString().endsWith(".part")).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    public List<Track> list(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) throw error(HttpStatus.BAD_REQUEST, "Invalid pagination");
        return db.query("SELECT * FROM tracks ORDER BY created_at, id LIMIT ? OFFSET ?", ROW, limit, offset);
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
        Path temp = Files.createTempFile(staging, "upload-", ".part");
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
            return commit(temp, track);
        } finally { Files.deleteIfExists(temp); }
    }

    // Serialize the file/row commit and duplicate check within the single service instance.
    private synchronized UploadResult commit(Path temp, Track track) throws IOException {
        Optional<Track> existing = find(track.id());
        if (existing.isPresent()) return new UploadResult(existing.get(), false);
        Path destination = path(track);
        // An orphan from a crash between move and insert can safely be replaced by identical bytes.
        Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        try {
            db.update("INSERT INTO tracks VALUES (?, ?, ?, ?, ?, ?, ?, ?)", track.id(), track.filename(),
                    track.title(), track.artist(), track.album(), track.durationMs(), track.sizeBytes(), track.createdAt());
        } catch (RuntimeException e) {
            Files.deleteIfExists(destination);
            throw e;
        }
        return new UploadResult(track, true);
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
