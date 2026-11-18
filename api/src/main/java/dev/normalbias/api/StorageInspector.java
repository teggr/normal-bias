package dev.normalbias.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Online, report-only inspection. Hashing never holds the catalogue mutation monitor. */
@Service
public class StorageInspector {
    private final JdbcTemplate db;
    private final TrackStore store;
    private final Path root;
    private final Path music;

    public StorageInspector(JdbcTemplate db, TrackStore store, StorageDirectory directory) {
        this.db = db;
        this.store = store;
        root = directory.root();
        music = root.resolve("music");
    }

    void inspect(boolean full, Observer observer) throws IOException {
        var tracks = store.inspectionSnapshot();
        observer.snapshot(tracks.size());
        for (Track track : tracks) {
            interrupted();
            observer.visited(inspectTrack(track, full, observer));
        }
        inspectMusic(observer);
        inspectDirectory(music.resolve("staging"), true, observer);
        inspectDirectory(music.resolve("quarantine"), false, observer);
        synchronized (store) {
            var active = store.activeUploadNames();
            db.query("SELECT id, staging_name FROM pending_uploads ORDER BY id", rows -> {
                if (!active.contains(rows.getString(2))) observer.finding(new Finding("PENDING_UPLOAD",
                        "music/staging/" + rows.getString(2), rows.getString(1), "Restart recovery required"));
            });
            for (String id : db.queryForList("SELECT id FROM pending_deletions ORDER BY id", String.class)) {
                observer.finding(new Finding("PENDING_DELETION", "music/" + id + ".mp3", id, "Restart recovery required"));
            }
        }
    }

    private boolean inspectTrack(Track snapshot, boolean full, Observer observer) throws IOException {
        Path file;
        BasicFileAttributes before;
        FileChannel channel;
        long audioVersion;
        synchronized (store) {
            if (!snapshot.equals(current(snapshot.id()))) return true;
            if (!snapshot.id().matches("[a-f0-9]{64}")) {
                observer.finding(new Finding("INVALID_ID", "", snapshot.id(), "Invalid catalogue content ID"));
                return false;
            }
            file = music.resolve(snapshot.id() + ".mp3");
            try {
                before = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!before.isRegularFile()) {
                    finding(observer, "UNEXPECTED_FILE", file, snapshot.id(), "Expected a regular file; symlinks are not followed");
                    return false;
                }
                if (before.size() != snapshot.sizeBytes()) {
                    finding(observer, "SIZE_MISMATCH", file, snapshot.id(), "Stored size differs from catalogue");
                    return false;
                }
                if (!full) return false;
                channel = FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
                audioVersion = store.audioVersion(snapshot.id());
            } catch (NoSuchFileException e) {
                finding(observer, "MISSING", file, snapshot.id(), "Catalogue audio is absent");
                return false;
            } catch (IOException e) {
                finding(observer, "UNREADABLE", file, snapshot.id(), e.getMessage());
                return false;
            }
        }

        // Keep the descriptor open across deletion/replacement, then revalidate before reporting.
        String hash = null;
        IOException failure = null;
        try (channel; var input = Channels.newInputStream(channel)) { hash = hash(input); }
        catch (InterruptedIOException e) { throw e; }
        catch (IOException e) { failure = e; }
        synchronized (store) {
            if (audioVersion != store.audioVersion(snapshot.id()) || !snapshot.equals(current(snapshot.id()))) return true;
            BasicFileAttributes after;
            try { after = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
            catch (NoSuchFileException e) { return true; }
            catch (IOException e) { finding(observer, "UNREADABLE", file, snapshot.id(), e.getMessage()); return false; }
            if (!sameFile(before, after)) return true;
            if (failure != null) finding(observer, "UNREADABLE", file, snapshot.id(), failure.getMessage());
            else if (!snapshot.id().equals(hash)) finding(observer, "HASH_MISMATCH", file, snapshot.id(), "SHA-256 differs from content ID");
        }
        return false;
    }

    private Track current(String id) {
        return db.query("SELECT * FROM tracks WHERE id = ?", TrackStore.ROW, id).stream().findFirst().orElse(null);
    }

    private static boolean sameFile(BasicFileAttributes before, BasicFileAttributes after) {
        return after.isRegularFile() && Objects.equals(before.fileKey(), after.fileKey())
                && before.size() == after.size() && before.lastModifiedTime().equals(after.lastModifiedTime());
    }

    String hash(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int read;
            while ((read = input.read(buffer)) != -1) {
                interrupted();
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private void inspectMusic(Observer observer) throws IOException {
        try (var files = Files.list(music)) {
            for (Path file : files.sorted().toList()) {
                interrupted();
                String name = file.getFileName().toString();
                if (name.equals("staging") || name.equals("quarantine")) continue;
                synchronized (store) {
                    if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (name.matches("[a-f0-9]{64}\\.mp3")) {
                        String id = name.substring(0, 64);
                        long references = db.queryForObject("""
                                SELECT (SELECT COUNT(*) FROM tracks WHERE id = ?)
                                + (SELECT COUNT(*) FROM pending_uploads WHERE id = ?)
                                + (SELECT COUNT(*) FROM pending_deletions WHERE id = ?)
                                """, Long.class, id, id, id);
                        if (references != 0) continue;
                        if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                            finding(observer, "ORPHAN", file, id, "Audio has no catalogue entry or pending operation");
                            continue;
                        }
                    }
                    finding(observer, "UNEXPECTED_FILE", file, null, "Unrecognised file or non-regular storage path");
                }
            }
        }
    }

    private void inspectDirectory(Path directory, boolean staging, Observer observer) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            finding(observer, "UNEXPECTED_FILE", directory, null, "Expected a directory; symlinks are not followed");
            return;
        }
        try (var files = Files.list(directory)) {
            for (Path file : files.sorted().toList()) {
                interrupted();
                synchronized (store) {
                    if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (staging && (store.activeUploadNames().contains(file.getFileName().toString())
                            || db.queryForObject("SELECT COUNT(*) FROM pending_uploads WHERE staging_name = ?", Integer.class,
                            file.getFileName().toString()) != 0)) continue;
                    finding(observer, staging ? "STAGING_FILE" : "QUARANTINED", file, null, "Preserved for inspection");
                }
            }
        }
    }

    private void finding(Observer observer, String kind, Path file, String id, String detail) {
        observer.finding(new Finding(kind, root.relativize(file).toString(), id, detail));
    }

    private static void interrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Inspection interrupted during shutdown");
    }

    public record Finding(String kind, String path, String id, String detail) {}
    interface Observer {
        void snapshot(long tracks);
        void visited(boolean changed);
        void finding(Finding finding);
    }
}
