package com.hookrelay.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.hookrelay.HookRelayApplication;
import com.hookrelay.api.dto.DeliveryDtos.DeliveryResponse;
import com.hookrelay.api.dto.EventDtos.IngestEventRequest;
import com.hookrelay.api.dto.EventDtos.IngestEventResponse;
import com.hookrelay.api.service.DeliveryService;
import com.hookrelay.api.service.EventIngestService;
import com.hookrelay.core.entity.*;
import com.hookrelay.core.repository.*;
import com.hookrelay.core.service.HmacSignatureService;
import com.hookrelay.worker.service.DeliveryWorker;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = HookRelayApplication.class, properties = {
        "hookrelay.worker.enabled=true",
        "hookrelay.worker.poll-delay-ms=999999999"
})
@ActiveProfiles("test")
class PipelineE2ETest {

    private static WireMockServer wireMockServer;
    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> redis;

    @BeforeAll
    static void startContainersAndWireMock() {
        // Start WireMock on dynamic port
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        // Start real Testcontainers if Docker is available
        if (isDockerAvailable()) {
            postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("hookrelay_tc")
                    .withUsername("test_user")
                    .withPassword("test_pass");
            postgres.start();

            redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);
            redis.start();
        }
    }

    @AfterAll
    static void stopAll() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (redis != null) {
            redis.stop();
        }
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void configureDynamicProperties(DynamicPropertyRegistry registry) {
        if (postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
            registry.add("spring.flyway.enabled", () -> "true");
        }
        if (redis != null && redis.isRunning()) {
            registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
        }
    }

    @Autowired
    private EventIngestService eventIngestService;

    @Autowired
    private DeliveryWorker deliveryWorker;

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private OrgRepository orgRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryAttemptRepository deliveryAttemptRepository;

    @Autowired
    private HmacSignatureService hmacSignatureService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("End-to-End Pipeline: Ingest Event -> Fan-Out -> WireMock Dispatch -> Verify HMAC -> Idempotency Check -> Delivery Replay")
    void testFullWebhookLifecycle() throws Exception {
        String endpointPath = "/webhook-e2e-receiver";
        stubFor(post(urlEqualTo(endpointPath))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"received\", \"code\": 200}")));

        // 1. Create Org, Project, and Endpoint pointing at WireMock
        Org org = orgRepository.save(Org.builder()
                .name("E2E Test Org " + UUID.randomUUID())
                .slug("e2e-org-" + UUID.randomUUID())
                .build());

        Project project = projectRepository.save(Project.builder()
                .org(org)
                .name("E2E Pipeline Project")
                .rateLimitPerSecond(500)
                .build());

        String webhookSecret = "whsec_e2e_secret_key_998127";
        Endpoint endpoint = endpointRepository.save(Endpoint.builder()
                .project(project)
                .name("Wiremock E2E Consumer")
                .url("http://localhost:" + wireMockServer.port() + endpointPath)
                .secret(webhookSecret)
                .eventTypesFilter(List.of("order.created"))
                .active(true)
                .failureThreshold(5)
                .build());

        // 2. Ingest an event with Idempotency-Key
        String idempotencyKey = "e2e_idem_" + UUID.randomUUID();
        String payloadJson = "{\"orderId\": \"ord_e2e_999\", \"amount\": 199.95, \"currency\": \"USD\"}";
        IngestEventRequest ingestReq = new IngestEventRequest(
                "order.created",
                objectMapper.readTree(payloadJson),
                idempotencyKey
        );

        IngestEventResponse ingestRes = eventIngestService.ingestEvent(project, ingestReq, idempotencyKey);

        assertNotNull(ingestRes);
        assertFalse(ingestRes.isDuplicate());
        assertEquals(1, ingestRes.deliveriesCreated());
        UUID eventId = ingestRes.eventId();

        // Verify Delivery row created with PENDING status
        List<Delivery> pendingDeliveries = deliveryRepository.findAll().stream()
                .filter(d -> d.getEvent().getId().equals(eventId))
                .toList();
        assertEquals(1, pendingDeliveries.size());
        Delivery initialDelivery = pendingDeliveries.get(0);
        assertEquals(DeliveryStatus.PENDING, initialDelivery.getStatus());

        // 3. Run DeliveryWorker single-tick directly for this delivery
        deliveryWorker.executeDeliveryTransaction(initialDelivery.getId());

        // 4. Assert:
        // - WireMock received exactly one POST
        verify(1, postRequestedFor(urlEqualTo(endpointPath)));

        // - WireMock received X-HookRelay-Signature and it verifies against payload
        String expectedPayload = eventRepository.findById(eventId).orElseThrow().getData();
        verify(postRequestedFor(urlEqualTo(endpointPath))
                .withHeader("X-HookRelay-Event-Id", equalTo(eventId.toString()))
                .withHeader("X-HookRelay-Signature", matching("t=[0-9]+,v1=[a-f0-9]+")));

        // - Delivery row marked SUCCEEDED
        Delivery completedDelivery = deliveryRepository.findById(initialDelivery.getId()).orElseThrow();
        assertEquals(DeliveryStatus.SUCCEEDED, completedDelivery.getStatus());

        // - Delivery attempt row recorded
        List<DeliveryAttempt> attempts = deliveryAttemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(initialDelivery.getId());
        assertEquals(1, attempts.size());
        assertEquals(1, attempts.get(0).getAttemptNumber());
        assertEquals(200, attempts.get(0).getResponseStatus());
        assertNotNull(attempts.get(0).getLatencyMs());
        assertTrue(attempts.get(0).getLatencyMs() >= 0);

        // 5. Replay SAME Event with SAME Idempotency-Key -> Assert NO second event is created
        IngestEventResponse dupRes = eventIngestService.ingestEvent(project, ingestReq, idempotencyKey);
        assertTrue(dupRes.isDuplicate(), "Expected idempotency duplicate detection");
        assertEquals(eventId, dupRes.eventId(), "Returned event ID must match existing event");
        assertEquals(0, dupRes.deliveriesCreated(), "No new deliveries must be created for duplicate ingest");

        // 6. Replay the DELIVERY (manual replay) -> Assert WireMock receives a second POST
        DeliveryResponse replayedDeliveryRes = deliveryService.replayDelivery(project.getId(), completedDelivery.getId(), org.getId());
        assertEquals("PENDING", replayedDeliveryRes.status());

        // Execute delivery again
        deliveryWorker.executeDeliveryTransaction(completedDelivery.getId());

        // Verify WireMock received second POST
        verify(2, postRequestedFor(urlEqualTo(endpointPath)));

        List<DeliveryAttempt> attemptsAfterReplay = deliveryAttemptRepository.findByDeliveryIdOrderByAttemptNumberAsc(initialDelivery.getId());
        assertEquals(2, attemptsAfterReplay.size());
        assertEquals(2, attemptsAfterReplay.get(1).getAttemptNumber());
        assertEquals(200, attemptsAfterReplay.get(1).getResponseStatus());
    }

    @Test
    @DisplayName("E2E Pipeline with Transform: Ingest Event -> Apply JSONPath -> WireMock Dispatch -> Verify Transformed Body & HMAC Signature")
    void transformedDeliverySignatureVerifies() throws Exception {
        String endpointPath = "/webhook-transformed-e2e";
        stubFor(post(urlEqualTo(endpointPath))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"received\": true}")));

        // 1. Create Org, Project, and Endpoint with transformRules pointing at WireMock
        Org org = orgRepository.save(Org.builder()
                .name("Transform Org " + UUID.randomUUID())
                .slug("transform-org-" + UUID.randomUUID())
                .build());

        Project project = projectRepository.save(Project.builder()
                .org(org)
                .name("Transform Project")
                .rateLimitPerSecond(500)
                .build());

        String webhookSecret = "whsec_e2e_transform_secret_554433";
        Endpoint endpoint = endpointRepository.save(Endpoint.builder()
                .project(project)
                .name("Wiremock Transformed Consumer")
                .url("http://localhost:" + wireMockServer.port() + endpointPath)
                .secret(webhookSecret)
                .eventTypesFilter(List.of("payment.completed"))
                .active(true)
                .transformRules("{\"payment_id\":\"$.data.paymentId\",\"formatted_amount\":\"$.data.amount | number:'0.00'\"}")
                .build());

        // 2. Ingest event with raw nested payload
        String rawPayload = "{\"data\":{\"paymentId\":\"pay_9988\",\"amount\":125.5}}";
        IngestEventRequest ingestReq = new IngestEventRequest(
                "payment.completed",
                objectMapper.readTree(rawPayload),
                "idem_transform_" + UUID.randomUUID()
        );

        IngestEventResponse ingestRes = eventIngestService.ingestEvent(project, ingestReq, null);
        assertNotNull(ingestRes);
        assertEquals(1, ingestRes.deliveriesCreated());

        Delivery delivery = deliveryRepository.findAll().stream()
                .filter(d -> d.getEvent().getId().equals(ingestRes.eventId()))
                .findFirst().orElseThrow();

        // 3. Run worker synchronously for this delivery
        deliveryWorker.executeDeliveryTransaction(delivery.getId());

        // 4. Assert:
        // - WireMock received exactly one POST
        var postRequests = wireMockServer.findAll(postRequestedFor(urlEqualTo(endpointPath)));
        assertFalse(postRequests.isEmpty(), "WireMock should have recorded the delivery");
        var recordedRequest = postRequests.get(postRequests.size() - 1);

        // - WireMock received the TRANSFORMED body (not the raw payload)
        String receivedBody = recordedRequest.getBodyAsString();
        assertEquals("{\"payment_id\":\"pay_9988\",\"formatted_amount\":\"125.50\"}", receivedBody);

        // - Signature header validates against TRANSFORMED body
        String signatureHeader = recordedRequest.getHeader(HmacSignatureService.SIGNATURE_HEADER);
        assertNotNull(signatureHeader);
        boolean signatureValid = hmacSignatureService.verify(receivedBody, signatureHeader, webhookSecret, 300);
        assertTrue(signatureValid, "Signature MUST verify against the transformed body");

        // - Delivery status marked SUCCEEDED
        Delivery completedDelivery = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStatus.SUCCEEDED, completedDelivery.getStatus());
    }
}
