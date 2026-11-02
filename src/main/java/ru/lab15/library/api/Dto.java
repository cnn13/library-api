package ru.lab15.library.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import ru.lab15.library.domain.Author;
import ru.lab15.library.domain.Book;

/** DTO запросов и ответов. Ограничения проверяются Bean Validation (ошибка → 422). */
public final class Dto {

    private Dto() {
    }

    /** ISBN-10 или ISBN-13: цифры, допускаются дефисы и пробелы; у ISBN-10 последний символ может быть X. */
    static final String ISBN_REGEX = "^(?:[0-9][- ]?){9}[0-9Xx]$|^(?:[0-9][- ]?){12}[0-9]$";

    @Schema(name = "AuthorRequest")
    public record AuthorRequest(
            @NotBlank @Size(max = 100) @Schema(example = "Лев Толстой") String name,
            @Size(max = 60) @Schema(example = "Россия") String country,
            @Min(1000) @Max(2100) @Schema(example = "1828") Integer birthYear) {
    }

    @Schema(name = "Author")
    public record AuthorResponse(Long id, String name, String country, Integer birthYear, long booksCount,
                                 OffsetDateTime createdAt) {
        static AuthorResponse of(Author a, long booksCount) {
            return new AuthorResponse(a.getId(), a.getName(), a.getCountry(), a.getBirthYear(), booksCount, a.getCreatedAt());
        }
    }

    @Schema(name = "BookRequest", description = "Создание (POST) и полная замена (PUT) книги")
    public record BookRequest(
            @NotBlank @Size(max = 200) @Schema(example = "Война и мир") String title,
            @NotNull @Positive @Schema(example = "1") Long authorId,
            @NotNull @Min(1450) @Max(2100) @Schema(example = "1869") Integer year,
            @Pattern(regexp = ISBN_REGEX, message = "must be ISBN-10 or ISBN-13") @Schema(example = "978-5-17-090335-2") String isbn,
            @Min(1) @Max(10000) @Schema(example = "1274") Integer pages) {
    }

    @Schema(name = "BookPatch", description = "Частичное изменение: передаются только изменяемые поля")
    public record BookPatch(
            @Size(min = 1, max = 200) String title,
            @Positive Long authorId,
            @Min(1450) @Max(2100) Integer year,
            @Pattern(regexp = ISBN_REGEX, message = "must be ISBN-10 or ISBN-13") String isbn,
            @Min(1) @Max(10000) Integer pages) {
        boolean isEmpty() {
            return title == null && authorId == null && year == null && isbn == null && pages == null;
        }
    }

    @Schema(name = "Book")
    public record BookResponse(Long id, String title, AuthorShort author, Integer year, String isbn, Integer pages,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt) {
        static BookResponse of(Book b) {
            return new BookResponse(b.getId(), b.getTitle(), new AuthorShort(b.getAuthor().getId(), b.getAuthor().getName()),
                    b.getYear(), b.getIsbn(), b.getPages(), b.getCreatedAt(), b.getUpdatedAt());
        }
    }

    public record AuthorShort(Long id, String name) {
    }

    @Schema(name = "PageOfBooks")
    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
        static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }
}
