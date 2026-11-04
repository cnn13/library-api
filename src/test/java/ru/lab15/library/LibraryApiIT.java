package ru.lab15.library;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Сквозные тесты: настоящий HTTP → контроллеры → сервис → JPA → PostgreSQL (Testcontainers).
 * Проверяют то, что не видно в unit-тестах на моках: миграции Flyway, SQL-ограничения,
 * транзакции, сериализацию ответов и формат ошибок целиком.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresContainer.class)
class LibraryApiIT {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    // ---------- вспомогательные ----------

    private ResponseEntity<Map<String, Object>> call(HttpMethod method, String url, String json) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url, method, json == null ? null : new HttpEntity<>(json, h), MAP);
    }

    private long createAuthor(String name) {
        var r = call(HttpMethod.POST, "/api/v1/authors", "{\"name\":\"" + name + "\",\"country\":\"Россия\",\"birthYear\":1860}");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) r.getBody().get("id")).longValue();
    }

    private static String isbn13() {
        // 13 цифр, уникальные для каждого теста
        return "978" + String.format("%010d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 10_000_000_000L);
    }

    private ResponseEntity<Map<String, Object>> createBook(long authorId, String title, String isbn) {
        return call(HttpMethod.POST, "/api/v1/books",
                "{\"title\":\"" + title + "\",\"authorId\":" + authorId + ",\"year\":1900,\"isbn\":\"" + isbn + "\",\"pages\":100}");
    }

    // ---------- тесты ----------

    @Test
    void flywayAppliedMigrationsAndSeedData() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versions).startsWith("1", "2");

        var authors = rest.exchange("/api/v1/authors", HttpMethod.GET, null, new ParameterizedTypeReference<List<Map<String, Object>>>() {
        });
        assertThat(authors.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authors.getBody()).extracting(a -> a.get("name")).contains("Лев Толстой", "Фёдор Достоевский");
    }

    @Test
    void fullBookLifecycle() {
        long authorId = createAuthor("Антон Чехов");
        String isbn = isbn13();

        var created = createBook(authorId, "Вишнёвый сад", isbn);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).isNotNull();
        long bookId = ((Number) created.getBody().get("id")).longValue();

        var got = call(HttpMethod.GET, "/api/v1/books/" + bookId, null);
        assertThat(got.getBody()).containsEntry("isbn", isbn).containsEntry("title", "Вишнёвый сад");

        var replaced = call(HttpMethod.PUT, "/api/v1/books/" + bookId,
                "{\"title\":\"Вишнёвый сад (комедия)\",\"authorId\":" + authorId + ",\"year\":1904,\"pages\":112}");
        assertThat(replaced.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replaced.getBody()).containsEntry("year", 1904).doesNotContainKey("isbn"); // PUT без isbn очищает поле

        var patched = call(HttpMethod.PATCH, "/api/v1/books/" + bookId, "{\"pages\":120}");
        assertThat(patched.getBody()).containsEntry("pages", 120).containsEntry("title", "Вишнёвый сад (комедия)");

        assertThat(call(HttpMethod.DELETE, "/api/v1/books/" + bookId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.GET, "/api/v1/books/" + bookId, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.DELETE, "/api/v1/authors/" + authorId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void duplicateIsbnIsConflict() {
        long authorId = createAuthor("Иван Бунин");
        String isbn = isbn13();
        assertThat(createBook(authorId, "Тёмные аллеи", isbn).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        var dup = createBook(authorId, "Копия", isbn);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getHeaders().getContentType()).hasToString("application/problem+json");
        assertThat((String) dup.getBody().get("detail")).contains(isbn);
    }

    @Test
    void cannotDeleteAuthorWithBooks() {
        long authorId = createAuthor("Николай Гоголь");
        createBook(authorId, "Мёртвые души", isbn13());

        var r = call(HttpMethod.DELETE, "/api/v1/authors/" + authorId, null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) r.getBody().get("detail")).contains("has 1 book");
    }

    @Test
    @SuppressWarnings("unchecked")
    void unknownAuthorIsValidationError() {
        var r = createBook(999_999, "Без автора", isbn13());
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((Map<String, Object>) r.getBody().get("errors")).containsKey("authorId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void filterByAuthorAndPagination() {
        long authorId = createAuthor("Александр Пушкин");
        createBook(authorId, "Евгений Онегин", isbn13());
        createBook(authorId, "Капитанская дочка", isbn13());

        var page = call(HttpMethod.GET, "/api/v1/books?authorId=" + authorId + "&size=1&page=0", null).getBody();
        assertThat(page).containsEntry("totalItems", 2).containsEntry("totalPages", 2).containsEntry("size", 1);
        var items = (List<Map<String, Object>>) page.get("items");
        assertThat(items).hasSize(1);
        assertThat((Map<String, Object>) items.get(0).get("author")).containsEntry("name", "Александр Пушкин");
    }

    @Test
    void dbCheckConstraintBacksUpValidation() {
        // ограничения продублированы в БД: даже в обход API база не примет некорректные данные
        Integer authorId = jdbc.queryForObject("insert into authors(name) values ('Тест') returning id", Integer.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        jdbc.update("insert into books(title, author_id, published_year, pages) values ('X', ?, 1000, 1)", authorId))
                .hasMessageContaining("books_published_year_check");
    }
}
