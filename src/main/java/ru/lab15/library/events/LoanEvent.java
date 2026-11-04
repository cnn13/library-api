package ru.lab15.library.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Событие о выдаче/возврате книги (топик library.loans, ключ — bookId,
 * поэтому все события одной книги попадают в одну партицию и читаются по порядку).
 * eventId — для идемпотентной обработки на стороне consumer.
 */
public record LoanEvent(
        UUID eventId,
        Type type,
        OffsetDateTime occurredAt,
        long loanId,
        long bookId,
        long readerId,
        OffsetDateTime dueAt,
        OffsetDateTime returnedAt,
        Boolean overdue) {

    public enum Type { BOOK_ISSUED, BOOK_RETURNED }
}
