package com.cliptova.transcription;

import java.util.List;

public record Transcript(String language, List<Segment> segments) {
    public record Segment(long startMs, long endMs, String text) {}
}
