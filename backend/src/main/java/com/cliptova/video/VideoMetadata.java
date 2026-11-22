package com.cliptova.video;

public record VideoMetadata(
        double durationSeconds, int width, int height, Double frameRate, String videoCodec, String audioCodec) {}
