package com.hospital.scheduler.digital.sandbox.repository;

import com.hospital.scheduler.digital.sandbox.domain.SandboxStatus;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for SandboxSession entities.
 */
@Repository
public interface SandboxSessionRepository extends JpaRepository<SandboxSession, Long> {

    /**
     * Find by session key.
     */
    Optional<SandboxSession> findBySessionKey(String sessionKey);

    /**
     * Find all sessions for a user.
     */
    List<SandboxSession> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * Find sessions by status.
     */
    List<SandboxSession> findByStatusOrderByCreatedAtDesc(SandboxStatus status);

    /**
     * Find sessions by source period.
     */
    List<SandboxSession> findBySourcePeriodIdOrderByCreatedAtDesc(Integer sourcePeriodId);

    /**
     * Find expired sessions that should be cleaned up.
     */
    @Query("SELECT s FROM SandboxSession s WHERE s.expiresAt < :now AND s.isPinned = false AND s.status NOT IN :terminalStatuses")
    List<SandboxSession> findExpiredSessions(
            @Param("now") LocalDateTime now,
            @Param("terminalStatuses") List<SandboxStatus> terminalStatuses
    );

    /**
     * Find sessions with their snapshots count.
     */
    @Query("SELECT s FROM SandboxSession s LEFT JOIN s.sessionKey WHERE s.id = :id")
    Optional<SandboxSession> findByIdWithSnapshots(@Param("id") Long id);

    /**
     * Count active sessions for a user.
     */
    @Query("SELECT COUNT(s) FROM SandboxSession s WHERE s.createdBy = :username AND s.status NOT IN :terminalStatuses")
    long countActiveSessionsByUser(
            @Param("username") String username,
            @Param("terminalStatuses") List<SandboxStatus> terminalStatuses
    );

    /**
     * Update session status.
     */
    @Modifying
    @Query("UPDATE SandboxSession s SET s.status = :status, s.updatedAt = :now WHERE s.id = :id")
    int updateStatus(@Param("id") Long id, @Param("status") SandboxStatus status, @Param("now") LocalDateTime now);

    /**
     * Delete sessions by status.
     */
    @Modifying
    @Query("DELETE FROM SandboxSession s WHERE s.status = :status")
    int deleteByStatus(@Param("status") SandboxStatus status);

    /**
     * Find sessions exceeding storage quota (most recent first).
     */
    @Query("SELECT s FROM SandboxSession s WHERE s.status NOT IN :terminalStatuses ORDER BY s.createdAt ASC")
    List<SandboxSession> findSessionsForCleanup(
            @Param("terminalStatuses") List<SandboxStatus> terminalStatuses
    );
}
