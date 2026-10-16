package com.hookrelay.core.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PayloadTransformService {

    private final ObjectMapper objectMapper;
    private final Configuration jsonPathConfig;

    private static final Pattern NUMBER_PIPE_PATTERN = Pattern.compile("number:\\s*['\"]([^'\"]+)['\"]");

    public PayloadTransformService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Suppress PathNotFoundException to gracefully return null for missing paths
        this.jsonPathConfig = Configuration.defaultConfiguration()
                .addOptions(Option.SUPPRESS_EXCEPTIONS);
    }

    public record TransformResult(boolean valid, String transformedPayload, String errorMessage) {}

    /**
     * Validates that the provided rules JSON string is a valid JSON object
     * with valid JSONPath expressions and supported pipe transformations.
     * Throws IllegalArgumentException on validation failure.
     */
    public void validateRules(String rulesJson) {
        if (rulesJson == null || rulesJson.isBlank()) {
            return;
        }

        Map<String, Object> rules;
        try {
            rules = objectMapper.readValue(rulesJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Transform rules must be a valid JSON object: " + e.getMessage());
        }

        if (rules.isEmpty()) {
            throw new IllegalArgumentException("Transform rules cannot be an empty JSON object");
        }

        for (Map.Entry<String, Object> entry : rules.entrySet()) {
            String targetKey = entry.getKey();
            if (targetKey == null || targetKey.isBlank()) {
                throw new IllegalArgumentException("Transform rule target key cannot be blank");
            }

            Object exprObj = entry.getValue();
            if (!(exprObj instanceof String expr) || expr.isBlank()) {
                throw new IllegalArgumentException("Transform rule for '" + targetKey + "' must be a non-empty string expression");
            }

            String[] parts = expr.split("\\|", 2);
            String jsonPathStr = parts[0].trim();

            if (!jsonPathStr.startsWith("$")) {
                throw new IllegalArgumentException("Invalid JSONPath for '" + targetKey + "': expression must start with '$' (got: " + jsonPathStr + ")");
            }

            try {
                JsonPath.compile(jsonPathStr);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSONPath syntax for '" + targetKey + "': " + e.getMessage());
            }

            if (parts.length > 1) {
                String filter = parts[1].trim();
                validateFilter(targetKey, filter);
            }
        }
    }

    private void validateFilter(String targetKey, String filter) {
        if (filter.equalsIgnoreCase("upper")
                || filter.equalsIgnoreCase("lower")
                || filter.equalsIgnoreCase("trim")) {
            return;
        }
        if (filter.startsWith("number:")) {
            Matcher m = NUMBER_PIPE_PATTERN.matcher(filter);
            if (m.find()) {
                try {
                    new DecimalFormat(m.group(1));
                    return;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid number format pattern for '" + targetKey + "': " + m.group(1));
                }
            }
        }
        throw new IllegalArgumentException("Unsupported filter pipe for '" + targetKey + "': " + filter + ". Supported filters: upper, lower, trim, number:'<format>'");
    }

    /**
     * Transforms an input JSON payload according to endpoint transformation rules.
     * If rulesJson is null or blank, returns rawPayload unmodified.
     */
    public String transform(String rawPayload, String rulesJson) {
        if (rulesJson == null || rulesJson.isBlank()) {
            return rawPayload;
        }
        if (rawPayload == null || rawPayload.isBlank()) {
            return rawPayload;
        }

        Map<String, String> rules;
        try {
            rules = objectMapper.readValue(rulesJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse transform rules: " + e.getMessage(), e);
        }

        DocumentContext documentContext;
        try {
            documentContext = JsonPath.using(jsonPathConfig).parse(rawPayload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse payload as JSON for transformation: " + e.getMessage(), e);
        }

        Map<String, Object> transformed = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : rules.entrySet()) {
            String targetKey = entry.getKey();
            String expr = entry.getValue();

            String[] parts = expr.split("\\|", 2);
            String jsonPathStr = parts[0].trim();
            String filter = parts.length > 1 ? parts[1].trim() : null;

            Object val = null;
            try {
                val = documentContext.read(jsonPathStr);
            } catch (PathNotFoundException e) {
                val = null;
            } catch (Exception e) {
                log.warn("Error reading JSONPath '{}': {}", jsonPathStr, e.getMessage());
            }

            if (val != null && filter != null) {
                val = applyFilter(val, filter);
            }

            transformed.put(targetKey, val);
        }

        try {
            return objectMapper.writeValueAsString(transformed);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize transformed payload: " + e.getMessage(), e);
        }
    }

    private Object applyFilter(Object value, String filter) {
        if (value == null) {
            return null;
        }

        if (filter.equalsIgnoreCase("upper")) {
            return value.toString().toUpperCase();
        }
        if (filter.equalsIgnoreCase("lower")) {
            return value.toString().toLowerCase();
        }
        if (filter.equalsIgnoreCase("trim")) {
            return value.toString().trim();
        }
        if (filter.startsWith("number:")) {
            Matcher m = NUMBER_PIPE_PATTERN.matcher(filter);
            if (m.find()) {
                String pattern = m.group(1);
                try {
                    DecimalFormat df = new DecimalFormat(pattern);
                    double num;
                    if (value instanceof Number n) {
                        num = n.doubleValue();
                    } else {
                        num = Double.parseDouble(value.toString().trim());
                    }
                    return df.format(num);
                } catch (Exception e) {
                    log.warn("Failed to format number '{}' with pattern '{}': {}", value, pattern, e.getMessage());
                    return value;
                }
            }
        }

        return value;
    }

    /**
     * Tests a payload transformation against sample rules, returning a safe result.
     */
    public TransformResult testTransform(String rawPayload, String rulesJson) {
        try {
            validateRules(rulesJson);
            String result = transform(rawPayload, rulesJson);
            return new TransformResult(true, result, null);
        } catch (Exception e) {
            return new TransformResult(false, null, e.getMessage());
        }
    }
}
