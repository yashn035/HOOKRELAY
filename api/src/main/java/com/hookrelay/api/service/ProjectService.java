package com.hookrelay.api.service;

import com.hookrelay.api.dto.ProjectDtos.*;
import com.hookrelay.core.entity.Org;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.repository.OrgRepository;
import com.hookrelay.core.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final OrgRepository orgRepository;

    public ProjectService(
            ProjectRepository projectRepository,
            OrgRepository orgRepository
    ) {
        this.projectRepository = projectRepository;
        this.orgRepository = orgRepository;
    }

    public List<ProjectResponse> getProjectsForOrg(UUID orgId) {
        return projectRepository.findByOrgId(orgId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ProjectResponse getProject(UUID projectId, UUID orgId) {
        Project project = getProjectEntity(projectId, orgId);
        return toResponse(project);
    }

    public Project getProjectEntity(UUID projectId, UUID orgId) {
        return projectRepository.findByIdAndOrgId(projectId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found or access denied"));
    }

    @Transactional
    public ProjectResponse createProject(UUID orgId, CreateProjectRequest req) {
        Org org = orgRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        Project project = Project.builder()
                .org(org)
                .name(req.name().trim())
                .description(req.description())
                .rateLimitPerSecond(req.rateLimitPerSecond() != null ? req.rateLimitPerSecond() : 100)
                .build();

        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public ProjectResponse updateProject(UUID projectId, UUID orgId, UpdateProjectRequest req) {
        Project project = getProjectEntity(projectId, orgId);

        if (req.name() != null && !req.name().isBlank()) {
            project.setName(req.name().trim());
        }
        if (req.description() != null) {
            project.setDescription(req.description());
        }
        if (req.rateLimitPerSecond() != null) {
            project.setRateLimitPerSecond(req.rateLimitPerSecond());
        }

        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void deleteProject(UUID projectId, UUID orgId) {
        Project project = getProjectEntity(projectId, orgId);
        projectRepository.delete(project);
    }

    private ProjectResponse toResponse(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getOrg().getId(),
                p.getName(),
                p.getDescription(),
                p.getRateLimitPerSecond(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
