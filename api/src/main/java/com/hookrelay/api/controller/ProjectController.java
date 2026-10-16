package com.hookrelay.api.controller;

import com.hookrelay.api.dto.ProjectDtos.*;
import com.hookrelay.api.security.UserPrincipal;
import com.hookrelay.api.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/projects")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Projects", description = "Tenant project workspace management")
public class ProjectController {

    private final ProjectService projectService;


    @GetMapping
    @Operation(summary = "List all projects in the current user's organization")
    public ResponseEntity<List<ProjectResponse>> listProjects(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(projectService.getProjectsForOrg(principal.getOrgId()));
    }

    @PostMapping
    @Operation(summary = "Create a new project workspace")
    public ResponseEntity<ProjectResponse> createProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateProjectRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.createProject(principal.getOrgId(), req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project details by ID")
    public ResponseEntity<ProjectResponse> getProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(projectService.getProject(id, principal.getOrgId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update project settings and rate limits")
    public ResponseEntity<ProjectResponse> updateProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest req
    ) {
        return ResponseEntity.ok(projectService.updateProject(id, principal.getOrgId(), req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete project and associated resources")
    public ResponseEntity<Void> deleteProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        projectService.deleteProject(id, principal.getOrgId());
        return ResponseEntity.noContent().build();
    }
}
