package com.cliptova.transcription;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class WhisperOutput {
    private WhisperOutput() {}

    static Transcript parse(JsonNode output, long durationMs) throws IOException {
        if (output == null || !output.path("transcription").isArray()) {
            throw new IOException("Unrecognized Whisper output");
        }

        String language = output.path("result").path("language").asText();
        if (!language.matches("[a-z]{2,3}")) {
            language = null;
        }

        var segments = new ArrayList<Transcript.Segment>();
        for (JsonNode field : output.path("transcription")) {
            JsonNode offsets = field.path("offsets");
            long start = offsets.path("from").asLong(-1);
            long end = Math.min(durationMs, offsets.path("to").asLong(-1));
            String text = field.path("text").asText().replace("\0", "").strip();
            if (start >= 0 && end > start && !text.isBlank()) {
                segments.add(new Transcript.Segment(start, end, text));
            }
        }
        segments.sort(Comparator.comparingLong(Transcript.Segment::startMs));
        return new Transcript(language, List.copyOf(segments));
    }
}
