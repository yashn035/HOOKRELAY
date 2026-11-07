package com.hookrelay.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PiiRedactorTest {

    private PiiRedactor piiRedactor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        piiRedactor = new PiiRedactor(objectMapper);
    }

    @Test
    @DisplayName("Should redact email addresses in plain text and JSON")
    void testEmailRedaction() throws Exception {
        String rawText = "Contact user at support@hookrelay.dev or alice.smith+promo@example.co.uk immediately.";
        String redactedText = piiRedactor.redactText(rawText);
        assertEquals("Contact user at [REDACTED_EMAIL] or [REDACTED_EMAIL] immediately.", redactedText);

        String json = "{\"user\": \"alice\", \"email\": \"alice@company.org\"}";
        String redactedJson = piiRedactor.redact(json);
        JsonNode node = objectMapper.readTree(redactedJson);
        assertEquals("[REDACTED_EMAIL]", node.get("email").asText());
        assertEquals("alice", node.get("user").asText());
    }

    @Test
    @DisplayName("Should redact phone numbers in multiple formats")
    void testPhoneRedaction() {
        String text1 = "Call us at +1-800-555-0199 or (212) 555-1234 or 415-555-9876.";
        String redacted = piiRedactor.redact(text1);
        assertFalse(redacted.contains("800-555-0199"));
        assertFalse(redacted.contains("212"));
        assertTrue(redacted.contains("[REDACTED_PHONE]"));
    }

    @Test
    @DisplayName("Should redact US Social Security Numbers (SSN)")
    void testSsnRedaction() {
        String text = "Customer SSN is 123-45-6789 and secondary is 987 65 4321.";
        String redacted = piiRedactor.redact(text);
        assertFalse(redacted.contains("123-45-6789"));
        assertFalse(redacted.contains("987 65 4321"));
        assertEquals("Customer SSN is [REDACTED_SSN] and secondary is [REDACTED_SSN].", redacted);
    }

    @Test
    @DisplayName("Should redact 15 and 16 digit credit cards with dashes, spaces, and continuous digits")
    void testCreditCardRedaction() {
        String formatted = "Card 4532-1234-5678-9012 charged $99.00.";
        assertEquals("Card [REDACTED_CREDIT_CARD] charged $99.00.", piiRedactor.redact(formatted));

        String continuous = "Card 4532123456789012 charged $99.00.";
        assertEquals("Card [REDACTED_CREDIT_CARD] charged $99.00.", piiRedactor.redact(continuous));
    }

    @Test
    @DisplayName("Should recursively traverse and redact deeply nested JSON objects and arrays")
    void testRecursiveJsonRedaction() throws Exception {
        String complexJson = """
                {
                  "event": "charge.failed",
                  "customer": {
                    "name": "Bob Vance",
                    "email": "bob@vancerefrigeration.com",
                    "phone": "312-555-0144",
                    "meta": {
                      "ssn": "000-12-3456"
                    }
                  },
                  "paymentMethod": {
                    "type": "card",
                    "cardNumber": "4111-2222-3333-4444"
                  },
                  "history": [
                    { "attempt": 1, "notify": "alerts@client.io" },
                    { "attempt": 2, "notify": "backup@client.io" }
                  ],
                  "unrelatedInt": 42,
                  "unrelatedBool": true
                }
                """;

        String resultJson = piiRedactor.redact(complexJson);
        JsonNode root = objectMapper.readTree(resultJson);

        // Verify leaf redactions
        assertEquals("[REDACTED_EMAIL]", root.at("/customer/email").asText());
        assertEquals("[REDACTED_PHONE]", root.at("/customer/phone").asText());
        assertEquals("[REDACTED_SSN]", root.at("/customer/meta/ssn").asText());
        assertEquals("[REDACTED_CREDIT_CARD]", root.at("/paymentMethod/cardNumber").asText());
        assertEquals("[REDACTED_EMAIL]", root.at("/history/0/notify").asText());
        assertEquals("[REDACTED_EMAIL]", root.at("/history/1/notify").asText());

        // Verify preserved non-PII
        assertEquals("Bob Vance", root.at("/customer/name").asText());
        assertEquals("card", root.at("/paymentMethod/type").asText());
        assertEquals(42, root.at("/unrelatedInt").asInt());
        assertTrue(root.at("/unrelatedBool").asBoolean());
    }

    @Test
    @DisplayName("Should handle null, blank, and non-JSON string inputs gracefully")
    void testEdgeCases() {
        assertNull(piiRedactor.redact(null));
        assertEquals("", piiRedactor.redact(""));
        assertEquals("   ", piiRedactor.redact("   "));

        String rawHtml = "<html><body>Error contacting user@example.com</body></html>";
        String redactedHtml = piiRedactor.redact(rawHtml);
        assertEquals("<html><body>Error contacting [REDACTED_EMAIL]</body></html>", redactedHtml);
    }
}
