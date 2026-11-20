package dev.normalbias.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** Durable, bounded background work. Uploads, catalogue reads and playback never wait for providers. */
@Service
@EnableScheduling
@ConditionalOnProperty(name="normalbias.artwork.background-enabled", matchIfMissing=true)
public class BackgroundArtwork {
    private record Work(Track track, long metadataRevision, int attempts) {}
    private static final String DUE = "(c.track_id IS NULL OR c.metadata_revision<>t.metadata_revision OR c.next_attempt<=?)";
    private final JdbcTemplate db;
    private final TrackStore tracks;
    private final EmbeddedArtwork embedded;
    private final AlbumArtwork albums;
    private final Clock clock;

    @Autowired
    public BackgroundArtwork(JdbcTemplate db, TrackStore tracks, EmbeddedArtwork embedded, AlbumArtwork albums) {
        this(db, tracks, embedded, albums, Clock.systemUTC());
    }
    BackgroundArtwork(JdbcTemplate db, TrackStore tracks, EmbeddedArtwork embedded, AlbumArtwork albums, Clock clock) {
        this.db=db; this.tracks=tracks; this.embedded=embedded; this.albums=albums; this.clock=clock;
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private List<Work> due(String filter, Object... parameters) {
        return db.query("SELECT t.*, COALESCE(c.attempts,0) AS attempts FROM tracks t LEFT JOIN artwork_checks c ON c.track_id=t.id WHERE "
                + DUE + filter + " ORDER BY t.created_at,t.id LIMIT 200",
                (row, index) -> new Work(TrackStore.ROW.mapRow(row,index),row.getLong("metadata_revision"),row.getInt("attempts")), parameters);
    }

    @Scheduled(initialDelayString="${normalbias.artwork.initial-delay-ms:5000}",
            fixedDelayString="${normalbias.artwork.scan-delay-ms:30000}")
    public synchronized void runPending() {
        // At most 25 seeds per pass; one album's eligible tracks share one provider search.
        var seeds = due("", now()).stream().limit(25).toList();
        var processed = new HashSet<String>();
        for (Work seed : seeds) {
            if (Thread.currentThread().isInterrupted()) return;
            if (processed.contains(seed.track.id())) continue;
            var track = seed.track;
            var group = track.artist()==null || track.album()==null ? List.of(seed) : due(
                    " AND LOWER(TRIM(t.artist))=LOWER(TRIM(?)) AND LOWER(TRIM(t.album))=LOWER(TRIM(?))",
                    now(),track.artist(),track.album());
            var missing = new ArrayList<Work>();
            try {
                for (Work item : group) {
                    processed.add(item.track.id());
                    if (!claim(item)) continue;
                    if (embedded.get(item.track.id())!=null || albums.get(item.track.id())!=null) {
                        finish(item,"COVERED",null);
                    } else if (item.track.artist()==null || item.track.album()==null) {
                        finish(item,"MISSING_TAGS",null);
                    } else missing.add(item);
                }
                if (missing.isEmpty()) continue;
                int total=db.queryForObject("SELECT COUNT(*) FROM tracks WHERE LOWER(TRIM(artist))=LOWER(TRIM(?)) AND LOWER(TRIM(album))=LOWER(TRIM(?))",
                        Integer.class,track.artist(),track.album());
                boolean filled=albums.fillMissing(missing.stream().map(w -> w.track.id()).toList(),total);
                for (Work item : missing) finish(item,filled?"FILLED":"NO_MATCH",filled?null:now().plusDays(7));
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) { Thread.currentThread().interrupt(); return; }
                boolean stale=ex instanceof ResponseStatusException r && (r.getStatusCode().value()==409 || r.getStatusCode().value()==404);
                org.slf4j.LoggerFactory.getLogger(BackgroundArtwork.class).warn("Background artwork deferred: {}",ex.toString());
                // Also recover claimed items if embedded extraction failed before they reached 'missing'.
                for (Work item : group) {
                    long delay=stale?30:Math.min(86400,900L << Math.min(item.attempts,7));
                    OffsetDateTime retry=now().plusSeconds(delay);
                    if (ex instanceof MusicBrainzProvider.Unavailable unavailable) {
                        retry=OffsetDateTime.ofInstant(unavailable.retryAt(),ZoneOffset.UTC).plusSeconds(1);
                        if (retry.isBefore(now().plusSeconds(1))) retry=now().plusSeconds(1);
                    }
                    db.update("UPDATE artwork_checks SET state=?,next_attempt=? WHERE track_id=? AND metadata_revision=? AND state='CHECKING'",
                            stale?"STALE":"FAILED",retry,item.track.id(),item.metadataRevision);
                }
            }
        }
    }
    private boolean claim(Work item) {
        synchronized (tracks) {
            var current=db.queryForList("SELECT metadata_revision FROM tracks WHERE id=?",Long.class,item.track.id());
            if (current.isEmpty() || current.getFirst()!=item.metadataRevision) return false;
            // A lease survives a crash. Restart retries unfinished work after at most 15 minutes.
            db.update("MERGE INTO artwork_checks(track_id,metadata_revision,state,attempts,next_attempt) KEY(track_id) VALUES(?,?,?,?,?)",
                    item.track.id(),item.metadataRevision,"CHECKING",item.attempts+1,now().plusMinutes(15));
            return true;
        }
    }
    private void finish(Work item, String state, OffsetDateTime next) {
        db.update("UPDATE artwork_checks SET state=?,next_attempt=? WHERE track_id=? AND metadata_revision=?",
                state,next,item.track.id(),item.metadataRevision);
    }
}
