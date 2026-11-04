package ru.lab15.library.repo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import ru.lab15.library.PostgresContainer;

/**
 * Срез JPA на реальном PostgreSQL: JPQL-запрос search с необязательными параметрами.
 * Конструкция ":param is null" — частый источник ошибок «could not determine data type of parameter»
 * именно в PostgreSQL; на H2 такой тест прошёл бы и ничего бы не доказал.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainer.class)
class BookRepositoryIT {

    @Autowired
    BookRepository books;

    @Test
    void searchWithoutFiltersReturnsSeedBooks() {
        assertThat(books.search(null, null, PageRequest.of(0, 10)).getTotalElements()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void searchByAuthor() {
        var page = books.search(1L, null, PageRequest.of(0, 10));
        assertThat(page.getContent()).isNotEmpty().allMatch(b -> b.getAuthor().getId() == 1L);
    }

    @Test
    void searchByAuthorAndYear() {
        assertThat(books.search(1L, 1869, PageRequest.of(0, 10)).getContent())
                .extracting(b -> b.getTitle()).containsExactly("Война и мир");
        assertThat(books.search(1L, 2000, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void derivedQueries() {
        assertThat(books.existsByIsbn("9785170903352")).isTrue();
        assertThat(books.countByAuthorId(1L)).isEqualTo(2);
    }
}
