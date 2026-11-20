package dev.normalbias.api;

import java.time.OffsetDateTime;

public record Track(String id, String filename, String title, String artist, String album,
                    long durationMs, long sizeBytes, OffsetDateTime createdAt, String artworkRevision) {
    public Track(String id, String filename, String title, String artist, String album,
                 long durationMs, long sizeBytes, OffsetDateTime createdAt) {
        this(id, filename, title, artist, album, durationMs, sizeBytes, createdAt, "embedded");
    }
}
