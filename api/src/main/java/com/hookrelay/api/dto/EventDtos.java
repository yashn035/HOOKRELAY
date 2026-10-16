package com.hookrelay.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class EventDtos {

    public record IngestEventRequest(
            @NotBlank(message = "Event type is required")
            @Size(max = 255)
            String type,

            @NotNull(message = "Event data payload is required")
            JsonNode data,

            String idempotencyKey
    ) {}

    public record BatchIngestRequest(
            @NotNull(message = "Events list cannot be null")
            @Size(min = 1, max = 500, message = "Batch size must be between 1 and 500 events")
            List<IngestEventRequest> events
    ) {}

    public record IngestEventResponse(
            UUID eventId,
            String type,
            int deliveriesCreated,
            boolean isDuplicate,
            Instant createdAt
    ) {}

    public record BatchIngestResponse(
            int totalEvents,
            int totalDeliveriesCreated,
            List<IngestEventResponse> results
    ) {}

    public record EventDetailResponse(
            UUID id,
            UUID projectId,
            String type,
            String data,
            String idempotencyKey,
            Instant createdAt,
            int totalDeliveries
    ) {}
}
