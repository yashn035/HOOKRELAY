package com.hookrelay.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@Profile("!test")
public class DatabaseConfig {

    @Value("${DATABASE_URL:}")
    private String renderDatabaseUrl;

    @Value("${spring.datasource.url:}")
    private String defaultJdbcUrl;

    @Value("${spring.datasource.username:postgres}")
    private String defaultUsername;

    @Value("${spring.datasource.password:postgres}")
    private String defaultPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        // Priority 1: If spring.datasource.url is explicitly provided (and not a placeholder/localhost), use it
        // Priority 2: Render's DATABASE_URL environment variable (from linked Postgres)
        // Priority 3: Fallback spring.datasource.url (e.g. localhost or in-memory)
        String rawUrl = "";
        if (isValidRemoteUrl(defaultJdbcUrl)) {
            log.info("Using explicit spring.datasource.url for database connection.");
            rawUrl = defaultJdbcUrl.trim();
        } else if (isValidRemoteUrl(renderDatabaseUrl)) {
            log.info("Using DATABASE_URL from Render environment for database connection.");
            rawUrl = renderDatabaseUrl.trim();
        } else if (defaultJdbcUrl != null && !defaultJdbcUrl.isBlank() && !defaultJdbcUrl.contains("<")) {
            rawUrl = defaultJdbcUrl.trim();
        } else if (renderDatabaseUrl != null && !renderDatabaseUrl.isBlank() && !renderDatabaseUrl.contains("<")) {
            rawUrl = renderDatabaseUrl.trim();
        }

        boolean isPostgresTarget = false;

        if (!rawUrl.isBlank()) {
            if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://") || rawUrl.startsWith("jdbc:postgresql:")) {
                if (!rawUrl.contains("localhost") && !rawUrl.contains("127.0.0.1")) {
                    isPostgresTarget = true;
                } else {
                    try (Socket s = new Socket("localhost", 5432)) {
                        isPostgresTarget = true;
                    } catch (Exception e) {
                        log.warn("PostgreSQL not reachable at localhost:5432. Falling back to embedded H2 database.");
                        isPostgresTarget = false;
                    }
                }
            }
        }

        if (isPostgresTarget) {
            try {
                log.info("Configuring PostgreSQL DataSource from connection string...");
                String jdbcUrl;
                String username = defaultUsername;
                String password = defaultPassword;

                if (rawUrl.startsWith("jdbc:")) {
                    jdbcUrl = rawUrl;
                } else {
                    // Safe parser for postgres:// or postgresql:// connection strings
                    String withoutScheme = rawUrl;
                    if (withoutScheme.startsWith("postgresql://")) {
                        withoutScheme = withoutScheme.substring("postgresql://".length());
                    } else if (withoutScheme.startsWith("postgres://")) {
                        withoutScheme = withoutScheme.substring("postgres://".length());
                    }

                    int atIndex = withoutScheme.lastIndexOf('@');
                    if (atIndex != -1) {
                        String creds = withoutScheme.substring(0, atIndex);
                        String hostAndPath = withoutScheme.substring(atIndex + 1);
                        int colon = creds.indexOf(':');
                        if (colon != -1) {
                            username = URLDecoder.decode(creds.substring(0, colon), StandardCharsets.UTF_8);
                            password = URLDecoder.decode(creds.substring(colon + 1), StandardCharsets.UTF_8);
                        } else {
                            username = URLDecoder.decode(creds, StandardCharsets.UTF_8);
                        }
                        jdbcUrl = "jdbc:postgresql://" + hostAndPath;
                    } else {
                        jdbcUrl = "jdbc:postgresql://" + withoutScheme;
                    }
                }

                // If explicit username/password are injected and not placeholder templates
                if (defaultUsername != null && !defaultUsername.isBlank() && !defaultUsername.equals("postgres") && !defaultUsername.contains("<")) {
                    username = defaultUsername;
                }
                if (defaultPassword != null && !defaultPassword.isBlank() && !defaultPassword.equals("postgres") && !defaultPassword.contains("<")) {
                    password = defaultPassword;
                }

                config.setJdbcUrl(jdbcUrl);
                config.setUsername(username);
                config.setPassword(password);
                config.setDriverClassName("org.postgresql.Driver");
                config.setMaximumPoolSize(60);
                config.setMinimumIdle(10);
                config.setConnectionTimeout(20000);
                config.setIdleTimeout(300000);
                config.setMaxLifetime(1200000);
            } catch (Exception ex) {
                log.error("Failed to parse PostgreSQL URL, falling back to embedded H2: {}", ex.getMessage());
                configureEmbeddedH2(config);
            }
        } else {
            log.info("No remote PostgreSQL configured or reachable. Initializing embedded in-memory database with PostgreSQL compatibility.");
            configureEmbeddedH2(config);
        }

        return new HikariDataSource(config);
    }

    private boolean isValidRemoteUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        // Reject unreplaced template placeholders like <HOST> or <DATABASE_NAME>
        if (trimmed.contains("<") || trimmed.contains(">")) {
            return false;
        }
        if (trimmed.contains("localhost") || trimmed.contains("127.0.0.1")) {
            return false;
        }
        return trimmed.startsWith("jdbc:postgresql:") || trimmed.startsWith("postgres://") || trimmed.startsWith("postgresql://");
    }

    private void configureEmbeddedH2(HikariConfig config) {
        config.setJdbcUrl("jdbc:h2:mem:hookrelay;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(10000);
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (Exception ex) {
                log.warn("Flyway migration skipped or bypassed ({}); continuing with Hibernate DDL schema.", ex.getMessage());
            }
        };
    }
}
