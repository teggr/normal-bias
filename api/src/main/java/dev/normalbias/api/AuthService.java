package dev.normalbias.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static dev.normalbias.api.AuthPolicy.*;

/** Durable single-instance identity state. Raw credentials are returned once, never stored. */
@Service
public class AuthService {
    static final Duration IDLE = Duration.ofDays(90), ABSOLUTE = Duration.ofDays(365), GRANT = Duration.ofMinutes(2);
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final String dummyHash;
    record Issued(String token, Identity identity) { }
    record OneTime(String id, String token, String kind, long expiresAt) { }
    record Playback(String href, long expiresAt) { }
    record Page(List<Map<String, Object>> items, int offset, int limit, long total) { }
    public AuthService(JdbcTemplate db, PasswordEncoder passwords, Clock clock, PlatformTransactionManager manager) {
        this.db = db; this.passwords = passwords; this.clock = clock; transaction = new TransactionTemplate(manager);
        dummyHash = passwords.encode(secret());
    }
    // Serialize transitions in the database, including concurrent admin and HTTP requests.
    private <T> T locked(Supplier<T> operation) {
        return transaction.execute(status -> {
            db.queryForObject("SELECT id FROM auth_guard WHERE id=1 FOR UPDATE", Integer.class);
            return operation.get();
        });
    }
    static String secret() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private static boolean tokenShape(String token) { return token != null && token.matches("[a-f0-9]{64}"); }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    private static String username(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]{2,63}")) throw error(HttpStatus.BAD_REQUEST, "Use a username of 3–64 letters, digits, dots, underscores or hyphens");
        return value.toLowerCase(Locale.ROOT);
    }
    private String encode(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 12 || password.length() > 256)
            throw error(HttpStatus.BAD_REQUEST, "Use a password of 12–256 characters");
        return passwords.encode(password);
    }
    void bootstrap(String name, String password) {
        String normalized = username(name), encoded = encode(password);
        locked(() -> {
            if (db.queryForObject("SELECT COUNT(*) FROM auth_accounts WHERE role='OWNER'", Long.class) != 0)
                throw error(HttpStatus.CONFLICT, "Owner already exists");
            insertAccount(normalized, encoded, "OWNER"); return null;
        });
    }
    private String insertAccount(String name, String encoded, String role) {
        if (db.queryForObject("SELECT COUNT(*) FROM auth_accounts WHERE username=?", Long.class, name) != 0)
            throw error(HttpStatus.CONFLICT, "Username unavailable");
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO auth_accounts VALUES (?, ?, ?, ?, TRUE, ?)", id, name, encoded, role, clock.millis());
        return id;
    }
    private Map<String, Object> account(String name) {
        return db.queryForList("SELECT * FROM auth_accounts WHERE username=?", username(name)).stream().findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Account not found"));
    }
    private static String string(Map<String, Object> row, String field) { return (String) row.get(field); }
    Issued login(String name, String password, String kind, String remote) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (normalized.length() > 64 || password == null || password.length() > 256) throw error(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        List<String> limits = List.of(hash("account:" + normalized), hash("address:" + remote));
        locked(() -> { checkLimits(limits); return null; });
        var row = db.queryForList("SELECT * FROM auth_accounts WHERE username=?", normalized).stream().findFirst().orElse(null);
        String expected = row == null ? dummyHash : string(row, "PASSWORD_HASH");
        boolean matches = passwords.matches(password, expected);
        Issued result = locked(() -> {
            checkLimits(limits);
            // Re-read after the slow hash so reset/disable racing login cannot restore access.
            var fresh = db.queryForList("SELECT * FROM auth_accounts WHERE username=?", normalized).stream().findFirst().orElse(null);
            if (!matches || fresh == null || !Boolean.TRUE.equals(fresh.get("ENABLED")) || !expected.equals(fresh.get("PASSWORD_HASH"))) {
                failedLogin(limits); return null;
            }
            db.update("DELETE FROM auth_login_limits WHERE key_hash=?", limits.getFirst());
            return issue(fresh, kind);
        });
        if (result == null) throw error(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        return result;
    }
    private void checkLimits(List<String> limits) {
        long now = clock.millis();
        for (int i = 0; i < limits.size(); i++) {
            var rows = db.queryForList("SELECT * FROM auth_login_limits WHERE key_hash=?", limits.get(i));
            if (!rows.isEmpty() && ((Number) rows.getFirst().get("WINDOW_START")).longValue() > now - LOGIN_WINDOW.toMillis()
                    && ((Number) rows.getFirst().get("FAILURES")).intValue() >= (i == 0 ? 5 : 20))
                throw error(HttpStatus.TOO_MANY_REQUESTS, "Try signing in later");
        }
    }
    private void failedLogin(List<String> limits) {
        long now = clock.millis();
        db.update("DELETE FROM auth_login_limits WHERE window_start<=?", now - LOGIN_WINDOW.toMillis());
        for (String key : limits) {
            db.update("DELETE FROM auth_login_limits WHERE key_hash=? AND window_start<=?", key, now - LOGIN_WINDOW.toMillis());
            if (db.update("UPDATE auth_login_limits SET failures=failures+1 WHERE key_hash=?", key) == 0)
                db.update("INSERT INTO auth_login_limits VALUES (?, ?, 1)", key, now);
        }
    }
    private Issued issue(Map<String, Object> row, String kind) {
        if (!Set.of("COOKIE", "BEARER", "UPLOAD").contains(kind)) throw new IllegalArgumentException("Invalid credential kind");
        String token = secret(), id = UUID.randomUUID().toString(); long now = clock.millis();
        db.update("INSERT INTO auth_credentials VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE)", id, hash(token), row.get("ID"), kind, now, now, now + IDLE.toMillis(), now + ABSOLUTE.toMillis());
        return new Issued(token, new Identity(id, string(row, "ID"), string(row, "USERNAME"), string(row, "ROLE"), kind, now + IDLE.toMillis(), now + ABSOLUTE.toMillis()));
    }
    Optional<Identity> authenticate(String token, String transport) {
        if (!tokenShape(token)) return Optional.empty();
        return locked(() -> {
            var rows = db.queryForList("SELECT c.*, a.username, a.role, a.enabled FROM auth_credentials c JOIN auth_accounts a ON a.id=c.account_id WHERE c.token_hash=?", hash(token));
            if (rows.isEmpty()) return Optional.empty();
            var row = rows.getFirst(); String kind = string(row, "KIND");
            if (!(kind.equals(transport) || transport.equals("BEARER") && kind.equals("UPLOAD")) || !valid(row)) return Optional.empty();
            return Optional.of(touch(row));
        });
    }
    private boolean valid(Map<String, Object> row) {
        long now = clock.millis();
        return Boolean.TRUE.equals(row.get("ENABLED")) && !Boolean.TRUE.equals(row.get("REVOKED"))
                && ((Number) row.get("IDLE_EXPIRES")).longValue() > now && ((Number) row.get("ABSOLUTE_EXPIRES")).longValue() > now;
    }
    private Identity touch(Map<String, Object> row) {
        long absolute = ((Number) row.get("ABSOLUTE_EXPIRES")).longValue(), idle = Math.min(absolute, clock.millis() + IDLE.toMillis());
        db.update("UPDATE auth_credentials SET last_seen=?, idle_expires=? WHERE id=?", clock.millis(), idle, row.get("ID"));
        return new Identity(string(row, "ID"), string(row, "ACCOUNT_ID"), string(row, "USERNAME"), string(row, "ROLE"), string(row, "KIND"), idle, absolute);
    }
    private Map<String, Object> current(Identity identity) {
        var rows = db.queryForList("SELECT c.*, a.username, a.role, a.enabled FROM auth_credentials c JOIN auth_accounts a ON a.id=c.account_id WHERE c.id=?", identity.credentialId());
        if (rows.isEmpty() || !valid(rows.getFirst())) throw error(HttpStatus.UNAUTHORIZED, "Sign in again");
        return rows.getFirst();
    }
    Playback grant(Identity identity, String trackId) {
        return locked(() -> {
            var row = current(identity);
            if (!allows(touch(row), Action.LISTEN)) throw error(HttpStatus.FORBIDDEN, "Action unavailable");
            if (db.queryForObject("SELECT COUNT(*) FROM tracks WHERE id=?", Long.class, trackId) == 0) throw error(HttpStatus.NOT_FOUND, "Track not found");
            db.update("DELETE FROM auth_playback_grants WHERE expires_at<=?", clock.millis());
            String token = secret(); long expires = Math.min(clock.millis() + GRANT.toMillis(), Math.min(((Number) row.get("IDLE_EXPIRES")).longValue(), ((Number) row.get("ABSOLUTE_EXPIRES")).longValue()));
            db.update("INSERT INTO auth_playback_grants VALUES (?, ?, ?, ?)", hash(token), identity.credentialId(), trackId, expires);
            return new Playback("/api/tracks/" + trackId + "/audio?grant=" + token, expires);
        });
    }
    Optional<Identity> authenticateGrant(String token, String trackId) {
        if (!tokenShape(token)) return Optional.empty();
        return locked(() -> {
            var rows = db.queryForList("SELECT c.*, a.username, a.role, a.enabled, g.expires_at FROM auth_playback_grants g JOIN auth_credentials c ON c.id=g.credential_id JOIN auth_accounts a ON a.id=c.account_id WHERE g.token_hash=? AND g.track_id=?", hash(token), trackId);
            if (rows.isEmpty() || !valid(rows.getFirst()) || ((Number) rows.getFirst().get("EXPIRES_AT")).longValue() <= clock.millis()) return Optional.empty();
            Identity parent = touch(rows.getFirst());
            return Optional.of(new Identity(parent.credentialId(), parent.accountId(), parent.username(), parent.role(), "GRANT", parent.idleExpires(), parent.absoluteExpires()));
        });
    }
    void logout(Identity identity) { locked(() -> { db.update("UPDATE auth_credentials SET revoked=TRUE WHERE id=?", identity.credentialId()); return null; }); }
    void changePassword(Identity identity, String oldPassword, String newPassword) {
        String encoded = encode(newPassword);
        var row = db.queryForMap("SELECT password_hash FROM auth_accounts WHERE id=?", identity.accountId());
        String expected = string(row, "PASSWORD_HASH");
        if (oldPassword == null || oldPassword.length() > 256 || !passwords.matches(oldPassword, expected)) throw error(HttpStatus.UNAUTHORIZED, "Invalid current password");
        locked(() -> {
            current(identity);
            if (db.update("UPDATE auth_accounts SET password_hash=? WHERE id=? AND password_hash=?", encoded, identity.accountId(), expected) != 1)
                throw error(HttpStatus.CONFLICT, "Account changed; sign in again");
            revokeAccount(identity.accountId()); return null;
        });
    }
    OneTime invitation() { return locked(() -> issueOneTime("INVITE", null, Duration.ofDays(1))); }
    OneTime reset(String name) {
        return locked(() -> {
            String id = string(account(name), "ID");
            db.update("UPDATE auth_one_time_tokens SET consumed=TRUE WHERE account_id=? AND kind='RESET'", id);
            return issueOneTime("RESET", id, Duration.ofHours(1));
        });
    }
    private OneTime issueOneTime(String kind, String accountId, Duration duration) {
        String token = secret(), id = UUID.randomUUID().toString(); long now = clock.millis(), expires = now + duration.toMillis();
        db.update("DELETE FROM auth_one_time_tokens WHERE expires_at<=?", now);
        db.update("INSERT INTO auth_one_time_tokens VALUES (?, ?, ?, ?, ?, ?, FALSE)", id, hash(token), kind, accountId, now, expires);
        return new OneTime(id, token, kind, expires);
    }
    private Map<String, Object> oneTime(String token, String kind) {
        if (!tokenShape(token)) throw error(HttpStatus.NOT_FOUND, "Link unavailable");
        var rows = db.queryForList("SELECT * FROM auth_one_time_tokens WHERE token_hash=? AND kind=? AND consumed=FALSE AND expires_at>?", hash(token), kind, clock.millis());
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Link unavailable");
        return rows.getFirst();
    }
    long inspect(String token, String kind) { return locked(() -> ((Number) oneTime(token, kind).get("EXPIRES_AT")).longValue()); }
    void accept(String token, String name, String password) {
        String normalized = username(name), encoded = encode(password);
        locked(() -> {
            oneTime(token, "INVITE"); insertAccount(normalized, encoded, "LISTENER");
            db.update("UPDATE auth_one_time_tokens SET consumed=TRUE WHERE token_hash=?", hash(token)); return null;
        });
    }
    void completeReset(String token, String password) {
        String encoded = encode(password);
        locked(() -> {
            String id = string(oneTime(token, "RESET"), "ACCOUNT_ID");
            db.update("UPDATE auth_accounts SET password_hash=? WHERE id=?", encoded, id); revokeAccount(id);
            db.update("UPDATE auth_one_time_tokens SET consumed=TRUE WHERE account_id=? AND kind='RESET'", id); return null;
        });
    }
    private void revokeAccount(String id) {
        db.update("UPDATE auth_credentials SET revoked=TRUE WHERE account_id=?", id);
        db.update("UPDATE auth_one_time_tokens SET consumed=TRUE WHERE account_id=?", id);
        String name = db.queryForObject("SELECT username FROM auth_accounts WHERE id=?", String.class, id);
        db.update("DELETE FROM auth_login_limits WHERE key_hash=?", hash("account:" + name));
    }
    void recoverOwner(String name, String password) {
        String encoded = encode(password);
        locked(() -> {
            var row = account(name);
            if (!row.get("ROLE").equals("OWNER")) throw error(HttpStatus.BAD_REQUEST, "Use a reset link for a listener");
            db.update("UPDATE auth_accounts SET password_hash=?, enabled=TRUE WHERE id=?", encoded, row.get("ID"));
            revokeAccount(string(row, "ID")); return null;
        });
    }
    void enabled(String name, boolean enabled) {
        locked(() -> {
            var row = account(name);
            if (row.get("ROLE").equals("OWNER")) throw error(HttpStatus.BAD_REQUEST, "Owner cannot be disabled");
            db.update("UPDATE auth_accounts SET enabled=? WHERE id=?", enabled, row.get("ID"));
            if (!enabled) revokeAccount(string(row, "ID")); return null;
        });
    }
    Issued uploadCredential(String name) {
        return locked(() -> {
            var row = account(name);
            if (!row.get("ROLE").equals("OWNER") || !Boolean.TRUE.equals(row.get("ENABLED"))) throw error(HttpStatus.FORBIDDEN, "Owner required");
            return issue(row, "UPLOAD");
        });
    }
    void revoke(String id) { locked(() -> { if (db.update("UPDATE auth_credentials SET revoked=TRUE WHERE id=?", id) != 1) throw error(HttpStatus.NOT_FOUND, "Session not found"); return null; }); }
    void revokeLink(String id) { locked(() -> { if (db.update("UPDATE auth_one_time_tokens SET consumed=TRUE WHERE id=?", id) != 1) throw error(HttpStatus.NOT_FOUND, "Link not found"); return null; }); }
    Page accounts(int offset, int limit) {
        page(offset, limit);
        return new Page(db.queryForList("SELECT id, username, role, enabled, created_at FROM auth_accounts ORDER BY username LIMIT ? OFFSET ?", limit, offset), offset, limit, db.queryForObject("SELECT COUNT(*) FROM auth_accounts", Long.class));
    }
    Page sessions(String name, int offset, int limit) {
        page(offset, limit); String id = string(account(name), "ID");
        return new Page(db.queryForList("SELECT id, kind, created_at, last_seen, idle_expires, absolute_expires, revoked FROM auth_credentials WHERE account_id=? ORDER BY created_at, id LIMIT ? OFFSET ?", id, limit, offset), offset, limit, db.queryForObject("SELECT COUNT(*) FROM auth_credentials WHERE account_id=?", Long.class, id));
    }
    private static void page(int offset, int limit) { if (offset < 0 || limit < 1 || limit > 200) throw error(HttpStatus.BAD_REQUEST, "Invalid page"); }
}
