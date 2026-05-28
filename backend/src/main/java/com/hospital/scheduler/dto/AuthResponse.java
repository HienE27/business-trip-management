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
@Schema(description = "Login response with JWT token")
public class AuthResponse {

    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Token expiration in milliseconds", example = "86400000")
    private Long expiresIn;

    @Schema(description = "User ID", example = "1")
    private Long userId;

    @Schema(description = "Username", example = "admin")
    private String username;

    @Schema(description = "User roles", example = "[\"ADMIN\", \"MANAGER\"]")
    private List<String> roles;
}
