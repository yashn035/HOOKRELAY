package com.hookrelay.core.service;

import com.hookrelay.core.entity.DeliveryStatus;
import com.hookrelay.core.repository.DeliveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HookRelayMetricsServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    private MeterRegistry meterRegistry;
    private HookRelayMetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new HookRelayMetricsService(meterRegistry, deliveryRepository);
        metricsService.registerGauges();
    }

    @Test
    void testRecordEventIngested() {
        UUID tenantId = UUID.randomUUID();
        metricsService.recordEventIngested(tenantId);

        double count = meterRegistry.counter("hookrelay.events.ingested", "tenant_id", tenantId.toString()).count();
        assertEquals(1.0, count);
    }

    @Test
    void testRecordRateLimitRejected() {
        UUID tenantId = UUID.randomUUID();
        metricsService.recordRateLimitRejected(tenantId);

        double count = meterRegistry.counter("hookrelay.rate_limit.rejected", "tenant_id", tenantId.toString()).count();
        assertEquals(1.0, count);
    }

    @Test
    void testRecordDeliveryAttempt() {
        metricsService.recordDeliveryAttempt("SUCCEEDED");
        metricsService.recordDeliveryAttempt("FAILED");

        double succeeded = meterRegistry.counter("hookrelay.deliveries.attempted", "status", "SUCCEEDED").count();
        double failed = meterRegistry.counter("hookrelay.deliveries.attempted", "status", "FAILED").count();

        assertEquals(1.0, succeeded);
        assertEquals(1.0, failed);
    }

    @Test
    void testRecordDeliveryLatency() {
        UUID endpointId = UUID.randomUUID();
        metricsService.recordDeliveryLatency(endpointId, 45);

        long count = meterRegistry.timer("hookrelay.delivery.latency", "endpoint_id", endpointId.toString()).count();
        assertEquals(1, count);
    }

    @Test
    void testQueueDepthGauge() {
        when(deliveryRepository.countByStatus(DeliveryStatus.PENDING)).thenReturn(17L);

        double gaugeValue = meterRegistry.get("hookrelay.deliveries.queue_depth").gauge().value();
        assertEquals(17.0, gaugeValue);
        verify(deliveryRepository).countByStatus(DeliveryStatus.PENDING);
    }
}
