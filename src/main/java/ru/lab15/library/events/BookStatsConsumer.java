package ru.lab15.library.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer событий выдачи: строит проекцию book_stats (популярность книг).
 * Идемпотентность: eventId записывается в processed_events в той же транзакции, что и обновление
 * статистики. Повторная доставка того же события — insert не проходит (ON CONFLICT), событие пропускается.
 * Ошибка разбора/обработки → повторы, затем Dead Letter Topic (см. KafkaConfig).
 */
@Component
public class BookStatsConsumer {

    private static final Logger log = LoggerFactory.getLogger(BookStatsConsumer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public BookStatsConsumer(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @KafkaListener(topics = "${app.kafka.loans-topic}", groupId = "library-book-stats")
    @Transactional
    public void onLoanEvent(ConsumerRecord<String, String> record) throws Exception {
        LoanEvent e = json.readValue(record.value(), LoanEvent.class);

        int fresh = jdbc.update("insert into processed_events(event_id) values (?) on conflict do nothing", e.eventId());
        if (fresh == 0) {
            log.info("consumer: duplicate event {} skipped (partition {} offset {})", e.eventId(), record.partition(), record.offset());
            return;
        }
        int issued = e.type() == LoanEvent.Type.BOOK_ISSUED ? 1 : 0;
        int returned = e.type() == LoanEvent.Type.BOOK_RETURNED ? 1 : 0;
        int overdue = Boolean.TRUE.equals(e.overdue()) ? 1 : 0;
        jdbc.update("""
                insert into book_stats(book_id, times_issued, times_returned, overdue_returns, last_event_at)
                values (?, ?, ?, ?, ?)
                on conflict (book_id) do update set
                    times_issued    = book_stats.times_issued + excluded.times_issued,
                    times_returned  = book_stats.times_returned + excluded.times_returned,
                    overdue_returns = book_stats.overdue_returns + excluded.overdue_returns,
                    last_event_at   = greatest(book_stats.last_event_at, excluded.last_event_at)
                """, e.bookId(), issued, returned, overdue, e.occurredAt());
        log.info("consumer: {} book={} applied (partition {} offset {})", e.type(), e.bookId(), record.partition(), record.offset());
    }
}
