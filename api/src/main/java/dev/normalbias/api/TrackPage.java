package dev.normalbias.api;

import java.util.List;

public record TrackPage(List<Track> items, int offset, int limit, long total) {}
