package com.cliptova.highlight;

import java.util.UUID;

record Highlight(UUID id, int rank, long startMs, long endMs, String title, String hook, String description,
                 String category, String reason, int qualityScore, Decision decision) {
    enum Decision { PENDING, SELECTED, REJECTED }
}
