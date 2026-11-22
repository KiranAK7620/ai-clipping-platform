package com.cliptova.highlight;

import com.cliptova.transcription.Transcript;
import com.cliptova.transcription.TranscriptionStore;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HighlightServiceTest {
    private final UUID project = UUID.randomUUID();
    private final UUID transcript = UUID.randomUUID();
    private final HighlightStore store = mock(HighlightStore.class);
    private final TranscriptionStore transcripts = mock(TranscriptionStore.class);
    private final LlmProvider provider = mock(LlmProvider.class);
    private final HighlightService service = new HighlightService(store, transcripts, provider);

    @Test
    void groundsTimestampsAcrossAnHourBoundaryAndRanksNonOverlappingCandidates() {
        long offsetMs = 3_590_000;
        when(store.source(project)).thenReturn(Optional.of(
                new HighlightStore.Source("EDUCATION", "video.mp4", offsetMs + 100_000, transcript, 3, "en")));
        var segments = List.of(new Transcript.Segment(offsetMs + 100, offsetMs + 10_000, "Opening"),
                new Transcript.Segment(offsetMs + 10_000, offsetMs + 25_000, "Explanation"),
                new Transcript.Segment(offsetMs + 30_000, offsetMs + 48_000, "Conclusion"),
                new Transcript.Segment(offsetMs + 48_000, offsetMs + 75_000, "Context"),
                new Transcript.Segment(offsetMs + 75_000, offsetMs + 100_010, "Trailing audio"));
        when(transcripts.readSegments(transcript)).thenReturn(segments);
        when(provider.suggest("EDUCATION", "en", segments)).thenReturn(List.of(
                candidate(2, 2, "Conclusion", 75), candidate(0, 1, "Explanation", 95),
                candidate(1, 1, "Duplicate", 90), candidate(4, 4, "Outside video", 100),
                candidate(0, 3, "Too long", 100), candidate(5, 5, "Invalid index", 100)));
        when(store.save(eq(transcript), anyList())).thenAnswer(call -> call.getArgument(1));

        var result = service.generate(project);

        assertThat(result.transcriptRevision()).isEqualTo(3);
        assertThat(result.highlights()).extracting(Highlight::title).containsExactly("Explanation", "Conclusion");
        var first = result.highlights().get(0);
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.startMs()).isEqualTo(3_590_100);
        assertThat(first.endMs()).isEqualTo(3_615_000);
        assertThat(first.decision()).isEqualTo(Highlight.Decision.PENDING);
    }

    @Test
    void snapsRoundedTranscriptEndToTheActualVideoEnd() {
        when(store.source(project)).thenReturn(Optional.of(
                new HighlightStore.Source("EDUCATION", "video.mp4", 50_000, transcript, 1, "en")));
        var segments = List.of(new Transcript.Segment(40_000, 50_001, "Conclusion"));
        when(transcripts.readSegments(transcript)).thenReturn(segments);
        when(provider.suggest("EDUCATION", "en", segments)).thenReturn(List.of(candidate(0, 0, "Conclusion", 90)));
        when(store.save(eq(transcript), anyList())).thenAnswer(call -> call.getArgument(1));

        assertThat(service.generate(project).highlights()).extracting(Highlight::endMs).containsExactly(50_000L);
    }

    @Test
    void reusesStoredSuggestionsForTheSameRevision() {
        when(store.source(project)).thenReturn(Optional.of(
                new HighlightStore.Source("EDUCATION", "video.mp4", 50_000, transcript, 1, "en")));
        var saved = List.of(new Highlight(UUID.randomUUID(), 1, 1, 1000, "Title", "Hook", "Description",
                "EDUCATION", "Reason", 95, Highlight.Decision.SELECTED));
        when(store.list(transcript)).thenReturn(saved);

        assertThat(service.generate(project).highlights()).isEqualTo(saved);
        verifyNoInteractions(provider, transcripts);
    }

    @Test
    void rejectsEditsBeyondTheVideo() {
        when(store.source(project)).thenReturn(Optional.of(
                new HighlightStore.Source("EDUCATION", "video.mp4", 50_000, transcript, 1, "en")));

        assertThatThrownBy(() -> service.update(project, UUID.randomUUID(), 0, 50_001, Highlight.Decision.SELECTED))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
    }

    private static LlmProvider.Candidate candidate(int start, int end, String title, int score) {
        return new LlmProvider.Candidate(start, end, title, "Hook", "Description", "Reason", score);
    }
}
