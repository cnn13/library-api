package ru.lab15.library.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.lab15.library.api.LoanDto.BookStats;
import ru.lab15.library.api.LoanDto.LoanRequest;
import ru.lab15.library.api.LoanDto.LoanResponse;
import ru.lab15.library.api.LoanDto.ReaderRequest;
import ru.lab15.library.api.LoanDto.ReaderResponse;
import ru.lab15.library.service.LoanService;

@RestController
@Tag(name = "Loans", description = "Читатели, выдача и возврат книг")
public class LoanController {

    private final LoanService service;
    private final JdbcTemplate jdbc;

    public LoanController(LoanService service, JdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @PostMapping("/api/v1/readers")
    @Operation(summary = "Зарегистрировать читателя")
    public ResponseEntity<ReaderResponse> createReader(@Valid @RequestBody ReaderRequest req) {
        ReaderResponse r = service.createReader(req.name(), req.email());
        return ResponseEntity.created(URI.create("/api/v1/readers/" + r.id())).body(r);
    }

    @GetMapping("/api/v1/readers/{id}")
    @Operation(summary = "Читатель по id (с числом активных выдач)")
    public ReaderResponse getReader(@PathVariable Long id) {
        return service.getReader(id);
    }

    @PostMapping("/api/v1/loans")
    @Operation(summary = "Выдать книгу", description = "409 — книга уже выдана или у читателя исчерпан лимит; событие BOOK_ISSUED уходит в Kafka")
    public ResponseEntity<LoanResponse> issue(@Valid @RequestBody LoanRequest req) {
        LoanResponse l = service.issue(req.bookId(), req.readerId());
        return ResponseEntity.created(URI.create("/api/v1/loans/" + l.id())).body(l);
    }

    @PostMapping("/api/v1/loans/{id}/return")
    @Operation(summary = "Вернуть книгу", description = "409 — уже возвращена; событие BOOK_RETURNED уходит в Kafka")
    public LoanResponse returnBook(@PathVariable Long id) {
        return service.returnBook(id);
    }

    @GetMapping("/api/v1/loans")
    @Operation(summary = "Список выдач", description = "Фильтры: readerId, active=true|false")
    public List<LoanResponse> list(@RequestParam(required = false) @Positive Long readerId,
                                   @RequestParam(required = false) Boolean active) {
        return service.list(readerId, active);
    }

    /** Проекция, которую строит Kafka-consumer (eventual consistency: обновляется с небольшой задержкой). */
    @GetMapping("/api/v1/stats/books/top")
    @Operation(summary = "Самые популярные книги", description = "Строится consumer'ом из событий Kafka")
    public List<BookStats> topBooks(@RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit) {
        return jdbc.query("""
                select s.book_id, b.title, s.times_issued, s.times_returned, s.overdue_returns, s.last_event_at
                from book_stats s join books b on b.id = s.book_id
                order by s.times_issued desc, s.book_id
                limit ?
                """, (rs, i) -> new BookStats(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                rs.getObject(6, OffsetDateTime.class)), limit);
    }
}
