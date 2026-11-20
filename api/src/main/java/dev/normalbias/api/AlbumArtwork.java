package dev.normalbias.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static dev.normalbias.api.TrackStore.error;

/** Explicit, immutable selection snapshots. No catalogue metadata or audio is rewritten. */
@Service
public class AlbumArtwork {
    public record Selection(List<Track> items, int offset, int limit, long total, String warning) {}
    public record Start(List<String> trackIds) {}
    public record Accept(String candidateId, String revision) {}
    public record Result(String revision, List<String> trackIds) {}
    public record Status(String id, String state, String message, String revision, int tracks) {}
    public record Provenance(String revision, String provider, String releaseId, String sourceUrl,
                             OffsetDateTime acceptedAt, String imageHash, com.fasterxml.jackson.databind.JsonNode evidence) {}
    public Provenance provenance(String trackId) {
        tracks.get(trackId);
        return db.query("SELECT s.* FROM album_members m JOIN album_selections s ON m.selection_id=s.id WHERE m.track_id=?",
                (row, index) -> {
                    try { return new Provenance(row.getString("id"), row.getString("provider"), row.getString("release_id"),
                            row.getString("source_url"), row.getObject("accepted_at", OffsetDateTime.class), row.getString("image_hash"),
                            json.readTree(row.getString("evidence"))); }
                    catch (IOException ex) { throw new IllegalStateException("Invalid stored artwork provenance", ex); }
                }, trackId).stream().findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "No accepted cover"));
    }
    public record Candidates(List<AlbumProvider.Candidate> items, int offset, int limit, int total) {}
    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final List<String> ids;
        final String revision;
        final String evidence;
        final long created = System.currentTimeMillis();
        volatile String state = "LOOKING_UP", message = "Looking for release editions…";
        volatile List<AlbumProvider.Candidate> candidates = List.of();
        final Map<String, byte[]> previews = new HashMap<>();
        Job(List<String> ids, String revision, String evidence) { this.ids = ids; this.revision = revision; this.evidence = evidence; }
        Status status() { return new Status(id, state, message, revision, ids.size()); }
    }
    private final TrackStore tracks;
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final AlbumProvider provider;
    private final Path directory;
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public AlbumArtwork(TrackStore tracks, JdbcTemplate db, TransactionTemplate tx, AlbumProvider provider,
                        StorageDirectory storage) throws IOException {
        this.tracks = tracks; this.db = db; this.tx = tx; this.provider = provider;
        directory = storage.root().resolve("artwork");
        Files.createDirectories(directory);
        // The durable normalized image is also the recovery source. Never expose a half-file.
        db.query("SELECT hash, image FROM artwork_assets", row -> {
            try { publish(row.getString(1), row.getBytes(2)); }
            catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
        });
    }
    @PreDestroy void stop() { worker.shutdownNow(); }

    public Selection suggestions(String trackId, int offset, int limit) {
        page(offset, limit);
        synchronized (tracks) {
            Track seed = tracks.get(trackId);
            if (seed.artist() == null || seed.album() == null) return new Selection(offset == 0 ? List.of(seed) : List.of(),
                    offset, limit, 1, "Missing artist or album: only this track is suggested. Edition is unknown.");
            String where = " FROM tracks WHERE LOWER(TRIM(artist)) = LOWER(TRIM(?)) AND LOWER(TRIM(album)) = LOWER(TRIM(?))";
            long total = db.queryForObject("SELECT COUNT(*)" + where, Long.class, seed.artist(), seed.album());
            return new Selection(db.query("SELECT *" + where + " ORDER BY created_at, id LIMIT ? OFFSET ?", TrackStore.ROW,
                    seed.artist(), seed.album(), limit, offset), offset, limit, total,
                    "Artist and album tags are only a grouping hint. Edition, year, disc order and compilation identity are unverified. Uncheck unrelated tracks; embedded covers are preserved.");
        }
    }
    private static boolean same(String a, String b) { return a == null ? b == null : b != null && a.strip().equalsIgnoreCase(b.strip()); }
    private static void page(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) throw error(HttpStatus.BAD_REQUEST, "Invalid pagination");
    }
    private String evidence(List<String> ids) {
        try {
            var evidence = new ArrayList<Object>();
            for (String id : ids) {
                Track t = tracks.get(id);
                evidence.add(List.of(t, db.queryForMap("SELECT metadata_revision, artwork_revision FROM tracks WHERE id = ?", id)));
            }
            return json.writeValueAsString(evidence);
        } catch (IOException ex) { throw new IllegalStateException(ex); }
    }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static String revision(String evidence) { return hash(evidence.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

    public synchronized Status start(Start request) {
        jobs.values().removeIf(j -> System.currentTimeMillis() - j.created > 15 * 60_000);
        if (jobs.values().stream().anyMatch(j -> j.state.equals("LOOKING_UP"))) throw error(HttpStatus.CONFLICT, "A lookup is already active");
        if (request.trackIds() == null || request.trackIds().isEmpty() || request.trackIds().size() > 200
                || request.trackIds().stream().anyMatch(Objects::isNull)) throw error(HttpStatus.BAD_REQUEST, "Select 1–200 tracks");
        List<String> ids = request.trackIds().stream().distinct().sorted().toList();
        Job job;
        Track seed;
        synchronized (tracks) {
            seed = tracks.get(ids.getFirst());
            // Inconsistent tags require separate lookups; never infer compilation membership.
            if (seed.album() == null) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Album title is missing; edit catalogue metadata first");
            for (String id : ids) {
                Track t = tracks.get(id);
                if (!same(seed.album(), t.album()) || !same(seed.artist(), t.artist()))
                    throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Inconsistent tags: match separately");
            }
            String evidence = evidence(ids);
            job = new Job(ids, revision(evidence), evidence);
        }
        if (jobs.size() >= 20) jobs.remove(jobs.keySet().iterator().next());
        jobs.put(job.id, job);
        String artist = seed.artist(), album = seed.album();
        worker.submit(() -> {
            try {
                job.candidates = List.copyOf(provider.search(artist, album));
                job.state = job.candidates.isEmpty() ? "NO_MATCH" : "READY";
                job.message = job.candidates.isEmpty() ? "No matching releases. Retry later or correct the tags." :
                        "Choose an edition explicitly. Track order, year and durations have not been verified.";
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(AlbumArtwork.class).warn("Album provider lookup failed: {}", ex.toString());
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                job.state = "FAILED";
                job.message = "Provider unavailable or throttled. Your artwork is unchanged; retry later.";
            }
        });
        return job.status();
    }
    private synchronized Job job(String id) {
        Job job = jobs.get(id);
        if (job == null || System.currentTimeMillis() - job.created > 15 * 60_000) throw error(HttpStatus.GONE, "Lookup expired; start again");
        return job;
    }
    public Status status(String id) { return job(id).status(); }
    public Candidates candidates(String id, int offset, int limit) {
        page(offset, limit);
        var list = job(id).candidates;
        return new Candidates(list.stream().skip(offset).limit(limit).toList(), offset, limit, list.size());
    }
    public byte[] preview(String id, String candidateId) throws IOException, InterruptedException {
        Job job = job(id);
        if (job.candidates.stream().noneMatch(c -> c.id().equals(candidateId))) throw error(HttpStatus.NOT_FOUND, "Candidate not found");
        synchronized (job) {
            if (!job.previews.containsKey(candidateId)) job.previews.put(candidateId, provider.cover(candidateId));
            return job.previews.get(candidateId);
        }
    }
    public Result accept(String id, Accept request) throws IOException {
        Job job = job(id);
        if (request.candidateId() == null || job.candidates.stream().noneMatch(c -> c.id().equals(request.candidateId())))
            throw error(HttpStatus.BAD_REQUEST, "Invalid candidate");
        byte[] bytes;
        synchronized (job) { bytes = job.previews.get(request.candidateId()); }
        if (bytes == null) throw error(HttpStatus.CONFLICT, "Preview a valid cover before accepting it");
        // Check again at commit; no provider or image decoding under the catalogue monitor.
        String imageHash = hash(bytes);
        db.update("MERGE INTO artwork_assets (hash, image) KEY(hash) VALUES (?, ?)", imageHash, bytes);
        publish(imageHash, bytes);
        synchronized (tracks) {
            String fresh;
            try { fresh = revision(evidence(job.ids)); }
            catch (org.springframework.web.server.ResponseStatusException deleted) { throw error(HttpStatus.CONFLICT, "Tracks changed; match again"); }
            if (!job.revision.equals(request.revision()) || !fresh.equals(job.revision))
                throw error(HttpStatus.CONFLICT, "Tracks or artwork changed; match again");
            String selection = UUID.randomUUID().toString();
            String acceptedEvidence = json.writeValueAsString(Map.of("catalogueSnapshot", job.evidence,
                    "release", job.candidates.stream().filter(c -> c.id().equals(request.candidateId())).findFirst().orElseThrow(),
                    "limitations", "Catalogue-only evidence; year, disc order, track list and durations not compared"));
            tx.executeWithoutResult(ignored -> {
                db.update("INSERT INTO album_selections VALUES (?, ?, ?, ?, ?, ?, ?)", selection,
                        "MusicBrainz / Cover Art Archive", request.candidateId(),
                        "https://coverartarchive.org/release/" + request.candidateId() + "/front-500",
                        OffsetDateTime.now(ZoneOffset.UTC), imageHash, acceptedEvidence);
                for (String trackId : job.ids) {
                    db.update("MERGE INTO album_members (track_id, selection_id) KEY(track_id) VALUES (?, ?)", trackId, selection);
                    db.update("UPDATE tracks SET artwork_revision = ? WHERE id = ?", selection, trackId);
                }
            });
            job.state = "ACCEPTED"; job.message = "Cover accepted. Embedded images remain in use where present.";
            return new Result(selection, job.ids);
        }
    }
    private void publish(String hash, byte[] bytes) throws IOException {
        Path target = directory.resolve(hash + ".png");
        if (Files.exists(target) && hash(Files.readAllBytes(target)).equals(hash)) return;
        Path temp = Files.createTempFile(directory, "cover-", ".part");
        try {
            Files.write(temp, bytes);
            try (var channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    public byte[] get(String trackId) throws IOException {
        var hashes = db.queryForList("SELECT s.image_hash FROM album_members m JOIN album_selections s ON m.selection_id=s.id WHERE m.track_id=?", String.class, trackId);
        return hashes.isEmpty() ? null : Files.readAllBytes(directory.resolve(hashes.getFirst() + ".png"));
    }
}
