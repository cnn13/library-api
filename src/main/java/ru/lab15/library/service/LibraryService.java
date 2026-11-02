package ru.lab15.library.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.lab15.library.api.ApiException;
import ru.lab15.library.domain.Author;
import ru.lab15.library.domain.Book;
import ru.lab15.library.repo.AuthorRepository;
import ru.lab15.library.repo.BookRepository;

@Service
@Transactional
public class LibraryService {

    private static final Logger log = LoggerFactory.getLogger(LibraryService.class);

    private final AuthorRepository authors;
    private final BookRepository books;

    public LibraryService(AuthorRepository authors, BookRepository books) {
        this.authors = authors;
        this.books = books;
    }

    // ---------- авторы ----------

    @Transactional(readOnly = true)
    public List<Author> listAuthors() {
        return authors.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Author getAuthor(Long id) {
        return authors.findById(id).orElseThrow(() -> ApiException.notFound("Author", id));
    }

    @Transactional(readOnly = true)
    public long countBooks(Long authorId) {
        return books.countByAuthorId(authorId);
    }

    public Author createAuthor(String name, String country, Integer birthYear) {
        Author a = authors.save(new Author(name.trim(), blankToNull(country), birthYear));
        log.info("author created id={} name='{}'", a.getId(), a.getName());
        return a;
    }

    public void deleteAuthor(Long id) {
        Author a = getAuthor(id);
        long n = books.countByAuthorId(id);
        if (n > 0) {
            throw ApiException.conflict("Author id=" + id + " has " + n + " book(s); delete them first");
        }
        authors.delete(a);
        log.info("author deleted id={}", id);
    }

    // ---------- книги ----------

    @Transactional(readOnly = true)
    public Page<Book> listBooks(Long authorId, Integer year, int page, int size) {
        return books.search(authorId, year, PageRequest.of(page, size, Sort.by("id")));
    }

    @Transactional(readOnly = true)
    public Book getBook(Long id) {
        return books.findById(id).orElseThrow(() -> ApiException.notFound("Book", id));
    }

    public Book createBook(String title, Long authorId, Integer year, String isbn, Integer pages) {
        String normIsbn = normalizeIsbn(isbn);
        checkIsbnFree(normIsbn, null);
        Book b = books.save(new Book(title.trim(), existingAuthor(authorId), checkYear(year), normIsbn, pages));
        log.info("book created id={} title='{}' authorId={}", b.getId(), b.getTitle(), authorId);
        return b;
    }

    /** PUT: полная замена; null в необязательных полях очищает их. */
    public Book replaceBook(Long id, String title, Long authorId, Integer year, String isbn, Integer pages) {
        Book b = getBook(id);
        String normIsbn = normalizeIsbn(isbn);
        checkIsbnFree(normIsbn, id);
        b.setTitle(title.trim());
        b.setAuthor(existingAuthor(authorId));
        b.setYear(checkYear(year));
        b.setIsbn(normIsbn);
        b.setPages(pages);
        log.info("book replaced id={}", id);
        return books.saveAndFlush(b);
    }

    /** PATCH: меняются только переданные (не null) поля. */
    public Book patchBook(Long id, String title, Long authorId, Integer year, String isbn, Integer pages) {
        Book b = getBook(id);
        if (title != null) {
            if (title.isBlank()) {
                throw ApiException.invalidField("title", "must not be blank");
            }
            b.setTitle(title.trim());
        }
        if (authorId != null) {
            b.setAuthor(existingAuthor(authorId));
        }
        if (year != null) {
            b.setYear(checkYear(year));
        }
        if (isbn != null) {
            String normIsbn = normalizeIsbn(isbn);
            checkIsbnFree(normIsbn, id);
            b.setIsbn(normIsbn);
        }
        if (pages != null) {
            b.setPages(pages);
        }
        log.info("book patched id={}", id);
        return books.saveAndFlush(b);
    }

    public void deleteBook(Long id) {
        books.delete(getBook(id));
        log.info("book deleted id={}", id);
    }

    // ---------- проверки ----------

    private Author existingAuthor(Long authorId) {
        return authors.findById(authorId)
                .orElseThrow(() -> ApiException.invalidField("authorId", "author with id=" + authorId + " does not exist"));
    }

    private Integer checkYear(Integer year) {
        int current = java.time.Year.now().getValue();
        if (year > current) {
            throw ApiException.invalidField("year", "must not be in the future (> " + current + ")");
        }
        return year;
    }

    private void checkIsbnFree(String isbn, Long selfId) {
        if (isbn == null) {
            return;
        }
        boolean taken = selfId == null ? books.existsByIsbn(isbn) : books.existsByIsbnAndIdNot(isbn, selfId);
        if (taken) {
            throw ApiException.conflict("Book with ISBN " + isbn + " already exists");
        }
    }

    /** Убираем дефисы и пробелы, X приводим к верхнему регистру. */
    static String normalizeIsbn(String isbn) {
        if (isbn == null || isbn.isBlank()) {
            return null;
        }
        return isbn.replaceAll("[- ]", "").toUpperCase();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
