package com.hospital.scheduler.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Startup health-check for security-critical configuration.
 *
 * Logs warnings (does NOT fail startup) for configuration that is
 * acceptable in dev but unacceptable in production:
 * - JWT secret is the default placeholder value
 * - JWT secret is too short (less than 256 bits / 32 bytes base64)
 * - CORS is set to wildcard (not configured for this app)
 */
@Component
public class SecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(SecurityValidator.class);

    private static final String DEFAULT_JWT_SECRET = "Ym9va2NhbXBzZWNyZXRrZXlmb3Jqd3R0b2tlbndpdGhBdExlYXN0MzJjaGFycw==";
    private static final int MIN_JWT_SECRET_BYTES = 32; // 256 bits

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @PostConstruct
    public void validate() {
        boolean isProduction = "prod".equalsIgnoreCase(activeProfile)
                || "production".equalsIgnoreCase(activeProfile)
                || "prod".equalsIgnoreCase(System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", ""));

        if (isProduction) {
            validateJwtSecret();
        } else {
            // Only warn, don't block dev
            if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
                log.warn("[SECURITY] JWT secret is using the default placeholder value. " +
                        "Set JWT_SECRET environment variable in production.");
            }
        }
    }

    private void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            log.error("[SECURITY] JWT secret is not configured. " +
                    "Set JWT_SECRET environment variable in production!");
            return;
        }

        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            log.error("[SECURITY] JWT secret is using the default placeholder value. " +
                    "This is a CRITICAL security vulnerability in production. " +
                    "Generate a new secret: openssl rand -base64 32");
            return;
        }

        // Check minimum length (base64 encoded, so 32 bytes → ~44 chars)
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(jwtSecret);
            if (decoded.length < MIN_JWT_SECRET_BYTES) {
                log.warn("[SECURITY] JWT secret is too short ({} bytes). " +
                        "Minimum recommended: {} bytes. Generate: openssl rand -base64 32",
                        decoded.length, MIN_JWT_SECRET_BYTES);
            }
        } catch (IllegalArgumentException e) {
            log.warn("[SECURITY] JWT secret is not valid base64. " +
                    "Ensure JWT_SECRET is base64-encoded. Generate: openssl rand -base64 32");
        }
    }
}
