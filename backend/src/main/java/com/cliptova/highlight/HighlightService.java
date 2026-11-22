package com.cliptova.highlight;

import com.cliptova.transcription.Transcript;
import com.cliptova.transcription.TranscriptionStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
class HighlightService {
    private static final int MAX_TEXT_CHARS = 5000;
    private static final int MAX_WINDOW_SEGMENTS = 65;
    private static final int MAX_RESULTS = 12;
    private static final long MAX_SUGGESTION_MS = 60_000;

    private final HighlightStore store;
    private final TranscriptionStore transcripts;
    private final LlmProvider provider;
    private final AtomicBoolean generating = new AtomicBoolean();

    HighlightService(HighlightStore store, TranscriptionStore transcripts, LlmProvider provider) {
        this.store = store;
        this.transcripts = transcripts;
        this.provider = provider;
    }

    HighlightStore.Source source(UUID projectId) {
        return store.source(projectId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No source video was found for this project."));
    }

    Result list(UUID projectId) {
        var source = source(projectId);
        return new Result(source.revision(), existing(source));
    }

    Result generate(UUID projectId) {
        var source = requireTranscript(projectId);
        var existing = existing(source);
        if (!existing.isEmpty()) {
            return new Result(source.revision(), existing);
        }
        if (!generating.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Highlight generation is already running. Try again when it finishes.");
        }
        try {
            existing = existing(source);
            if (!existing.isEmpty()) {
                return new Result(source.revision(), existing);
            }
            var segments = transcripts.readSegments(source.transcriptId());
            var candidates = rank(suggest(source, segments));
            if (candidates.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No complete highlight with valid timestamps was found in this transcript.");
            }
            return new Result(source.revision(), store.save(source.transcriptId(), candidates));
        } finally {
            generating.set(false);
        }
    }

    Highlight update(UUID projectId, UUID id, long startMs, long endMs, Highlight.Decision decision) {
        var source = requireTranscript(projectId);
        if (startMs < 0 || endMs <= startMs || endMs > source.durationMs()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Choose a start and end time within the video, with the end after the start.");
        }
        return store.update(source.transcriptId(), id, startMs, endMs, decision).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "This highlight is unavailable for the current transcript."));
    }

    private HighlightStore.Source requireTranscript(UUID projectId) {
        var source = source(projectId);
        if (source.transcriptId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transcribe the video before generating highlights.");
        }
        return source;
    }

    private List<Highlight> existing(HighlightStore.Source source) {
        return source.transcriptId() == null ? List.of() : store.list(source.transcriptId());
    }

    private List<Highlight> suggest(HighlightStore.Source source, List<Transcript.Segment> segments) {
        var candidates = new ArrayList<Highlight>();
        long deadline = System.nanoTime() + Duration.ofMinutes(15).toNanos();
        int start = 0;
        while (start < segments.size()) {
            if (System.nanoTime() >= deadline) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Local highlight generation took too long.");
            }
            int end = windowEnd(segments, start);
            var window = segments.subList(start, end);
            for (var candidate : provider.suggest(source.category(), source.language(), window)) {
                var highlight = validate(source, window, candidate);
                if (highlight != null) {
                    candidates.add(highlight);
                }
            }
            if (end == segments.size()) {
                break;
            }
            // Overlap windows so a complete thought can straddle a window boundary.
            start = Math.max(start + 1, end - Math.min(8, window.size() / 4));
        }
        return candidates;
    }

    private static int windowEnd(List<Transcript.Segment> segments, int start) {
        int end = start + 1;
        int chars = segments.get(start).text().length();
        while (end < segments.size() && end - start < MAX_WINDOW_SEGMENTS) {
            chars += segments.get(end).text().length();
            if (chars > MAX_TEXT_CHARS) {
                break;
            }
            end++;
        }
        return end;
    }

    private static Highlight validate(HighlightStore.Source source, List<Transcript.Segment> segments,
                                      LlmProvider.Candidate candidate) {
        if (candidate == null || candidate.firstSegment() == null || candidate.lastSegment() == null
                || candidate.firstSegment() < 0 || candidate.lastSegment() < candidate.firstSegment()
                || candidate.lastSegment() >= segments.size() || candidate.qualityScore() == null
                || candidate.qualityScore() < 0 || candidate.qualityScore() > 100
                || invalid(candidate.title(), 120) || invalid(candidate.hook(), 240)
                || invalid(candidate.description(), 400) || invalid(candidate.reason(), 400)) {
            return null;
        }
        long startMs = segments.get(candidate.firstSegment()).startMs();
        long endMs = segments.get(candidate.lastSegment()).endMs();
        // Whisper rounds source duration to the nearest millisecond. The preview uses the last whole millisecond.
        if (endMs - 1 == source.durationMs()) {
            endMs = source.durationMs();
        }
        if (startMs < 0 || endMs > source.durationMs() || endMs <= startMs
                || endMs - startMs > MAX_SUGGESTION_MS) {
            return null;
        }
        return new Highlight(null, 0, startMs, endMs, candidate.title().strip(), candidate.hook().strip(),
                candidate.description().strip(), source.category(), candidate.reason().strip(),
                candidate.qualityScore(), Highlight.Decision.PENDING);
    }

    private static boolean invalid(String text, int maxLength) {
        return text == null || text.isBlank() || text.strip().length() > maxLength || text.indexOf(0) >= 0;
    }

    private static List<Highlight> rank(List<Highlight> candidates) {
        candidates.sort(Comparator.comparingInt(Highlight::qualityScore).reversed()
                .thenComparingLong(item -> Math.abs(item.endMs() - item.startMs() - 30_000))
                .thenComparingLong(Highlight::startMs));
        var ranked = new ArrayList<Highlight>();
        for (var candidate : candidates) {
            if (ranked.stream().noneMatch(item -> substantiallyOverlaps(item, candidate))) {
                ranked.add(new Highlight(null, ranked.size() + 1, candidate.startMs(), candidate.endMs(),
                        candidate.title(), candidate.hook(), candidate.description(), candidate.category(),
                        candidate.reason(), candidate.qualityScore(), candidate.decision()));
                if (ranked.size() == MAX_RESULTS) {
                    break;
                }
            }
        }
        return ranked;
    }

    private static boolean substantiallyOverlaps(Highlight a, Highlight b) {
        long overlap = Math.min(a.endMs(), b.endMs()) - Math.max(a.startMs(), b.startMs());
        long shorter = Math.min(a.endMs() - a.startMs(), b.endMs() - b.startMs());
        return overlap >= shorter * 0.7;
    }

    record Result(Integer transcriptRevision, List<Highlight> highlights) {}
}
