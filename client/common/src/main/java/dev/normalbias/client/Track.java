package dev.normalbias.client;

/** A catalogue snapshot; audio identity is immutable on the service. */
public record Track(String id, String title, String artist, String album, int durationMs) {
    public String description() {
        return (artist == null || artist.isEmpty() ? "Unknown artist" : artist)
                + (album == null || album.isEmpty() ? "" : " · " + album);
    }
}
