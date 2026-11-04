package ru.lab15.library;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Общая инфраструктура сквозных тестов: PostgreSQL + Kafka + управляемые часы.
 * Один и тот же набор в @Import → Spring переиспользует контекст (и контейнеры) между классами тестов.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({PostgresContainer.class, KafkaTestContainer.class})
public class TestInfra {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }
}
