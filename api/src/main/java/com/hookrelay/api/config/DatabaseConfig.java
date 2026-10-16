package com.hookrelay.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
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

        // Check if Render DATABASE_URL is present (postgres://user:pass@host:port/dbname)
        if (renderDatabaseUrl != null && !renderDatabaseUrl.isBlank() && renderDatabaseUrl.startsWith("postgres")) {
            try {
                log.info("Detected Render DATABASE_URL. Parsing connection parameters...");
                // Standardize URI format
                String uriString = renderDatabaseUrl.replace("jdbc:postgresql://", "postgres://");
                URI dbUri = URI.create(uriString);

                String userInfo = dbUri.getUserInfo();
                String username = defaultUsername;
                String password = defaultPassword;

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

                String jdbcUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + port + "/" + dbName;
                if (dbUri.getQuery() != null) {
                    jdbcUrl += "?" + dbUri.getQuery();
                }

                log.info("Connecting to PostgreSQL at {}:{}", dbUri.getHost(), port);
                config.setJdbcUrl(jdbcUrl);
                config.setUsername(username);
                config.setPassword(password);
                config.setDriverClassName("org.postgresql.Driver");
            } catch (Exception ex) {
                log.error("Failed to parse DATABASE_URL, falling back to default JDBC URL: {}", ex.getMessage());
                config.setJdbcUrl(defaultJdbcUrl);
                config.setUsername(defaultUsername);
                config.setPassword(defaultPassword);
            }
        } else {
            config.setJdbcUrl(defaultJdbcUrl != null && !defaultJdbcUrl.isBlank() ? defaultJdbcUrl : "jdbc:postgresql://localhost:5432/hookrelay");
            config.setUsername(defaultUsername);
            config.setPassword(defaultPassword);
        }

        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(20000);
        config.setIdleTimeout(300000);
        config.setMaxLifetime(1200000);

        return new HikariDataSource(config);
    }
}
