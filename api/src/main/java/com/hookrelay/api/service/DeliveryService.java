package com.hookrelay.api.service;

import com.hookrelay.api.dto.DeliveryDtos.*;
import com.hookrelay.core.entity.Delivery;
import com.hookrelay.core.entity.DeliveryAttempt;
import com.hookrelay.core.entity.DeliveryStatus;
import com.hookrelay.core.repository.DeliveryAttemptRepository;
import com.hookrelay.core.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final ProjectService projectService;

    public Page<DeliveryResponse> listDeliveries(
            UUID projectId,
            UUID orgId,
            String statusStr,
            int page,
            int size
    ) {
        projectService.getProjectEntity(projectId, orgId);
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Delivery> pageResult;
        if (statusStr != null && !statusStr.isBlank()) {
            DeliveryStatus status = DeliveryStatus.valueOf(statusStr.toUpperCase());
            pageResult = deliveryRepository.findByProjectIdAndStatus(projectId, status, pageRequest);
        } else {
            pageResult = deliveryRepository.findByProjectId(projectId, pageRequest);
        }

        return pageResult.map(this::toResponse);
    }

    public DeliveryDetailResponse getDeliveryDetail(UUID projectId, UUID deliveryId, UUID orgId) {
        projectService.getProjectEntity(projectId, orgId);
        Delivery delivery = deliveryRepository.findByIdAndProjectId(deliveryId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found"));

        List<DeliveryAttemptResponse> attempts = deliveryAttemptRepository
                .findByDeliveryIdOrderByAttemptNumberAsc(deliveryId).stream()
                .map(this::toAttemptResponse)
                .toList();

        return new DeliveryDetailResponse(
                toResponse(delivery),
                delivery.getEvent().getData(),
                attempts
        );
    }

    @Transactional
    public DeliveryResponse replayDelivery(UUID projectId, UUID deliveryId, UUID orgId) {
        projectService.getProjectEntity(projectId, orgId);
        Delivery delivery = deliveryRepository.findByIdAndProjectId(deliveryId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Delivery not found"));

        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);
        delivery.setUpdatedAt(Instant.now());

        return toResponse(deliveryRepository.save(delivery));
    }

    @Transactional
    public BulkReplayResponse bulkReplay(UUID projectId, UUID orgId, BulkReplayRequest req) {
        projectService.getProjectEntity(projectId, orgId);

        // Fetch deliveries to replay
        PageRequest pageReq = PageRequest.of(0, 500);
        Page<Delivery> targets;
        if (req.eventId() != null) {
            targets = deliveryRepository.findByEventId(req.eventId(), pageReq);
        } else if (req.status() != null && !req.status().isBlank()) {
            DeliveryStatus status = DeliveryStatus.valueOf(req.status().toUpperCase());
            targets = deliveryRepository.findByProjectIdAndStatus(projectId, status, pageReq);
        } else {
            targets = deliveryRepository.findByProjectId(projectId, pageReq);
        }

        int count = 0;
        Instant now = Instant.now();
        for (Delivery d : targets.getContent()) {
            d.setStatus(DeliveryStatus.PENDING);
            d.setNextAttemptAt(now);
            d.setLockedBy(null);
            d.setLockedUntil(null);
            d.setUpdatedAt(now);
            count++;
        }

        if (count > 0) {
            deliveryRepository.saveAll(targets.getContent());
        }

        return new BulkReplayResponse(count);
    }

    private DeliveryResponse toResponse(Delivery d) {
        return new DeliveryResponse(
                d.getId(),
                d.getEvent().getId(),
                d.getEvent().getType(),
                d.getEndpoint().getId(),
                d.getEndpoint().getName(),
                d.getEndpoint().getUrl(),
                d.getStatus().name(),
                d.getAttemptCount(),
                d.getMaxAttempts(),
                d.getNextAttemptAt(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }

    private DeliveryAttemptResponse toAttemptResponse(DeliveryAttempt a) {
        return new DeliveryAttemptResponse(
                a.getId(),
                a.getAttemptNumber(),
                a.getResponseStatus(),
                a.getResponseBody(),
                a.getResponseHeaders(),
                a.getLatencyMs(),
                a.getErrorMessage(),
                a.getExecutedAt()
        );
    }
}
