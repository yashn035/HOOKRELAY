package com.hookrelay.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class IdempotencyService {

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    // Local fallback for in-memory / testing
    private final Map<String, String> localCache = new ConcurrentHashMap<>();

    private static final Duration TTL = Duration.ofHours(24);

    public Optional<String> getExistingEventId(UUID projectId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }

        String key = "idemp:" + projectId + ":" + idempotencyKey;
        if (redisTemplate != null) {
            try {
                String val = redisTemplate.opsForValue().get(key);
                if (val != null) {
                    return Optional.of(val);
                }
            } catch (Exception e) {
                log.warn("Redis idempotency read failed: {}", e.getMessage());
            }
        }

        return Optional.ofNullable(localCache.get(key));
    }

    public void recordIdempotency(UUID projectId, String idempotencyKey, UUID eventId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }

        String key = "idemp:" + projectId + ":" + idempotencyKey;
        String val = eventId.toString();

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, val, TTL);
                return;
            } catch (Exception e) {
                log.warn("Redis idempotency write failed: {}", e.getMessage());
            }
        }

        localCache.put(key, val);
    }
}
