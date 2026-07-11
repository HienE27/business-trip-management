package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Mark every non-revoked token belonging to a staff as revoked. Called
     * when the staff is deactivated, password changes, or "logout everywhere"
     * is requested.
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = CURRENT_TIMESTAMP " +
           "WHERE r.staff.id = :staffId AND r.revokedAt IS NULL")
    int revokeAllByStaffId(@Param("staffId") Integer staffId);
}