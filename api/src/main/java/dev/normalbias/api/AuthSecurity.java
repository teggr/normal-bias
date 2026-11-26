package dev.normalbias.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.time.Clock;
import java.util.*;
import static dev.normalbias.api.AuthPolicy.*;

@Configuration
class AuthSecurity {
    static final String COOKIE = "__Host-NormalBias-Session";
    private final boolean demo;
    private final boolean privateDemo;
    AuthSecurity(@Value("${normalbias.auth.mode:required}") String mode, @Value("${server.address:127.0.0.1}") String address) {
        if (!Set.of("required", "demo", "private-demo").contains(mode)) throw new IllegalArgumentException("normalbias.auth.mode must be required, demo or private-demo");
        privateDemo = mode.equals("private-demo");
        demo = mode.equals("demo") || privateDemo;
        if (mode.equals("demo") && !Set.of("127.0.0.1", "::1", "[::1]").contains(address))
            throw new IllegalArgumentException("Unauthenticated demo mode requires a literal loopback server.address");
        if (privateDemo && !privateAddress(address))
            throw new IllegalArgumentException("private-demo requires an explicit literal RFC1918 IPv4 server.address");
    }
    boolean demo() { return demo; }
    private static boolean privateAddress(String address) {
        if (!address.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) return false;
        int[] octets = Arrays.stream(address.split("\\.")).mapToInt(Integer::parseInt).toArray();
        if (Arrays.stream(octets).anyMatch(value -> value > 255)) return false;
        return octets[0] == 10 || octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31
                || octets[0] == 192 && octets[1] == 168;
    }
    @Bean @ConditionalOnMissingBean(Clock.class) Clock authClock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder authPasswords() {
        // Versioned adaptive hashes; no bcrypt truncation of Unicode passwords.
        return new DelegatingPasswordEncoder("pbkdf2-sha256-v1", Map.of(
                "pbkdf2-sha256-v1", new Pbkdf2PasswordEncoder("", 16, 600_000, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256)));
    }
    @Bean CookieCsrfTokenRepository authCsrf() {
        var repository = new CookieCsrfTokenRepository();
        repository.setCookieName("__Host-NormalBias-Csrf");
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(true).path("/").sameSite("Lax"));
        return repository;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, AuthService service, ObjectMapper json, CookieCsrfTokenRepository csrf, Clock clock) throws Exception {
        // Authentication lives in H2 and is resolved afresh for each request. Servlet
        // SessionManagementFilter would treat every cookie request as a new login,
        // invoking CsrfAuthenticationStrategy and deleting CSRF on catalogue reads.
        http.sessionManagement(s -> s.disable())
                .securityContext(c -> c.securityContextRepository(new org.springframework.security.web.context.NullSecurityContextRepository()))
                .requestCache(c -> c.disable()).formLogin(c -> c.disable()).httpBasic(c -> c.disable()).logout(c -> c.disable())
                .csrf(c -> c.csrfTokenRepository(csrf).requireCsrfProtectionMatcher(request -> {
                    if (demo || !CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)) return false;
                    Identity identity = identity(SecurityContextHolder.getContext().getAuthentication());
                    if (identity != null && Set.of("BEARER", "UPLOAD").contains(identity.kind())) return false;
                    // These operations use explicit JSON credentials/one-time tokens, never ambient cookies.
                    return !Set.of("/api/auth/device-sessions", "/api/auth/invitations/inspect", "/api/auth/invitations/accept",
                            "/api/auth/resets/inspect", "/api/auth/resets/complete").contains(request.getServletPath());
                }))
                .exceptionHandling(c -> c.authenticationEntryPoint((request, response, failure) -> write(json, response, 401, "Sign in required"))
                        .accessDeniedHandler((request, response, failure) -> write(json, response, 403, "Action unavailable or CSRF token missing")))
                .headers(c -> c.addHeaderWriter((request, response) -> {
                    if (!demo && request.getServletPath().startsWith("/api")) {
                        response.setHeader("Cache-Control", "no-store"); response.setHeader("Referrer-Policy", "no-referrer");
                    }
                }))
                .addFilterBefore(new CredentialsFilter(service, json, demo, clock), CsrfFilter.class);
        http.authorizeHttpRequests(c -> {
            c.requestMatchers("/error").permitAll();
            if (demo) {
                c.requestMatchers("/api/auth/**", "/api/tracks/*/playback-grants").denyAll();
                if (privateDemo) {
                    // Listening-only LAN trial: never accept passwords, uploads or management actions.
                    c.requestMatchers(HttpMethod.GET, "/api", "/api/tracks", "/api/tracks/*", "/api/tracks/*/audio", "/api/tracks/*/artwork", "/api/library/**", "/api/album-artwork/revision").permitAll()
                            .requestMatchers(HttpMethod.HEAD, "/api/tracks/*/audio", "/api/tracks/*/artwork").permitAll()
                            .requestMatchers("/api/**").denyAll()
                            .requestMatchers(HttpMethod.GET, "/**").permitAll().anyRequest().denyAll();
                } else c.anyRequest().permitAll();
                return;
            }
            c.requestMatchers(HttpMethod.GET, "/api", "/api/auth/session").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/browser-sessions", "/api/auth/device-sessions",
                            "/api/auth/invitations/inspect", "/api/auth/invitations/accept", "/api/auth/resets/inspect", "/api/auth/resets/complete").permitAll()
                    .requestMatchers(HttpMethod.DELETE, "/api/auth/session").access((auth, ctx) -> decision(auth.get(), Action.SESSION))
                    .requestMatchers(HttpMethod.POST, "/api/auth/password").access((auth, ctx) -> decision(auth.get(), Action.SESSION))
                    .requestMatchers("/api/tracks/*/audio").access((auth, ctx) -> new AuthorizationDecision(
                            identity(auth.get()) != null && identity(auth.get()).kind().equals("GRANT")
                                    && Set.of("GET", "HEAD").contains(ctx.getRequest().getMethod())))
                    .requestMatchers(HttpMethod.POST, "/api/tracks/*/playback-grants").access((auth, ctx) -> decision(auth.get(), Action.LISTEN))
                    .requestMatchers(HttpMethod.POST, "/api/tracks").access((auth, ctx) -> decision(auth.get(), Action.UPLOAD))
                    .requestMatchers(HttpMethod.PATCH, "/api/tracks/*").access((auth, ctx) -> decision(auth.get(), Action.MANAGE))
                    .requestMatchers(HttpMethod.DELETE, "/api/tracks/*").access((auth, ctx) -> decision(auth.get(), Action.MANAGE))
                    .requestMatchers(HttpMethod.GET, "/api/tracks", "/api/tracks/**", "/api/library/**", "/api/album-artwork/revision").access((auth, ctx) -> decision(auth.get(), Action.LISTEN))
                    .requestMatchers(HttpMethod.HEAD, "/api/tracks/**").access((auth, ctx) -> decision(auth.get(), Action.LISTEN))
                    .requestMatchers(HttpMethod.HEAD, "/api/library/**", "/api/album-artwork/revision").access((auth, ctx) -> decision(auth.get(), Action.LISTEN))
                    .requestMatchers("/api/inspections/**").access((auth, ctx) -> decision(auth.get(), Action.INSPECT))
                    .requestMatchers("/api/album-artwork/**").access((auth, ctx) -> decision(auth.get(), Action.MANAGE))
                    .requestMatchers("/api/**").denyAll()
                    .requestMatchers(HttpMethod.GET, "/**").permitAll().anyRequest().denyAll();
        });
        return http.build();
    }
    private static AuthorizationDecision decision(org.springframework.security.core.Authentication auth, Action action) {
        return new AuthorizationDecision(allows(identity(auth), action));
    }
    static void write(ObjectMapper json, HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setHeader("Cache-Control", "no-store");
        json.writeValue(response.getOutputStream(), status == 426 ? Map.of("status", status, "message", message)
                : Map.of("status", status, "message", message, "_links", Map.of("session", link("/api", "GET"))));
    }
    private static final class CredentialsFilter extends OncePerRequestFilter {
        private final AuthService service; private final ObjectMapper json; private final boolean demo; private final Clock clock;
        CredentialsFilter(AuthService service, ObjectMapper json, boolean demo, Clock clock) { this.service = service; this.json = json; this.demo = demo; this.clock = clock; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
            String path = request.getServletPath();
            if (!path.equals("/api") && !path.startsWith("/api/")) { chain.doFilter(request, response); return; }
            if (demo) { chain.doFilter(request, response); return; }
            if (!request.isSecure()) { write(json, response, 426, "HTTPS required"); return; }
            Identity identity = null;
            String grant = request.getParameter("grant"), authorization = request.getHeader("Authorization");
            String cookie = null;
            if (request.getCookies() != null) for (Cookie value : request.getCookies()) if (value.getName().equals(COOKIE)) {
                if (cookie != null) { write(json, response, 401, "Invalid credentials"); return; } cookie = value.getValue();
            }
            if (grant != null) {
                if (request.getParameterValues("grant").length != 1) { write(json, response, 401, "Invalid playback grant"); return; }
                if (!path.matches("/api/tracks/[a-f0-9]{64}/audio") || !Set.of("GET", "HEAD").contains(request.getMethod())) {
                    write(json, response, 401, "Invalid playback grant"); return;
                }
                identity = service.authenticateGrant(grant, path.split("/")[3]).orElse(null);
            } else if (authorization != null) {
                if (authorization.startsWith("Bearer ")) identity = service.authenticate(authorization.substring(7), "BEARER").orElse(null);
            } else if (cookie != null) identity = service.authenticate(cookie, "COOKIE").orElse(null);
            if (cookie != null && authorization == null && grant == null && identity == null) {
                AuthController.clearCookie(response);
                // An expired cookie must not prevent recovery or a fresh sign-in.
                if (Set.of("/api", "/api/auth/session", "/api/auth/browser-sessions", "/api/auth/device-sessions",
                        "/api/auth/invitations/inspect", "/api/auth/invitations/accept", "/api/auth/resets/inspect", "/api/auth/resets/complete").contains(path)) cookie = null;
            }
            if ((grant != null || authorization != null || cookie != null) && identity == null) {
                write(json, response, 401, "Credentials expired or invalid"); return;
            }
            if (identity != null) {
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(identity, null, List.of())); SecurityContextHolder.setContext(context);
                if (identity.kind().equals("COOKIE")) AuthController.cookie(response, cookie, identity, clock.millis());
            }
            // Bound credential-bearing JSON before parsing, including chunked requests.
            if (path.startsWith("/api/auth/") && Set.of("POST", "PUT", "PATCH").contains(request.getMethod())) {
                byte[] body = request.getInputStream().readNBytes(16_385);
                if (body.length > 16_384) { write(json, response, 413, "Authentication request too large"); return; }
                var source = new ByteArrayInputStream(body);
                request = new HttpServletRequestWrapper(request) {
                    @Override public ServletInputStream getInputStream() {
                        return new ServletInputStream() {
                            @Override public int read() { return source.read(); }
                            @Override public boolean isFinished() { return source.available() == 0; }
                            @Override public boolean isReady() { return true; }
                            @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                        };
                    }
                };
            }
            chain.doFilter(request, response);
        }
    }
}
