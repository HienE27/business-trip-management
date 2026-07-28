package com.hospital.scheduler.security;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Compares the {@code permVer} claim on the incoming JWT with the current
 * permission-matrix version stored in {@code algorithm_config.permissions.version}.
 *
 * <p>When an ADMIN toggles a permission via the matrix UI, the
 * {@link PermissionVersionService#bump()} call invalidates every outstanding
 * JWT — the next API request from any user gets a {@code 401
 * PERMISSION_VERSION_STALE} response and the frontend interceptor forces
 * a re-login (which re-issues a fresh JWT with the new permissions).
 *
 * <p>Whitelisted paths (auth login/refresh, public endpoints) bypass the
 * check so users who need to re-authenticate can actually hit
 * {@code POST /api/v1/auth/refresh}.
 *
 * <p>Ordering: this filter runs AFTER {@link JwtAuthenticationFilter} so
 * the principal/authorities are already populated; we just need to compare
 * the JWT claim. {@link Order} is high (runs last) so {@link JwtAuthenticationFilter}
 * (lower number) executes first.
 */
@Component
@Order(50)
@RequiredArgsConstructor
public class PermissionInvalidationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(PermissionInvalidationFilter.class);

    private final JwtService jwtService;
    private final PermissionVersionService permissionVersionService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Allow login + refresh to run — users need them to recover from a
        // stale token. Static + actuator endpoints bypass for the same reason.
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        // BUGFIX (was STALE-LOGOUT): the standard Spring contract puts the
        // username in `auth.getPrincipal()`, not the JWT. The original code
        // blindly cast principal to String, which fed the literal username
        // ("admin") into `jwtService.extractPermissionVersion(...)` — that
        // throws → the filter returned 401 on EVERY request right after
        // deploy, logging everyone out. Read the raw token from the details
        // payload (set by JwtAuthenticationFilter) instead.
        String jwt = null;
        if (auth.getDetails() instanceof JwtAuthenticationDetails details) {
            jwt = details.getToken();
        }
        if (jwt == null) {
            // Fallback for tests / contexts that set principal to the raw token
            // directly (e.g. our own unit tests mock the SecurityContext).
            if (auth.getPrincipal() instanceof String principalAsToken
                    && principalAsToken.contains(".")) {
                jwt = principalAsToken;
            }
        }
        if (jwt == null) {
            // No way to inspect the version claim — don't block the request,
            // we just can't enforce the policy. Logged at debug for ops.
            logger.debug("No JWT available on auth details; skipping permVer check (principal={})",
                    auth.getPrincipal());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Long tokenVersion = jwtService.extractPermissionVersion(jwt);
            long currentVersion = permissionVersionService.currentVersion();

            if (tokenVersion == null || tokenVersion < currentVersion) {
                logger.warn("Stale permission matrix version in JWT (token={}, current={}) — forcing re-login",
                        tokenVersion, currentVersion);
                writeStaleResponse(response, currentVersion);
                return;
            }
        } catch (Exception e) {
            // BUGFIX (M07-JWT-500): JWT may be signed with a different secret
            // (e.g. after backend restart with different profile), causing
            // extractPermissionVersion to throw. Don't let this crash the filter
            // chain with HTTP 500 — pass through unauthenticated so the user
            // gets a proper 401/403 from downstream security checks.
            logger.warn("Failed to verify permission version (JWT may be from a different backend instance): {}",
                    e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeStaleResponse(HttpServletResponse response, long currentVersion) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setHeader("X-Permission-Version", String.valueOf(currentVersion));
        response.getWriter().write(
                "{\"success\":false,\"code\":\"PERMISSION_VERSION_STALE\","
                        + "\"message\":\"Permission matrix has changed — please log in again.\","
                        + "\"timestamp\":\"" + java.time.Instant.now() + "\"}"
        );
    }
}