package com.hospital.scheduler.service;

import com.hospital.scheduler.config.AuthCookieProperties;
import com.hospital.scheduler.config.RateLimitingFilter;
import com.hospital.scheduler.dto.AuthResponse;
import com.hospital.scheduler.dto.LoginRequest;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.JwtService;
import com.hospital.scheduler.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StaffRepository staffRepository;
    private final RateLimitingFilter rateLimitingFilter;
    private final RefreshTokenService refreshTokenService;
    private final PermissionService permissionService;
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
}