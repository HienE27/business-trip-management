package com.hospital.scheduler.digital.sandbox.repository;

import com.hospital.scheduler.digital.sandbox.entity.SandboxSnapshot;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SandboxSnapshot entities.
 */
@Repository
public interface SandboxSnapshotRepository extends JpaRepository<SandboxSnapshot, Long> {

    /**
     * Find all snapshots for a session (ordered by iteration).
     */
    List<SandboxSnapshot> findBySessionOrderByIterationAsc(SandboxSession session);

    /**
     * Find snapshots for a session with pagination (for large sessions).
     */
    Page<SandboxSnapshot> findBySessionOrderByIterationAsc(SandboxSession session, Pageable pageable);

    /**
     * Find a specific snapshot by session and iteration.
     */
    Optional<SandboxSnapshot> findBySessionAndIteration(SandboxSession session, Integer iteration);

    /**
     * Find the latest snapshot for a session.
     */
    Optional<SandboxSnapshot> findFirstBySessionOrderByIterationDesc(SandboxSession session);

    /**
     * Find all checkpoint snapshots for a session.
     */
    List<SandboxSnapshot> findBySessionAndIsCheckpointTrueOrderByIterationAsc(SandboxSession session);

    /**
     * Count snapshots for a session.
     */
    long countBySession(SandboxSession session);

    /**
     * Delete all snapshots for a session.
     */
    @Modifying
    @Query("DELETE FROM SandboxSnapshot s WHERE s.session = :session")
    int deleteBySession(@Param("session") SandboxSession session);

    /**
     * Delete snapshots for a session older than iteration.
     */
    @Modifying
    @Query("DELETE FROM SandboxSnapshot s WHERE s.session = :session AND s.iteration < :iteration")
    int deleteBySessionAndIterationBefore(@Param("session") SandboxSession session, @Param("iteration") Integer iteration);

    /**
     * Find snapshots in a range (for replay window).
     */
    @Query("SELECT s FROM SandboxSnapshot s WHERE s.session = :session AND s.iteration BETWEEN :start AND :end ORDER BY s.iteration ASC")
    List<SandboxSnapshot> findByIterationRange(
            @Param("session") SandboxSession session,
            @Param("start") Integer start,
            @Param("end") Integer end
    );

    /**
     * Get total storage size for a session.
     */
    @Query("SELECT COALESCE(SUM(s.memoryBytes), 0) FROM SandboxSnapshot s WHERE s.session = :session")
    Long getTotalMemoryBySession(@Param("session") SandboxSession session);

    /**
     * Get total storage size across all snapshots in one query — used by
     * {@code SandboxCleanupService.enforceStorageQuota} in place of findAll().
     */
    @Query("SELECT COALESCE(SUM(s.memoryBytes), 0) FROM SandboxSnapshot s")
    long sumAllMemoryBytes();

    /**
     * Per-session storage aggregation — used to evaluate cleanup candidates
     * in one round-trip instead of N calls to {@link #getTotalMemoryBySession}.
     */
    @Query("SELECT s.session.id, COALESCE(SUM(s.memoryBytes), 0) FROM SandboxSnapshot s GROUP BY s.session.id")
    List<Object[]> sumMemoryBytesGroupedBySession();

    /**
     * Find all snapshots with state data (for replay).
     */
    @Query("SELECT s FROM SandboxSnapshot s WHERE s.session = :session AND s.stateJson IS NOT NULL ORDER BY s.iteration ASC")
    List<SandboxSnapshot> findSnapshotsWithState(@Param("session") SandboxSession session);
}
