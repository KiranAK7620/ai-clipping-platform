package com.cliptova.clip;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaptionThemeController.class)
class CaptionThemeControllerTest {
    private static final String URL = "/api/caption-themes";
    private final CaptionSettings settings = new CaptionSettings(false, "#FFD15A", "#142C70", 80,
            CaptionSettings.FontFamily.SERIF, 51, false, CaptionSettings.Position.TOP,
            CaptionSettings.Alignment.RIGHT, "#102030", 3);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean CaptionThemeStore store;

    @Test
    void savesAndListsTheCompleteThemeEvenWhenCaptionsAreOff() throws Exception {
        var saved = new CaptionThemeStore.Theme(UUID.randomUUID(), "Amber Serif", settings.normalized());
        when(store.save("Amber Serif", settings.normalized())).thenReturn(Optional.of(saved));
        when(store.list()).thenReturn(List.of(saved));
        var body = new CaptionThemeController.SaveTheme(" Amber Serif ", settings);

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.captions.textColor").value("#ffd15a"))
                .andExpect(jsonPath("$.captions.enabled").value(false));
        mvc.perform(get(URL)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Amber Serif"))
                .andExpect(jsonPath("$[0].captions.fontSize").value(51));
    }

    @Test
    void rejectsAnAlreadySavedName() throws Exception {
        when(store.save("Amber Serif", settings.normalized())).thenReturn(Optional.empty());

        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                        new CaptionThemeController.SaveTheme("Amber Serif", settings))))
                .andExpect(status().isConflict());
    }

    @Test
    void updatesTheSavedThemeWithoutChangingItsId() throws Exception {
        var id = UUID.randomUUID();
        var updated = new CaptionThemeStore.Theme(id, "Soft Amber", settings.normalized());
        when(store.update(id, "Soft Amber", settings.normalized())).thenReturn(Optional.of(updated));

        mvc.perform(put(URL + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new CaptionThemeController.SaveTheme(" Soft Amber ", settings))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Soft Amber"))
                .andExpect(jsonPath("$.captions.textColor").value("#ffd15a"));
    }

    @Test
    void rejectsRenamingToAnExistingTheme() throws Exception {
        var id = UUID.randomUUID();
        when(store.update(id, "Amber Serif", settings.normalized())).thenThrow(new DuplicateKeyException("duplicate"));

        mvc.perform(put(URL + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(new CaptionThemeController.SaveTheme("Amber Serif", settings))))
                .andExpect(status().isConflict());
    }

    @Test
    void validatesTheNameAndSettingsBeforeSaving() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":" ","captions":{"enabled":true}}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(store);
    }
}
