package com.hookrelay.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookrelay.api.dto.EventDtos.IngestEventRequest;
import com.hookrelay.api.dto.EventDtos.IngestEventResponse;
import com.hookrelay.api.exception.RateLimitExceededException;
import com.hookrelay.core.entity.Delivery;
import com.hookrelay.core.entity.Endpoint;
import com.hookrelay.core.entity.Event;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.repository.DeliveryRepository;
import com.hookrelay.core.repository.EndpointRepository;
import com.hookrelay.core.repository.EventRepository;
import com.hookrelay.core.service.IdempotencyService;
import com.hookrelay.core.service.RateLimitResult;
import com.hookrelay.core.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventIngestServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private EndpointRepository endpointRepository;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private com.hookrelay.core.service.HookRelayMetricsService metricsService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private EventIngestService eventIngestService;

    private Project testProject;
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testProject = Project.builder()
                .name("Production")
                .rateLimitPerSecond(100)
                .build();
        testProject.setId(projectId);
    }

    @Test
    @DisplayName("Should ingest event and fan out deliveries to matching active endpoints")
    void testIngestAndFanOut() throws Exception {
        IngestEventRequest req = new IngestEventRequest(
                "order.created",
                objectMapper.readTree("{\"orderId\": \"ord_123\"}"),
                "idem_key_1"
        );

        Endpoint endpoint = Endpoint.builder()
                .project(testProject)
                .name("Order Endpoint")
                .url("https://example.com/webhook")
                .eventTypesFilter(List.of("order.created"))
                .active(true)
                .circuitBroken(false)
                .build();

        when(rateLimitService.checkRateLimit(projectId, 100))
                .thenReturn(new RateLimitResult(true, 100, 99, 0));
        when(idempotencyService.getExistingEventId(projectId, "idem_key_1")).thenReturn(Optional.empty());
        when(eventRepository.findByProjectIdAndIdempotencyKey(projectId, "idem_key_1")).thenReturn(Optional.empty());
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(i -> {
            Event e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
        when(endpointRepository.findByProjectIdAndActiveTrueAndCircuitBrokenFalse(projectId))
                .thenReturn(List.of(endpoint));

        IngestEventResponse res = eventIngestService.ingestEvent(testProject, req, "idem_key_1");

        assertNotNull(res);
        assertFalse(res.isDuplicate());
        assertEquals("order.created", res.type());
        assertEquals(1, res.deliveriesCreated());

        verify(deliveryRepository).saveAll(any());
        verify(idempotencyService).recordIdempotency(eq(projectId), eq("idem_key_1"), any());
    }

    @Test
    @DisplayName("Should detect idempotency duplicate and skip re-ingestion")
    void testIdempotencyDeduplication() throws Exception {
        UUID existingEventId = UUID.randomUUID();
        IngestEventRequest req = new IngestEventRequest(
                "order.created",
                objectMapper.readTree("{\"orderId\": \"ord_123\"}"),
                "idem_key_duplicate"
        );

        when(rateLimitService.checkRateLimit(projectId, 100))
                .thenReturn(new RateLimitResult(true, 100, 99, 0));
        when(idempotencyService.getExistingEventId(projectId, "idem_key_duplicate"))
                .thenReturn(Optional.of(existingEventId.toString()));

        IngestEventResponse res = eventIngestService.ingestEvent(testProject, req, "idem_key_duplicate");

        assertTrue(res.isDuplicate());
        assertEquals(existingEventId, res.eventId());
        assertEquals(0, res.deliveriesCreated());
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should throw RateLimitExceededException when token bucket is exhausted")
    void testRateLimitExceeded() throws Exception {
        IngestEventRequest req = new IngestEventRequest(
                "order.created",
                objectMapper.readTree("{\"orderId\": \"ord_123\"}"),
                null
        );

        when(rateLimitService.checkRateLimit(projectId, 100))
                .thenReturn(new RateLimitResult(false, 100, 0, 1));

        assertThrows(RateLimitExceededException.class, () ->
                eventIngestService.ingestEvent(testProject, req, null));
    }
}
