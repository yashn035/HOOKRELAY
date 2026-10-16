package com.hookrelay.core.repository;

import com.hookrelay.core.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByOrgId(UUID orgId);
    Optional<Project> findByIdAndOrgId(UUID id, UUID orgId);
}
