package com.hookrelay.api.service;

import com.hookrelay.api.dto.EndpointDtos.*;
import com.hookrelay.core.entity.Endpoint;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.repository.EndpointRepository;
import com.hookrelay.core.service.PayloadTransformService;
import com.hookrelay.core.service.PayloadTransformService.TransformResult;
import com.hookrelay.core.service.SsrfProtectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class EndpointService {

    private final EndpointRepository endpointRepository;
    private final ProjectService projectService;
    private final SsrfProtectionService ssrfProtectionService;
    private final PayloadTransformService payloadTransformService;
    private static final SecureRandom RANDOM = new SecureRandom();

    public EndpointService(
            EndpointRepository endpointRepository,
            ProjectService projectService,
            SsrfProtectionService ssrfProtectionService,
            PayloadTransformService payloadTransformService
    ) {
        this.endpointRepository = endpointRepository;
        this.projectService = projectService;
        this.ssrfProtectionService = ssrfProtectionService;
        this.payloadTransformService = payloadTransformService;
    }

    public List<EndpointResponse> getEndpoints(UUID projectId, UUID orgId) {
        projectService.getProjectEntity(projectId, orgId);
        return endpointRepository.findByProjectId(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    public EndpointResponse getEndpoint(UUID projectId, UUID endpointId, UUID orgId) {
        projectService.getProjectEntity(projectId, orgId);
        Endpoint endpoint = endpointRepository.findByIdAndProjectId(endpointId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Endpoint not found"));
        return toResponse(endpoint);
    }

    @Transactional
    public EndpointResponse createEndpoint(UUID projectId, UUID orgId, CreateEndpointRequest req) {
        Project project = projectService.getProjectEntity(projectId, orgId);

        // Validate URL against SSRF and HTTPS requirements
        ssrfProtectionService.validateEndpointUrl(req.url());

        // Validate transform rules if provided
        String transformRules = null;
        if (req.transformRules() != null && !req.transformRules().isBlank()) {
            payloadTransformService.validateRules(req.transformRules());
            transformRules = req.transformRules().trim();
        }

        String secret = req.secret();
        if (secret == null || secret.isBlank()) {
            secret = generateWebhookSecret();
        }

        Endpoint endpoint = Endpoint.builder()
                .project(project)
                .name(req.name().trim())
                .url(req.url().trim())
                .secret(secret)
                .eventTypesFilter(req.eventTypesFilter() != null ? req.eventTypesFilter() : List.of())
                .active(true)
                .failureThreshold(req.failureThreshold() != null ? req.failureThreshold() : 10)
                .rateLimitPerSecond(req.rateLimitPerSecond())
                .transformRules(transformRules)
                .build();

        return toResponse(endpointRepository.save(endpoint));
    }

    @Transactional
    public EndpointResponse updateEndpoint(UUID projectId, UUID endpointId, UUID orgId, UpdateEndpointRequest req) {
        projectService.getProjectEntity(projectId, orgId);
        Endpoint endpoint = endpointRepository.findByIdAndProjectId(endpointId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Endpoint not found"));

        if (req.name() != null && !req.name().isBlank()) {
            endpoint.setName(req.name().trim());
        }
        if (req.url() != null && !req.url().isBlank()) {
            ssrfProtectionService.validateEndpointUrl(req.url());
            endpoint.setUrl(req.url().trim());
        }
        if (req.eventTypesFilter() != null) {
            endpoint.setEventTypesFilter(req.eventTypesFilter());
        }
        if (req.active() != null) {
            endpoint.setActive(req.active());
        }
        if (req.failureThreshold() != null) {
            endpoint.setFailureThreshold(req.failureThreshold());
        }
        if (req.rateLimitPerSecond() != null) {
            endpoint.setRateLimitPerSecond(req.rateLimitPerSecond());
        }
        if (req.transformRules() != null) {
            if (!req.transformRules().isBlank()) {
                payloadTransformService.validateRules(req.transformRules());
                endpoint.setTransformRules(req.transformRules().trim());
            } else {
                endpoint.setTransformRules(null);
            }
        }

        return toResponse(endpointRepository.save(endpoint));
    }

    public TestTransformResponse testTransform(UUID projectId, UUID orgId, TestTransformRequest req) {
        projectService.getProjectEntity(projectId, orgId);
        TransformResult result = payloadTransformService.testTransform(req.payload(), req.rules());
        return new TestTransformResponse(result.valid(), result.transformedPayload(), result.errorMessage());
    }

    @Transactional
    public EndpointResponse rotateSecret(UUID projectId, UUID endpointId, UUID orgId) {
        projectService.getProjectEntity(projectId, orgId);
        Endpoint endpoint = endpointRepository.findByIdAndProjectId(endpointId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Endpoint not found"));

        endpoint.setSecret(generateWebhookSecret());
        return toResponse(endpointRepository.save(endpoint));
    }

    @Transactional
    public EndpointResponse resetCircuitBreaker(UUID projectId, UUID endpointId, UUID orgId) {
        projectService.getProjectEntity(projectId, orgId);
        Endpoint endpoint = endpointRepository.findByIdAndProjectId(endpointId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Endpoint not found"));

        endpoint.setCircuitBroken(false);
        endpoint.setCircuitBrokenAt(null);
        endpoint.setConsecutiveFailures(0);
        endpoint.setActive(true);

        return toResponse(endpointRepository.save(endpoint));
    }

    @Transactional
    public void deleteEndpoint(UUID projectId, UUID endpointId, UUID orgId) {
        projectService.getProjectEntity(projectId, orgId);
        Endpoint endpoint = endpointRepository.findByIdAndProjectId(endpointId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("Endpoint not found"));
        endpointRepository.delete(endpoint);
    }

    public static String generateWebhookSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }

    private EndpointResponse toResponse(Endpoint e) {
        return new EndpointResponse(
                e.getId(),
                e.getProject().getId(),
                e.getName(),
                e.getUrl(),
                e.getSecret(),
                e.getEventTypesFilter(),
                e.isActive(),
                e.getConsecutiveFailures(),
                e.getFailureThreshold(),
                e.isCircuitBroken(),
                e.getCircuitBrokenAt(),
                e.getRateLimitPerSecond(),
                e.getTransformRules(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
