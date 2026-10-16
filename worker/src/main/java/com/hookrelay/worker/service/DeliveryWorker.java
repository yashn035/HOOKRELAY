package com.hookrelay.worker.service;

import com.hookrelay.core.entity.*;
import com.hookrelay.core.repository.DeadLetterRepository;
import com.hookrelay.core.repository.DeliveryAttemptRepository;
import com.hookrelay.core.repository.DeliveryRepository;
import com.hookrelay.core.repository.EndpointRepository;
import com.hookrelay.core.service.BackoffCalculator;
import com.hookrelay.worker.service.DeliveryDispatcher.DispatchResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

@Slf4j
@Service
@ConditionalOnProperty(name = "hookrelay.worker.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DeliveryWorker {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final EndpointRepository endpointRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final DeliveryDispatcher deliveryDispatcher;
    private final BackoffCalculator backoffCalculator;

    @Value("${hookrelay.worker.batch-size:50}")
    private int batchSize;

    @Value("${hookrelay.worker.threads:10}")
    private int threadCount;

    private ExecutorService threadPool;
    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
    private volatile boolean isRunning = true;

    @PostConstruct
    public void init() {
        threadPool = new ThreadPoolExecutor(
                threadCount,
                threadCount * 2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "hr-worker-" + (++counter));
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("Initialized HookRelay delivery worker instance [{}] with {} threads", workerId, threadCount);
    }

    @Scheduled(fixedDelayString = "${hookrelay.worker.poll-delay-ms:2000}")
    public void pollAndProcess() {
        if (!isRunning) {
            return;
        }

        try {
            List<UUID> deliveryIds = claimBatch();
            if (deliveryIds == null || deliveryIds.isEmpty()) {
                return;
            }

            log.debug("Worker [{}] claimed {} pending deliveries", workerId, deliveryIds.size());
            for (UUID deliveryId : deliveryIds) {
                if (!isRunning) break;
                threadPool.submit(() -> processSingleDelivery(deliveryId));
            }
        } catch (Exception ex) {
            log.error("Worker poll loop encountered error: {}", ex.getMessage(), ex);
        }
    }

    @Transactional
    public List<UUID> claimBatch() {
        Instant now = Instant.now();
        List<UUID> ids = deliveryRepository.claimPendingDeliveryIds(now, batchSize);
        if (ids.isEmpty()) {
            return ids;
        }

        Instant lockExpiry = now.plus(Duration.ofMinutes(1));
        deliveryRepository.leaseDeliveries(ids, DeliveryStatus.PROCESSING, lockExpiry, workerId, now);
        return ids;
    }

    public void processSingleDelivery(UUID deliveryId) {
        try {
            executeDeliveryTransaction(deliveryId);
        } catch (Exception ex) {
            log.error("Error processing delivery {}: {}", deliveryId, ex.getMessage(), ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeDeliveryTransaction(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }

        Endpoint endpoint = delivery.getEndpoint();
        // If endpoint was circuit broken while delivery was queued, hold delivery or dead letter
        if (endpoint.isCircuitBroken()) {
            log.info("Endpoint {} is circuit-broken. Delaying delivery {}", endpoint.getId(), deliveryId);
            delivery.setStatus(DeliveryStatus.PENDING);
            delivery.setNextAttemptAt(Instant.now().plus(Duration.ofMinutes(5)));
            delivery.setLockedBy(null);
            delivery.setLockedUntil(null);
            deliveryRepository.save(delivery);
            return;
        }

        int currentAttempt = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(currentAttempt);

        // Dispatch HTTP request
        DispatchResult result = deliveryDispatcher.dispatch(delivery);

        // Log Delivery Attempt
        DeliveryAttempt attempt = DeliveryAttempt.builder()
                .delivery(delivery)
                .attemptNumber(currentAttempt)
                .responseStatus(result.httpStatusCode())
                .responseBody(result.responseBody())
                .responseHeaders(result.responseHeaders())
                .latencyMs(result.latencyMs())
                .errorMessage(result.errorMessage())
                .executedAt(Instant.now())
                .build();
        deliveryAttemptRepository.save(attempt);

        Instant now = Instant.now();
        delivery.setLockedBy(null);
        delivery.setLockedUntil(null);

        if (result.successful()) {
            delivery.setStatus(DeliveryStatus.SUCCEEDED);
            delivery.setUpdatedAt(now);
            deliveryRepository.save(delivery);

            // Reset endpoint consecutive failures
            if (endpoint.getConsecutiveFailures() > 0) {
                endpoint.setConsecutiveFailures(0);
                endpointRepository.save(endpoint);
            }
            log.info("Delivery {} to {} SUCCEEDED (status={}, latency={}ms)", deliveryId, endpoint.getUrl(), result.httpStatusCode(), result.latencyMs());
        } else {
            // Failure handling
            handleDeliveryFailure(delivery, endpoint, currentAttempt, result, now);
        }
    }

    private void handleDeliveryFailure(
            Delivery delivery,
            Endpoint endpoint,
            int currentAttempt,
            DispatchResult result,
            Instant now
    ) {
        // Increment endpoint failure counter & evaluate circuit breaker
        int failures = endpoint.getConsecutiveFailures() + 1;
        endpoint.setConsecutiveFailures(failures);
        if (failures >= endpoint.getFailureThreshold() && !endpoint.isCircuitBroken()) {
            endpoint.setCircuitBroken(true);
            endpoint.setCircuitBrokenAt(now);
            log.warn("Circuit breaker TRIPPED for endpoint {} after {} consecutive failures", endpoint.getId(), failures);
        }
        endpointRepository.save(endpoint);

        if (currentAttempt >= delivery.getMaxAttempts()) {
            // Max attempts reached -> Move to Dead Letter Queue
            delivery.setStatus(DeliveryStatus.DEAD_LETTER);
            delivery.setUpdatedAt(now);
            deliveryRepository.save(delivery);

            DeadLetter deadLetter = DeadLetter.builder()
                    .delivery(delivery)
                    .project(delivery.getProject())
                    .endpoint(endpoint)
                    .reason("MAX_ATTEMPTS_EXCEEDED")
                    .detail("Failed after " + currentAttempt + " attempts. Last error: " + result.errorMessage())
                    .build();
            deadLetterRepository.save(deadLetter);
            log.warn("Delivery {} moved to DEAD_LETTER queue after {} attempts", delivery.getId(), currentAttempt);
        } else {
            // Schedule next attempt with exponential backoff + jitter
            Instant nextAttemptAt = backoffCalculator.calculateNextAttemptAt(currentAttempt);
            delivery.setStatus(DeliveryStatus.PENDING);
            delivery.setNextAttemptAt(nextAttemptAt);
            delivery.setUpdatedAt(now);
            deliveryRepository.save(delivery);
            log.info("Delivery {} FAILED (attempt {}/{}). Next retry scheduled at {}", delivery.getId(), currentAttempt, delivery.getMaxAttempts(), nextAttemptAt);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Initiating graceful shutdown of delivery worker [{}]", workerId);
        this.isRunning = false;

        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(8, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Release any held leases back to PENDING so other nodes can process them
        try {
            int released = deliveryRepository.releaseWorkerLeases(workerId, DeliveryStatus.PROCESSING, DeliveryStatus.PENDING, Instant.now());
            if (released > 0) {
                log.info("Released {} delivery leases held by [{}] during shutdown", released, workerId);
            }
        } catch (Exception e) {
            log.warn("Failed to release worker leases during shutdown: {}", e.getMessage());
        }
    }
}
