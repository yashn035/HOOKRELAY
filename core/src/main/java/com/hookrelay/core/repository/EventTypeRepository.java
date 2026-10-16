package com.hookrelay.core.repository;

import com.hookrelay.core.entity.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventTypeRepository extends JpaRepository<EventType, UUID> {
    List<EventType> findByProjectId(UUID projectId);
    Optional<EventType> findByProjectIdAndName(UUID projectId, String name);
}
