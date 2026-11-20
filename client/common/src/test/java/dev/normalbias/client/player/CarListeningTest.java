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
        public void play() { }
        public void pause() { }
        public void close() { closes++; }
        public void seek(int time) { }
        public String snapshot() { return "{}"; }
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
        rows(context).stream().filter(row -> row.getTitle().equals(title)).findFirst().orElseThrow()
                .getOnAction().actionPerformed(context);
    }
    @Test void carAndPhoneShareQueueAndDisconnectDoesNotStopPlayback() throws Exception {
        Audio audio = new Audio();
        LibraryApi api = new LibraryApi("http://127.0.0.1:8080");
        Player player = new Player(api, () -> {}, audio);
        CarListening car = new CarListening(api, player);
        HeadUnit bridge = new HeadUnit();
        CarContext context = context(bridge);
        context.pushScreen(car.onCreateRootScreen(context));
        for (int i = 0; i < 8; i++) player.enqueue(new Track("track" + i, "Track " + i, "Demo", "Synthetic", 42000));
        tap(context, "Queue (8)");
        assertTrue(rows(context).size() <= bridge.getListRowLimit());
        tap(context, "More queued tracks");
        tap(context, "Track 2");
        assertEquals(2, player.queue.index());
        assertEquals(1, audio.opens);
        tap(context, "Next");
        assertEquals(3, player.queue.index());
        tap(context, "Previous");
        assertEquals(2, player.queue.index());
        tap(context, "Play");
        assertTrue(player.isPlaying());
        tap(context, "Pause");
        assertFalse(player.isPlaying());
        tap(context, "Now Playing");
        assertInstanceOf(CarNowPlayingTemplate.class, context.getTopScreen().dispatchCreateTemplate());
        int closes = audio.closes;
        car.onCarDisconnected();
        car.refresh();
        assertEquals(closes, audio.closes);
        assertEquals(8, player.queue.size());
        context.popScreen();
        context.popScreen();
        tap(context, "Clear queue");
        assertEquals(0, player.queue.size());
        assertEquals(closes + 1, audio.closes);
    }
}
