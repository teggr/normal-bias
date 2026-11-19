package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.ui.layouts.*;
import com.codename1.ui.util.UITimer;
import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import dev.normalbias.client.player.Player;

/** A small listening surface; all state changes occur on the Codename One EDT. */
public final class LibraryForm extends Form {
    private final LibraryApi api;
    private final Player player;
    private final TextField search = new TextField("", "Search title, artist or album", 30, TextField.ANY);
    private final Container tracks = new Container(BoxLayout.y());
    private final Container queue = new Container(BoxLayout.y());
    private final Label pageInfo = new Label("Loading library…");
    private final Label title = new Label("Nothing playing", "NowTitle");
    private final Label artist = new Label("Your library, ready when you are", "Muted");
    private final TextArea status = new TextArea("Choose a track to start listening", 2, 40);
    private final Label time = new Label("0:00 / 0:00", "Muted");
    private final Slider seek = new Slider();
    private final Button play = new Button("Play");
    private final Button previous = new Button("Previous");
    private final Button next = new Button("Next");
    private final Button retry = new Button("Retry track");
    private final Button pageBack = new Button("Back");
    private final Button pageNext = new Button("More");
    private final Tabs tabs = new Tabs();
    private int requestVersion;
    private int offset;
    private int total;
    private String query = "";
    private boolean busy;
    private boolean seeking;

    public LibraryForm(LibraryApi api) {
        super("Normal Bias", new BorderLayout());
        this.api = api;
        player = new Player(api, this::refreshPlayer);
        getToolbar().setHidden(true);
        getToolbar().setVisible(false);
        Button find = new Button("Search");
        Button reset = new Button("Reset");
        find.addActionListener(e -> { query = search.getText().trim(); load(0); });
        search.addActionListener(e -> { query = search.getText().trim(); load(0); });
        reset.addActionListener(e -> { search.setText(""); query = ""; load(0); });
        Container searchBar = BorderLayout.center(search).add(BorderLayout.EAST, BoxLayout.encloseX(find, reset));
        searchBar.setUIID("SearchBar");
        Container header = BoxLayout.encloseY(new Label("Normal Bias", "Brand"), searchBar);
        header.setUIID("Header");
        add(BorderLayout.NORTH, header);

        tracks.setScrollableY(true);
        pageBack.addActionListener(e -> load(Math.max(0, offset - LibraryApi.PAGE_SIZE)));
        pageNext.addActionListener(e -> load(offset + LibraryApi.PAGE_SIZE));
        Container library = BorderLayout.center(tracks).add(BorderLayout.SOUTH,
                BorderLayout.center(pageInfo).add(BorderLayout.EAST, BoxLayout.encloseX(pageBack, pageNext)));
        queue.setScrollableY(true);
        Button clear = new Button("Clear queue");
        clear.addActionListener(e -> player.clear());
        Container queuePanel = BorderLayout.center(queue).add(BorderLayout.SOUTH, clear);
        tabs.setTabPlacement(Component.TOP);
        tabs.addTab("Library", library);
        tabs.addTab("Queue (0)", queuePanel);
        add(BorderLayout.CENTER, tabs);

        status.setEditable(false);
        status.setFocusable(false);
        status.setUIID("Status");
        seek.setEditable(true);
        seek.addPointerPressedListener(e -> seeking = true);
        seek.addPointerReleasedListener(e -> seeking = false);
        seek.setMinValue(0);
        seek.setMaxValue(100);
        seek.addActionListener(e -> player.seek(seek.getProgress()));
        seek.setName("Seek");
        play.addActionListener(e -> player.toggle());
        previous.addActionListener(e -> player.previous());
        next.addActionListener(e -> player.next());
        retry.addActionListener(e -> player.retry());
        Container controls = BoxLayout.encloseX(previous, play, next, retry);
        Container footer = BoxLayout.encloseY(title, artist, BorderLayout.center(seek).add(BorderLayout.EAST, time), controls, status);
        footer.setUIID("PlayerPanel");
        add(BorderLayout.SOUTH, footer);
        new UITimer(() -> { player.poll(); refreshProgress(); }).schedule(500, true, this);
        refreshPlayer();
        load(0);
    }

