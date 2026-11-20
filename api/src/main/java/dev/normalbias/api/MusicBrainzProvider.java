package dev.normalbias.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

@Component
public class MusicBrainzProvider implements AlbumProvider {
    static final class Unavailable extends IOException {
        private final Instant retryAt;
        Unavailable(String message, Instant retryAt) { super(message); this.retryAt=retryAt; }
        Instant retryAt() { return retryAt; }
    }
    static final String AGENT = "NormalBias/0.1 (https://github.com/teggr/normal-bias; local personal music client)";
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();
    private long nextRequest;
    private long blockedUntil;
    private final URI musicBrainz;
    private final URI archive;
    private final long interval;
    private final long bodyTimeoutMs;
    public MusicBrainzProvider() { this(URI.create("https://musicbrainz.org"), URI.create("https://coverartarchive.org"), 1100); }
    // Package-private transport fixture seam; no configurable production provider origin.
    MusicBrainzProvider(URI musicBrainz, URI archive, long interval) {
        this(musicBrainz, archive, interval, 15000);
    }
    MusicBrainzProvider(URI musicBrainz, URI archive, long interval, long bodyTimeoutMs) {
        this.musicBrainz = musicBrainz; this.archive = archive; this.interval = interval; this.bodyTimeoutMs = bodyTimeoutMs;
    }
    private void destination(URI uri, boolean image) throws IOException {
        if (interval == 0 && (sameOrigin(uri, musicBrainz) || sameOrigin(uri, archive))) return;
        validate(uri, image);
    }
    private static boolean sameOrigin(URI a, URI b) {
        return Objects.equals(a.getScheme(), b.getScheme()) && Objects.equals(a.getHost(), b.getHost())
                && a.getPort() == b.getPort() && a.getRawUserInfo() == null;
    }

    // One gate for all provider traffic, including CAA and redirects. Retries are explicit;
    // throttled providers are never hammered by a background retry loop.
    private synchronized byte[] request(URI uri, boolean image) throws IOException, InterruptedException {
        if (System.currentTimeMillis() < blockedUntil)
            throw new Unavailable("Provider is cooling down; retry later",Instant.ofEpochMilli(blockedUntil));
        for (int redirect = 0; redirect < 5; redirect++) {
            destination(uri, image);
            long delay = nextRequest - System.currentTimeMillis();
            if (delay > 0) Thread.sleep(delay);
            nextRequest = System.currentTimeMillis() + interval;
            var response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", AGENT).header("Accept", image ? "image/jpeg, image/png" : "application/json")
                    .GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                int status = response.statusCode();
                if (status == 429 || status == 503) {
                    long seconds = 60;
                    String retry = response.headers().firstValue("Retry-After").orElse("");
                    try { seconds = Math.max(seconds, Long.parseLong(retry)); }
                    catch (NumberFormatException ignored) {
                        try { seconds = Math.max(seconds, Duration.between(Instant.now(), ZonedDateTime.parse(retry,
                                DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds()); }
                        catch (RuntimeException invalidDate) { }
                    }
                    blockedUntil = System.currentTimeMillis() + Math.min(seconds, 86400) * 1000;
                    throw new Unavailable("Provider HTTP " + status + " at " + uri.getHost() + "; retry later",
                            Instant.ofEpochMilli(blockedUntil));
                }
                if (status == 404 && image) return null;
                if (status >= 300 && status < 400 && image) {
                    uri = uri.resolve(response.headers().firstValue("Location").orElseThrow(() -> new IOException("Missing redirect")));
                    continue;
                }
                if (status != 200) throw new IOException("Provider HTTP " + status);
                int limit = image ? 8 * 1024 * 1024 : 1024 * 1024;
                var read = new FutureTask<byte[]>(() -> input.readNBytes(limit + 1));
                Thread.ofVirtual().start(read);
                byte[] bytes;
                try { bytes = read.get(bodyTimeoutMs, TimeUnit.MILLISECONDS); }
                catch (TimeoutException ex) { read.cancel(true); throw new IOException("Provider body timed out", ex); }
                catch (ExecutionException ex) { throw new IOException("Provider body failed", ex.getCause()); }
                if (bytes.length > limit) throw new IOException("Provider response too large");
                return bytes;
            }
        }
        throw new IOException("Too many provider redirects");
    }
    static void validate(URI uri, boolean image) throws IOException {
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || host == null || !(host.equals("musicbrainz.org") || image && (host.equals("coverartarchive.org")
                || host.equals("archive.org") || host.endsWith(".archive.org")))) throw new IOException("Unexpected provider destination");
    }
    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    @Override public List<Candidate> search(String artist, String album) throws IOException, InterruptedException {
        String query = "release:" + quoted(album) + (artist == null || artist.isBlank() ? "" : " AND artist:" + quoted(artist));
        var root = json.readTree(request(URI.create(musicBrainz + "/ws/2/release/?fmt=json&limit=10&query="
                + URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)), false));
        if (!root.path("releases").isArray()) throw new IOException("Malformed provider releases");
        var result = new ArrayList<Candidate>();
        for (var row : root.path("releases")) {
            if (result.size() >= 10) break;
            String id = row.path("id").asText();
            if (!id.matches("[a-f0-9-]{36}")) continue;
            var artists = new StringBuilder();
            for (var credit : row.path("artist-credit")) artists.append(credit.path("name").asText())
                    .append(credit.path("joinphrase").asText());
            int count = row.path("track-count").asInt(0);
            if (count == 0) for (var medium : row.path("media")) count += medium.path("track-count").asInt();
            result.add(new Candidate(id, row.path("title").asText(), artists.toString(), row.path("date").asText(),
                    row.path("country").asText(), row.path("disambiguation").asText(), count,
                    row.path("release-group").path("id").asText(), "https://musicbrainz.org/release/" + id));
        }
        return result;
    }
    @Override public byte[] cover(String id) throws IOException, InterruptedException {
        if (!id.matches("[a-f0-9-]{36}")) throw new IOException("Invalid release identifier");
        byte[] bytes = request(URI.create(archive + "/release/" + id + "/front-500"), true);
        return bytes == null ? null : EmbeddedArtwork.normalize(bytes);
    }
}
