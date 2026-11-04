package ru.lab15.library;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Настоящий PostgreSQL той же версии, что в проде, в Docker-контейнере на время тестов.
 * {@code @ServiceConnection} сам подставляет URL, логин и пароль контейнера в DataSource —
 * не нужно ни H2, ни ручных spring.datasource.* в тестах.
 * Spring кеширует контекст, поэтому контейнер один на все интеграционные тесты.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainer {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}
