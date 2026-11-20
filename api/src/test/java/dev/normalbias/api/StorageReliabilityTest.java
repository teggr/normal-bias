package dev.normalbias.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StorageReliabilityTest {
    @TempDir Path root;
    byte[] tone;

    @BeforeEach void fixture() throws IOException {
        try (var input = getClass().getResourceAsStream("/tone.mp3")) { tone = input.readAllBytes(); }
    }

    Track track() throws IOException {
        Path file = root.resolve("fixture.mp3");
        Files.write(file, tone);
        String id = StorageOperations.sha256(file);
        Files.delete(file);
        return new Track(id, "tone.mp3", "Edited title", "Artist", "Album", 1000, tone.length,
                OffsetDateTime.parse("2026-11-18T00:00:00Z"));
    }

    void insert(Fixture fixture, Track track) {
        fixture.db.update("INSERT INTO tracks (id, filename, title, artist, album, duration_ms, size_bytes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", track.id(), track.filename(), track.title(),
                track.artist(), track.album(), track.durationMs(), track.sizeBytes(), track.createdAt());
    }

    void prepare(Fixture fixture, Track track) throws IOException {
        Files.createDirectories(root.resolve("music/staging"));
        Files.write(root.resolve("music/staging/upload-101.part"), tone);
        fixture.db.update("INSERT INTO pending_uploads VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", track.id(), track.filename(), track.title(),
                track.artist(), track.album(), track.durationMs(), track.sizeBytes(), track.createdAt(), "upload-101.part");
    }

    @Test void freshMigrationsRepeatAndChecksumDriftIsRejected() throws Exception {
        try (var fixture = new Fixture(root, true)) {
            SchemaMigrations.migrate(fixture.flyway());
            assertEquals(3, fixture.db.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE AND \"version\" IS NOT NULL", Integer.class));
            fixture.db.update("UPDATE \"flyway_schema_history\" SET \"checksum\" = 0 WHERE \"version\" = '2'");
            assertThrows(RuntimeException.class, () -> SchemaMigrations.migrate(fixture.flyway()));
        }
    }

    @Test void legacyUpgradePreservesMetadataAndAudio() throws Exception {
        Track track = track();
        try (var fixture = new Fixture(root, false)) {
            try (var input = getClass().getResourceAsStream("/db/migration/V1__catalogue.sql")) {
                fixture.db.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            insert(fixture, track);
            Files.createDirectories(root.resolve("music"));
            Files.write(root.resolve("music/" + track.id() + ".mp3"), tone);
        }
        try (var fixture = new Fixture(root, true)) {
            var operations = fixture.operations();
            assertEquals(track, fixture.db.queryForObject("SELECT * FROM tracks", TrackStore.ROW));
            assertArrayEquals(tone, Files.readAllBytes(operations.path(track.id())));
            assertEquals("3", fixture.flyway().info().current().getVersion().toString());
        }
    }

    @Test void unexpectedLegacySchemaIsNotBaselined() throws Exception {
        try (var fixture = new Fixture(root, false)) {
            fixture.db.execute("CREATE TABLE tracks(id VARCHAR(64) PRIMARY KEY, title VARCHAR(20))");
            assertThrows(IllegalStateException.class, () -> SchemaMigrations.migrate(fixture.flyway()));
            assertEquals(0, fixture.db.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'flyway_schema_history'", Integer.class));
            assertEquals(0, fixture.db.queryForObject("SELECT COUNT(*) FROM tracks", Integer.class));
        }
    }

    @Test void preparedUploadRecoversAfterReopenAndCanReplayAgain() throws Exception {
        Track track = track();
        try (var fixture = new Fixture(root, true)) { prepare(fixture, track); }
        for (int replay = 0; replay < 2; replay++) {
            try (var fixture = new Fixture(root, true)) {
                var operations = fixture.operations();
                assertEquals(track, fixture.db.queryForObject("SELECT * FROM tracks", TrackStore.ROW));
                assertArrayEquals(tone, Files.readAllBytes(operations.path(track.id())));
                assertEquals(0, fixture.db.queryForObject("SELECT COUNT(*) FROM pending_uploads", Integer.class));
            }
        }
    }

    @Test void uploadMovedBeforeCatalogueCommitRecovers() throws Exception {
        Track track = track();
        try (var fixture = new Fixture(root, true)) {
            prepare(fixture, track);
            Files.move(root.resolve("music/staging/upload-101.part"), root.resolve("music/" + track.id() + ".mp3"));
        }
        try (var fixture = new Fixture(root, true)) {
            fixture.operations();
            assertEquals(track, fixture.db.queryForObject("SELECT * FROM tracks", TrackStore.ROW));
            assertEquals(0, fixture.db.queryForObject("SELECT COUNT(*) FROM pending_uploads", Integer.class));
        }
    }

    @Test void corruptPendingUploadFailsWithoutDiscardingEvidence() throws Exception {
        Track track = track();
        try (var fixture = new Fixture(root, true)) {
            prepare(fixture, track);
            Files.writeString(root.resolve("music/staging/upload-101.part"), "damaged");
        }
        try (var fixture = new Fixture(root, true)) {
            assertThrows(IOException.class, fixture::operations);
            assertEquals("damaged", Files.readString(root.resolve("music/staging/upload-101.part")));
            assertEquals(1, fixture.db.queryForObject("SELECT COUNT(*) FROM pending_uploads", Integer.class));
            assertEquals(0, fixture.db.queryForObject("SELECT COUNT(*) FROM tracks", Integer.class));
        }
    }

    @Test void uploadRecoveryQuarantinesConflictingDestination() throws Exception {
        Track track = track();
        try (var fixture = new Fixture(root, true)) {
            prepare(fixture, track);
            Files.writeString(root.resolve("music/" + track.id() + ".mp3"), "conflicting bytes");
        }
        try (var fixture = new Fixture(root, true)) {
            var operations = fixture.operations();
            assertArrayEquals(tone, Files.readAllBytes(operations.path(track.id())));
            try (var files = Files.list(root.resolve("music/quarantine"))) {
                assertEquals("conflicting bytes", Files.readString(files.findFirst().orElseThrow()));
            }
        }
    }

    @Test void committedDeletionRecoversBeforeAndAfterFileRemoval() throws Exception {
        Track track = track();
        for (boolean removed : List.of(false, true)) {
            try (var fixture = new Fixture(root, true)) {
                fixture.operations();
                Files.write(root.resolve("music/" + track.id() + ".mp3"), tone);
                fixture.db.update("INSERT INTO pending_deletions VALUES (?)", track.id());
                if (removed) Files.delete(root.resolve("music/" + track.id() + ".mp3"));
            }
            try (var fixture = new Fixture(root, true)) {
                var operations = fixture.operations();
                assertFalse(Files.exists(operations.path(track.id())));
                assertEquals(0, fixture.db.queryForObject("SELECT COUNT(*) FROM pending_deletions", Integer.class));
                var store = new TrackStore(fixture.db, operations, 1048576);
                assertTrue(store.upload(new ByteArrayInputStream(tone), "tone.mp3", tone.length).created());
                store.delete(track.id());
            }
        }
    }

    @Test void legacyDeletionIsRestoredAndAmbiguousFilesAreQuarantined() throws Exception {
        Track track = track();
        try (var fixture = new Fixture(root, true)) {
            insert(fixture, track);
            Files.createDirectories(root.resolve("music/staging"));
            Files.write(root.resolve("music/staging/" + track.id() + ".part"), tone);
            Files.writeString(root.resolve("music/staging/ambiguous.part"), "preserve");
            Files.writeString(root.resolve("music/staging/upload-102.part"), "aborted request");
        }
        try (var fixture = new Fixture(root, true)) {
            var operations = fixture.operations();
            assertArrayEquals(tone, Files.readAllBytes(operations.path(track.id())));
            try (var files = Files.list(root.resolve("music/quarantine"))) {
                assertEquals("preserve", Files.readString(files.findFirst().orElseThrow()));
            }
            try (var files = Files.list(root.resolve("music/staging"))) { assertEquals(0, files.count()); }
        }
    }

    @Test void conflictingDeletionIntentFailsWithoutRemovingCataloguedAudio() throws Exception {
        Track track = track();
        try (var fixture = new Fixture(root, true)) {
            fixture.operations(); insert(fixture, track);
            Files.write(root.resolve("music/" + track.id() + ".mp3"), tone);
            fixture.db.update("INSERT INTO pending_deletions VALUES (?)", track.id());
        }
        try (var fixture = new Fixture(root, true)) {
            assertThrows(IOException.class, fixture::operations);
            assertArrayEquals(tone, Files.readAllBytes(root.resolve("music/" + track.id() + ".mp3")));
            assertEquals(1, fixture.db.queryForObject("SELECT COUNT(*) FROM tracks", Integer.class));
            assertEquals(1, fixture.db.queryForObject("SELECT COUNT(*) FROM pending_deletions", Integer.class));
        }
    }

    @Test void failedFileDeletionRetainsIntentForRestartRecovery() throws Exception {
        Track track = track();
        try (var fixture = new Fixture(root, true)) {
            var operations = fixture.operations(); insert(fixture, track);
            Path unexpected = Files.createDirectory(operations.path(track.id()));
            Files.writeString(unexpected.resolve("preserve.txt"), "evidence");
            assertThrows(IOException.class, () -> operations.delete(track));
            assertEquals(0, fixture.db.queryForObject("SELECT COUNT(*) FROM tracks", Integer.class));
            assertEquals(1, fixture.db.queryForObject("SELECT COUNT(*) FROM pending_deletions", Integer.class));
            assertEquals("evidence", Files.readString(unexpected.resolve("preserve.txt")));
            // Operator moves the unexpected directory aside, then startup can finish the intent.
            Files.move(unexpected, root.resolve("preserved-directory"));
        }
        try (var fixture = new Fixture(root, true)) {
            fixture.operations();
            assertEquals(0, fixture.db.queryForObject("SELECT COUNT(*) FROM pending_deletions", Integer.class));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final StorageDirectory directory;
        final SingleConnectionDataSource source;
        final JdbcTemplate db;
        Fixture(Path root, boolean migrate) throws Exception {
            directory = new StorageDirectory(root, true);
            source = new SingleConnectionDataSource("jdbc:h2:file:" + root.resolve("catalogue") + ";WRITE_DELAY=0", "sa", "", true);
            db = new JdbcTemplate(source);
            try { if (migrate) SchemaMigrations.migrate(flyway()); }
            catch (Exception e) { close(); throw e; }
        }
        Flyway flyway() { return Flyway.configure().dataSource(source).baselineVersion("1").cleanDisabled(true).load(); }
        StorageOperations operations() throws IOException { return new StorageOperations(db, new DataSourceTransactionManager(source), directory); }
        @Override public void close() throws IOException { try { source.destroy(); } finally { directory.close(); } }
    }
}