    private void load(int requestedOffset) {
        int version = ++requestVersion;
        busy = true;
        pageInfo.setText("Loading library…");
        tracks.removeAll();
        tracks.add(new Label("Loading tracks…", "Muted"));
        updatePaging();
        revalidate();
        api.list(query, requestedOffset, page -> {
            if (version != requestVersion) return;
            busy = false;
            offset = page.offset();
            total = page.total();
            tracks.removeAll();
            if (page.items().isEmpty()) {
                tracks.add(new Label(query.isEmpty() ? "Your library is empty. Upload some MP3s to begin." : "No tracks match your search.", "Muted"));
            }
            for (Track track : page.items()) tracks.add(trackRow(track));
            pageInfo.setText(page.items().isEmpty() ? "0 shown · " + total + " tracks" : (offset + 1) + "–" + (offset + page.items().size()) + " of " + total);
            updatePaging();
            revalidate();
        }, message -> {
            if (version != requestVersion) return;
            busy = false;
            tracks.removeAll();
            TextArea error = new TextArea(message);
            error.setEditable(false);
            Button reload = new Button("Retry library");
            reload.addActionListener(e -> load(requestedOffset));
            tracks.add(error).add(reload);
            pageInfo.setText("Library unavailable");
            updatePaging();
            revalidate();
        });
    }
    private Container trackRow(Track track) {
        Button listen = new Button("Play");
        Button enqueue = new Button("+ Queue");
        listen.addActionListener(e -> player.playTrack(track));
        enqueue.addActionListener(e -> player.enqueue(track));
        Container detail = BoxLayout.encloseY(new Label(track.title(), "TrackTitle"), new Label(track.description(), "Muted"));
        Container row = BorderLayout.center(detail).add(BorderLayout.EAST, BoxLayout.encloseX(
                new Label(formatTime(track.durationMs()), "Muted"), listen, enqueue));
        row.setUIID("TrackRow");
        return row;
    }
    private void updatePaging() {
        pageBack.setEnabled(!busy && offset > 0);
        pageNext.setEnabled(!busy && offset + LibraryApi.PAGE_SIZE < total);
    }
    private void refreshPlayer() {
        Track current = player.queue.current();
        title.setText(current == null ? "Nothing playing" : current.title());
        artist.setText(current == null ? "Your library, ready when you are" : current.description());
        status.setText(player.status());
        play.setText(player.isPlaying() ? "Pause" : "Play");
        play.setEnabled(current != null && !player.isLoading());
        retry.setEnabled(current != null);
        previous.setEnabled(player.queue.index() > 0);
        next.setEnabled(player.queue.index() + 1 < player.queue.size());
        queue.removeAll();
        if (player.queue.size() == 0) queue.add(new Label("Add tracks from the library to build your queue.", "Muted"));
        for (int i = 0; i < player.queue.size(); i++) {
            final int index = i;
            Track track = player.queue.get(i);
            Button item = new Button((i == player.queue.index() ? "▶ " : "") + (i + 1) + ". " + track.title());
            item.addActionListener(e -> player.select(index));
            queue.add(item);
        }
        tabs.setTabTitle("Queue (" + player.queue.size() + ")", null, 1);
        refreshProgress();
        revalidate();
    }
    private void refreshProgress() {
        int duration = player.duration();
        int position = player.time();
        if (!seeking) seek.setProgress(duration > 0 ? (int) Math.min(100, (long) position * 100 / duration) : 0);
        seek.setEnabled(duration > 0 && !player.isLoading());
        time.setText(formatTime(position) + " / " + formatTime(duration));
        String text = player.isPlaying() ? "Pause" : "Play";
        if (!text.equals(play.getText())) play.setText(text);
    }
    private static String formatTime(int ms) {
        int seconds = Math.max(0, ms / 1000);
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
    public void disposePlayer() { player.release(); }
}
