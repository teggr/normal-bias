package dev.normalbias.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class UploadCliTest {
    @TempDir Path dir;

    @Test void rejectsRemoteOriginsAndSymlinkRoot() throws Exception {
        var cli = new UploadCli();
        for (String uri : new String[]{"http://example.com", "http://127.0.0.1/path", "https://127.0.0.1",
                "http://user@127.0.0.1", "http://127.0.0.1?query=1"}) {
            assertThrows(IllegalArgumentException.class, () -> cli.run(dir, URI.create(uri), System.out));
        }
        Path link = dir.resolve("link");
        Files.createSymbolicLink(link, dir);
        assertThrows(IllegalArgumentException.class, () -> cli.run(link, URI.create("http://127.0.0.1"), System.out));
    }

    @Test void cancelsAnInFlightUploadAndStartsNoFurtherFiles() throws Exception {
        // A slow local receiver ensures cancel happens during a transfer, not before discovery.
        try (var file = new RandomAccessFile(dir.resolve("large.mp3").toFile(), "rw")) {
            file.setLength(64L * 1024 * 1024);
        }
        Files.writeString(dir.resolve("second.mp3"), "second");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tracks", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().read();
            entered.countDown();
            try { release.await(10, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        UploadCli cli = new UploadCli();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> cli.run(dir,
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), new PrintStream(bytes)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            cli.cancel();
            assertEquals(130, future.get(5, TimeUnit.SECONDS));
            assertEquals(1, requests.get());
            assertTrue(bytes.toString().contains("Cancelled:"));
        } finally { release.countDown(); server.stop(0); }
    }

    @Test void preCancelledRunDoesNotContactServer() throws Exception {
        Files.writeString(dir.resolve("track.mp3"), "test");
        UploadCli cli = new UploadCli();
        cli.cancel();
        assertEquals(130, cli.run(dir, URI.create("http://127.0.0.1:1"), System.out));
    }
}
