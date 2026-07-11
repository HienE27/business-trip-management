package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.RefreshToken;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.RefreshTokenRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Manages refresh token lifecycle: hash-and-store, lookup, rotate, revoke.
 *
 * <p>Industry-standard rotation on use:
 * <ul>
 *   <li>Each /auth/refresh call validates the supplied refresh token,
 *       marks it as used (revokedAt = now) and issues a new pair.</li>
 *   <li>If a refresh token is used twice, we treat the second use as
 *       a token-theft indicator and revoke the whole chain via
 *       {@code revokeAllByStaffId}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final StaffRepository staffRepository;
    private final JwtService jwtService;

    /**
     * Generate + persist a fresh refresh token for the given staff.
     * Returns the RAW token — only chance the caller has to read it.
     */
    @Transactional
    public IssuedRefreshToken issue(Staff staff, String clientIp) {
        String rawToken = UUID.randomUUID().toString() + "." + UUID.randomUUID().toString();
        String hash = sha256(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(
                jwtService.getRefreshExpirationTime() * 1_000_000);

        RefreshToken entity = RefreshToken.builder()
                .tokenHash(hash)
                .staff(staff)
                .expiresAt(expiresAt)
                .issuedIp(clientIp)
                .build();
        refreshTokenRepository.save(entity);

        return new IssuedRefreshToken(rawToken, expiresAt);
    }

    /**
     * Rotate: validate the supplied raw token and emit a new pair (access + refresh).
     * If the token was already used, all of that staff's refresh tokens are revoked
     * (possible theft signal).
     *
     * @return new (accessToken, refreshToken) pair, or empty if the supplied token is invalid.
     */
    @Transactional
    public java.util.Optional<RotatedTokens> rotate(String rawRefreshToken, String clientIp) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return java.util.Optional.empty();
        }
        String hash = sha256(rawRefreshToken);
        var maybeExisting = refreshTokenRepository.findByTokenHash(hash);
        if (maybeExisting.isEmpty()) {
            log.warn("Refresh token not found — likely revoked or never issued");
            return java.util.Optional.empty();
        }
        RefreshToken existing = maybeExisting.get();

        // Theft detector: token was already revoked but is being used again.
        if (existing.getRevokedAt() != null) {
            log.warn("Reuse detected on refresh token id={} staff={} — revoking all tokens",
                    existing.getId(), existing.getStaff().getId());
            refreshTokenRepository.revokeAllByStaffId(existing.getStaff().getId());
            return java.util.Optional.empty();
        }

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.info("Refresh token expired id={}", existing.getId());
            return java.util.Optional.empty();
        }

        Staff staff = existing.getStaff();
        if (!Boolean.TRUE.equals(staff.getIsActive())) {
            log.info("Refresh token rejected — staff {} is inactive", staff.getId());
            return java.util.Optional.empty();
        }

        // Revoke the old one
        existing.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(existing);

        // Issue new refresh token
        IssuedRefreshToken newRefresh = issue(staff, clientIp);
        // Bind the new token as the rotation target of the old one (for forensics).
        var newEntity = refreshTokenRepository.findByTokenHash(sha256(newRefresh.rawToken()))
                .orElseThrow();
        existing.setReplacedBy(newEntity);
        refreshTokenRepository.save(existing);

        // Issue new access token
        List<String> roles = staff.getStaffRoles().stream()
                .map(sr -> sr.getRole() != null ? sr.getRole().getName().name() : null)
                .filter(r -> r != null)
                .toList();
        String newAccessToken = jwtService.generateToken(staff.getUsername(), roles);

        return java.util.Optional.of(new RotatedTokens(newAccessToken,
                jwtService.getExpirationTime(),
                newRefresh.rawToken(),
                newRefresh.expiresAt()));
    }

    @Transactional
    public boolean revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) return false;
        return refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
                .filter(t -> t.getRevokedAt() == null)
                .map(t -> {
                    t.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(t);
                    return true;
                })
                .orElse(false);
    }

    public record IssuedRefreshToken(String rawToken, LocalDateTime expiresAt) {}

    public record RotatedTokens(String accessToken,
                                long accessExpiresIn,
                                String refreshToken,
                                LocalDateTime refreshExpiresAt) {}

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory on every JVM; this branch is unreachable.
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }
}