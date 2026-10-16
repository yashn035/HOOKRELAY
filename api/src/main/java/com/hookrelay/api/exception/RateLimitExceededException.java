package com.hookrelay.api.exception;

import com.hookrelay.core.service.RateLimitResult;
import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {

    private final RateLimitResult result;

    public RateLimitExceededException(RateLimitResult result) {
        super("Rate limit exceeded for project. Retry after " + result.retryAfterSeconds() + " seconds.");
        this.result = result;
    }
}
