package com.hookrelay.core.repository;

import com.hookrelay.core.entity.DeadLetter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetter, UUID> {
    Page<DeadLetter> findByProjectId(UUID projectId, Pageable pageable);
    Optional<DeadLetter> findByDeliveryId(UUID deliveryId);
}
