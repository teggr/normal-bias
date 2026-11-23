package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.components.SpanLabel;
import com.codename1.ui.layouts.*;
import com.codename1.ui.util.UITimer;
import dev.normalbias.client.Track;
import dev.normalbias.client.net.LibraryApi;
import dev.normalbias.client.player.Player;
import dev.normalbias.client.car.CarListening;
import com.codename1.car.Car;

/** A small listening surface; all state changes occur on the Codename One EDT. */
public final class LibraryForm extends Form {
    private final java.util.List<RecordArtwork> rowArtwork = new java.util.ArrayList<>();
    private final LibraryApi api;
    private final Player player;
    private final CarListening car;
    private final TextField search = new TextField("", "Search title, artist or album", 30, TextField.ANY);
    private final Container tracks = new Container(BoxLayout.y());
    private final Container queue = new Container(BoxLayout.y());
    private final Label pageInfo = new Label("Loading library…");
    private final SpanLabel title = new SpanLabel("Nothing playing", "NowTitle");
    private final SpanLabel artist = new SpanLabel("Your library, ready when you are", "Muted");
    private final TextArea status = new TextArea("Choose a track to start listening", 1, 24);
    private final Label time = new Label("0:00", "TimeLabel");
    private final Label remaining = new Label("−0:00", "TimeLabel");
    private final RecordArtwork cover = new RecordArtwork(false);
    private final RecordArtwork metadataCover = new RecordArtwork(true);
    private final RecordArtwork miniCover = new RecordArtwork(true);
    private final FineSeek seek = new FineSeek();
    private final HardwareButton play = new HardwareButton("Play", FontImage.MATERIAL_PLAY_ARROW, true);
    private final Button previous = new Button("Previous");
    private final Button next = new Button("Next");
    private final HardwareButton retry = new HardwareButton("Retry track", FontImage.MATERIAL_REFRESH, true);
    private final Button pageBack = new Button("Back");
    private final Button pageNext = new Button("More");
    private final Tabs tabs = new Tabs();
    private final Container body = new Container(new BorderLayout());
    private final Container playerPanel = new Container(new BorderLayout());
    private final Container miniPlayer = new Container(new BorderLayout());
    private final Button miniTitle = new Button("Now Playing");
    private final Button miniPlay = new Button();
    private final Label miniArtist = new Label("Your music, ready to play", "Muted");
    private final Container header = new Container(BoxLayout.y());
    private final Container searchBar = new Container();
    private final Container pagingBar = new Container();
    private final Button find = new Button("Search");
    private final Button reset = new Button("Reset");
    private final LibraryViewButton[] viewButtons = new LibraryViewButton[3];
    private String libraryView = "songs";
    private String selectedArtist;
    private String selectedAlbum;
    private final Button libraryUp = new Button("Library views");
    private final Container libraryLocationBar;
    private final Label libraryLocation = new Label("All songs", "Muted");
    private boolean compact;
    private boolean layoutReady;
    private int requestVersion;
    private int offset;
    private int total;
    private String query = "";
    private boolean busy;
    private UITimer timer;

