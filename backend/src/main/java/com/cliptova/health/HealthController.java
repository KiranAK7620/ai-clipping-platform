package com.cliptova.health;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.cliptova.health.HealthResponse.Status.DOWN;
import static com.cliptova.health.HealthResponse.Status.UP;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final DatabaseHealthCheck databaseHealthCheck;

    public HealthController(DatabaseHealthCheck databaseHealthCheck) {
        this.databaseHealthCheck = databaseHealthCheck;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        boolean available = databaseHealthCheck.isAvailable();
        return ResponseEntity.status(available ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(available ? new HealthResponse(UP, UP) : new HealthResponse(DOWN, DOWN));
    }
}
