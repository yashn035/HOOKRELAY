package com.hookrelay.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RateLimitService {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // Fallback in-memory rate limiter when Redis is absent (e.g. offline tests)
    private final Map<String, WindowCounter> fallbackCounters = new ConcurrentHashMap<>();

    private record WindowCounter(long windowSecond, AtomicInteger count) {}

    /**
     * Checks if tenant has token available under their allowed rate per second.
     *
     * @param projectId Tenant project UUID
     * @param limitPerSecond Allowed rate limit
     * @return RateLimitResult indicating whether request is allowed and retry headers
     */
    public RateLimitResult checkRateLimit(UUID projectId, int limitPerSecond) {
        long currentSecond = Instant.now().getEpochSecond();
        String redisKey = "rl:" + projectId + ":" + currentSecond;

        if (redisTemplate != null) {
            try {
                Long currentCount = redisTemplate.opsForValue().increment(redisKey);
                if (currentCount != null && currentCount == 1) {
                    redisTemplate.expire(redisKey, Duration.ofSeconds(2));
                }

                if (currentCount != null && currentCount > limitPerSecond) {
                    return new RateLimitResult(false, limitPerSecond, 0, 1);
                }

                int remaining = (int) Math.max(0, limitPerSecond - (currentCount != null ? currentCount : 0));
                return new RateLimitResult(true, limitPerSecond, remaining, 0);
            } catch (Exception e) {
                log.warn("Redis rate limiter unavailable, failing open: {}", e.getMessage());
                return new RateLimitResult(true, limitPerSecond, limitPerSecond, 0);
            }
        }

        // Local window fallback
        String fallbackKey = projectId.toString();
        WindowCounter counter = fallbackCounters.compute(fallbackKey, (k, existing) -> {
            if (existing == null || existing.windowSecond() != currentSecond) {
                return new WindowCounter(currentSecond, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        int current = counter.count().get();
        if (current > limitPerSecond) {
            return new RateLimitResult(false, limitPerSecond, 0, 1);
        }

        return new RateLimitResult(true, limitPerSecond, limitPerSecond - current, 0);
    }
}
