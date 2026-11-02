package ru.lab15.library.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.lab15.library.api.Dto.AuthorRequest;
import ru.lab15.library.api.Dto.AuthorResponse;
import ru.lab15.library.domain.Author;
import ru.lab15.library.service.LibraryService;

@RestController
@RequestMapping("/api/v1/authors")
@Tag(name = "Authors", description = "Авторы")
public class AuthorController {

    private final LibraryService service;

    public AuthorController(LibraryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Список авторов")
    public List<AuthorResponse> list() {
        return service.listAuthors().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Автор по id")
    public AuthorResponse get(@PathVariable Long id) {
        return toResponse(service.getAuthor(id));
    }

    @PostMapping
    @Operation(summary = "Создать автора")
    public ResponseEntity<AuthorResponse> create(@Valid @RequestBody AuthorRequest req) {
        Author a = service.createAuthor(req.name(), req.country(), req.birthYear());
        return ResponseEntity.created(URI.create("/api/v1/authors/" + a.getId())).body(AuthorResponse.of(a, 0));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить автора", description = "409 Conflict, если у автора есть книги")
    public void delete(@PathVariable Long id) {
        service.deleteAuthor(id);
    }

    private AuthorResponse toResponse(Author a) {
        return AuthorResponse.of(a, service.countBooks(a.getId()));
    }
}
