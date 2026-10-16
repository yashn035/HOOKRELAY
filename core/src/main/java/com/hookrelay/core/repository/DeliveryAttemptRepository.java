package com.hookrelay.core.repository;

import com.hookrelay.core.entity.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findByDeliveryIdOrderByAttemptNumberAsc(UUID deliveryId);

    @Query("""
        SELECT AVG(a.latencyMs) FROM DeliveryAttempt a
        JOIN a.delivery d
        WHERE d.project.id = :projectId
          AND a.executedAt >= :since
          AND a.latencyMs IS NOT NULL
        """)
    Double getAverageLatencyMs(@Param("projectId") UUID projectId, @Param("since") Instant since);

    @Query(value = """
        SELECT PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY a.latency_ms)
        FROM delivery_attempts a
        JOIN deliveries d ON a.delivery_id = d.id
        WHERE d.project_id = :projectId
          AND a.executed_at >= :since
          AND a.latency_ms IS NOT NULL
        """, nativeQuery = true)
    Double getP95LatencyMs(@Param("projectId") UUID projectId, @Param("since") Instant since);
}
