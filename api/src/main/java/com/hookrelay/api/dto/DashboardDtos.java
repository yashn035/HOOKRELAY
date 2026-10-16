package com.hookrelay.api.dto;

import java.util.List;
import java.util.UUID;

public class DashboardDtos {

    public record TimeSeriesPoint(
            String timestamp,
            long succeeded,
            long failed
    ) {}

    public record LatencyBucket(
            String range,
            long count
    ) {}

    public record FailingEndpointStat(
            UUID endpointId,
            String endpointName,
            String url,
            int consecutiveFailures,
            boolean circuitBroken
    ) {}

    public record DashboardOverview(
            long totalEvents,
            long totalDeliveries,
            long succeededDeliveries,
            long failedDeliveries,
            long pendingDeliveries,
            long deadLetterDeliveries,
            double successRatePercent,
            double p95LatencyMs,
            double avgLatencyMs,
            List<TimeSeriesPoint> deliveryChart,
            List<LatencyBucket> latencyDistribution,
            List<FailingEndpointStat> topFailingEndpoints
    ) {}
}
