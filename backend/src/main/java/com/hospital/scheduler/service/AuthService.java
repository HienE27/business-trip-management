package com.hospital.scheduler.service;

import com.hospital.scheduler.config.AuthCookieProperties;
import com.hospital.scheduler.config.RateLimitingFilter;
import com.hospital.scheduler.dto.AuthResponse;
import com.hospital.scheduler.dto.LoginRequest;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.JwtService;
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

    @Transactional(readOnly = true)
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

        String token = jwtService.generateToken(staff.getUsername(), roles);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .userId(Long.valueOf(staff.getId()))
                .username(staff.getUsername())
                .roles(roles)
                .build();
    }

    private String getClientIp(HttpServletRequest req) {
        // Only trust X-Forwarded-For when the request originates from a known/trusted proxy.
        // For direct requests or untrusted proxies, fall back to the direct socket address.
        String xf = req.getHeader("X-Forwarded-For");
        String directIp = req.getRemoteAddr();
        if (xf == null || xf.isBlank()) {
            return directIp;
        }
        // Whitelist of known proxy IPs — only use X-Forwarded-For from these sources.
        // If not from a trusted proxy, return the direct IP to prevent spoofing.
        String firstIp = xf.split(",")[0].trim();
        return isTrustedProxy(directIp) ? firstIp : directIp;
    }

    private boolean isTrustedProxy(String ip) {
        return "127.0.0.1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || ip.startsWith("10.")   // RFC 1918 private
                || ip.startsWith("172.16.") || ip.startsWith("172.17.")
                || ip.startsWith("172.18.") || ip.startsWith("172.19.")
                || ip.startsWith("172.20.") || ip.startsWith("172.21.")
                || ip.startsWith("172.22.") || ip.startsWith("172.23.")
                || ip.startsWith("172.24.") || ip.startsWith("172.25.")
                || ip.startsWith("172.26.") || ip.startsWith("172.27.")
                || ip.startsWith("172.28.") || ip.startsWith("172.29.")
                || ip.startsWith("172.30.") || ip.startsWith("172.31.")
                || ip.startsWith("192.168."); // RFC 1918 private
    }
}
