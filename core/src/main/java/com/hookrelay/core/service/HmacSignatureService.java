package com.hookrelay.core.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class HmacSignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    public static final String SIGNATURE_HEADER = "X-HookRelay-Signature";
    public static final String TIMESTAMP_HEADER = "X-HookRelay-Timestamp";

    /**
     * Signs payload with HMAC-SHA256 over timestamp + "." + rawBody.
     * Output header format: t=1700000000,v1=abcdef...
     */
    public String generateSignatureHeader(String rawBody, String secret, long timestampSeconds) {
        String signature = computeHmac(timestampSeconds + "." + rawBody, secret);
        return "t=" + timestampSeconds + ",v1=" + signature;
    }

    /**
     * Computes raw HMAC-SHA256 hex digest.
     */
    public String computeHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    /**
     * Verifies the signature in constant time against timing attacks.
     * Prevents replay attacks if toleranceSeconds > 0.
     */
    public boolean verify(String rawBody, String headerValue, String secret, long toleranceSeconds) {
        if (headerValue == null || secret == null || rawBody == null) {
            return false;
        }

        String[] parts = headerValue.split(",");
        String timestampStr = null;
        String v1Signature = null;

        for (String part : parts) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                if ("t".equalsIgnoreCase(kv[0])) {
                    timestampStr = kv[1];
                } else if ("v1".equalsIgnoreCase(kv[0])) {
                    v1Signature = kv[1];
                }
            }
        }

        if (timestampStr == null || v1Signature == null) {
            return false;
        }

        try {
            long timestamp = Long.parseLong(timestampStr);
            if (toleranceSeconds > 0) {
                long current = Instant.now().getEpochSecond();
                if (Math.abs(current - timestamp) > toleranceSeconds) {
                    return false;
                }
            }

            String expected = computeHmac(timestamp + "." + rawBody, secret);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    v1Signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
