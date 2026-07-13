package com.hospital.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login response with JWT access token + refresh token")
public class AuthResponse {

    @Schema(description = "JWT access token (short-lived, e.g. 15 min)", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    /**
     * Long-lived refresh token (default 7 days). Frontend should send this
     * to {@code POST /api/v1/auth/refresh} when the access token expires.
     * NEVER store the refresh token in localStorage — use an HttpOnly cookie
     * or in-memory only.
     */
    @Schema(description = "Opaque refresh token (long-lived, e.g. 7 days)")
    private String refreshToken;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Access token expiration in milliseconds", example = "900000")
    private Long expiresIn;

    @Schema(description = "Refresh token expiration in milliseconds", example = "604800000")
    private Long refreshExpiresIn;

    @Schema(description = "User ID", example = "1")
    private Long userId;

    @Schema(description = "Username", example = "admin")
    private String username;

    @Schema(description = "User roles", example = "[\"ADMIN\", \"MANAGER\"]")
    private List<String> roles;

    @Schema(description = "Flattened permission set for the user (deduplicated across roles)",
            example = "[\"STAFF_VIEW\",\"SCHEDULE_VIEW\"]")
    private List<String> permissions;
}
