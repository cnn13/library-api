package ru.lab15.library.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Журнал запросов: метод, путь, код ответа, время обработки.
 * Каждому запросу присваивается requestId (берётся из заголовка X-Request-Id или генерируется),
 * он попадает в MDC (во все строки лога этого запроса), в заголовок ответа и в тело ошибок.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http");
    static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String id = req.getHeader(HEADER);
        if (id == null || id.isBlank() || id.length() > 64) {
            id = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put("requestId", id);
        resp.setHeader(HEADER, id);
        long start = System.nanoTime();
        try {
            chain.doFilter(req, resp);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            String q = req.getQueryString() == null ? "" : "?" + req.getQueryString();
            if (!req.getRequestURI().startsWith("/actuator")) {
                log.info("{} {}{} -> {} ({} ms)", req.getMethod(), req.getRequestURI(), q, resp.getStatus(), ms);
            }
            MDC.remove("requestId");
        }
    }
}
