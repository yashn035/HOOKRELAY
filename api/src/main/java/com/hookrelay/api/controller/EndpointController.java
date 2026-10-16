package com.hookrelay.api.controller;

import com.hookrelay.api.dto.EndpointDtos.*;
import com.hookrelay.api.security.UserPrincipal;
import com.hookrelay.api.service.EndpointService;
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
@RequestMapping("/api/v1/projects/{projectId}/endpoints")
@PreAuthorize("hasRole('USER')")
@Tag(name = "Endpoints", description = "Subscriber destination endpoints and circuit breakers")
public class EndpointController {

    private final EndpointService endpointService;


    @GetMapping
    @Operation(summary = "List all webhook subscriber endpoints for a project")
    public ResponseEntity<List<EndpointResponse>> listEndpoints(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(endpointService.getEndpoints(projectId, principal.getOrgId()));
    }

    @PostMapping
    @Operation(summary = "Register a new subscriber endpoint with SSRF check")
    public ResponseEntity<EndpointResponse> createEndpoint(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateEndpointRequest req
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(endpointService.createEndpoint(projectId, principal.getOrgId(), req));
    }

    @GetMapping("/{endpointId}")
    @Operation(summary = "Get subscriber endpoint configuration and circuit breaker state")
    public ResponseEntity<EndpointResponse> getEndpoint(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID endpointId
    ) {
        return ResponseEntity.ok(endpointService.getEndpoint(projectId, endpointId, principal.getOrgId()));
    }

    @PutMapping("/{endpointId}")
    @Operation(summary = "Update subscriber endpoint URL, event filters, or status")
    public ResponseEntity<EndpointResponse> updateEndpoint(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID endpointId,
            @Valid @RequestBody UpdateEndpointRequest req
    ) {
        return ResponseEntity.ok(endpointService.updateEndpoint(projectId, endpointId, principal.getOrgId(), req));
    }

    @PostMapping("/{endpointId}/rotate-secret")
    @Operation(summary = "Rotate webhook signing secret for an endpoint")
    public ResponseEntity<EndpointResponse> rotateSecret(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID endpointId
    ) {
        return ResponseEntity.ok(endpointService.rotateSecret(projectId, endpointId, principal.getOrgId()));
    }

    @PostMapping("/{endpointId}/circuit-breaker/reset")
    @Operation(summary = "Reset tripped circuit breaker and restore endpoint to active delivery")
    public ResponseEntity<EndpointResponse> resetCircuitBreaker(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID endpointId
    ) {
        return ResponseEntity.ok(endpointService.resetCircuitBreaker(projectId, endpointId, principal.getOrgId()));
    }

    @PostMapping("/test-transform")
    @Operation(summary = "Test payload transformation rules on sample JSON without persisting")
    public ResponseEntity<TestTransformResponse> testTransform(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody TestTransformRequest req
    ) {
        return ResponseEntity.ok(endpointService.testTransform(projectId, principal.getOrgId(), req));
    }

    @DeleteMapping("/{endpointId}")
    @Operation(summary = "Delete subscriber endpoint")
    public ResponseEntity<Void> deleteEndpoint(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID endpointId
    ) {
        endpointService.deleteEndpoint(projectId, endpointId, principal.getOrgId());
        return ResponseEntity.noContent().build();
    }
}
