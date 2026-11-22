package com.cliptova.clip;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/caption-themes")
class CaptionThemeController {
    private final CaptionThemeStore themes;

    CaptionThemeController(CaptionThemeStore themes) {
        this.themes = themes;
    }

    @GetMapping
    List<CaptionThemeStore.Theme> list() {
        return themes.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CaptionThemeStore.Theme save(@Valid @RequestBody SaveTheme request) {
        return themes.save(request.name().strip(), request.captions().normalized()).orElseThrow(CaptionThemeController::nameConflict);
    }

    @PutMapping("/{id}")
    CaptionThemeStore.Theme update(@PathVariable UUID id, @Valid @RequestBody SaveTheme request) {
        try {
            return themes.update(id, request.name().strip(), request.captions().normalized()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Caption theme not found."));
        } catch (DuplicateKeyException exception) {
            throw nameConflict();
        }
    }

    private static ResponseStatusException nameConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "That theme name is already in use. Choose another name.");
    }

    record SaveTheme(@NotBlank @Size(max = 60) String name, @NotNull @Valid CaptionSettings captions) {}
}
