package com.hookrelay.core.service;

import com.hookrelay.core.entity.DeliveryStatus;
import com.hookrelay.core.repository.DeliveryRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class HookRelayMetricsService {

    private final MeterRegistry meterRegistry;
    private final DeliveryRepository deliveryRepository;

    public HookRelayMetricsService(MeterRegistry meterRegistry, DeliveryRepository deliveryRepository) {
        this.meterRegistry = meterRegistry;
        this.deliveryRepository = deliveryRepository;
    }

    @PostConstruct
    public void registerGauges() {
        Gauge.builder("hookrelay.deliveries.queue_depth", deliveryRepository, repo -> (double) repo.countByStatus(DeliveryStatus.PENDING))
                .description("Current number of PENDING deliveries in PostgreSQL queue")
                .register(meterRegistry);
        log.info("Registered custom Micrometer metric: hookrelay.deliveries.queue_depth");
    }

    public void recordEventIngested(UUID tenantId) {
        meterRegistry.counter("hookrelay.events.ingested", "tenant_id", tenantId.toString()).increment();
    }

    public void recordRateLimitRejected(UUID tenantId) {
        meterRegistry.counter("hookrelay.rate_limit.rejected", "tenant_id", tenantId.toString()).increment();
    }

    public void recordDeliveryAttempt(String status) {
        meterRegistry.counter("hookrelay.deliveries.attempted", "status", status).increment();
    }

    public void recordDeliveryLatency(UUID endpointId, long latencyMs) {
        meterRegistry.timer("hookrelay.delivery.latency", "endpoint_id", endpointId.toString())
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }
}
