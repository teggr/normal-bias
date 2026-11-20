package dev.normalbias.api;

import java.io.IOException;
import java.util.List;

/** Metadata-only matching boundary. Audio bytes and paths never cross it. */
public interface AlbumProvider {
    record Candidate(String id, String title, String artist, String date, String country,
                     String edition, int trackCount, String groupId, String providerUrl) {}
    List<Candidate> search(String artist, String album) throws IOException, InterruptedException;
    byte[] cover(String releaseId) throws IOException, InterruptedException;
}
