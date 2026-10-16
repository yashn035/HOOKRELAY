package com.hookrelay.api.service;

import com.hookrelay.api.dto.DeliveryDtos.DeliveryDetailResponse;
import com.hookrelay.api.dto.DeliveryDtos.DeliveryResponse;
import com.hookrelay.core.entity.*;
import com.hookrelay.core.repository.DeliveryAttemptRepository;
import com.hookrelay.core.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private DeliveryService deliveryService;

    private Project testProject;
    private Endpoint testEndpoint;
    private Event testEvent;
    private Delivery testDelivery;
    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Org org = Org.builder().name("Test Org").slug("test-org").build();
        org.setId(orgId);

        testProject = Project.builder().org(org).name("Production").build();
        testProject.setId(projectId);

        testEndpoint = Endpoint.builder()
                .project(testProject)
                .name("Webhook Sink")
                .url("https://httpbin.org/post")
                .secret("whsec_secret")
                .build();
        testEndpoint.setId(UUID.randomUUID());

        testEvent = Event.builder()
                .project(testProject)
                .type("order.created")
                .data("{\"orderId\": \"1001\"}")
                .build();
        testEvent.setId(UUID.randomUUID());

        testDelivery = Delivery.builder()
                .project(testProject)
                .endpoint(testEndpoint)
                .event(testEvent)
                .status(DeliveryStatus.FAILED)
                .attemptCount(3)
                .maxAttempts(8)
                .build();
        testDelivery.setId(deliveryId);
    }

    @Test
    @DisplayName("Should list deliveries for a project")
    void testListDeliveries() {
        when(projectService.getProjectEntity(projectId, orgId)).thenReturn(testProject);
        when(deliveryRepository.findByProjectId(eq(projectId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(testDelivery)));

        Page<DeliveryResponse> page = deliveryService.listDeliveries(projectId, orgId, null, 0, 10);

        assertNotNull(page);
        assertEquals(1, page.getContent().size());
        assertEquals("order.created", page.getContent().get(0).eventType());
    }

    @Test
    @DisplayName("Should get delivery details with execution attempts transcript")
    void testGetDeliveryDetail() {
        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .delivery(testDelivery)
                .attemptNumber(1)
                .responseStatus(200)
                .responseBody("{\"ok\": true}")
                .latencyMs(45L)
                .executedAt(Instant.now())
                .build();

        when(projectService.getProjectEntity(projectId, orgId)).thenReturn(testProject);
        when(deliveryRepository.findByIdAndProjectId(deliveryId, projectId)).thenReturn(Optional.of(testDelivery));
        when(deliveryAttemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(deliveryId)).thenReturn(List.of(attempt));

        DeliveryDetailResponse detail = deliveryService.getDeliveryDetail(projectId, deliveryId, orgId);

        assertNotNull(detail);
        assertEquals(deliveryId, detail.delivery().id());
        assertEquals(1, detail.attempts().size());
        assertEquals(200, detail.attempts().get(0).responseStatus());
    }

    @Test
    @DisplayName("Should manually replay a failed delivery and reset status to PENDING")
    void testReplayDelivery() {
        when(projectService.getProjectEntity(projectId, orgId)).thenReturn(testProject);
        when(deliveryRepository.findByIdAndProjectId(deliveryId, projectId)).thenReturn(Optional.of(testDelivery));
        when(deliveryRepository.save(any(Delivery.class))).thenAnswer(i -> i.getArgument(0));

        DeliveryResponse replayed = deliveryService.replayDelivery(projectId, deliveryId, orgId);

        assertNotNull(replayed);
        assertEquals(DeliveryStatus.PENDING.name(), replayed.status());
        assertNull(testDelivery.getLockedBy());
        assertNull(testDelivery.getLockedUntil());
        verify(deliveryRepository).save(testDelivery);
    }
}
