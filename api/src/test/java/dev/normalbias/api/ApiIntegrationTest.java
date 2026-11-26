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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"normalbias.artwork.background-enabled=false", "normalbias.auth.mode=demo"})
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
    @Test void alphabeticalBrowseAndIndexesShareFilteredOrderingAndLiveKeys() throws Exception {
        String[] names = {"Zulu", "Éclair", "Alpha", "alpha", "The Apples", "42 songs", "東京", "Mellow"};
        for (int i = 0; i < names.length; i++) db.update("INSERT INTO tracks (id, filename, title, artist, album, duration_ms, size_bytes, created_at) VALUES (?, ?, ?, ?, ?, 1000, 1, ?)",
                String.format("%064x", i), "song" + i + ".mp3", names[i], i % 2 == 0 ? "Zulu artist" : "Alpha artist", names[i], java.time.OffsetDateTime.parse("2026-11-18T00:00:00Z"));
        var legacy = json.readTree(get("/api/tracks").body()); assertEquals("Zulu", legacy.path("items").get(0).path("title").asText());
        var page = json.readTree(get("/api/tracks?sort=alphabetical&limit=2").body());
        assertEquals("Alpha", page.path("items").get(0).path("title").asText());
        assertEquals("alpha", page.path("items").get(1).path("title").asText());
        assertEquals("A", page.path("items").get(0).path("initial").asText());
        var next = json.readTree(get(page.path("_links").path("next").path("href").asText()).body());
        assertEquals("Éclair", next.path("items").get(0).path("title").asText());
        var alphabet = json.readTree(get(page.path("_links").path("index").path("href").asText()).body());
        assertEquals(27, alphabet.path("items").size()); assertEquals(8, alphabet.path("total").asInt());
        assertEquals(2, alphabet.path("items").get(0).path("count").asInt());
        var b = alphabet.path("items").get(1);
        assertEquals("Éclair", json.readTree(get(b.path("_links").path("jump").path("href").asText()).body()).path("items").get(0).path("title").asText());
        assertEquals(2, alphabet.path("items").get(26).path("count").asInt());
        var albumPage = json.readTree(get("/api/library/albums?sort=alphabetical").body());
        assertEquals("Alpha", albumPage.path("items").get(0).path("album").asText());
        var filtered = json.readTree(get("/api/library/index?view=albums&artist=Zulu%20artist&q=Alpha").body());
        assertEquals(1, filtered.path("total").asInt()); assertEquals(1, filtered.path("items").get(0).path("count").asInt());
        assertEquals(200, patch(String.format("%064x", 0), "{\"title\":\"Apple\",\"album\":\"A new album\"}").statusCode());
        assertEquals("Apple", json.readTree(get("/api/tracks?sort=alphabetical&q=Apple").body()).path("items").get(0).path("title").asText());
        var empty = json.readTree(get("/api/library/index?view=songs&q=absent").body());
        assertEquals(0, empty.path("total").asInt()); assertTrue(empty.path("items").get(0).path("_links").isEmpty());
        assertEquals(400, get("/api/tracks?sort=unsupported").statusCode());
        assertEquals(400, get("/api/library/index?view=invalid").statusCode());
        assertEquals(400, get("/api/library/index?view=songs&limit=201").statusCode());
    }

    @Test void libraryViewsGroupFilterAndReflectEdits() throws Exception {
        for (int i = 0; i < 5; i++) db.update("INSERT INTO tracks (id, filename, title, artist, album, duration_ms, size_bytes, created_at) VALUES (?, ?, ?, ?, ?, 1000, 1, CURRENT_TIMESTAMP)",
                String.format("%064x", i), "song.mp3", "Song " + i, i == 4 ? null : i < 3 ? "Artist A" : "Artist B", i == 4 ? null : "Shared % album");
        var albums = json.readTree(get("/api/library/albums?limit=1").body());
        assertEquals(3, albums.get("total").asInt());
        assertEquals(1, albums.get("items").size());
        assertEquals(3, json.readTree(get("/api/library/artists").body()).get("total").asInt());
        assertEquals(2, json.readTree(get("/api/library/albums?q=%25").body()).get("total").asInt());
        var artistAlbums = json.readTree(get("/api/library/albums?artist=Artist%20A").body());
        assertEquals(1, artistAlbums.get("total").asInt());
        assertEquals(3, artistAlbums.get("items").get(0).get("trackCount").asInt());
        assertEquals(3, json.readTree(get("/api/tracks?artist=Artist%20A&album=Shared%20%25%20album").body()).get("total").asInt());
        assertEquals(0, json.readTree(get("/api/tracks?artist=Artist%20A&q=Song%204").body()).get("total").asInt());
        assertEquals(1, json.readTree(get("/api/tracks?artist=&album=").body()).get("total").asInt());
        assertEquals(400, get("/api/library/albums?limit=201").statusCode());
        assertEquals(400, get("/api/library/artists?offset=-1").statusCode());
        db.update("UPDATE tracks SET artist='Artist B' WHERE artist='Artist A'");
        assertEquals(2, json.readTree(get("/api/library/albums").body()).get("total").asInt());
        db.update("DELETE FROM tracks WHERE artist='Artist B'");
        assertEquals(1, json.readTree(get("/api/library/artists").body()).get("total").asInt());
    }

    @Test void trackAlbumLinksReturnOneCompleteExactGroupIndependentOfSearch() throws Exception {
        String name = "Shared % & é album";
        for (int i = 0; i < 6; i++) db.update("INSERT INTO tracks (id, filename, title, artist, album, duration_ms, size_bytes, created_at) VALUES (?, 'song.mp3', ?, ?, ?, 1000, 1, CURRENT_TIMESTAMP)",
                String.format("%064x", i), "Song " + i, i >= 4 ? null : i == 2 ? "Artist B" : "Artist A",
                i >= 4 ? null : i == 3 ? "Other album" : name);
        var song = json.readTree(get("/api/tracks?q=Song%200").body()).path("items").get(0);
        String album = song.path("_links").path("album").path("href").asText();
        assertFalse(album.isBlank());
        var groupPage = json.readTree(get(album).body());
        assertEquals(1, groupPage.path("total").asInt());
        var group = groupPage.path("items").get(0);
        assertEquals("Artist A", group.path("artist").asText()); assertEquals(name, group.path("album").asText());
        assertEquals(2, group.path("trackCount").asInt());
        assertEquals(2, json.readTree(get(group.path("_links").path("contents").path("href").asText()).body()).path("total").asInt());
        var unknown = json.readTree(get("/api/tracks/" + String.format("%064x", 4)).body());
        var missing = json.readTree(get(unknown.path("_links").path("album").path("href").asText()).body());
        assertEquals(1, missing.path("total").asInt()); assertEquals(2, missing.path("items").get(0).path("trackCount").asInt());
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

    HttpResponse<byte[]> patch(String id, String body) throws Exception {
        return send(HttpRequest.newBuilder(uri("/api/tracks/" + id)).header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build());
    }

    HttpResponse<byte[]> delete(String id) throws Exception {
        return send(HttpRequest.newBuilder(uri("/api/tracks/" + id)).DELETE().build());
    }

    private byte[] withArtwork(byte[] image, String mime) throws Exception {
        Path input = directory.resolve("input.mp3");
        Files.write(input, tone);
        var mp3 = new com.mpatric.mp3agic.Mp3File(input);
        mp3.getId3v2Tag().setAlbumImage(image, mime);
        Path output = directory.resolve(UUID.randomUUID() + ".mp3");
        mp3.save(output.toString());
        return Files.readAllBytes(output);
    }

    @Test void embeddedArtworkIsDerivedForExistingTracksAndKeepsAudioImmutable() throws Exception {
        for (String format : List.of("png", "jpg")) {
            var image = new java.awt.image.BufferedImage(800, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            graphics.setColor(java.awt.Color.ORANGE);
            graphics.fillRect(0, 0, 800, 400);
            graphics.dispose();
            var output = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, format, output);
            byte[] tagged = withArtwork(output.toByteArray(), "image/" + format);
            var uploaded = upload(tagged, "audio/mpeg");
            assertEquals(201, uploaded.statusCode());
            String id = json.readTree(uploaded.body()).get("id").asText();
            // Artwork is extracted only at GET, so previously imported MP3s work too.
            var cover = get("/api/tracks/" + id + "/artwork");
            assertEquals(200, cover.statusCode());
            assertEquals("image/png", cover.headers().firstValue("Content-Type").orElseThrow());
            var decoded = javax.imageio.ImageIO.read(new ByteArrayInputStream(cover.body()));
            assertEquals(512, decoded.getWidth());
            assertEquals(256, decoded.getHeight());
            String etag = cover.headers().firstValue("ETag").orElseThrow();
            assertEquals(304, send(HttpRequest.newBuilder(uri("/api/tracks/" + id + "/artwork"))
                    .header("If-None-Match", etag).GET().build()).statusCode());
            var head = send(HttpRequest.newBuilder(uri("/api/tracks/" + id + "/artwork"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build());
            assertEquals(200, head.statusCode());
            assertEquals(0, head.body().length);
            assertEquals(200, patch(id, "{\"title\":\"Changed\"}").statusCode());
            assertEquals(200, upload(tagged, "audio/mpeg").statusCode());
            assertArrayEquals(cover.body(), get("/api/tracks/" + id + "/artwork").body());
            assertArrayEquals(tagged, get("/api/tracks/" + id + "/audio").body());
            assertEquals(204, delete(id).statusCode());
            assertEquals(404, get("/api/tracks/" + id + "/artwork").statusCode());
        }
    }

    @Test void absentInvalidAndOversizedArtworkIsOptional() throws Exception {
        String plain = uploadedId();
        assertEquals(404, get("/api/tracks/" + plain + "/artwork").statusCode());
        for (byte[] invalid : List.of("not an image".getBytes(StandardCharsets.UTF_8), new byte[]{(byte)137, 80, 78, 71})) {
            String id = json.readTree(upload(withArtwork(invalid, "image/png"), "audio/mpeg").body()).get("id").asText();
            assertEquals(404, get("/api/tracks/" + id + "/artwork").statusCode());
            assertEquals(200, get("/api/tracks/" + id + "/audio").statusCode());
        }
        var huge = new java.awt.image.BufferedImage(4097, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(huge, "png", output);
        assertNull(EmbeddedArtwork.normalize(output.toByteArray()));
        assertEquals(404, get("/api/tracks/not-an-id/artwork").statusCode());
    }

    @Test void searchFiltersFieldsLiterallyAndPaginates() throws Exception {
        String id = uploadedId();
        for (String term : List.of("SYNTHETIC", "bias", "fixtures", "tone.mp3", "  synthetic  ")) {
            var result = get("/api/tracks?q=" + URLEncoder.encode(term, StandardCharsets.UTF_8));
            assertEquals(200, result.statusCode());
            assertEquals(id, json.readTree(result.body()).get("items").get(0).get("id").asText());
        }
        for (String term : List.of("absent", "%", "_", "' OR 1=1 --")) {
            assertEquals(0, json.readTree(get("/api/tracks?q="
                    + URLEncoder.encode(term, StandardCharsets.UTF_8)).body()).get("items").size());
        }
        assertEquals(0, json.readTree(get("/api/tracks?q=synthetic&offset=1&limit=1").body()).get("items").size());
        assertEquals(400, get("/api/tracks?q=" + "a".repeat(1025)).statusCode());
        assertEquals(1, json.readTree(get("/api/tracks?q=%20%20").body()).get("items").size());
    }

    @Test void metadataEditsPreserveAudioIdentityAndDuplicateUploads() throws Exception {
        String id = uploadedId();
        JsonNode original = json.readTree(get("/api/tracks/" + id).body());
        var response = patch(id, "{\"title\":\"  Edited title  \",\"artist\":null,\"album\":\"  \"}");
        assertEquals(200, response.statusCode());
        JsonNode edited = json.readTree(response.body());
        assertEquals("Edited title", edited.get("title").asText());
        assertTrue(edited.get("artist").isNull());
        assertTrue(edited.get("album").isNull());
        for (String field : List.of("id", "filename", "durationMs", "sizeBytes", "createdAt")) {
            assertEquals(original.get(field), edited.get(field));
        }
        assertEquals(edited, json.readTree(get("/api/tracks/" + id).body()));
        assertEquals("Edited title", db.queryForObject("SELECT title FROM tracks WHERE id = ?", String.class, id));
        assertArrayEquals(tone, get("/api/tracks/" + id + "/audio").body());
        assertEquals(edited, json.readTree(upload(tone, "audio/mpeg").body()));
        assertEquals(0, json.readTree(get("/api/tracks?q=synthetic").body()).get("items").size());
        assertEquals(1, json.readTree(get("/api/tracks?q=edited").body()).get("items").size());
        assertEquals(200, patch(id, "{\"artist\":\"New artist\"}").statusCode());
        JsonNode partial = json.readTree(get("/api/tracks/" + id).body());
        assertEquals("Edited title", partial.get("title").asText());
        assertEquals("New artist", partial.get("artist").asText());
        assertTrue(partial.get("album").isNull());
    }

    @Test void invalidEditsAreRejectedWithoutPartialUpdates() throws Exception {
        String id = uploadedId();
        JsonNode original = json.readTree(get("/api/tracks/" + id).body());
        for (String body : List.of("{}", "[]", "null", "{", "{\"title\":null}", "{\"title\":\"  \"}",
                "{\"artist\":3}", "{\"album\":[]}", "{\"title\":\"changed\",\"id\":\"other\"}",
                "{\"artist\":\"line\\nbreak\"}", "{\"album\":\"" + "a".repeat(1025) + "\"}")) {
            assertEquals(400, patch(id, body).statusCode(), body);
            assertEquals(original, json.readTree(get("/api/tracks/" + id).body()));
        }
        assertEquals(404, patch("missing", "{\"title\":\"x\"}").statusCode());
        assertEquals(404, patch("f".repeat(64), "{\"title\":\"x\"}").statusCode());
    }

    @Test void deletionRemovesCatalogueAndAudioAndAllowsReupload() throws Exception {
        String id = uploadedId();
        Path audio = DATA.resolve("music/" + id + ".mp3");
        assertTrue(Files.exists(audio));
        var response = delete(id);
        assertEquals(204, response.statusCode());
        assertEquals(0, response.body().length);
        assertFalse(Files.exists(audio));
        assertEquals(0, count());
        assertEquals(404, get("/api/tracks/" + id).statusCode());
        assertEquals(404, get("/api/tracks/" + id + "/audio").statusCode());
        assertEquals(404, delete(id).statusCode());
        assertEquals(404, delete("missing").statusCode());
        assertEquals(id, uploadedId());
        assertArrayEquals(tone, get("/api/tracks/" + id + "/audio").body());
        // A missing stored file must not prevent removal of its stale catalogue entry.
        Files.delete(audio);
        assertEquals(204, delete(id).statusCode());
        try (var paths = Files.list(DATA.resolve("music/staging"))) { assertEquals(0, paths.count()); }
    }

    @Test void failedCatalogueDeletionPreservesAudioAndRollsBackIntent() throws Exception {
        String id = uploadedId();
        // The row and deletion intent must commit together before any audio is touched.
        db.execute("CREATE TABLE deletion_guard (track_id VARCHAR(64) REFERENCES tracks(id))");
        try {
            db.update("INSERT INTO deletion_guard VALUES (?)", id);
            assertEquals(500, delete(id).statusCode());
            assertEquals(1, count());
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM pending_deletions", Integer.class));
            assertArrayEquals(tone, get("/api/tracks/" + id + "/audio").body());
            try (var paths = Files.list(DATA.resolve("music/staging"))) { assertEquals(0, paths.count()); }
        } finally {
            db.execute("DROP TABLE deletion_guard");
        }
    }

    @Test void duplicateUploadRepairsMissingAndCorruptAudioWithoutLosingEdits() throws Exception {
        String id = uploadedId();
        assertEquals(200, patch(id, "{\"title\":\"Keep this edit\"}").statusCode());
        Path file = DATA.resolve("music/" + id + ".mp3");
        Files.delete(file);
        var restored = upload(tone, "audio/mpeg");
        assertEquals(200, restored.statusCode());
        assertEquals("Keep this edit", json.readTree(restored.body()).get("title").asText());
        assertArrayEquals(tone, get("/api/tracks/" + id + "/audio").body());
        byte[] corrupt = tone.clone(); corrupt[corrupt.length - 1] ^= 1;
        Files.write(file, corrupt);
        assertEquals(200, upload(tone, "audio/mpeg").statusCode());
        assertArrayEquals(tone, get("/api/tracks/" + id + "/audio").body());
        assertEquals("Keep this edit", json.readTree(get("/api/tracks/" + id).body()).get("title").asText());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM pending_uploads", Integer.class));
    }

    @Test void searchAcrossFourteenThousandTracks() throws Exception {
        // Synthetic catalogue rows only: this measures search, not audio storage or upload throughput.
        var rows = new ArrayList<Object[]>();
        var created = java.time.OffsetDateTime.parse("2026-11-18T00:00:00Z");
        for (int i = 0; i < 14000; i++) {
            rows.add(new Object[]{String.format("%064x", i), "track-" + i + ".mp3", "Song " + i,
                    "Artist " + (i % 140), "Album " + (i % 700), 1000L, 8686L, created});
        }
        db.batchUpdate("INSERT INTO tracks (id, filename, title, artist, album, duration_ms, size_bytes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", rows);
        var albumPage = json.readTree(get("/api/library/albums?offset=699&limit=1").body());
        assertEquals(700, albumPage.get("total").asInt());
        assertEquals(1, albumPage.get("items").size());
        assertEquals(20, albumPage.get("items").get(0).get("trackCount").asInt());
        assertEquals(140, json.readTree(get("/api/library/artists?offset=140").body()).get("total").asInt());
        assertEquals(0, json.readTree(get("/api/library/artists?offset=140").body()).get("items").size());
        long browseStarted = System.nanoTime();
        var alphabet = json.readTree(get("/api/library/index?view=songs").body());
        assertEquals(14000, alphabet.path("total").asInt());
        assertEquals(14000, alphabet.path("items").get(18).path("count").asInt());
        var sorted = json.readTree(get("/api/tracks?sort=alphabetical&offset=13750&limit=200").body());
        assertEquals(200, sorted.path("items").size());
        assertEquals("S", sorted.path("items").get(0).path("initial").asText());
        System.out.printf("14,000-track alphabet index + late sorted batch: %.1f ms%n", (System.nanoTime() - browseStarted) / 1_000_000.0);
        var timings = new ArrayList<Double>();
        for (int repetition = 0; repetition < 10; repetition++) {
            for (String query : List.of("sOnG", "Artist 139", "Album 699", "track-13999.mp3", "absent")) {
                long started = System.nanoTime();
                var response = get("/api/tracks?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=200");
                timings.add((System.nanoTime() - started) / 1_000_000.0);
                assertEquals(200, response.statusCode());
                int expected = switch (query) {
                    case "sOnG" -> 200;
                    case "Artist 139" -> 100;
                    case "Album 699" -> 20;
                    case "track-13999.mp3" -> 1;
                    default -> 0;
                };
                JsonNode page = json.readTree(response.body());
                assertEquals(expected, page.get("items").size());
                assertEquals(query.equals("sOnG") ? 14000 : expected, page.get("total").asLong());
                assertEquals(0, page.get("offset").asInt());
                assertEquals(200, page.get("limit").asInt());
            }
        }
        Collections.sort(timings);
        System.out.printf("14,000-track search, 50 HTTP requests: mean %.1f ms, p95 %.1f ms, max %.1f ms%n",
                timings.stream().mapToDouble(Double::doubleValue).average().orElseThrow(), timings.get(47), timings.get(49));
        JsonNode first = json.readTree(get("/api/tracks?q=song&limit=200").body()).get("items");
        JsonNode next = json.readTree(get("/api/tracks?q=song&offset=200&limit=200").body()).get("items");
        assertEquals(String.format("%064x", 0), first.get(0).get("id").asText());
        assertEquals(String.format("%064x", 200), next.get(0).get("id").asText());
        assertEquals(0, json.readTree(get("/api/tracks?q=song&offset=14000").body()).get("items").size());
        JsonNode all = json.readTree(get("/api/tracks").body());
        assertEquals(14000, all.get("total").asLong());
        assertEquals(50, all.get("limit").asInt());
        assertEquals(50, all.get("items").size());
        JsonNode pastEnd = json.readTree(get("/api/tracks?offset=14000&limit=10").body());
        assertEquals(14000, pastEnd.get("total").asLong());
        assertEquals(14000, pastEnd.get("offset").asInt());
        assertEquals(10, pastEnd.get("limit").asInt());
        assertTrue(pastEnd.get("items").isEmpty());
    }

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
        assertEquals(1, json.readTree(get("/api/tracks").body()).get("items").size());
        assertEquals(0, json.readTree(get("/api/tracks?offset=1&limit=1").body()).get("items").size());
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
        assertEquals(400, get("/api/tracks?limit=0").statusCode());
        assertEquals(400, get("/api/tracks?q=tone&limit=0").statusCode());
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
