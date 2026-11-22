package com.cliptova.clip;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/clips")
class ClipController {
    private static final MediaType MP4 = MediaType.parseMediaType("video/mp4");
    private final ClipStore store;
    private final ClipFiles files;

    ClipController(ClipStore store, ClipFiles files) {
        this.store = store;
        this.files = files;
    }

    @GetMapping
    List<Clip> list(@PathVariable UUID projectId) {
        return store.list(projectId);
    }

    @PostMapping
    ResponseEntity<Clip> queue(@PathVariable UUID projectId, @Valid @RequestBody Generate request) {
        var captions = request.captions() != null ? request.captions().canonical()
                : CaptionSettings.fromLegacy(request.captionStyle());
        var clip = store.enqueue(projectId, request.highlightId(), request.duration(), captions);
        return ResponseEntity.status(clip.status() == Clip.Status.READY ? HttpStatus.OK : HttpStatus.ACCEPTED).body(clip);
    }

    @GetMapping("/{id}/preview")
    ResponseEntity<Resource> preview(@PathVariable UUID projectId, @PathVariable UUID id) {
        return serve(projectId, id, false);
    }

    @GetMapping("/{id}/download")
    ResponseEntity<Resource> download(@PathVariable UUID projectId, @PathVariable UUID id) {
        return serve(projectId, id, true);
    }

    private ResponseEntity<Resource> serve(UUID projectId, UUID id, boolean download) {
        var path = store.findFile(projectId, id).flatMap(files::find).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "The generated clip is unavailable."));
        var response = ResponseEntity.ok().contentType(MP4).cacheControl(CacheControl.noStore());
        if (download) {
            response.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                    .filename("cliptova-" + id + ".mp4").build().toString());
        }
        return response.body(new FileSystemResource(path));
    }

    record Generate(@NotNull UUID highlightId, @NotNull Clip.Duration duration, Clip.CaptionStyle captionStyle,
                    @Valid CaptionSettings captions) {}
}
