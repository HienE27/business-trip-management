package com.hospital.scheduler.digital.sandbox.repository;

import com.hospital.scheduler.digital.sandbox.entity.SandboxAssignment;
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
 * Repository for SandboxAssignment entities.
 */
@Repository
public interface SandboxAssignmentRepository extends JpaRepository<SandboxAssignment, Long> {

    /**
     * Find all assignments for a session.
     */
    List<SandboxAssignment> findBySession(SandboxSession session);

    /**
     * Find assignment by session and slot.
     */
    Optional<SandboxAssignment> findBySessionAndSlotId(SandboxSession session, Integer slotId);

    /**
     * Find all assignments for a session and staff.
     */
    List<SandboxAssignment> findBySessionAndStaffId(SandboxSession session, Integer staffId);

    /**
     * Find all assignments for a session on a specific date.
     */
    List<SandboxAssignment> findBySessionAndWorkDate(SandboxSession session, LocalDateTime workDate);

    /**
     * Find all simulated (generated) assignments for a session.
     */
    List<SandboxAssignment> findBySessionAndIsSimulatedTrue(SandboxSession session);

    /**
     * Find assignments with conflicts.
     */
    List<SandboxAssignment> findBySessionAndHasConflictTrue(SandboxSession session);

    /**
     * Count assignments for a session.
     */
    long countBySession(SandboxSession session);

    /**
     * Count simulated assignments.
     */
    long countBySessionAndIsSimulatedTrue(SandboxSession session);

    /**
     * Delete all assignments for a session.
     */
    @Modifying
    @Query("DELETE FROM SandboxAssignment s WHERE s.session = :session")
    int deleteBySession(@Param("session") SandboxSession session);

    /**
     * Update assignments after promotion (mark original ones as not simulated).
     */
    @Modifying
    @Query("UPDATE SandboxAssignment s SET s.isSimulated = false WHERE s.session = :session")
    int markAssignmentsAsOriginal(@Param("session") SandboxSession session);
}
