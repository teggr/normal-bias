package dev.normalbias.client.player;

import com.codename1.ai.Tool;
import com.codename1.io.*;
import com.codename1.media.MediaManager;
import com.codename1.media.StreamRequest;
import com.codename1.mcp.MCP;
import com.codename1.security.SecureStorage;
import com.codename1.ui.*;
import dev.normalbias.client.net.LibraryApi;
import dev.normalbias.client.ui.LibraryForm;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/** Injected ONLY into an ignored copy of the real client for the native iOS trial.
 * Retains LibraryForm, Player, FrameworkAudio and SystemAudio; no production login.
 */
public final class AuthAudioProbe {
    private static final String ORIGIN = "https://127.0.0.1:18443";
    private static final String KEY = "normalbias.auth-audio-trial";
    private static String token, grantPath;
    private static LibraryForm form;
    private static boolean starting;

    public static void boot() {
        if (form != null) { form.show(); return; }
        if (starting) return;
        starting = true;
        Form connecting = new Form("Authenticated audio trial");
        connecting.add(new Label("Connecting to disposable HTTPS service…")); connecting.show();
        String restored = SecureStorage.getInstance().get(KEY);
        if (restored != null) {
            token = restored; grantPath = "/trial/grants"; ready(true); return;
        }
        try {
            Map<String, Object> config = new JSONParser().parseJSON(new InputStreamReader(CN.getResourceAsStream("/auth-audio-trial.json"), "UTF-8"));
            request("/trial/session", "POST", "", (String) config.get("bootstrap"), result -> {
                token = (String) result.get("token"); grantPath = link(result, "grant");
                if (token == null || !SecureStorage.getInstance().set(KEY, token)
                        || !token.equals(SecureStorage.getInstance().get(KEY))) throw new IllegalStateException("Keychain roundtrip failed");
                ready(false);
            }, AuthAudioProbe::failed);
        } catch (Exception failure) { failed(); }
    }
    private static void failed() {
        Form failed = new Form("Trial connection failed");
        failed.add(new Label("Check TLS trust and the disposable service. No TLS bypass is used.")); failed.show();
    }
    private static void ready(boolean restored) {
        form = new LibraryForm(new LibraryApi(ORIGIN)); form.show();
        MCP.addTool(new Tool("auth_audio_probe", "Synthetic iOS authenticated-audio test controls; no secrets returned",
                "{\"type\":\"object\",\"properties\":{\"op\":{\"type\":\"string\"}},\"required\":[\"op\"]}", AuthAudioProbe::control));
        MCP.startSocketServer(18867);
        event("keychain", Map.of("restored", restored, "roundtrip", token.equals(SecureStorage.getInstance().get(KEY))));
        event("headers", Map.of("supported", MediaManager.isStreamHeadersSupported()));
    }
    public static void dispose() { if (form != null) form.disposePlayer(); }
    public static void authorize(ConnectionRequest request) { if (token != null) request.addRequestHeader("Authorization", "Bearer " + token); }
    private static String link(Map<String, Object> result, String relation) {
        Map<?, ?> links = (Map<?, ?>) result.get("_links");
        Map<?, ?> link = (Map<?, ?>) links.get(relation);
        String target = (String) link.get("href");
        if (target == null || !target.startsWith("/") || target.startsWith("//") || target.contains("\\")) throw new IllegalStateException("Invalid trial link");
        return target;
    }
    private static void request(String path, String method, String body, String bootstrap,
                                Consumer<Map<String, Object>> success, Runnable failure) {
        ConnectionRequest request = new ConnectionRequest() {
            private Map<String, Object> result;
            @Override protected void readResponse(InputStream input) throws IOException {
                JSONParser parser = new JSONParser(); parser.setUseBooleanInstance(true);
                result = parser.parseJSON(new InputStreamReader(input, "UTF-8"));
            }
            @Override protected void postResponse() { CN.callSerially(() -> success.accept(result)); }
            @Override protected void handleErrorResponseCode(int code, String message) { CN.callSerially(failure); }
            @Override protected void handleException(Exception error) { CN.callSerially(failure); }
        };
        request.setUrl(ORIGIN + path); request.setPost(method.equals("POST")); request.setHttpMethod(method);
        if (method.equals("POST")) { request.setRequestBody(body); request.setContentType("text/plain"); }
        if (bootstrap != null) request.addRequestHeader("X-Trial-Pairing", bootstrap); else authorize(request);
        request.setTimeout(10000); request.setReadTimeout(10000);
        NetworkManager.getInstance().addToQueue(request);
    }
    private static void event(String kind, Map<String, Object> values) {
        Map<String, Object> entry = new HashMap<>(values); entry.put("kind", kind); entry.put("atMs", System.currentTimeMillis());
        request("/trial/event", "POST", JSONParser.mapToJson(entry), null, ignored -> {}, () -> {});
    }
    private static String control(String json) throws Exception {
        Map<String, Object> args = new JSONParser().parseJSON(new StringReader(json));
        String op = (String) args.get("op");
        String[] result = new String[1];
        CN.callSeriallyAndWait(() -> {
            Player player = form.probePlayer();
            switch (op) {
                case "pause": player.pause(); break;
                case "resume": player.resume(); break;
                case "seek90": player.seekTime(90_000); break;
                case "seek10": player.seekTime(10_000); break;
                case "nearEnd": player.seekTime(175_000); break;
                case "next": player.next(); break;
                case "clear": player.clear(); break;
                case "headers":
                    try {
                        new StreamRequest(ORIGIN + "/unused").addHeader("Authorization", "Bearer synthetic").create();
                        result[0] = "Unexpectedly accepted stream headers";
                    } catch (IOException expected) { result[0] = expected.getMessage(); }
                    return;
                default: break;
            }
            player.poll();
            Map<String, Object> state = new HashMap<>();
            state.put("time", player.time()); state.put("duration", player.duration()); state.put("playing", player.isPlaying());
            state.put("loading", player.isLoading()); state.put("queueSize", player.queue.size()); state.put("index", player.queue.index());
            state.put("status", player.status()); state.put("op", op);
            event("state", state); result[0] = JSONParser.mapToJson(state);
        });
        return result[0];
    }
    public static BrowserAudio audio(Runnable changed) { return new GrantedAudio(changed); }
    private static final class GrantedAudio implements BrowserAudio {
        private final FrameworkAudio delegate;
        private final Runnable changed;
        private int generation;
        private boolean pending, wantPlay;
        private String error = "";
        GrantedAudio(Runnable changed) { this.changed = changed; delegate = new FrameworkAudio(changed); }
        public boolean isSupported() { return true; }
        public void open(String url) {
            close(); pending = true; int attempt = generation;
            String path = url.substring(ORIGIN.length()); String id = path.split("/")[3];
            request(grantPath, "POST", id, null, result -> {
                if (attempt != generation) return;
                String target = link(result, "audio");
                delegate.open(ORIGIN + target); pending = false;
                if (wantPlay) delegate.play();
                event("media-open", Map.of("track", id)); changed.run();
            }, () -> { if (attempt == generation) { pending = false; error = "Trial grant refused"; changed.run(); } });
        }
        public void play() { wantPlay = true; if (!pending) delegate.play(); }
        public void pause() { wantPlay = false; if (!pending) delegate.pause(); }
        public void seek(int milliseconds) { if (!pending) delegate.seek(milliseconds); }
        public String snapshot() {
            if (pending || !error.isEmpty()) return "{\"time\":0,\"duration\":0,\"playing\":false,\"loading\":" + pending + ",\"waiting\":false,\"ended\":false,\"error\":\"" + error + "\"}";
            return delegate.snapshot();
        }
        public void close() { generation++; pending = false; wantPlay = false; error = ""; delegate.close(); }
    }
}
