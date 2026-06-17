package com.hospital.scheduler.controller;

import com.hospital.scheduler.config.AuthCookieProperties;
import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.AuthResponse;
import com.hospital.scheduler.dto.LoginRequest;
import com.hospital.scheduler.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication APIs")
public class AuthController {

    private static final String AUTH_COOKIE_NAME = "medschedule_access_token";
    private static final String AUTH_TOKEN_HEADER = "X-Auth-Token";

    private final AuthService authService;
    private final AuthCookieProperties authCookieProperties;

    @PostMapping("/login")
    @Operation(
            summary = "Login",
            description = "Authenticate user and return JWT token"
    )
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request, httpRequest);
        response.addHeader(HttpHeaders.SET_COOKIE, buildAuthCookie(authResponse.getToken(), authResponse.getExpiresIn()).toString());
        response.setHeader(AUTH_TOKEN_HEADER, authResponse.getToken());
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Login successful"));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout",
            description = "Clear authentication cookie"
    )
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildExpiredAuthCookie().toString());
        return ResponseEntity.ok(ApiResponse.success(null, "Logout successful"));
    }

    private ResponseCookie buildAuthCookie(String token, long expiresIn) {
        return ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(authCookieProperties.cookieSecure())
                .sameSite(authCookieProperties.cookieSameSite())
                .path("/")
                .maxAge(expiresIn / 1000)
                .build();
    }

    private ResponseCookie buildExpiredAuthCookie() {
        return ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authCookieProperties.cookieSecure())
                .sameSite(authCookieProperties.cookieSameSite())
                .path("/")
                .maxAge(0)
                .build();
    }
}
