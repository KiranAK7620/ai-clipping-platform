package com.cliptova.video;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

public record VideoUpload(String originalName, String extension, String demuxer) {
    public static VideoUpload describe(String filename) {
        String path = filename == null ? "" : filename.replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1).replaceAll("[\\p{Cc}\\p{Cf}]", "").strip();
        int dot = name.lastIndexOf('.');
        if (dot <= 0) {
            throw unsupportedFormat();
        }

        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String demuxer = switch (extension) {
            case "mp4", "mov" -> "mov";
            case "mkv", "webm" -> "matroska";
            default -> throw unsupportedFormat();
        };

        String stem = name.substring(0, dot);
        int maxStemLength = 254 - extension.length();
        if (stem.codePointCount(0, stem.length()) > maxStemLength) {
            stem = stem.substring(0, stem.offsetByCodePoints(0, maxStemLength));
        }
        return new VideoUpload(stem + name.substring(dot), extension, demuxer);
    }

    private static ResponseStatusException unsupportedFormat() {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Choose an MP4, MOV, MKV or WebM video.");
    }
}
