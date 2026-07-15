package com.hospital.scheduler.service;

import com.hospital.scheduler.config.RateLimitingFilter;
import com.hospital.scheduler.dto.request.ChangePasswordRequest;
import com.hospital.scheduler.dto.response.ResetPasswordResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.RoleName;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.BusinessRuleException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.security.ClientIpResolver;
import com.hospital.scheduler.security.JwtService;
import com.hospital.scheduler.security.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the password-management surface on {@link AuthService}:
 * <ul>
 *   <li>{@code changePassword}: self-service rotation (verify-old, set-new).</li>
 *   <li>{@code resetPassword}: admin drives a temp password for any staff.</li>
 * </ul>
 *
 * <p>Both methods must audit-log with the actor resolved from
 * {@link AuthContextService}, and the temp password is exposed to the caller
 * exactly once (via the response payload).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService - changePassword / resetPassword")
class AuthServicePasswordTest {

    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StaffRepository staffRepository;
    @Mock private RateLimitingFilter rateLimitingFilter;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PermissionService permissionService;
    @Mock private AuthContextService authContextService;
    @Mock private AuditHistoryService auditHistoryService;
    @Mock private ClientIpResolver clientIpResolver;

    @InjectMocks private AuthService authService;

    private Staff currentStaff;
    private Staff targetStaff;

    @BeforeEach
    void setUp() {
        currentStaff = Staff.builder()
                .id(1)
                .username("admin")
                .passwordHash("$2a$10$OLD_HASH_admin")
                .isActive(true)
                .staffRoles(Set.of())
                .build();
        targetStaff = Staff.builder()
                .id(7)
                .username("user7")
                .passwordHash("$2a$10$OLD_HASH_user7")
                .isActive(true)
                .staffRoles(Set.of())
                .build();
    }

    // ── changePassword ───────────────────────────────────────────────────────

    @Test
    @DisplayName("changePassword: hashes new password, updates staff, logs audit with current actor")
    void changePassword_happyPath() {
        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .currentPassword("oldPass123")
                .newPassword("newPass123")
                .build();
        when(authContextService.getCurrentStaff()).thenReturn(currentStaff);
        when(passwordEncoder.matches("oldPass123", "$2a$10$OLD_HASH_admin")).thenReturn(true);
        when(passwordEncoder.matches("newPass123", "$2a$10$OLD_HASH_admin")).thenReturn(false);
        when(passwordEncoder.encode("newPass123")).thenReturn("$2a$10$NEW_HASH_admin");

        authService.changePassword(req);

        // passwordHash must be the freshly-encoded value, not the old one.
        assertThat(currentStaff.getPasswordHash()).isEqualTo("$2a$10$NEW_HASH_admin");
        verify(staffRepository).save(currentStaff);

        // audit fired with the current user as actor (not anonymous, not admin-bot).
        ArgumentCaptor<AuditHistory.ActionType> actionCap =
                ArgumentCaptor.forClass(AuditHistory.ActionType.class);
        verify(auditHistoryService).logAction(eq("staff"), eq(1), actionCap.capture(),
                any(), any(), eq(1));
        assertThat(actionCap.getValue()).isEqualTo(AuditHistory.ActionType.UPDATE);
    }

