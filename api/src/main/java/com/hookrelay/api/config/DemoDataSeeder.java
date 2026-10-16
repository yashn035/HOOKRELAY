package com.hookrelay.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hookrelay.core.entity.*;
import com.hookrelay.core.repository.*;
import com.hookrelay.core.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class DemoDataSeeder implements CommandLineRunner {

    public static final String DEMO_API_KEY = "hr_live_demo_acme_production_key_001";

    private final OrgRepository orgRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final ApiKeyService apiKeyService;
    private final EndpointRepository endpointRepository;
    private final EventRepository eventRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryAttemptRepository deliveryAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public DemoDataSeeder(
            OrgRepository orgRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            ApiKeyService apiKeyService,
            EndpointRepository endpointRepository,
            EventRepository eventRepository,
            DeliveryRepository deliveryRepository,
            DeliveryAttemptRepository deliveryAttemptRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper
    ) {
        this.orgRepository = orgRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.apiKeyService = apiKeyService;
        this.endpointRepository = endpointRepository;
        this.eventRepository = eventRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryAttemptRepository = deliveryAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmail("admin@hookrelay.dev")) {
            return;
        }

        log.info("Seeding initial demo tenant, endpoints, and 100 webhook events...");

        // 1. Create Demo Org
        Org org = Org.builder()
                .name("Acme Cloud Corp")
                .slug("acme-cloud")
                .build();
        Org savedOrg = orgRepository.save(org);

        // 2. Create Demo User
        User user = User.builder()
                .org(savedOrg)
                .email("admin@hookrelay.dev")
                .fullName("Sarah Connor")
                .passwordHash(passwordEncoder.encode("password123!"))
                .role(Role.ADMIN)
                .build();
        userRepository.save(user);

        // 3. Create Demo Project
        Project project = Project.builder()
                .org(savedOrg)
                .name("Production Gateway")
                .description("Main multi-region production event pipeline")
                .rateLimitPerSecond(250)
                .build();
        Project savedProject = projectRepository.save(project);

        // 4. Create Demo API Key
        ApiKey apiKey = apiKeyService.createDeterministicApiKey(savedProject, "E-Commerce Ingest Service", DEMO_API_KEY);
        log.info("Seeded Demo Tenant API Key: {}", DEMO_API_KEY);

        // 5. Create Endpoints
        Endpoint ep1 = Endpoint.builder()
                .project(savedProject)
                .name("Billing Microservice (Stripe Sink)")
                .url("https://httpbin.org/post")
                .secret("whsec_billing_prod_secret_882910")
                .eventTypesFilter(List.of("payment.succeeded", "invoice.generated"))
                .active(true)
                .failureThreshold(5)
                .build();

        Endpoint ep2 = Endpoint.builder()
                .project(savedProject)
                .name("Order Fulfillment Webhook")
                .url("https://httpbin.org/post")
                .secret("whsec_fulfillment_prod_secret_994112")
                .eventTypesFilter(List.of("order.created", "order.shipped"))
                .active(true)
                .failureThreshold(5)
                .build();

        Endpoint ep3 = Endpoint.builder()
                .project(savedProject)
                .name("Legacy Analytics Collector (Degraded / Failing)")
                .url("https://invalid-host-mock.internal/collector")
                .secret("whsec_legacy_collector_secret_112233")
                .eventTypesFilter(List.of())
                .active(true)
                .consecutiveFailures(6)
                .failureThreshold(5)
                .circuitBroken(true)
                .circuitBrokenAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build();

        endpointRepository.saveAll(List.of(ep1, ep2, ep3));

        // 6. Seed 100 Events and Deliveries with Attempt Logs
        String[] eventTypes = {"order.created", "payment.succeeded", "order.shipped", "invoice.generated", "customer.churned"};
        List<Event> events = new ArrayList<>();
        List<Delivery> deliveries = new ArrayList<>();
        List<DeliveryAttempt> attempts = new ArrayList<>();

        Instant now = Instant.now();

        for (int i = 1; i <= 100; i++) {
            String type = eventTypes[i % eventTypes.length];
            Instant eventTime = now.minus(ThreadLocalRandom.current().nextInt(1, 1440), ChronoUnit.MINUTES);

            Map<String, Object> payloadMap = Map.of(
                    "id", "evt_" + (1000 + i),
                    "type", type,
                    "amount", 10.0 + (i * 3.75),
                    "currency", "USD",
                    "customerId", "cust_" + (500 + (i % 20)),
                    "timestamp", eventTime.toString()
            );

            String dataJson;
            try {
                dataJson = objectMapper.writeValueAsString(payloadMap);
            } catch (Exception e) {
                dataJson = "{}";
            }

            Event event = Event.builder()
                    .project(savedProject)
                    .type(type)
                    .data(dataJson)
                    .idempotencyKey("idem_" + i + "_" + eventTime.getEpochSecond())
                    .createdAt(eventTime)
                    .build();
            events.add(event);

            // Determine subscriber
            Endpoint targetEp = (i % 3 == 0) ? ep3 : (i % 2 == 0 ? ep2 : ep1);
            boolean isSuccessful = (targetEp != ep3);

            Delivery delivery = Delivery.builder()
                    .project(savedProject)
                    .endpoint(targetEp)
                    .event(event)
                    .status(isSuccessful ? DeliveryStatus.SUCCEEDED : DeliveryStatus.DEAD_LETTER)
                    .attemptCount(isSuccessful ? 1 : 8)
                    .maxAttempts(8)
                    .nextAttemptAt(eventTime.plusSeconds(10))
                    .build();
            delivery.setCreatedAt(eventTime);
            delivery.setUpdatedAt(eventTime.plusSeconds(1));
            deliveries.add(delivery);

            // Delivery attempt log
            DeliveryAttempt attempt = DeliveryAttempt.builder()
                    .delivery(delivery)
                    .attemptNumber(1)
                    .responseStatus(isSuccessful ? 200 : 503)
                    .responseBody(isSuccessful ? "{\"received\": true, \"status\": \"PROCESSED\"}" : "{\"error\": \"Service Unavailable\"}")
                    .responseHeaders("Content-Type: application/json\nServer: gunicorn")
                    .latencyMs((long) ThreadLocalRandom.current().nextInt(25, 220))
                    .errorMessage(isSuccessful ? null : "HTTP status 503: Service Unavailable")
                    .executedAt(eventTime.plusSeconds(1))
                    .build();
            attempts.add(attempt);
        }

        eventRepository.saveAll(events);
        deliveryRepository.saveAll(deliveries);
        deliveryAttemptRepository.saveAll(attempts);

        log.info("Demo seeding completed! Created demo user [admin@hookrelay.dev / password123!], 3 endpoints, and 100 historical webhook deliveries.");
    }
}
