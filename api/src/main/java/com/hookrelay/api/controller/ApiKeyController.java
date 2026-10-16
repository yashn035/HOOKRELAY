package com.hookrelay.api.controller;

import com.hookrelay.api.dto.ApiKeyDtos.*;
import com.hookrelay.api.security.UserPrincipal;
import com.hookrelay.api.service.ProjectService;
import com.hookrelay.core.entity.ApiKey;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.service.ApiKeyService;
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

@RestController
@RequestMapping("/api/v1/projects/{projectId}/api-keys")
@RequiredArgsConstructor
@PreAuthorize("hasRole('USER')")
@Tag(name = "API Keys", description = "Management of tenant ingestion API keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final ProjectService projectService;

    @GetMapping
    @Operation(summary = "List all API keys for a project")
    public ResponseEntity<List<ApiKeyResponse>> listKeys(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId
    ) {
        projectService.getProjectEntity(projectId, principal.getOrgId());
        List<ApiKeyResponse> keys = apiKeyService.listProjectKeys(projectId).stream()
                .map(k -> new ApiKeyResponse(
                        k.getId(),
                        k.getName(),
                        k.getKeyPrefix(),
                        k.isRevoked(),
                        k.getRevokedAt(),
                        k.getLastUsedAt(),
                        k.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(keys);
    }

    @PostMapping
    @Operation(summary = "Generate a new API key (Secret is revealed only once)")
    public ResponseEntity<CreatedApiKeyResponse> createKey(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateApiKeyRequest req
    ) {
        Project project = projectService.getProjectEntity(projectId, principal.getOrgId());
        ApiKeyService.GeneratedApiKey generated = apiKeyService.createApiKey(project, req.name().trim());
        ApiKey key = generated.apiKey();

        CreatedApiKeyResponse response = new CreatedApiKeyResponse(
                key.getId(),
                key.getName(),
                key.getKeyPrefix(),
                generated.rawSecret(),
                key.getCreatedAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{keyId}")
    @Operation(summary = "Revoke an active API key")
    public ResponseEntity<Void> revokeKey(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID projectId,
            @PathVariable UUID keyId
    ) {
        projectService.getProjectEntity(projectId, principal.getOrgId());
        apiKeyService.revokeApiKey(keyId, projectId);
        return ResponseEntity.noContent().build();
    }
}
