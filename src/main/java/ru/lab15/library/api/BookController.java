package ru.lab15.library.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.lab15.library.api.Dto.BookPatch;
import ru.lab15.library.api.Dto.BookRequest;
import ru.lab15.library.api.Dto.BookResponse;
import ru.lab15.library.api.Dto.PageResponse;
import ru.lab15.library.domain.Book;
import ru.lab15.library.service.LibraryService;

// без @Validated: ограничения на параметрах проверяет сам Spring MVC (HandlerMethodValidationException → 400)
@RestController
@RequestMapping("/api/v1/books")
@Tag(name = "Books", description = "Книги")
public class BookController {

    private final LibraryService service;

    public BookController(LibraryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Список книг", description = "Фильтры authorId и year, постраничный вывод page/size")
    public PageResponse<BookResponse> list(
            @RequestParam(required = false) @Positive Long authorId,
            @RequestParam(required = false) @Min(1450) @Max(2100) Integer year,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.of(service.listBooks(authorId, year, page, size).map(BookResponse::of));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Книга по id")
    public BookResponse get(@PathVariable Long id) {
        return BookResponse.of(service.getBook(id));
    }

    @PostMapping
    @Operation(summary = "Добавить книгу")
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest req) {
        Book b = service.createBook(req.title(), req.authorId(), req.year(), req.isbn(), req.pages());
        return ResponseEntity.created(URI.create("/api/v1/books/" + b.getId())).body(BookResponse.of(b));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Заменить книгу целиком")
    public BookResponse replace(@PathVariable Long id, @Valid @RequestBody BookRequest req) {
        return BookResponse.of(service.replaceBook(id, req.title(), req.authorId(), req.year(), req.isbn(), req.pages()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Изменить отдельные поля книги")
    public BookResponse patch(@PathVariable Long id, @Valid @RequestBody BookPatch req) {
        if (req.isEmpty()) {
            throw ApiException.badRequest("PATCH body must contain at least one field: title, authorId, year, isbn, pages");
        }
        return BookResponse.of(service.patchBook(id, req.title(), req.authorId(), req.year(), req.isbn(), req.pages()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить книгу")
    public void delete(@PathVariable Long id) {
        service.deleteBook(id);
    }
}
