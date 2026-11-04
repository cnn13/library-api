package ru.lab15.library.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Выдача книги читателю. Активная — пока returnedAt == null. */
@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_id", nullable = false)
    private Reader reader;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    protected Loan() {
    }

    public Loan(Book book, Reader reader, OffsetDateTime issuedAt, OffsetDateTime dueAt) {
        this.book = book;
        this.reader = reader;
        this.issuedAt = issuedAt;
        this.dueAt = dueAt;
    }

    public boolean isActive() {
        return returnedAt == null;
    }

    /** Возврат: фиксирует время и сообщает, была ли просрочка. */
    public boolean markReturned(OffsetDateTime at) {
        this.returnedAt = at;
        return at.isAfter(dueAt);
    }

    public Long getId() { return id; }
    public Book getBook() { return book; }
    public Reader getReader() { return reader; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public OffsetDateTime getDueAt() { return dueAt; }
    public OffsetDateTime getReturnedAt() { return returnedAt; }
}
