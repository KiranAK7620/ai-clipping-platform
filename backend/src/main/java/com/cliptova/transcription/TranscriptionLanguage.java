package com.cliptova.transcription;

enum TranscriptionLanguage {
    AUTO("auto"), ENGLISH("en"), HINDI("hi"), MARATHI("mr");

    private final String code;

    TranscriptionLanguage(String code) {
        this.code = code;
    }

    String code() {
        return code;
    }
}
