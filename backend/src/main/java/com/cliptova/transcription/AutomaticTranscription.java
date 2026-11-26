package com.cliptova.transcription;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class AutomaticTranscription {
    private final TranscriptionStore jobs;
    private final LocalTranscription engine;

    AutomaticTranscription(TranscriptionStore jobs, LocalTranscription engine) {
        this.jobs = jobs;
        this.engine = engine;
    }

    public boolean isReady(UUID project, TranscriptionLanguage language, boolean retryFailed) {
        var video = jobs.source(project).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No source video was found for this project."));
        var state = jobs.state(video.id());
        if (state.isPresent()) {
            var job = state.get();
            var saved = jobs.readTranscript(job.id());
            if (saved.isPresent() && !saved.get().transcript().segments().isEmpty()) {
                return true;
            }
            switch (job.status()) {
                case QUEUED, EXTRACTING_AUDIO, TRANSCRIBING -> { return false; }
                case COMPLETED -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "The transcript has no timestamped speech.");
                case FAILED, CANCELLED -> {
                    if (!retryFailed) {
                        String message = job.error() != null ? job.error()
                                : "Transcription stopped before the batch could find highlights.";
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
                    }
                }
                default -> { }
            }
        }

        validateSource(video, engine, language);
        jobs.enqueue(video.id(), language, null);
        return false;
    }

    static void validateSource(TranscriptionStore.SourceVideo video, LocalTranscription engine,
                               TranscriptionLanguage language) {
        if (video.audioCodec() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "This video has no recognized audio track.");
        }
        if (!engine.supportsDuration(video.durationSeconds())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This video exceeds the configured transcription duration limit.");
        }
        if (!engine.modelAvailable(language)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The local Whisper model is missing. See the transcription setup in the README.");
        }
    }
}
