package com.hospital.scheduler.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds a per-request correlation ID to the MDC and to the response header.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>If the client supplied {@code X-Request-Id}, reuse it (lets a frontend
 *       or upstream proxy propagate the same ID through the call chain).</li>
 *   <li>Otherwise generate a UUIDv4.</li>
 *   <li>Put it under {@code MDC} key {@code requestId} for the lifetime of the
 *       request so log lines can include it via the Logback pattern.</li>
 *   <li>Echo it back in the {@code X-Request-Id} response header so the client
 *       can correlate failures with server logs.</li>
 * </ul>
 *
 * <p>MDC is always cleared in {@code finally} to avoid leaking the value into
 * subsequent pooled threads.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank() || requestId.length() > 128) {
            requestId = UUID.randomUUID().toString();
        }
        try {
            MDC.put(MDC_KEY, requestId);
            response.setHeader(HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}