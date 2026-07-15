package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.RefreshToken;
import com.hospital.scheduler.entity.RoleName;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.StaffRole;
import com.hospital.scheduler.entity.AppRole;
import com.hospital.scheduler.repository.RefreshTokenRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.JwtService;
import com.hospital.scheduler.security.PermissionService;
import com.hospital.scheduler.security.PermissionVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

/**
 * Tests for RefreshTokenService.
 *
 * Covers:
 * - Issue: generates raw token + persists hash (not raw).
 * - Rotate happy path: returns new pair, old is revoked with replacedBy link.
 * - Theft detection: re-use of revoked token → revoke all of that staff's tokens.
 * - Expiry: expired token returns empty.
 * - Logout: revoke marks revokedAt.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private PermissionService permissionService;

    /** Use a real JwtService but stub the long-lived expiration to a small value. */
    private JwtService jwtService;

    @InjectMocks private RefreshTokenService service;

    private Staff activeStaff;

    @BeforeEach
    void setUp() throws Exception {
        // BUGFIX (was COOKIE-FALLBACK): JwtService now requires a
        // PermissionVersionService in its constructor so it can stamp the permVer
        // claim on every token. The version doesn't matter for the tests below
        // (they only assert structural things), so any non-null stub works.
        // PermissionVersionService.currentVersion() now returns a long (epoch ms),
        // not a LocalDateTime — see the AtomicLong refactor in
        // PermissionVersionService for context.
        PermissionVersionService versionService = mock(PermissionVersionService.class, withSettings().lenient());
        lenient().when(versionService.currentVersion()).thenReturn(java.time.LocalDateTime.now()
                .atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli());
        jwtService = new JwtService(versionService);
        // Inject @Value fields via reflection.
        setField(jwtService, "secretKey", java.util.Base64.getEncoder().encodeToString(
                "test-secret-32-bytes-of-data-AAAA".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        setField(jwtService, "jwtExpiration", 900000L);
        setField(jwtService, "refreshExpiration", 604800000L);
        // @InjectMocks only sees fields declared in RefreshTokenService — assign manually.
        inject(service, "jwtService", jwtService);

        AppRole role = AppRole.builder().id(1).name(RoleName.ADMIN).build();
        activeStaff = Staff.builder()
                .id(7).username("user").fullName("User")
                .isActive(true)
                .staffRoles(Set.of(StaffRole.builder().roleId(1).role(role).build()))
                .build();
    }

    @Test
    @DisplayName("Issue: persists SHA-256 hash, NEVER the raw token")
    void issue_persistsHashNotRaw() throws Exception {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken arg = inv.getArgument(0);
            arg.setId(1);
            return arg;
        });

        RefreshTokenService.IssuedRefreshToken issued = service.issue(activeStaff, "127.0.0.1");

        assertThat(issued.rawToken()).isNotBlank();
        assertThat(issued.rawToken()).contains("."); // UUID + UUID
        assertThat(issued.expiresAt()).isAfter(LocalDateTime.now());

        // Capture the persisted entity
        org.mockito.ArgumentCaptor<RefreshToken> captor = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        org.mockito.Mockito.verify(refreshTokenRepository).save(captor.capture());
        RefreshToken persisted = captor.getValue();

        assertThat(persisted.getTokenHash())
                .as("DB must only contain the hash, never the raw token")
                .doesNotContain(issued.rawToken())
                .hasSize(64); // SHA-256 = 32 bytes = 64 hex chars
        assertThat(persisted.getStaff()).isEqualTo(activeStaff);
        assertThat(persisted.getIssuedIp()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Rotate happy path: new pair + old revoked + replacedBy link")
    void rotate_happyPath_returnsNewPair() throws Exception {
        // We can't easily round-trip the hashed token without persistence.
        // For unit-level coverage we just verify that on a valid existing token,
        // the service invokes save(revoke) + save(new) and returns RotatedTokens.
        String secret = "this-is-a-freshly-issued-raw-token-uuid-pair";
        String hash = sha256(secret);

        RefreshToken existing = RefreshToken.builder()
                .id(100)
                .tokenHash(hash)
                .staff(activeStaff)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .issuedIp("127.0.0.1")
                .build();

        java.util.List<RefreshToken> savedEntities = new java.util.ArrayList<>();
        when(refreshTokenRepository.findByTokenHash(anyString())).thenAnswer(inv -> {
            String h = inv.getArgument(0, String.class);
            RefreshToken rt = new RefreshToken();
            rt.setId(h.equals(hash) ? 100 : 999);
            rt.setTokenHash(h);
            rt.setStaff(activeStaff);
            rt.setExpiresAt(LocalDateTime.now().plusDays(7));
            return Optional.of(rt);
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken arg = inv.getArgument(0);
            if (arg.getId() == null) arg.setId(999);
            savedEntities.add(arg);
            return arg;
        });

        Optional<RefreshTokenService.RotatedTokens> result = service.rotate(secret, "127.0.0.1");

        assertThat(result).isPresent();
        assertThat(result.get().accessToken()).isNotBlank();
        assertThat(result.get().refreshToken()).isNotBlank();
        assertThat(result.get().refreshToken()).isNotEqualTo(secret);

        // Verify the existing (id=100) token was marked revoked + linked
        RefreshToken theOld = savedEntities.stream()
                .filter(t -> t.getId() != null && t.getId() == 100)
                .findFirst().orElseThrow(() -> new AssertionError("Old token should have been saved at least once"));
        assertThat(theOld.getRevokedAt()).isNotNull();
        assertThat(theOld.getReplacedBy()).isNotNull();
    }

    @Test
    @DisplayName("Theft detection: re-using a revoked token → revoke all of staff's tokens")
    void rotate_reusedRevoked_revokesAll() throws Exception {
        String secret = "compromised-raw-token";
        String hash = sha256(secret);

        RefreshToken existing = RefreshToken.builder()
                .id(101)
                .tokenHash(hash)
                .staff(activeStaff)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revokedAt(LocalDateTime.now().minusMinutes(1)) // already revoked
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.revokeAllByStaffId(7)).thenReturn(3);

        Optional<RefreshTokenService.RotatedTokens> result = service.rotate(secret, "127.0.0.1");

        assertThat(result).isEmpty();
        org.mockito.Mockito.verify(refreshTokenRepository).revokeAllByStaffId(7);
    }

    @Test
    @DisplayName("Expired refresh token → empty result (no rotation)")
    void rotate_expired_returnsEmpty() throws Exception {
        String secret = "expired-raw-token";
        String hash = sha256(secret);

        RefreshToken existing = RefreshToken.builder()
                .id(102)
                .tokenHash(hash)
                .staff(activeStaff)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(existing));

        Optional<RefreshTokenService.RotatedTokens> result = service.rotate(secret, "127.0.0.1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Unknown refresh token → empty result")
    void rotate_unknown_returnsEmpty() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        Optional<RefreshTokenService.RotatedTokens> result = service.rotate("does-not-exist", "127.0.0.1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Inactive staff → empty result, token must NOT be rotated")
    void rotate_inactiveStaff_returnsEmpty() throws Exception {
        Staff inactive = Staff.builder()
                .id(99).username("x").isActive(false).staffRoles(Set.of()).build();
        String secret = "valid-but-inactive";
        String hash = sha256(secret);

        RefreshToken existing = RefreshToken.builder()
                .id(103)
                .tokenHash(hash)
                .staff(inactive)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(existing));

        Optional<RefreshTokenService.RotatedTokens> result = service.rotate(secret, "127.0.0.1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Logout: revoke marks revokedAt, returns true")
    void logout_revokesRefreshToken() throws Exception {
        String secret = "to-logout";
        String hash = sha256(secret);
        RefreshToken existing = RefreshToken.builder()
                .id(104)
                .tokenHash(hash)
                .staff(activeStaff)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean ok = service.revoke(secret);

        assertThat(ok).isTrue();
        assertThat(existing.getRevokedAt()).isNotNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static String sha256(String input) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}