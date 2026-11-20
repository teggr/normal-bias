package dev.normalbias.client.net;

import com.codename1.io.ConnectionRequest;
import com.codename1.io.JSONParser;
import com.codename1.io.NetworkManager;
import com.codename1.ui.CN;
import com.codename1.ui.Image;
import java.io.ByteArrayOutputStream;
import com.codename1.ui.EncodedImage;
import java.util.LinkedHashMap;
import java.util.HashMap;
import dev.normalbias.client.Track;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** All requests go directly to the API on the page's origin. No CORS proxy. */
public final class LibraryApi {
    public static final int PAGE_SIZE = 25;
    private final String origin;

    public LibraryApi(String pageUrl) {
        int scheme = pageUrl.indexOf("://");
        if (scheme < 0) throw new IllegalArgumentException("Open the client through the API's HTTP server");
        int end = pageUrl.indexOf('/', scheme + 3);
        origin = end < 0 ? pageUrl : pageUrl.substring(0, end);
    }

    public String audioUrl(Track track) { return origin + "/api/tracks/" + track.id() + "/audio"; }

    private final Map<String, String> revisions = new HashMap<>();
    public String artworkKey(Track track) { return track.id() + "?v=" + revisions.getOrDefault(track.id(), "embedded"); }
    public void invalidateArtwork(List<String> ids, String revision) {
        for (String id : ids) revisions.put(id, revision);
        covers.clear();
    }
    public void json(String path, String body, Consumer<Map<String, Object>> success, Consumer<String> failure) {
        ConnectionRequest request = new ConnectionRequest() {
            private Map<String, Object> result;
            private boolean delivered;
            private void fail(String text) { if (!delivered) { delivered = true; CN.callSerially(() -> failure.accept(text)); } }
            @Override protected void readResponse(InputStream input) throws IOException {
                result = new JSONParser().parseJSON(new InputStreamReader(input, "UTF-8"));
            }
            @Override protected void postResponse() { if (!delivered) { delivered = true; CN.callSerially(() -> success.accept(result)); } }
            @Override protected void handleErrorResponseCode(int code, String message) {
                fail(code == 409 || code == 410 ? "Tracks or artwork changed, or lookup expired. Start a fresh match." :
                        "Artwork request failed (HTTP " + code + "). Check tags/connection and retry.");
            }
            @Override protected void handleException(Exception ex) { fail("Cannot reach the artwork service. Retry later."); }
        };
        request.setUrl(origin + path);
        request.setPost(body != null);
        request.setHttpMethod(body == null ? "GET" : "POST");
        if (body != null) { request.setContentType("application/json"); request.setRequestBody(body); }
        request.setTimeout(15000); request.setReadTimeout(20000);
        NetworkManager.getInstance().addToQueue(request);
    }
    public void preview(String path, Consumer<Image> loaded) {
        ConnectionRequest request = new ConnectionRequest() {
            private byte[] bytes;
            @Override protected void readResponse(InputStream input) throws IOException {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 2 * 1024 * 1024) throw new IOException("Preview too large");
                    output.write(buffer, 0, count);
                }
                bytes = output.toByteArray();
            }
            @Override protected void postResponse() { CN.callSerially(() -> {
                Image image = null;
                try { if (bytes != null) image = EncodedImage.create(bytes); } catch (RuntimeException ignored) { }
                loaded.accept(image);
            }); }
            @Override protected void handleErrorResponseCode(int code, String message) { CN.callSerially(() -> loaded.accept(null)); }
            @Override protected void handleException(Exception ex) { CN.callSerially(() -> loaded.accept(null)); }
        };
        request.setUrl(origin + path); request.setPost(false); request.setHttpMethod("GET");
        request.setTimeout(15000); request.setReadTimeout(20000);
        NetworkManager.getInstance().addToQueue(request);
    }

    private final Map<String, Image> covers = new LinkedHashMap<>();
    private final Map<String, List<Consumer<Image>>> pendingCovers = new HashMap<>();

    /** EDT-only, shared across library/player/mini-player. Missing art preserves the sleeve. */
    public void artwork(Track track, Consumer<Image> loaded) {
        String id = artworkKey(track);
        if (covers.containsKey(id)) { loaded.accept(covers.get(id)); return; }
        if (pendingCovers.containsKey(id)) { pendingCovers.get(id).add(loaded); return; }
        List<Consumer<Image>> listeners = new ArrayList<>();
        listeners.add(loaded);
        pendingCovers.put(id, listeners);
        ConnectionRequest request = new ConnectionRequest() {
            private byte[] bytes;
            private boolean delivered;
            private void finish(boolean cache) {
                if (delivered) return;
                delivered = true;
                CN.callSerially(() -> {
                    Image image = null;
                    try { if (bytes != null && bytes.length > 0) image = EncodedImage.create(bytes); }
                    catch (RuntimeException ignored) { }
                    if (cache) {
                        covers.put(id, image);
                        if (covers.size() > 32) covers.remove(covers.keySet().iterator().next());
                    }
                    List<Consumer<Image>> callbacks = pendingCovers.remove(id);
                    if (callbacks != null) for (Consumer<Image> callback : callbacks) callback.accept(image);
                });
            }
            @Override protected void readResponse(InputStream input) throws IOException {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer, 0, buffer.length)) != -1) {
                    if (output.size() + count > 2 * 1024 * 1024) throw new IOException("Artwork response too large");
                    output.write(buffer, 0, count);
                }
                bytes = output.toByteArray();
                if (bytes.length == 0) throw new IOException("Empty artwork response");
            }
            @Override protected void postResponse() { finish(true); }
            @Override protected void handleErrorResponseCode(int code, String message) { finish(code == 404); }
            @Override protected void handleException(Exception ex) { finish(false); }
        };
        request.setUrl(origin + "/api/tracks/" + track.id() + "/artwork?v=" + revisions.getOrDefault(track.id(), "embedded"));
        request.setPost(false);
        request.setHttpMethod("GET");
        request.setReadTimeout(15000);
        request.setTimeout(15000);
        NetworkManager.getInstance().addToQueue(request);
    }

    public record Page(List<Track> items, int offset, int limit, int total) {}

    public void list(String query, int offset, Consumer<Page> success, Consumer<String> failure) {
        ConnectionRequest request = new ConnectionRequest() {
            private Page page;
            private boolean delivered;

            private void fail(String message) {
                if (delivered) return;
                delivered = true;
                CN.callSerially(() -> failure.accept(message));
            }

            @Override protected void readResponse(InputStream input) throws IOException {
                try {
                    Map<String, Object> json = new JSONParser().parseJSON(new InputStreamReader(input, "UTF-8"));
                    List<Track> tracks = new ArrayList<>();
                    for (Object item : (List<?>) json.get("items")) {
                        Map<?, ?> row = (Map<?, ?>) item;
                        String id = (String) row.get("id");
                        if (row.get("artworkRevision") != null) revisions.put(id, (String) row.get("artworkRevision"));
                        if (id == null || id.length() != 64) throw new IllegalArgumentException("Invalid track ID");
                        tracks.add(new Track(id, (String) row.get("title"), (String) row.get("artist"),
                                (String) row.get("album"), ((Number) row.get("durationMs")).intValue()));
                    }
                    page = new Page(tracks, ((Number) json.get("offset")).intValue(),
                            ((Number) json.get("limit")).intValue(), ((Number) json.get("total")).intValue());
                } catch (RuntimeException ex) {
                    throw new IOException("Unexpected catalogue response", ex);
                }
            }
            @Override protected void postResponse() {
                if (!delivered) { delivered = true; success.accept(page); }
            }
            @Override protected void handleErrorResponseCode(int code, String message) {
                fail("Catalogue request failed (HTTP " + code + "). Try again.");
            }
            @Override protected void handleException(Exception ex) {
                fail("Cannot load the library. Check the API connection and try again.");
            }
        };
        request.setUrl(origin + "/api/tracks");
        request.setPost(false);
        request.setHttpMethod("GET");
        request.setReadTimeout(15000);
        request.setTimeout(15000);
        request.addArgument("q", query);
        request.addArgument("offset", String.valueOf(offset));
        request.addArgument("limit", String.valueOf(PAGE_SIZE));
        NetworkManager.getInstance().addToQueue(request);
    }
}
