package dev.normalbias.api;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"normalbias.artwork.background-enabled=false", "normalbias.auth.mode=demo"})
class InspectionIntegrationTest {
    static final Path DATA;
    static { try { DATA = Files.createTempDirectory("normal-bias-inspections-"); } catch (IOException e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("normalbias.data-dir", () -> DATA.toString());
        registry.add("spring.datasource.url", () -> "jdbc:h2:file:" + DATA.resolve("catalogue") + ";WRITE_DELAY=0");
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    HttpClient client;
    byte[] tone;

    @BeforeEach void setup() throws IOException {
        client = HttpClient.newHttpClient();
        try (var input = getClass().getResourceAsStream("/tone.mp3")) { tone = input.readAllBytes(); }
        db.update("DELETE FROM pending_uploads"); db.update("DELETE FROM pending_deletions"); db.update("DELETE FROM tracks");
        try (var files = Files.walk(DATA.resolve("music"))) {
            for (Path file : files.filter(path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).toList()) Files.delete(file);
        }
    }
    @AfterEach void close() { client.close(); }
    HttpResponse<byte[]> request(String path, String method, byte[] body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30));
        if (body != null) builder.header("Content-Type", "audio/mpeg");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    JsonNode get(String path) throws Exception {
        var response = request(path, "GET", null); assertEquals(200, response.statusCode()); return json.readTree(response.body());
    }
    String start(String mode) throws Exception {
        var response = request("/api/inspections?mode=" + mode, "POST", null);
        assertEquals(202, response.statusCode());
        assertEquals("RUNNING", json.readTree(response.body()).get("state").asText());
        return response.headers().firstValue("Location").orElseThrow();
    }
    JsonNode completed(String path) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            JsonNode status = get(path);
            if (!status.get("state").asText().equals("RUNNING")) {
                assertEquals("COMPLETED", status.get("state").asText(), status.toString()); return status;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Inspection did not complete");
    }
    String upload() throws Exception {
        var response = request("/api/tracks?filename=tone.mp3", "POST", tone); assertEquals(201, response.statusCode());
        return json.readTree(response.body()).get("id").asText();
    }

    @Test void onlineQuickAndFullChecksAreReadOnlyAndReportAudioProblems() throws Exception {
        String id = upload();
        Path file = DATA.resolve("music/" + id + ".mp3");
        String healthy = start("quick"); assertEquals(0, completed(healthy).get("total").asLong());
        byte[] changed = tone.clone(); changed[changed.length - 1] ^= 1; Files.write(file, changed);
        assertEquals(0, completed(start("quick")).get("total").asLong());
        String full = start("full");
        assertEquals(1, completed(full).get("counts").get("HASH_MISMATCH").asLong());
        assertEquals("HASH_MISMATCH", get(full + "/findings").get("items").get(0).get("kind").asText());
        assertArrayEquals(changed, Files.readAllBytes(file));
        Files.writeString(file, "short");
        assertEquals(1, completed(start("quick")).get("counts").get("SIZE_MISMATCH").asLong());
        Files.delete(file);
        assertEquals(1, completed(start("quick")).get("counts").get("MISSING").asLong());
        Files.createSymbolicLink(file, DATA.resolve("catalogue.mv.db"));
        assertEquals(1, completed(start("full")).get("counts").get("UNEXPECTED_FILE").asLong());
        Files.delete(file); Files.write(file, tone);
        Path orphan = DATA.resolve("music/" + "a".repeat(64) + ".mp3"); Files.write(orphan, tone);
        assertEquals(1, completed(start("quick")).get("counts").get("ORPHAN").asLong());
        assertArrayEquals(tone, Files.readAllBytes(orphan));
        assertArrayEquals(tone, request("/api/tracks/" + id + "/audio", "GET", null).body());
        assertEquals(1, get("/api/tracks").get("total").asLong());
    }

    @Test void findingsRemainPaginatedAcrossFourteenThousandSongsWhileApiIsAvailable() throws Exception {
        var rows = new ArrayList<Object[]>();
        for (int i = 0; i < 14000; i++) rows.add(new Object[]{String.format("%064x", i), "tone.mp3", "Song " + i,
                null, null, 1000, tone.length, OffsetDateTime.parse("2026-11-18T00:00:00Z")});
        db.batchUpdate("INSERT INTO tracks (id, filename, title, artist, album, duration_ms, size_bytes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", rows);
        String path = start("quick");
        assertEquals(14000, get("/api/tracks?limit=1").get("total").asLong());
        JsonNode status = completed(path);
        assertEquals(14000, status.get("snapshotTracks").asLong());
        assertEquals(14000, status.get("scannedTracks").asLong());
        assertEquals(14000, status.get("total").asLong());
        assertEquals(14000, status.get("counts").get("MISSING").asLong());
        JsonNode page = get(path + "/findings?offset=50&limit=10");
        assertEquals(14000, page.get("total").asLong()); assertEquals(10, page.get("items").size());
        assertEquals(String.format("%064x", 50), page.get("items").get(0).get("id").asText());
        assertEquals(50, page.get("offset").asInt()); assertEquals(10, page.get("limit").asInt());
        assertTrue(get(path + "/findings?offset=14000").get("items").isEmpty());
        assertEquals(page, get(path + "/findings?offset=50&limit=10"));
    }

    @Test void pendingAndPreservedFilesAreReportedAndInvalidRequestsAreRejected() throws Exception {
        db.update("INSERT INTO pending_deletions VALUES (?)", "b".repeat(64));
        Files.writeString(DATA.resolve("music/staging/unknown.part"), "preserve");
        Files.writeString(DATA.resolve("music/quarantine/evidence.mp3"), "preserve too");
        String path = start("quick"); JsonNode status = completed(path);
        assertEquals(1, status.get("counts").get("PENDING_DELETION").asLong());
        assertEquals(1, status.get("counts").get("STAGING_FILE").asLong());
        assertEquals(1, status.get("counts").get("QUARANTINED").asLong());
        assertEquals(50, get(path + "/findings").get("limit").asInt());
        for (String query : List.of("limit=0", "limit=201", "offset=-1", "offset=nope")) {
            assertEquals(400, request(path + "/findings?" + query, "GET", null).statusCode());
        }
        assertEquals(400, request("/api/inspections?mode=invalid", "POST", null).statusCode());
        assertEquals(400, request("/api/inspections/not-a-uuid", "GET", null).statusCode());
        assertEquals(404, request("/api/inspections/" + UUID.randomUUID(), "GET", null).statusCode());
        assertEquals(404, request("/api/inspections/" + UUID.randomUUID() + "/findings", "GET", null).statusCode());
    }

    @Test void completedRunsAreEvictedAfterFiveAndFailuresAreExplicit() throws Exception {
        String oldest = start("quick"); completed(oldest);
        for (int i = 0; i < 5; i++) completed(start("quick"));
        assertEquals(404, request(oldest, "GET", null).statusCode());
        Path original = DATA.resolve("music"); Path saved = DATA.resolve("saved-music");
        Files.move(original, saved);
        try {
            String failed = start("quick");
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            JsonNode status;
            do { status = get(failed); if (!status.get("state").asText().equals("RUNNING")) break; Thread.sleep(10); }
            while (System.nanoTime() < deadline);
            assertEquals("FAILED", status.get("state").asText()); assertFalse(status.get("error").isNull());
        } finally { Files.move(saved, original); }
    }
}
