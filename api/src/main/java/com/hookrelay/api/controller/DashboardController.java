package com.hookrelay.api.controller;

import com.hookrelay.api.dto.DashboardDtos.DashboardOverview;
import com.hookrelay.api.security.UserPrincipal;
import com.hookrelay.api.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Tag(name = "Dashboard", description = "Aggregated delivery metrics, success rates, and latency analytics")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Get overview metrics and chart datasets for project dashboard")
    public ResponseEntity<DashboardOverview> getOverview(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(dashboardService.getOverview(projectId, principal.getOrgId()));
    }
}
