package com.hookrelay.core.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyServiceTest {

    private IdempotencyService idempotencyService;
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService();
    }

    @Test
    @DisplayName("Should return empty when checking non-existent or blank key")
    void testBlankOrMissingKey() {
        assertTrue(idempotencyService.getExistingEventId(projectId, null).isEmpty());
        assertTrue(idempotencyService.getExistingEventId(projectId, "  ").isEmpty());
        assertTrue(idempotencyService.getExistingEventId(projectId, "non_existent").isEmpty());
    }

    @Test
    @DisplayName("Should record and retrieve idempotency key successfully in local cache")
    void testRecordAndRetrieve() {
        String key = "order_create_tx_9918";
        UUID eventId = UUID.randomUUID();

        idempotencyService.recordIdempotency(projectId, key, eventId);

        Optional<String> cached = idempotencyService.getExistingEventId(projectId, key);
        assertTrue(cached.isPresent());
        assertEquals(eventId.toString(), cached.get());
    }

    @Test
    @DisplayName("Should isolate keys across different projects")
    void testProjectIsolation() {
        String key = "shared_key_name";
        UUID event1 = UUID.randomUUID();
        UUID project2 = UUID.randomUUID();

        idempotencyService.recordIdempotency(projectId, key, event1);

        assertTrue(idempotencyService.getExistingEventId(projectId, key).isPresent());
        assertTrue(idempotencyService.getExistingEventId(project2, key).isEmpty());
    }
}
