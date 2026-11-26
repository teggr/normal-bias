package dev.normalbias.client.player;

import com.codename1.car.*;
import com.codename1.car.spi.CarBridge;
import dev.normalbias.client.Track;
import dev.normalbias.client.car.CarListening;
import dev.normalbias.client.net.LibraryApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CarListeningTest {
    private static final class Audio implements BrowserAudio {
        int opens, closes;
        public boolean isSupported() { return true; }
        public void open(String url) { opens++; }
        boolean playing;
        public void play() { playing = true; }
        public void pause() { playing = false; }
        public void close() { closes++; }
        public void seek(int time) { }
        String error = "";
        public String snapshot() { return "{\"error\":\"" + error + "\",\"time\":0,\"duration\":42000,\"playing\":" + playing + ",\"loading\":false}"; }
    }
    private static final class HeadUnit implements CarBridge {
        int invalidations;
        public void pushScreen(CarScreen screen) { }
        public void popScreen() { }
        public void invalidate(CarScreen screen) { invalidations++; }
        public void finish() { }
        public boolean isConnected() { return true; }
        public void showToast(String text, int duration) { }
        public int getListRowLimit() { return 5; }
        public int getGridItemLimit() { return 5; }
    }
    // The framework creates this context during a native connection. Test the actual
    // portable stack without starting Swing/EDT or a native head-unit window.
    private CarContext context(HeadUnit bridge) throws Exception {
        var constructor = CarContext.class.getDeclaredConstructor(CarBridge.class);
        constructor.setAccessible(true);
        return constructor.newInstance(bridge);
    }
    private List<CarRow> rows(CarContext context) {
        return ((CarListTemplate) context.getTopScreen().dispatchCreateTemplate()).getSections().get(0).getRows();
    }
    private void tap(CarContext context, String title) {
        CarListTemplate list = (CarListTemplate) context.getTopScreen().dispatchCreateTemplate();
        if (list.getHeaderActions() != null) {
            for (CarAction action : list.getHeaderActions().getActions()) if (action.getTitle().equals(title)) {
                action.getOnAction().actionPerformed(context); return;
            }
        }
        rows(context).stream().filter(row -> row.getTitle().equals(title)).findFirst().orElseThrow()
                .getOnAction().actionPerformed(context);
    }
    @Test void carOnlyOffersAdvertisedCollections() throws Exception {
        LibraryApi api = new LibraryApi("http://127.0.0.1:8080") {
            @Override public String browsePath(String view, String query, String artist, String album, boolean alphabetical) {
                if (!view.equals("albums")) throw new IllegalStateException("No collection affordance");
                return "/advertised-albums";
            }
        };
        CarListening car = new CarListening(api, new Player(api, () -> {}, new Audio()));
        CarContext context = context(new HeadUnit());
        context.pushScreen(car.onCreateRootScreen(context));
        tap(context, "Library");
        assertEquals(List.of("Albums"), rows(context).stream().map(CarRow::getTitle).toList());
    }

    @Test void carAndPhoneShareQueueAndDisconnectDoesNotStopPlayback() throws Exception {
        Audio audio = new Audio();
        LibraryApi api = new LibraryApi("http://127.0.0.1:8080") {
            @Override public Runnable artwork(Track track, java.util.function.Consumer<com.codename1.ui.Image> loaded) {
                return () -> {}; // This portable-stack test has no initialized image/network port.
            }
        };
        Player player = new Player(api, () -> {}, audio);
        CarListening car = new CarListening(api, player);
        HeadUnit bridge = new HeadUnit();
        CarContext context = context(bridge);
        context.pushScreen(car.onCreateRootScreen(context));
        for (int i = 0; i < 8; i++) player.enqueue(new Track("track" + i, "Track " + i, "Demo", "Synthetic", 42000));
        assertEquals(0, player.queue.index()); assertEquals(1, audio.opens); assertFalse(player.isPlaying());
        assertEquals(List.of("Library", "Now Playing"), rows(context).stream().map(CarRow::getTitle).toList());
        tap(context, "Now Playing");
        tap(context, "Queue");
        assertTrue(rows(context).size() <= bridge.getListRowLimit());
        tap(context, "More");
        tap(context, "Track 2");
        assertTrue(((CarListTemplate) context.getTopScreen().dispatchCreateTemplate()).getTitle().startsWith("Queue"));
        context.popScreen();
        assertEquals(2, player.queue.index());
        assertEquals(2, audio.opens);
        assertTrue(rows(context).stream().anyMatch(r -> r.getTitle().equals("Loading track…")));
        assertTrue(rows(context).stream().noneMatch(r -> r.getTitle().equals("Restart track")));
        tap(context, "Next track");
        assertEquals(3, player.queue.index());
        tap(context, "Previous track");
        assertEquals(2, player.queue.index());
        player.poll();
        tap(context, "Pause");
        assertFalse(player.isPlaying());
        tap(context, "Play");
        assertTrue(player.isPlaying());
        tap(context, "Pause");
        assertFalse(player.isPlaying());
        audio.error = "Stream unavailable";
        player.poll();
        assertTrue(rows(context).stream().anyMatch(r -> r.getTitle().equals("Retry track")));
        assertTrue(rows(context).stream().noneMatch(r -> r.getTitle().equals("Restart track")));
        audio.error = "";
        tap(context, "Retry track");
        player.poll();
        if (player.isPlaying()) tap(context, "Pause");
        assertFalse(player.hasFailed());
        assertTrue(rows(context).stream().anyMatch(r -> r.getTitle().equals("Restart track")));
        tap(context, "System player");
        assertInstanceOf(CarNowPlayingTemplate.class, context.getTopScreen().dispatchCreateTemplate());
        context.popScreen();
        tap(context, "Queue");
        int opens = audio.opens;
        Track current = player.queue.current();
        tap(context, "Shuffle queue");
        assertEquals(current, player.queue.current());
        assertEquals(opens, audio.opens);
        assertFalse(player.isPlaying());
        context.popScreen();
        int closes = audio.closes;
        car.onCarDisconnected();
        car.refresh();
        assertEquals(closes, audio.closes);
        assertEquals(8, player.queue.size());
        tap(context, "Queue");
        tap(context, "Clear queue");
        assertEquals(0, player.queue.size());
        assertEquals(closes + 1, audio.closes);
    }
}
