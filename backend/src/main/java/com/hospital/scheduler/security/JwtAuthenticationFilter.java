package com.hospital.scheduler.security;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTH_COOKIE_NAME = "medschedule_access_token";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            @Nonnull HttpServletRequest request,
            @Nonnull HttpServletResponse response,
            @Nonnull FilterChain filterChain
    ) throws ServletException, IOException {
        final String jwt = resolveJwt(request);

        if (jwt == null || jwt.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // BUG-FIX: refresh tokens must NOT be accepted as access tokens.
        // They have longer expiry and would otherwise keep an attacker authenticated
        // indefinitely if leaked — only the short-lived access token is the bearer of
        // an authentication context. Refresh tokens are exchanged exclusively via
        // POST /api/v1/auth/refresh.
        // BUG-FIX (was 500-on-empty-auth): extractTokenType throws on malformed
        // tokens. Wrapped the whole block in try/catch so a stray empty/whitespace
        // token in Authorization or X-Auth-Token can't take down the filter chain
        // with a 500.
        try {
            if (!"access".equals(jwtService.extractTokenType(jwt))) {
                logger.warn("Rejecting non-access token on protected endpoint (tokenType != access)");
                filterChain.doFilter(request, response);
                return;
            }

            try {
                final String username = jwtService.extractUsername(jwt);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    if (jwtService.isTokenValid(jwt)) {
                        List<String> roles = jwtService.extractRoles(jwt);
                        List<String> permissions = jwtService.extractPermissions(jwt);

                        java.util.Set<SimpleGrantedAuthority> authorities = new java.util.LinkedHashSet<>();
                        // Roles keep the ROLE_ prefix so legacy @hasRole checks keep working.
                        if (roles != null) {
                            for (String role : roles) {
                                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                            }
                        }
                        // Permissions are flat authorities (no prefix) so @hasAuthority('PERM_X') matches.
                        if (permissions != null) {
                            for (String perm : permissions) {
                                authorities.add(new SimpleGrantedAuthority(perm));
                            }
                        }

                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                authorities
                        );
                        // BUGFIX (was STALE-LOGOUT): PermissionInvalidationFilter relies
                        // on being able to extract the raw JWT from the Authentication so
                        // it can re-read the permVer claim. The standard Spring contract
                        // is to put the username in the principal slot, which would break
                        // that filter. Stash the JWT in `details` so both the standard
                        // contract AND the cross-filter contract hold simultaneously.
                        authToken.setDetails(new JwtAuthenticationDetails(jwt, request));
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            } catch (Exception e) {
                logger.error("Cannot set user authentication: " + e.getMessage());
            }
        } catch (Exception e) {
            logger.error("JwtAuthenticationFilter: failed to inspect token — passing through unauthenticated: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveJwt(HttpServletRequest request) {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Also check custom token header (for frontend localStorage token)
        final String customHeader = request.getHeader("X-Auth-Token");
        if (customHeader != null && !customHeader.isBlank()) {
            return customHeader;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> AUTH_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
