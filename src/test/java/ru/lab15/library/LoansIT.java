package ru.lab15.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Выдача/возврат книг целиком: HTTP → транзакция (выдача + outbox) → publisher → Kafka → consumer → book_stats.
 * Каждый тест создаёт своего автора, книги и читателей, чтобы не зависеть от других тестов в общей БД.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestInfra.class)
class LoansIT {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MutableClock clock;

    @Autowired
    KafkaTemplate<String, String> kafka;

    @Autowired
    KafkaConnectionDetails kafkaConnection;

    @Value("${app.kafka.loans-topic}")
    String topic;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    // ---------- вспомогательные ----------

    private ResponseEntity<Map<String, Object>> post(String url, Object body) {
        return rest.exchange(url, HttpMethod.POST, body == null ? null : new HttpEntity<>(body), MAP);
    }

    private ResponseEntity<Map<String, Object>> get(String url) {
        return rest.exchange(url, HttpMethod.GET, null, MAP);
    }

    private long author() {
        var r = post("/api/v1/authors", Map.of("name", "Автор " + UUID.randomUUID()));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) r.getBody().get("id")).longValue();
    }

    private long book(long authorId) {
        var r = post("/api/v1/books", Map.of("title", "Книга " + UUID.randomUUID(), "authorId", authorId, "year", 2000));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) r.getBody().get("id")).longValue();
    }

    private long reader() {
        String email = "r-" + UUID.randomUUID() + "@example.com";
        var r = post("/api/v1/readers", Map.of("name", "Читатель", "email", email));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) r.getBody().get("id")).longValue();
    }

    private ResponseEntity<Map<String, Object>> issue(long bookId, long readerId) {
        return post("/api/v1/loans", Map.of("bookId", bookId, "readerId", readerId));
    }

    private ResponseEntity<Map<String, Object>> giveBack(long loanId) {
        return post("/api/v1/loans/" + loanId + "/return", null);
    }

    private Map<String, Object> stats(long bookId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select * from book_stats where book_id = ?", bookId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    // ---------- тесты ----------

    @Test
    void issueAndReturnWithOverduePublishesEventsAndBuildsStats() {
        long bookId = book(author());
        long readerId = reader();

        var issued = issue(bookId, readerId);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(issued.getBody()).containsEntry("status", "ACTIVE");
        long loanId = ((Number) issued.getBody().get("id")).longValue();

        // вторая выдача той же книги — конфликт
        assertThat(issue(bookId, reader()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // «прошло» 15 дней — выдача просрочена
        clock.advance(Duration.ofDays(15));
        var list = rest.exchange("/api/v1/loans?readerId=" + readerId + "&active=true", HttpMethod.GET, null, LIST);
        assertThat(list.getBody()).singleElement().satisfies(l -> assertThat(l).containsEntry("status", "OVERDUE"));

        var returned = giveBack(loanId);
        assertThat(returned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(returned.getBody()).containsEntry("status", "RETURNED_LATE");
        assertThat(giveBack(loanId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // outbox опубликован, consumer построил статистику (асинхронно — ждём, а не спим)
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(stats(bookId)).containsEntry("times_issued", 1).containsEntry("times_returned", 1)
                    .containsEntry("overdue_returns", 1);
        });
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ? and published_at is null",
                Long.class, String.valueOf(bookId))).isZero();

        var top = rest.exchange("/api/v1/stats/books/top?limit=50", HttpMethod.GET, null, LIST);
        assertThat(top.getBody()).anySatisfy(s -> assertThat(((Number) s.get("bookId")).longValue()).isEqualTo(bookId));
    }

    @Test
    void readerLimitAndValidation() {
        long authorId = author();
        long readerId = reader();
        for (int i = 0; i < 3; i++) {
            assertThat(issue(book(authorId), readerId).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        var fourth = issue(book(authorId), readerId);
        assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) fourth.getBody().get("detail")).contains("limit is 3");

        assertThat(get("/api/v1/readers/" + readerId).getBody()).containsEntry("activeLoans", 3);

        var unknownBook = issue(999_999, readerId);
        assertThat(unknownBook.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(post("/api/v1/loans", Map.of("bookId", 1)).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(post("/api/v1/loans/999999/return", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        String email = "dup-" + UUID.randomUUID() + "@example.com";
        assertThat(post("/api/v1/readers", Map.of("name", "X", "email", email.toUpperCase())).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(post("/api/v1/readers", Map.of("name", "X", "email", email)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/api/v1/readers", Map.of("name", "X", "email", "not-an-email")).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(get("/api/v1/stats/books/top?limit=0").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** 10 параллельных запросов на одну книгу: проверка в коде их не остановит, остановит частичный уникальный индекс. */
    @Test
    void concurrentIssueOfSameBookExactlyOneWins() throws Exception {
        long bookId = book(author());
        List<Long> readers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            readers.add(reader());
        }
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpStatus>> results = new ArrayList<>();
        for (long readerId : readers) {
            results.add(pool.submit(() -> {
                start.await();
                return HttpStatus.valueOf(issue(bookId, readerId).getStatusCode().value());
            }));
        }
        start.countDown();
        List<HttpStatus> statuses = new ArrayList<>();
        for (Future<HttpStatus> f : results) {
            statuses.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == HttpStatus.CREATED).hasSize(1);
        assertThat(statuses).filteredOn(s -> s != HttpStatus.CREATED).allMatch(s -> s == HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("select count(*) from loans where book_id = ? and returned_at is null", Long.class, bookId))
                .isEqualTo(1L);
    }

    /** At-least-once доставка: то же событие пришло дважды — статистика увеличилась один раз. */
    @Test
    void duplicateEventIsAppliedOnce() throws Exception {
        long bookId = book(author());
        String payload = """
                {"eventId":"%s","type":"BOOK_ISSUED","occurredAt":"2026-01-01T10:00:00Z","loanId":1,
                 "bookId":%d,"readerId":1,"dueAt":"2026-01-15T10:00:00Z"}
                """.formatted(UUID.randomUUID(), bookId);
        kafka.send(topic, String.valueOf(bookId), payload).get(10, TimeUnit.SECONDS);
        kafka.send(topic, String.valueOf(bookId), payload).get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(stats(bookId)).containsEntry("times_issued", 1));
        // ещё одно событие после дубля — убеждаемся, что дубль точно обработан (порядок внутри партиции), а не «не дошёл»
        kafka.send(topic, String.valueOf(bookId), payload.replaceFirst("\"eventId\":\"[^\"]+\"",
                "\"eventId\":\"" + UUID.randomUUID() + "\"")).get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(stats(bookId)).containsEntry("times_issued", 2));
    }

    /** «Ядовитое» сообщение не блокирует партицию: уходит в DLT, следующие события обрабатываются. */
    @Test
    void malformedEventGoesToDeadLetterTopic() throws Exception {
        String marker = "broken-" + UUID.randomUUID();
        kafka.send(topic, "dlt-test", "{not json " + marker).get(10, TimeUnit.SECONDS);

        Map<String, Object> props = new java.util.HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaConnection.getBootstrapServers()));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-reader-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic + ".DLT"));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> r : records) {
                    if (r.value().contains(marker)) {
                        return true;
                    }
                }
                return false;
            });
        }
        // consumer жив и обрабатывает дальше
        long bookId = book(author());
        assertThat(issue(bookId, reader()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(stats(bookId)).containsEntry("times_issued", 1));
    }
}
