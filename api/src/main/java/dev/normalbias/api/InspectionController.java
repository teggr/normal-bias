package dev.normalbias.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/inspections")
public class InspectionController {
    private final InspectionService service;
    public InspectionController(InspectionService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<InspectionService.Status> start(@RequestParam(defaultValue = "quick") String mode) {
        var status = service.start(mode);
        return ResponseEntity.accepted().location(URI.create("/api/inspections/" + status.id())).body(status);
    }

    @GetMapping("/{id}")
    public InspectionService.Status status(@PathVariable UUID id) { return service.status(id); }

    @GetMapping("/{id}/findings")
    public InspectionService.Page findings(@PathVariable UUID id, @RequestParam(defaultValue = "0") int offset,
                                            @RequestParam(defaultValue = "50") int limit) {
        return service.findings(id, offset, limit);
    }
}
