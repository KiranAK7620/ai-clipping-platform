package com.cliptova.clip;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.awt.Font;
import java.util.Locale;

record CaptionSettings(
        @NotNull Boolean enabled,
        @NotNull @Pattern(regexp = HEX_COLOR) String textColor,
        @NotNull @Pattern(regexp = HEX_COLOR) String backgroundColor,
        @NotNull @Min(0) @Max(100) Integer backgroundOpacity,
        @NotNull FontFamily fontFamily,
        @NotNull @Min(20) @Max(72) Integer fontSize,
        @NotNull Boolean bold,
        @NotNull Position position,
        @NotNull Alignment alignment,
        @NotNull @Pattern(regexp = HEX_COLOR) String outlineColor,
        @NotNull @Min(0) @Max(8) Integer outlineWidth) {

    private static final String HEX_COLOR = "#[0-9a-fA-F]{6}";
    private static final String INK = "#070a14";

    static CaptionSettings fromLegacy(Clip.CaptionStyle style) {
        boolean boxed = style == Clip.CaptionStyle.BOXED;
        return new CaptionSettings(true, "#ffffff", INK, boxed ? 86 : 0, FontFamily.SANS_SERIF, 38,
                true, Position.BOTTOM, Alignment.CENTER, INK, boxed ? 0 : 7);
    }

    CaptionSettings canonical() {
        if (!enabled) {
            return new CaptionSettings(false, "#ffffff", INK, 0, FontFamily.SANS_SERIF, 38,
                    true, Position.BOTTOM, Alignment.CENTER, INK, 7);
        }
        return normalized();
    }

    CaptionSettings normalized() {
        return new CaptionSettings(enabled, textColor.toLowerCase(Locale.ROOT), backgroundColor.toLowerCase(Locale.ROOT),
                backgroundOpacity, fontFamily, fontSize, bold, position, alignment, outlineColor.toLowerCase(Locale.ROOT),
                outlineWidth);
    }

    Font font() {
        return new Font(fontFamily.logicalName, bold ? Font.BOLD : Font.PLAIN, fontSize);
    }

    enum FontFamily {
        SANS_SERIF(Font.SANS_SERIF), SERIF(Font.SERIF), MONOSPACE(Font.MONOSPACED);

        private final String logicalName;

        FontFamily(String logicalName) {
            this.logicalName = logicalName;
        }
    }

    enum Position {
        TOP("118"), MIDDLE("(H-h)/2"), BOTTOM("H-h-118");

        private final String overlayY;

        Position(String overlayY) {
            this.overlayY = overlayY;
        }

        String overlayY() {
            return overlayY;
        }
    }

    enum Alignment { LEFT, CENTER, RIGHT }
}
