package com.hookrelay.core.service;

import com.hookrelay.core.entity.ApiKey;
import com.hookrelay.core.entity.Project;
import com.hookrelay.core.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "hr_live_";

    public record GeneratedApiKey(ApiKey apiKey, String rawSecret) {}

    @Transactional
    public GeneratedApiKey createApiKey(Project project, String name) {
        // Generate high-entropy 256-bit token
        byte[] randomBytes = new byte[24];
        RANDOM.nextBytes(randomBytes);
        String tokenBody = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String fullKey = KEY_PREFIX + tokenBody;

        String prefix = fullKey.substring(0, Math.min(16, fullKey.length()));
        String keyHash = hashKey(fullKey);

        ApiKey apiKey = ApiKey.builder()
                .project(project)
                .name(name)
                .keyPrefix(prefix)
                .keyHash(keyHash)
                .revoked(false)
                .build();

        ApiKey saved = apiKeyRepository.save(apiKey);
        return new GeneratedApiKey(saved, fullKey);
    }

    public Optional<ApiKey> validateApiKey(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String hash = hashKey(rawKey);
        Optional<ApiKey> keyOpt = apiKeyRepository.findByKeyHashAndRevokedFalse(hash);
        keyOpt.ifPresent(key -> {
            key.setLastUsedAt(Instant.now());
            apiKeyRepository.save(key);
        });
        return keyOpt;
    }

    @Transactional
    public void revokeApiKey(UUID keyId, UUID projectId) {
        apiKeyRepository.findByIdAndProjectId(keyId, projectId).ifPresent(key -> {
            key.setRevoked(true);
            key.setRevokedAt(Instant.now());
            apiKeyRepository.save(key);
        });
    }

    public List<ApiKey> listProjectKeys(UUID projectId) {
        return apiKeyRepository.findByProjectId(projectId);
    }

    public static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
