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
import java.net.URI;

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

        String rawUrl = (renderDatabaseUrl != null && !renderDatabaseUrl.isBlank())
                ? renderDatabaseUrl.trim()
                : (defaultJdbcUrl != null ? defaultJdbcUrl.trim() : "");

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
                    String uriString = rawUrl.replace("postgresql://", "postgres://");
                    URI dbUri = URI.create(uriString);
                    String userInfo = dbUri.getUserInfo();
                    if (userInfo != null && userInfo.contains(":")) {
                        String[] parts = userInfo.split(":", 2);
                        username = parts[0];
                        password = parts[1];
                    }
                    int port = dbUri.getPort() == -1 ? 5432 : dbUri.getPort();
                    String dbName = dbUri.getPath();
                    if (dbName != null && dbName.startsWith("/")) {
                        dbName = dbName.substring(1);
                    }
                    jdbcUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + port + "/" + dbName;
                    if (dbUri.getQuery() != null) {
                        jdbcUrl += "?" + dbUri.getQuery();
                    }
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
