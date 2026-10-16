package com.hookrelay.worker.service;

import com.hookrelay.core.entity.Delivery;
import com.hookrelay.core.entity.DeliveryAttempt;
import com.hookrelay.core.entity.Endpoint;
import com.hookrelay.core.entity.Event;
import com.hookrelay.core.service.HmacSignatureService;
import com.hookrelay.core.service.PayloadTransformService;
import com.hookrelay.core.service.SsrfProtectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DeliveryDispatcher {

    private final HmacSignatureService hmacSignatureService;
    private final SsrfProtectionService ssrfProtectionService;
    private final PayloadTransformService payloadTransformService;
    private final HttpClient httpClient;

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RESPONSE_BODY_LENGTH = 4096;

    public DeliveryDispatcher(
            HmacSignatureService hmacSignatureService,
            SsrfProtectionService ssrfProtectionService,
            PayloadTransformService payloadTransformService
    ) {
        this.hmacSignatureService = hmacSignatureService;
        this.ssrfProtectionService = ssrfProtectionService;
        this.payloadTransformService = payloadTransformService;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER) // Prevent SSRF redirect hijacking
                .build();
    }

    public record DispatchResult(
            boolean successful,
            int httpStatusCode,
            long latencyMs,
            String responseBody,
            String responseHeaders,
            String errorMessage
    ) {}

    public DispatchResult dispatch(Delivery delivery) {
        Endpoint endpoint = delivery.getEndpoint();
        Event event = delivery.getEvent();

        // 1. SSRF and Destination Validation
        try {
            ssrfProtectionService.validateEndpointUrl(endpoint.getUrl());
        } catch (Exception e) {
            log.warn("Delivery {} destination SSRF blocked: {}", delivery.getId(), e.getMessage());
            return new DispatchResult(false, 0, 0, null, null, "SSRF validation failed: " + e.getMessage());
        }

        // 2. Apply Payload Transformation (if configured)
        String payload = event.getData();
        if (endpoint.getTransformRules() != null && !endpoint.getTransformRules().isBlank()) {
            try {
                payload = payloadTransformService.transform(payload, endpoint.getTransformRules());
            } catch (Exception e) {
                log.warn("Delivery {} payload transform failed: {}", delivery.getId(), e.getMessage());
                return new DispatchResult(false, 0, 0, null, null, "Payload transformation failed: " + e.getMessage());
            }
        }

        // 3. Compute HMAC Signature
        long timestampSeconds = Instant.now().getEpochSecond();
        String signatureHeader = hmacSignatureService.generateSignatureHeader(payload, endpoint.getSecret(), timestampSeconds);

        // 3. Build HTTP Request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.getUrl()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", "HookRelay/1.0.0 (Webhook Delivery Engine)")
                .header("X-HookRelay-Event-Id", event.getId().toString())
                .header("X-HookRelay-Event-Type", event.getType())
                .header("X-HookRelay-Delivery-Id", delivery.getId().toString())
                .header(HmacSignatureService.TIMESTAMP_HEADER, String.valueOf(timestampSeconds))
                .header(HmacSignatureService.SIGNATURE_HEADER, signatureHeader)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        long start = System.currentTimeMillis();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            int statusCode = response.statusCode();
            boolean success = (statusCode >= 200 && statusCode < 300);

            String body = response.body();
            if (body != null && body.length() > MAX_RESPONSE_BODY_LENGTH) {
                body = body.substring(0, MAX_RESPONSE_BODY_LENGTH) + "... [truncated]";
            }

            String headers = response.headers().map().entrySet().stream()
                    .map(e -> e.getKey() + ": " + String.join(", ", e.getValue()))
                    .collect(Collectors.joining("\n"));

            return new DispatchResult(success, statusCode, latency, body, headers, success ? null : "HTTP status " + statusCode);
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - start;
            log.debug("Delivery {} failed dispatch to {}: {}", delivery.getId(), endpoint.getUrl(), ex.getMessage());
            return new DispatchResult(false, 0, latency, null, null, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}
