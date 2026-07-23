package com.hospital.scheduler.digital.sandbox.service;

import com.hospital.scheduler.digital.sandbox.domain.SandboxStatus;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import com.hospital.scheduler.digital.sandbox.repository.SandboxAssignmentRepository;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSessionRepository;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for cleaning up expired sandbox sessions.
 *
 * <p>Cleanup policies:
 * <ul>
 *   <li>TTL-based: Auto-delete after expiration</li>
 *   <li>Storage quota: Limit total storage usage</li>
 *   <li>Max sessions per user: Prevent resource exhaustion</li>
 *   <li>Manual cleanup: Admin can trigger cleanup</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxCleanupService {

    private final SandboxSessionRepository sessionRepository;
    private final SandboxSnapshotRepository snapshotRepository;
    private final SandboxAssignmentRepository assignmentRepository;

    // Configurable limits
    private static final int MAX_SESSIONS_PER_USER = 5;
    private static final int MAX_TOTAL_SESSIONS = 100;
    private static final long MAX_STORAGE_BYTES = 500 * 1024 * 1024; // 500MB
    private static final int CLEANUP_BATCH_SIZE = 10;

    private static final List<SandboxStatus> TERMINAL_STATUSES = List.of(
            SandboxStatus.PROMOTED,
            SandboxStatus.DELETED,
            SandboxStatus.EXPIRED
    );

    // ─── Scheduled Cleanup ──────────────────────────────────────────────────

    /**
     * Run cleanup every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    @Transactional
    public void scheduledCleanup() {
        log.debug("Running scheduled sandbox cleanup");

        try {
            // 1. Expire old sessions
            expireSessions();

            // 2. Clean up expired sessions
            cleanupExpiredSessions();

            // 3. Enforce storage quota
            enforceStorageQuota();

            log.debug("Scheduled cleanup completed");
        } catch (Exception e) {
            log.error("Error during scheduled cleanup", e);
        }
    }

    /**
     * Expire sessions past their TTL.
     */
    @Transactional
    public int expireSessions() {
        List<SandboxSession> expired = sessionRepository.findExpiredSessions(
                LocalDateTime.now(),
                TERMINAL_STATUSES
        );

        for (SandboxSession session : expired) {
            session.setStatus(SandboxStatus.EXPIRED);
            sessionRepository.save(session);
        }

        if (!expired.isEmpty()) {
            log.info("Expired {} sandbox sessions", expired.size());
        }

        return expired.size();
    }

    /**
     * Delete expired session data.
     */
    @Transactional
    public int cleanupExpiredSessions() {
        List<SandboxSession> sessions = sessionRepository.findByStatusOrderByCreatedAtDesc(SandboxStatus.EXPIRED);

        int deletedCount = 0;
        for (SandboxSession session : sessions) {
            if (!session.getIsPinned()) {
                deleteSessionData(session);
                deletedCount++;
            }
        }

        if (deletedCount > 0) {
            log.info("Cleaned up {} expired sessions", deletedCount);
        }

        return deletedCount;
    }

    // ─── Manual Cleanup ───────────────────────────────────────────────────

    /**
     * Delete all sessions for a user.
     */
    @Transactional
    public int deleteAllByUser(String username) {
        List<SandboxSession> sessions = sessionRepository.findByCreatedByOrderByCreatedAtDesc(username);
        int deleted = 0;

        for (SandboxSession session : sessions) {
            if (!session.isTerminal()) {
                deleteSessionData(session);
                deleted++;
            }
        }

        log.info("Deleted {} sessions for user {}", deleted, username);
        return deleted;
    }

    /**
     * Delete all sessions for a period.
     */
    @Transactional
    public int deleteAllByPeriod(Integer periodId) {
        List<SandboxSession> sessions = sessionRepository.findBySourcePeriodIdOrderByCreatedAtDesc(periodId);
        int deleted = 0;

        for (SandboxSession session : sessions) {
            if (!session.isTerminal()) {
                deleteSessionData(session);
                deleted++;
            }
        }

        log.info("Deleted {} sessions for period {}", deleted, periodId);
        return deleted;
    }

    /**
     * Force cleanup a specific session.
     */
    @Transactional
    public void forceDelete(Long sessionId) {
        SandboxSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        deleteSessionData(session);
        log.info("Force deleted session {}", sessionId);
    }

    // ─── Storage Quota ──────────────────────────────────────────────────────

    /**
     * Enforce storage quota by deleting oldest non-pinned sessions.
     */
    @Transactional
    public int enforceStorageQuota() {
        // BUGFIX: was findAll().sum() — loads every snapshot row into memory
        // just to compute a total. Now SUM aggregation runs server-side.
        long totalSize = snapshotRepository.sumAllMemoryBytes();

        if (totalSize <= MAX_STORAGE_BYTES) {
            return 0;
        }

        log.warn("Storage quota exceeded ({} bytes). Running cleanup.", totalSize);

        List<SandboxSession> sessions = sessionRepository.findSessionsForCleanup(TERMINAL_STATUSES);

        // BUGFIX: was one query per session in the loop. Now a single GROUP BY
        // returns sizes for every session, indexed by session id.
        java.util.Map<Long, Long> sizeBySessionId = new java.util.HashMap<>();
        for (Object[] row : snapshotRepository.sumMemoryBytesGroupedBySession()) {
            sizeBySessionId.put((Long) row[0], (Long) row[1]);
        }

        int deleted = 0;
        long freedBytes = 0;

        for (SandboxSession session : sessions) {
            if (session.getIsPinned() || session.isTerminal()) {
                continue;
            }

            long sessionSize = sizeBySessionId.getOrDefault(session.getId(), 0L);
            deleteSessionData(session);
            freedBytes += sessionSize;
            deleted++;

            if (totalSize - freedBytes <= MAX_STORAGE_BYTES * 0.8) { // Stop at 80% capacity
                break;
            }
        }

        log.info("Freed {} bytes by deleting {} sessions", freedBytes, deleted);
        return deleted;
    }

    /**
     * Check if user has reached session limit.
     */
    public boolean hasReachedUserLimit(String username) {
        long activeCount = sessionRepository.countActiveSessionsByUser(username, TERMINAL_STATUSES);
        return activeCount >= MAX_SESSIONS_PER_USER;
    }

    /**
     * Get cleanup statistics.
     */
    public CleanupStats getCleanupStats() {
        long totalSessions = sessionRepository.count();
        long activeSessions = sessionRepository.findByStatusOrderByCreatedAtDesc(SandboxStatus.RUNNING).size();
        long expiredSessions = sessionRepository.findByStatusOrderByCreatedAtDesc(SandboxStatus.EXPIRED).size();
        long completedSessions = sessionRepository.findByStatusOrderByCreatedAtDesc(SandboxStatus.COMPLETED).size();

        return new CleanupStats(
                totalSessions,
                activeSessions,
                expiredSessions,
                completedSessions,
                MAX_SESSIONS_PER_USER,
                MAX_STORAGE_BYTES
        );
    }

    // ─── Internal Helpers ──────────────────────────────────────────────────

    /**
     * Delete all data for a session.
     */
    private void deleteSessionData(SandboxSession session) {
        // Delete snapshots first (foreign key)
        snapshotRepository.deleteBySession(session);

        // Delete assignments
        assignmentRepository.deleteBySession(session);

        // Mark session as deleted
        session.setStatus(SandboxStatus.DELETED);
        sessionRepository.save(session);

        log.debug("Deleted all data for session {}", session.getSessionKey());
    }

    // ─── Stats Record ─────────────────────────────────────────────────────

    public record CleanupStats(
            long totalSessions,
            long activeSessions,
            long expiredSessions,
            long completedSessions,
            int maxSessionsPerUser,
            long maxStorageBytes
    ) {}
}