    public LibraryForm(LibraryApi api) { this(api, () -> {}); }
    public LibraryForm(LibraryApi api, Runnable logout) {
        super("Normal Bias", new BorderLayout());
        this.api = api;
        getContentPane().setSafeArea(true);
        compact = CN.getDisplayWidth() < CN.convertToPixels(120);
        player = new Player(api, this::refreshPlayer);
        car = new CarListening(api, player);
        Car.setApplication(car);
        getToolbar().setHidden(true);
        getToolbar().setVisible(false);
        icon(find, "Search", FontImage.MATERIAL_SEARCH, "SearchAction");
        icon(reset, "Reset", FontImage.MATERIAL_CLOSE, "SearchAction");
        find.addActionListener(e -> searchLibrary());
        search.addActionListener(e -> searchLibrary());
        reset.addActionListener(e -> { search.setText(""); searchLibrary(); });
        searchBar.setUIID("SearchBar");
        Container brand = BorderLayout.center(new Label("Normal Bias", "Brand"));
        if (api.canLogout()) {
            Button signOut = new Button("Sign out"); signOut.setName("Sign out");
            signOut.addActionListener(event -> logout.run());
            brand.add(BorderLayout.EAST, signOut);
            header.add(brand); header.add(new Label(api.username(), "Muted"));
        } else header.add(brand);
        pageInfo.setUIID("PageInfo");
        pageBack.setUIID("PagingButton");
        pageNext.setUIID("PagingButton");
        header.setUIID("Header");
        add(BorderLayout.NORTH, header);

        tracks.setScrollableY(true);
        tracks.setScrollVisible(false);
        tracks.setUIID("LibraryPanel");
        pageBack.addActionListener(e -> load(Math.max(0, offset - LibraryApi.PAGE_SIZE)));
        pageNext.addActionListener(e -> load(offset + LibraryApi.PAGE_SIZE));
        Container views = new Container(new GridLayout(1, 3));
        String[] names = {"All songs", "Albums", "Artists"};
        String[] values = {"songs", "albums", "artists"};
        for (int i = 0; i < names.length; i++) {
            final String value = values[i];
            LibraryViewButton choice = new LibraryViewButton(names[i]);
            viewButtons[i] = choice;
            choice.setActive(i == 0);
            choice.addActionListener(e -> {
                for (LibraryViewButton button : viewButtons) button.setActive(button == choice);
                libraryView = value; selectedArtist = null; selectedAlbum = null;
                search.setText(""); query = ""; load(0);
            });
            views.add(choice);
        }
        libraryUp.setUIID("PagingButton");
        libraryUp.addActionListener(e -> {
            if (selectedAlbum != null) {
                selectedAlbum = null;
                if (libraryView.equals("albums")) selectedArtist = null;
            }
            else selectedArtist = null;
            search.setText(""); query = ""; load(0);
        });
        libraryLocationBar = BorderLayout.center(libraryLocation).add(BorderLayout.WEST, libraryUp);
        Container library = BorderLayout.center(tracks).add(BorderLayout.SOUTH, pagingBar)
                .add(BorderLayout.NORTH, BoxLayout.encloseY(views, searchBar, libraryLocationBar));
        library.setUIID("LibraryPanel");
        queue.setScrollableY(true);
        queue.setScrollVisible(false);
        Button clear = new Button("Clear queue");
        clear.addActionListener(e -> player.clear());
        Container queuePanel = BorderLayout.center(queue).add(BorderLayout.SOUTH, clear);
        queuePanel.setUIID("LibraryPanel");
        tabs.setUIID("LibraryPanel");
        tabs.getContentPane().setUIID("LibraryPanel");
        tabs.setTabPlacement(Component.TOP);
        tabs.setAnimateTabSelection(false);
        tabs.addSelectionListener((oldIndex, newIndex) -> {
            updatePlayerChrome(newIndex);
            revalidate();
        });
        tabs.addTab("Library", library);
        tabs.addTab("Queue (0)", queuePanel);
        body.add(BorderLayout.CENTER, tabs);
        add(BorderLayout.CENTER, body);

        status.setEditable(false);
        status.setFocusable(false);
        status.setUIID("Status");
        seek.setEditable(true);
        seek.setPreferredH(CN.convertToPixels(5));
        seek.setMinValue(0);
        seek.setMaxValue(100);
        seek.addActionListener(e -> player.seek(seek.getProgress(e)));
        seek.setName("Seek");
        play.addActionListener(e -> player.toggle());
        previous.addActionListener(e -> player.previous());
        next.addActionListener(e -> player.next());
        retry.addActionListener(e -> player.retry());
        HardwareButton showLibrary = new HardwareButton("Library", FontImage.MATERIAL_LIBRARY_MUSIC, true);
        HardwareButton showQueue = new HardwareButton("Queue", FontImage.MATERIAL_QUEUE_MUSIC, true);
        HardwareButton more = new HardwareButton("Open queue", FontImage.MATERIAL_MORE_HORIZ, true);
        more.addActionListener(e -> tabs.setSelectedIndex(1));
        showLibrary.addActionListener(e -> tabs.setSelectedIndex(0));
        showQueue.addActionListener(e -> tabs.setSelectedIndex(1));
        TransportWheel wheel = new TransportWheel(previous, play, next, more, showQueue, retry, showLibrary);
        icon(previous, "Previous", FontImage.MATERIAL_SKIP_PREVIOUS, "WheelAction");
        icon(next, "Next", FontImage.MATERIAL_SKIP_NEXT, "WheelAction");
        cover.setUIID("ArtworkRecess");
        Container metadata = BorderLayout.center(BoxLayout.encloseY(artist, title))
                .add(BorderLayout.WEST, metadataCover);
        metadata.setUIID("MetadataWell");
        Container times = BorderLayout.center(time).add(BorderLayout.EAST, remaining);
        Container timeline = BorderLayout.center(seek).add(BorderLayout.SOUTH, times);
        timeline.setUIID("Timeline");
        HardwareButton back = new HardwareButton("Back to library", FontImage.MATERIAL_ARROW_BACK, false);
        back.setUIID("HeaderHardware");
        back.addActionListener(e -> tabs.setSelectedIndex(0));
        HardwareButton info = new HardwareButton("Player information", FontImage.MATERIAL_INFO_OUTLINE, false);
        info.setUIID("HeaderHardware");
        info.addActionListener(e -> showDetails());
        Container playerHeader = BorderLayout.center(new Label("Now Playing", "PlayerHeading"))
                .add(BorderLayout.WEST, back).add(BorderLayout.EAST, info);
        playerHeader.setUIID("PlayerHeader");
        playerPanel.setUIID("PlayerPanel");
        // Keep navigation outside the scrolling face: native focus/scroll must not
        // move Back/Information above the safe area on shorter phone viewports.
        playerHeader.setPreferredH(CN.convertToPixels(12));
        Container playerContent = new Container(BoxLayout.y());
        playerContent.setUIID("PlayerPanel");
        playerContent.setScrollableY(true);
        playerContent.setScrollVisible(false);
        playerContent.add(new PlayerFace(cover, metadata, timeline, wheel, status));
        playerPanel.add(BorderLayout.NORTH, playerHeader).add(BorderLayout.CENTER, playerContent);
        miniTitle.setUIID("MiniTitle");
        miniTitle.addActionListener(e -> tabs.setSelectedIndex(2));
        icon(miniPlay, "Play", FontImage.MATERIAL_PLAY_ARROW, "MiniPlay");
        miniPlay.addActionListener(e -> player.toggle());
        miniPlayer.setUIID("MiniPlayer");
        Container miniCopy = BoxLayout.encloseY(miniTitle, miniArtist);
        miniCopy.setLeadComponent(miniTitle);
        miniPlayer.add(BorderLayout.WEST, miniCover).add(BorderLayout.CENTER, miniCopy).add(BorderLayout.EAST, miniPlay);
        layoutReady = true;
        applyLayout();
        addSizeChangedListener(e -> {
            boolean narrow = getWidth() < CN.convertToPixels(120);
            if (layoutReady && narrow != compact) {
                compact = narrow;
                applyLayout();
                revalidate();
            } else if (!narrow) {
                playerPanel.setPreferredW(sidebarWidth());
                revalidate();
            }
        });
        timer = new UITimer(() -> {
            player.poll(); refreshProgress(); refreshArtworkViews();
            checkArtwork(false);
        });
        timer.schedule(500, true, this);
        refreshPlayer();
        load(0);
    }

