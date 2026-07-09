package com.hospital.scheduler.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based rate limiter for the login endpoint.
 *
 * Configurable via application.properties:
 * - app.auth.rate-limit.enabled=true|false (default true)
 * - app.auth.rate-limit.max-attempts=5 (default 5)
 * - app.auth.rate-limit.window-minutes=15 (default 15)
 *
 * Set app.auth.rate-limit.enabled=false in development to avoid blocking testing.
 */
@Component
@Order(1)
public class RateLimitingFilter implements Filter {

    @Value("${app.auth.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${app.auth.rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.auth.rate-limit.window-minutes:15}")
    private int windowMinutes;

    private final Map<String, List<Long>> ipAttempts = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (!rateLimitEnabled) {
            chain.doFilter(request, response);
            return;
        }

        String uri = req.getRequestURI();
        if ("/api/v1/auth/login".equals(uri) && "POST".equalsIgnoreCase(req.getMethod())) {
            String ip = getClientIp(req);
            if (isBlocked(ip)) {
                res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                res.setContentType("application/json");
                String message = String.format(
                        "{\"success\":false,\"message\":\"Quá nhiều yêu cầu đăng nhập. Thử lại sau %d phút.\"}",
                        windowMinutes);
                res.getWriter().write(message);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    public void recordFailedLogin(String ip) {
        ipAttempts.computeIfAbsent(ip, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(Instant.now().toEpochMilli());
        cleanup(ip);
    }

    private boolean isBlocked(String ip) {
        List<Long> attempts = ipAttempts.getOrDefault(ip, Collections.emptyList());
        cleanup(ip);
        return attempts.size() >= maxAttempts;
    }

    private void cleanup(String ip) {
        long windowMs = windowMinutes * 60 * 1000L;
        long cutoff = Instant.now().toEpochMilli() - windowMs;
        List<Long> attempts = ipAttempts.get(ip);
        if (attempts != null) {
            attempts.removeIf(t -> t < cutoff);
        }
    }

    private String getClientIp(HttpServletRequest req) {
        String xf = req.getHeader("X-Forwarded-For");
        return xf != null ? xf.split(",")[0].trim() : req.getRemoteAddr();
    }
}
