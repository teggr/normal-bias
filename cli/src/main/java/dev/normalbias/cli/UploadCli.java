package dev.normalbias.cli;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Flow;

/** Sequential, streaming directory uploader. All music transfers go through the HTTP API. */
public final class UploadCli {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile CompletableFuture<?> inFlight;

    public void cancel() {
        cancelled.set(true);
        var request = inFlight;
        if (request != null) request.cancel(true);
    }

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java -jar normal-bias-cli.jar DIRECTORY [http://127.0.0.1:8080]");
            System.exit(2);
        }
        UploadCli cli = new UploadCli();
        Thread hook = new Thread(() -> {
            cli.cancel();
            System.err.println("Cancelled. Completed tracks remain; retrying the directory deduplicates exact files.");
        });
        Runtime.getRuntime().addShutdownHook(hook);
        int status;
        try {
            status = cli.run(Path.of(args[0]), URI.create(args.length == 2 ? args[1] : "http://127.0.0.1:8080"), System.out);
        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            status = 2;
        }
        Runtime.getRuntime().removeShutdownHook(hook);
        System.exit(status);
    }

    public int run(Path directory, URI base, PrintStream out) throws IOException {
        validateBase(base);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("DIRECTORY must be a directory, not a symlink");
        }
        int[] counts = new int[3]; // created, duplicate, failed
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return cancelled.get() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (cancelled.get()) return FileVisitResult.TERMINATE;
                    if (!attrs.isRegularFile() || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        out.printf("Uploading %s (%d bytes)%n", directory.relativize(file), attrs.size());
                        var publisher = new ProgressPublisher(HttpRequest.BodyPublishers.ofFile(file), out);
                        String name = URLEncoder.encode(file.getFileName().toString(), java.nio.charset.StandardCharsets.UTF_8);
                        HttpRequest request = HttpRequest.newBuilder(base.resolve("/api/tracks?filename=" + name))
                                .header("Content-Type", "audio/mpeg").timeout(Duration.ofMinutes(30))
                                .POST(publisher).build();
                        var future = client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
                        inFlight = future;
                        // Close the race between starting a request and a concurrent cancel().
                        if (cancelled.get()) future.cancel(true);
                        HttpResponse<Void> response = future.get();
                        if (response.statusCode() == 201) { counts[0]++; out.println("Stored"); }
                        else if (response.statusCode() == 200) { counts[1]++; out.println("Already present"); }
                        else { counts[2]++; out.println("Failed: HTTP " + response.statusCode()); }
                    } catch (CancellationException e) {
                        cancelled.set(true);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        cancel();
                    } catch (IOException | ExecutionException e) {
                        counts[2]++;
                        out.println("Failed: " + e.getMessage());
                    } finally { inFlight = null; }
                    return cancelled.get() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path file, IOException failure) {
                    counts[2]++;
                    out.println("Cannot read " + file + ": " + failure.getMessage());
                    return cancelled.get() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) {
                    if (failure != null) { counts[2]++; out.println("Cannot finish directory " + dir); }
                    return cancelled.get() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
            });
        }
        out.printf("%s: %d stored, %d duplicates, %d failed%n", cancelled.get() ? "Cancelled" : "Finished",
                counts[0], counts[1], counts[2]);
        return cancelled.get() ? 130 : counts[2] > 0 ? 1 : 0;
    }

    private static void validateBase(URI base) {
        // Restrict this unauthenticated PoC to literal loopback destinations. No DNS or redirects.
        if (!"http".equals(base.getScheme()) || !("127.0.0.1".equals(base.getHost()) || "[::1]".equals(base.getHost()))
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || base.getPath().equals("/"))) {
            throw new IllegalArgumentException("API URL must be an http://127.0.0.1[:port] or http://[::1][:port] origin");
        }
    }

    private static final class ProgressPublisher implements HttpRequest.BodyPublisher {
        private final HttpRequest.BodyPublisher delegate;
        private final PrintStream out;
        ProgressPublisher(HttpRequest.BodyPublisher delegate, PrintStream out) { this.delegate = delegate; this.out = out; }
        @Override public long contentLength() { return delegate.contentLength(); }
        @Override public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            delegate.subscribe(new Flow.Subscriber<ByteBuffer>() {
                long sent;
                int lastBucket = -1;
                public void onSubscribe(Flow.Subscription subscription) { subscriber.onSubscribe(subscription); }
                public void onNext(ByteBuffer buffer) {
                    sent += buffer.remaining();
                    int percentage = contentLength() <= 0 ? 100 : (int) Math.min(100, sent * 100 / contentLength());
                    if (percentage / 10 != lastBucket) {
                        out.printf("  %,d / %,d bytes (%d%% handed to HTTP client)%n", sent, contentLength(), percentage);
                        lastBucket = percentage / 10;
                    }
                    subscriber.onNext(buffer);
                }
                public void onError(Throwable failure) { subscriber.onError(failure); }
                public void onComplete() { subscriber.onComplete(); }
            });
        }
    }
}
