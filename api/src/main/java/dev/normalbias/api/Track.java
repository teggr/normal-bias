package dev.normalbias.api;

import java.time.OffsetDateTime;

public record Track(String id, String filename, String title, String artist, String album,
                    long durationMs, long sizeBytes, OffsetDateTime createdAt) {}
