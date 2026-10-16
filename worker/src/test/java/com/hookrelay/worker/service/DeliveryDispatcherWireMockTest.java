package com.hookrelay.worker.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hookrelay.core.entity.Delivery;
import com.hookrelay.core.entity.Endpoint;
import com.hookrelay.core.entity.Event;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.service.HmacSignatureService;
import com.hookrelay.core.service.SsrfProtectionService;
import com.hookrelay.worker.service.DeliveryDispatcher.DispatchResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryDispatcherWireMockTest {

    private static WireMockServer wireMockServer;
    private static DeliveryDispatcher dispatcher;
    private static HmacSignatureService hmacSignatureService;

    @BeforeAll
    static void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        hmacSignatureService = new HmacSignatureService();
        SsrfProtectionService ssrfService = new SsrfProtectionService();
        // Allow HTTP and localhost for unit testing with WireMock
        ReflectionTestUtils.setField(ssrfService, "enforceHttps", false);
        ReflectionTestUtils.setField(ssrfService, "blockPrivateIps", false);

        dispatcher = new DeliveryDispatcher(hmacSignatureService, ssrfService);
    }

    @AfterAll
    static void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Should successfully dispatch signed webhook to WireMock endpoint and verify signature")
    void testSuccessfulWebhookDispatch() {
        String endpointPath = "/test-webhook";
        stubFor(post(urlEqualTo(endpointPath))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"acknowledged\"}")));

        String secret = "whsec_mock_test_secret_123456";
        Project project = Project.builder().name("Test Project").build();
        project.setId(UUID.randomUUID());

        Endpoint endpoint = Endpoint.builder()
                .project(project)
                .name("Wiremock Endpoint")
                .url("http://localhost:" + wireMockServer.port() + endpointPath)
                .secret(secret)
                .build();
        endpoint.setId(UUID.randomUUID());

        Event event = Event.builder()
                .project(project)
                .type("order.created")
                .data("{\"orderId\": \"ord_1001\", \"total\": 49.99}")
                .build();
        event.setId(UUID.randomUUID());

        Delivery delivery = Delivery.builder()
                .project(project)
                .endpoint(endpoint)
                .event(event)
                .build();
        delivery.setId(UUID.randomUUID());

        DispatchResult result = dispatcher.dispatch(delivery);

        assertTrue(result.successful());
        assertEquals(200, result.httpStatusCode());
        assertNotNull(result.responseBody());
        assertTrue(result.responseBody().contains("acknowledged"));
        assertTrue(result.latencyMs() >= 0);

        // Verify request received with HMAC signature headers
        verify(postRequestedFor(urlEqualTo(endpointPath))
                .withHeader("X-HookRelay-Event-Id", equalTo(event.getId().toString()))
                .withHeader("X-HookRelay-Event-Type", equalTo("order.created"))
                .withHeader("X-HookRelay-Delivery-Id", equalTo(delivery.getId().toString()))
                .withHeader("X-HookRelay-Signature", matching("t=[0-9]+,v1=[a-f0-9]+")));
    }

    @Test
    @DisplayName("Should record failure on HTTP 500 error from subscriber")
    void testFailedWebhookDispatch() {
        String endpointPath = "/failing-webhook";
        stubFor(post(urlEqualTo(endpointPath))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        Project project = Project.builder().name("Test Project").build();
        project.setId(UUID.randomUUID());

        Endpoint endpoint = Endpoint.builder()
                .project(project)
                .name("Failing Endpoint")
                .url("http://localhost:" + wireMockServer.port() + endpointPath)
                .secret("whsec_secret")
                .build();
        endpoint.setId(UUID.randomUUID());

        Event event = Event.builder()
                .project(project)
                .type("payment.failed")
                .data("{\"chargeId\": \"ch_999\"}")
                .build();
        event.setId(UUID.randomUUID());

        Delivery delivery = Delivery.builder()
                .project(project)
                .endpoint(endpoint)
                .event(event)
                .build();
        delivery.setId(UUID.randomUUID());

        DispatchResult result = dispatcher.dispatch(delivery);

        assertFalse(result.successful());
        assertEquals(500, result.httpStatusCode());
        assertTrue(result.errorMessage().contains("500"));
    }
}
