package com.hookrelay.core.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EndpointTest {

    @Test
    @DisplayName("Endpoint subscription logic handles wildcards, specific events, and active states")
    void testIsSubscribedTo() {
        Endpoint ep = Endpoint.builder()
                .name("Test Endpoint")
                .url("https://example.com/webhook")
                .secret("whsec_test")
                .eventTypesFilter(List.of("order.created", "payment.*"))
                .active(true)
                .circuitBroken(false)
                .build();

        assertTrue(ep.isSubscribedTo("order.created"));
        assertFalse(ep.isSubscribedTo("user.signup"));

        // Inactive or circuit broken endpoints must never receive events
        ep.setActive(false);
        assertFalse(ep.isSubscribedTo("order.created"));

        ep.setActive(true);
        ep.setCircuitBroken(true);
        assertFalse(ep.isSubscribedTo("order.created"));

        // Wildcard filter matches everything
        ep.setCircuitBroken(false);
        ep.setEventTypesFilter(List.of("*"));
        assertTrue(ep.isSubscribedTo("any.random.event"));

        // Empty filter defaults to wildcard
        ep.setEventTypesFilter(List.of());
        assertTrue(ep.isSubscribedTo("any.random.event"));
    }
}
