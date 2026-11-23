package dev.normalbias.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
class AuthErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> failure(ResponseStatusException failure, HttpServletRequest request) {
        // Preserve the earlier catalogue error contract; authentication reasons are fixed safe strings.
        if (!request.getServletPath().startsWith("/api/auth/") && !request.getServletPath().endsWith("/playback-grants")) throw failure;
        var response = ResponseEntity.status(failure.getStatusCode()).cacheControl(CacheControl.noStore());
        if (failure.getStatusCode().value() == 429) response.header("Retry-After", "900");
        return response.body(Map.of("status", failure.getStatusCode().value(), "message", failure.getReason() == null ? "Request refused" : failure.getReason(),
                "_links", Map.of("session", AuthPolicy.link("/api/auth/session", "GET"))));
    }
}
