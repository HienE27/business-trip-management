package com.hospital.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.config.AuthCookieProperties;
import com.hospital.scheduler.config.JacksonConfig;
import com.hospital.scheduler.config.PaginationConfig;
import com.hospital.scheduler.testsupport.MethodSecurityTestConfig;
import com.hospital.scheduler.dto.AuthResponse;
import com.hospital.scheduler.dto.LoginRequest;
import com.hospital.scheduler.dto.RefreshTokenRequest;
import com.hospital.scheduler.exception.GlobalExceptionHandler;
import com.hospital.scheduler.security.JwtAuthenticationFilter;
import com.hospital.scheduler.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AuthController}.
 *
 * <p>{@code /api/v1/auth/**} is in the project's {@code permitAll()} list
 * (see {@code SecurityConfig}), so we disable the JWT filter here — the slice
 * is not validating auth, it's validating HTTP wiring (cookies, body binding,
 * status codes, error mapping).
 *
 * <p>Pinned to {@link com.hospital.scheduler.config.AuthCookieProperties}
 * because the controller's cookie builder reads
 * {@code cookieSecure}/{@code cookieSameSite} from it; without the bean the
 * SpEL defaulting fails. We register a minimal impl via {@link Import}
 * (see {@link TestSecurityConfig}) and rely on the {@code @MockitoBean}
 * for the auth service so we can shape the JWT/refresh tokens we want.
 */
@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, AuthControllerWebMvcTest.TestSecurityConfig.class, PaginationConfig.class, MethodSecurityTestConfig.class})
@DisplayName("AuthController - HTTP wiring slice")
class AuthControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean AuthService authService;
    @MockitoBean FilterChainProxy filterChainProxy;
    @MockitoBean com.hospital.scheduler.config.RateLimitingFilter rateLimitingFilter;
    @MockitoBean com.hospital.scheduler.security.ClientIpResolver clientIpResolver;
    @MockitoBean com.hospital.scheduler.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean com.hospital.scheduler.security.JwtService jwtService;

    @org.junit.jupiter.api.BeforeEach
    void passThroughSecurityChain() throws Exception {
        // Mocks above replace the real filters; without these stubs the chain
        // stops at the first mocked filter and no controller is invoked.
        org.mockito.Mockito.doAnswer(inv -> {
            ((jakarta.servlet.FilterChain) inv.getArgument(2))
                    .doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        org.mockito.Mockito.doAnswer(inv -> {
            ((jakarta.servlet.FilterChain) inv.getArgument(2))
                    .doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(rateLimitingFilter).doFilter(any(), any(), any());
    }

    static final String ACCESS_COOKIE = "medschedule_access_token";
    static final String REFRESH_COOKIE = "medschedule_refresh_token";

    // ── /login ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login: success → 200, sets access+refresh cookies, returns AuthResponse")
    void login_success() throws Exception {
        AuthResponse resp = AuthResponse.builder()
                .token("access.jwt.value")
                .refreshToken("raw-refresh-token")
                .tokenType("Bearer")
                .expiresIn(900_000L)
                .refreshExpiresIn(2_592_000_000L)
                .userId(7L)
                .username("alice")
                .build();
        when(authService.login(any(LoginRequest.class), any())).thenReturn(resp);

        LoginRequest body = LoginRequest.builder().username("alice").password("correct").build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("access.jwt.value"))
                .andExpect(jsonPath("$.data.refreshToken").value("raw-refresh-token"))
                .andExpect(cookie().value(ACCESS_COOKIE, "access.jwt.value"))
                .andExpect(cookie().httpOnly(ACCESS_COOKIE, true))
                .andExpect(cookie().value(REFRESH_COOKIE, "raw-refresh-token"))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andExpect(header().string("X-Auth-Token", "access.jwt.value"));
    }

    @Test
    @DisplayName("POST /login: bad credentials → 401, no cookies set")
    void login_badCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new BadCredentialsException("wrong"));

        LoginRequest body = LoginRequest.builder().username("alice").password("wrong").build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Tên đăng nhập")))
                .andExpect(cookie().doesNotExist(ACCESS_COOKIE))
                .andExpect(cookie().doesNotExist(REFRESH_COOKIE));
    }

    @Test
    @DisplayName("POST /login: blank username → 400 (bean validation)")
    void login_blankUsername() throws Exception {
        // LoginRequest has @NotBlank on username/password — validation must fire
        // before we ever reach the service.
        LoginRequest body = LoginRequest.builder().username("").password("").build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any(), any());
    }

    // ── /refresh ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /refresh with body token: success → 200, cookies rotated")
    void refresh_bodyToken() throws Exception {
        AuthResponse resp = AuthResponse.builder()
                .token("rotated.jwt")
                .refreshToken("rotated.refresh")
                .tokenType("Bearer")
                .expiresIn(900_000L)
                .refreshExpiresIn(2_592_000_000L)
                .username("alice")
                .build();
        when(authService.refresh(eq("raw-old-refresh"), any())).thenReturn(resp);

        RefreshTokenRequest body = RefreshTokenRequest.builder().refreshToken("raw-old-refresh").build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("rotated.jwt"))
                .andExpect(cookie().value(ACCESS_COOKIE, "rotated.jwt"))
                .andExpect(cookie().value(REFRESH_COOKIE, "rotated.refresh"));
    }

    @Test
    @DisplayName("POST /refresh with cookie (no body): token taken from cookie")
    void refresh_cookieOnly() throws Exception {
        AuthResponse resp = AuthResponse.builder()
                .token("from-cookie.jwt")
                .refreshToken("from-cookie.refresh")
                .tokenType("Bearer")
                .expiresIn(900_000L)
                .refreshExpiresIn(2_592_000_000L)
                .username("alice")
                .build();
        when(authService.refresh(eq("cookie-refresh"), any())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, "cookie-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("from-cookie.jwt"));
    }

    @Test
    @DisplayName("POST /refresh: expired/invalid refresh token → 401 (edge case)")
    void refresh_expiredToken() throws Exception {
        // Edge: caller presents a refresh token that the service can't rotate
        // (revoked, expired, or stolen). AuthService throws BadCredentialsException,
        // GlobalExceptionHandler maps to 401 with generic message — must NOT leak
        // which failure mode (security: prevents token-state probing).
        when(authService.refresh(eq("expired-token"), any()))
                .thenThrow(new BadCredentialsException("Refresh token không hợp lệ hoặc đã hết hạn"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"expired-token\"}"))
                .andExpect(status().isUnauthorized())
                // Message must be the GENERIC one from GlobalExceptionHandler,
                // not the raw service message — security boundary.
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Tên đăng nhập")))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Refresh token"))));
    }

    // ── /logout ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /logout with cookie: revokes refresh, clears both cookies (maxAge=0)")
    void logout_withCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE, "raw-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(cookie().maxAge(ACCESS_COOKIE, 0))
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));

        verify(authService).logout("raw-refresh");
    }

    @Test
    @DisplayName("POST /logout with body token: body wins over cookie when both present")
    void logout_bodyTakesPrecedence() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"body-refresh\"}")
                        .cookie(new Cookie(REFRESH_COOKIE, "cookie-refresh")))
                .andExpect(status().isOk());

        verify(authService).logout("body-refresh");
        verify(authService, never()).logout("cookie-refresh");
    }

    @Test
    @DisplayName("POST /logout with no token at all: still 200, AuthService.logout(null) tolerated")
    void logout_noToken() throws Exception {
        // Edge: anonymous cleanup (expired browser session). Service must accept null
        // without throwing; we just verify HTTP returns 200 and we don't blow up.
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk());

        verify(authService).logout(null);
    }

    @Test
    @DisplayName("POST /logout when service throws: 500 with generic message (no leakage)")
    void logout_serviceThrows_isGeneric() throws Exception {
        doThrow(new RuntimeException("DB blew up — internal schema name=staff_refresh_token"))
                .when(authService).logout(any());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie(REFRESH_COOKIE, "x")))
                .andExpect(status().isInternalServerError())
                // Catch-all handler must NOT echo the exception message — it contained
                // a table name. Test guards against a regression to verbose 500s.
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("lỗi nội bộ")));
    }

    /**
     * AuthController depends on {@link AuthCookieProperties} for the cookie
     * builder. {@code @WebMvcTest} does not auto-load arbitrary config beans,
     * so register a minimal one here. Values are picked to match dev defaults.
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        AuthCookieProperties authCookieProperties() {
            return new AuthCookieProperties(false, "Lax");
        }
    }
}