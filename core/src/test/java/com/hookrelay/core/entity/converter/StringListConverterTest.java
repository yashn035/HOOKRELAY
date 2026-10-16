package com.hookrelay.core.entity.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StringListConverterTest {

    private StringListConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StringListConverter();
    }

    @Test
    @DisplayName("Should convert list of strings to comma-separated database string")
    void testConvertToDatabaseColumn() {
        assertEquals("", converter.convertToDatabaseColumn(null));
        assertEquals("", converter.convertToDatabaseColumn(List.of()));
        assertEquals("order.created,payment.succeeded", converter.convertToDatabaseColumn(List.of("order.created", "payment.succeeded")));
    }

    @Test
    @DisplayName("Should parse comma-separated database column to trimmed string list")
    void testConvertToEntityAttribute() {
        assertTrue(converter.convertToEntityAttribute(null).isEmpty());
        assertTrue(converter.convertToEntityAttribute("").isEmpty());
        assertTrue(converter.convertToEntityAttribute("   ").isEmpty());

        List<String> list = converter.convertToEntityAttribute("order.created, payment.succeeded, user.signup");
        assertEquals(3, list.size());
        assertEquals("order.created", list.get(0));
        assertEquals("payment.succeeded", list.get(1));
        assertEquals("user.signup", list.get(2));
    }
}
