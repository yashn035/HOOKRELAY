package com.hookrelay.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@Profile("aws")
@ConditionalOnProperty(name = "aws.enabled", havingValue = "true")
public class CloudWatchMetricsService {

    private final CloudWatchClient cloudWatchClient;
    private final String namespace;

    public CloudWatchMetricsService(
            CloudWatchClient cloudWatchClient,
            @Value("${aws.cloudwatch.namespace:HookRelay}") String namespace
    ) {
        this.cloudWatchClient = cloudWatchClient;
        this.namespace = namespace;
    }

    public void pushQueueDepth(long depth) {
        MetricDatum datum = MetricDatum.builder()
                .metricName("QueueDepth")
                .value((double) depth)
                .unit(StandardUnit.COUNT)
                .timestamp(Instant.now())
                .dimensions(Dimension.builder().name("Service").value("DeliveryWorker").build())
                .build();

        sendMetrics(List.of(datum));
    }

    public void pushDeliveryLatencyP99(double latencyP99) {
        MetricDatum datum = MetricDatum.builder()
                .metricName("DeliveryLatencyP99")
                .value(latencyP99)
                .unit(StandardUnit.MILLISECONDS)
                .timestamp(Instant.now())
                .dimensions(Dimension.builder().name("Service").value("DeliveryWorker").build())
                .build();

        sendMetrics(List.of(datum));
    }

    public void pushMetrics(long queueDepth, double latencyP99) {
        Instant now = Instant.now();
        List<MetricDatum> datums = new ArrayList<>();

        datums.add(MetricDatum.builder()
                .metricName("QueueDepth")
                .value((double) queueDepth)
                .unit(StandardUnit.COUNT)
                .timestamp(now)
                .dimensions(Dimension.builder().name("Service").value("DeliveryWorker").build())
                .build());

        datums.add(MetricDatum.builder()
                .metricName("DeliveryLatencyP99")
                .value(latencyP99)
                .unit(StandardUnit.MILLISECONDS)
                .timestamp(now)
                .dimensions(Dimension.builder().name("Service").value("DeliveryWorker").build())
                .build());

        sendMetrics(datums);
    }

    private void sendMetrics(List<MetricDatum> datums) {
        try {
            PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(namespace)
                    .metricData(datums)
                    .build();

            cloudWatchClient.putMetricData(request);
            log.debug("Successfully pushed {} custom metrics to CloudWatch namespace [{}]", datums.size(), namespace);
        } catch (Exception ex) {
            log.error("Failed to push custom metrics to CloudWatch namespace [{}]: {}", namespace, ex.getMessage(), ex);
            throw new RuntimeException("CloudWatch metrics push failed: " + ex.getMessage(), ex);
        }
    }

    public String getNamespace() {
        return namespace;
    }
}
