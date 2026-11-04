package ru.lab15.library.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.lab15.library.domain.Loan;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    boolean existsByBookIdAndReturnedAtIsNull(Long bookId);

    long countByReaderIdAndReturnedAtIsNull(Long readerId);

    /** Выдача вместе с книгой и читателем одним запросом (без N+1 при построении ответа). */
    @Query("select l from Loan l join fetch l.book join fetch l.reader where l.id = :id")
    Optional<Loan> findWithDetails(@Param("id") Long id);

    @Query("""
            select l from Loan l join fetch l.book join fetch l.reader
            where (:readerId is null or l.reader.id = :readerId)
              and (:active is null or (:active = true and l.returnedAt is null) or (:active = false and l.returnedAt is not null))
            order by l.id desc
            """)
    List<Loan> search(@Param("readerId") Long readerId, @Param("active") Boolean active);
}
