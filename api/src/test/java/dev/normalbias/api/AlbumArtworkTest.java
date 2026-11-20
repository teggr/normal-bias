package dev.normalbias.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.flywaydb.core.Flyway;
import java.io.*;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlbumArtworkTest {
    @TempDir Path root;
    JdbcTemplate db;
    TrackStore tracks;
    StorageDirectory storage;
    AlbumArtwork service;
    TransactionTemplate tx;
    byte[] image;
    static final String RELEASE = "11111111-1111-1111-1111-111111111111";
    List<AlbumProvider.Candidate> candidates = List.of(
            new AlbumProvider.Candidate(RELEASE, "Album", "Artist", "1994", "GB", "Original", 2, "group", "https://musicbrainz.org/release/"+RELEASE),
            new AlbumProvider.Candidate("22222222-2222-2222-2222-222222222222", "Album", "Artist", "2014", "GB", "Deluxe", 40, "group", "https://musicbrainz.org/release/other"));
    boolean offline;
    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).load().migrate();
        db = new JdbcTemplate(source); tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        storage = new StorageDirectory(root, true);
        tracks = new TrackStore(db, new StorageOperations(db, new DataSourceTransactionManager(source), storage), 268435456);
        var output = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(80, 80, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", output);
        image = output.toByteArray(); service = make();
    }
    AlbumArtwork make() throws IOException {
        return new AlbumArtwork(tracks, db, tx, new AlbumProvider() {
            public List<Candidate> search(String artist, String album) throws IOException { if (offline) throw new IOException("503"); return candidates; }
            public byte[] cover(String id) { return image; }
        }, storage);
    }
    @AfterEach void close() throws Exception { service.stop(); storage.close(); }
    String insert(int n, String artist, String album) throws Exception {
        String id = String.format("%064x", n);
        byte[] audio = getClass().getResourceAsStream("/tone.mp3").readAllBytes();
        Files.write(root.resolve("music/" + id + ".mp3"), audio);
        db.update("INSERT INTO tracks(id,filename,title,artist,album,duration_ms,size_bytes,created_at) VALUES(?,?,?,?,?,?,?,?)",
                id, "fixture.mp3", "Track " + n, artist, album, 1000, audio.length, OffsetDateTime.now());
        return id;
    }
    AlbumArtwork.Status lookup(List<String> ids) throws Exception {
        var start = service.start(new AlbumArtwork.Start(ids));
        for (int i=0; i<200 && service.status(start.id()).state().equals("LOOKING_UP"); i++) Thread.sleep(10);
        return service.status(start.id());
    }
    AlbumArtwork.Result accept(AlbumArtwork.Status job) throws Exception {
        assertNotNull(service.preview(job.id(), RELEASE));
        return service.accept(job.id(), new AlbumArtwork.Accept(RELEASE, job.revision()));
    }
    @Test void explicitCoverSurvivesRestartAndFileRecoveryWithoutChangingAudioOrMetadata() throws Exception {
        String a = insert(1,"Artist","Album"), b = insert(2,"Artist","Album");
        byte[] original = Files.readAllBytes(root.resolve("music/"+a+".mp3")); Track before = tracks.get(a);
        var job = lookup(List.of(a,b)); assertEquals("READY",job.state());
        assertEquals(2,service.candidates(job.id(),0,1).total()); assertEquals(1,service.candidates(job.id(),1,1).items().size());
        var result = accept(job); assertArrayEquals(image,service.get(a)); assertArrayEquals(image,service.get(b));
        assertEquals(before.title(),tracks.get(a).title()); assertEquals(before.id(),tracks.get(a).id());
        assertArrayEquals(original,Files.readAllBytes(tracks.path(before))); assertEquals(result.revision(),tracks.get(a).artworkRevision());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM album_selections WHERE provider='MusicBrainz / Cover Art Archive'",Integer.class));
        service.stop(); Files.delete(root.resolve("artwork/"+AlbumArtwork.hash(image)+".png")); service=make();
        assertArrayEquals(image,service.get(a)); tracks.delete(a); assertArrayEquals(image,service.get(b)); tracks.delete(b);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM artwork_assets",Integer.class)); // retained provenance; safe shared deletion
    }
    @Test void staleEditsEvenIfRevertedAndConcurrentAcceptanceAreRejected() throws Exception {
        String id=insert(1,"Artist","Album"); var job=lookup(List.of(id)); service.preview(job.id(),RELEASE);
        tracks.edit(id,new ObjectMapper().readTree("{\"album\":\"Other\"}"));
        tracks.edit(id,new ObjectMapper().readTree("{\"album\":\"Album\"}"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.accept(job.id(),new AlbumArtwork.Accept(RELEASE,job.revision()))).getStatusCode().value());
        var fresh=lookup(List.of(id)); accept(fresh);
        assertThrows(ResponseStatusException.class,()->accept(fresh));
    }
    @Test void deletionAndFailedPublicationLeavePriorCoverValid() throws Exception {
        String id=insert(1,"Artist","Album"); var job=lookup(List.of(id)); accept(job);
        var fresh=lookup(List.of(id)); service.preview(fresh.id(),RELEASE);
        tracks.delete(id);
        assertThrows(ResponseStatusException.class,()->service.accept(fresh.id(),new AlbumArtwork.Accept(RELEASE,fresh.revision())));
        String b=insert(2,"Artist","Album"); accept(lookup(List.of(b)));
        byte[] prior=service.get(b); image=Arrays.copyOf(image,image.length+1); // different asset key; publication failure
        var second=lookup(List.of(b)); service.preview(second.id(),RELEASE);
        Files.createDirectory(root.resolve("artwork/"+AlbumArtwork.hash(image)+".png"));
        assertThrows(IOException.class,()->service.accept(second.id(),new AlbumArtwork.Accept(RELEASE,second.revision())));
        assertArrayEquals(prior,service.get(b));
    }
    @Test void missingMetadataCompilationsAndDifferentAlbumsAreNotSilentlyMerged() throws Exception {
        String a=insert(1,"Artist","Album"); insert(2,"Other artist","Album"); insert(3,"Artist","Other");
        String missing=insert(4,null,null);
        assertEquals(1,service.suggestions(a,0,50).total()); assertEquals(1,service.suggestions(missing,0,50).total());
        assertThrows(ResponseStatusException.class,()->service.start(new AlbumArtwork.Start(List.of(a,missing))));
        assertThrows(ResponseStatusException.class,()->service.start(new AlbumArtwork.Start(List.of(missing))));
        assertThrows(ResponseStatusException.class,()->service.suggestions(a,0,201));
    }
    @Test void noMatchOfflineAndMissingPreviewNeverAcceptOrChangeExistingArt() throws Exception {
        String id=insert(1,"Artist","Album");
        var job=lookup(List.of(id)); assertThrows(ResponseStatusException.class,()->service.accept(job.id(),new AlbumArtwork.Accept(RELEASE,job.revision())));
        candidates=List.of(); assertEquals("NO_MATCH",lookup(List.of(id)).state());
        offline=true; assertEquals("FAILED",lookup(List.of(id)).state()); assertNull(service.get(id));
    }
    @Test void rejectsUntrustedRedirectDestinationsAndMalformedImages() throws Exception {
        for(String url:List.of("http://archive.org/x","https://archive.org.evil.test/x","https://127.0.0.1/x","https://user@archive.org/x","https://archive.org:8443/x"))
            assertThrows(IOException.class,()->MusicBrainzProvider.validate(java.net.URI.create(url),true));
        MusicBrainzProvider.validate(java.net.URI.create("https://ia800123.us.archive.org/x"),true);
        assertNull(EmbeddedArtwork.normalize("not an image".getBytes()));
    }
}
