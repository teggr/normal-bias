package dev.normalbias.cli;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.Locale;
import java.util.ArrayList;
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
        var positional = new ArrayList<String>(); Path tokenFile = null;
        for (String argument : args) {
            if (argument.startsWith("--token-file=") && tokenFile == null) tokenFile = Path.of(argument.substring(13));
            else positional.add(argument);
        }
        if (positional.size() < 1 || positional.size() > 2 || positional.stream().anyMatch(value -> value.startsWith("--"))) {
            System.err.println("Usage: java -jar normal-bias-cli.jar DIRECTORY [LOOPBACK_ORIGIN] [--token-file=PATH]");
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
            status = cli.run(Path.of(positional.getFirst()), URI.create(positional.size() == 2 ? positional.get(1) : "http://127.0.0.1:8080"), System.out,
                    tokenFile == null ? null : readToken(tokenFile));
        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            status = 2;
        }
        Runtime.getRuntime().removeShutdownHook(hook);
        System.exit(status);
    }

    public int run(Path directory, URI base, PrintStream out) throws IOException {
        return run(directory, base, out, null);
    }
    public int run(Path directory, URI base, PrintStream out, String token) throws IOException {
        validateBase(base);
        if (token != null && !token.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Authenticated uploads require a valid token and a loopback HTTP or HTTPS origin");
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("DIRECTORY must be a directory, not a symlink");
        }
        if (cancelled.get()) { out.println("Cancelled: 0 stored, 0 duplicates, 0 failed"); return 130; }
        int[] counts = new int[3]; // created, duplicate, failed
        boolean[] authFailed = {false};
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            final URI upload;
            try { upload = token == null ? base.resolve("/api/tracks") : discoverUpload(client, base, token); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); cancel(); return 130; }
            catch (IOException refused) { out.println("Upload access refused. Check the credential and advertised upload action."); return 1; }
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
                        URI target = URI.create(upload.toASCIIString() + (upload.getRawQuery() == null ? "?" : "&") + "filename=" + name);
                        var builder = HttpRequest.newBuilder(target)
                                .header("Content-Type", "audio/mpeg").timeout(Duration.ofMinutes(30))
                                .POST(publisher);
                        if (token != null) builder.header("Authorization", "Bearer " + token);
                        HttpRequest request = builder.build();
                        var future = client.sendAsync(request, HttpResponse.BodyHandlers.discarding());
                        inFlight = future;
                        // Close the race between starting a request and a concurrent cancel().
                        if (cancelled.get()) future.cancel(true);
                        HttpResponse<Void> response = future.get();
                        if (response.statusCode() == 201) { counts[0]++; out.println("Stored"); }
                        else if (response.statusCode() == 200) { counts[1]++; out.println("Already present"); }
                        else {
                            counts[2]++; out.println("Failed: HTTP " + response.statusCode());
                            if (response.statusCode() == 401 || response.statusCode() == 403) {
                                authFailed[0] = true; out.println("Upload access refused. Check the credential before retrying.");
                            }
                        }
                    } catch (CancellationException e) {
                        cancelled.set(true);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        cancel();
                    } catch (IOException | ExecutionException e) {
                        counts[2]++;
                        out.println("Failed: " + e.getMessage());
                    } finally { inFlight = null; }
                    return cancelled.get() || authFailed[0] ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
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
        // Literal loopback destinations only, including authenticated TLS. No DNS or redirects.
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || !("127.0.0.1".equals(base.getHost()) || "[::1]".equals(base.getHost()))
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || base.getPath().equals("/"))) {
            throw new IllegalArgumentException("API URL must be a literal loopback HTTP or HTTPS origin");
        }
    }

    private static URI discoverUpload(HttpClient client, URI base, String token) throws IOException, InterruptedException {
        var response = client.send(HttpRequest.newBuilder(base.resolve("/api")).header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() != 200) throw new IOException("Discovery refused");
        for (String link : response.headers().allValues("Link")) {
            var match = java.util.regex.Pattern.compile("^<([^<>]+)>; rel=\"upload\"; method=\"POST\"$").matcher(link);
            if (!match.matches()) continue;
            try {
                URI target = base.resolve(match.group(1));
                if (!base.getScheme().equals(target.getScheme()) || !base.getHost().equals(target.getHost()) || base.getPort() != target.getPort()
                        || target.getRawUserInfo() != null || target.getRawFragment() != null) throw new IOException("Untrusted upload action");
                return target;
            } catch (IllegalArgumentException invalid) { throw new IOException("Invalid upload action"); }
        }
        throw new IOException("No upload action advertised");
    }

    static String readToken(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Credential file must be a regular file, not a symlink");
        var permissions = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
        if (permissions.stream().anyMatch(permission -> permission.name().startsWith("GROUP_") || permission.name().startsWith("OTHERS_")))
            throw new IOException("Credential file must be accessible only by its owner");
        byte[] bytes;
        try (var input = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) { bytes = input.readNBytes(129); }
        if (bytes.length > 128) throw new IOException("Invalid credential file");
        String value = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).strip();
        if (!value.matches("[a-f0-9]{64}")) throw new IOException("Invalid credential file");
        return value;
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
