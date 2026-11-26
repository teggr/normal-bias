package dev.normalbias.client.net;

import com.codename1.io.*;
import com.codename1.security.SecureStorage;
import com.codename1.ui.CN;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** EDT-owned session lifecycle. Passwords are never retained; native credentials use Keychain. */
public final class AuthSession {
    public final String origin;
    private final boolean browser;
    private final String storageKey;
    private String bearer;
    private Map<String, Object> state = new HashMap<>();
    private int generation;
    private boolean reachable;
    private ConnectionRequest discoveryRequest;
    private java.util.Timer discoveryDeadline;
    private Runnable lost = () -> {};
    public AuthSession(String page, boolean browser) {
        int scheme = page.indexOf("://"), end = page.indexOf('/', scheme + 3);
        origin = end < 0 ? page : page.substring(0, end);
        if (scheme < 0 || !(origin.startsWith("https://") || origin.startsWith("http://")) || origin.contains("@") || origin.contains("?") || origin.contains("#")) throw new IllegalArgumentException("Invalid service origin");
        this.browser = browser;
        storageKey = "normalbias.session." + origin;
        if (!browser) { try { bearer = SecureStorage.getInstance().get(storageKey); } catch (RuntimeException unavailable) { bearer = null; } }
    }
    public String identity() {
        Object info = state.get("session");
        return info instanceof Map ? (String) ((Map<?, ?>) info).get("id") : "demo";
    }
    public boolean authenticated() { return Boolean.TRUE.equals(state.get("authenticated")); }
    public boolean hasRememberedSignIn() { return bearer != null; }
    public boolean reachable() { return reachable; }
    public boolean demo() { return Boolean.FALSE.equals(state.get("authRequired")); }
    public String username() {
        Object account = state.get("account");
        return account instanceof Map ? (String) ((Map<?, ?>) account).get("username") : "Local demo";
    }
    public String link(String relation, String method) { return ApiLinks.href(state, relation, method); }
    public void onLost(Runnable listener) { lost = listener; }
    public void invalidate() {
        generation++;
        if (discoveryDeadline != null) { discoveryDeadline.cancel(); discoveryDeadline = null; }
        ConnectionRequest pending = discoveryRequest; discoveryRequest = null;
        if (pending != null) pending.kill();
    }
    public void restore(Runnable ready, Consumer<String> failure) {
        reachable = false;
        request("/api", "GET", null, false, data -> {
            if (!(data.get("authenticated") instanceof Boolean) || !(data.get("authRequired") instanceof Boolean)
                    || !(data.get("_links") instanceof Map)) {
                failure.accept("This address did not return a Normal Bias service. Check the service address."); return;
            }
            state = data;
            reachable = true;
            if (Preferences.get(storageKey + ".logout", false)) {
                if (!authenticated()) { clearCredential(); Preferences.delete(storageKey + ".logout"); ready.run(); }
                else logout(() -> restore(ready, failure), failure);
            } else ready.run();
        }, code -> {
            if (code == 401 && bearer != null) { clearCredential(); restore(ready, failure); }
            else failure.accept(message(code));
        });
    }
    public void login(String username, String password, Runnable ready, Consumer<String> failure) {
        if (!origin.startsWith("https://")) { failure.accept("Sign in requires HTTPS."); return; }
        String action = link(browser ? "browser-login" : "device-login", "POST");
        if (action == null) { failure.accept("Sign in is not currently available. Retry the connection."); return; }
        Map<String, Object> body = new HashMap<>(); body.put("username", username); body.put("password", password);
        request(action, "POST", body, false, data -> {
            if (!browser) {
                bearer = (String) data.get("token");
                if (bearer == null) { failure.accept("Unexpected sign-in response."); return; }
                if (!remember()) {
                    // Retain the live credential to revoke it; never fall back to plaintext storage.
                    Preferences.set(storageKey + ".logout", true);
                    restore(() -> failure.accept("Secure storage unavailable. Please sign in again."), failure); return;
                }
            }
            restore(ready, failure); // Login rotates CSRF; always discover a fresh token and capabilities.
        }, code -> {
            String text = code == 401 ? "Username or password not recognised." : code == 429 ? "Too many attempts. Wait before trying again." : message(code);
            // Refresh CSRF after failures without replaying a password submission.
            restore(() -> failure.accept(text), failure);
        });
    }
    public void logout(Runnable done, Consumer<String> failure) {
        invalidate();
        Preferences.set(storageKey + ".logout", true);
        String action = link("logout", "DELETE");
        if (action == null) { failure.accept("Cannot finish sign out. Retry the connection."); return; }
        request(action, "DELETE", null, false, data -> finishLogout(done), code -> {
            if (code == 401) finishLogout(done);
            else failure.accept("Playback stopped. Server sign out is pending; retry to finish.");
        });
    }
    public void inspect(AccountLink link, Consumer<Map<String, Object>> ready, Consumer<String> failure) {
        String action = link(link.invitation ? "inspect-invitation" : "inspect-reset", "POST");
        if (action == null) { failure.accept("Reconnect or sign out before opening this link."); return; }
        Map<String, Object> body = new HashMap<>(); body.put("token", link.token);
        request(action, "POST", body, false, ready, code -> failure.accept(accountError(code)));
    }
    public void complete(AccountLink link, Map<String, Object> inspected, String username, String password,
                         Runnable ready, Consumer<String> failure) {
        String action = ApiLinks.href(inspected, link.invitation ? "accept" : "complete", "POST");
        if (action == null) { failure.accept("This link is no longer available. Ask the owner for a new link."); return; }
        Map<String, Object> body = new HashMap<>(); body.put("token", link.token); body.put("password", password);
        if (link.invitation) body.put("username", username);
        request(action, "POST", body, false, data -> restore(ready, failure), code -> failure.accept(accountError(code)));
    }
    public void changePassword(String current, String password, Runnable ready, Consumer<String> failure) {
        String action = link("change-password", "POST");
        if (action == null) { failure.accept("Password change is unavailable. Reconnect to refresh your session."); return; }
        Map<String, Object> body = new HashMap<>(); body.put("currentPassword", current); body.put("newPassword", password);
        request(action, "POST", body, false, data -> {
            invalidate(); clearCredential(); state = new HashMap<>(); Preferences.delete(storageKey + ".logout"); ready.run();
        }, code -> {
            // A wrong current password is also 401; discovery distinguishes it from a lost session.
            restore(() -> {
                if (!authenticated()) { unauthorized(); return; }
                failure.accept(code == 401 ? "Current password not recognised." : accountError(code));
            }, failure);
        });
    }
    private static String accountError(int code) {
        if (code == 404) return "This link is invalid, expired, already used or revoked. Ask the owner for a new link.";
        if (code == 409) return "That username or account state changed. Check your username and inspect the link again.";
        if (code == 400) return "Check the username (3–64 letters, digits, dots, underscores or hyphens) and password (12–256 characters).";
        if (code < 0 || code >= 500) return "The result could not be confirmed. Try signing in first; if needed, inspect the link again before submitting.";
        return message(code);
    }
    private void finishLogout(Runnable done) {
        clearCredential(); state = new HashMap<>(); Preferences.delete(storageKey + ".logout"); done.run();
    }
    private boolean remember() {
        try { return SecureStorage.getInstance().set(storageKey, bearer) && bearer.equals(SecureStorage.getInstance().get(storageKey)); }
        catch (RuntimeException unavailable) { return false; }
    }
    private void clearCredential() {
        bearer = null;
        if (!browser) { try { SecureStorage.getInstance().remove(storageKey); } catch (RuntimeException unavailable) { /* Already revoked server-side, or handled as 401 on restore. */ } }
    }
    public void authorize(ConnectionRequest request) {
        request.setFollowRedirects(false);
        request.setCookiesEnabled(browser);
        request.setCacheMode(ConnectionRequest.CachingMode.OFF);
        if (bearer != null) request.addRequestHeader("Authorization", "Bearer " + bearer);
    }
    public void unauthorized() {
        invalidate(); clearCredential(); state = new HashMap<>(); lost.run();
    }
    public void request(String path, String method, Map<String, Object> body, boolean protectedRequest,
                        Consumer<Map<String, Object>> success, Consumer<Integer> failure) {
        final int version = generation;
        final java.util.Timer[] deadline = new java.util.Timer[1];
        ConnectionRequest request = new ApiRequest() {
            private Map<String, Object> result;
            private boolean delivered;
            private void finish(int code) {
                CN.callSerially(() -> {
                    if (delivered) return; delivered = true;
                    if (deadline[0] != null) deadline[0].cancel();
                    if (discoveryRequest == this) { discoveryRequest = null; discoveryDeadline = null; }
                    if (version != generation) return;
                    if (protectedRequest && code == 401) { unauthorized(); return; }
                    if (code == 0) success.accept(result); else failure.accept(code);
                });
            }
            @Override protected void readResponse(InputStream input) throws IOException {
                JSONParser parser = new JSONParser(); parser.setUseBooleanInstance(true);
                result = parser.parseJSON(new InputStreamReader(input, "UTF-8"));
            }
            @Override protected void postResponse() { finish(0); }
            @Override protected void handleErrorResponseCode(int code, String message) { finish(code); }
            @Override protected void handleException(Exception error) { finish(connectionError(error)); }
        };
        request.setUrl(origin + ApiLinks.checked(path)); request.setPost(!method.equals("GET")); request.setHttpMethod(method);
        authorize(request);
        if (browser && !method.equals("GET") && state.get("csrfToken") instanceof String) request.addRequestHeader((String) state.get("csrfHeader"), (String) state.get("csrfToken"));
        if (body != null) { request.setRequestBody(JSONParser.mapToJson(body)); request.setContentType("application/json"); }
        request.setSilentRetryCount(0);
        request.setTimeout(15000); request.setReadTimeout(20000);
        if (path.equals("/api") && method.equals("GET")) {
            request.setTimeout(6000); request.setReadTimeout(6000);
            // A UI deadline also bounds native/network-queue stalls; late responses cannot unlock the form.
            deadline[0] = CN.setTimeout(6000, () -> {
                ((ApiRequest) request).networkFailed(new java.io.IOException("Connection timed out"));
                request.kill();
            });
            discoveryRequest = request; discoveryDeadline = deadline[0];
        }
        NetworkManager.getInstance().addToQueue(request);
    }
    static int connectionError(Exception error) {
        String detail = String.valueOf(error).toLowerCase();
        if (detail.contains("certificate") || detail.contains("ssl") || detail.contains("tls") || detail.contains("trust")) return -3;
        if (detail.contains("unknownhost") || detail.contains("resolve") || detail.contains("hostname")) return -4;
        if (detail.contains("timed out") || detail.contains("timeout")) return -2;
        return -1;
    }
    public static String message(int code) {
        if (code == -2) return "Connection timed out. Check the service address, Wi-Fi and Local Network permission, then retry.";
        if (code == -3) return "Cannot verify this service’s HTTPS certificate. Check its expiry, address and certificate trust on this device.";
        if (code == -4) return "Service hostname not found. Check the address and your network, then retry.";
        if (code == 403) return "This action is not allowed. Reconnect to refresh your session.";
        if (code == 426) return "This service requires HTTPS.";
        if (code > 0) return "Service request failed (HTTP " + code + "). Retry the connection.";
        return "Cannot reach the service. Check the address, Wi-Fi and Local Network permission, then retry.";
    }
}
