package ru.lab15.library.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.lab15.library.domain.Book;

public interface BookRepository extends JpaRepository<Book, Long> {

    /** Список книг с необязательными фильтрами по автору и году. */
    @Query("""
            select b from Book b
            where (:authorId is null or b.author.id = :authorId)
              and (:year is null or b.year = :year)
            """)
    Page<Book> search(@Param("authorId") Long authorId, @Param("year") Integer year, Pageable pageable);

    boolean existsByAuthorId(Long authorId);

    long countByAuthorId(Long authorId);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);
}
