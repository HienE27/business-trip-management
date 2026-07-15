package com.hospital.scheduler.service;

import com.hospital.scheduler.config.AuthCookieProperties;
import com.hospital.scheduler.config.RateLimitingFilter;
import com.hospital.scheduler.dto.AuthResponse;
import com.hospital.scheduler.dto.LoginRequest;
import com.hospital.scheduler.dto.request.ChangePasswordRequest;
import com.hospital.scheduler.dto.response.ResetPasswordResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.BusinessRuleException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.JwtService;
import com.hospital.scheduler.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // BUGFIX (was PWD-RANDOM): same alphabet as StaffMutationService / DataSeeder
    // — excludes visually ambiguous chars (0/O, 1/I/l) so the admin can read the
    // temp password back to the staff member over the phone.
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TEMP_PASSWORD_LENGTH = 10;

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StaffRepository staffRepository;
    private final RateLimitingFilter rateLimitingFilter;
    private final RefreshTokenService refreshTokenService;
    private final PermissionService permissionService;
    private final com.hospital.scheduler.security.AuthContextService authContextService;
    private final AuditHistoryService auditHistoryService;
    // BUGFIX (was BE#14): inject shared ClientIpResolver so login & rate-limit
    // agree on IPv6 + X-Forwarded-For validation.
    private final com.hospital.scheduler.security.ClientIpResolver clientIpResolver;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Staff staff = staffRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> {
                    rateLimitingFilter.recordFailedLogin(getClientIp(httpRequest));
                    return new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng");
                });

        if (!staff.getIsActive()) {
            throw new BadCredentialsException("Tài khoản của bạn đã bị vô hiệu hóa");
        }

        if (!passwordEncoder.matches(request.getPassword(), staff.getPasswordHash())) {
            rateLimitingFilter.recordFailedLogin(getClientIp(httpRequest));
            throw new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        List<String> roles = staff.getStaffRoles().stream()
                .map(sr -> sr.getRole() != null ? sr.getRole().getName().name() : null)
                .filter(r -> r != null)
                .collect(Collectors.toList());

        List<String> permissions = permissionService.permissionsOf(staff);

        String accessToken = jwtService.generateToken(staff.getUsername(), roles, permissions);
        RefreshTokenService.IssuedRefreshToken refresh =
                refreshTokenService.issue(staff, getClientIp(httpRequest));

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refresh.rawToken())
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .refreshExpiresIn(jwtService.getRefreshExpirationTime())
                .userId(Long.valueOf(staff.getId()))
                .username(staff.getUsername())
                .roles(roles)
                .permissions(permissions)
                .build();
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        return refreshTokenService.rotate(rawRefreshToken, clientIp)
                .map(rt -> AuthResponse.builder()
                        .token(rt.accessToken())
                        .refreshToken(rt.refreshToken())
                        .tokenType("Bearer")
                        .expiresIn(rt.accessExpiresIn())
                        .refreshExpiresIn(jwtService.getRefreshExpirationTime())
                        .username(jwtService.extractUsername(rt.accessToken()))
                        .roles(jwtService.extractRoles(rt.accessToken()))
                        .permissions(jwtService.extractPermissions(rt.accessToken()))
                        .build())
                .orElseThrow(() -> new BadCredentialsException("Refresh token không hợp lệ hoặc đã hết hạn"));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revoke(rawRefreshToken);
        }
    }

    private String getClientIp(HttpServletRequest req) {
        // BUGFIX (was BE#14): delegate to ClientIpResolver so the trusted-proxy
        // check handles IPv6 (::1, fc00::/7, fe80::/10, ::ffff:1.2.3.4) and
        // shared address space (100.64/10) correctly. The original hand-written
        // allowlist only matched the long-form IPv6 loopback string and missed
        // the canonical short-form.
        return clientIpResolver.resolve(req);
    }

    /**
     * Self-service password rotation for the currently authenticated user.
     *
     * <p>The supplied {@code currentPassword} is verified against the persisted
     * hash before {@code newPassword} is accepted. The active access token stays
     * valid — a password change is not a session killer, otherwise every
     * rotation would force the user through /login again.
     *
     * <p>Audit-logged with action type UPDATE and the same actor that initiated
     * the change. The actor is resolved via {@code AuthContextService} so it
     * always matches the principal in the security context.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        if (request == null) {
            throw new BadRequestException("Yêu cầu đổi mật khẩu không được trống");
        }
        Staff current = authContextService.getCurrentStaff();
        if (!passwordEncoder.matches(request.getCurrentPassword(), current.getPasswordHash())) {
            // Same wording / status as the login path so a probe can't
            // distinguish "wrong old password" from "unknown user".
            throw new BadCredentialsException("Mật khẩu hiện tại không đúng");
        }
        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new BadRequestException("Mật khẩu mới không được để trống");
        }
        if (request.getNewPassword().length() < 6) {
            throw new BadRequestException("Mật khẩu mới phải có ít nhất 6 ký tự");
        }
        if (passwordEncoder.matches(request.getNewPassword(), current.getPasswordHash())) {
            throw new BusinessRuleException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        String oldHash = current.getPasswordHash();
        current.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        staffRepository.save(current);
        auditHistoryService.logAction("staff", current.getId(),
                AuditHistory.ActionType.UPDATE,
                java.util.Map.of("passwordHash", "***"),
                java.util.Map.of("passwordHash", "***", "selfChange", true),
                current.getId());
        log.info("Password changed for staff id={}", current.getId());
    }

    /**
     * Admin-driven password reset for any staff member. Generates a fresh random
     * temporary password, hashes + persists it, and returns the plaintext so the
     * admin can hand it to the staff member. The plaintext is shown to the caller
     * EXACTLY ONCE — the API response is the only chance to surface it, because
     * we never store plaintext anywhere.
     *
     * <p>Audit-logged with action type UPDATE and the ADMIN's id as the actor.
     * Refresh tokens for the target staff are NOT revoked here — the staff
     * should be able to log in with the temp password right away. The first
     * legitimate login will issue a new access token bearing the latest permVer.
     */
    @Transactional
    public ResetPasswordResponse resetPassword(Integer targetStaffId) {
        Staff target = staffRepository.findById(targetStaffId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhân sự với ID: " + targetStaffId));
        Staff actor;
        try {
            actor = authContextService.getCurrentStaff();
        } catch (Exception ex) {
            // Background call paths (no security context) — accept as system action
            // but stamp audit with null actor so the lineage stays visible.
            actor = null;
        }
        Integer actorId = actor != null ? actor.getId() : null;
        if (actor != null && actor.getId().equals(target.getId())) {
            // Don't let an admin reset their own password this way — they should use
            // /auth/change-password (which forces re-auth). Resetting without
            // verifying the old password is wider blast radius than necessary.
            throw new BusinessRuleException(
                    "Vui lòng dùng chức năng đổi mật khẩu cho tài khoản của chính bạn");
        }
        String tempPassword = generateTempPassword();
        target.setPasswordHash(passwordEncoder.encode(tempPassword));
        staffRepository.save(target);
        auditHistoryService.logAction("staff", target.getId(),
                AuditHistory.ActionType.UPDATE,
                java.util.Map.of("passwordHash", "***"),
                java.util.Map.of("passwordHash", "***", "adminReset", true, "actorId", String.valueOf(actorId)),
                actorId);
        log.info("Admin {} reset password for staff id={}", actorId, target.getId());
        return ResetPasswordResponse.builder()
                .staffId(target.getId())
                .username(target.getUsername())
                .tempPassword(tempPassword)
                .message("Đã cấp lại mật khẩu tạm thời cho nhân sự. Vui lòng chuyển mật khẩu này cho nhân sự một cách an toàn.")
                .build();
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}