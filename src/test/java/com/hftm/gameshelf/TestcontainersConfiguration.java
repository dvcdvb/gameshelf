package com.hftm.gameshelf;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Startet für die Tests ein ECHTES PostgreSQL in Docker (T8) – dieselbe Version wie in docker-compose.yml.
 * @ServiceConnection sorgt dafür, dass Spring Boot die Datasource automatisch auf diesen Container zeigt;
 * die Werte aus application.yml (localhost:5432) werden in Tests also nicht verwendet.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
