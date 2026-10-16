package com.hookrelay.api.service;

import com.hookrelay.api.dto.EndpointDtos.CreateEndpointRequest;
import com.hookrelay.api.dto.EndpointDtos.EndpointResponse;
import com.hookrelay.api.dto.EndpointDtos.TestTransformRequest;
import com.hookrelay.api.dto.EndpointDtos.TestTransformResponse;
import com.hookrelay.core.entity.Endpoint;
import com.hookrelay.core.entity.Org;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.repository.EndpointRepository;
import com.hookrelay.core.service.PayloadTransformService;
import com.hookrelay.core.service.PayloadTransformService.TransformResult;
import com.hookrelay.core.service.SsrfProtectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EndpointServiceTest {

    @Mock
    private EndpointRepository endpointRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private SsrfProtectionService ssrfProtectionService;

    @Mock
    private PayloadTransformService payloadTransformService;

    @InjectMocks
    private EndpointService endpointService;

    private Project testProject;
    private Endpoint testEndpoint;
    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID endpointId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Org org = Org.builder().name("Test Org").slug("test-org").build();
        org.setId(orgId);

        testProject = Project.builder().org(org).name("Production").build();
        testProject.setId(projectId);

        testEndpoint = Endpoint.builder()
                .project(testProject)
                .name("Stripe Sink")
                .url("https://httpbin.org/post")
                .secret("whsec_test_secret")
                .active(true)
                .failureThreshold(5)
                .transformRules("{\"orderId\": \"$.data.id\"}")
                .build();
        testEndpoint.setId(endpointId);
    }

    @Test
    @DisplayName("Should create endpoint with auto-generated secret and SSRF validation")
    void testCreateEndpoint() {
        CreateEndpointRequest req = new CreateEndpointRequest(
                "Stripe Sink",
                "https://httpbin.org/post",
                null, // test auto-generated secret
                List.of("order.created"),
                5,
                100,
                "{\"orderId\": \"$.data.id\"}"
        );

        when(projectService.getProjectEntity(projectId, orgId)).thenReturn(testProject);
        when(endpointRepository.save(any(Endpoint.class))).thenAnswer(i -> {
            Endpoint ep = i.getArgument(0);
            ep.setId(endpointId);
            return ep;
        });

        EndpointResponse res = endpointService.createEndpoint(projectId, orgId, req);

        assertNotNull(res);
        assertEquals("Stripe Sink", res.name());
        assertTrue(res.secret().startsWith("whsec_"));
        assertEquals("{\"orderId\": \"$.data.id\"}", res.transformRules());
        verify(ssrfProtectionService).validateEndpointUrl("https://httpbin.org/post");
        verify(payloadTransformService).validateRules("{\"orderId\": \"$.data.id\"}");
        verify(endpointRepository).save(any(Endpoint.class));
    }

    @Test
    @DisplayName("Should test transform preview")
    void testTestTransformPreview() {
        TestTransformRequest req = new TestTransformRequest("{\"data\":{\"id\":\"123\"}}", "{\"orderId\":\"$.data.id\"}");
        when(projectService.getProjectEntity(projectId, orgId)).thenReturn(testProject);
        when(payloadTransformService.testTransform(req.payload(), req.rules()))
                .thenReturn(new TransformResult(true, "{\"orderId\":\"123\"}", null));

        TestTransformResponse res = endpointService.testTransform(projectId, orgId, req);

        assertTrue(res.valid());
        assertEquals("{\"orderId\":\"123\"}", res.transformedPayload());
        assertNull(res.errorMessage());
    }

    @Test
    @DisplayName("Should reset tripped circuit breaker")
    void testResetCircuitBreaker() {
        testEndpoint.setCircuitBroken(true);
        testEndpoint.setConsecutiveFailures(10);

        when(projectService.getProjectEntity(projectId, orgId)).thenReturn(testProject);
        when(endpointRepository.findByIdAndProjectId(endpointId, projectId)).thenReturn(Optional.of(testEndpoint));
        when(endpointRepository.save(any(Endpoint.class))).thenAnswer(i -> i.getArgument(0));

        EndpointResponse res = endpointService.resetCircuitBreaker(projectId, endpointId, orgId);

        assertFalse(res.circuitBroken());
        assertEquals(0, res.consecutiveFailures());
        assertTrue(res.active());
    }

    @Test
    @DisplayName("Should rotate signing secret")
    void testRotateSecret() {
        when(projectService.getProjectEntity(projectId, orgId)).thenReturn(testProject);
        when(endpointRepository.findByIdAndProjectId(endpointId, projectId)).thenReturn(Optional.of(testEndpoint));
        when(endpointRepository.save(any(Endpoint.class))).thenAnswer(i -> i.getArgument(0));

        EndpointResponse res = endpointService.rotateSecret(projectId, endpointId, orgId);

        assertNotNull(res.secret());
        assertNotEquals("whsec_test_secret", res.secret());
    }
}
