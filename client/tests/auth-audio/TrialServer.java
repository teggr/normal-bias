import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Disposable loopback TLS/auth gate in front of the real synthetic Spring API.
 * Not production authentication. Never use with personal music or real credentials.
 */
public class TrialServer {
    record Grant(String track, long expires) { }
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private final String session = secret();
    private final Properties config = new Properties();
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private boolean paired;
    private volatile boolean revoked;
    private final Path events;
    private TrialServer(Path directory) throws Exception {
        try (var input = Files.newInputStream(directory.resolve("server.properties"))) { config.load(input); }
        events = directory.resolve("events.jsonl");
        KeyStore keys = KeyStore.getInstance("PKCS12");
        char[] password = config.getProperty("password").toCharArray();
        try (var input = Files.newInputStream(directory.resolve("tls.p12"))) { keys.load(input, password); }
        var km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        km.init(keys, password);
        var tls = SSLContext.getInstance("TLS"); tls.init(km.getKeyManagers(), null, null);
        var https = HttpsServer.create(new InetSocketAddress("127.0.0.1", 18443), 0);
        https.setHttpsConfigurator(new HttpsConfigurator(tls));
        https.createContext("/", this::handle);
        https.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); https.start();
        var admin = HttpServer.create(new InetSocketAddress("127.0.0.1", 18445), 0);
        admin.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/events") && exchange.getRequestMethod().equals("GET")) {
                send(exchange, 200, Files.exists(events) ? Files.readString(events) : "");
            } else if (path.equals("/revoke") && exchange.getRequestMethod().equals("POST")) {
                revoked = true; event("revoke", "session", 200, ""); send(exchange, 200, "Revoked");
            } else send(exchange, 404, "");
        });
        admin.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); admin.start();
        event("ready", "tls", 200, "");
        System.out.println("Disposable HTTPS audio gate on 127.0.0.1:18443; controls on 127.0.0.1:18445. Secrets redacted.");
    }
    private static String secret() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private boolean authenticated(HttpExchange x) { return !revoked && ("Bearer " + session).equals(x.getRequestHeaders().getFirst("Authorization")); }
    private void handle(HttpExchange x) throws IOException {
        String path = x.getRequestURI().getPath(), method = x.getRequestMethod();
        try {
            if (path.equals("/trial/session") && method.equals("POST")) {
                synchronized (this) {
                    if (paired || !config.getProperty("bootstrap").equals(x.getRequestHeaders().getFirst("X-Trial-Pairing"))) { send(x, 401, "{}"); return; }
                    paired = true;
                }
                send(x, 200, "{\"token\":\"" + session + "\",\"_links\":{\"grant\":{\"href\":\"/trial/grants\",\"method\":\"POST\"}}}");
                event("pair", path, 200, ""); return;
            }
            if (path.endsWith("/audio") && path.matches("/api/tracks/[a-f0-9]{64}/audio")) {
                String id = path.split("/")[3];
                String query = x.getRequestURI().getRawQuery();
                Grant grant = query != null && query.startsWith("grant=") ? grants.get(hash(query.substring(6))) : null;
                if (revoked || grant == null || !grant.track.equals(id) || grant.expires <= System.currentTimeMillis()) {
                    event("audio-denied", path, 401, x.getRequestHeaders().getFirst("Range")); send(x, 401, "{}"); return;
                }
                if (!Set.of("GET", "HEAD").contains(method)) { send(x, 405, "{}"); return; }
                proxy(x, path, true); return;
            }
            if (!authenticated(x)) { event("denied", path, 401, ""); send(x, 401, "{}"); return; }
            if (path.equals("/trial/grants") && method.equals("POST")) {
                String id = new String(x.getRequestBody().readNBytes(128), StandardCharsets.UTF_8);
                if (!id.matches("[a-f0-9]{64}")) { send(x, 400, "{}"); return; }
                long lifetime = Long.parseLong(config.getProperty("grant-ms", "120000"));
                String token = secret(); grants.put(hash(token), new Grant(id, System.currentTimeMillis() + lifetime));
                send(x, 200, "{\"_links\":{\"audio\":{\"href\":\"/api/tracks/" + id + "/audio?grant=" + token + "\",\"method\":\"GET\"}}}");
                event("grant", id, 200, ""); return;
            }
            if (path.equals("/trial/event") && method.equals("POST")) {
                // Probe telemetry contains state only, never URLs, credentials or personal data.
                String value = new String(x.getRequestBody().readNBytes(4096), StandardCharsets.UTF_8);
                if (value.contains("Bearer") || value.contains("grant=") || value.contains(session)) { send(x, 400, "{}"); return; }
                synchronized (this) { Files.writeString(events, value + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
                send(x, 200, "{}"); return;
            }
            if (path.startsWith("/api/") && Set.of("GET", "HEAD").contains(method)) { proxy(x, x.getRequestURI().toString(), false); return; }
            send(x, 404, "{}");
        } catch (Exception failure) {
            event("transport-error", path, 502, failure.getClass().getSimpleName());
            try { send(x, 502, "{}"); } catch (Exception ignored) { x.close(); }
        }
    }
    private void proxy(HttpExchange x, String path, boolean audio) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:18444" + path)).method(x.getRequestMethod(), HttpRequest.BodyPublishers.noBody());
        for (String name : List.of("Range", "If-Range", "If-None-Match")) {
            String value = x.getRequestHeaders().getFirst(name); if (value != null) builder.header(name, value);
        }
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        for (String name : List.of("Content-Type", "Content-Range", "Accept-Ranges", "ETag")) response.headers().firstValue(name).ifPresent(v -> x.getResponseHeaders().set(name, v));
        x.getResponseHeaders().set("Cache-Control", "no-store");
        boolean head = x.getRequestMethod().equals("HEAD");
        long size = response.headers().firstValueAsLong("Content-Length").orElse(0);
        if (head) x.getResponseHeaders().set("Content-Length", Long.toString(size));
        x.sendResponseHeaders(response.statusCode(), head ? -1 : size);
        if (audio) event("audio", x.getRequestURI().getPath(), response.statusCode(), x.getRequestHeaders().getFirst("Range"));
        try (var input = response.body(); var output = x.getResponseBody()) {
            if (!head) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count); output.flush();
                    // Force later seeks to require additional authenticated ranges.
                    if (audio) Thread.sleep(100);
                }
            }
        } catch (IOException disconnected) { /* Media players cancel ranges normally. */ }
    }
    private synchronized void event(String kind, String resource, int code, String detail) throws IOException {
        Files.writeString(events, "{\"at\":\"" + Instant.now() + "\",\"kind\":\"" + kind + "\",\"resource\":\"" + resource + "\",\"code\":" + code + ",\"detail\":\"" + (detail == null ? "" : detail.replace("\\", "").replace("\"", "")) + "\"}\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private static void send(HttpExchange x, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json"); x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(code, bytes.length); try (var output = x.getResponseBody()) { output.write(bytes); }
    }
    public static void main(String[] args) throws Exception { new TrialServer(Path.of(args[0])); }
}
