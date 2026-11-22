package com.cliptova.clip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
class CaptionSettingsJson {
    private final ObjectMapper mapper;

    CaptionSettingsJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    CaptionSettings decode(String json) {
        try {
            return mapper.readValue(json, CaptionSettings.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read stored caption settings", exception);
        }
    }

    String encode(CaptionSettings captions) {
        try {
            return mapper.writeValueAsString(captions);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode caption settings", exception);
        }
    }
}
