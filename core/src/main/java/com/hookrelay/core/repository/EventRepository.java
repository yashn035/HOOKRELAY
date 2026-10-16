package com.hookrelay.core.repository;

import com.hookrelay.core.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    Optional<Event> findByProjectIdAndIdempotencyKey(UUID projectId, String idempotencyKey);
    Page<Event> findByProjectId(UUID projectId, Pageable pageable);
    Page<Event> findByProjectIdAndType(UUID projectId, String type, Pageable pageable);
    Optional<Event> findByIdAndProjectId(UUID id, UUID projectId);
}
