package com.hookrelay.api.controller;

import com.hookrelay.api.dto.DeliveryDtos.*;
import com.hookrelay.api.security.UserPrincipal;
import com.hookrelay.api.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/deliveries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Tag(name = "Deliveries", description = "Webhook delivery logs, attempt timelines, and manual replay")
public class DeliveryController {

    private final DeliveryService deliveryService;

    @GetMapping
    @Operation(summary = "List deliveries with status filtering and pagination")
    public ResponseEntity<Page<DeliveryResponse>> listDeliveries(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(deliveryService.listDeliveries(projectId, principal.getOrgId(), status, page, size));
    }

    @GetMapping("/{deliveryId}")
    @Operation(summary = "Get delivery attempt timeline and raw request/response details")
    public ResponseEntity<DeliveryDetailResponse> getDeliveryDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID deliveryId
    ) {
        return ResponseEntity.ok(deliveryService.getDeliveryDetail(projectId, deliveryId, principal.getOrgId()));
    }

    @PostMapping("/{deliveryId}/replay")
    @Operation(summary = "Manually replay a delivery")
    public ResponseEntity<DeliveryResponse> replayDelivery(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID deliveryId
    ) {
        return ResponseEntity.ok(deliveryService.replayDelivery(projectId, deliveryId, principal.getOrgId()));
    }

    @PostMapping("/bulk-replay")
    @Operation(summary = "Bulk replay deliveries filtered by event or status")
    public ResponseEntity<BulkReplayResponse> bulkReplay(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @RequestBody BulkReplayRequest request
    ) {
        return ResponseEntity.ok(deliveryService.bulkReplay(projectId, principal.getOrgId(), request));
    }
}
