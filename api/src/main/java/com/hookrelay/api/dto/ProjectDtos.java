package com.hookrelay.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class ProjectDtos {

    public record CreateProjectRequest(
            @NotBlank(message = "Project name is required")
            @Size(max = 255)
            String name,

            String description,

            @Min(value = 1, message = "Rate limit must be at least 1/sec")
            @Max(value = 10000, message = "Rate limit cannot exceed 10,000/sec")
            Integer rateLimitPerSecond
    ) {}

    public record UpdateProjectRequest(
            String name,
            String description,
            @Min(1) @Max(10000) Integer rateLimitPerSecond
    ) {}

    public record ProjectResponse(
            UUID id,
            UUID orgId,
            String name,
            String description,
            int rateLimitPerSecond,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
