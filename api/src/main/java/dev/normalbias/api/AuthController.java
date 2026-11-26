package dev.normalbias.api;

import jakarta.servlet.http.*;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;
import static dev.normalbias.api.AuthPolicy.*;

@RestController
class AuthController {
    private final AuthService service; private final AuthSecurity security; private final Clock clock; private final CookieCsrfTokenRepository csrf; private final InspectionService inspections;
    AuthController(AuthService service, AuthSecurity security, Clock clock, CookieCsrfTokenRepository csrf, InspectionService inspections) {
        this.service = service; this.security = security; this.clock = clock; this.csrf = csrf; this.inspections = inspections;
    }
    record Login(String username, String password) { }
    record Token(String token) { }
    record Accept(String token, String username, String password) { }
    record Reset(String token, String password) { }
    record PasswordChange(String currentPassword, String newPassword) { }
    @GetMapping({"/api", "/api/auth/session"})
    Map<String, Object> session(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        Identity identity = identity(authentication); var result = new LinkedHashMap<String, Object>();
        result.put("authenticated", identity != null); result.put("authRequired", !security.demo()); result.put("httpsRequired", security.requireHttps() && !security.demo());
        var links = new LinkedHashMap<String, Link>(); links.put("self", link(security.demo() ? "/api" : "/api/auth/session", "GET"));
        if (identity == null) {
            if (!security.demo()) {
                links.put("browser-login", link("/api/auth/browser-sessions", "POST"));
                links.put("device-login", link("/api/auth/device-sessions", "POST"));
                links.put("inspect-invitation", link("/api/auth/invitations/inspect", "POST"));
                links.put("inspect-reset", link("/api/auth/resets/inspect", "POST"));
            }
        } else {
            result.put("account", Map.of("username", identity.username(), "role", identity.role()));
            result.put("session", Map.of("id", identity.credentialId(), "kind", identity.kind(), "idleExpiresAt", Instant.ofEpochMilli(identity.idleExpires()), "absoluteExpiresAt", Instant.ofEpochMilli(identity.absoluteExpires())));
            if (allows(identity, Action.SESSION)) {
                links.put("logout", link("/api/auth/session", "DELETE")); links.put("change-password", link("/api/auth/password", "POST"));
            }
        }
        if (security.demo() || allows(identity, Action.LISTEN)) {
            links.put("artwork-revision", link("/api/album-artwork/revision", "GET"));
            links.put("songs", link("/api/tracks", "GET")); links.put("albums", link("/api/library/albums", "GET")); links.put("artists", link("/api/library/artists", "GET"));
            links.put("songs-alphabetical", link("/api/tracks?sort=alphabetical", "GET"));
            links.put("albums-alphabetical", link("/api/library/albums?sort=alphabetical", "GET"));
            links.put("artists-alphabetical", link("/api/library/artists?sort=alphabetical", "GET"));
        }
        if (allows(identity, Action.UPLOAD)) {
            links.put("upload", link("/api/tracks", "POST"));
            // Standard HTTP link discovery also keeps the small JDK-only uploader dependency-free.
            response.addHeader("Link", "</api/tracks>; rel=\"upload\"; method=\"POST\"");
        }
        if (allows(identity, Action.INSPECT)) {
            var active = inspections.active();
            if (active.isPresent()) links.put("inspection", link("/api/inspections/" + active.get().id(), "GET"));
            else links.put("start-inspection", link("/api/inspections", "POST"));
        }
        if (!security.demo() && (identity == null || identity.kind().equals("COOKIE"))) {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) { result.put("csrfToken", token.getToken()); result.put("csrfHeader", token.getHeaderName()); }
        }
        result.put("_links", links); return result;
    }
    @PostMapping(value="/api/auth/browser-sessions", consumes="application/json")
    Map<String, Object> browser(@RequestBody Login login, HttpServletRequest request, HttpServletResponse response) {
        var issued = service.login(login.username(), login.password(), "COOKIE", request.getRemoteAddr());
        cookie(response, issued.token(), issued.identity(), clock.millis(), security);
        // Rotate the anonymous CSRF token at the authentication boundary; fetch /api again.
        csrf.saveToken(null, request, response);
        return Map.of("authenticated", true, "_links", Map.of("session", link("/api/auth/session", "GET")));
    }
    @PostMapping(value="/api/auth/device-sessions", consumes="application/json")
    Map<String, Object> device(@RequestBody Login login, HttpServletRequest request) {
        var issued = service.login(login.username(), login.password(), "BEARER", request.getRemoteAddr());
        return Map.of("token", issued.token(), "tokenType", "Bearer", "idleExpiresAt", Instant.ofEpochMilli(issued.identity().idleExpires()),
                "absoluteExpiresAt", Instant.ofEpochMilli(issued.identity().absoluteExpires()), "_links", Map.of("session", link("/api/auth/session", "GET")));
    }
    @DeleteMapping("/api/auth/session")
    Map<String, Object> logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        service.logout(identity(authentication)); clear(request, response);
        return Map.of("authenticated", false, "_links", Map.of("session", link("/api/auth/session", "GET")));
    }
    @PostMapping(value="/api/auth/password", consumes="application/json")
    Map<String, Object> password(Authentication authentication, @RequestBody PasswordChange change, HttpServletRequest request, HttpServletResponse response) {
        service.changePassword(identity(authentication), change.currentPassword(), change.newPassword()); clear(request, response);
        return Map.of("authenticated", false, "_links", Map.of("session", link("/api/auth/session", "GET")));
    }
    @PostMapping(value="/api/auth/invitations/inspect", consumes="application/json")
    Map<String, Object> inspectInvitation(@RequestBody Token token) {
        return Map.of("expiresAt", Instant.ofEpochMilli(service.inspect(token.token(), "INVITE")), "_links", Map.of("accept", link("/api/auth/invitations/accept", "POST")));
    }
    @PostMapping(value="/api/auth/resets/inspect", consumes="application/json")
    Map<String, Object> inspectReset(@RequestBody Token token) {
        return Map.of("expiresAt", Instant.ofEpochMilli(service.inspect(token.token(), "RESET")), "_links", Map.of("complete", link("/api/auth/resets/complete", "POST")));
    }
    @PostMapping(value="/api/auth/invitations/accept", consumes="application/json")
    Map<String, Object> accept(@RequestBody Accept accept) {
        service.accept(accept.token(), accept.username(), accept.password()); return signIn();
    }
    @PostMapping(value="/api/auth/resets/complete", consumes="application/json")
    Map<String, Object> reset(@RequestBody Reset reset) { service.completeReset(reset.token(), reset.password()); return signIn(); }
    private Map<String, Object> signIn() { return Map.of("_links", Map.of("session", link("/api/auth/session", "GET"))); }
    @PostMapping("/api/tracks/{id}/playback-grants")
    Map<String, Object> grant(@PathVariable String id, Authentication authentication) {
        var grant = service.grant(identity(authentication), id);
        return Map.of("expiresAt", Instant.ofEpochMilli(grant.expiresAt()), "_links", Map.of("audio", link(grant.href(), "GET")));
    }
    static void cookie(HttpServletResponse response, String token, Identity identity, long now, AuthSecurity security) {
        response.addHeader("Set-Cookie", ResponseCookie.from(security.sessionCookie(), token).httpOnly(true).secure(security.requireHttps()).sameSite("Lax").path("/")
                .maxAge(Duration.ofSeconds(Math.max(0, (Math.min(identity.idleExpires(), identity.absoluteExpires()) - now) / 1000))).build().toString());
    }
    private void clear(HttpServletRequest request, HttpServletResponse response) {
        clearCookie(response, security);
        csrf.saveToken(null, request, response);
    }
    static void clearCookie(HttpServletResponse response, AuthSecurity security) {
        response.addHeader("Set-Cookie", ResponseCookie.from(security.sessionCookie(), "").httpOnly(true).secure(security.requireHttps()).sameSite("Lax").path("/").maxAge(0).build().toString());
    }
}
