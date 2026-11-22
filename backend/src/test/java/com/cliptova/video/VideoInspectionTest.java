package com.cliptova.video;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoInspectionTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void extractsMetadataAndSkipsAttachedArtwork() throws Exception {
        VideoMetadata metadata = Ffprobe.readMetadata(json.readTree("""
                {
                  "format": {"duration": "12.34"},
                  "streams": [
                    {"codec_type": "video", "codec_name": "mjpeg", "width": 300, "height": 300,
                     "disposition": {"attached_pic": 1}},
                    {"codec_type": "video", "codec_name": "h264", "width": 1920, "height": 1080,
                     "avg_frame_rate": "30000/1001"},
                    {"codec_type": "audio", "codec_name": "aac"}
                  ]
                }
                """));

        assertThat(metadata).isEqualTo(new VideoMetadata(12.34, 1920, 1080, 30000.0 / 1001, "h264", "aac"));
    }

    @Test
    void permitsSilentVideoWithoutReportedFrameRate() throws Exception {
        VideoMetadata metadata = Ffprobe.readMetadata(json.readTree("""
                {
                  "format": {"duration": "1.0"},
                  "streams": [{"codec_type": "video", "codec_name": "vp9", "width": 640, "height": 480,
                               "avg_frame_rate": "0/0"}]
                }
                """));

        assertThat(metadata.audioCodec()).isNull();
        assertThat(metadata.frameRate()).isNull();
    }

    @Test
    void rejectsAudioWithoutAVideoStream() throws Exception {
        var data = json.readTree("""
                {"format": {"duration": "1"}, "streams": [{"codec_type": "audio", "codec_name": "aac"}]}
                """);

        assertThatThrownBy(() -> Ffprobe.readMetadata(data))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void stripsClientPathAndNormalizesTheFormatForInspection() {
        assertThat(VideoUpload.describe("..\\..\\clip.MP4"))
                .isEqualTo(new VideoUpload("clip.MP4", "mp4", "mov"));
    }

    @Test
    void rejectsUnsupportedExtension() {
        assertThatThrownBy(() -> VideoUpload.describe("playlist.m3u8"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(415));
    }
}
