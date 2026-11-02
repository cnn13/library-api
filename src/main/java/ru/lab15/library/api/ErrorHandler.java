package ru.lab15.library.api;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Все ошибки превращаются в ответ application/problem+json (RFC 7807):
 * {type, title, status, detail, instance, timestamp, requestId, [errors]}.
 * Стандартные ошибки Spring MVC (404 неизвестный путь, 405, 415, 400 при неверном типе параметра)
 * обрабатывает базовый ResponseEntityExceptionHandler, здесь они только дополняются и логируются.
 */
@RestControllerAdvice
public class ErrorHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    /** Ошибки валидации тела запроса (@Valid) → 422 со списком полей. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Request contains invalid fields");
        pd.setTitle("Validation failed");
        pd.setProperty("errors", errors);
        return handleExceptionInternal(ex, pd, headers, HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    /** Ошибки валидации параметров запроса (?size=1000, ?page=-1) → 400 со списком параметров. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex, HttpHeaders headers,
                                                                            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(r -> errors.put(
                r.getMethodParameter().getParameterName(),
                r.getResolvableErrors().isEmpty() ? "invalid" : r.getResolvableErrors().get(0).getDefaultMessage()));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameters");
        pd.setProperty("errors", errors);
        return handleExceptionInternal(ex, pd, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Битый JSON, неверный тип поля, неизвестное поле → 400 с понятным сообщением. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String detail = "Malformed JSON request body";
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException u) {
            detail = "Unknown field '" + u.getPropertyName() + "'";
        } else if (cause instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException f && !f.getPath().isEmpty()) {
            detail = "Field '" + f.getPath().get(0).getFieldName() + "' has invalid value '" + f.getValue() + "'";
        } else if (cause instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException m && !m.getPath().isEmpty()) {
            detail = "Field '" + m.getPath().get(0).getFieldName() + "' has invalid type";
        } else if (ex.getMessage() != null && ex.getMessage().startsWith("Required request body is missing")) {
            detail = "Request body is required";
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        return handleExceptionInternal(ex, pd, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApi(ApiException ex, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        pd.setTitle(ex.getTitle());
        if (ex.getErrors() != null) {
            pd.setProperty("errors", ex.getErrors());
        }
        return handleExceptionInternal(ex, pd, new HttpHeaders(), ex.getStatus(), request);
    }

    /** Ограничения, проверенные вне Spring MVC (например, через @Validated-прокси) → тоже 400, а не 500. */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraint(jakarta.validation.ConstraintViolationException ex, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String path = v.getPropertyPath().toString();
            errors.put(path.substring(path.lastIndexOf('.') + 1), v.getMessage());
        });
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameters");
        pd.setProperty("errors", errors);
        return handleExceptionInternal(ex, pd, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    /** Нарушение ограничений БД, не пойманное проверками сервиса (гонка при одинаковом ISBN и т.п.). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Data integrity violation");
        return handleExceptionInternal(ex, pd, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    /** СУБД недоступна → 503, сервис при этом продолжает работать. */
    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class})
    public ResponseEntity<Object> handleDbDown(Exception ex, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Database is unavailable, try again later");
        return handleExceptionInternal(ex, pd, new HttpHeaders(), HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    /** Всё непредвиденное → 500 без подробностей наружу, стек — в лог. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleOther(Exception ex, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
        return handleExceptionInternal(ex, pd, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /** Общая точка: дополняем тело полями instance/timestamp/requestId и пишем в лог. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = body instanceof ProblemDetail p ? p : ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        if (request instanceof ServletWebRequest swr) {
            pd.setInstance(java.net.URI.create(swr.getRequest().getRequestURI()));
        }
        pd.setProperty("timestamp", OffsetDateTime.now());
        pd.setProperty("requestId", MDC.get("requestId"));

        String path = pd.getInstance() == null ? "?" : pd.getInstance().toString();
        if (status.is5xxServerError()) {
            log.error("request failed: status={} path={} detail='{}'", status.value(), path, pd.getDetail(), ex);
        } else {
            log.warn("request rejected: status={} path={} detail='{}'{}", status.value(), path, pd.getDetail(),
                    pd.getProperties() != null && pd.getProperties().get("errors") != null ? " errors=" + pd.getProperties().get("errors") : "");
        }
        return super.handleExceptionInternal(ex, pd, headers, status, request);
    }
}