    /** Foreground return rechecks immediately; it preserves the active player and queue. */
    public void resumeArtwork() { checkArtwork(true); refreshArtworkViews(); }
    private void checkArtwork(boolean force) {
        api.checkArtworkRevision(force, changed -> {
            if (changed) { refreshArtworkViews(); player.refreshArtwork(); }
        });
    }
    private void refreshArtworkViews() {
        cover.refresh(); metadataCover.refresh(); miniCover.refresh();
        for (RecordArtwork art : rowArtwork) art.refresh();
    }

    private int sidebarWidth() {
        int available = CN.getDisplayHeight() - header.getPreferredH();
        return Math.min(CN.convertToPixels(62), Math.max(CN.convertToPixels(45), available * 100 / 202));
    }

    private void applyLayout() {
        search.remove();
        find.remove();
        reset.remove();
        searchBar.removeAll();
        searchBar.setLayout(new BorderLayout());
        searchBar.add(BorderLayout.CENTER, search).add(BorderLayout.EAST, BoxLayout.encloseX(find, reset));
        pageInfo.remove();
        pageBack.remove();
        pageNext.remove();
        pagingBar.removeAll();
        pagingBar.setLayout(new BorderLayout());
        pagingBar.add(BorderLayout.CENTER, pageInfo).add(BorderLayout.EAST, BoxLayout.encloseX(pageBack, pageNext));
        if (tabs.getTabCount() > 2) tabs.removeTabAt(2);
        if (playerPanel.getParent() != null) playerPanel.remove();
        if (miniPlayer.getParent() != null) miniPlayer.remove();
        if (compact) {
            playerPanel.setPreferredW(0);
            tabs.addTab("Now Playing", playerPanel);
            add(BorderLayout.SOUTH, miniPlayer);
        } else {
            playerPanel.setPreferredW(sidebarWidth());
            body.add(BorderLayout.EAST, playerPanel);
        }
        updatePlayerChrome(tabs.getSelectedIndex());
    }

