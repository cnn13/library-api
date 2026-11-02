package ru.lab15.library.api;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Ожидаемые ошибки бизнес-логики: превращаются в ответ с нужным HTTP-кодом. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final Map<String, String> errors;

    public ApiException(HttpStatus status, String title, String detail, Map<String, String> errors) {
        super(detail);
        this.status = status;
        this.title = title;
        this.errors = errors;
    }

    public static ApiException notFound(String what, Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "Not Found", what + " with id=" + id + " not found", null);
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Conflict", detail, null);
    }

    public static ApiException invalidField(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed", "Request contains invalid fields",
                Map.of(field, message));
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Bad Request", detail, null);
    }

    public HttpStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public Map<String, String> getErrors() { return errors; }
}
