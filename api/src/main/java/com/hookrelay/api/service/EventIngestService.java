package com.hookrelay.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookrelay.api.dto.EventDtos.*;
import com.hookrelay.api.exception.RateLimitExceededException;
import com.hookrelay.core.entity.Delivery;
import com.hookrelay.core.entity.DeliveryStatus;
import com.hookrelay.core.entity.Endpoint;
import com.hookrelay.core.entity.Event;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.repository.DeliveryRepository;
import com.hookrelay.core.repository.EndpointRepository;
import com.hookrelay.core.repository.EventRepository;
import com.hookrelay.core.service.HookRelayMetricsService;
import com.hookrelay.core.service.IdempotencyService;
import com.hookrelay.core.service.RateLimitResult;
import com.hookrelay.core.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class EventIngestService {

    private final EventRepository eventRepository;
    private final DeliveryRepository deliveryRepository;
    private final EndpointRepository endpointRepository;
    private final RateLimitService rateLimitService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final HookRelayMetricsService metricsService;

    public EventIngestService(
            EventRepository eventRepository,
            DeliveryRepository deliveryRepository,
            EndpointRepository endpointRepository,
            RateLimitService rateLimitService,
            IdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            HookRelayMetricsService metricsService
    ) {
        this.eventRepository = eventRepository;
        this.deliveryRepository = deliveryRepository;
        this.endpointRepository = endpointRepository;
        this.rateLimitService = rateLimitService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    @Transactional
    public IngestEventResponse ingestEvent(Project project, IngestEventRequest req, String headerIdempotencyKey) {
        // 1. Rate limiting check
        int rateLimit = project.getRateLimitPerSecond();
        RateLimitResult rateLimitResult = rateLimitService.checkRateLimit(project.getId(), rateLimit);
        if (!rateLimitResult.allowed()) {
            if (metricsService != null) {
                metricsService.recordRateLimitRejected(project.getId());
            }
            throw new RateLimitExceededException(rateLimitResult);
        }

        // Determine effective idempotency key (header takes precedence)
        String idempotencyKey = (headerIdempotencyKey != null && !headerIdempotencyKey.isBlank())
                ? headerIdempotencyKey.trim()
                : req.idempotencyKey();

        // 2. Idempotency cache check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<String> existingId = idempotencyService.getExistingEventId(project.getId(), idempotencyKey);
            if (existingId.isPresent()) {
                UUID existingUuid = UUID.fromString(existingId.get());
                return new IngestEventResponse(existingUuid, req.type(), 0, true, Instant.now());
            }

            // DB fallback idempotency check
            Optional<Event> existingEvent = eventRepository.findByProjectIdAndIdempotencyKey(project.getId(), idempotencyKey);
            if (existingEvent.isPresent()) {
                Event e = existingEvent.get();
                idempotencyService.recordIdempotency(project.getId(), idempotencyKey, e.getId());
                return new IngestEventResponse(e.getId(), e.getType(), 0, true, e.getCreatedAt());
            }
        }

        // 3. Persist Event
        String payloadString;
        try {
            payloadString = objectMapper.writeValueAsString(req.data());
        } catch (Exception e) {
            payloadString = req.data().toString();
        }

        Event event = Event.builder()
                .project(project)
                .type(req.type().trim())
                .data(payloadString)
                .idempotencyKey(idempotencyKey)
                .build();

        Event savedEvent;
        try {
            savedEvent = eventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent duplicate idempotency key caught by DB constraint
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<Event> existing = eventRepository.findByProjectIdAndIdempotencyKey(project.getId(), idempotencyKey);
                if (existing.isPresent()) {
                    return new IngestEventResponse(existing.get().getId(), existing.get().getType(), 0, true, existing.get().getCreatedAt());
                }
            }
            throw ex;
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.recordIdempotency(project.getId(), idempotencyKey, savedEvent.getId());
        }

        // 4. Fan out to active, non-circuit-broken endpoints
        List<Endpoint> activeEndpoints = endpointRepository.findByProjectIdAndActiveTrueAndCircuitBrokenFalse(project.getId());
        List<Delivery> deliveries = new ArrayList<>();

        for (Endpoint endpoint : activeEndpoints) {
            if (endpoint.isSubscribedTo(savedEvent.getType())) {
                Delivery delivery = Delivery.builder()
                        .event(savedEvent)
                        .endpoint(endpoint)
                        .project(project)
                        .status(DeliveryStatus.PENDING)
                        .attemptCount(0)
                        .maxAttempts(8)
                        .nextAttemptAt(Instant.now())
                        .build();
                deliveries.add(delivery);
            }
        }

        if (!deliveries.isEmpty()) {
            deliveryRepository.saveAll(deliveries);
        }

        if (metricsService != null) {
            metricsService.recordEventIngested(project.getId());
        }

        log.info("Ingested event id={}, type={}, fanout_deliveries={}", savedEvent.getId(), savedEvent.getType(), deliveries.size());
        return new IngestEventResponse(savedEvent.getId(), savedEvent.getType(), deliveries.size(), false, savedEvent.getCreatedAt());
    }

    @Transactional
    public BatchIngestResponse batchIngest(Project project, BatchIngestRequest req) {
        List<IngestEventResponse> results = new ArrayList<>();
        int totalDeliveries = 0;

        for (IngestEventRequest eventReq : req.events()) {
            IngestEventResponse res = ingestEvent(project, eventReq, eventReq.idempotencyKey());
            results.add(res);
            totalDeliveries += res.deliveriesCreated();
        }

        return new BatchIngestResponse(results.size(), totalDeliveries, results);
    }
}
