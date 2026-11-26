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
    private final LibraryApi api;
    private final Player player;
    private final CarListening car;
    private final TextField search = new TextField("", "Search title, artist or album", 30, TextField.ANY);
    private final LibraryCollection collection;
    private final java.util.List<LibraryState> libraryHistory = new java.util.ArrayList<>();
    private record LibraryState(String artist, String album, String query, String editor, LibraryCollection.Anchor anchor, LibraryApi.Group albumGroup, LibraryApi.Group artistGroup, boolean searchVisible) {}
    private final Container queue = new Container(BoxLayout.y());
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
    private final Container wideShell = new Container(new BorderLayout());
    private final Container wideLibrary = new Container(new BorderLayout());
    private final Container widePlayer = new Container(new BorderLayout());
    private final Container wideQueue = new Container(new BorderLayout());
    private final Container wideQueueActions = new Container(BoxLayout.x());
    private boolean wide;
    private final HardwareButton showQueue = new HardwareButton("Queue", FontImage.MATERIAL_QUEUE_MUSIC, true);
    private final Button miniTitle = new Button("Now Playing") {
        @Override public void paintBackground(Graphics g) {
            super.paintBackground(g);
            // Keep keyboard focus visible without framing the metadata on touch.
            if (hasFocus() && !Display.getInstance().isTouchScreenDevice()) {
                g.setColor(0xf3f3f1);
                g.drawLine(getX(), getY() + getHeight() - 1, getX() + getWidth() - 1, getY() + getHeight() - 1);
            }
        }
    };
    private final HardwareButton miniPlay = new HardwareButton("Play", FontImage.MATERIAL_PLAY_ARROW, true);
    private final HardwareButton miniNext = new HardwareButton("Next track", FontImage.MATERIAL_SKIP_NEXT, true);
    private final Label miniArtist = new Label("Your music, ready to play", "Muted");
    private final Container header = new Container(BoxLayout.y());
    private final Container searchBar = new Container();
    private final Button find = new Button("Search");
    private final HardwareButton searchToggle = new HardwareButton("Show search", FontImage.MATERIAL_SEARCH, true);
    private boolean searchVisible;
    private final LibraryViewButton[] viewButtons = new LibraryViewButton[3];
    private final Container libraryViews = new Container(new GridLayout(1, 3));
    private String libraryView = "songs";
    private String selectedArtist;
    private String selectedAlbum;
    private LibraryApi.Group selectedAlbumGroup;
    private LibraryApi.Group selectedArtistGroup;
    private String selectedAlbumContents;
    private final HardwareButton queueAlbum = new HardwareButton("Queue album", FontImage.MATERIAL_ADD, true);
    private final SpanLabel albumMessage = new SpanLabel("", "AlbumMessage");
    private int albumRequest;
    private int albumNavigation;
    private Button pendingAlbumButton;
    private final SpanLabel albumNavigationMessage = new SpanLabel("", "AlbumMessage");
    private boolean albumQueueLoading;
    private final RecordArtwork albumCover = new RecordArtwork(false, true);
    private final SpanLabel albumTitle = new SpanLabel("", "AlbumHeading");
    private final SpanLabel albumArtist = new SpanLabel("", "Muted");
    private final Label albumCount = new Label("", "Muted");
    private final Container albumSummary = new Container(new BorderLayout());
    private final LibraryViewButton libraryUp = new LibraryViewButton("");
    private final Container libraryCategories = new Container(new BorderLayout());
    private final Container libraryBack = new Container(new BorderLayout());
    private final Container libraryLocationBar;
    private final RecordArtwork artistCover = new RecordArtwork(false, true);
    private final SpanLabel artistHeading = new SpanLabel("", "ArtistHeading");
    private final Label artistCount = new Label("", "Muted");
    private final Container artistSummary = new Container(new BorderLayout());
    private String query = "";
    private UITimer timer;

    public LibraryForm(LibraryApi api) { this(api, () -> {}); }
    public LibraryForm(LibraryApi api, Runnable logout) { this(api, logout, null); }
    public LibraryForm(LibraryApi api, Runnable logout, Runnable password) {
        super("Normal Bias", new BorderLayout());
        this.api = api;
        collection = new LibraryCollection(api, item -> item.track() != null ? trackRow(item.track()) : groupRow(item));
        this.logout = logout;
        this.password = password;
        getContentPane().setSafeArea(true);
        getContentPane().setScrollableX(false);
        player = new Player(api, this::refreshPlayer);
        car = new CarListening(api, player);
        Car.setApplication(car);
        getToolbar().setHidden(true);
        getToolbar().setVisible(false);
        icon(find, "Search", FontImage.MATERIAL_SEARCH, "SearchAction");
        find.addActionListener(e -> searchLibrary());
        search.addActionListener(e -> searchLibrary());
        searchBar.setUIID("SearchBar");
        search.setName("Library search");
        searchToggle.setPreferredW(CN.convertToPixels(9));
        searchToggle.setPreferredH(CN.convertToPixels(9));
        searchToggle.addActionListener(e -> toggleSearch());
        updateSearchChrome();
        header.setUIID("Header");
        add(BorderLayout.NORTH, header);

        String[] names = {"All songs", "Albums", "Artists"};
        String[] values = {"songs", "albums", "artists"};
        for (int i = 0; i < names.length; i++) {
            final String value = values[i];
            LibraryViewButton choice = new LibraryViewButton(names[i]);
            viewButtons[i] = choice;
            choice.setActive(i == 0);
            choice.addActionListener(e -> {
                for (LibraryViewButton button : viewButtons) button.setActive(button == choice);
                libraryHistory.clear();
                libraryView = value; selectedArtist = null; selectedAlbum = null; selectedAlbumGroup = null; selectedArtistGroup = null;
                search.setText(""); query = ""; load(0);
            });
            libraryViews.add(choice);
        }
        libraryUp.setEndsWith3Points(true);
        libraryUp.setPreferredH(CN.convertToPixels(9));
        libraryUp.addActionListener(e -> closeLibraryGroup());
        queueAlbum.setUIID("AlbumQueueButton");
        queueAlbum.setGlyph("Queue album", FontImage.MATERIAL_ADD);
        queueAlbum.addActionListener(e -> queueSelectedAlbum());
        for (HardwareButton button : new HardwareButton[] {queueAlbum, miniPlay, miniNext}) {
            button.setPreferredW(CN.convertToPixels(9)); button.setPreferredH(CN.convertToPixels(9));
        }
        albumCover.setPreferredW(CN.convertToPixels(18));
        albumCover.setPreferredH(CN.convertToPixels(18));
        albumCover.setName("Album artwork");
        albumTitle.setName("Album title");
        albumArtist.setName("Album artist");
        albumCount.setName("Album song count");
        Container albumCopy = BoxLayout.encloseY(albumTitle, albumArtist, albumCount);
        albumSummary.setUIID("AlbumSummary");
        Container albumActions = FlowLayout.encloseCenterMiddle(queueAlbum);
        albumActions.setUIID("AlbumActions");
        albumSummary.add(BorderLayout.WEST, albumCover).add(BorderLayout.CENTER, albumCopy)
                .add(BorderLayout.EAST, albumActions);
        artistCover.setPreferredW(CN.convertToPixels(18));
        artistCover.setPreferredH(CN.convertToPixels(18));
        artistCover.setName("Artist artwork");
        artistHeading.setName("Artist name");
        artistCount.setName("Artist song count");
        artistSummary.setUIID("AlbumSummary");
        artistSummary.add(BorderLayout.WEST, artistCover)
                .add(BorderLayout.CENTER, BoxLayout.encloseYCenter(artistHeading, artistCount));
        libraryLocationBar = new Container(new BorderLayout());
        libraryCategories.add(BorderLayout.CENTER, libraryViews).add(BorderLayout.EAST, searchToggle);
        libraryBack.add(BorderLayout.WEST, libraryUp);
        albumNavigationMessage.setHidden(true); albumNavigationMessage.setVisible(false);
        library.add(BorderLayout.CENTER, collection)
                .add(BorderLayout.NORTH, BoxLayout.encloseY(libraryCategories, libraryBack, searchBar, albumNavigationMessage, libraryLocationBar));
        library.setUIID("LibraryPanel");
        queue.setScrollableY(true);
        queue.setScrollVisible(false);
        Button clear = new Button("Clear queue");
        clear.addActionListener(e -> {
            if (albumQueueLoading) { finishAlbumQueue(albumRequest, "Album queueing cancelled."); albumRequest++; }
            player.clear();
        });
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
        informationToggle.addActionListener(e -> toggleInformation());
        shuffle.addActionListener(e -> player.shuffle());
        queueShuffle.addActionListener(e -> player.shuffle());
        showQueue.addActionListener(e -> navigate(wide && view == View.QUEUE ? View.LIBRARY : View.QUEUE));
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
        wideShell.setName("Wide listening layout");
        wideLibrary.setUIID("ListeningPane");
        widePlayer.setUIID("ListeningPlayerPane");
        wideQueue.setUIID("ListeningPane");
        wideLibrary.add(BorderLayout.NORTH, paneHeading("Library"));
        wideQueue.add(BorderLayout.NORTH, BorderLayout.center(paneHeading("Queue"))
                .add(BorderLayout.WEST, navigation("Close queue", FontImage.MATERIAL_CLOSE, () -> navigate(View.LIBRARY)))
                .add(BorderLayout.EAST, wideQueueActions));
        wideShell.add(BorderLayout.NORTH, widePlayer);
        queue.setName("Playback queue");
        miniTitle.setUIID("MiniTitle");
        miniTitle.setName("Open Now Playing");
        miniTitle.setAccessibilityText("Open Now Playing");
        miniTitle.getSemantics().setLabel("Open Now Playing");
        miniTitle.addActionListener(e -> navigate(View.NOW_PLAYING));
        miniPlay.setGlyph("Play", FontImage.MATERIAL_PLAY_ARROW);
        miniPlay.addActionListener(e -> player.toggle());
        miniNext.addActionListener(e -> player.next());
        miniPlayer.setUIID("MiniPlayer");
        Container miniCopy = BoxLayout.encloseY(miniTitle, miniArtist);
        miniCopy.setLeadComponent(miniTitle);
        miniPlayer.add(BorderLayout.WEST, miniCover).add(BorderLayout.CENTER, miniCopy).add(BorderLayout.EAST, BoxLayout.encloseX(miniPlay, miniNext));
        searchBar.setLayout(new BorderLayout());
        searchBar.add(BorderLayout.CENTER, search).add(BorderLayout.EAST, find);
        navigate(View.LIBRARY);
        // The port updates the Form bounds during its resize callback. Recompose
        // on the next EDT turn, after the Form has its current window bounds.
        addSizeChangedListener(e -> CN.callSerially(() -> { sizeContent(); revalidate(); }));
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
        collection.refreshArtwork();
        if (selectedAlbumGroup != null) albumCover.refresh();
        else if (selectedArtistGroup != null) artistCover.refresh();
        for (RecordArtwork art : queueArtwork) art.refresh();
    }

    private void sizeContent() {
        int available = getWidth() > 0 ? getWidth() : CN.getDisplayWidth();
        // SpanLabel retains its measured width; bound it again when the viewport changes.
        if (accountUsername != null) accountUsername.setPreferredW(Math.max(1, available - CN.convertToPixels(12)));
        accountPanel.setPreferredW(available);
        accountPanel.setShouldCalcPreferredSize(true);
        boolean wasWide = wide;
        int height = getHeight() > 0 ? getHeight() : CN.getDisplayHeight();
        wide = available >= CN.convertToPixels(130) && height >= CN.convertToPixels(100);
        wideShell.setPreferredW(available);
        if (wide != wasWide) libraryViews.setLayout(wide ? new FlowLayout(Component.LEFT) : new GridLayout(1, 3));
        int libraryWidth = available;
        libraryUp.setPreferredSize(null);
        libraryUp.setPreferredW(Math.min(libraryUp.getPreferredW(), Math.max(1, available - CN.convertToPixels(8))));
        libraryUp.setPreferredH(CN.convertToPixels(9));
        collection.presentation(wide, available);
        albumNavigationMessage.setPreferredW(Math.max(1, libraryWidth - CN.convertToPixels(8)));
        albumNavigationMessage.setShouldCalcPreferredSize(true);
        artistHeading.setPreferredW(Math.max(1, libraryWidth - CN.convertToPixels(30)));
        artistHeading.setShouldCalcPreferredSize(true);
        albumTitle.setPreferredW(Math.max(1, libraryWidth - CN.convertToPixels(39)));
        albumTitle.setShouldCalcPreferredSize(true);
        albumArtist.setPreferredW(Math.max(1, libraryWidth - CN.convertToPixels(39)));
        albumArtist.setShouldCalcPreferredSize(true);
        int width = wide ? available : Math.min(available, CN.convertToPixels(62));
        playerFace.horizontal(wide);
        metadataCover.setHidden(wide); metadataCover.setVisible(!wide);
        artworkSlot.setUIID(wide ? "ConsoleArtwork" : "ArtworkRecess");
        cover.compact(wide);
        // BorderLayout otherwise retains the cover's 43 mm preferred height and
        // centres it inside the smaller console slot, cropping its lower edge.
        com.codename1.ui.geom.Dimension sleeve = wide ? new com.codename1.ui.geom.Dimension(
                PlayerFace.artworkWidth(width), PlayerFace.artworkWidth(width)) : null;
        artworkSlot.setPreferredSize(sleeve);
        cover.setPreferredSize(sleeve);
        trackInformation.fitWidth(wide ? PlayerFace.artworkWidth(width) : width);
        // CN1 setPreferredW also freezes the previous preferred height.
        playerFace.setPreferredSize(null);
        playerFace.setWidth(width);
        playerFace.setShouldCalcPreferredSize(true);
        if (wide) widePlayer.setPreferredH(Math.min(playerFace.getPreferredH(), Math.max(CN.convertToPixels(45), height * 48 / 100)));
        if (wasWide != wide && body.getComponentCount() > 0) navigate(view, true);
    }

    private Label paneHeading(String text) { return new Label(text, "ListeningHeading"); }

    private HardwareButton navigation(String name, char glyph, Runnable action) {
        HardwareButton button = new HardwareButton(name, glyph, true);
        button.setPreferredW(CN.convertToPixels(9));
        button.setPreferredH(CN.convertToPixels(9));
        if (name.equals("Now Playing")) button.setIcon(NowPlayingIcon.create());
        button.addActionListener(e -> action.run());
        return button;
    }

    private void navigate(View target) {
        navigate(target, false);
    }

    private void navigate(View target, boolean force) {
        if (!force && view == target && body.getComponentCount() > 0) return;
        view = target;
        boolean combined = wide && target != View.ACCOUNT;
        if (target == View.ACCOUNT || !combined && target != View.LIBRARY) cancelAlbumNavigation();
        queue.setFocusable(combined);
        collection.shown(combined ? target != View.QUEUE : target == View.LIBRARY);
        library.remove(); playerPanel.remove(); queuePanel.remove(); accountPanel.remove();
        body.removeAll();
        if (combined) {
            wideLibrary.add(BorderLayout.CENTER, library);
            widePlayer.add(BorderLayout.CENTER, playerPanel);
            wideQueue.add(BorderLayout.CENTER, queuePanel);
            Container lower = target == View.QUEUE ? wideQueue : wideLibrary;
            wideLibrary.remove(); wideQueue.remove();
            wideShell.add(BorderLayout.CENTER, lower);
            // Keep a bounded scroll viewport for large text without hiding the wheel.
            widePlayer.setPreferredH(Math.min(playerFace.getPreferredH(), Math.max(CN.convertToPixels(45), getHeight() * 48 / 100)));
            wideShell.remove(); body.add(BorderLayout.CENTER, wideShell);
        } else {
            Container content = target == View.LIBRARY ? library : target == View.QUEUE ? queuePanel : target == View.ACCOUNT ? accountPanel : playerPanel;
            body.add(BorderLayout.CENTER, content);
        }
        miniPlayer.remove();
        if (!combined && (target == View.LIBRARY || target == View.QUEUE)) add(BorderLayout.SOUTH, miniPlayer);
        showQueue.setUIID(combined && target == View.QUEUE ? "QueueToggleActive" : "HardwareButton");
        showQueue.setGlyph("Queue", FontImage.MATERIAL_QUEUE_MUSIC);
        showQueue.getSemantics().setDescription(combined ? (target == View.QUEUE ? "Close queue" : "Open queue") : "Open queue");
        header.removeAll();
        Container actions = new Container(BoxLayout.x());
        queueShuffle.remove();
        if (combined) {
            wideQueueActions.add(queueShuffle);
        } else if (target == View.LIBRARY) {
            actions.add(navigation("Now Playing", FontImage.MATERIAL_ALBUM, () -> navigate(View.NOW_PLAYING)));
        } else if (target == View.QUEUE) {
            queueShuffle.remove();
            actions.add(queueShuffle);
        } else if (target == View.NOW_PLAYING) {
            actions.add(navigation("Library", FontImage.MATERIAL_LIBRARY_MUSIC, () -> navigate(View.LIBRARY)));
        }
        if ((combined || target == View.LIBRARY || target == View.NOW_PLAYING) && api.canLogout()) actions.add(navigation("Profile", FontImage.MATERIAL_PERSON_OUTLINE, this::showProfile));
        Label heading = new Label(combined ? "Normal Bias" : target == View.LIBRARY ? "Library" : target == View.QUEUE ? "Queue" : target == View.ACCOUNT ? "Account" : "Now Playing", "ScreenHeading");
        Container bar = BorderLayout.center(heading).add(BorderLayout.EAST, actions);
        if (!combined && target == View.QUEUE)
            bar.add(BorderLayout.WEST, navigation("Back to Now Playing", FontImage.MATERIAL_ARROW_BACK, () -> navigate(View.NOW_PLAYING)));
        header.add(bar);
        View returnTo = target == View.ACCOUNT ? accountOrigin : target == View.QUEUE ? View.NOW_PLAYING : View.LIBRARY;
        String backName = "Back to " + (returnTo == View.LIBRARY ? "Library" : returnTo == View.QUEUE ? "Queue" : "Now Playing");
        if (target == View.ACCOUNT)
            bar.add(BorderLayout.WEST, navigation(backName, FontImage.MATERIAL_ARROW_BACK, () -> navigate(returnTo)));
        setBackCommand(combined || target == View.LIBRARY ? null : new Command(backName) {
            @Override public void actionPerformed(com.codename1.ui.events.ActionEvent e) { navigate(returnTo); }
        });
        getContentPane().setUIID(!combined && target == View.NOW_PLAYING ? "PlayerPanel" : "LibraryPanel");
        revalidate();
        if (combined && target == View.QUEUE) queue.requestFocus();
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
        boolean showEditor = searchVisible && libraryHistory.isEmpty();
        searchBar.setHidden(!showEditor);
        searchBar.setVisible(showEditor);
        searchToggle.setUIID(searchVisible || !query.isEmpty() ? "SearchToggleActive" : "SearchToggle");
        searchToggle.setGlyph(searchVisible ? "Hide search" : "Show search", FontImage.MATERIAL_SEARCH);
        searchToggle.getSemantics().setDescription(query.isEmpty() ? "Toggle library search" : "Library filtered by " + query);
    }

    private void searchLibrary() {
        query = search.getText().trim();
        navigate(View.LIBRARY);
        load(0);
    }

    private void load(int ignored) { load(null, null); }
    private void load(String path, LibraryCollection.Anchor anchor) {
        cancelAlbumNavigation();
        albumRequest++; albumQueueLoading = false;
        albumMessage.setText(""); albumMessage.setHidden(true); albumMessage.setVisible(false);
        boolean child = !libraryHistory.isEmpty();
        libraryCategories.setHidden(child); libraryCategories.setVisible(!child);
        libraryBack.setHidden(!child); libraryBack.setVisible(child);
        if (child) {
            LibraryState parent = libraryHistory.get(libraryHistory.size() - 1);
            String destination = parent.artist() != null ? display(parent.artist(), "Unknown artist")
                    : libraryView.equals("songs") ? "All songs" : libraryView.equals("albums") ? "Albums" : "Artists";
            libraryUp.setText("‹ " + destination);
            libraryUp.setName("Back to " + destination);
            libraryUp.setAccessibilityText("Back to " + destination);
            libraryUp.getSemantics().setLabel("Back to " + destination);
            libraryUp.getSemantics().setDescription("Return to the previous Library collection");
        }
        updateSearchChrome();
        boolean grouped = selectedAlbum == null && (!libraryView.equals("songs") || selectedArtist != null);
        boolean artistList = grouped && libraryView.equals("artists") && selectedArtist == null;
        libraryLocationBar.setHidden(selectedArtist == null && selectedAlbum == null);
        libraryLocationBar.setVisible(selectedArtist != null || selectedAlbum != null);
        libraryLocationBar.removeAll();
        boolean albumOpen = selectedAlbumGroup != null;
        boolean artistOpen = !albumOpen && selectedArtistGroup != null;
        if (!artistOpen) artistCover.release();
        if (albumOpen) {
            LibraryApi.Group group = selectedAlbumGroup;
            queueAlbum.setEnabled(selectedAlbumContents != null && group.trackCount() > 0);
            albumTitle.setText(display(group.album(), "Unknown album"));
            albumArtist.setText(display(group.artist(), "Unknown artist"));
            albumCount.setText(group.trackCount() + (group.trackCount() == 1 ? " song" : " songs"));
            albumCover.setTrack(new Track(group.trackId(), group.album(), group.artist(), group.album(), 0), api);
            libraryLocationBar.add(BorderLayout.CENTER, albumSummary).add(BorderLayout.SOUTH, albumMessage);
        } else {
            albumCover.release();
            if (artistOpen) {
                LibraryApi.Group group = selectedArtistGroup;
                artistHeading.setText(display(group.artist(), "Unknown artist"));
                artistCount.setText(group.trackCount() + (group.trackCount() == 1 ? " song" : " songs"));
                artistCover.setTrack(new Track(group.trackId(), group.artist(), group.artist(), null, 0), api);
                libraryLocationBar.add(BorderLayout.CENTER, artistSummary);
            }
        }
        libraryLocationBar.setUIID(albumOpen || artistOpen ? "AlbumLocation" : "Container");
        sizeContent();
        String view = grouped ? artistList ? "artists" : "albums" : "songs";
        boolean alphabetical = selectedAlbum == null;
        String empty = grouped ? "No " + view + " found." : query.isEmpty()
                ? "Your library is empty. Upload some MP3s to begin." : "No tracks match your search.";
        collection.load(path == null ? api.browsePath(view, query, selectedArtist, selectedAlbum, alphabetical) : path,
                grouped, alphabetical, empty, anchor);
        revalidate();
    }
    private void closeLibraryGroup() {
        if (libraryHistory.isEmpty()) return;
        LibraryState parent = libraryHistory.remove(libraryHistory.size() - 1);
        selectedArtist = parent.artist(); selectedAlbum = parent.album(); selectedAlbumGroup = parent.albumGroup(); selectedArtistGroup = parent.artistGroup();
        selectedAlbumContents = null;
        query = parent.query(); search.setText(parent.editor()); searchVisible = parent.searchVisible();
        load(null, parent.anchor());
    }
    private void albumMessage(String message) {
        albumMessage.setText(message); albumMessage.setHidden(false); albumMessage.setVisible(true); revalidate();
    }
    private void queueSelectedAlbum() {
        if (albumQueueLoading || selectedAlbumContents == null) return;
        if (selectedAlbumGroup.trackCount() > dev.normalbias.client.player.PlaybackQueue.MAX_TRACKS) {
            albumMessage("Album does not fit in the 200-track queue."); return;
        }
        albumQueueLoading = true; queueAlbum.setEnabled(false);
        albumMessage("Loading album for queue…");
        fetchQueueAlbum(selectedAlbumContents, ++albumRequest, new java.util.ArrayList<>(), new java.util.HashSet<>());
    }
    private void fetchQueueAlbum(String path, int request, java.util.List<LibraryApi.Item> items, java.util.Set<String> visited) {
        if (!visited.add(path)) { finishAlbumQueue(request, "Could not load the complete album. Try again."); return; }
        api.browse(path, false, page -> {
            if (request != albumRequest) return;
            for (LibraryApi.Item item : page.items()) {
                if (item.track() == null || api.scopedPlayback() &&
                        dev.normalbias.client.net.ApiLinks.href(item.resource(), "playback-grant", "POST") == null) {
                    finishAlbumQueue(request, "Album playback is unavailable. Refresh the library and try again."); return;
                }
                items.add(item);
                if (items.size() > dev.normalbias.client.player.PlaybackQueue.MAX_TRACKS) {
                    finishAlbumQueue(request, "Album does not fit in the 200-track queue."); return;
                }
            }
            if (page.next() != null) {
                if (page.items().isEmpty()) { finishAlbumQueue(request, "Could not load the complete album. Try again."); return; }
                fetchQueueAlbum(page.next(), request, items, visited); return;
            }
            if (items.size() != page.total()) {
                finishAlbumQueue(request, "Album changed while loading. Refresh the library and try again."); return;
            }
            java.util.List<Track> tracks = new java.util.ArrayList<>();
            java.util.List<String> retained = new java.util.ArrayList<>();
            for (int i = 0; i < player.queue.size(); i++) retained.add(player.queue.get(i).id());
            for (LibraryApi.Item item : items) tracks.add(item.track());
            api.rememberForQueue(items, retained);
            int added = player.enqueueAlbum(tracks);
            finishAlbumQueue(request, added < 0 ? "Album does not fit in the 200-track queue. Clear space and try again."
                    : added == 0 ? "Album already in queue." : "Album queued: " + added + (added == 1 ? " track added." : " tracks added."));
        }, error -> finishAlbumQueue(request, "Album was not queued. " + error + " Try again."));
    }
    private void finishAlbumQueue(int request, String message) {
        if (request != albumRequest) return;
        albumQueueLoading = false;
        queueAlbum.setEnabled(selectedAlbumContents != null);
        albumMessage(message);
    }
    private static String display(String text, String fallback) { return text == null || text.isEmpty() ? fallback : text; }
    private Container groupRow(LibraryApi.Item item) {
        LibraryApi.Group group = item.group();
        boolean artistList = libraryView.equals("artists") && selectedArtist == null;
        String name = artistList ? display(group.artist(), "Unknown artist") : display(group.album(), "Unknown album");
        Button open = new Button(name, "TrackTitle");
        open.setAccessibilityText("Open " + name);
        open.getSemantics().setLabel("Open " + name);
        open.setEnabled(item.contents() != null);
        open.addActionListener(e -> {
            libraryHistory.add(new LibraryState(selectedArtist, selectedAlbum, query, search.getText(), collection.anchor(), selectedAlbumGroup, selectedArtistGroup, searchVisible));
            if (artistList) selectedArtistGroup = group;
            selectedArtist = group.artist();
            selectedAlbum = artistList ? null : group.album();
            selectedAlbumGroup = artistList ? null : group;
            selectedAlbumContents = artistList ? null : item.contents();
            searchVisible = false; search.setText(""); query = ""; load(item.contents(), null);
        });
        String subtitle = (artistList ? "" : display(group.artist(), "Unknown artist") + " · ")
                + group.trackCount() + (group.trackCount() == 1 ? " song" : " songs");
        return new GroupEntry(open, name, subtitle,
                new Track(group.trackId(), name, group.artist(), group.album(), 0), api);
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
        art.setTrack(track, api, false);
        Container detail = BorderLayout.center(copy).add(BorderLayout.WEST, art);
        detail.setLeadComponent(listen);
        Container actions = BoxLayout.encloseX();
        String albumLink = api.albumLink(track);
        if (libraryView.equals("songs") && selectedArtist == null && selectedAlbum == null && albumLink != null) {
            Button album = new Button();
            icon(album, "Open album for " + track.title(), FontImage.MATERIAL_ALBUM, "AlbumRowAction");
            album.setPreferredW(CN.convertToPixels(9)); album.setPreferredH(CN.convertToPixels(9));
            album.getSemantics().setDescription("View " + display(track.album(), "Unknown album") + " by " + display(track.artist(), "Unknown artist"));
            album.addActionListener(e -> openTrackAlbum(albumLink, album));
            actions.add(album);
        }
        actions.add(enqueue);
        Container row = BorderLayout.center(detail).add(BorderLayout.EAST, actions);
        row.setUIID("TrackRow"); row.putClientProperty("artwork", art);
        return row;
    }
    private void openTrackAlbum(String link, Button button) {
        cancelAlbumNavigation();
        int request = albumNavigation;
        pendingAlbumButton = button;
        button.setEnabled(false);
        albumNavigationMessage.setText("Opening album…");
        albumNavigationMessage.setHidden(false); albumNavigationMessage.setVisible(true); revalidate();
        api.browse(link, true, page -> {
            if (request != albumNavigation) return;
            button.setEnabled(true); pendingAlbumButton = null;
            if (page.total() != 1 || page.items().size() != 1 || page.items().get(0).contents() == null) {
                albumNavigationError("Album unavailable. Refresh the library and try again."); return;
            }
            LibraryApi.Item item = page.items().get(0);
            api.remember(page);
            libraryHistory.add(new LibraryState(selectedArtist, selectedAlbum, query, search.getText(), collection.anchor(), selectedAlbumGroup, selectedArtistGroup, searchVisible));
            selectedAlbumGroup = item.group(); selectedArtist = item.group().artist(); selectedAlbum = item.group().album();
            selectedAlbumContents = item.contents();
            searchVisible = false; search.setText(""); query = ""; load(item.contents(), null);
        }, error -> {
            if (request != albumNavigation) return;
            button.setEnabled(true); pendingAlbumButton = null;
            albumNavigationError("Could not open album. " + error + " Try again.");
        });
    }
    private void cancelAlbumNavigation() {
        albumNavigation++;
        if (pendingAlbumButton != null) pendingAlbumButton.setEnabled(true);
        pendingAlbumButton = null;
        albumNavigationMessage.setHidden(true); albumNavigationMessage.setVisible(false);
    }
    private void albumNavigationError(String message) {
        albumNavigationMessage.setText(message); revalidate();
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
    private void refreshPlayer() {
        java.util.List<String> queuedIds = new java.util.ArrayList<>();
        for (int i = 0; i < player.queue.size(); i++) queuedIds.add(player.queue.get(i).id());
        api.retainQueue(queuedIds);
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
        miniNext.setEnabled(next.isEnabled());
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
            miniPlay.setGlyph(text, player.isPlaying() ? FontImage.MATERIAL_PAUSE : FontImage.MATERIAL_PLAY_ARROW);
        }
    }
    private static String formatTime(int ms) {
        int seconds = Math.max(0, ms / 1000);
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
    public void disposePlayer() {
        cancelAlbumNavigation();
        albumRequest++;
        collection.dispose();
        albumCover.release(); artistCover.release();
        if (timer != null) timer.cancel();
        api.dispose(); queueArtwork.clear(); queue.removeAll();
        Car.endSession();
        Car.setApplication(null);
        player.dispose();
        refreshPlayer();
    }
}
