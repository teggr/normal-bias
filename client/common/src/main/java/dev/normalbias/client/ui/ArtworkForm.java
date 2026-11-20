package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.components.SpanLabel;
import com.codename1.ui.layouts.*;
import com.codename1.ui.util.UITimer;
import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import java.util.*;
import java.util.List;

/** Matching never owns or replaces the player. Returning preserves its queue and media. */
final class ArtworkForm extends Form {
    private final LibraryApi api;
    private final Track seed;
    private final Form parent;
    private final Runnable accepted;
    private final Runnable pollPlayer;
    private final Container content = new Container(BoxLayout.y());
    private final SpanLabel status = new SpanLabel("Loading suggested tracks…");
    private final Map<String, CheckBox> selected = new LinkedHashMap<>();
    private String job, revision;
    private int count;
    private int generation;

    ArtworkForm(LibraryApi api, Track seed, Form parent, Runnable accepted, Runnable pollPlayer) {
        super("Find album artwork", new BorderLayout());
        this.api = api; this.seed = seed; this.parent = parent; this.accepted = accepted; this.pollPlayer = pollPlayer;
        new UITimer(pollPlayer).schedule(500, true, this);
        getToolbar().setHidden(true); getToolbar().setVisible(false);
        Button back = new Button("Back to library", "PagingButton");
        back.addActionListener(e -> { generation++; parent.showBack(); });
        Container heading = BorderLayout.center(new Label("Find album artwork", "PlayerHeading")).add(BorderLayout.WEST, back);
        Container header = BoxLayout.encloseY(heading, status); header.setUIID("Header");
        content.setScrollableY(true);
        add(BorderLayout.NORTH, header); add(BorderLayout.CENTER, content);
        suggestions(0);
    }
    private void suggestions(int offset) {
        api.json("/api/album-artwork/suggestions/" + seed.id() + "?offset=" + offset + "&limit=50", null, result -> {
            status.setText("Suggested album: " + seed.album());
            if (offset == 0) {
                content.add(new SpanLabel((String) result.get("warning")));
                content.add(new SpanLabel("The accepted cover fills missing artwork on the checked tracks. You can leave this list as it is."));
            }
            for (Object item : (List<?>) result.get("items")) {
                Map<?, ?> track = (Map<?, ?>) item;
                CheckBox check = new CheckBox((String) track.get("title"));
                check.setSelected(true);
                selected.put((String) track.get("id"), check); content.add(check);
            }
            int total = ((Number) result.get("total")).intValue();
            if (offset + 50 < total && offset + 50 < 200) {
                suggestions(offset + 50);
            } else {
                if (total > 200) content.add(new SpanLabel("At most 200 tracks per lookup. Narrow the tags before matching larger sets."));
                Button find = new Button("Find candidate releases"); find.addActionListener(e -> start()); content.add(find);
            }
            revalidate();
        }, this::fail);
    }
    private void fail(String message) {
        status.setText(message);
        Button retry = new Button("Start fresh match");
        retry.addActionListener(e -> { generation++; new ArtworkForm(api, seed, parent, accepted, pollPlayer).show(); });
        content.add(retry); revalidate();
    }
    private void start() {
        StringBuilder ids = new StringBuilder(); count = 0;
        for (Map.Entry<String, CheckBox> entry : selected.entrySet()) if (entry.getValue().isSelected()) {
            if (count++ > 0) ids.append(','); ids.append('"').append(entry.getKey()).append('"');
        }
        if (count == 0) { status.setText("Choose at least one track."); revalidate(); return; }
        content.removeAll(); status.setText("Looking up release editions…"); revalidate();
        api.json("/api/album-artwork/matches", "{\"trackIds\":[" + ids + "]}", result -> {
            job = (String) result.get("id"); revision = (String) result.get("revision"); poll(generation);
        }, this::fail);
    }
    private void poll(int expected) {
        if (expected != generation) return;
        api.json("/api/album-artwork/matches/" + job, null, result -> {
            if (expected != generation) return;
            status.setText((String) result.get("message"));
            if ("LOOKING_UP".equals(result.get("state"))) new UITimer(() -> poll(expected)).schedule(1200, false, this);
            else if ("READY".equals(result.get("state"))) candidates(0);
            else fail((String) result.get("message"));
            revalidate();
        }, this::fail);
    }
    private void candidates(int offset) {
        content.removeAll();
        api.json("/api/album-artwork/matches/" + job + "/candidates?offset=" + offset + "&limit=5", null, result -> {
            for (Object item : (List<?>) result.get("items")) {
                Map<?, ?> c = (Map<?, ?>) item;
                String id = (String) c.get("id");
                int trackCount = ((Number) c.get("trackCount")).intValue();
                content.add(new SpanLabel(c.get("title") + " · " + c.get("artist") + "\n" + c.get("date") + " · "
                        + c.get("country") + " · " + c.get("edition") + "\nRelease tracks: " + trackCount + "; selected: " + count
                        + (trackCount == count ? " (count agrees; edition unverified)" : " (count differs or unknown)")));
                Button link = new Button("View release on MusicBrainz");
                link.addActionListener(e -> CN.execute((String) c.get("providerUrl"))); content.add(link);
                Label image = new Label("Cover not loaded"); content.add(image);
                Button preview = new Button("Preview cover");
                Button accept = new Button("Accept cover for " + count + " tracks"); accept.setEnabled(false);
                preview.addActionListener(e -> {
                    preview.setEnabled(false); image.setText("Loading cover…"); revalidate();
                    api.preview("/api/album-artwork/matches/" + job + "/candidates/" + id + "/preview", cover -> {
                        if (cover == null) { image.setText("No usable cover or provider unavailable"); preview.setEnabled(true); }
                        else { image.setText(""); int size = CN.convertToPixels(38);
                            float scale = (float) size / Math.max(cover.getWidth(), cover.getHeight());
                            image.setIcon(cover.scaled(Math.max(1, (int) (cover.getWidth() * scale)), Math.max(1, (int) (cover.getHeight() * scale)))); accept.setEnabled(true); }
                        revalidate();
                    });
                });
                accept.addActionListener(e -> {
                    accept.setEnabled(false); status.setText("Saving accepted cover…"); revalidate();
                    api.json("/api/album-artwork/matches/" + job + "/accept",
                            "{\"candidateId\":\"" + id + "\",\"revision\":\"" + revision + "\"}", saved -> {
                                List<String> ids = new ArrayList<>(); for (Object value : (List<?>) saved.get("trackIds")) ids.add((String) value);
                                api.invalidateArtwork(ids, (String) saved.get("revision"));
                                generation++; accepted.run(); content.removeAll();
                                status.setText("Cover accepted. Embedded images preserved. Playback and queue continue.");
                                Button done = new Button("Return to library"); done.addActionListener(event -> parent.showBack());
                                content.add(done); revalidate();
                            }, this::fail);
                });
                content.add(preview).add(accept);
            }
            int total = ((Number) result.get("total")).intValue();
            if (offset > 0) { Button back = new Button("Previous candidates"); back.addActionListener(e -> candidates(offset - 5)); content.add(back); }
            if (offset + 5 < total) { Button more = new Button("More candidates"); more.addActionListener(e -> candidates(offset + 5)); content.add(more); }
            revalidate();
        }, this::fail);
    }
}
