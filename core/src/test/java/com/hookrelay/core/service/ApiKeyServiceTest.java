package com.hookrelay.core.service;

import com.hookrelay.core.entity.ApiKey;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.repository.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private Project testProject;

    @BeforeEach
    void setUp() {
        testProject = Project.builder()
                .name("Production")
                .rateLimitPerSecond(100)
                .build();
        testProject.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should generate secure API key with prefix and SHA-256 hash")
    void testCreateApiKey() {
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(i -> i.getArgument(0));

        ApiKeyService.GeneratedApiKey result = apiKeyService.createApiKey(testProject, "Backend Ingest");

        assertNotNull(result);
        assertNotNull(result.rawSecret());
        assertTrue(result.rawSecret().startsWith("hr_live_"));
        assertEquals("Backend Ingest", result.apiKey().getName());
        assertNotNull(result.apiKey().getKeyHash());
        assertEquals(64, result.apiKey().getKeyHash().length()); // 256-bit hex
    }

    @Test
    @DisplayName("Should validate valid API key against stored SHA-256 hash")
    void testValidateApiKey() {
        String rawKey = "hr_live_test_token_secret_123456";
        String hash = ApiKeyService.hashKey(rawKey);

        ApiKey apiKey = ApiKey.builder()
                .project(testProject)
                .name("Test Key")
                .keyPrefix("hr_live_")
                .keyHash(hash)
                .revoked(false)
                .build();

        when(apiKeyRepository.findByKeyHashAndRevokedFalse(hash)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(apiKey);

        Optional<ApiKey> validated = apiKeyService.validateApiKey(rawKey);

        assertTrue(validated.isPresent());
        assertEquals("Test Key", validated.get().getName());
        verify(apiKeyRepository).save(apiKey); // Verified lastUsedAt was touched
    }

    @Test
    @DisplayName("Should reject malformed or non-prefixed key string")
    void testRejectInvalidFormat() {
        Optional<ApiKey> res = apiKeyService.validateApiKey("invalid_format_key");
        assertFalse(res.isPresent());
        verify(apiKeyRepository, never()).findByKeyHashAndRevokedFalse(any());
    }
}
