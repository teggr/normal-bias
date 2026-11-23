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
public class LibraryApi {
    public static final int PAGE_SIZE = 25;
    private final String origin;
    private final AuthSession session;
    private boolean disposed;
    private final Map<String, Map<String, Object>> resources = new java.util.HashMap<>();
    public LibraryApi(AuthSession session) { this.session = session; this.origin = session.origin; }
    public void dispose() { disposed = true; resources.clear(); artworkLoaded = ignored -> {}; }
    public String username() { return session == null ? "Local demo" : session.username(); }
    public boolean canLogout() { return session != null && session.link("logout", "DELETE") != null; }
    private String collection(String relation, String legacy) {
        String path = session == null ? legacy : session.link(relation, "GET");
        if (path == null) throw new IllegalStateException("Library action unavailable");
        return path;
    }
    public boolean canPlay(Track track) { return !scopedPlayback() || ApiLinks.href(resources.get(track.id()), "playback-grant", "POST") != null; }
    public boolean scopedPlayback() { return session != null && !session.demo(); }
    public void playback(Track track, Consumer<String> ready, Consumer<String> failure) {
        if (!scopedPlayback()) { ready.accept(audioUrl(track)); return; }
        String action = ApiLinks.href(resources.get(track.id()), "playback-grant", "POST");
        if (action == null) { failure.accept("Playback is not allowed for this track. Refresh the library."); return; }
        session.request(action, "POST", new java.util.HashMap<>(), true, data -> {
            if (disposed) return;
            String link = ApiLinks.href(data, "audio", "GET");
            if (link == null) failure.accept("No playback link returned."); else ready.accept(origin + link);
        }, code -> { if (!disposed) failure.accept(AuthSession.message(code)); });
    }

    public LibraryApi(String pageUrl) {
        session = null;
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
        json(collection("artwork-revision", "/api/album-artwork/revision"), result -> {
            Object revision = result == null ? null : result.get("revision");
            changed.accept(artworkState.revisionLoaded(token, revision instanceof String ? (String) revision : null));
        }, ignored -> artworkState.revisionFailed(token));
    }
    private void json(String path, Consumer<Map<String, Object>> success, Consumer<String> failure) {
        if (disposed) return;
        if (session != null) {
            session.request(path, "GET", null, true, data -> { if (!disposed) success.accept(data); },
                    code -> { if (!disposed) failure.accept(AuthSession.message(code)); });
            return;
        }
        throw new IllegalStateException("Discovery is required for network requests");
    }
    /** EDT-only. Both positive and missing results are shared, with bounded negative lifetimes. */
    public void artwork(Track track, Consumer<Image> loaded) {
        if (disposed) return;
        artworkState.get(track.id(), loaded, (key, complete) -> {
            ConnectionRequest request = new ApiRequest() {
                private byte[] bytes;
                private boolean delivered;
                private void finish(boolean definitive) {
                    if (delivered) return;
                    delivered = true;
                    CN.callSerially(() -> {
                        if (disposed) return;
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
                @Override protected void handleErrorResponseCode(int code, String message) { if (code == 401 && session != null) CN.callSerially(() -> { if (!disposed) session.unauthorized(); }); finish(code == 404); }
                @Override protected void handleException(Exception ex) { finish(false); }
            };
            int version = key.indexOf('?');
            String path = scopedPlayback() ? ApiLinks.href(resources.get(track.id()), "artwork", "GET")
                    : "/api/tracks/" + track.id() + "/artwork";
            if (path == null) { complete.complete(null, true); return; }
            request.setUrl(origin + path + key.substring(version));
            if (session != null) session.authorize(request);
            request.setPost(false); request.setHttpMethod("GET");
            request.setReadTimeout(15000); request.setTimeout(15000);
            NetworkManager.getInstance().addToQueue(request);
        });
    }

    public record Group(String artist, String album, int trackCount, String trackId) {}
    public record GroupPage(List<Group> items, int offset, int total) {}
    public void groups(String view, String query, int offset, String artist,
                       Consumer<GroupPage> success, Consumer<String> failure) {
        String path = collection(view, "/api/library/" + view) + "?offset=" + offset + "&limit=" + PAGE_SIZE
                + "&q=" + com.codename1.io.Util.encodeUrl(query);
        if (artist != null) path += "&artist=" + com.codename1.io.Util.encodeUrl(artist);
        json(path, data -> {
            try {
                List<Group> groups = new ArrayList<>();
                for (Object item : (List<?>) data.get("items")) {
                    Map<?, ?> row = (Map<?, ?>) item;
                    if (!resources.containsKey((String) row.get("trackId"))) resources.put((String) row.get("trackId"), (Map<String, Object>) row);
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
        String path = collection("songs", "/api/tracks") + "?q=" + com.codename1.io.Util.encodeUrl(query)
                + "&offset=" + offset + "&limit=" + PAGE_SIZE;
        if (artist != null) path += "&artist=" + com.codename1.io.Util.encodeUrl(artist);
        if (album != null) path += "&album=" + com.codename1.io.Util.encodeUrl(album);
        json(path, data -> {
            try {
                List<Track> tracks = new ArrayList<>();
                for (Object item : (List<?>) data.get("items")) {
                    Map<String, Object> row = (Map<String, Object>) item;
                    String id = (String) row.get("id");
                    if (id == null || id.length() != 64) throw new IllegalArgumentException("Invalid track ID");
                    resources.put(id, row);
                    tracks.add(new Track(id, (String) row.get("title"), (String) row.get("artist"),
                            (String) row.get("album"), ((Number) row.get("durationMs")).intValue()));
                }
                success.accept(new Page(tracks, ((Number) data.get("offset")).intValue(),
                        ((Number) data.get("limit")).intValue(), ((Number) data.get("total")).intValue()));
            } catch (RuntimeException error) { failure.accept("Unexpected catalogue response. Try again."); }
        }, failure);
    }
}
