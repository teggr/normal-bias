package dev.normalbias.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class InspectionConcurrencyTest {
    @TempDir Path root;
    byte[] tone;
    @BeforeEach void setup() throws IOException {
        try (var input = getClass().getResourceAsStream("/tone.mp3")) { tone = input.readAllBytes(); }
    }

    @Test void hashingDoesNotBlockMutationsAndRevalidatesDeletionOrRepair() throws Exception {
        for (boolean repair : List.of(false, true)) {
            try (var fixture = new Fixture(root.resolve(repair ? "repair" : "delete"))) {
                var uploaded = fixture.store.upload(new ByteArrayInputStream(tone), "tone.mp3", tone.length).track();
                if (repair) {
                    byte[] corrupt = tone.clone(); corrupt[corrupt.length - 1] ^= 1;
                    Files.write(fixture.operations.path(uploaded.id()), corrupt);
                }
                var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
                var inspector = new StorageInspector(fixture.db, fixture.store, fixture.directory) {
                    @Override String hash(InputStream input) throws IOException {
                        entered.countDown();
                        try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test did not release hash reader"); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterruptedIOException(); }
                        return super.hash(input);
                    }
                };
                try (var mutations = Executors.newSingleThreadExecutor()) {
                    var service = new InspectionService(inspector);
                    try {
                        var started = service.start("full");
                        assertTrue(entered.await(5, TimeUnit.SECONDS));
                        var conflict = assertThrows(ResponseStatusException.class, () -> service.start("quick"));
                        assertEquals(409, conflict.getStatusCode().value());
                        // The reader is paused with its file open: mutations must still finish.
                        var mutation = mutations.submit(() -> {
                            assertEquals(1, fixture.store.list(0, 50, "").total());
                            if (!repair) fixture.store.delete(uploaded.id());
                            fixture.store.upload(new ByteArrayInputStream(tone), "tone.mp3", tone.length);
                            return true;
                        });
                        try { assertTrue(mutation.get(2, TimeUnit.SECONDS)); }
                        finally { release.countDown(); }
                        var status = await(service, started.id());
                        assertEquals("COMPLETED", status.state());
                        assertEquals(1, status.scannedTracks());
                        assertEquals(1, status.skippedChangedTracks());
                        assertEquals(repair ? 1 : 0, status.total());
                        assertFalse(status.counts().containsKey("HASH_MISMATCH"));
                        if (repair) assertEquals(1L, status.counts().get("QUARANTINED"));
                        assertArrayEquals(tone, Files.readAllBytes(fixture.operations.path(uploaded.id())));
                    } finally { release.countDown(); service.close(); }
                }
            }
        }
    }

    @Test void activeRequestBodyIsNotReportedAsAbandonedStaging() throws Exception {
        try (var fixture = new Fixture(root)) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var firstRead = new AtomicBoolean(true);
            var input = new ByteArrayInputStream(tone) {
                @Override public synchronized int read(byte[] bytes, int offset, int length) {
                    if (firstRead.getAndSet(false)) {
                        entered.countDown();
                        try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test upload was not released"); }
                        catch (InterruptedException e) { throw new IllegalStateException(e); }
                    }
                    return super.read(bytes, offset, length);
                }
            };
            try (var uploads = Executors.newSingleThreadExecutor()) {
                var future = uploads.submit(() -> fixture.store.upload(input, "tone.mp3", tone.length));
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    assertEquals(1, fixture.store.activeUploadNames().size());
                    var observer = new RecordingObserver();
                    new StorageInspector(fixture.db, fixture.store, fixture.directory).inspect(false, observer);
                    assertTrue(observer.findings.isEmpty(), observer.findings.toString());
                } finally { release.countDown(); }
                assertTrue(future.get(5, TimeUnit.SECONDS).created());
                assertTrue(fixture.store.activeUploadNames().isEmpty());
            }
        }
    }

    @Test void offlineSubcommandIsNoLongerSupported() {
        assertThrows(IllegalArgumentException.class, () -> NormalBiasApplication.main(new String[]{"doctor", root.toString()}));
    }

    private static InspectionService.Status await(InspectionService service, UUID id) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        InspectionService.Status status;
        do { status = service.status(id); if (!status.state().equals("RUNNING")) return status; Thread.sleep(10); }
        while (System.nanoTime() < deadline);
        throw new AssertionError("Inspection did not finish");
    }

    private static final class RecordingObserver implements StorageInspector.Observer {
        final List<StorageInspector.Finding> findings = new ArrayList<>();
        public void snapshot(long tracks) {}
        public void visited(boolean changed) {}
        public void finding(StorageInspector.Finding finding) { findings.add(finding); }
    }

    private static final class Fixture implements AutoCloseable {
        final StorageDirectory directory;
        final SingleConnectionDataSource source;
        final JdbcTemplate db;
        final StorageOperations operations;
        final TrackStore store;
        Fixture(Path root) throws Exception {
            directory = new StorageDirectory(root, true);
            source = new SingleConnectionDataSource("jdbc:h2:file:" + root.resolve("catalogue") + ";WRITE_DELAY=0", "sa", "", true);
            db = new JdbcTemplate(source);
            try {
                SchemaMigrations.migrate(Flyway.configure().dataSource(source).load());
                operations = new StorageOperations(db, new DataSourceTransactionManager(source), directory);
                store = new TrackStore(db, operations, 1048576);
            } catch (Exception e) { close(); throw e; }
        }
        public void close() throws IOException { try { source.destroy(); } finally { directory.close(); } }
    }
}
