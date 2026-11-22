package com.cliptova.highlight;

import com.cliptova.transcription.Transcript;
import java.util.List;

interface LlmProvider {
    List<Candidate> suggest(String category, String language, List<Transcript.Segment> segments);

    record Candidate(Integer firstSegment, Integer lastSegment, String title, String hook,
                     String description, String reason, Integer qualityScore) {}
}
