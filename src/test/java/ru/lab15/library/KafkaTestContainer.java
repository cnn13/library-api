package ru.lab15.library;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/** Настоящий Kafka-брокер (KRaft, без ZooKeeper); {@code @ServiceConnection} подставляет bootstrap-servers. */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestContainer {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:3.9.1");
    }
}
