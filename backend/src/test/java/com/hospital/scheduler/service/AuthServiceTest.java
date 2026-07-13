package com.hospital.scheduler.service;

import com.hospital.scheduler.config.RateLimitingFilter;
import com.hospital.scheduler.dto.AuthResponse;
import com.hospital.scheduler.dto.LoginRequest;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.ClientIpResolver;
import com.hospital.scheduler.security.JwtService;
import com.hospital.scheduler.security.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AuthService unit tests. No Spring context — collaborators mocked so each
 * branch (wrong password, disabled account, refresh rotation, revoke) is
 * verified in isolation.
 *
 * <p>Why this matters: the refresh-rotation flow has a non-obvious theft
 * signal (reuse of a revoked token revokes ALL of a user's tokens). That
 * policy lives in RefreshTokenService — we don't re-test it here. We only
 * assert AuthService delegates correctly and converts the result to
 * {@link AuthResponse}.
 */
@DisplayName("AuthService")
class AuthServiceTest {

    private static final String IP = "192.0.2.10";
    private static final String USERNAME = "alice";
    private static final String RAW_PASSWORD = "S3cret!";
    private static final String PASSWORD_HASH = "$2a$10$hashed";
    private static final String ACCESS_TOKEN = "access.jwt.value";
    private static final long ACCESS_TTL_MS = 900_000L;
    private static final long REFRESH_TTL_MS = 604_800_000L;

    private StaffRepository staffRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private RateLimitingFilter rateLimitingFilter;
    private RefreshTokenService refreshTokenService;
    private PermissionService permissionService;
    private ClientIpResolver clientIpResolver;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        staffRepository = mock(StaffRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        rateLimitingFilter = mock(RateLimitingFilter.class);
        refreshTokenService = mock(RefreshTokenService.class);
        permissionService = mock(PermissionService.class);
        clientIpResolver = mock(ClientIpResolver.class);

        authService = new AuthService(
                jwtService, passwordEncoder, staffRepository, rateLimitingFilter,
                refreshTokenService, permissionService, clientIpResolver);

        when(clientIpResolver.resolve(any())).thenReturn(IP);
        when(jwtService.getExpirationTime()).thenReturn(ACCESS_TTL_MS);
        when(jwtService.getRefreshExpirationTime()).thenReturn(REFRESH_TTL_MS);
    }

    // ---------- login ----------

    @Test
    @DisplayName("login: happy path returns tokens, roles, permissions")
    void login_success() {
        Staff active = activeStaff(USERNAME, PASSWORD_HASH, true);
        when(staffRepository.findByUsername(USERNAME)).thenReturn(Optional.of(active));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(jwtService.generateToken(eq(USERNAME), any(), any())).thenReturn(ACCESS_TOKEN);
        when(refreshTokenService.issue(eq(active), eq(IP)))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-raw", null));
        when(permissionService.permissionsOf(active)).thenReturn(List.of("STAFF_VIEW"));

        AuthResponse resp = authService.login(loginReq(USERNAME, RAW_PASSWORD), new MockHttpServletRequest());

        assertThat(resp.getToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(resp.getRefreshToken()).isEqualTo("refresh-raw");
        assertThat(resp.getTokenType()).isEqualTo("Bearer");
        assertThat(resp.getExpiresIn()).isEqualTo(ACCESS_TTL_MS);
        assertThat(resp.getRefreshExpiresIn()).isEqualTo(REFRESH_TTL_MS);
        assertThat(resp.getUsername()).isEqualTo(USERNAME);
        assertThat(resp.getUserId()).isEqualTo((long) active.getId());
        assertThat(resp.getPermissions()).containsExactly("STAFF_VIEW");
        verify(rateLimitingFilter, never()).recordFailedLogin(anyString());
    }

    @Test
    @DisplayName("login: unknown username records failed-login and throws BadCredentials")
    void login_unknownUsername() {
        when(staffRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginReq(USERNAME, RAW_PASSWORD), new MockHttpServletRequest()))
                .isInstanceOf(BadCredentialsException.class);
        verify(rateLimitingFilter).recordFailedLogin(IP);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("login: wrong password records failed-login and throws BadCredentials")
    void login_wrongPassword() {
        Staff active = activeStaff(USERNAME, PASSWORD_HASH, true);
        when(staffRepository.findByUsername(USERNAME)).thenReturn(Optional.of(active));
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginReq(USERNAME, RAW_PASSWORD), new MockHttpServletRequest()))
                .isInstanceOf(BadCredentialsException.class);
        verify(rateLimitingFilter).recordFailedLogin(IP);
    }

    @Test
    @DisplayName("login: inactive staff throws without recording failed-login (not a brute-force signal)")
    void login_inactiveStaff() {
        Staff inactive = activeStaff(USERNAME, PASSWORD_HASH, false);
        when(staffRepository.findByUsername(USERNAME)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> authService.login(loginReq(USERNAME, RAW_PASSWORD), new MockHttpServletRequest()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("vô hiệu hóa");
        verify(rateLimitingFilter, never()).recordFailedLogin(anyString());
    }

    // ---------- refresh ----------

    @Test
    @DisplayName("refresh: rotated tokens become a full AuthResponse")
    void refresh_success() {
        when(refreshTokenService.rotate("old-refresh", IP)).thenReturn(Optional.of(
                new RefreshTokenService.RotatedTokens("new.access", 900_000L, "new.refresh", null)));
        when(jwtService.extractUsername("new.access")).thenReturn(USERNAME);
        when(jwtService.extractRoles("new.access")).thenReturn(List.of("ADMIN"));
        when(jwtService.extractPermissions("new.access")).thenReturn(List.of("STAFF_VIEW"));

        AuthResponse resp = authService.refresh("old-refresh", new MockHttpServletRequest());

        assertThat(resp.getToken()).isEqualTo("new.access");
        assertThat(resp.getRefreshToken()).isEqualTo("new.refresh");
        assertThat(resp.getExpiresIn()).isEqualTo(900_000L);
        assertThat(resp.getRefreshExpiresIn()).isEqualTo(REFRESH_TTL_MS);
        assertThat(resp.getUsername()).isEqualTo(USERNAME);
        assertThat(resp.getRoles()).containsExactly("ADMIN");
        assertThat(resp.getPermissions()).containsExactly("STAFF_VIEW");
    }

    @Test
    @DisplayName("refresh: invalid token → BadCredentials")
    void refresh_invalid() {
        when(refreshTokenService.rotate("bogus", IP)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("bogus", new MockHttpServletRequest()))
                .isInstanceOf(BadCredentialsException.class);
    }

    // ---------- logout ----------

    @Test
    @DisplayName("logout: revokes token when present")
    void logout_withToken() {
        authService.logout("refresh-raw");
        verify(refreshTokenService).revoke("refresh-raw");
    }

    @Test
    @DisplayName("logout: null/blank token is a no-op (does not throw)")
    void logout_nullOrBlank() {
        authService.logout(null);
        authService.logout("   ");
        verify(refreshTokenService, never()).revoke(anyString());
    }

    // ---------- helpers ----------

    private static LoginRequest loginReq(String u, String p) {
        return LoginRequest.builder().username(u).password(p).build();
    }

    private static Staff activeStaff(String username, String hash, boolean isActive) {
        return Staff.builder()
                .id(7)
                .username(username)
                .passwordHash(hash)
                .isActive(isActive)
                .staffRoles(new java.util.HashSet<>())
                .build();
    }
}