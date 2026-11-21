package com.cliptova.health;

public record HealthResponse(Status status, Status database) {
    public enum Status {
        UP,
        DOWN
    }
}
