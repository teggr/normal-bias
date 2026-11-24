package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.ui.layouts.BoxLayout;
import dev.normalbias.client.Track;

/** Read-only current-track details occupying the player's existing artwork recess. */
final class TrackInformation extends Container {
    private String currentId;
    private final Container fields = new Container(BoxLayout.y());
    private final TextArea empty = text("Choose a track from your library.", "Muted");
    private final TextArea title = field("Title");
    private final TextArea artist = field("Artist");
    private final TextArea album = field("Album");
    private final TextArea duration = field("Duration");
    private final TextArea playback = field("Playback");

    TrackInformation() {
        super(BoxLayout.y());
        setUIID("TrackInformation");
        setScrollableY(true);
        setScrollVisible(false);
        add(new Label("Track information", "NowTitle"));
        add(empty); add(fields);
    }

    private static TextArea text(String value, String uiid) {
        TextArea text = new TextArea(value, 1, 1);
        text.setUIID(uiid);
        text.setEditable(false);
        text.setFocusable(false);
        text.setGrowByContent(true);
        return text;
    }

    private TextArea field(String caption) {
        TextArea value = text("", "InformationValue");
        fields.add(new Label(caption, "Muted")).add(value);
        return value;
    }

    void update(Track track, int durationMs, String state) {
        String id = track == null ? null : track.id();
        if (id == null ? currentId != null : !id.equals(currentId)) {
            currentId = id;
            scrollRectToVisible(0, 0, 1, 1, this);
        }
        empty.setHidden(track != null); empty.setVisible(track == null);
        fields.setHidden(track == null); fields.setVisible(track != null);
        if (track == null) return;
        title.setText(value(track.title(), "Untitled track"));
        artist.setText(value(track.artist(), "Unknown artist"));
        album.setText(value(track.album(), "Unknown album"));
        int seconds = Math.max(0, durationMs > 0 ? durationMs : track.durationMs()) / 1000;
        duration.setText(seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60);
        playback.setText(state);
    }

    void fitWidth(int width) {
        int available = Math.max(1, width - getStyle().getHorizontalPadding());
        empty.setWidth(available);
        empty.setShouldCalcPreferredSize(true);
        for (TextArea label : new TextArea[]{title, artist, album, duration, playback}) {
            label.setWidth(available);
            label.setShouldCalcPreferredSize(true);
        }
        setPreferredW(width);
        setShouldCalcPreferredSize(true);
    }

    private static String value(String text, String fallback) { return text == null || text.isEmpty() ? fallback : text; }
}
