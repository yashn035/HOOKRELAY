package com.hookrelay.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SuppressWarnings("resource")
public abstract class AbstractIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    protected static PostgreSQLContainer<?> postgres;
    protected static GenericContainer<?> redis;

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void configureSharedDynamicProperties(DynamicPropertyRegistry registry) {
        if (isDockerAvailable()) {
            try {
                if (postgres == null) {
                    postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                            .withDatabaseName("hookrelay_tc")
                            .withUsername("test_user")
                            .withPassword("test_pass");
                    postgres.start();
                    log.info("Testcontainers PostgreSQL started: {}", postgres.getJdbcUrl());
                }
                if (redis == null) {
                    redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                            .withExposedPorts(6379);
                    redis.start();
                    log.info("Testcontainers Redis started on port: {}", redis.getFirstMappedPort());
                }

                registry.add("spring.datasource.url", postgres::getJdbcUrl);
                registry.add("spring.datasource.username", postgres::getUsername);
                registry.add("spring.datasource.password", postgres::getPassword);
                registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
                registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
                registry.add("spring.flyway.enabled", () -> "true");
                registry.add("spring.data.redis.url", () -> "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
                return;
            } catch (Throwable t) {
                log.warn("Failed to initialize Testcontainers: {}. Falling back to in-memory H2.", t.getMessage());
            }
        }
        log.info("Docker is not available. Using in-memory H2 fallback for tests.");
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:hookrelay_tc;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }
}

