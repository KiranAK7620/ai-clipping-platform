package com.cliptova.project;

import java.util.UUID;

record VideoResponse(
        UUID id, String originalName, long sizeBytes, double durationSeconds,
        int width, int height, Double frameRate, String videoCodec, String audioCodec) {}
