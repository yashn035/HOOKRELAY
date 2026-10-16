package com.hookrelay.core.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BackoffCalculator {

    // Base delays in seconds for attempts 1 through 7
    private static final long[] BASE_DELAYS_SECONDS = {
            10,       // attempt 1 -> next attempt in ~10s
            30,       // attempt 2 -> next attempt in ~30s
            120,      // attempt 3 -> next attempt in ~2m
            600,      // attempt 4 -> next attempt in ~10m
            3600,     // attempt 5 -> next attempt in ~1h
            21600,    // attempt 6 -> next attempt in ~6h
            86400     // attempt 7 -> next attempt in ~24h
    };

    /**
     * Calculates the next attempt timestamp using exponential backoff with full jitter.
     * Full jitter selects a random delay between [0.5 * base, 1.5 * base] to prevent thundering herd.
     *
     * @param attemptNumber current attempt number (1-based)
     * @return Instant for the next delivery attempt
     */
    public Instant calculateNextAttemptAt(int attemptNumber) {
        int index = Math.min(Math.max(1, attemptNumber) - 1, BASE_DELAYS_SECONDS.length - 1);
        long baseDelay = BASE_DELAYS_SECONDS[index];

        // Apply 50% to 150% jitter
        double jitterMultiplier = ThreadLocalRandom.current().nextDouble(0.5, 1.5);
        long actualDelaySeconds = Math.max(1, (long) (baseDelay * jitterMultiplier));

        return Instant.now().plus(Duration.ofSeconds(actualDelaySeconds));
    }
}