    private void updatePlayerChrome(int selectedIndex) {
        searchBar.setHidden(selectedIndex != 0);
        searchBar.setVisible(selectedIndex == 0);
        boolean fullPlayer = compact && selectedIndex == 2;
        header.setHidden(fullPlayer);
        header.setVisible(!fullPlayer);
        miniPlayer.setHidden(fullPlayer);
        miniPlayer.setVisible(!fullPlayer);
        tabs.getTabsContainer().setHidden(fullPlayer);
        tabs.getTabsContainer().setVisible(!fullPlayer);
        getContentPane().setUIID(fullPlayer ? "PlayerPanel" : "LibraryPanel");
    }

    private void searchLibrary() {
        query = search.getText().trim();
        tabs.setSelectedIndex(0);
        load(0);
    }

    private void load(int requestedOffset) {
        int version = ++requestVersion;
        busy = true;
        pageInfo.setText("Loading library…");
        rowArtwork.clear();
        tracks.removeAll();
        tracks.add(new Label("Loading library…", "Muted"));
        boolean grouped = !libraryView.equals("songs") && selectedAlbum == null;
        boolean artistList = libraryView.equals("artists") && selectedArtist == null;
        boolean inGroup = selectedArtist != null || selectedAlbum != null;
        libraryLocationBar.setHidden(!inGroup);
        libraryLocationBar.setVisible(inGroup);
        libraryUp.setHidden(!inGroup);
        libraryUp.setVisible(selectedArtist != null || selectedAlbum != null);
        libraryLocation.setText(selectedAlbum != null ? display(selectedAlbum, "Unknown album")
                : selectedArtist != null ? display(selectedArtist, "Unknown artist")
                : libraryView.equals("songs") ? "All songs" : libraryView.equals("albums") ? "Albums" : "Artists");
        updatePaging();
        revalidate();
        java.util.function.Consumer<String> failure = message -> {
            if (version != requestVersion) return;
            busy = false;
            tracks.removeAll();
            TextArea error = new TextArea(message);
            error.setEditable(false);
            Button reload = new Button("Retry library");
            reload.addActionListener(e -> load(requestedOffset));
            tracks.add(error).add(reload);
            pageInfo.setText("Library unavailable");
            updatePaging(); revalidate();
        };
        if (grouped) {
            api.groups(artistList ? "artists" : "albums", query, requestedOffset, selectedArtist, page -> {
                if (version != requestVersion) return;
                busy = false; offset = page.offset(); total = page.total();
                tracks.removeAll(); rowArtwork.clear();
                if (page.items().isEmpty()) tracks.add(new Label("No " + (artistList ? "artists" : "albums") + " found.", "Muted"));
                for (LibraryApi.Group group : page.items()) tracks.add(groupRow(group, artistList));
                pageInfo.setText(total == 0 ? "0 shown" : (offset + 1) + "–" + (offset + page.items().size()) + " of " + total);
                tracks.scrollRectToVisible(0, 0, 1, 1, tracks); updatePaging(); revalidate();
            }, failure);
            return;
        }
        api.list(query, requestedOffset, selectedArtist, selectedAlbum, page -> {
            if (version != requestVersion) return;
            busy = false;
            offset = page.offset();
            total = page.total();
            tracks.removeAll();
            rowArtwork.clear();
            if (page.items().isEmpty()) {
                tracks.add(new Label(query.isEmpty() ? "Your library is empty. Upload some MP3s to begin." : "No tracks match your search.", "Muted"));
            }
            for (Track track : page.items()) tracks.add(trackRow(track));
            // Refresh all player surfaces after rebuilding the rows; artwork keys use one
            // service revision so late catalogue responses cannot invalidate cover callbacks.
            player.refreshArtwork();
            pageInfo.setText(page.items().isEmpty() ? "0 shown · " + total + " tracks" : (offset + 1) + "–" + (offset + page.items().size()) + " of " + total);
            tracks.scrollRectToVisible(0, 0, 1, 1, tracks);
            updatePaging();
            revalidate();
        }, failure);
    }
    private static String display(String text, String fallback) { return text == null || text.isEmpty() ? fallback : text; }
    private Container groupRow(LibraryApi.Group group, boolean artistList) {
        String name = artistList ? display(group.artist(), "Unknown artist") : display(group.album(), "Unknown album");
        Button open = new Button(name, "TrackTitle");
        open.setAccessibilityText("Open " + name);
        open.getSemantics().setLabel("Open " + name);
        open.addActionListener(e -> {
            selectedArtist = group.artist();
            selectedAlbum = artistList ? null : group.album();
            search.setText(""); query = ""; load(0);
        });
        String subtitle = (artistList ? "" : display(group.artist(), "Unknown artist") + " · ")
                + group.trackCount() + (group.trackCount() == 1 ? " song" : " songs");
        RecordArtwork art = new RecordArtwork(true);
        art.setTrack(new Track(group.trackId(), name, group.artist(), group.album(), 0), api);
        rowArtwork.add(art);
        Container row = BorderLayout.center(BoxLayout.encloseY(open, new Label(subtitle, "Muted")))
                .add(BorderLayout.WEST, art);
        row.setLeadComponent(open); row.setUIID("TrackRow");
        return row;
    }