    @Test
    @DisplayName("changePassword: wrong current password → BadCredentials, no save, no audit")
    void changePassword_wrongCurrent_rejectedWithoutPersist() {
        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .currentPassword("WRONG")
                .newPassword("newPass123")
                .build();
        when(authContextService.getCurrentStaff()).thenReturn(currentStaff);
        when(passwordEncoder.matches("WRONG", "$2a$10$OLD_HASH_admin")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("không đúng");

        verify(staffRepository, never()).save(any());
        verify(auditHistoryService, never()).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("changePassword: new password == current → BusinessRule 422 (no save, no audit)")
    void changePassword_sameAsCurrent_rejected() {
        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .currentPassword("samePass")
                .newPassword("samePass")
                .build();
        when(authContextService.getCurrentStaff()).thenReturn(currentStaff);
        when(passwordEncoder.matches("samePass", "$2a$10$OLD_HASH_admin")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("khác mật khẩu hiện tại");

        verify(staffRepository, never()).save(any());
        verify(auditHistoryService, never()).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("changePassword: new password shorter than 6 chars → BadRequest 400")
    void changePassword_tooShort_rejected() {
        ChangePasswordRequest req = ChangePasswordRequest.builder()
                .currentPassword("oldPass123")
                .newPassword("abc")
                .build();
        when(authContextService.getCurrentStaff()).thenReturn(currentStaff);
        when(passwordEncoder.matches("oldPass123", "$2a$10$OLD_HASH_admin")).thenReturn(true);
        when(passwordEncoder.matches("abc", "$2a$10$OLD_HASH_admin")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ít nhất 6");
    }

    @Test
    @DisplayName("changePassword: null request → BadRequest 400")
    void changePassword_null_rejected() {
        assertThatThrownBy(() -> authService.changePassword(null))
                .isInstanceOf(BadRequestException.class);
    }

    // ── resetPassword ────────────────────────────────────────────────────────

    @Test
    @DisplayName("resetPassword: returns plaintext temp + hashes + saves + audit with admin actor")
    void resetPassword_returnsTempPassword() {
        when(authContextService.getCurrentStaff()).thenReturn(currentStaff);
        when(staffRepository.findById(7)).thenReturn(Optional.of(targetStaff));
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$NEW_HASH_user7");

        ResetPasswordResponse resp = authService.resetPassword(7);

        // API contract: caller gets the plaintext exactly once.
        assertThat(resp.getStaffId()).isEqualTo(7);
        assertThat(resp.getUsername()).isEqualTo("user7");
        assertThat(resp.getTempPassword()).isNotBlank().hasSize(10);
        assertThat(resp.getMessage()).contains("an toàn");

        // Persistence side
        assertThat(targetStaff.getPasswordHash()).isEqualTo("$2a$10$NEW_HASH_user7");
        verify(staffRepository).save(targetStaff);

        // Audit fired with the ADMIN's id (1) as actor — proves who flipped the
        // password. Refreshtoken is NOT revoked (staff can log in with the temp).
        verify(auditHistoryService, times(1)).logAction(
                eq("staff"), eq(7), eq(AuditHistory.ActionType.UPDATE),
                any(), any(), eq(1));
        verify(refreshTokenService, never()).revoke(any());
    }

    @Test
    @DisplayName("resetPassword: unknown staff id → ResourceNotFound 404, no audit")
    void resetPassword_unknownStaff_rejected() {
        when(authContextService.getCurrentStaff()).thenReturn(currentStaff);
        when(staffRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(99))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(staffRepository, never()).save(any());
        verify(auditHistoryService, never()).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("resetPassword: caller resets their own password → BusinessRule 422 (use changePassword)")
    void resetPassword_selfReset_rejected() {
        when(authContextService.getCurrentStaff()).thenReturn(currentStaff);
        when(staffRepository.findById(1)).thenReturn(Optional.of(currentStaff));

        assertThatThrownBy(() -> authService.resetPassword(1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("chính bạn");

        verify(staffRepository, never()).save(any());
        verify(auditHistoryService, never()).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("resetPassword: temp password uses ambiguous-free alphabet (no 0/O/1/l)")
    void resetPassword_alphabetIsReadable() {
        when(authContextService.getCurrentStaff()).thenReturn(currentStaff);
        when(staffRepository.findById(7)).thenReturn(Optional.of(targetStaff));
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$NEW_HASH_user7");

        // Run several times to make sure every char comes from the allowed set.
        for (int i = 0; i < 50; i++) {
            ResetPasswordResponse resp = authService.resetPassword(7);
            assertThat(resp.getTempPassword())
                    .as("iteration %d", i)
                    .matches("^[A-HJ-NP-Za-hjkmnp-z2-9]+$");
        }
    }
}
