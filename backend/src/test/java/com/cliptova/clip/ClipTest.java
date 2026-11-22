package com.cliptova.clip;

import com.cliptova.transcription.Transcript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ClipTest {
    @TempDir
    Path work;

    @Test
    void resolvesPresetsAtTheSourceBoundaryAndPreservesTheExactSavedRange() {
        assertThat(Clip.Duration.SAVED_RANGE.resolve(448_440, 458_440, 487_966))
                .isEqualTo(new Clip.Range(448_440, 458_440));
        assertThat(Clip.Duration.SECONDS_30.resolve(448_440, 458_440, 487_966))
                .isEqualTo(new Clip.Range(448_440, 478_440));
        assertThat(Clip.Duration.SECONDS_45.resolve(448_440, 458_440, 487_966))
                .isEqualTo(new Clip.Range(442_966, 487_966));
        assertThat(Clip.Duration.SECONDS_60.resolve(4_000, 7_000, 8_000))
                .isEqualTo(new Clip.Range(0, 8_000));
        assertThat(new Clip.Range(0, 60_001).validFor(90_000)).isFalse();
    }

    @Test
    void rebasesCaptionsAndClampsBothEndsToTheClip() {
        var segments = List.of(new Transcript.Segment(1000, 2000, "Before"),
                new Transcript.Segment(2500, 3200, "  first\nline "),
                new Transcript.Segment(3000, 5500, "हिंदी text"),
                new Transcript.Segment(6000, 7000, "After"));
        assertThat(ClipCaptions.cues(segments, 3000, 5000)).containsExactly(
                new ClipCaptions.Cue(0, 200, "first line"),
                new ClipCaptions.Cue(200, 2000, "हिंदी text"));
    }

    @Test
    void rendersCustomCaptionColorsAndRightAlignment() throws Exception {
        var settings = new CaptionSettings(true, "#f7dd52", "#1238ab", 100, CaptionSettings.FontFamily.SERIF,
                58, false, CaptionSettings.Position.TOP, CaptionSettings.Alignment.RIGHT, "#010203", 0);
        var claim = new ClipStore.Claim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "test.mp4", 0, 2000, settings);
        ClipCaptions.write(work, List.of(new Transcript.Segment(0, 1900, "Style")), claim);
        var image = ImageIO.read(work.resolve("caption-0.png").toFile());

        assertThat(Arrays.stream(image.getRGB(0, 0, 360, 230, null, 0, 360)).allMatch(pixel -> pixel == 0)).isTrue();
        var pixels = image.getRGB(360, 0, 360, 230, null, 0, 360);
        assertThat(pixels).contains(0xfff7dd52, 0xff1238ab);
        assertThat(settings.font().getSize()).isEqualTo(58);
        assertThat(settings.font().isBold()).isFalse();
    }

    @Test
    void normalizesColorsAndDeduplicatesDisabledCaptionStyles() {
        var custom = new CaptionSettings(true, "#AA33BB", "#3399DD", 45, CaptionSettings.FontFamily.MONOSPACE,
                52, false, CaptionSettings.Position.MIDDLE, CaptionSettings.Alignment.LEFT, "#FFAA77", 3);
        var disabled = new CaptionSettings(false, "#AA33BB", "#3399DD", 45, CaptionSettings.FontFamily.MONOSPACE,
                52, false, CaptionSettings.Position.MIDDLE, CaptionSettings.Alignment.LEFT, "#FFAA77", 3);
        assertThat(custom.canonical().textColor()).isEqualTo("#aa33bb");
        assertThat(disabled.canonical()).isEqualTo(disabled.canonical().canonical());
        assertThat(disabled.canonical().enabled()).isFalse();
        assertThat(disabled.canonical().fontFamily()).isEqualTo(CaptionSettings.FontFamily.SANS_SERIF);
    }

    @Test
    void createsTimedTransparentOverlaysForCaptionGaps() throws Exception {
        var claim = new ClipStore.Claim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "test.mp4", 3000, 5000, CaptionSettings.fromLegacy(Clip.CaptionStyle.BOXED));
        var manifest = ClipCaptions.write(work, List.of(new Transcript.Segment(3500, 4200, "हिंदी and English")), claim);

        assertThat(Files.readString(manifest)).containsSubsequence("file blank.png", "duration 0.500",
                "file caption-0.png", "duration 0.700", "file blank.png", "duration 0.800", "file blank.png");
        var image = ImageIO.read(work.resolve("caption-0.png").toFile());
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat(image.getRGB(360, 115) >>> 24).isPositive();
        assertThat(image.getRGB(0, 0) >>> 24).isZero();
    }
}
