package com.hookrelay.core.repository;

import com.hookrelay.core.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHashAndRevokedFalse(String keyHash);
    List<ApiKey> findByProjectId(UUID projectId);
    Optional<ApiKey> findByIdAndProjectId(UUID id, UUID projectId);
}
