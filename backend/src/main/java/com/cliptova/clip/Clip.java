package com.cliptova.clip;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

record Clip(UUID id, UUID highlightId, String title, long startMs, long endMs,
            CaptionSettings captions, Status status, String error) {
    @JsonProperty("captionStyle")
    CaptionStyle captionStyle() {
        return captions.backgroundOpacity() > 0 ? CaptionStyle.BOXED : CaptionStyle.OUTLINED;
    }

    enum CaptionStyle { OUTLINED, BOXED }
    enum Status { QUEUED, RENDERING, READY, FAILED }

    enum Duration {
        SAVED_RANGE(0), SECONDS_30(30_000), SECONDS_45(45_000), SECONDS_60(60_000);

        private final long milliseconds;

        Duration(long milliseconds) {
            this.milliseconds = milliseconds;
        }

        Range resolve(long start, long end, long sourceDuration) {
            if (this == SAVED_RANGE) {
                return new Range(start, end);
            }
            long length = Math.min(milliseconds, sourceDuration);
            long adjustedStart = Math.min(start, sourceDuration - length);
            return new Range(adjustedStart, adjustedStart + length);
        }
    }

    record Range(long startMs, long endMs) {
        boolean mostlyOverlaps(Range other) {
            long overlap = Math.min(endMs, other.endMs) - Math.max(startMs, other.startMs);
            long shorter = Math.min(endMs - startMs, other.endMs - other.startMs);
            return overlap > shorter / 2;
        }

        boolean validFor(long durationMs) {
            return startMs >= 0 && endMs > startMs && endMs <= durationMs && endMs - startMs <= 60_000;
        }
    }
}
