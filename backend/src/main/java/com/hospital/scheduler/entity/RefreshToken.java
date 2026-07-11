package com.hospital.scheduler.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Persistent refresh token record.
 *
 * <p>An issued refresh token is stored as a SHA-256 hash of the raw value
 * (the raw value is only ever returned to the client once at issuance).
 * Each token has an absolute expiry and a {@code revokedAt} timestamp —
 * a non-null {@code revokedAt} makes the token unusable regardless of expiry.
 *
 * <p>Why we need a table:
 * <ul>
 *   <li>Stateless JWT alone can't be revoked (a stolen token stays valid
 *       until it expires). Storing the refresh token lets us revoke it
 *       on logout or when a staff is deactivated.</li>
 *   <li>The "rotation on use" pattern (recommended by OWASP) — every
 *       successful refresh issues a NEW refresh token and revokes the old
 *       one, limiting the blast radius if a refresh token leaks.</li>
 * </ul>
 */
@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "idx_refresh_token_hash", columnList = "token_hash"),
        @Index(name = "idx_refresh_token_staff", columnList = "staff_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** SHA-256 hash of the raw token bytes. Never store the raw token. */
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * IP that the token was issued to / last used from.
     * Used by the {@code REFRESH} endpoint to detect token theft (mismatch → revoke).
     */
    @Column(name = "issued_ip", length = 64)
    private String issuedIp;

    /**
     * Replaces this token on every successful rotation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private RefreshToken replacedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }
}