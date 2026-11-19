package dev.normalbias.client.net;

import com.codename1.io.ConnectionRequest;
import com.codename1.io.JSONParser;
import com.codename1.io.NetworkManager;
import com.codename1.ui.CN;
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
