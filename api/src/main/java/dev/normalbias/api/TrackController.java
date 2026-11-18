package dev.normalbias.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.*;
import java.net.URI;

@RestController
@RequestMapping("/api/tracks")
public class TrackController {
    private final TrackStore store;
    public TrackController(TrackStore store) { this.store = store; }

    @PostMapping(consumes = "audio/mpeg", produces = "application/json")
    public ResponseEntity<Track> upload(HttpServletRequest request,
                                      @RequestParam(defaultValue = "upload.mp3") String filename) throws IOException {
        var result = store.upload(request.getInputStream(), filename, request.getContentLengthLong());
        return ResponseEntity.status(result.created() ? 201 : 200)
                .location(URI.create("/api/tracks/" + result.track().id())).body(result.track());
    }

    @GetMapping
    public TrackPage list(@RequestParam(defaultValue = "0") int offset,
                            @RequestParam(defaultValue = "50") int limit,
                            @RequestParam(defaultValue = "") String q) {
        return store.list(offset, limit, q);
    }

    @GetMapping("/{id}")
    public Track get(@PathVariable String id) { return store.get(id); }

    @PatchMapping(value = "/{id}", consumes = "application/json")
    public Track edit(@PathVariable String id, @RequestBody JsonNode changes) {
        return store.edit(id, changes);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) throws IOException {
        store.delete(id);
        return ResponseEntity.noContent().build();
    }

    @RequestMapping(value = "/{id}/audio", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void audio(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Track track = store.get(id);
        long size = track.sizeBytes();
        long start = 0, end = size - 1;
        String range = request.getHeader("Range");
        String etag = "\"" + track.id() + "\"";
        // If-Range with an unmatched validator requests the full immutable representation.
        if (request.getHeader("If-Range") != null && !etag.equals(request.getHeader("If-Range"))) range = null;
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("ETag", etag);
        if (range != null) {
            try {
                if (!range.matches("bytes=[0-9]*-[0-9]*")) throw new IllegalArgumentException();
                String[] bounds = range.substring(6).split("-", -1);
                if (bounds[0].isEmpty()) {
                    long suffix = Long.parseLong(bounds[1]);
                    if (suffix <= 0) throw new IllegalArgumentException();
                    start = Math.max(0, size - suffix);
                } else {
                    start = Long.parseLong(bounds[0]);
                    if (!bounds[1].isEmpty()) end = Math.min(end, Long.parseLong(bounds[1]));
                }
                if (start >= size || end < start) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                response.setStatus(416);
                response.setHeader("Content-Range", "bytes */" + size);
                response.setContentLengthLong(0);
                return;
            }
            response.setStatus(206);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + size);
        }
        // Open before committing headers so a missing/corrupt storage file does not look like success.
        try (RandomAccessFile file = new RandomAccessFile(store.path(track).toFile(), "r")) {
            if (file.length() != size) throw new IOException("Stored size mismatch");
            response.setContentType("audio/mpeg");
            response.setContentLengthLong(end - start + 1);
            if (request.getMethod().equals("HEAD")) return;
            file.seek(start);
            byte[] buffer = new byte[65536];
            long remaining = end - start + 1;
            OutputStream output = response.getOutputStream();
            while (remaining > 0) {
                int count = file.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) throw new EOFException("Stored file truncated");
                output.write(buffer, 0, count);
                remaining -= count;
            }
        }
    }
}
