package com.hookrelay.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UuidUtilsTest {

    @Test
    @DisplayName("Should generate valid UUIDv7 with version 7 bits")
    void testGenerateV7() {
        UUID uuid = UuidUtils.generateV7();
        assertNotNull(uuid);
        assertEquals(7, uuid.version(), "UUID version must be 7 (RFC 9562)");
        assertEquals(2, uuid.variant(), "UUID variant must be 2 (IETF Leach-Salz)");
    }

    @Test
    @DisplayName("UUIDv7 instances should be strictly monotonically ordered over time")
    void testMonotonicOrdering() throws InterruptedException {
        UUID u1 = UuidUtils.generateV7();
        Thread.sleep(2);
        UUID u2 = UuidUtils.generateV7();

        assertTrue(u1.compareTo(u2) < 0, "Earlier UUIDv7 must be strictly smaller than subsequent UUIDv7");
    }
}
