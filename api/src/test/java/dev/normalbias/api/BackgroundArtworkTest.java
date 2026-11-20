package dev.normalbias.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BackgroundArtworkTest {
    @TempDir Path root;
    AlbumArtworkTest f;
    final Instant epoch=Instant.parse("2026-11-20T12:00:00Z");
    BackgroundArtwork worker(long seconds) {
        return new BackgroundArtwork(f.db,f.tracks,new EmbeddedArtwork(f.tracks),f.service,
                Clock.fixed(epoch.plusSeconds(seconds),ZoneOffset.UTC));
    }
    @BeforeEach void setup() throws Exception { f=new AlbumArtworkTest(); f.root=root; f.setup(); }
    @AfterEach void close() throws Exception { f.close(); }
    String state(String id) { return f.db.queryForObject("SELECT state FROM artwork_checks WHERE track_id=?",String.class,id); }

    @Test void fillsExistingLibraryWithoutAClientAndNewUploadsOnLaterPasses() throws Exception {
        String a=f.insert(1,"Artist","Album"), b=f.insert(2,"Artist","Album");
        byte[] audio=Files.readAllBytes(f.tracks.path(f.tracks.get(a)));
        var before=f.tracks.get(a);
        worker(0).runPending();
        assertArrayEquals(f.image,f.service.get(a)); assertArrayEquals(f.image,f.service.get(b));
        assertEquals("FILLED",state(a)); assertEquals(1,f.searches); assertEquals(1,f.covers);
        assertEquals(1,f.service.artworkVersion());
        assertEquals(before.title(),f.tracks.get(a).title());
        assertArrayEquals(audio,Files.readAllBytes(f.tracks.path(before)));
        assertTrue(f.service.provenance(a).evidence().path("decision").asText().contains("automatic background"));
        // Durable success is not re-fetched by a new worker instance.
        worker(60).runPending(); assertEquals(1,f.searches);
        String c=f.insert(3,"Artist","Album"); worker(90).runPending();
        assertEquals("FILLED",state(c)); assertEquals(2,f.searches);
        assertEquals(before.id(),f.tracks.get(a).id());
    }

    @Test void preservesEmbeddedAndPreviouslyAcceptedCoversAndSkipsMissingTags() throws Exception {
        String embedded=f.insert(1,"Artist","Album"), saved=f.insert(2,"Artist","Album"), missing=f.insert(3,null,null);
        Path path=f.tracks.path(f.tracks.get(embedded)), changed=root.resolve("tagged.mp3");
        var mp3=new com.mpatric.mp3agic.Mp3File(path);
        var tag=new com.mpatric.mp3agic.ID3v24Tag(); tag.setAlbumImage(f.image,"image/png");mp3.setId3v2Tag(tag);mp3.save(changed.toString());
        Files.move(changed,path,StandardCopyOption.REPLACE_EXISTING);
        f.accept(f.lookup(List.of(saved))); int searches=f.searches;
        byte[] original=f.service.get(saved);
        worker(0).runPending();
        assertEquals(searches,f.searches); assertEquals("COVERED",state(embedded)); assertEquals("COVERED",state(saved));
        assertEquals("MISSING_TAGS",state(missing)); assertArrayEquals(original,f.service.get(saved));
        assertNull(f.service.get(embedded)); // embedded art never gets a sidecar association
    }

    @Test void negativeResultsAndFailuresBackOffAcrossWorkerRestartsButTagEditsAreEligible() throws Exception {
        String id=f.insert(1,"Artist","Album");
        f.offline=true;worker(0).runPending(); assertEquals("FAILED",state(id));
        worker(30).runPending(); assertEquals(1,f.searches);
        f.offline=false; f.candidates=List.of();worker(901).runPending();
        assertEquals("NO_MATCH",state(id));worker(1000).runPending();assertEquals(2,f.searches);
        worker(901+8*86400).runPending(); assertEquals(3,f.searches);
        f.tracks.edit(id,new ObjectMapper().readTree("{\"title\":\"Edited\"}"));
        worker(902+8*86400).runPending();assertEquals(4,f.searches);
    }

    @Test void crashedLeaseResumesAfterExpiryAndDoesNotHammerProvider() throws Exception {
        String id=f.insert(1,"Artist","Album");
        f.db.update("INSERT INTO artwork_checks VALUES(?,0,'CHECKING',1,?)",id,OffsetDateTime.ofInstant(epoch.plusSeconds(900),ZoneOffset.UTC));
        worker(0).runPending();assertEquals(0,f.searches);
        worker(901).runPending();assertEquals("FILLED",state(id));assertEquals(1,f.searches);
    }

    @Test void temporaryProviderOutageUsesItsCooldownInsteadOfLongExponentialBackoff() throws Exception {
        String id=f.insert(1,"Artist","Album");
        f.offline=true;f.retryAt=epoch.plusSeconds(120);
        worker(0).runPending();
        assertEquals(epoch.plusSeconds(121),f.db.queryForObject("SELECT next_attempt FROM artwork_checks WHERE track_id=?",
                OffsetDateTime.class,id).toInstant());
        worker(90).runPending();assertEquals(1,f.searches);
        f.offline=false;worker(122).runPending();
        assertEquals("FILLED",state(id));assertEquals(2,f.searches);
    }

    @Test void concurrentMetadataChangeCannotCommitTheOldMatch() throws Exception {
        String id=f.insert(1,"Artist","Album");
        f.beforeCover=() -> f.tracks.edit(id,new ObjectMapper().createObjectNode().put("album","Other"));
        worker(0).runPending();
        assertNull(f.service.get(id));assertEquals("STALE",state(id));assertEquals(0,f.service.artworkVersion());
        f.beforeCover=() -> {};
        f.candidates=List.of(new AlbumProvider.Candidate(AlbumArtworkTest.RELEASE,"Other","Artist","","","",1,"group",""));
        worker(1).runPending();assertEquals("FILLED",state(id));
    }

    @Test void rejectsFuzzyMatchesAndDifferentReleaseGroupsButTriesAnotherExactCover() throws Exception {
        String id=f.insert(1,"Artist","Album");
        var original=f.candidates;
        f.candidates=List.of(new AlbumProvider.Candidate(AlbumArtworkTest.RELEASE,"Different album","Artist","","","",1,"group",""));
        worker(0).runPending();assertNull(f.service.get(id));assertEquals(0,f.covers);
        f.db.update("DELETE FROM artwork_checks");
        f.candidates=List.of(original.getFirst(),new AlbumProvider.Candidate(original.getLast().id(),"Album","Artist","","","",2,"different-group",""));
        worker(1).runPending();assertNull(f.service.get(id));assertEquals(0,f.covers);
        f.db.update("DELETE FROM artwork_checks");f.candidates=original;f.missingCovers.add(AlbumArtworkTest.RELEASE);
        worker(2).runPending();assertEquals("FILLED",state(id));assertEquals(2,f.covers);
        assertEquals(original.getLast().id(),f.service.provenance(id).releaseId());
    }

    @Test void albumsLargerThanOneBatchContinueOnTheNextPass() throws Exception {
        for(int n=1;n<=201;n++)f.insert(n,"Artist","Album");
        worker(0).runPending();assertEquals(200,f.db.queryForObject("SELECT COUNT(*) FROM album_members",Integer.class));
        worker(30).runPending();assertEquals(201,f.db.queryForObject("SELECT COUNT(*) FROM album_members",Integer.class));
        assertEquals(2,f.searches);
    }

    @Test void fullAlbumCountDistinguishesASameNamePromoInAnotherGroup() throws Exception {
        String id=f.insert(1,"Artist","Album"); f.insert(2,"Artist","Album");
        var album=f.candidates.getFirst();
        f.candidates=List.of(new AlbumProvider.Candidate(f.candidates.getLast().id(),"Album","Artist","","","Promo",1,"promo-group",""),album);
        worker(0).runPending();
        assertEquals("FILLED",state(id));assertEquals(album.id(),f.service.provenance(id).releaseId());
    }
}
