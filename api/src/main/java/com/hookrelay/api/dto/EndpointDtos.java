package com.hookrelay.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class EndpointDtos {

    public record CreateEndpointRequest(
            @NotBlank(message = "Endpoint name is required")
            @Size(max = 255)
            String name,

            @NotBlank(message = "Endpoint destination URL is required")
            String url,

            String secret, // Optional: auto-generated whsec_ if empty

            List<String> eventTypesFilter, // Empty = all

            Integer failureThreshold,

            Integer rateLimitPerSecond
    ) {}

    public record UpdateEndpointRequest(
            String name,
            String url,
            List<String> eventTypesFilter,
            Boolean active,
            Integer failureThreshold,
            Integer rateLimitPerSecond
    ) {}

    public record EndpointResponse(
            UUID id,
            UUID projectId,
            String name,
            String url,
            String secret,
            List<String> eventTypesFilter,
            boolean active,
            int consecutiveFailures,
            int failureThreshold,
            boolean circuitBroken,
            Instant circuitBrokenAt,
            Integer rateLimitPerSecond,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
