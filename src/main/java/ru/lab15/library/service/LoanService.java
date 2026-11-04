package ru.lab15.library.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lab15.library.api.ApiException;
import ru.lab15.library.api.LoanDto.LoanResponse;
import ru.lab15.library.api.LoanDto.ReaderResponse;
import ru.lab15.library.domain.Book;
import ru.lab15.library.domain.Loan;
import ru.lab15.library.domain.OutboxEvent;
import ru.lab15.library.domain.Reader;
import ru.lab15.library.events.LoanEvent;
import ru.lab15.library.repo.BookRepository;
import ru.lab15.library.repo.LoanRepository;
import ru.lab15.library.repo.OutboxRepository;
import ru.lab15.library.repo.ReaderRepository;

/**
 * Выдача и возврат книг. Каждое изменение и событие о нём пишутся в одной транзакции
 * (transactional outbox): либо в БД есть и выдача, и событие, либо ни того ни другого.
 */
@Service
@Transactional
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final LoanRepository loans;
    private final BookRepository books;
    private final ReaderRepository readers;
    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final Clock clock;
    private final Duration loanPeriod;
    private final int maxActiveLoans;

    public LoanService(LoanRepository loans, BookRepository books, ReaderRepository readers, OutboxRepository outbox,
                       ObjectMapper json, Clock clock,
                       @Value("${app.loans.period:P14D}") Duration loanPeriod,
                       @Value("${app.loans.max-active-per-reader:3}") int maxActiveLoans) {
        this.loans = loans;
        this.books = books;
        this.readers = readers;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
        this.loanPeriod = loanPeriod;
        this.maxActiveLoans = maxActiveLoans;
    }

    // ---------- читатели ----------

    public ReaderResponse createReader(String name, String email) {
        String normalized = email.trim().toLowerCase();
        if (readers.existsByEmailIgnoreCase(normalized)) {
            throw ApiException.conflict("Reader with email " + normalized + " already exists");
        }
        Reader r = readers.save(new Reader(name.trim(), normalized));
        log.info("reader created id={}", r.getId());
        return ReaderResponse.of(r, 0);
    }

    @Transactional(readOnly = true)
    public ReaderResponse getReader(Long id) {
        Reader r = readers.findById(id).orElseThrow(() -> ApiException.notFound("Reader", id));
        return ReaderResponse.of(r, loans.countByReaderIdAndReturnedAtIsNull(id));
    }

    // ---------- выдача ----------

    public LoanResponse issue(Long bookId, Long readerId) {
        Book book = books.findById(bookId)
                .orElseThrow(() -> ApiException.invalidField("bookId", "book with id=" + bookId + " does not exist"));
        Reader reader = readers.findById(readerId)
                .orElseThrow(() -> ApiException.invalidField("readerId", "reader with id=" + readerId + " does not exist"));
        if (loans.existsByBookIdAndReturnedAtIsNull(bookId)) {
            throw ApiException.conflict("Book id=" + bookId + " is already on loan");
        }
        long active = loans.countByReaderIdAndReturnedAtIsNull(readerId);
        if (active >= maxActiveLoans) {
            throw ApiException.conflict("Reader id=" + readerId + " already has " + active + " active loan(s), limit is " + maxActiveLoans);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        Loan loan;
        try {
            // flush сразу: если параллельный запрос успел выдать ту же книгу, частичный уникальный индекс
            // ux_loans_active_book отклонит вставку здесь, а не при коммите
            loan = loans.saveAndFlush(new Loan(book, reader, now, now.plus(loanPeriod)));
        } catch (DataIntegrityViolationException race) {
            throw ApiException.conflict("Book id=" + bookId + " is already on loan");
        }
        writeEvent(new LoanEvent(UUID.randomUUID(), LoanEvent.Type.BOOK_ISSUED, now, loan.getId(), bookId, readerId,
                loan.getDueAt(), null, null));
        log.info("book issued loanId={} bookId={} readerId={} dueAt={}", loan.getId(), bookId, readerId, loan.getDueAt());
        return LoanResponse.of(loan, now);
    }

    public LoanResponse returnBook(Long loanId) {
        Loan loan = loans.findWithDetails(loanId).orElseThrow(() -> ApiException.notFound("Loan", loanId));
        if (!loan.isActive()) {
            throw ApiException.conflict("Loan id=" + loanId + " is already returned at " + loan.getReturnedAt());
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean overdue = loan.markReturned(now);
        writeEvent(new LoanEvent(UUID.randomUUID(), LoanEvent.Type.BOOK_RETURNED, now, loan.getId(), loan.getBook().getId(),
                loan.getReader().getId(), loan.getDueAt(), now, overdue));
        log.info("book returned loanId={} overdue={}", loanId, overdue);
        return LoanResponse.of(loan, now);
    }

    @Transactional(readOnly = true)
    public List<LoanResponse> list(Long readerId, Boolean active) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return loans.search(readerId, active).stream().map(l -> LoanResponse.of(l, now)).toList();
    }

    private void writeEvent(LoanEvent event) {
        try {
            outbox.save(new OutboxEvent(event.eventId(), "book", String.valueOf(event.bookId()), event.type().name(),
                    json.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize event " + event, e);
        }
    }
}
