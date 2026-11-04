package ru.lab15.library.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Clock;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableScheduling
public class KafkaConfig {

    /** 3 партиции: до трёх параллельных consumer'ов; порядок событий сохраняется внутри книги (ключ = bookId). */
    @Bean
    NewTopic loansTopic(@Value("${app.kafka.loans-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    /** Партиций столько же, сколько в основном топике: recoverer кладёт сообщение в ту же партицию DLT. */
    @Bean
    NewTopic loansDeadLetterTopic(@Value("${app.kafka.loans-topic}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(3).replicas(1).build();
    }

    /**
     * Ошибка обработки → 2 повтора с паузой 500 мс → сообщение уходит в <topic>.DLT, consumer идёт дальше
     * (явно задаём имя <topic>.DLT и ту же партицию; один «ядовитый» message не блокирует партицию). Битый JSON не повторяем — он не исправится.
     * Spring Boot сам подключает этот бин к фабрике listener-контейнеров.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template, (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", rec.partition())),
                new FixedBackOff(500L, 2));
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }

    /** Часы как бин: в тестах подменяются, чтобы проверить просрочку без ожидания 14 дней. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
