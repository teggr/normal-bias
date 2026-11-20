package dev.normalbias.client.net;

import com.codename1.io.ConnectionRequest;
import com.codename1.io.JSONParser;
import com.codename1.io.NetworkManager;
import com.codename1.ui.CN;
import com.codename1.ui.Image;
import java.io.ByteArrayOutputStream;
import com.codename1.ui.EncodedImage;
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

    private final ArtworkState<Image> artworkState = new ArtworkState<>(System::currentTimeMillis, com.codename1.io.Log::e);
    private Consumer<String> artworkLoaded = ignored -> {};
    public void onArtworkLoaded(Consumer<String> listener) { artworkLoaded = listener; }
    public String artworkKey(Track track) { return artworkState.key(track.id()); }
    /** Passive checks are throttled and recover if a native request never completes. */
    public void checkArtworkRevision(boolean force, Consumer<Boolean> changed) {
        long token = artworkState.beginRevision(force);
        if (token == 0) return;
        json("/api/album-artwork/revision", result -> {
            Object revision = result == null ? null : result.get("revision");
            changed.accept(artworkState.revisionLoaded(token, revision instanceof String ? (String) revision : null));
        }, ignored -> artworkState.revisionFailed(token));
    }
    private void json(String path, Consumer<Map<String, Object>> success, Consumer<String> failure) {
        ConnectionRequest request = new ConnectionRequest() {
            private Map<String, Object> result;
            private boolean delivered;
            private void fail(String text) { if (!delivered) { delivered = true; CN.callSerially(() -> failure.accept(text)); } }
            @Override protected void readResponse(InputStream input) throws IOException {
                result = new JSONParser().parseJSON(new InputStreamReader(input, "UTF-8"));
            }
            @Override protected void postResponse() { if (!delivered) { delivered = true; CN.callSerially(() -> success.accept(result)); } }
            @Override protected void handleErrorResponseCode(int code, String message) {
                fail("Library request failed (HTTP " + code + "). Try again.");
            }
            @Override protected void handleException(Exception ex) { fail("Cannot reach the library service. Retry later."); }
        };
        request.setUrl(origin + path);
        request.setPost(false);
        request.setHttpMethod("GET");
        request.setTimeout(15000); request.setReadTimeout(20000);
        NetworkManager.getInstance().addToQueue(request);
    }
    /** EDT-only. Both positive and missing results are shared, with bounded negative lifetimes. */
    public void artwork(Track track, Consumer<Image> loaded) {
        artworkState.get(track.id(), loaded, (key, complete) -> {
            ConnectionRequest request = new ConnectionRequest() {
                private byte[] bytes;
                private boolean delivered;
                private void finish(boolean definitive) {
                    if (delivered) return;
                    delivered = true;
                    CN.callSerially(() -> {
                        Image image = null;
                        try { if (bytes != null && bytes.length > 0) image = EncodedImage.create(bytes); }
                        catch (RuntimeException ignored) { }
                        complete.complete(image, definitive);
                        if (image != null && key.equals(artworkKey(track))) artworkLoaded.accept(track.id());
                    });
                }
                @Override protected void readResponse(InputStream input) throws IOException {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
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
            int version = key.indexOf('?');
            request.setUrl(origin + "/api/tracks/" + track.id() + "/artwork" + key.substring(version));
            request.setPost(false); request.setHttpMethod("GET");
            request.setReadTimeout(15000); request.setTimeout(15000);
            NetworkManager.getInstance().addToQueue(request);
        });
    }

    public record Group(String artist, String album, int trackCount, String trackId) {}
    public record GroupPage(List<Group> items, int offset, int total) {}
    public void groups(String view, String query, int offset, String artist,
                       Consumer<GroupPage> success, Consumer<String> failure) {
        String path = "/api/library/" + view + "?offset=" + offset + "&limit=" + PAGE_SIZE
                + "&q=" + com.codename1.io.Util.encodeUrl(query);
        if (artist != null) path += "&artist=" + com.codename1.io.Util.encodeUrl(artist);
        json(path, data -> {
            try {
                List<Group> groups = new ArrayList<>();
                for (Object item : (List<?>) data.get("items")) {
                    Map<?, ?> row = (Map<?, ?>) item;
                    groups.add(new Group((String) row.get("artist"), (String) row.get("album"),
                            ((Number) row.get("trackCount")).intValue(), (String) row.get("trackId")));
                }
                success.accept(new GroupPage(groups, ((Number) data.get("offset")).intValue(), ((Number) data.get("total")).intValue()));
            } catch (RuntimeException ex) { failure.accept("Unexpected library response. Try again."); }
        }, failure);
    }

    public record Page(List<Track> items, int offset, int limit, int total) {}

    public void list(String query, int offset, Consumer<Page> success, Consumer<String> failure) {
        list(query, offset, null, null, success, failure);
    }

    public void list(String query, int offset, String artist, String album, Consumer<Page> success, Consumer<String> failure) {
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
        if (artist != null) request.addArgument("artist", artist);
        if (album != null) request.addArgument("album", album);
        request.addArgument("q", query);
        request.addArgument("offset", String.valueOf(offset));
        request.addArgument("limit", String.valueOf(PAGE_SIZE));
        NetworkManager.getInstance().addToQueue(request);
    }
}
