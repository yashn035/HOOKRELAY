package com.hookrelay.core.repository;

import com.hookrelay.core.entity.Delivery;
import com.hookrelay.core.entity.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    @Query(value = """
        SELECT d.id FROM deliveries d
        WHERE (d.status = 'PENDING' OR (d.status = 'PROCESSING' AND d.locked_until < :now))
          AND d.next_attempt_at <= :now
        ORDER BY d.next_attempt_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<UUID> claimPendingDeliveryIds(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    @Modifying
    @Query("""
        UPDATE Delivery d
        SET d.status = :processingStatus,
            d.lockedUntil = :lockedUntil,
            d.lockedBy = :lockedBy,
            d.updatedAt = :now
        WHERE d.id IN :ids
        """)
    int leaseDeliveries(
            @Param("ids") List<UUID> ids,
            @Param("processingStatus") DeliveryStatus processingStatus,
            @Param("lockedUntil") Instant lockedUntil,
            @Param("lockedBy") String lockedBy,
            @Param("now") Instant now
    );

    @Modifying
    @Query("""
        UPDATE Delivery d
        SET d.lockedUntil = NULL,
            d.lockedBy = NULL,
            d.status = :pendingStatus,
            d.updatedAt = :now
        WHERE d.lockedBy = :workerId AND d.status = :processingStatus
        """)
    int releaseWorkerLeases(
            @Param("workerId") String workerId,
            @Param("processingStatus") DeliveryStatus processingStatus,
            @Param("pendingStatus") DeliveryStatus pendingStatus,
            @Param("now") Instant now
    );

    Page<Delivery> findByProjectId(UUID projectId, Pageable pageable);

    Page<Delivery> findByProjectIdAndStatus(UUID projectId, DeliveryStatus status, Pageable pageable);

    Page<Delivery> findByEventId(UUID eventId, Pageable pageable);

    Page<Delivery> findByEndpointId(UUID endpointId, Pageable pageable);

    Optional<Delivery> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, DeliveryStatus status);

    @Query("""
        SELECT COUNT(d) FROM Delivery d
        WHERE d.project.id = :projectId
          AND d.createdAt >= :since
        """)
    long countRecentByProjectId(@Param("projectId") UUID projectId, @Param("since") Instant since);

    @Query("""
        SELECT COUNT(d) FROM Delivery d
        WHERE d.project.id = :projectId
          AND d.status = :status
          AND d.createdAt >= :since
        """)
    long countRecentByProjectIdAndStatus(
            @Param("projectId") UUID projectId,
            @Param("status") DeliveryStatus status,
            @Param("since") Instant since
    );
}
