package com.hookrelay.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@Tag(name = "Health", description = "Liveness and readiness probes for Render and container orchestrators")
public class HealthController {

    private final Instant startTime = Instant.now();

    @GetMapping("/health")
    @Operation(summary = "Render platform health check probe")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "hookrelay",
                "uptimeSeconds", Instant.now().getEpochSecond() - startTime.getEpochSecond(),
                "timestamp", Instant.now().toString()
        ));
    }
}
