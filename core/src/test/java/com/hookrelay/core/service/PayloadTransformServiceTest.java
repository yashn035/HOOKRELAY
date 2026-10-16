package com.hookrelay.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadTransformServiceTest {

    private PayloadTransformService transformService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        transformService = new PayloadTransformService(objectMapper);
    }

    @Test
    @DisplayName("Case 1: Standard JSONPath extraction maps nested fields to new top-level keys")
    void testStandardJsonPathMapping() throws Exception {
        String inputJson = """
                {
                    "event": "order.completed",
                    "data": {
                        "orderId": "ord_9981",
                        "customer": {
                            "name": "Jane Doe",
                            "email": "jane@example.com"
                        }
                    }
                }
                """;

        String rules = """
                {
                    "order_id": "$.data.orderId",
                    "customer_name": "$.data.customer.name",
                    "customer_email": "$.data.customer.email"
                }
                """;

        String outputJson = transformService.transform(inputJson, rules);
        JsonNode node = objectMapper.readTree(outputJson);

        assertThat(node.get("order_id").asText()).isEqualTo("ord_9981");
        assertThat(node.get("customer_name").asText()).isEqualTo("Jane Doe");
        assertThat(node.get("customer_email").asText()).isEqualTo("jane@example.com");
    }

    @Test
    @DisplayName("Case 2: Array indexing and deep navigation extraction")
    void testArrayExtraction() throws Exception {
        String inputJson = """
                {
                    "items": [
                        {"sku": "SKU-APPLE", "qty": 2, "price": 1.50},
                        {"sku": "SKU-ORANGE", "qty": 5, "price": 0.80}
                    ]
                }
                """;

        String rules = """
                {
                    "first_item_sku": "$.items[0].sku",
                    "second_item_qty": "$.items[1].qty"
                }
                """;

        String outputJson = transformService.transform(inputJson, rules);
        JsonNode node = objectMapper.readTree(outputJson);

        assertThat(node.get("first_item_sku").asText()).isEqualTo("SKU-APPLE");
        assertThat(node.get("second_item_qty").asInt()).isEqualTo(5);
    }

    @Test
    @DisplayName("Case 3: Decimal formatting pipe filter e.g. number:'0.00'")
    void testNumberFormattingPipe() throws Exception {
        String inputJson = """
                {
                    "data": {
                        "amount": 149.5,
                        "raw_balance": "2500.1234"
                    }
                }
                """;

        String rules = """
                {
                    "amount_fmt": "$.data.amount | number:'0.00'",
                    "balance_fmt": "$.data.raw_balance | number:'#,##0.00'"
                }
                """;

        String outputJson = transformService.transform(inputJson, rules);
        JsonNode node = objectMapper.readTree(outputJson);

        assertThat(node.get("amount_fmt").asText()).isEqualTo("149.50");
        assertThat(node.get("balance_fmt").asText()).isEqualTo("2,500.12");
    }

    @Test
    @DisplayName("Case 4: String transformation pipes: upper, lower, and trim")
    void testStringPipeTransformations() throws Exception {
        String inputJson = """
                {
                    "user": {
                        "status": "pending_verification",
                        "tier": "enterprise",
                        "raw_code": "   ABC-123   "
                    }
                }
                """;

        String rules = """
                {
                    "status_upper": "$.user.status | upper",
                    "tier_lower": "$.user.tier | lower",
                    "clean_code": "$.user.raw_code | trim"
                }
                """;

        String outputJson = transformService.transform(inputJson, rules);
        JsonNode node = objectMapper.readTree(outputJson);

        assertThat(node.get("status_upper").asText()).isEqualTo("PENDING_VERIFICATION");
        assertThat(node.get("tier_lower").asText()).isEqualTo("enterprise");
        assertThat(node.get("clean_code").asText()).isEqualTo("ABC-123");
    }

    @Test
    @DisplayName("Case 5: Validation rejects malformed JSONPath syntax and invalid rules")
    void testValidationRejectsMalformedRules() {
        // Not a JSON object
        assertThatThrownBy(() -> transformService.validateRules("not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid JSON object");

        // Invalid JSONPath syntax
        assertThatThrownBy(() -> transformService.validateRules("{\"field\": \"invalid.not.starting.with.dollar\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with '$'");

        // Malformed brackets
        assertThatThrownBy(() -> transformService.validateRules("{\"field\": \"$.items[unclosed\"}"))
                .isInstanceOf(IllegalArgumentException.class);

        // Unsupported pipe
        assertThatThrownBy(() -> transformService.validateRules("{\"field\": \"$.name | unsupported_pipe\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported filter pipe");
    }

    @Test
    @DisplayName("Case 6: Passthrough when rules are null or empty")
    void testPassthroughWhenRulesEmpty() {
        String raw = "{\"hello\":\"world\"}";
        assertThat(transformService.transform(raw, null)).isEqualTo(raw);
        assertThat(transformService.transform(raw, "")).isEqualTo(raw);
        assertThat(transformService.transform(raw, "   ")).isEqualTo(raw);
    }

    @Test
    @DisplayName("Case 7: testTransform method returns valid result object or error message safely")
    void testTestTransformMethod() {
        String raw = "{\"id\":\"123\"}";
        var okResult = transformService.testTransform(raw, "{\"out_id\": \"$.id\"}");
        assertThat(okResult.valid()).isTrue();
        assertThat(okResult.transformedPayload()).contains("\"out_id\":\"123\"");
        assertThat(okResult.errorMessage()).isNull();

        var errResult = transformService.testTransform(raw, "{\"out_id\": \"malformed\"}");
        assertThat(errResult.valid()).isFalse();
        assertThat(errResult.transformedPayload()).isNull();
        assertThat(errResult.errorMessage()).isNotBlank();
    }
}
