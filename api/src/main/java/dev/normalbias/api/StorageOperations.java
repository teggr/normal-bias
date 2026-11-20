package dev.normalbias.api;

import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Durable intent precedes file changes; replay is idempotent and precedes HTTP service startup. */
@Service
@DependsOnDatabaseInitialization
public class StorageOperations {
    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    private final Path music;
    private final Path staging;
    private final Path quarantine;

    public StorageOperations(JdbcTemplate db, PlatformTransactionManager manager, StorageDirectory directory) throws IOException {
        this.db = db;
        transaction = new TransactionTemplate(manager);
        music = subdirectory(directory.root().resolve("music"));
        staging = subdirectory(music.resolve("staging"));
        quarantine = subdirectory(music.resolve("quarantine"));
        recover();
    }

    private static Path subdirectory(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Storage directory must not be a symlink: " + path);
        return Files.createDirectories(path);
    }

    Path staging() { return staging; }
    Path path(String id) { requireId(id); return music.resolve(id + ".mp3"); }

    void install(Track track, Path temp) throws IOException {
        finishDeletion(track.id());
        if (db.queryForObject("SELECT COUNT(*) FROM pending_uploads WHERE id = ?", Integer.class, track.id()) != 0) {
            throw new IOException("Upload recovery pending; restart the API or use online inspection");
        }
        // Flush validated bytes before recording an intent that promises they can be recovered.
        try (var file = FileChannel.open(temp, StandardOpenOption.WRITE)) { file.force(true); }
        db.update("INSERT INTO pending_uploads VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", track.id(), track.filename(),
                track.title(), track.artist(), track.album(), track.durationMs(), track.sizeBytes(), track.createdAt(),
                temp.getFileName().toString());
        finishUpload(track, temp.getFileName().toString());
    }

    void delete(Track track) throws IOException {
        if (db.queryForObject("SELECT COUNT(*) FROM pending_uploads WHERE id = ?", Integer.class, track.id()) != 0) {
            throw new IOException("Upload recovery pending; restart the API or use online inspection");
        }
        transaction.executeWithoutResult(status -> {
            db.update("INSERT INTO pending_deletions VALUES (?)", track.id());
            db.update("DELETE FROM tracks WHERE id = ?", track.id());
        });
        // Only committed deletion intents permit removal of audio. Database failure leaves it untouched.
        finishDeletion(track.id());
    }

    private void finishDeletion(String id) throws IOException {
        requireId(id);
        if (db.queryForObject("SELECT COUNT(*) FROM pending_deletions WHERE id = ?", Integer.class, id) == 0) return;
        if (db.queryForObject("SELECT COUNT(*) FROM tracks WHERE id = ?", Integer.class, id) != 0) {
            throw new IOException("Conflicting deletion intent; preserving audio for " + id);
        }
        Path file = path(id);
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Unexpected deletion path; preserving " + file);
        }
        Files.deleteIfExists(file);
        db.update("DELETE FROM pending_deletions WHERE id = ?", id);
    }

    private void finishUpload(Track track, String name) throws IOException {
        if (!name.matches("upload-[0-9]+\\.part")) throw new IOException("Invalid upload staging name; preserving operation");
        Path temp = staging.resolve(name);
        Path destination = path(track.id());
        if (!matches(destination, track)) {
            if (!matches(temp, track)) throw new IOException("Unrecoverable upload " + track.id() + "; files preserved for investigation");
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) quarantine(destination);
            Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE);
        }
        transaction.executeWithoutResult(status -> {
            if (db.queryForObject("SELECT COUNT(*) FROM tracks WHERE id = ?", Integer.class, track.id()) == 0) {
                db.update("INSERT INTO tracks (id, filename, title, artist, album, duration_ms, size_bytes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", track.id(), track.filename(),
                        track.title(), track.artist(), track.album(), track.durationMs(), track.sizeBytes(), track.createdAt());
            }
            db.update("DELETE FROM pending_uploads WHERE id = ?", track.id());
        });
        Files.deleteIfExists(temp);
    }

    void recover() throws IOException {
        for (String id : db.queryForList("SELECT id FROM pending_deletions ORDER BY id", String.class)) finishDeletion(id);
        var uploads = db.query("SELECT *, 'embedded' AS artwork_revision FROM pending_uploads ORDER BY id", (r, n) ->
                new PendingUpload(TrackStore.ROW.mapRow(r, n), r.getString("staging_name")));
        for (var upload : uploads) finishUpload(upload.track(), upload.name());

        // Recover legacy deletion staging before discarding known, uncommitted upload fragments.
        try (var files = Files.list(staging)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.matches("[a-f0-9]{64}\\.part")) {
                    String id = name.substring(0, 64);
                    var tracks = db.query("SELECT * FROM tracks WHERE id = ?", TrackStore.ROW, id);
                    if (!tracks.isEmpty() && !Files.exists(path(id), LinkOption.NOFOLLOW_LINKS) && matches(file, tracks.getFirst())) {
                        Files.move(file, path(id), StandardCopyOption.ATOMIC_MOVE);
                    } else quarantine(file);
                } else if (name.matches("upload-[0-9]+\\.part") && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    Files.delete(file); // No remaining intent references this abandoned request body.
                } else quarantine(file);
            }
        }
    }

    private void quarantine(Path file) throws IOException {
        Files.move(file, quarantine.resolve(UUID.randomUUID() + "-" + file.getFileName()), StandardCopyOption.ATOMIC_MOVE);
    }

    static void requireId(String id) {
        if (!id.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid storage ID");
    }

    static boolean matches(Path file, Track track) throws IOException {
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) == track.sizeBytes()
                && sha256(file).equals(track.id());
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] bytes = new byte[65536];
                int length;
                while ((length = input.read(bytes)) != -1) digest.update(bytes, 0, length);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private record PendingUpload(Track track, String name) {}
}
