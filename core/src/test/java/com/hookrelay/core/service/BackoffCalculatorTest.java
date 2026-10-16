package com.hookrelay.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BackoffCalculatorTest {

    private BackoffCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new BackoffCalculator();
    }

    @Test
    @DisplayName("Attempt 1 delay should fall within expected base + jitter range (~5s to 15s)")
    void testAttempt1() {
        Instant before = Instant.now();
        Instant next = calculator.calculateNextAttemptAt(1);
        long seconds = Duration.between(before, next).getSeconds();

        // 10s * [0.5, 1.5] = 5s to 15s
        assertTrue(seconds >= 4 && seconds <= 16, "Actual seconds: " + seconds);
    }

    @Test
    @DisplayName("Attempt 2 delay should be roughly ~30s with jitter")
    void testAttempt2() {
        Instant before = Instant.now();
        Instant next = calculator.calculateNextAttemptAt(2);
        long seconds = Duration.between(before, next).getSeconds();

        // 30s * [0.5, 1.5] = 15s to 45s
        assertTrue(seconds >= 14 && seconds <= 46, "Actual seconds: " + seconds);
    }

    @Test
    @DisplayName("Attempt delay increases monotonically across base tiers")
    void testMonotonicIncrease() {
        Instant next1 = calculator.calculateNextAttemptAt(1);
        Instant next3 = calculator.calculateNextAttemptAt(3);
        Instant next5 = calculator.calculateNextAttemptAt(5);

        assertTrue(next3.isAfter(next1));
        assertTrue(next5.isAfter(next3));
    }
}
