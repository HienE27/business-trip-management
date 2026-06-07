package com.hospital.scheduler.service;

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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final StaffRepository staffRepository;

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Staff staff = staffRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng"));

        if (!staff.getIsActive()) {
            throw new BadCredentialsException("Tài khoản của bạn đã bị vô hiệu hóa");
        }

        if (!passwordEncoder.matches(request.getPassword(), staff.getPasswordHash())) {
            throw new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        List<String> roles = staff.getStaffRoles().stream()
                .map(sr -> sr.getRole() != null ? sr.getRole().getName() : null)
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
}
