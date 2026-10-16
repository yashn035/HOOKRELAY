package com.hookrelay.core.util;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

import java.util.UUID;

/**
 * High-performance UUIDv7 generator (RFC 9562).
 * Provides millisecond-precision unix timestamp ordering with 74 bits of entropy.
 * Ensures optimal B-Tree insertion locality in PostgreSQL indexes.
 */
public final class UuidUtils {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidUtils() {
    }

    /**
     * Generates a new RFC 9562 UUIDv7.
     *
     * @return time-ordered UUID
     */
    public static UUID generateV7() {
        return GENERATOR.generate();
    }
}
