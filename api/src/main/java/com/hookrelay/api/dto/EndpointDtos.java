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

            Integer rateLimitPerSecond,

            String transformRules
    ) {}

    public record UpdateEndpointRequest(
            String name,
            String url,
            List<String> eventTypesFilter,
            Boolean active,
            Integer failureThreshold,
            Integer rateLimitPerSecond,
            String transformRules
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
            String transformRules,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record TestTransformRequest(
            @NotBlank(message = "Sample payload JSON is required")
            String payload,

            @NotBlank(message = "Transform rules JSON is required")
            String rules
    ) {}

    public record TestTransformResponse(
            boolean valid,
            String transformedPayload,
            String errorMessage
    ) {}
}
