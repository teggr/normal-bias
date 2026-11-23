package dev.normalbias.api;

import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class InspectionService {
    private final StorageInspector inspector;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final LinkedHashMap<UUID, Run> runs = new LinkedHashMap<>();

    public InspectionService(StorageInspector inspector) { this.inspector = inspector; }

    public synchronized Status start(String mode) {
        if (!Set.of("quick", "full").contains(mode)) throw TrackStore.error(HttpStatus.BAD_REQUEST, "Mode must be quick or full");
        if (active().isPresent()) {
            throw TrackStore.error(HttpStatus.CONFLICT, "An inspection is already running");
        }
        Run run = new Run(mode);
        runs.put(run.id, run);
        if (runs.size() > 5) runs.remove(runs.keySet().iterator().next());
        Status accepted = run.status();
        worker.submit(() -> {
            try { inspector.inspect(mode.equals("full"), run); run.finish("COMPLETED", null); }
            catch (Exception e) { run.finish("FAILED", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
        });
        return accepted;
    }

    public synchronized Status status(UUID id) { return get(id).status(); }
    public synchronized Optional<Status> active() {
        return runs.values().stream().map(Run::status).filter(status -> status.state().equals("RUNNING")).findFirst();
    }

    public synchronized Page findings(UUID id, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) throw TrackStore.error(HttpStatus.BAD_REQUEST, "Invalid pagination");
        return get(id).page(offset, limit);
    }

    private Run get(UUID id) {
        Run run = runs.get(id);
        if (run == null) throw TrackStore.error(HttpStatus.NOT_FOUND, "Inspection not found");
        return run;
    }

    @PreDestroy public void close() {
        worker.shutdownNow();
        try { worker.awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public record Status(UUID id, String mode, String state, Instant startedAt, Instant completedAt,
                         long snapshotTracks, long scannedTracks, long skippedChangedTracks, long total,
                         Map<String, Long> counts, String error) {}
    public record Page(List<StorageInspector.Finding> items, int offset, int limit, long total, String state) {}

    private static final class Run implements StorageInspector.Observer {
        final UUID id = UUID.randomUUID();
        final String mode;
        final Instant startedAt = Instant.now();
        final List<StorageInspector.Finding> findings = new ArrayList<>();
        final Map<String, Long> counts = new TreeMap<>();
        String state = "RUNNING", error;
        Instant completedAt;
        long snapshotTracks, scannedTracks, skippedChangedTracks;
        Run(String mode) { this.mode = mode; }
        @Override public synchronized void snapshot(long tracks) { snapshotTracks = tracks; }
        @Override public synchronized void visited(boolean changed) { scannedTracks++; if (changed) skippedChangedTracks++; }
        @Override public synchronized void finding(StorageInspector.Finding finding) {
            findings.add(finding);
            counts.merge(finding.kind(), 1L, Long::sum);
        }
        synchronized void finish(String state, String error) { this.state = state; this.error = error; completedAt = Instant.now(); }
        synchronized Status status() {
            return new Status(id, mode, state, startedAt, completedAt, snapshotTracks, scannedTracks, skippedChangedTracks,
                    findings.size(), Map.copyOf(counts), error);
        }
        synchronized Page page(int offset, int limit) {
            int start = Math.min(offset, findings.size());
            int end = start + Math.min(limit, findings.size() - start);
            return new Page(List.copyOf(findings.subList(start, end)), offset, limit, findings.size(), state);
        }
    }
}
