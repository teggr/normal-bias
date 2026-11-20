package dev.normalbias.api;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/album-artwork")
public class AlbumArtworkController {
    private final AlbumArtwork service;
    public AlbumArtworkController(AlbumArtwork service) { this.service = service; }
    @GetMapping("/suggestions/{trackId}")
    public AlbumArtwork.Selection suggestions(@PathVariable String trackId, @RequestParam(defaultValue="0") int offset,
            @RequestParam(defaultValue="50") int limit) { return service.suggestions(trackId, offset, limit); }
    @GetMapping("/tracks/{trackId}")
    public AlbumArtwork.Provenance provenance(@PathVariable String trackId) { return service.provenance(trackId); }
    @PostMapping("/matches")
    public ResponseEntity<AlbumArtwork.Status> start(@RequestBody AlbumArtwork.Start request) {
        return ResponseEntity.accepted().body(service.start(request));
    }
    @GetMapping("/matches/{id}") public AlbumArtwork.Status status(@PathVariable String id) { return service.status(id); }
    @GetMapping("/matches/{id}/candidates") public AlbumArtwork.Candidates candidates(@PathVariable String id,
            @RequestParam(defaultValue="0") int offset, @RequestParam(defaultValue="5") int limit) {
        return service.candidates(id, offset, limit);
    }
    @GetMapping("/matches/{id}/candidates/{candidate}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable String id, @PathVariable String candidate) throws IOException, InterruptedException {
        byte[] image = service.preview(id, candidate);
        return image == null ? ResponseEntity.notFound().build() : ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(image);
    }
    @PostMapping("/matches/{id}/accept") public AlbumArtwork.Result accept(@PathVariable String id,
            @RequestBody AlbumArtwork.Accept request) throws IOException { return service.accept(id, request); }
}
