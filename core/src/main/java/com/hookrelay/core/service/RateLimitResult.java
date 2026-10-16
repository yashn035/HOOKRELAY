package com.hookrelay.core.service;

public record RateLimitResult(
        boolean allowed,
        int limit,
        int remaining,
        long retryAfterSeconds
) {}
