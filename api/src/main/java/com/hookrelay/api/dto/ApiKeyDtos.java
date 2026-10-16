package com.hookrelay.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class ApiKeyDtos {

    public record CreateApiKeyRequest(
            @NotBlank(message = "API key name is required")
            @Size(max = 255)
            String name
    ) {}

    public record CreatedApiKeyResponse(
            UUID id,
            String name,
            String keyPrefix,
            String rawSecretKey, // Displayed once at creation!
            Instant createdAt
    ) {}

    public record ApiKeyResponse(
            UUID id,
            String name,
            String keyPrefix,
            boolean revoked,
            Instant revokedAt,
            Instant lastUsedAt,
            Instant createdAt
    ) {}
}
