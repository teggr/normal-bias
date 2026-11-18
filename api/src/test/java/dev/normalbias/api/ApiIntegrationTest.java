package dev.normalbias.api;

import com.fasterxml.jackson.databind.*;
import dev.normalbias.cli.UploadCli;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiIntegrationTest {
    static final Path DATA;
    static {
        try { DATA = Files.createTempDirectory("normal-bias-test-"); }
        catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("normalbias.data-dir", () -> DATA.toString());
        r.add("normalbias.max-upload-bytes", () -> "1048576");
        r.add("spring.datasource.url", () -> "jdbc:h2:file:" + DATA.resolve("catalogue"));
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @TempDir Path directory;
    HttpClient client;
    byte[] tone;

    @BeforeEach void setup() throws IOException {
        client = HttpClient.newHttpClient();
        try (var in = getClass().getResourceAsStream("/tone.mp3")) { tone = in.readAllBytes(); }
        db.update("DELETE FROM tracks");
    }
    @AfterEach void close() { client.close(); }

    URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
    HttpResponse<byte[]> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
    HttpResponse<byte[]> upload(byte[] bytes, String type) throws Exception {
        return send(HttpRequest.newBuilder(uri("/api/tracks?filename=tone.mp3"))
                .header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build());
    }
    String uploadedId() throws Exception {
        var response = upload(tone, "audio/mpeg");
        assertEquals(201, response.statusCode());
        return json.readTree(response.body()).get("id").asText();
    }
    HttpResponse<byte[]> get(String path) throws Exception { return send(HttpRequest.newBuilder(uri(path)).GET().build()); }
    long count() { return db.queryForObject("SELECT COUNT(*) FROM tracks", Long.class); }

    @Test void uploadMetadataCatalogueDuplicateAndFullStream() throws Exception {
        var first = upload(tone, "audio/mpeg");
        assertEquals(201, first.statusCode());
        JsonNode track = json.readTree(first.body());
        assertEquals("Synthetic tone", track.get("title").asText());
        assertEquals("Normal Bias tests", track.get("artist").asText());
        assertEquals("Fixtures", track.get("album").asText());
        assertTrue(track.get("durationMs").asLong() >= 1000);
        assertEquals(tone.length, track.get("sizeBytes").asLong());
        String path = first.headers().firstValue("Location").orElseThrow();
        assertEquals(track, json.readTree(get(path).body()));
        assertEquals(1, json.readTree(get("/api/tracks").body()).size());
        assertEquals(0, json.readTree(get("/api/tracks?offset=1&limit=1").body()).size());
        var duplicate = upload(tone, "audio/mpeg");
        assertEquals(200, duplicate.statusCode());
        assertEquals(track, json.readTree(duplicate.body()));
        assertEquals(1, count());
        var full = get(path + "/audio");
        assertEquals(200, full.statusCode());
        assertArrayEquals(tone, full.body());
        assertEquals("audio/mpeg", full.headers().firstValue("Content-Type").orElseThrow());
        var head = send(HttpRequest.newBuilder(uri(path + "/audio")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build());
        assertEquals(200, head.statusCode());
        assertEquals(0, head.body().length);
        assertEquals(tone.length, head.headers().firstValueAsLong("Content-Length").orElseThrow());
    }

    @Test void untaggedMp3FallsBackToFilenameAndIsNotAnExactDuplicate() throws Exception {
        uploadedId();
        // Strip the synthetic fixture's ID3v2 header/tag; retain its MPEG frames.
        assertArrayEquals(new byte[]{'I', 'D', '3'}, Arrays.copyOf(tone, 3));
        int tagSize = 10 + ((tone[6] & 127) << 21) + ((tone[7] & 127) << 14)
                + ((tone[8] & 127) << 7) + (tone[9] & 127);
        var response = upload(Arrays.copyOfRange(tone, tagSize, tone.length), "audio/mpeg");
        assertEquals(201, response.statusCode());
        JsonNode track = json.readTree(response.body());
        assertEquals("tone.mp3", track.get("title").asText());
        assertTrue(track.get("artist").isNull());
        assertEquals(2, count());
    }

    @Test void rangesAndIfRange() throws Exception {
        String path = "/api/tracks/" + uploadedId() + "/audio";
        assertRange(path, "bytes=0-9", 0, 9);
        assertRange(path, "bytes=7-", 7, tone.length - 1);
        assertRange(path, "bytes=-10", tone.length - 10, tone.length - 1);
        assertRange(path, "bytes=3-999999", 3, tone.length - 1);
        assertRange(path, "bytes=-999999", 0, tone.length - 1);
        for (String range : List.of("bytes=999999-", "bytes=5-2", "bytes=-0", "bytes=-", "bytes=abc-def",
                "bytes=0-1,3-4", "items=0-2", "bytes=999999999999999999999999-")) {
            var response = send(HttpRequest.newBuilder(uri(path)).header("Range", range).GET().build());
            assertEquals(416, response.statusCode(), range);
            assertEquals("bytes */" + tone.length, response.headers().firstValue("Content-Range").orElseThrow());
        }
        var mismatch = send(HttpRequest.newBuilder(uri(path)).header("Range", "bytes=0-1")
                .header("If-Range", "\"old\"").GET().build());
        assertEquals(200, mismatch.statusCode());
        assertArrayEquals(tone, mismatch.body());
    }
    void assertRange(String path, String range, int start, int end) throws Exception {
        var response = send(HttpRequest.newBuilder(uri(path)).header("Range", range).GET().build());
        assertEquals(206, response.statusCode());
        assertEquals("bytes " + start + "-" + end + "/" + tone.length,
                response.headers().firstValue("Content-Range").orElseThrow());
        assertArrayEquals(Arrays.copyOfRange(tone, start, end + 1), response.body());
    }

    @Test void invalidUploadsAndPagination() throws Exception {
        assertEquals(400, upload(new byte[0], "audio/mpeg").statusCode());
        assertEquals(422, upload("not an mp3".getBytes(StandardCharsets.UTF_8), "audio/mpeg").statusCode());
        assertEquals(415, upload(tone, "text/plain").statusCode());
        assertEquals(413, upload(new byte[1048577], "audio/mpeg").statusCode());
        var chunked = HttpRequest.newBuilder(uri("/api/tracks")).header("Content-Type", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(new byte[1048577]))).build();
        assertEquals(413, send(chunked).statusCode());
        assertEquals(404, get("/api/tracks/missing").statusCode());
        assertEquals(404, get("/api/tracks/" + "a".repeat(64) + "/audio").statusCode());
        assertEquals(400, get("/api/tracks?limit=201").statusCode());
        assertEquals(400, get("/api/tracks?offset=-1").statusCode());
        assertEquals(0, count());
        try (var paths = Files.list(DATA.resolve("music/staging"))) { assertEquals(0, paths.count()); }
    }

    @Test void filenameCannotChooseStoragePath() throws Exception {
        var response = send(HttpRequest.newBuilder(uri("/api/tracks?filename=..%2F..%2Foutside.mp3"))
                .header("Content-Type", "audio/mpeg").POST(HttpRequest.BodyPublishers.ofByteArray(tone)).build());
        assertEquals(201, response.statusCode());
        assertEquals("outside.mp3", json.readTree(response.body()).get("filename").asText());
        assertFalse(Files.exists(DATA.resolve("outside.mp3")));
    }

    @Test void concurrentDuplicatesHaveOneCatalogueRow() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> upload(tone, "audio/mpeg").statusCode());
            var two = executor.submit(() -> upload(tone, "audio/mpeg").statusCode());
            assertEquals(Set.of(200, 201), Set.of(one.get(), two.get()));
            assertEquals(1, count());
        }
    }

    @Test void cliTraversesDirectorySkipsSymlinksAndRetries() throws Exception {
        Path nested = Files.createDirectories(directory.resolve("nested"));
        Files.write(nested.resolve("tone.MP3"), tone);
        Files.writeString(directory.resolve("ignored.txt"), "not music");
        Files.createSymbolicLink(directory.resolve("link.mp3"), nested.resolve("tone.MP3"));
        Files.createSymbolicLink(nested.resolve("cycle"), directory);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        var output = new PrintStream(bytes);
        assertEquals(0, new UploadCli().run(directory, uri("/"), output));
        assertEquals(1, count());
        assertTrue(bytes.toString().contains("1 stored, 0 duplicates, 0 failed"));
        assertTrue(bytes.toString().contains("handed to HTTP client"));
        assertEquals(0, new UploadCli().run(directory, uri("/"), output));
        assertTrue(bytes.toString().contains("0 stored, 1 duplicates, 0 failed"));
        Files.writeString(directory.resolve("bad.mp3"), "invalid");
        assertEquals(1, new UploadCli().run(directory, uri("/"), output));
    }

    @Test void interruptedHttpUploadLeavesNoTrackAndCleansStaging() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            var output = socket.getOutputStream();
            output.write(("POST /api/tracks HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: audio/mpeg\r\n"
                    + "Content-Length: 100000\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(tone, 0, 1000);
            output.flush();
            // Wait until the server is actually consuming this request before interrupting it.
            awaitStaging(true);
        }
        awaitStaging(false);
        assertEquals(0, count());
        assertEquals(201, upload(tone, "audio/mpeg").statusCode());
    }
    void awaitStaging(boolean expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            try (var paths = Files.list(DATA.resolve("music/staging"))) {
                if ((paths.count() > 0) == expected) return;
            }
            Thread.sleep(20);
        }
        fail("Staging presence did not become " + expected);
    }
}
