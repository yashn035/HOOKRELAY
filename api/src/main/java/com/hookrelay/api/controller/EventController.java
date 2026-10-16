package com.hookrelay.api.controller;

import com.hookrelay.api.dto.EventDtos.*;
import com.hookrelay.api.security.TenantPrincipal;
import com.hookrelay.api.security.UserPrincipal;
import com.hookrelay.api.service.EventIngestService;
import com.hookrelay.api.service.ProjectService;
import com.hookrelay.core.entity.Event;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.repository.EventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Events", description = "High-throughput event ingestion and event history")
public class EventController {

    private final EventIngestService eventIngestService;
    private final EventRepository eventRepository;
    private final ProjectService projectService;


    @PostMapping("/events")
    @Operation(
            summary = "Publish and ingest a webhook event",
            description = "Validates API key, applies tenant token bucket rate limiting, verifies idempotency, persists event, and fans out deliveries.",
            security = {@SecurityRequirement(name = "BearerAuth"), @SecurityRequirement(name = "ApiKeyAuth")}
    )
    public ResponseEntity<IngestEventResponse> publishEvent(
            Authentication authentication,
            @Parameter(description = "Optional idempotency key to prevent double publishing")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Parameter(description = "Project ID (Required only if authenticating with User JWT instead of API key)")
            @RequestParam(value = "projectId", required = false) UUID paramProjectId,
            @Valid @RequestBody IngestEventRequest request
    ) {
        Project project = resolveProject(authentication, paramProjectId);
        IngestEventResponse response = eventIngestService.ingestEvent(project, request, idempotencyKey);
        HttpStatus status = response.isDuplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/events/batch")
    @Operation(summary = "Publish a batch of events atomically", security = {@SecurityRequirement(name = "ApiKeyAuth")})
    public ResponseEntity<BatchIngestResponse> publishBatch(
            Authentication authentication,
            @RequestParam(value = "projectId", required = false) UUID paramProjectId,
            @Valid @RequestBody BatchIngestRequest request
    ) {
        Project project = resolveProject(authentication, paramProjectId);
        BatchIngestResponse response = eventIngestService.batchIngest(project, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/projects/{projectId}/events")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Query event history for a project")
    public ResponseEntity<Page<Event>> listEvents(
            Authentication authentication,
            @PathVariable UUID projectId,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();
        projectService.getProjectEntity(projectId, user.getOrgId());

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Event> events;
        if (type != null && !type.isBlank()) {
            events = eventRepository.findByProjectIdAndType(projectId, type.trim(), pageRequest);
        } else {
            events = eventRepository.findByProjectId(projectId, pageRequest);
        }
        return ResponseEntity.ok(events);
    }

    @GetMapping("/projects/{projectId}/events/{eventId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get single event by ID")
    public ResponseEntity<Event> getEvent(
            Authentication authentication,
            @PathVariable UUID projectId,
            @PathVariable UUID eventId
    ) {
        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();
        projectService.getProjectEntity(projectId, user.getOrgId());

        Event event = eventRepository.findByIdAndProjectId(eventId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found"));
        return ResponseEntity.ok(event);
    }

    private Project resolveProject(Authentication authentication, UUID paramProjectId) {
        if (authentication.getPrincipal() instanceof TenantPrincipal tenantPrincipal) {
            return tenantPrincipal.getProject();
        } else if (authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            if (paramProjectId == null) {
                throw new IllegalArgumentException("projectId parameter is required when publishing with user token");
            }
            return projectService.getProjectEntity(paramProjectId, userPrincipal.getOrgId());
        }
        throw new IllegalStateException("Authentication principal unrecognized");
    }
}
