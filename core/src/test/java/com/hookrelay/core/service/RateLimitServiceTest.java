package com.hookrelay.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    @Test
    @DisplayName("Should allow requests within specified limit")
    void testAllowWithinLimit() {
        RateLimitResult res1 = rateLimitService.checkRateLimit(projectId, 5);
        assertTrue(res1.allowed());
        assertEquals(5, res1.limit());
        assertEquals(4, res1.remaining());

        RateLimitResult res2 = rateLimitService.checkRateLimit(projectId, 5);
        assertTrue(res2.allowed());
        assertEquals(3, res2.remaining());
    }

    @Test
    @DisplayName("Should return 429 rate limit exceeded when threshold reached")
    void testBlockExceedingLimit() {
        for (int i = 0; i < 5; i++) {
            rateLimitService.checkRateLimit(projectId, 5);
        }

        // 6th request must be rejected
        RateLimitResult res = rateLimitService.checkRateLimit(projectId, 5);
        assertFalse(res.allowed());
        assertEquals(0, res.remaining());
        assertTrue(res.retryAfterSeconds() >= 1);
    }

    @Test
    @DisplayName("Should fail open when Redis throws an exception")
    void testFailOpenOnRedisException() {
        org.springframework.data.redis.core.StringRedisTemplate mockRedis = org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.mockito.Mockito.when(mockRedis.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));
        org.springframework.test.util.ReflectionTestUtils.setField(rateLimitService, "redisTemplate", mockRedis);

        RateLimitResult res = rateLimitService.checkRateLimit(projectId, 10);
        assertTrue(res.allowed());
        assertEquals(10, res.remaining());
    }
}

