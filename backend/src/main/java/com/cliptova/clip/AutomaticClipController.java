package com.cliptova.clip;

import com.cliptova.transcription.TranscriptionLanguage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/automatic-clips")
class AutomaticClipController {
    private final AutomaticClipStore runs;

    AutomaticClipController(AutomaticClipStore runs) {
        this.runs = runs;
    }

    @GetMapping
    ResponseEntity<AutomaticClipStore.Run> latest(@PathVariable UUID projectId) {
        return runs.latest(projectId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    ResponseEntity<AutomaticClipStore.Run> request(@PathVariable UUID projectId, @Valid @RequestBody Start request) {
        if (request.duration() == Clip.Duration.SAVED_RANGE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a 30-, 45-, or 60-second duration.");
        }
        var settings = new AutomaticClipStore.Settings(request.clipCount(), request.duration(), request.language(),
                request.captions().canonical());
        return ResponseEntity.accepted().body(runs.start(projectId, request.requestId(), settings));
    }

    record Start(@NotNull UUID requestId, @NotNull @Min(1) @Max(5) Integer clipCount,
                 @NotNull Clip.Duration duration, @NotNull TranscriptionLanguage language,
                 @NotNull @Valid CaptionSettings captions) {}
}
