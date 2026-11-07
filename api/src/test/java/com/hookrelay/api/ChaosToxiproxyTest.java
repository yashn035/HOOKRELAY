package com.hookrelay.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.hookrelay.HookRelayApplication;
import com.hookrelay.api.dto.EventDtos.IngestEventRequest;
import com.hookrelay.api.service.EventIngestService;
import com.hookrelay.core.entity.*;
import com.hookrelay.core.repository.*;
import com.hookrelay.worker.service.DeliveryWorker;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"deprecation", "resource"})
@SpringBootTest(classes = HookRelayApplication.class, properties = {
        "hookrelay.worker.enabled=true",
        "hookrelay.worker.poll-delay-ms=999999999",
        "hookrelay.security.enforce-https=false",
        "hookrelay.security.block-private-ips=false"
})
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChaosToxiproxyTest {

    private static final Logger log = LoggerFactory.getLogger(ChaosToxiproxyTest.class);

    private static WireMockServer wireMockServer;
    private static Network network;
    private static ToxiproxyContainer toxiproxyContainer;
    private static ToxiproxyContainer.ContainerProxy proxy;
    private static boolean useDockerToxiproxy = false;
    private static int wireMockPort;

    @Autowired
    private EventIngestService eventIngestService;

    @Autowired
    private DeliveryWorker deliveryWorker;

    @Autowired
    private OrgRepository orgRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeadLetterRepository deadLetterRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static Org testOrg;
    private static Project testProject;

    @BeforeAll
    static void setUpAll() {
        // 1. Start dynamic WireMock downstream mock receiver
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        wireMockPort = wireMockServer.port();
        WireMock.configureFor("localhost", wireMockPort);

        // 2. Determine if Docker is available for Testcontainers Toxiproxy
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                network = Network.newNetwork();
                toxiproxyContainer = new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.9.0")
                        .withNetwork(network);
                toxiproxyContainer.start();

                // WireMock runs on host machine, bridge into container host
                org.testcontainers.Testcontainers.exposeHostPorts(wireMockPort);
                proxy = toxiproxyContainer.getProxy("host.testcontainers.internal", wireMockPort);
                useDockerToxiproxy = true;
                log.info("Testcontainers Toxiproxy successfully booted. Downstream proxy port: {}", proxy.getProxyPort());
            } else {
                log.warn("Docker is not available in current environment. Using simulated WireMock toxics for chaos validation.");
            }
        } catch (Throwable t) {
            log.warn("Toxiproxy Testcontainers setup skipped due to environment: {}", t.getMessage());
            useDockerToxiproxy = false;
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        if (toxiproxyContainer != null) {
            toxiproxyContainer.stop();
        }
        if (network != null) {
            try {
                network.close();
            } catch (Exception ignored) {}
        }
    }

    @BeforeEach
    void setupTestData() {
        if (testOrg == null) {
            testOrg = orgRepository.save(Org.builder()
                    .name("Chaos Engineering Org " + UUID.randomUUID())
                    .slug("chaos-org-" + UUID.randomUUID())
                    .build());
            testProject = projectRepository.save(Project.builder()
                    .org(testOrg)
                    .name("Chaos Project")
                    .description("Toxiproxy Chaos Test Workspace")
                    .build());
        }
    }

    private String getTargetEndpointUrl(String downstreamPath) {
        if (useDockerToxiproxy && proxy != null) {
            return "http://" + proxy.getContainerIpAddress() + ":" + proxy.getProxyPort() + downstreamPath;
        }
        return "http://localhost:" + wireMockPort + downstreamPath;
    }

    @Test
    @Order(1)
    @DisplayName("Chaos Test 1: Injects 1500ms latency toxic -> Delivery eventually succeeds despite network degradation")
    void testLatencyToxic_ShouldEventuallySucceed() throws Exception {
        String path = "/chaos-latency-sink";
        stubFor(post(urlEqualTo(path))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\": \"ACKNOWLEDGED_UNDER_LATENCY\"}")));

        // Inject 1500ms downstream latency toxic
        if (useDockerToxiproxy && proxy != null) {
            proxy.toxics().latency("latency_toxic", ToxicDirection.DOWNSTREAM, 1500L);
        } else {
            // Emulate 1500ms network delay on WireMock stub when Docker Toxiproxy is bypassed
            stubFor(post(urlEqualTo(path))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withFixedDelay(1500)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"status\": \"ACKNOWLEDGED_UNDER_LATENCY\"}")));
        }

        String targetUrl = getTargetEndpointUrl(path);

        Endpoint endpoint = endpointRepository.save(Endpoint.builder()
                .project(testProject)
                .name("Chaos Latency Endpoint")
                .url(targetUrl)
                .secret("whsec_chaos_latency_secret_998877")
                .active(true)
                .failureThreshold(5)
                .eventTypesFilter(List.of("order.created"))
                .build());

        String payloadJson = "{\"orderId\": \"ord_chaos_001\", \"amount\": 49.99}";
        IngestEventRequest ingestRequest = new IngestEventRequest(
                "order.created",
                objectMapper.readTree(payloadJson),
                "idem_chaos_lat_" + UUID.randomUUID()
        );

        // Ingest event
        var ingestResponse = eventIngestService.ingestEvent(testProject, ingestRequest, ingestRequest.idempotencyKey());
        assertNotNull(ingestResponse.eventId());

        List<Delivery> deliveries = deliveryRepository.findAll();
        Delivery delivery = deliveries.stream()
                .filter(d -> d.getEndpoint().getId().equals(endpoint.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Delivery not found for endpoint"));

        // Worker dispatches through Toxiproxy
        long start = System.currentTimeMillis();
        deliveryWorker.executeDeliveryTransaction(delivery.getId());
        long duration = System.currentTimeMillis() - start;

        // Verify the delivery was processed and succeeded despite 1.5s latency
        Delivery updatedDelivery = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStatus.SUCCEEDED, updatedDelivery.getStatus(), "Delivery should succeed through latency proxy");
        assertTrue(duration >= 1400, "Dispatch should have incurred >= 1.5s toxic latency (actual: " + duration + "ms)");

        // Clean up toxic
        if (useDockerToxiproxy && proxy != null) {
            proxy.toxics().get("latency_toxic").remove();
        }
    }

    @Test
    @Order(2)
    @DisplayName("Chaos Test 2: Injects reset_peer toxic -> Delivery exhausts retries and lands in DLQ")
    void testResetPeerToxic_ShouldExhaustRetriesAndMoveToDLQ() throws Exception {
        String path = "/chaos-reset-peer-sink";

        // Inject reset_peer toxic to simulate connection reset / hard TCP failure
        if (useDockerToxiproxy && proxy != null) {
            proxy.toxics().resetPeer("reset_peer_toxic", ToxicDirection.DOWNSTREAM, 0L);
        } else {
            // Emulate connection reset on WireMock stub
            stubFor(post(urlEqualTo(path))
                    .willReturn(aResponse()
                            .withFault(Fault.CONNECTION_RESET_BY_PEER)));
        }

        String targetUrl = getTargetEndpointUrl(path);

        Endpoint endpoint = endpointRepository.save(Endpoint.builder()
                .project(testProject)
                .name("Chaos Reset Peer Endpoint")
                .url(targetUrl)
                .secret("whsec_chaos_reset_secret_123456")
                .active(true)
                .failureThreshold(2)
                .eventTypesFilter(List.of("payment.failed"))
                .build());

        String payloadJson = "{\"paymentId\": \"pay_chaos_reset_001\", \"reason\": \"insufficient_funds\"}";
        IngestEventRequest ingestRequest = new IngestEventRequest(
                "payment.failed",
                objectMapper.readTree(payloadJson),
                "idem_chaos_reset_" + UUID.randomUUID()
        );

        // Ingest event into HookRelay
        var ingestResponse = eventIngestService.ingestEvent(testProject, ingestRequest, ingestRequest.idempotencyKey());
        assertNotNull(ingestResponse.eventId());

        List<Delivery> deliveries = deliveryRepository.findAll();
        Delivery delivery = deliveries.stream()
                .filter(d -> d.getEndpoint().getId().equals(endpoint.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Delivery not created"));

        // Set maxAttempts to 2 to deterministically test retry exhaustion
        delivery.setMaxAttempts(2);
        deliveryRepository.save(delivery);

        // Attempt 1: Encounters reset_peer -> fails and schedules retry
        deliveryWorker.executeDeliveryTransaction(delivery.getId());
        Delivery afterAttempt1 = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStatus.PENDING, afterAttempt1.getStatus());
        assertEquals(1, afterAttempt1.getAttemptCount());

        // Attempt 2: Encounters reset_peer -> exhausts max retries (2/2) -> moves to DLQ
        deliveryWorker.executeDeliveryTransaction(delivery.getId());

        Delivery finalDelivery = deliveryRepository.findById(delivery.getId()).orElseThrow();
        assertEquals(DeliveryStatus.DEAD_LETTER, finalDelivery.getStatus(),
                "Delivery should transition to DEAD_LETTER after retries exhausted by reset_peer");

        // Assert DLQ entry exists
        List<DeadLetter> deadLetters = deadLetterRepository.findAll();
        assertFalse(deadLetters.isEmpty(), "DLQ should contain entry for failed delivery");
        DeadLetter deadLetter = deadLetters.stream()
                .filter(dl -> dl.getDelivery().getId().equals(delivery.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Dead letter record not found"));

        assertEquals("MAX_ATTEMPTS_EXCEEDED", deadLetter.getReason());
        assertTrue(deadLetter.getDetail().contains("Failed after 2 attempts"));

        // Clean up toxic
        if (useDockerToxiproxy && proxy != null) {
            proxy.toxics().get("reset_peer_toxic").remove();
        }
    }
}

