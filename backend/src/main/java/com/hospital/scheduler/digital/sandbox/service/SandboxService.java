package com.hospital.scheduler.digital.sandbox.service;

import com.hospital.scheduler.digital.sandbox.domain.SandboxStatus;
import com.hospital.scheduler.digital.sandbox.domain.SimulationMode;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import com.hospital.scheduler.digital.sandbox.repository.SandboxAssignmentRepository;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSessionRepository;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Main service for sandbox lifecycle management.
 *
 * <p>Handles:
 * <ul>
 *   <li>Session creation and cloning</li>
 *   <li>Session status transitions</li>
 *   <li>Session retrieval and listing</li>
 *   <li>Session cleanup and deletion</li>
 * </ul>
 *
 * <p>Status transitions:
 * <pre>
 * CREATED → CLONING → READY → RUNNING ⇄ PAUSED → COMPLETED
 *                 ↓                           ↓
 *              FAILED                      EXPIRED
 *                                             ↓
 *                                         PROMOTED
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxService {

    private final SandboxSessionRepository sessionRepository;
    private final SandboxSnapshotRepository snapshotRepository;
    private final SandboxAssignmentRepository assignmentRepository;
    private final SandboxCloneService cloneService;
    private final SandboxCleanupService cleanupService;

    // ─── Session Creation ─────────────────────────────────────────────────────

    /**
     * Create a new sandbox by cloning a period.
     *
     * @param periodId Source period to clone
     * @param profileId Profile to use for simulation
     * @param createdBy User creating the sandbox
     * @param name Optional session name
     * @param simulationMode Simulation mode
     * @param ttlHours TTL in hours
     * @return Created sandbox session
     */
    @Transactional
    public SandboxSession createSandbox(
            Integer periodId,
            Long profileId,
            String createdBy,
            String name,
            SimulationMode simulationMode,
            Integer ttlHours
    ) {
        log.info("Creating sandbox for period {} by user {}", periodId, createdBy);

        // Check user limit
        long activeCount = sessionRepository.countActiveSessionsByUser(
                createdBy,
                List.of(SandboxStatus.PROMOTED, SandboxStatus.DELETED, SandboxStatus.EXPIRED)
        );
        if (activeCount >= 5) {
            throw new IllegalStateException("Maximum 5 active sandboxes per user. Please delete or promote existing ones.");
        }

        // Create and clone
        SandboxSession session = cloneService.clonePeriodToSandbox(
                periodId,
                profileId,
                createdBy,
                name,
                ttlHours
        );

        // Update simulation mode
        session.setSimulationMode(simulationMode);
        return sessionRepository.save(session);
    }

    // ─── Session Retrieval ───────────────────────────────────────────────────

    /**
     * Get session by key.
     */
    @Transactional(readOnly = true)
    public Optional<SandboxSession> getByKey(String sessionKey) {
        return sessionRepository.findBySessionKey(sessionKey);
    }

    /**
     * Get session by ID.
     */
    @Transactional(readOnly = true)
    public Optional<SandboxSession> getById(Long id) {
        return sessionRepository.findById(id);
    }

    /**
     * Get all sessions for a user.
     */
    @Transactional(readOnly = true)
    public List<SandboxSession> getByUser(String createdBy) {
        return sessionRepository.findByCreatedByOrderByCreatedAtDesc(createdBy);
    }

    /**
     * Get all sessions for a period.
     */
    @Transactional(readOnly = true)
    public List<SandboxSession> getByPeriod(Integer periodId) {
        return sessionRepository.findBySourcePeriodIdOrderByCreatedAtDesc(periodId);
    }

    /**
     * Get sessions by status.
     */
    @Transactional(readOnly = true)
    public List<SandboxSession> getByStatus(SandboxStatus status) {
        return sessionRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    /**
     * Get active sessions (running, paused, ready).
     */
    @Transactional(readOnly = true)
    public List<SandboxSession> getActiveSessions() {
        return sessionRepository.findByStatusOrderByCreatedAtDesc(SandboxStatus.RUNNING);
    }

    /**
     * Get all sessions (admin).
     */
    @Transactional(readOnly = true)
    public List<SandboxSession> getAll() {
        return sessionRepository.findAll();
    }

    // ─── Session Control ─────────────────────────────────────────────────────

    /**
     * Start simulation.
     */
    @Transactional
    public SandboxSession start(String sessionKey) {
        SandboxSession session = getOrThrow(sessionKey);
        if (!session.canStart()) {
            throw new IllegalStateException("Cannot start session in status: " + session.getStatus());
        }
        session.setStatus(SandboxStatus.RUNNING);
        return sessionRepository.save(session);
    }

    /**
     * Pause simulation.
     */
    @Transactional
    public SandboxSession pause(String sessionKey) {
        SandboxSession session = getOrThrow(sessionKey);
        if (!session.canPause()) {
            throw new IllegalStateException("Cannot pause session in status: " + session.getStatus());
        }
        session.setStatus(SandboxStatus.PAUSED);
        return sessionRepository.save(session);
    }

    /**
     * Resume simulation.
     */
    @Transactional
    public SandboxSession resume(String sessionKey) {
        SandboxSession session = getOrThrow(sessionKey);
        if (!session.canResume()) {
            throw new IllegalStateException("Cannot resume session in status: " + session.getStatus());
        }
        session.setStatus(SandboxStatus.RUNNING);
        return sessionRepository.save(session);
    }

    /**
     * Cancel simulation.
     */
    @Transactional
    public SandboxSession cancel(String sessionKey) {
        SandboxSession session = getOrThrow(sessionKey);
        if (!session.isActive() && session.getStatus() != SandboxStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel session in status: " + session.getStatus());
        }
        session.setStatus(SandboxStatus.FAILED);
        session.setErrorMessage("Cancelled by user");
        return sessionRepository.save(session);
    }

    /**
     * Mark session as completed.
     */
    @Transactional
    public SandboxSession complete(String sessionKey, double bestScore, double coverageRate, double fairnessCv, int violations) {
        SandboxSession session = getOrThrow(sessionKey);
        session.setStatus(SandboxStatus.COMPLETED);
        session.setBestScore(bestScore);
        session.setCoverageRate(coverageRate);
        session.setFairnessCv(fairnessCv);
        session.setViolations(violations);
        return sessionRepository.save(session);
    }

    /**
     * Mark session as failed.
     */
    @Transactional
    public SandboxSession fail(String sessionKey, String errorMessage) {
        SandboxSession session = getOrThrow(sessionKey);
        session.setStatus(SandboxStatus.FAILED);
        session.setErrorMessage(errorMessage);
        return sessionRepository.save(session);
    }

    /**
     * Update session metrics.
     */
    @Transactional
    public SandboxSession updateMetrics(String sessionKey, int iterations, double currentScore) {
        SandboxSession session = getOrThrow(sessionKey);
        session.setIterations(iterations);
        session.setBestScore(Math.max(session.getBestScore() != null ? session.getBestScore() : 0, currentScore));
        return sessionRepository.save(session);
    }

    // ─── Session Deletion ─────────────────────────────────────────────────────

    /**
     * Delete a session and all its data.
     */
    @Transactional
    public void delete(String sessionKey) {
        SandboxSession session = getOrThrow(sessionKey);
        deleteSession(session);
    }

    /**
     * Delete a session by ID.
     */
    @Transactional
    public void deleteById(Long id) {
        SandboxSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));
        deleteSession(session);
    }

    private void deleteSession(SandboxSession session) {
        log.info("Deleting sandbox session: {}", session.getSessionKey());

        // Delete snapshots
        snapshotRepository.deleteBySession(session);

        // Delete assignments
        assignmentRepository.deleteBySession(session);

        // Delete session
        session.setStatus(SandboxStatus.DELETED);
        sessionRepository.save(session);
    }

    /**
     * Soft delete (mark as deleted).
     */
    @Transactional
    public SandboxSession softDelete(String sessionKey) {
        SandboxSession session = getOrThrow(sessionKey);
        session.setStatus(SandboxStatus.DELETED);
        return sessionRepository.save(session);
    }

    /**
     * Pin/unpin session from auto-cleanup.
     */
    @Transactional
    public SandboxSession togglePin(String sessionKey) {
        SandboxSession session = getOrThrow(sessionKey);
        session.setIsPinned(!session.getIsPinned());
        return sessionRepository.save(session);
    }

    /**
     * Extend session TTL.
     */
    @Transactional
    public SandboxSession extendTtl(String sessionKey, int additionalHours) {
        SandboxSession session = getOrThrow(sessionKey);
        LocalDateTime newExpiry = session.getExpiresAt().plusHours(additionalHours);
        session.setExpiresAt(newExpiry);
        return sessionRepository.save(session);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private SandboxSession getOrThrow(String sessionKey) {
        return sessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("Sandbox session not found: " + sessionKey));
    }
}
