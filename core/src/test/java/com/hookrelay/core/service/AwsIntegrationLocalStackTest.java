package com.hookrelay.core.service;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.CLOUDWATCH;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

class AwsIntegrationLocalStackTest {

    private static final Logger log = LoggerFactory.getLogger(AwsIntegrationLocalStackTest.class);
    private static final String BUCKET_NAME = "hookrelay-delivery-logs";
    private static final String CW_NAMESPACE = "HookRelay";

    private static LocalStackContainer localStack;
    private static boolean useLocalStack = false;

    private S3Client s3Client;
    private CloudWatchClient cloudWatchClient;
    private S3ArchivalService s3ArchivalService;
    private CloudWatchMetricsService cloudWatchMetricsService;

    @BeforeAll
    static void initLocalStack() {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4"))
                        .withServices(S3, CLOUDWATCH);
                localStack.start();
                useLocalStack = true;
                log.info("LocalStack Testcontainers started at endpoint: {}", localStack.getEndpoint());
            } else {
                log.warn("Docker is not available. Running AWS tests using mocks to verify logic and coverage.");
            }
        } catch (Throwable t) {
            log.warn("LocalStack startup skipped due to environment: {}", t.getMessage());
            useLocalStack = false;
        }
    }

    @AfterAll
    static void tearDownLocalStack() {
        if (localStack != null) {
            localStack.stop();
        }
    }

    @BeforeEach
    void setUp() {
        if (useLocalStack && localStack != null && localStack.isRunning()) {
            s3Client = S3Client.builder()
                    .endpointOverride(localStack.getEndpointOverride(S3))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                    .region(Region.of(localStack.getRegion()))
                    .forcePathStyle(true)
                    .build();

            // Create S3 Bucket if it doesn't exist
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
            } catch (Exception ignored) {}

            cloudWatchClient = CloudWatchClient.builder()
                    .endpointOverride(localStack.getEndpointOverride(CLOUDWATCH))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
                    .region(Region.of(localStack.getRegion()))
                    .build();
        } else {
            s3Client = mock(S3Client.class);
            cloudWatchClient = mock(CloudWatchClient.class);
        }

        s3ArchivalService = new S3ArchivalService(s3Client, BUCKET_NAME);
        cloudWatchMetricsService = new CloudWatchMetricsService(cloudWatchClient, CW_NAMESPACE);
    }

    @Test
    @DisplayName("S3 Archival: Gzips delivery log, partitions by /yyyy/MM/dd/, and sets AES256 encryption")
    void testS3DeliveryLogArchival() throws IOException {
        UUID deliveryId = UUID.randomUUID();
        Instant timestamp = Instant.parse("2026-11-08T12:00:00Z");
        String payload = """
                {
                  "eventId": "evt_99120",
                  "eventType": "order.created",
                  "customer": "enterprise_user@example.com",
                  "amount": 499.50,
                  "status": "SUCCEEDED"
                }
                """;

        String s3Key = s3ArchivalService.archiveDeliveryLog(deliveryId, payload, timestamp);

        assertNotNull(s3Key);
        assertTrue(s3Key.startsWith("2026/11/08/"), "S3 key should be partitioned by date /yyyy/MM/dd/");
        assertTrue(s3Key.endsWith(deliveryId + ".json.gz"), "S3 key should end with {deliveryId}.json.gz");

        if (useLocalStack && localStack != null && localStack.isRunning()) {
            // Verify by fetching directly from LocalStack S3
            var getObjectResponse = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(BUCKET_NAME)
                    .key(s3Key)
                    .build());

            assertEquals("gzip", getObjectResponse.response().contentEncoding());
            assertEquals(ServerSideEncryption.AES256, getObjectResponse.response().serverSideEncryption());

            byte[] downloadedBytes = getObjectResponse.readAllBytes();
            String decompressed = s3ArchivalService.decompressGzip(downloadedBytes);
            assertEquals(payload, decompressed);
        } else {
            // Verify S3Client interaction
            ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(putCaptor.capture(), any(RequestBody.class));

            PutObjectRequest captured = putCaptor.getValue();
            assertEquals(BUCKET_NAME, captured.bucket());
            assertEquals(s3Key, captured.key());
            assertEquals(ServerSideEncryption.AES256, captured.serverSideEncryption());
            assertEquals("gzip", captured.contentEncoding());
            assertEquals("application/json", captured.contentType());
        }
    }

    @Test
    @DisplayName("S3 Archival: Validates gzip compression and decompression helpers")
    void testGzipCompressionCycle() throws IOException {
        String original = "HookRelay High-Throughput Delivery Engine Test Payload";
        byte[] compressed = s3ArchivalService.compressGzip(original);
        assertNotNull(compressed);
        assertTrue(compressed.length > 0);

        String decompressed = s3ArchivalService.decompressGzip(compressed);
        assertEquals(original, decompressed);
    }

    @Test
    @DisplayName("S3 Archival: Rejects invalid parameters gracefully")
    void testS3ArchivalInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () ->
                s3ArchivalService.archiveDeliveryLog(null, "log", Instant.now()));
        assertThrows(IllegalArgumentException.class, () ->
                s3ArchivalService.archiveDeliveryLog(UUID.randomUUID(), null, Instant.now()));
    }

    @Test
    @DisplayName("CloudWatch Metrics: Pushes QueueDepth and DeliveryLatencyP99 custom metrics")
    void testCloudWatchMetricsPush() {
        long queueDepth = 42;
        double latencyP99 = 128.5;

        // Test combined push
        cloudWatchMetricsService.pushMetrics(queueDepth, latencyP99);

        // Test individual metric pushes
        cloudWatchMetricsService.pushQueueDepth(10);
        cloudWatchMetricsService.pushDeliveryLatencyP99(85.2);

        if (!useLocalStack || localStack == null || !localStack.isRunning()) {
            ArgumentCaptor<PutMetricDataRequest> captor = ArgumentCaptor.forClass(PutMetricDataRequest.class);
            verify(cloudWatchClient, atLeast(3)).putMetricData(captor.capture());

            PutMetricDataRequest captured = captor.getAllValues().get(0);
            assertEquals(CW_NAMESPACE, captured.namespace());
            assertEquals(2, captured.metricData().size());

            var queueMetric = captured.metricData().stream()
                    .filter(m -> "QueueDepth".equals(m.metricName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(42.0, queueMetric.value());
            assertEquals(StandardUnit.COUNT, queueMetric.unit());

            var latencyMetric = captured.metricData().stream()
                    .filter(m -> "DeliveryLatencyP99".equals(m.metricName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(128.5, latencyMetric.value());
            assertEquals(StandardUnit.MILLISECONDS, latencyMetric.unit());
        }
    }
}
