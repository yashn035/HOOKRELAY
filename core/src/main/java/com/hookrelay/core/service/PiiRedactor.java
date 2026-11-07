package com.hookrelay.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class PiiRedactor {

    public static final String REDACTED_EMAIL = "[REDACTED_EMAIL]";
    public static final String REDACTED_PHONE = "[REDACTED_PHONE]";
    public static final String REDACTED_SSN = "[REDACTED_SSN]";
    public static final String REDACTED_CREDIT_CARD = "[REDACTED_CREDIT_CARD]";

    // Regex for 13-19 digit credit cards (formatted or continuous digits)
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d{4}[- ]){3}\\d{4}\\b|\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12}|(?:2131|1800|35\\d{3})\\d{11}|\\d{15,16})\\b"
    );

    // Regex for US SSN: 000-00-0000 or 000 00 0000
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}[- ]\\d{2}[- ]\\d{4}\\b"
    );

    // Regex for Email Addresses
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    // Regex for Phone Numbers (North American and international formats)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?\\d{1,3}[-.\s]?)?\\(?\\d{3}\\)?[-.\s]?\\d{3}[-.\s]?\\d{4}\\b"
    );

    private final ObjectMapper objectMapper;

    public PiiRedactor() {
        this.objectMapper = new ObjectMapper();
    }

    public PiiRedactor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Redacts PII (email, phone, SSN, credit cards) from JSON structures or raw strings.
     *
     * @param input Raw string or JSON payload
     * @return PII-redacted text or JSON
     */
    public String redact(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        try {
            JsonNode root = objectMapper.readTree(input);
            JsonNode redactedNode = redactJsonNode(root);
            return objectMapper.writeValueAsString(redactedNode);
        } catch (Exception e) {
            // Not valid JSON, apply regex replacement to plain text
            return redactText(input);
        }
    }

    /**
     * Recursively traverses and redacts text values in JSON tree.
     */
    public JsonNode redactJsonNode(JsonNode node) {
        if (node == null) {
            return null;
        }

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode child = entry.getValue();
                if (child.isTextual()) {
                    entry.setValue(new TextNode(redactText(child.asText())));
                } else if (child.isObject() || child.isArray()) {
                    entry.setValue(redactJsonNode(child));
                }
            }
            return objectNode;
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode child = arrayNode.get(i);
                if (child.isTextual()) {
                    arrayNode.set(i, new TextNode(redactText(child.asText())));
                } else if (child.isObject() || child.isArray()) {
                    arrayNode.set(i, redactJsonNode(child));
                }
            }
            return arrayNode;
        } else if (node.isTextual()) {
            return new TextNode(redactText(node.asText()));
        }

        return node;
    }

    /**
     * Replaces credit cards, SSNs, emails, and phone numbers in a string.
     */
    public String redactText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // 1. Credit Card first (to avoid phone/SSN sub-matching on credit card digits)
        String result = CREDIT_CARD_PATTERN.matcher(text).replaceAll(REDACTED_CREDIT_CARD);

        // 2. SSN next
        result = SSN_PATTERN.matcher(result).replaceAll(REDACTED_SSN);

        // 3. Email next
        result = EMAIL_PATTERN.matcher(result).replaceAll(REDACTED_EMAIL);

        // 4. Phone numbers
        result = PHONE_PATTERN.matcher(result).replaceAll(REDACTED_PHONE);

        return result;
    }
}
