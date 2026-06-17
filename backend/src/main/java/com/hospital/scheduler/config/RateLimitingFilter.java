package com.hospital.scheduler.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class RateLimitingFilter implements Filter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000L; // 15 minutes

    private final Map<String, List<Long>> ipAttempts = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String uri = req.getRequestURI();
        if ("/api/v1/auth/login".equals(uri) && "POST".equalsIgnoreCase(req.getMethod())) {
            String ip = getClientIp(req);
            if (isBlocked(ip)) {
                res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                res.setContentType("application/json");
                res.getWriter().write("{\"success\":false,\"message\":\"Quá nhiều yêu cầu đăng nhập. Thử lại sau 15 phút.\"}");
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
        return attempts.size() >= MAX_ATTEMPTS;
    }

    private void cleanup(String ip) {
        long cutoff = Instant.now().toEpochMilli() - WINDOW_MS;
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
