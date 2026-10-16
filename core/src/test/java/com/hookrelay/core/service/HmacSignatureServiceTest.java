package com.hookrelay.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HmacSignatureServiceTest {

    private HmacSignatureService signatureService;
    private final String secret = "whsec_test_secret_key_12345";
    private final String payload = "{\"event\":\"order.created\",\"amount\":99.99}";

    @BeforeEach
    void setUp() {
        signatureService = new HmacSignatureService();
    }

    @Test
    @DisplayName("Should generate valid signature header containing timestamp and v1 hex")
    void testGenerateSignature() {
        long timestamp = Instant.now().getEpochSecond();
        String header = signatureService.generateSignatureHeader(payload, secret, timestamp);

        assertNotNull(header);
        assertTrue(header.startsWith("t=" + timestamp + ",v1="));
    }

    @Test
    @DisplayName("Should verify valid signature with timestamp tolerance")
    void testVerifyValidSignature() {
        long timestamp = Instant.now().getEpochSecond();
        String header = signatureService.generateSignatureHeader(payload, secret, timestamp);

        boolean isValid = signatureService.verify(payload, header, secret, 300);
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should reject tampered payload")
    void testRejectTamperedPayload() {
        long timestamp = Instant.now().getEpochSecond();
        String header = signatureService.generateSignatureHeader(payload, secret, timestamp);

        String tamperedPayload = "{\"event\":\"order.created\",\"amount\":99999.99}";
        boolean isValid = signatureService.verify(tamperedPayload, header, secret, 300);
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject expired timestamp")
    void testRejectExpiredTimestamp() {
        long pastTimestamp = Instant.now().getEpochSecond() - 600; // 10 mins ago
        String header = signatureService.generateSignatureHeader(payload, secret, pastTimestamp);

        boolean isValid = signatureService.verify(payload, header, secret, 300); // 5 min tolerance
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject incorrect secret")
    void testRejectIncorrectSecret() {
        long timestamp = Instant.now().getEpochSecond();
        String header = signatureService.generateSignatureHeader(payload, secret, timestamp);

        boolean isValid = signatureService.verify(payload, header, "whsec_wrong_secret", 300);
        assertFalse(isValid);
    }
}
