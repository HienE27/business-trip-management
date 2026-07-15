package com.hospital.scheduler.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/**
 * Extended auth details that carry the raw JWT alongside Spring Security's
 * standard request metadata (IP, session id).
 *
 * <p>Why this exists: {@link PermissionInvalidationFilter} needs to re-extract
 * the {@code permVer} claim from the bearer token to compare it against the
 * current DB row on every request. Spring's standard
 * {@code WebAuthenticationDetails} only carries the request context, so the
 * filter has to read the JWT from somewhere else. We pack it into the details
 * slot of the {@code UsernamePasswordAuthenticationToken} so:
 * <ul>
 *   <li>The standard principal contract still holds (principal = username).</li>
 *   <li>Downstream filters can pull the raw token via
 *       {@code auth.getDetails()} casted to this class.</li>
 * </ul>
 */
public class JwtAuthenticationDetails extends WebAuthenticationDetails {

    private final String token;

    public JwtAuthenticationDetails(String token, HttpServletRequest request) {
        super(request);
        this.token = token;
    }

    /** Raw bearer JWT — used by {@link PermissionInvalidationFilter} to check the version claim. */
    public String getToken() {
        return token;
    }
}