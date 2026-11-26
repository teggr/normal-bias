package dev.normalbias.api;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, the current default; account and playback enforcement must remain active. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "normalbias.artwork.background-enabled=false", "normalbias.auth.mode=required",
        "server.ssl.enabled=false"})
class AuthPlainHttpTest {
    static final Path DATA;
    static { try { DATA = Files.createTempDirectory("normalbias-auth-plain-http-"); } catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) { properties.add("normalbias.data-dir", DATA::toString); }
    @LocalServerPort int port;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    HttpClient client;
    @BeforeEach void before() {
        for (String table : List.of("auth_playback_grants", "auth_credentials", "auth_one_time_tokens", "auth_accounts", "auth_login_limits", "tracks")) db.update("DELETE FROM " + table);
        auth.bootstrap("owner", AuthServiceTest.PASSWORD);
        auth.accept(auth.invitation().token(), "listener", AuthServiceTest.PASSWORD);
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    @AfterEach void after() { client.close(); }
    HttpResponse<byte[]> request(String method, String path, byte[] body, Map<String,String> headers) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        headers.forEach(builder::header);
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    JsonNode tree(HttpResponse<byte[]> response) throws Exception { assertEquals(200, response.statusCode()); return json.readTree(response.body()); }
    byte[] loginBody(String user) throws Exception { return json.writeValueAsBytes(Map.of("username", user, "password", AuthServiceTest.PASSWORD)); }
    String bearer(String user) throws Exception { return tree(request("POST", "/api/auth/device-sessions", loginBody(user), Map.of("Content-Type", "application/json"))).path("token").asText(); }
    @Test void httpStillRequiresAccountsRolesAndRevocableTrackGrants() throws Exception {
        var discovery = tree(request("GET", "/api", null, Map.of()));
        assertTrue(discovery.path("authRequired").asBoolean()); assertFalse(discovery.path("httpsRequired").asBoolean());
        assertTrue(discovery.path("_links").has("device-login"));
        assertEquals(401, request("GET", "/api/tracks", null, Map.of()).statusCode());
        String owner = bearer("owner"), listener = bearer("listener");
        byte[] tone; try (var input = getClass().getResourceAsStream("/tone.mp3")) { tone = input.readAllBytes(); }
        assertEquals(403, request("POST", "/api/tracks?filename=test.mp3", tone, Map.of("Authorization", "Bearer " + listener, "Content-Type", "audio/mpeg")).statusCode());
        var upload = request("POST", "/api/tracks?filename=test.mp3", tone, Map.of("Authorization", "Bearer " + owner, "Content-Type", "audio/mpeg"));
        assertEquals(201, upload.statusCode()); String id = json.readTree(upload.body()).path("id").asText();
        String audio = "/api/tracks/" + id + "/audio";
        assertEquals(403, request("GET", audio, null, Map.of("Authorization", "Bearer " + listener)).statusCode());
        String grant = tree(request("POST", "/api/tracks/" + id + "/playback-grants", null, Map.of("Authorization", "Bearer " + listener))).path("_links").path("audio").path("href").asText();
        assertEquals(206, request("GET", grant, null, Map.of("Range", "bytes=0-31")).statusCode());
        assertEquals(200, request("HEAD", grant, null, Map.of()).statusCode());
        assertEquals(200, request("DELETE", "/api/auth/session", null, Map.of("Authorization", "Bearer " + listener)).statusCode());
        assertEquals(401, request("GET", grant, null, Map.of("Range", "bytes=32-63")).statusCode());
        assertEquals(401, request("HEAD", grant, null, Map.of()).statusCode());
    }
    @Test void localUploaderWorksWithDefaultHttpAndStillUsesScopedCredentials() throws Exception {
        Path music = Files.createTempDirectory(DATA, "music-");
        try (var input = getClass().getResourceAsStream("/tone.mp3")) { Files.copy(input, music.resolve("synthetic.mp3")); }
        var credential = auth.uploadCredential("owner");
        var output = new java.io.ByteArrayOutputStream();
        assertEquals(0, new dev.normalbias.cli.UploadCli().run(music, URI.create("http://127.0.0.1:" + port), new java.io.PrintStream(output), credential.token()));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM tracks", Integer.class));
        assertEquals(403, request("GET", "/api/tracks", null, Map.of("Authorization", "Bearer " + credential.token())).statusCode());
    }
    @Test void httpBrowserCookiesPersistAndCsrfIsStillRequired() throws Exception {
        String csrf = tree(request("GET", "/api", null, Map.of())).path("csrfToken").asText();
        assertEquals(403, request("POST", "/api/auth/browser-sessions", loginBody("listener"), Map.of("Content-Type", "application/json")).statusCode());
        var signedIn = request("POST", "/api/auth/browser-sessions", loginBody("listener"), Map.of("Content-Type", "application/json", "X-CSRF-TOKEN", csrf));
        assertEquals(200, signedIn.statusCode());
        String cookie = signedIn.headers().allValues("Set-Cookie").stream().filter(v -> v.startsWith("NormalBias-Session=")).findFirst().orElseThrow();
        assertFalse(cookie.contains("Secure")); assertFalse(cookie.startsWith("__Host-"));
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Lax")); assertTrue(cookie.contains("Max-Age="));
        assertTrue(tree(request("GET", "/api", null, Map.of())).path("authenticated").asBoolean());
        assertEquals(403, request("DELETE", "/api/auth/session", null, Map.of()).statusCode());
        csrf = tree(request("GET", "/api", null, Map.of())).path("csrfToken").asText();
        assertEquals(200, request("DELETE", "/api/auth/session", null, Map.of("X-CSRF-TOKEN", csrf)).statusCode());
        assertFalse(tree(request("GET", "/api", null, Map.of())).path("authenticated").asBoolean());
        assertEquals(401, request("GET", "/api/tracks", null, Map.of()).statusCode());
    }
}
