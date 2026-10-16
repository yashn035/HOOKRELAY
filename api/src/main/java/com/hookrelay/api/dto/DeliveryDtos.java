package com.hookrelay.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DeliveryDtos {

    public record DeliveryResponse(
            UUID id,
            UUID eventId,
            String eventType,
            UUID endpointId,
            String endpointName,
            String endpointUrl,
            String status,
            int attemptCount,
            int maxAttempts,
            Instant nextAttemptAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record DeliveryAttemptResponse(
            UUID id,
            int attemptNumber,
            Integer responseStatus,
            String responseBody,
            String responseHeaders,
            Long latencyMs,
            String errorMessage,
            Instant executedAt
    ) {}

    public record DeliveryDetailResponse(
            DeliveryResponse delivery,
            String eventPayload,
            List<DeliveryAttemptResponse> attempts
    ) {}

    public record BulkReplayRequest(
            UUID eventId,
            UUID endpointId,
            Instant since,
            String status // e.g. FAILED, DEAD_LETTER
    ) {}

    public record BulkReplayResponse(
            int count
    ) {}
}
