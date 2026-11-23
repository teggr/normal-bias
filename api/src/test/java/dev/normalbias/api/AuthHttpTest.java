package dev.normalbias.api;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={"normalbias.artwork.background-enabled=false", "normalbias.auth.mode=required"})
class AuthHttpTest {
    static final String PASSWORD = AuthServiceTest.PASSWORD;
    static final Path DATA, TLS;
    static {
        try {
            DATA = Files.createTempDirectory("normalbias-auth-http-"); TLS = DATA.resolve("test.p12");
            var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/keytool").toString(), "-genkeypair", "-alias", "test",
                    "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12", "-keystore", TLS.toString(), "-storepass", "synthetic-test-only",
                    "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-validity", "2", "-noprompt").redirectErrorStream(true).start();
            process.getInputStream().transferTo(OutputStream.nullOutputStream()); if (process.waitFor() != 0) throw new IOException("Test TLS generation failed");
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("normalbias.data-dir", DATA::toString); properties.add("server.ssl.enabled", () -> true);
        properties.add("server.ssl.key-store", TLS::toString); properties.add("server.ssl.key-store-password", () -> "synthetic-test-only");
    }
    @LocalServerPort int port;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    HttpClient client;
    SSLContext trust;
    KeyStore keys;
    byte[] tone;
    @BeforeEach void before() throws Exception {
        for (String table : List.of("auth_playback_grants", "auth_credentials", "auth_one_time_tokens", "auth_accounts", "auth_login_limits", "tracks")) db.update("DELETE FROM " + table);
        auth.bootstrap("owner", PASSWORD); var invitation = auth.invitation(); auth.accept(invitation.token(), "listener", PASSWORD);
        keys = KeyStore.getInstance("PKCS12"); try (var input = Files.newInputStream(TLS)) { keys.load(input, "synthetic-test-only".toCharArray()); }
        var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); managers.init(keys);
        trust = SSLContext.getInstance("TLS"); trust.init(null, managers.getTrustManagers(), null);
        client = HttpClient.newBuilder().sslContext(trust).version(HttpClient.Version.HTTP_1_1).build();
        try (var input = getClass().getResourceAsStream("/tone.mp3")) { tone = input.readAllBytes(); }
    }
    @AfterEach void after() { client.close(); }
    URI uri(String path) { return URI.create("https://localhost:" + port + path); }
    HttpResponse<byte[]> request(String method, String path, byte[] body, String bearer, Map<String,String> headers) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        headers.forEach(builder::header);
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    HttpResponse<byte[]> json(String path, Object value, String bearer) throws Exception {
        return request("POST", path, json.writeValueAsBytes(value), bearer, Map.of("Content-Type", "application/json"));
    }
    String login(String username) throws Exception {
        var response = json("/api/auth/device-sessions", Map.of("username", username, "password", PASSWORD), null);
        assertEquals(200, response.statusCode()); assertTrue(response.headers().firstValue("Set-Cookie").isEmpty());
        return json.readTree(response.body()).path("token").asText();
    }
    String upload(String bearer) throws Exception {
        var response = request("POST", "/api/tracks?filename=synthetic.mp3", tone, bearer, Map.of("Content-Type", "audio/mpeg"));
        assertTrue(Set.of(200,201).contains(response.statusCode())); return json.readTree(response.body()).path("id").asText();
    }
    JsonNode get(String path, String bearer) throws Exception {
        var response = request("GET", path, null, bearer, Map.of()); assertEquals(200, response.statusCode()); return json.readTree(response.body());
    }
    @Test void allLibraryAndManagementRoutesRequireCredentialsBeforeLookingUpResources() throws Exception {
        for (String path : List.of("/api/tracks", "/api/library/albums", "/api/library/artists", "/api/tracks/" + AuthServiceTest.TRACK,
                "/api/tracks/" + AuthServiceTest.TRACK + "/audio", "/api/tracks/" + AuthServiceTest.TRACK + "/artwork",
                "/api/album-artwork/revision", "/api/album-artwork/suggestions/" + AuthServiceTest.TRACK,
                "/api/album-artwork/matches/unknown", "/api/inspections/00000000-0000-0000-0000-000000000000"))
            assertEquals(401, request("GET", path, null, null, Map.of()).statusCode(), path);
        assertEquals(401, request("HEAD", "/api/tracks/" + AuthServiceTest.TRACK + "/audio", null, null, Map.of("Range", "bytes=0-1")).statusCode());
        assertEquals(401, request("GET", "/api/tracks", null, "invalid", Map.of()).statusCode());
        assertEquals(401, request("GET", "/api/tracks?grant=" + "a".repeat(64), null, null, Map.of()).statusCode());
        assertEquals(401, request("GET", "/api/tracks", null, null, Map.of("Cookie", "JSESSIONID=not-authentication")).statusCode());
        assertEquals(413, json("/api/auth/device-sessions", Map.of("password", "x".repeat(17000)), null).statusCode());
    }
    @Test void actionLinksReflectScopeAndCannotReplaceRequestEnforcement() throws Exception {
        String owner = login("owner"), listener = login("listener"), id = upload(owner);
        var ownerTrack = get("/api/tracks/" + id, owner); var listenerTrack = get("/api/tracks/" + id, listener);
        assertTrue(ownerTrack.path("_links").has("edit")); assertTrue(ownerTrack.path("_links").has("delete"));
        assertFalse(listenerTrack.path("_links").has("edit")); assertTrue(listenerTrack.path("_links").has("playback-grant"));
        assertEquals(403, request("PATCH", ownerTrack.path("_links").path("edit").path("href").asText(), "{}".getBytes(), listener, Map.of("Content-Type", "application/json")).statusCode());
        assertEquals(403, request("DELETE", "/api/tracks/" + id, null, listener, Map.of()).statusCode());
        assertEquals(403, request("POST", "/api/tracks", tone, listener, Map.of("Content-Type", "audio/mpeg")).statusCode());
        assertEquals(403, request("POST", "/api/inspections", null, listener, Map.of()).statusCode());
        assertEquals(403, request("GET", "/api/album-artwork/suggestions/" + id, null, listener, Map.of()).statusCode());
        assertTrue(get("/api", owner).path("_links").has("start-inspection")); assertFalse(get("/api", listener).path("_links").has("upload"));
        assertFalse(get("/api", owner).path("_links").has("invite")); // Local administration only.
        var page = get("/api/tracks?limit=1&q=Synthetic", listener);
        assertTrue(page.has("items")); assertTrue(page.has("offset")); assertTrue(page.path("_links").has("self"));
        auth.enabled("listener", false);
        assertEquals(401, request("POST", listenerTrack.path("_links").path("playback-grant").path("href").asText(), null, listener, Map.of()).statusCode());
    }
    @Test void scopedPlaybackUrlsSupportHeadRepeatedRangesAndParentLogoutRevocation() throws Exception {
        String owner = login("owner"), id = upload(owner), listener = login("listener");
        String grantPath = get("/api/tracks/" + id, listener).path("_links").path("playback-grant").path("href").asText();
        var grant = request("POST", grantPath, null, listener, Map.of()); assertEquals(200, grant.statusCode());
        String audio = json.readTree(grant.body()).path("_links").path("audio").path("href").asText();
        assertEquals(403, request("GET", "/api/tracks/" + id + "/audio", null, listener, Map.of()).statusCode());
        for (int i = 0; i < 2; i++) {
            var range = request("GET", audio, null, null, Map.of("Range", "bytes=0-1023"));
            assertEquals(206, range.statusCode()); assertArrayEquals(Arrays.copyOf(tone, 1024), range.body());
            assertEquals("no-store", range.headers().firstValue("Cache-Control").orElseThrow());
        }
        assertEquals(200, request("HEAD", audio, null, null, Map.of()).statusCode());
        assertEquals(401, request("GET", audio.replace(id, AuthServiceTest.OTHER), null, null, Map.of()).statusCode());
        assertEquals(401, request("POST", audio, new byte[0], null, Map.of()).statusCode());
        assertEquals(401, request("GET", "/api/tracks?" + audio.split("\\?")[1], null, null, Map.of()).statusCode());
        assertEquals(200, request("DELETE", "/api/auth/session", null, listener, Map.of()).statusCode());
        assertEquals(401, request("GET", audio, null, null, Map.of("Range", "bytes=1024-2047")).statusCode());
    }
    @Test void browserSessionUsesSecurePersistentCookiesAndCsrfWithoutServletSessionState() throws Exception {
        client.close(); client = HttpClient.newBuilder().sslContext(trust).cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        var entry = request("GET", "/api", null, null, Map.of()); assertEquals(200, entry.statusCode());
        String csrf = json.readTree(entry.body()).path("csrfToken").asText(); assertFalse(csrf.isEmpty());
        assertEquals(403, json("/api/auth/browser-sessions", Map.of("username", "owner", "password", PASSWORD), null).statusCode());
        var signedIn = request("POST", "/api/auth/browser-sessions", json.writeValueAsBytes(Map.of("username", "owner", "password", PASSWORD)), null,
                Map.of("Content-Type", "application/json", "X-CSRF-TOKEN", csrf));
        assertEquals(200, signedIn.statusCode());
        String cookie = signedIn.headers().allValues("Set-Cookie").stream().filter(value -> value.startsWith(AuthSecurity.COOKIE + "=")).findFirst().orElseThrow();
        assertTrue(cookie.contains("Secure")); assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Lax")); assertTrue(cookie.contains("Max-Age="));
        assertFalse(signedIn.headers().allValues("Set-Cookie").toString().contains("JSESSIONID")); assertFalse(json.readTree(signedIn.body()).has("token"));
        var session = get("/api/auth/session", null); assertTrue(session.path("authenticated").asBoolean());
        assertEquals(403, request("DELETE", "/api/auth/session", null, null, Map.of()).statusCode());
        csrf = get("/api", null).path("csrfToken").asText();
        for (String path : List.of("/api/tracks", "/api/album-artwork/revision")) {
            var read = request("GET", path, null, null, Map.of());
            assertEquals(200, read.statusCode());
            assertFalse(read.headers().allValues("Set-Cookie").stream().anyMatch(value -> value.startsWith("__Host-NormalBias-Csrf=")),
                    "Ordinary reads must not rotate or delete the login's CSRF cookie");
        }
        assertEquals(200, request("DELETE", "/api/auth/session", null, null, Map.of("X-CSRF-TOKEN", csrf)).statusCode());
        assertEquals(401, request("GET", "/api/tracks", null, null, Map.of()).statusCode());
    }
    @Test void expiredBrowserCookieDoesNotTrapUsersOutsideTheLoginFlow() throws Exception {
        var response = request("GET", "/api", null, null, Map.of("Cookie", AuthSecurity.COOKIE + "=" + "a".repeat(64)));
        assertEquals(200, response.statusCode()); assertFalse(json.readTree(response.body()).path("authenticated").asBoolean());
        assertTrue(response.headers().allValues("Set-Cookie").stream().anyMatch(value -> value.startsWith(AuthSecurity.COOKIE) && value.contains("Max-Age=0")));
    }
    @Test void invitationAndResetActionsDisappearWhenConsumedAndNeverAutomaticallyLogIn() throws Exception {
        var invitation = auth.invitation();
        var inspected = json("/api/auth/invitations/inspect", Map.of("token", invitation.token()), null);
        String accept = json.readTree(inspected.body()).path("_links").path("accept").path("href").asText(); assertFalse(accept.isEmpty());
        assertEquals(200, json(accept, Map.of("token", invitation.token(), "username", "new-listener", "password", PASSWORD), null).statusCode());
        assertEquals(404, json("/api/auth/invitations/inspect", Map.of("token", invitation.token()), null).statusCode());
        String session = login("new-listener"); var reset = auth.reset("new-listener");
        var inspection = json("/api/auth/resets/inspect", Map.of("token", reset.token()), null);
        String complete = json.readTree(inspection.body()).path("_links").path("complete").path("href").asText();
        assertEquals(200, json(complete, Map.of("token", reset.token(), "password", PASSWORD + " updated"), null).statusCode());
        assertEquals(401, request("GET", "/api/tracks", null, session, Map.of()).statusCode());
        assertEquals(404, json("/api/auth/resets/inspect", Map.of("token", reset.token()), null).statusCode());
    }
    @Test void uploadCredentialsCanOnlyUploadAndLoginErrorsAreGeneric() throws Exception {
        String upload = auth.uploadCredential("owner").token(); assertNotNull(upload(upload));
        var uploaded = request("POST", "/api/tracks", tone, upload, Map.of("Content-Type", "audio/mpeg"));
        assertTrue(json.readTree(uploaded.body()).path("_links").isEmpty(), "Upload receipt must not advertise unavailable read actions");
        assertEquals(403, request("GET", "/api/tracks", null, upload, Map.of()).statusCode());
        assertEquals(403, request("POST", "/api/tracks/" + AuthServiceTest.TRACK + "/playback-grants", null, upload, Map.of()).statusCode());
        var known = json("/api/auth/device-sessions", Map.of("username", "owner", "password", "wrong"), null);
        var unknown = json("/api/auth/device-sessions", Map.of("username", "unknown", "password", "wrong"), null);
        assertEquals(401, known.statusCode()); assertEquals(401, unknown.statusCode());
        assertEquals(json.readTree(known.body()).path("message"), json.readTree(unknown.body()).path("message"));
        assertEquals("Invalid username or password", json.readTree(known.body()).path("message").asText());
    }
    @Test void realUploaderUsesTlsCredentialAndDeduplicates() throws Exception {
        Path music = Files.createDirectory(DATA.resolve("upload-" + UUID.randomUUID())); Files.write(music.resolve("synthetic.mp3"), tone);
        String token = auth.uploadCredential("owner").token(); SSLContext previous = SSLContext.getDefault();
        try {
            SSLContext.setDefault(trust);
            var output = new ByteArrayOutputStream();
            for (int i = 0; i < 2; i++) assertEquals(0, new dev.normalbias.cli.UploadCli().run(music, URI.create("https://127.0.0.1:" + port), new PrintStream(output), token));
            assertTrue(output.toString().contains("Already present")); assertFalse(output.toString().contains(token));
            auth.revoke(auth.authenticate(token, "BEARER").orElseThrow().credentialId());
            assertEquals(1, new dev.normalbias.cli.UploadCli().run(music, URI.create("https://127.0.0.1:" + port), new PrintStream(output), token));
        } finally { SSLContext.setDefault(previous); }
    }

    @Test void uploaderFollowsAdvertisedActionsAndRefusesCrossOriginCredentialForwarding() throws Exception {
        var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); managers.init(keys, "synthetic-test-only".toCharArray());
        var tls = SSLContext.getInstance("TLS"); tls.init(managers.getKeyManagers(), null, null);
        var server = com.sun.net.httpserver.HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(tls));
        var target = new java.util.concurrent.atomic.AtomicReference<>("/different-upload"); var uploads = new java.util.concurrent.atomic.AtomicInteger();
        String token = "c".repeat(64);
        server.createContext("/api", exchange -> { exchange.getResponseHeaders().add("Link", "<" + target.get() + ">; rel=\"upload\"; method=\"POST\""); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext("/different-upload", exchange -> {
            assertEquals("Bearer " + token, exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes(); uploads.incrementAndGet(); exchange.sendResponseHeaders(201, -1); exchange.close();
        });
        server.start(); SSLContext previous = SSLContext.getDefault();
        Path music = Files.createDirectory(DATA.resolve("discovery-" + UUID.randomUUID())); Files.write(music.resolve("synthetic.mp3"), tone);
        try {
            SSLContext.setDefault(trust); URI base = URI.create("https://127.0.0.1:" + server.getAddress().getPort());
            assertEquals(0, new dev.normalbias.cli.UploadCli().run(music, base, new PrintStream(OutputStream.nullOutputStream()), token));
            assertEquals(1, uploads.get()); target.set("https://example.invalid/steal");
            assertEquals(1, new dev.normalbias.cli.UploadCli().run(music, base, new PrintStream(OutputStream.nullOutputStream()), token));
            assertEquals(1, uploads.get());
        } finally { SSLContext.setDefault(previous); server.stop(0); }
    }
}
