package com.cliptova.transcription;

import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/transcription")
class TranscriptionController {
    private final TranscriptionStore jobs;
    private final LocalTranscription engine;

    TranscriptionController(TranscriptionStore jobs, LocalTranscription engine) {
        this.jobs = jobs;
        this.engine = engine;
    }

    @GetMapping
    TranscriptionResponse status(@PathVariable UUID projectId) {
        return statusFor(source(projectId).id());
    }

    @PostMapping
    ResponseEntity<TranscriptionResponse> request(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "AUTO") TranscriptionLanguage language,
            @RequestParam(required = false) @Min(1) Integer revision) {
        var video = source(projectId);
        var existing = jobs.state(video.id());
        if (existing.isPresent()) {
            var state = existing.get();
            if (state.status() != TranscriptionStatus.CANCELLED && state.status() != TranscriptionStatus.FAILED
                    && !(state.status() == TranscriptionStatus.COMPLETED && revision != null)) {
                return ResponseEntity.ok(toResponse(state));
            }
        }

        AutomaticTranscription.validateSource(video, engine, language);

        boolean accepted = jobs.enqueue(video.id(), language, revision);
        return ResponseEntity.status(accepted ? HttpStatus.ACCEPTED : HttpStatus.OK).body(statusFor(video.id()));
    }

    @DeleteMapping
    TranscriptionResponse cancel(@PathVariable UUID projectId) {
        UUID videoId = source(projectId).id();
        jobs.cancel(videoId);
        return statusFor(videoId);
    }

    private TranscriptionStore.SourceVideo source(UUID projectId) {
        return jobs.source(projectId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No source video was found for this project."));
    }

    private TranscriptionResponse statusFor(UUID videoId) {
        return jobs.state(videoId).map(this::toResponse)
                .orElseGet(() -> new TranscriptionResponse(TranscriptionStatus.NOT_STARTED, null, null, null));
    }

    private TranscriptionResponse toResponse(TranscriptionStore.JobState state) {
        return jobs.readTranscript(state.id())
                .map(saved -> new TranscriptionResponse(state.status(), state.error(), saved.transcript(), saved.revision()))
                .orElseGet(() -> new TranscriptionResponse(state.status(), state.error(), null, null));
    }

    record TranscriptionResponse(TranscriptionStatus status, String error, Transcript transcript, Integer revision) {}
}