    private Container trackRow(Track track) {
        Button listen = new Button(track.title(), "TrackTitle");
        listen.getSemantics().setLabel("Play " + track.title());
        listen.setAccessibilityText("Play " + track.title());
        Button enqueue = new Button();
        icon(enqueue, "+ Queue", FontImage.MATERIAL_ADD, "RowAction");
        enqueue.getSemantics().setDescription("Add " + track.title() + " to queue");
        listen.setEnabled(api.canPlay(track)); enqueue.setEnabled(api.canPlay(track));
        listen.addActionListener(e -> player.playTrack(track));
        enqueue.addActionListener(e -> player.enqueue(track));
        Container copy = BoxLayout.encloseY(listen, new Label(track.artist(), "Muted"));
        RecordArtwork art = new RecordArtwork(true);
        art.setTrack(track, api);
        rowArtwork.add(art);
        Container detail = BorderLayout.center(copy).add(BorderLayout.WEST, art);
        detail.setLeadComponent(listen);
        Container row = BorderLayout.center(detail).add(BorderLayout.EAST, enqueue);
        row.setUIID("TrackRow");
        return row;
    }
    private static void icon(Button button, String name, char glyph, String uiid) {
        button.setText(""); button.setUIID(uiid); button.setName(name);
        button.setAccessibilityText(name); button.getSemantics().setLabel(name);
        FontImage.setMaterialIcon(button, glyph, 3.5f);
    }
    private void showDetails() {
        Track current = player.queue.current();
        Dialog.show("Now Playing", current == null ? "Choose a track from your library." :
                current.title() + "\n" + current.description() + "\n\n" + player.status(), "Close", null);
    }
    private void updatePaging() {
        pageBack.setEnabled(!busy && offset > 0);
        pageNext.setEnabled(!busy && offset + LibraryApi.PAGE_SIZE < total);
        pageBack.setHidden(total <= LibraryApi.PAGE_SIZE);
        pageBack.setVisible(total > LibraryApi.PAGE_SIZE);
        pageNext.setHidden(total <= LibraryApi.PAGE_SIZE);
        pageNext.setVisible(total > LibraryApi.PAGE_SIZE);
    }
    private void refreshPlayer() {
        if (car != null) car.refresh();
        Track current = player.queue.current();
        title.setText(current == null ? "Nothing playing" : current.title());
        artist.setText(current == null ? "Your library" : current.artist());
        cover.setTrack(current, api);
        metadataCover.setTrack(current, api);
        miniCover.setTrack(current, api);
        miniArtist.setText(current == null ? "Choose a track" : current.artist());
        status.setText(player.status());
        String state = player.status();
        boolean showStatus = !(state.equals("Playing") || state.equals("Paused") || state.equals("Queue cleared")
                || state.equals("Queue finished") || state.startsWith("Choose a track") || state.startsWith("Added to queue:"));
        status.setHidden(!showStatus);
        status.setVisible(showStatus);
        updatePlayButton();
        miniTitle.setText(current == null ? "Now Playing" : current.title());
        miniPlay.setEnabled(current != null && !player.isLoading());
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
            item.setUIID("QueueRow");
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
        if (!seek.isInteracting()) seek.setProgress(duration > 0 ? (int) Math.min(100, (long) position * 100 / duration) : 0);
        seek.setEnabled(duration > 0 && !player.isLoading());
        time.setText(formatTime(position));
        remaining.setText("−" + formatTime(Math.max(0, duration - position)));
        updatePlayButton();
    }
    private void updatePlayButton() {
        String text = player.isPlaying() ? "Pause" : "Play";
        if (!text.equals(play.getName())) {
            play.setGlyph(text, player.isPlaying() ? FontImage.MATERIAL_PAUSE : FontImage.MATERIAL_PLAY_ARROW);
            icon(miniPlay, text, player.isPlaying() ? FontImage.MATERIAL_PAUSE : FontImage.MATERIAL_PLAY_ARROW, "MiniPlay");
        }
    }
    private static String formatTime(int ms) {
        int seconds = Math.max(0, ms / 1000);
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
    public void disposePlayer() {
        requestVersion++;
        if (timer != null) timer.cancel();
        api.dispose(); rowArtwork.clear(); tracks.removeAll(); queue.removeAll();
        Car.endSession();
        Car.setApplication(null);
        player.dispose();
        refreshPlayer();
    }
}
