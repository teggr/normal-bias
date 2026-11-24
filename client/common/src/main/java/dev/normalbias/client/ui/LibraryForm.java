package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.components.SpanLabel;
import com.codename1.ui.layouts.*;
import com.codename1.ui.util.UITimer;
import com.codename1.ui.animations.CommonTransitions;
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
    private final Container artworkSlot = new Container(new BorderLayout());
    private final TrackInformation trackInformation = new TrackInformation();
    private final HardwareButton informationToggle = new HardwareButton("Player information", FontImage.MATERIAL_INFO_OUTLINE, true);
    private boolean informationVisible, informationTransition;
    private final RecordArtwork metadataCover = new RecordArtwork(true);
    private final RecordArtwork miniCover = new RecordArtwork(true);
    private final FineSeek seek = new FineSeek();
    private final HardwareButton play = new HardwareButton("Play", FontImage.MATERIAL_PLAY_ARROW, true);
    private final Button previous = new Button("Previous");
    private final Button next = new Button("Next");
    private final HardwareButton shuffle = new HardwareButton("Shuffle queue", FontImage.MATERIAL_SHUFFLE, true);
    private final HardwareButton queueShuffle = new HardwareButton("Shuffle queue", FontImage.MATERIAL_SHUFFLE, true);
    private final HardwareButton retry = new HardwareButton("Restart track", FontImage.MATERIAL_REPLAY, true);
    private final Button pageBack = new Button("Back");
    private final Button pageNext = new Button("More");
    private enum View { LIBRARY, NOW_PLAYING, QUEUE, ACCOUNT }
    private View view = View.LIBRARY;
    private View accountOrigin = View.LIBRARY;
    private final Container accountPanel = new Container(BoxLayout.y());
    private final Container library = new Container(new BorderLayout());
    private final Container queuePanel = new Container(new BorderLayout());
    private final Label queueCount = new Label("0 tracks", "Muted");
    private final java.util.List<RecordArtwork> queueArtwork = new java.util.ArrayList<>();
    private String queueIdentity = "";
    private final Runnable logout;
    private final Runnable password;
    private PlayerFace playerFace;
    private SpanLabel accountUsername;
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
    private final HardwareButton searchToggle = new HardwareButton("Show search", FontImage.MATERIAL_SEARCH, true);
    private boolean searchVisible;
    private final LibraryViewButton[] viewButtons = new LibraryViewButton[3];
    private String libraryView = "songs";
    private String selectedArtist;
    private String selectedAlbum;
    private final Button libraryUp = new Button("Library views");
    private final Container libraryLocationBar;
    private final Label libraryLocation = new Label("All songs", "Muted");
    private int requestVersion;
    private int offset;
    private int total;
    private String query = "";
    private boolean busy;
    private UITimer timer;

    public LibraryForm(LibraryApi api) { this(api, () -> {}); }
    public LibraryForm(LibraryApi api, Runnable logout) { this(api, logout, null); }
    public LibraryForm(LibraryApi api, Runnable logout, Runnable password) {
        super("Normal Bias", new BorderLayout());
        this.api = api;
        this.logout = logout;
        this.password = password;
        getContentPane().setSafeArea(true);
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
        search.setName("Library search");
        searchToggle.setPreferredW(CN.convertToPixels(9));
        searchToggle.setPreferredH(CN.convertToPixels(9));
        searchToggle.addActionListener(e -> toggleSearch());
        updateSearchChrome();
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
        library.add(BorderLayout.CENTER, tracks).add(BorderLayout.SOUTH, pagingBar)
                .add(BorderLayout.NORTH, BoxLayout.encloseY(BorderLayout.center(views).add(BorderLayout.EAST, searchToggle), searchBar, libraryLocationBar));
        library.setUIID("LibraryPanel");
        queue.setScrollableY(true);
        queue.setScrollVisible(false);
        Button clear = new Button("Clear queue");
        clear.addActionListener(e -> player.clear());
        clear.setName("Clear queue");
        Container queueSummary = BorderLayout.center(queueCount).add(BorderLayout.EAST, clear);
        queueSummary.setUIID("QueueSummary");
        queuePanel.add(BorderLayout.CENTER, queue).add(BorderLayout.NORTH, queueSummary);
        queuePanel.setUIID("LibraryPanel");
        accountPanel.setUIID("AccountPanel");
        accountPanel.setScrollableY(true);
        accountPanel.setScrollVisible(false);
        if (api.canLogout()) {
            accountUsername = new SpanLabel(api.username(), "NowTitle");
            accountUsername.setName("Account username");
            accountUsername.getSemantics().setDescription("Read-only username");
            Container identity = BoxLayout.encloseY(new Label("Username", "Muted"), accountUsername);
            identity.setUIID("MetadataWell");
            Container identityRow = BorderLayout.center(identity);
            identityRow.setUIID("AccountIdentity");
            accountPanel.add(identityRow);
        }
        if (password != null) accountPanel.add(accountAction("Change password", password));
        accountPanel.add(accountAction("Sign out", logout));
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
        retry.addActionListener(e -> { if (player.hasFailed()) player.retry(); else player.restart(); });
        HardwareButton showQueue = new HardwareButton("Queue", FontImage.MATERIAL_QUEUE_MUSIC, true);
        informationToggle.addActionListener(e -> toggleInformation());
        shuffle.addActionListener(e -> player.shuffle());
        queueShuffle.addActionListener(e -> player.shuffle());
        showQueue.addActionListener(e -> navigate(View.QUEUE));
        TransportWheel wheel = new TransportWheel(previous, play, next, informationToggle, showQueue, retry, shuffle);
        icon(previous, "Previous", FontImage.MATERIAL_SKIP_PREVIOUS, "WheelAction");
        icon(next, "Next", FontImage.MATERIAL_SKIP_NEXT, "WheelAction");
        cover.setUIID("ArtworkRecess");
        artworkSlot.setUIID("ArtworkRecess");
        artworkSlot.add(BorderLayout.CENTER, cover);
        Container metadata = BorderLayout.center(BoxLayout.encloseY(artist, title))
                .add(BorderLayout.WEST, metadataCover);
        metadata.setUIID("MetadataWell");
        Container times = BorderLayout.center(time).add(BorderLayout.EAST, remaining);
        Container timeline = BorderLayout.center(seek).add(BorderLayout.SOUTH, times);
        timeline.setUIID("Timeline");
        playerPanel.setUIID("PlayerPanel");
        // Preserve the actual player face/wheel, bounded and centred at all widths.
        Container playerContent = new Container(new FlowLayout(Component.CENTER, Component.TOP));
        playerContent.setUIID("PlayerPanel");
        playerContent.setScrollableY(true);
        playerContent.setScrollVisible(false);
        playerFace = new PlayerFace(artworkSlot, metadata, timeline, wheel, status);
        sizeContent();
        playerContent.add(playerFace);
        playerPanel.add(BorderLayout.CENTER, playerContent);
        miniTitle.setUIID("MiniTitle");
        miniTitle.setName("Open Now Playing");
        miniTitle.setAccessibilityText("Open Now Playing");
        miniTitle.getSemantics().setLabel("Open Now Playing");
        miniTitle.addActionListener(e -> navigate(View.NOW_PLAYING));
        icon(miniPlay, "Play", FontImage.MATERIAL_PLAY_ARROW, "MiniPlay");
        miniPlay.addActionListener(e -> player.toggle());
        miniPlayer.setUIID("MiniPlayer");
        Container miniCopy = BoxLayout.encloseY(miniTitle, miniArtist);
        miniCopy.setLeadComponent(miniTitle);
        miniPlayer.add(BorderLayout.WEST, miniCover).add(BorderLayout.CENTER, miniCopy).add(BorderLayout.EAST, miniPlay);
        searchBar.setLayout(new BorderLayout());
        searchBar.add(BorderLayout.CENTER, search).add(BorderLayout.EAST, BoxLayout.encloseX(find, reset));
        pagingBar.setLayout(new BorderLayout());
        pagingBar.add(BorderLayout.CENTER, pageInfo).add(BorderLayout.EAST, BoxLayout.encloseX(pageBack, pageNext));
        navigate(View.LIBRARY);
        addSizeChangedListener(e -> { sizeContent(); revalidate(); });
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
        for (RecordArtwork art : queueArtwork) art.refresh();
    }

    private void sizeContent() {
        int available = getWidth() > 0 ? getWidth() : CN.getDisplayWidth();
        // SpanLabel retains its measured width; bound it again when the viewport changes.
        if (accountUsername != null) accountUsername.setPreferredW(Math.max(1, available - CN.convertToPixels(12)));
        accountPanel.setPreferredW(available);
        accountPanel.setShouldCalcPreferredSize(true);
        int width = Math.min(available, CN.convertToPixels(62));
        trackInformation.fitWidth(width);
        playerFace.setWidth(width);
        playerFace.setPreferredW(width);
        playerFace.setShouldCalcPreferredSize(true);
    }

    private HardwareButton navigation(String name, char glyph, Runnable action) {
        HardwareButton button = new HardwareButton(name, glyph, true);
        button.setPreferredW(CN.convertToPixels(9));
        button.setPreferredH(CN.convertToPixels(9));
        if (name.equals("Now Playing")) button.setIcon(NowPlayingIcon.create());
        button.addActionListener(e -> action.run());
        return button;
    }

    private void navigate(View target) {
        if (view == target && body.getComponentCount() > 0) return;
        view = target;
        body.removeAll();
        Container content = target == View.LIBRARY ? library : target == View.QUEUE ? queuePanel : target == View.ACCOUNT ? accountPanel : playerPanel;
        content.remove();
        body.add(BorderLayout.CENTER, content);
        miniPlayer.remove();
        if (target == View.LIBRARY || target == View.QUEUE) add(BorderLayout.SOUTH, miniPlayer);
        header.removeAll();
        Container actions = new Container(BoxLayout.x());
        if (target == View.LIBRARY) {
            actions.add(navigation("Now Playing", FontImage.MATERIAL_ALBUM, () -> navigate(View.NOW_PLAYING)));
        } else if (target == View.QUEUE) {
            queueShuffle.remove();
            actions.add(queueShuffle);
        } else if (target == View.NOW_PLAYING) {
            actions.add(navigation("Library", FontImage.MATERIAL_LIBRARY_MUSIC, () -> navigate(View.LIBRARY)));
        }
        if ((target == View.LIBRARY || target == View.NOW_PLAYING) && api.canLogout()) actions.add(navigation("Profile", FontImage.MATERIAL_PERSON_OUTLINE, this::showProfile));
        Label heading = new Label(target == View.LIBRARY ? "Library" : target == View.QUEUE ? "Queue" : target == View.ACCOUNT ? "Account" : "Now Playing", "ScreenHeading");
        Container bar = BorderLayout.center(heading).add(BorderLayout.EAST, actions);
        if (target == View.QUEUE)
            bar.add(BorderLayout.WEST, navigation("Back to Now Playing", FontImage.MATERIAL_ARROW_BACK, () -> navigate(View.NOW_PLAYING)));
        header.add(bar);
        View returnTo = target == View.ACCOUNT ? accountOrigin : target == View.QUEUE ? View.NOW_PLAYING : View.LIBRARY;
        String backName = "Back to " + (returnTo == View.LIBRARY ? "Library" : returnTo == View.QUEUE ? "Queue" : "Now Playing");
        if (target == View.ACCOUNT)
            bar.add(BorderLayout.WEST, navigation(backName, FontImage.MATERIAL_ARROW_BACK, () -> navigate(returnTo)));
        setBackCommand(target == View.LIBRARY ? null : new Command(backName) {
            @Override public void actionPerformed(com.codename1.ui.events.ActionEvent e) { navigate(returnTo); }
        });
        getContentPane().setUIID(target == View.NOW_PLAYING ? "PlayerPanel" : "LibraryPanel");
        revalidate();
    }

    private Button accountAction(String name, Runnable action) {
        Button button = new Button(name, "AccountAction");
        button.setName(name);
        button.setPreferredH(CN.convertToPixels(10));
        button.addActionListener(e -> action.run());
        return button;
    }

    private void showProfile() {
        if (!api.canLogout() || view == View.ACCOUNT) return;
        accountOrigin = view;
        navigate(View.ACCOUNT);
    }

    private void toggleSearch() {
        if (searchVisible) {
            search.stopEditing(() -> {
                searchVisible = false;
                updateSearchChrome();
                searchToggle.requestFocus();
                revalidate();
            });
        } else {
            searchVisible = true;
            updateSearchChrome();
            revalidate();
            search.requestFocus();
            search.startEditingAsync();
        }
    }

    private void updateSearchChrome() {
        searchBar.setHidden(!searchVisible);
        searchBar.setVisible(searchVisible);
        searchToggle.setUIID(searchVisible || !query.isEmpty() ? "SearchToggleActive" : "SearchToggle");
        searchToggle.setGlyph(searchVisible ? "Hide search" : "Show search", FontImage.MATERIAL_SEARCH);
        searchToggle.getSemantics().setDescription(query.isEmpty() ? "Toggle library search" : "Library filtered by " + query);
    }

    private void searchLibrary() {
        query = search.getText().trim();
        navigate(View.LIBRARY);
        load(0);
    }

    private void load(int requestedOffset) {
        updateSearchChrome();
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
    private void toggleInformation() {
        if (informationTransition) return;
        informationTransition = true;
        informationVisible = !informationVisible;
        trackInformation.update(player.queue.current(), player.duration(), player.status());
        informationToggle.setGlyph(informationVisible ? "Show artwork" : "Player information",
                informationVisible ? FontImage.MATERIAL_ALBUM : FontImage.MATERIAL_INFO_OUTLINE);
        Component before = informationVisible ? cover : trackInformation;
        Component after = informationVisible ? trackInformation : cover;
        artworkSlot.replace(before, after, CommonTransitions.createSlide(
                CommonTransitions.SLIDE_HORIZONTAL, !informationVisible, 180), () -> CN.callSerially(() -> {
                    informationTransition = false;
                    revalidate();
                }), 0);
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
        trackInformation.update(current, player.duration(), player.status());
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
        retry.setGlyph(player.hasFailed() ? "Retry track" : "Restart track",
                player.hasFailed() ? FontImage.MATERIAL_REFRESH : FontImage.MATERIAL_REPLAY);
        retry.setEnabled(current != null && !player.isLoading());
        previous.setEnabled(player.queue.index() > 0);
        next.setEnabled(player.queue.index() + 1 < player.queue.size());
        shuffle.setEnabled(player.queue.size() > 1);
        queueShuffle.setEnabled(player.queue.size() > 1);
        queueCount.setText(player.queue.size() + (player.queue.size() == 1 ? " track" : " tracks"));
        StringBuilder identity = new StringBuilder().append(player.queue.index());
        for (int i = 0; i < player.queue.size(); i++) identity.append(':').append(player.queue.get(i).id());
        if (!identity.toString().equals(queueIdentity)) {
            queueIdentity = identity.toString();
            queue.removeAll(); queueArtwork.clear();
            if (player.queue.size() == 0) queue.add(new SpanLabel("Add tracks from the library to build your queue.", "EmptyQueue"));
            for (int i = 0; i < player.queue.size(); i++) {
                final int index = i;
                Track track = player.queue.get(i);
                boolean currentTrack = i == player.queue.index();
                Button item = new Button(track.title(), currentTrack ? "QueueCurrentTitle" : "TrackTitle");
                item.setName("Play queued " + track.title());
                item.setAccessibilityText("Play queued " + track.title());
                item.getSemantics().setLabel("Play queued " + track.title());
                item.addActionListener(e -> player.select(index));
                RecordArtwork art = new RecordArtwork(true);
                art.setTrack(track, api); queueArtwork.add(art);
                Container row = BorderLayout.center(BoxLayout.encloseY(item, new Label(track.artist(), "Muted")))
                        .add(BorderLayout.WEST, art);
                if (currentTrack) row.add(BorderLayout.EAST, new Label("▶", "QueueIndicator"));
                row.setLeadComponent(item); row.setUIID("TrackRow");
                queue.add(row);
            }
        }
        refreshProgress();
        if (!informationTransition) revalidate();
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
