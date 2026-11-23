package dev.normalbias.api;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.normalbias.api.AuthPolicy.*;

class AuthServiceTest {
    static final String PASSWORD = "Synthetic test password 2026";
    static final String TRACK = "a".repeat(64), OTHER = "b".repeat(64);
    @TempDir Path directory;
    static final class Time extends Clock {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    static final class Fixture implements AutoCloseable {
        final StorageDirectory storage; final JdbcTemplate db; final AuthService auth; final Time time;
        Fixture(Path path, Time time) throws Exception {
            this.time = time; storage = new StorageDirectory(path, true);
            var source = new DriverManagerDataSource("jdbc:h2:file:" + path.resolve("catalogue"), "sa", "");
            SchemaMigrations.migrate(Flyway.configure().dataSource(source).locations("classpath:db/migration").load());
            db = new JdbcTemplate(source);
            auth = new AuthService(db, new AuthSecurity("required", "127.0.0.1").authPasswords(), time, new DataSourceTransactionManager(source));
        }
        void owner() { auth.bootstrap("Owner", PASSWORD); }
        AuthService.Issued login(String name) { return auth.login(name, PASSWORD, "BEARER", "synthetic-address"); }
        String listener() { var invite = auth.invitation(); auth.accept(invite.token(), "Listener", PASSWORD); return invite.token(); }
        void track() { db.update("INSERT INTO tracks (id, filename, title, duration_ms, size_bytes, created_at) VALUES (?, 'synthetic.mp3', 'Synthetic', 1000, 1, CURRENT_TIMESTAMP)", TRACK); }
        @Override public void close() throws Exception { db.execute("SHUTDOWN"); storage.close(); }
    }
    static void status(int code, org.junit.jupiter.api.function.Executable operation) {
        assertEquals(code, assertThrows(ResponseStatusException.class, operation).getStatusCode().value());
    }
    @Test void bootstrapAndInvitationsNeverStorePasswordsOrRawSecrets() throws Exception {
        try (var f = new Fixture(directory, new Time())) {
            f.owner(); status(409, () -> f.auth.bootstrap("second", PASSWORD));
            String invite = f.listener(); status(404, () -> f.auth.inspect(invite, "INVITE"));
            status(404, () -> f.auth.accept(invite, "different", PASSWORD));
            var owner = f.login("OWNER"); var listener = f.login("listener");
            assertTrue(allows(owner.identity(), Action.MANAGE)); assertFalse(allows(listener.identity(), Action.UPLOAD));
            assertTrue(allows(listener.identity(), Action.LISTEN));
            String stored = f.db.queryForObject("SELECT password_hash FROM auth_accounts WHERE username='owner'", String.class);
            assertNotEquals(PASSWORD, stored); assertTrue(stored.startsWith("{pbkdf2-sha256-v1}"));
            assertEquals(0, f.db.queryForObject("SELECT COUNT(*) FROM auth_credentials WHERE token_hash=?", Long.class, owner.token()));
            status(400, () -> f.auth.bootstrap("invalid name", PASSWORD));
        }
    }
    @Test void sessionsConsumptionAndRevocationSurviveDatabaseRestart() throws Exception {
        var time = new Time(); AuthService.Issued session; String invite, grant;
        try (var f = new Fixture(directory, time)) {
            f.owner(); invite = f.listener(); f.track(); session = f.login("listener");
            grant = f.auth.grant(session.identity(), TRACK).href().split("grant=")[1];
        }
        try (var f = new Fixture(directory, time)) {
            assertTrue(f.auth.authenticate(session.token(), "BEARER").isPresent());
            assertTrue(f.auth.authenticateGrant(grant, TRACK).isPresent());
            status(404, () -> f.auth.inspect(invite, "INVITE")); f.auth.logout(session.identity());
        }
        try (var f = new Fixture(directory, time)) {
            assertTrue(f.auth.authenticate(session.token(), "BEARER").isEmpty());
            assertTrue(f.auth.authenticateGrant(grant, TRACK).isEmpty());
        }
    }
    @Test void idleAndAbsoluteLimitsUseServerClockAndActivityCannotExtendTheYear() throws Exception {
        var time = new Time();
        try (var f = new Fixture(directory, time)) {
            f.owner(); var idle = f.login("owner");
            time.advance(Duration.ofDays(90)); assertTrue(f.auth.authenticate(idle.token(), "BEARER").isEmpty());
            var active = f.login("owner"); long absolute = active.identity().absoluteExpires();
            for (int i = 0; i < 12; i++) {
                time.advance(Duration.ofDays(30)); var current = f.auth.authenticate(active.token(), "BEARER").orElseThrow();
                assertEquals(absolute, current.absoluteExpires()); assertTrue(current.idleExpires() <= absolute);
            }
            time.advance(Duration.ofDays(5)); assertTrue(f.auth.authenticate(active.token(), "BEARER").isEmpty());
        }
    }
    @Test void grantsAreTrackScopedRepeatedExpiringAndBoundToParentValidity() throws Exception {
        var time = new Time();
        try (var f = new Fixture(directory, time)) {
            f.owner(); f.track(); var session = f.login("owner");
            String grant = f.auth.grant(session.identity(), TRACK).href().split("grant=")[1];
            assertTrue(f.auth.authenticateGrant(grant, TRACK).isPresent()); assertTrue(f.auth.authenticateGrant(grant, TRACK).isPresent());
            assertTrue(f.auth.authenticateGrant(grant, OTHER).isEmpty()); assertTrue(f.auth.authenticate(grant, "BEARER").isEmpty());
            assertTrue(f.auth.authenticate(session.token(), "COOKIE").isEmpty());
            time.advance(Duration.ofMinutes(2)); assertTrue(f.auth.authenticateGrant(grant, TRACK).isEmpty());
            grant = f.auth.grant(session.identity(), TRACK).href().split("grant=")[1]; f.auth.revoke(session.identity().credentialId());
            assertTrue(f.auth.authenticateGrant(grant, TRACK).isEmpty()); status(401, () -> f.auth.grant(session.identity(), TRACK));
        }
    }
    @Test void uploadTokensCannotListenAndAccountDisableDoesNotReviveOldCredentials() throws Exception {
        try (var f = new Fixture(directory, new Time())) {
            f.owner(); f.listener(); f.track(); var upload = f.auth.uploadCredential("owner");
            assertTrue(allows(f.auth.authenticate(upload.token(), "BEARER").orElseThrow(), Action.UPLOAD));
            assertFalse(allows(upload.identity(), Action.LISTEN)); status(403, () -> f.auth.grant(upload.identity(), TRACK));
            status(403, () -> f.auth.uploadCredential("listener")); status(400, () -> f.auth.enabled("owner", false));
            var listener = f.login("listener"); f.auth.enabled("listener", false);
            assertTrue(f.auth.authenticate(listener.token(), "BEARER").isEmpty()); status(401, () -> f.login("listener"));
            f.auth.enabled("listener", true); assertTrue(f.auth.authenticate(listener.token(), "BEARER").isEmpty()); assertNotNull(f.login("listener"));
        }
    }
    @Test void resetAndPasswordChangesInvalidateSessionsAndOutstandingResetLinks() throws Exception {
        try (var f = new Fixture(directory, new Time())) {
            f.owner(); var session = f.login("owner"); var upload = f.auth.uploadCredential("owner");
            var first = f.auth.reset("owner"); var reset = f.auth.reset("owner"); status(404, () -> f.auth.inspect(first.token(), "RESET"));
            f.auth.completeReset(reset.token(), PASSWORD + " changed"); status(404, () -> f.auth.completeReset(reset.token(), PASSWORD));
            assertTrue(f.auth.authenticate(session.token(), "BEARER").isEmpty()); assertTrue(f.auth.authenticate(upload.token(), "BEARER").isEmpty());
            status(401, () -> f.login("owner"));
            var current = f.auth.login("owner", PASSWORD + " changed", "COOKIE", "synthetic-address");
            var pending = f.auth.reset("owner");
            f.auth.changePassword(current.identity(), PASSWORD + " changed", PASSWORD);
            status(404, () -> f.auth.inspect(pending.token(), "RESET"));
            assertTrue(f.auth.authenticate(current.token(), "COOKIE").isEmpty()); assertNotNull(f.login("owner"));
        }
    }
    @Test void linkExpiryRevocationAndConcurrentConsumptionAreAtomic() throws Exception {
        var time = new Time();
        try (var f = new Fixture(directory, time)) {
            f.owner(); var expired = f.auth.invitation(); time.advance(Duration.ofDays(1)); status(404, () -> f.auth.inspect(expired.token(), "INVITE"));
            var revoked = f.auth.invitation(); f.auth.revokeLink(revoked.id()); status(404, () -> f.auth.inspect(revoked.token(), "INVITE"));
            var invitation = f.auth.invitation(); var barrier = new CountDownLatch(1);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var results = new ArrayList<Future<Boolean>>();
                for (String name : List.of("first", "second")) results.add(executor.submit(() -> {
                    barrier.await();
                    try { f.auth.accept(invitation.token(), name, PASSWORD); return true; }
                    catch (ResponseStatusException unavailable) { assertEquals(404, unavailable.getStatusCode().value()); return false; }
                }));
                barrier.countDown(); int successes = 0; for (var result : results) if (result.get(10, TimeUnit.SECONDS)) successes++;
                assertEquals(1, successes);
            }
            assertEquals(2L, f.auth.accounts(0, 50).total());
        }
    }
    @Test void loginThrottlingPersistsFailuresButExpiresWithoutSleeping() throws Exception {
        var time = new Time();
        try (var f = new Fixture(directory, time)) {
            f.owner(); for (int i = 0; i < 5; i++) status(401, () -> f.auth.login("owner", "wrong", "BEARER", "address"));
            status(429, () -> f.auth.login("owner", PASSWORD, "BEARER", "different-address"));
            time.advance(Duration.ofMinutes(15)); assertNotNull(f.login("owner"));
            assertTrue(f.auth.sessions("owner", 0, 1).items().stream().noneMatch(row -> row.containsKey("TOKEN_HASH")));
        }
    }
    @Test void localCommandsUseOwnerOnlySocketAndTheExistingService() throws Exception {
        try (var f = new Fixture(directory, new Time()); var admin = new AuthAdmin(f.auth, new com.fasterxml.jackson.databind.ObjectMapper(), f.storage, new AuthSecurity("required", "127.0.0.1"))) {
            Path socket = AuthAdmin.socket(directory);
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(socket.getParent()));
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(socket));
            assertEquals(true, AuthAdmin.exchange(directory, Map.of("operation", "bootstrap", "username", "owner", "password", PASSWORD)).get("ok"));
            assertEquals(false, AuthAdmin.exchange(directory, Map.of("operation", "bootstrap", "username", "other", "password", PASSWORD)).get("ok"));
            assertEquals(true, AuthAdmin.exchange(directory, Map.of("operation", "invite")).get("ok"));
            assertThrows(java.io.IOException.class, () -> new StorageDirectory(directory, true));
        }
        assertFalse(Files.exists(AuthAdmin.socket(directory)));
    }
    @Test void insecureDemoConfigurationFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> new AuthSecurity("demo", "0.0.0.0"));
        assertThrows(IllegalArgumentException.class, () -> new AuthSecurity("demo", "192.168.1.205"));
        assertThrows(IllegalArgumentException.class, () -> new AuthSecurity("typo", "127.0.0.1"));
    }
    @Test void inspectionAffordanceTracksBusyStateAndPermissions() throws Exception {
        try (var f = new Fixture(directory, new Time())) {
            f.owner(); f.listener();
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var inspector = new StorageInspector(f.db, null, f.storage) {
                @Override void inspect(boolean full, Observer observer) throws java.io.IOException {
                    entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("Test release timeout"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new java.io.IOException(failure); }
                }
            };
            var inspections = new InspectionService(inspector);
            var security = new AuthSecurity("required", "127.0.0.1");
            var controller = new AuthController(f.auth, security, f.time, security.authCsrf(), inspections);
            var owner = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(f.login("owner").identity(), null, List.of());
            var listener = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(f.login("listener").identity(), null, List.of());
            var request = new org.springframework.mock.web.MockHttpServletRequest(); var response = new org.springframework.mock.web.MockHttpServletResponse();
            try {
                assertTrue(((Map<?,?>) controller.session(owner, request, response).get("_links")).containsKey("start-inspection"));
                var run = inspections.start("full"); assertTrue(entered.await(5, TimeUnit.SECONDS));
                Map<?,?> links = (Map<?,?>) controller.session(owner, request, response).get("_links");
                assertFalse(links.containsKey("start-inspection")); assertEquals("/api/inspections/" + run.id(), ((Link) links.get("inspection")).href());
                assertFalse(((Map<?,?>) controller.session(listener, request, response).get("_links")).containsKey("inspection"));
                status(409, () -> inspections.start("quick"));
            } finally { release.countDown(); inspections.close(); }
        }
    }
}
