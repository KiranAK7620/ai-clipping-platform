package com.cliptova.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
class ProjectController {
    private final ProjectService service;

    ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    ProjectService.ProjectList list(@RequestParam(defaultValue = "0") @Min(0) @Max(10_000_000) int page) {
        return service.list(page);
    }

    @GetMapping("/{projectId}")
    ProjectResponse get(@PathVariable UUID projectId) {
        return service.get(projectId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ProjectResponse> upload(@Valid @ModelAttribute UploadForm form) {
        var result = service.upload(form.projectId(), form.name(), form.category(), form.file());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.project());
    }

    record UploadForm(@NotNull UUID projectId, @NotBlank @Size(max = 160) String name,
                      @NotNull ProjectCategory category, @NotNull MultipartFile file) {}
}
