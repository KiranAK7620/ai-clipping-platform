package com.cliptova.transcription;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TranscriptionController.class)
class TranscriptionControllerTest {
    private final UUID projectId = UUID.randomUUID();
    private final UUID videoId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final String url = "/api/projects/" + projectId + "/transcription";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TranscriptionStore jobs;

    @MockitoBean
    private LocalTranscription engine;

    @Test
    void acceptsAnExplicitNewJobInTheChosenLanguage() throws Exception {
        sourceWithAudio("aac");
        when(engine.supportsDuration(12)).thenReturn(true);
        when(engine.modelAvailable(TranscriptionLanguage.HINDI)).thenReturn(true);
        when(jobs.enqueue(videoId, TranscriptionLanguage.HINDI, null)).thenReturn(true);
        when(jobs.state(videoId)).thenReturn(Optional.empty()).thenReturn(state(TranscriptionStatus.QUEUED));

        mvc.perform(post(url).param("language", "HINDI"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(jobs).enqueue(videoId, TranscriptionLanguage.HINDI, null);
    }

    @Test
    void repeatRequestReusesAnActiveJob() throws Exception {
        sourceWithAudio("aac");
        when(jobs.state(videoId)).thenReturn(state(TranscriptionStatus.TRANSCRIBING));

        mvc.perform(post(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRANSCRIBING"));

        verify(jobs, never()).enqueue(videoId, TranscriptionLanguage.AUTO, null);
    }

    @Test
    void showsTimestampedTranscriptFromTheDatabase() throws Exception {
        sourceWithAudio("aac");
        when(jobs.state(videoId)).thenReturn(state(TranscriptionStatus.COMPLETED));
        when(jobs.readTranscript(jobId)).thenReturn(Optional.of(savedTranscript()));

        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.transcript.language").value("en"))
                .andExpect(jsonPath("$.transcript.segments[0].startMs").value(120))
                .andExpect(jsonPath("$.transcript.segments[0].endMs").value(1700));
    }

    @Test
    void retakeKeepsPreviousTranscriptVisibleWhileQueued() throws Exception {
        sourceWithAudio("aac");
        when(engine.supportsDuration(12)).thenReturn(true);
        when(engine.modelAvailable(TranscriptionLanguage.HINDI)).thenReturn(true);
        when(jobs.enqueue(videoId, TranscriptionLanguage.HINDI, 1)).thenReturn(true);
        when(jobs.state(videoId)).thenReturn(state(TranscriptionStatus.COMPLETED))
                .thenReturn(state(TranscriptionStatus.QUEUED));
        when(jobs.readTranscript(jobId)).thenReturn(Optional.of(savedTranscript()));

        mvc.perform(post(url).param("language", "HINDI").param("revision", "1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.transcript.segments[0].text").value("Hello, ClipTova."));
    }

    @Test
    void rejectsSilentVideosBeforeEnqueueing() throws Exception {
        sourceWithAudio(null);

        mvc.perform(post(url)).andExpect(status().isUnprocessableEntity());

        verify(jobs, never()).enqueue(videoId, TranscriptionLanguage.AUTO, null);
    }

    @Test
    void cancelsTheSelectedVideoJob() throws Exception {
        sourceWithAudio("aac");
        when(jobs.state(videoId)).thenReturn(state(TranscriptionStatus.CANCELLED));

        mvc.perform(delete(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(jobs).cancel(videoId);
    }

    private void sourceWithAudio(String codec) {
        when(jobs.source(projectId)).thenReturn(Optional.of(
                new TranscriptionStore.SourceVideo(videoId, "source.mp4", 12, codec)));
    }

    private Optional<TranscriptionStore.JobState> state(TranscriptionStatus status) {
        return Optional.of(new TranscriptionStore.JobState(jobId, status, null));
    }

    private TranscriptionStore.SavedTranscript savedTranscript() {
        return new TranscriptionStore.SavedTranscript(1, new Transcript("en", List.of(
                new Transcript.Segment(120, 1700, "Hello, ClipTova."))));
    }
}
