package com.hospital.scheduler.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Refresh access token using long-lived refresh token")
public class RefreshTokenRequest {

    @NotBlank(message = "refreshToken không được để trống")
    @Schema(description = "Opaque refresh token issued at /auth/login",
            example = "f4a2c8e4-...-.d39b...")
    private String refreshToken;
}