package ru.lab15.library.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import ru.lab15.library.domain.Loan;
import ru.lab15.library.domain.Reader;

/** DTO читателей, выдач и статистики. */
public final class LoanDto {

    private LoanDto() {
    }

    @Schema(name = "ReaderRequest")
    public record ReaderRequest(
            @NotBlank @Size(max = 100) @Schema(example = "Иван Петров") String name,
            @NotBlank @Email @Size(max = 254) @Schema(example = "ivan@example.com") String email) {
    }

    @Schema(name = "Reader")
    public record ReaderResponse(Long id, String name, String email, long activeLoans, OffsetDateTime createdAt) {
        public static ReaderResponse of(Reader r, long activeLoans) {
            return new ReaderResponse(r.getId(), r.getName(), r.getEmail(), activeLoans, r.getCreatedAt());
        }
    }

    @Schema(name = "LoanRequest")
    public record LoanRequest(
            @NotNull @Positive @Schema(example = "1") Long bookId,
            @NotNull @Positive @Schema(example = "1") Long readerId) {
    }

    public enum Status { ACTIVE, OVERDUE, RETURNED, RETURNED_LATE }

    public record BookShort(Long id, String title) {
    }

    public record ReaderShort(Long id, String name) {
    }

    @Schema(name = "Loan")
    public record LoanResponse(Long id, BookShort book, ReaderShort reader, OffsetDateTime issuedAt, OffsetDateTime dueAt,
                               OffsetDateTime returnedAt, Status status) {
        public static LoanResponse of(Loan l, OffsetDateTime now) {
            Status s;
            if (l.getReturnedAt() == null) {
                s = now.isAfter(l.getDueAt()) ? Status.OVERDUE : Status.ACTIVE;
            } else {
                s = l.getReturnedAt().isAfter(l.getDueAt()) ? Status.RETURNED_LATE : Status.RETURNED;
            }
            return new LoanResponse(l.getId(), new BookShort(l.getBook().getId(), l.getBook().getTitle()),
                    new ReaderShort(l.getReader().getId(), l.getReader().getName()),
                    l.getIssuedAt(), l.getDueAt(), l.getReturnedAt(), s);
        }
    }

    @Schema(name = "BookStats")
    public record BookStats(Long bookId, String title, int timesIssued, int timesReturned, int overdueReturns,
                            OffsetDateTime lastEventAt) {
    }
}
