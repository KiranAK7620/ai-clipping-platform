package com.cliptova.highlight;

import com.cliptova.video.OriginalVideoStorage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/highlights")
class HighlightController {
    private final HighlightService highlights;
    private final OriginalVideoStorage originals;

    HighlightController(HighlightService highlights, OriginalVideoStorage originals) {
        this.highlights = highlights;
        this.originals = originals;
    }

    @GetMapping
    HighlightService.Result list(@PathVariable UUID projectId) {
        return highlights.list(projectId);
    }

    @PostMapping
    WebAsyncTask<HighlightService.Result> generate(@PathVariable UUID projectId) {
        return new WebAsyncTask<>(Duration.ofMinutes(20).toMillis(), () -> highlights.generate(projectId));
    }

    @PatchMapping("/{id}")
    Highlight update(@PathVariable UUID projectId, @PathVariable UUID id, @RequestBody @Valid Edit edit) {
        return highlights.update(projectId, id, edit.startMs(), edit.endMs(), edit.decision());
    }

    @GetMapping("/source")
    ResponseEntity<Resource> preview(@PathVariable UUID projectId) {
        String key = highlights.source(projectId).storageKey();
        try {
            Resource resource = new FileSystemResource(originals.findOriginal(key));
            String type = switch (key.substring(key.lastIndexOf('.') + 1)) {
                case "mp4" -> "video/mp4";
                case "webm" -> "video/webm";
                case "mov" -> "video/quicktime";
                default -> "video/x-matroska";
            };
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(type))
                    .cacheControl(CacheControl.noStore()).body(resource);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The original video is unavailable.");
        }
    }

    record Edit(@NotNull Long startMs, @NotNull Long endMs, @NotNull Highlight.Decision decision) {}
}
