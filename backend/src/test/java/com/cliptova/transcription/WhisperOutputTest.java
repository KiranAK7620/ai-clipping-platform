package com.cliptova.transcription;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WhisperOutputTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void validatesOffsetsAndKeepsTranscribedLanguage() throws IOException {
        Transcript result = WhisperOutput.parse(json.readTree("""
                {
                  "result":{"language":"hi"},
                  "transcription":[
                    {"offsets":{"from":3200,"to":5200},"text":" धन्यवाद। "},
                    {"offsets":{"from":120,"to":3000},"text":" नमस्ते। "},
                    {"offsets":{"from":-1,"to":1500},"text":"Invalid offset"},
                    {"offsets":{"from":5300,"to":5400},"text":"Outside source"},
                    {"offsets":{"from":3000,"to":3200},"text":" "}
                  ]
                }
                """), 5000);

        assertThat(result.language()).isEqualTo("hi");
        assertThat(result.segments()).containsExactly(
                new Transcript.Segment(120, 3000, "नमस्ते।"),
                new Transcript.Segment(3200, 5000, "धन्यवाद।"));
    }

    @Test
    void distinguishesEmptyTranscriptFromMalformedOutput() throws IOException {
        assertThat(WhisperOutput.parse(json.readTree("{\"transcription\":[]}"), 5000).segments()).isEmpty();
        assertThatThrownBy(() -> WhisperOutput.parse(json.readTree("{}"), 5000)).isInstanceOf(IOException.class);
    }
}
