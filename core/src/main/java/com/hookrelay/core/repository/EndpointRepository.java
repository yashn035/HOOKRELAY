package com.hookrelay.core.repository;

import com.hookrelay.core.entity.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EndpointRepository extends JpaRepository<Endpoint, UUID> {
    List<Endpoint> findByProjectId(UUID projectId);
    List<Endpoint> findByProjectIdAndActiveTrueAndCircuitBrokenFalse(UUID projectId);
    Optional<Endpoint> findByIdAndProjectId(UUID id, UUID projectId);
}
