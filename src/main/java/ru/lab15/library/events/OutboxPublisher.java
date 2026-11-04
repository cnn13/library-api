package ru.lab15.library.events;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.lab15.library.domain.OutboxEvent;
import ru.lab15.library.repo.OutboxRepository;

/**
 * Публикация outbox → Kafka. Гарантия at-least-once: строка помечается отправленной только после
 * подтверждения брокера (acks=all). Если сервис упадёт между отправкой и коммитом, событие уйдёт
 * повторно — поэтому consumer идемпотентен (processed_events).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final int batchSize;
    private final Counter published;
    private final Counter failed;

    public OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka, MeterRegistry metrics,
                           @Value("${app.kafka.loans-topic}") String topic,
                           @Value("${app.outbox.batch-size:50}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.topic = topic;
        this.batchSize = batchSize;
        this.published = Counter.builder("outbox.events.published").description("events sent to Kafka").register(metrics);
        this.failed = Counter.builder("outbox.events.failed").description("failed send attempts").register(metrics);
        Gauge.builder("outbox.events.pending", outbox, OutboxRepository::countByPublishedAtIsNull)
                .description("events waiting for publication").register(metrics);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:500ms}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent e : outbox.lockUnpublished(batchSize)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, e.getAggregateId(), e.getPayload());
            record.headers().add("eventId", e.getId().toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventType", e.getEventType().getBytes(StandardCharsets.UTF_8));
            try {
                kafka.send(record).get(10, TimeUnit.SECONDS);
            } catch (Exception ex) {
                // брокер недоступен: оставляем событие (и все следующие — ради порядка) до следующего цикла
                failed.increment();
                log.warn("outbox: send failed for event {} ({}): {}", e.getId(), e.getEventType(), ex.getMessage());
                return;
            }
            e.markPublished(OffsetDateTime.now());
            published.increment();
            log.info("outbox: published {} {} key={}", e.getEventType(), e.getId(), e.getAggregateId());
        }
    }
}
