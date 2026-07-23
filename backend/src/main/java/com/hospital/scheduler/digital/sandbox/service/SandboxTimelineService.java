package com.hospital.scheduler.digital.sandbox.service;

import com.hospital.scheduler.digital.sandbox.controller.SandboxTimelineController;
import com.hospital.scheduler.digital.sandbox.domain.SandboxStatus;
import com.hospital.scheduler.digital.sandbox.dto.TimelineEvent;
import com.hospital.scheduler.digital.sandbox.dto.TimelineEventType;
import com.hospital.scheduler.digital.sandbox.entity.SandboxAssignment;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSnapshot;
import com.hospital.scheduler.digital.sandbox.repository.SandboxAssignmentRepository;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSessionRepository;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSnapshotRepository;
import com.hospital.scheduler.scheduling.score.MutableScore;
import com.hospital.scheduler.scheduling.solution.IncrementalState;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing sandbox timeline events.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Record events during simulation</li>
 *   <li>Stream events via SSE</li>
 *   <li>Create snapshots at checkpoints</li>
 *   <li>Persist timeline for replay</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxTimelineService {

    private final SandboxSessionRepository sessionRepository;
    private final SandboxSnapshotRepository snapshotRepository;
    private final SandboxAssignmentRepository assignmentRepository;
    private final SandboxTimelineController timelineController;

    /** In-memory event buffer for batching. */
    private final ConcurrentHashMap<String, TimelineEventBuffer> eventBuffers = new ConcurrentHashMap<>();

    /** Checkpoint interval (every N iterations). */
    private static final int CHECKPOINT_INTERVAL = 10;

    // ─── Event Recording ────────────────────────────────────────────────────────

    /**
     * Record and broadcast a move event.
     */
    public void recordMove(
            String sessionKey,
            int iteration,
            String moveType,
            Integer staffId,
            String staffName,
            Integer slotId,
            boolean accepted,
            double scoreDelta,
            Map<String, Object> constraintDeltas
    ) {
        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) {
            log.warn("Session not found: {}", sessionKey);
            return;
        }

        TimelineEvent event = TimelineEvent.builder()
                .eventType(accepted ? TimelineEventType.MOVE_ACCEPTED : TimelineEventType.MOVE_REJECTED)
                .iteration(iteration)
                .timestamp(LocalDateTime.now())
                .elapsedMs(System.currentTimeMillis() - session.getCreatedAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
                .score(session.getBestScore() != null ? session.getBestScore() : 0)
                .coverage(session.getCoverageRate() != null ? session.getCoverageRate() : 0)
                .fairnessCv(session.getFairnessCv() != null ? session.getFairnessCv() : 0)
                .hardViolations(session.getViolations() != null ? session.getViolations() : 0)
                .moveType(moveType)
                .staffId(staffId)
                .staffName(staffName)
                .slotId(slotId)
                .accepted(accepted)
                .scoreDelta(scoreDelta)
                .constraintDeltas(constraintDeltas)
                .build();

        // Save to buffer for batch persistence
        getBuffer(sessionKey).addEvent(event);

        // Stream to SSE
        timelineController.publishEvent(sessionKey, event);

        // Create checkpoint periodically
        if (iteration % CHECKPOINT_INTERVAL == 0) {
            createSnapshot(sessionKey, event);
        }
    }

    /**
     * Record a score improvement event.
     */
    public void recordScoreImproved(
            String sessionKey,
            int iteration,
            double newScore,
            double coverage,
            double fairnessCv,
            int violations
    ) {
        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) return;

        // Update session metrics
        session.setBestScore(newScore);
        session.setCoverageRate(coverage);
        session.setFairnessCv(fairnessCv);
        session.setViolations(violations);
        session.setIterations(iteration);
        sessionRepository.save(session);

        TimelineEvent event = TimelineEvent.builder()
                .eventType(TimelineEventType.SCORE_IMPROVED)
                .iteration(iteration)
                .timestamp(LocalDateTime.now())
                .elapsedMs(System.currentTimeMillis() - session.getCreatedAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
                .score(newScore)
                .coverage(coverage)
                .fairnessCv(fairnessCv)
                .hardViolations(violations)
                .build();

        getBuffer(sessionKey).addEvent(event);
        timelineController.publishEvent(sessionKey, event);
    }

    /**
     * Record simulation started.
     */
    public void recordStarted(String sessionKey) {
        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) return;

        session.setStatus(SandboxStatus.RUNNING);
        sessionRepository.save(session);

        TimelineEvent event = TimelineEvent.builder()
                .eventType(TimelineEventType.STARTED)
                .iteration(0)
                .timestamp(LocalDateTime.now())
                .elapsedMs(0)
                .metadata(Map.of("type", "simulation_start"))
                .build();

        getBuffer(sessionKey).addEvent(event);
        timelineController.publishEvent(sessionKey, event);
    }

    /**
     * Record simulation completed.
     */
    @Transactional
    public void recordCompleted(String sessionKey, double finalScore, double coverage, double fairnessCv, int violations) {
        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) return;

        session.setStatus(SandboxStatus.COMPLETED);
        session.setBestScore(finalScore);
        session.setCoverageRate(coverage);
        session.setFairnessCv(fairnessCv);
        session.setViolations(violations);
        session.setIterations(session.getIterations() + 1);
        sessionRepository.save(session);

        TimelineEvent event = TimelineEvent.builder()
                .eventType(TimelineEventType.COMPLETED)
                .iteration(session.getIterations())
                .timestamp(LocalDateTime.now())
                .score(finalScore)
                .coverage(coverage)
                .fairnessCv(fairnessCv)
                .hardViolations(violations)
                .metadata(Map.of("type", "simulation_complete"))
                .build();

        getBuffer(sessionKey).addEvent(event);

        // Flush buffer to database
        flushBuffer(sessionKey);

        // Notify SSE clients
        timelineController.completeSession(sessionKey, session);
    }

    /**
     * Record simulation failed.
     */
    public void recordFailed(String sessionKey, String errorMessage) {
        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) return;

        session.setStatus(SandboxStatus.FAILED);
        session.setErrorMessage(errorMessage);
        sessionRepository.save(session);

        TimelineEvent event = TimelineEvent.builder()
                .eventType(TimelineEventType.FAILED)
                .timestamp(LocalDateTime.now())
                .metadata(Map.of("type", "simulation_failed", "error", errorMessage))
                .build();

        getBuffer(sessionKey).addEvent(event);
        timelineController.failSession(sessionKey, errorMessage);
    }

    /**
     * Record simulation paused.
     */
    public void recordPaused(String sessionKey) {
        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) return;

        session.setStatus(SandboxStatus.PAUSED);
        sessionRepository.save(session);

        TimelineEvent event = TimelineEvent.builder()
                .eventType(TimelineEventType.PAUSED)
                .iteration(session.getIterations())
                .timestamp(LocalDateTime.now())
                .build();

        timelineController.publishEvent(sessionKey, event);
    }

    /**
     * Record simulation resumed.
     */
    public void recordResumed(String sessionKey) {
        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) return;

        session.setStatus(SandboxStatus.RUNNING);
        sessionRepository.save(session);

        TimelineEvent event = TimelineEvent.builder()
                .eventType(TimelineEventType.RESUMED)
                .iteration(session.getIterations())
                .timestamp(LocalDateTime.now())
                .build();

        timelineController.publishEvent(sessionKey, event);
    }

    // ─── Snapshot Management ──────────────────────────────────────────────────

    /**
     * Create a snapshot at current state.
     */
    @Transactional
    public void createSnapshot(String sessionKey, TimelineEvent event) {
        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) return;

        SandboxSnapshot snapshot = SandboxSnapshot.builder()
                .session(session)
                .iteration(event.getIteration())
                .score(event.getScore())
                .coverageRate(event.getCoverage())
                .fairnessCv(event.getFairnessCv())
                .violations(event.getHardViolations())
                .moveType(event.getMoveType())
                .staffId(event.getStaffId())
                .slotId(event.getSlotId())
                .accepted(event.getAccepted())
                .scoreDelta(event.getScoreDelta())
                .accepted(event.getAccepted())
                .isCheckpoint(true)
                .build();

        snapshotRepository.save(snapshot);
        session.setCurrentSnapshotId(snapshot.getId());
        sessionRepository.save(session);

        log.debug("Created checkpoint snapshot at iteration {} for session {}", event.getIteration(), sessionKey);
    }

    // ─── Buffer Management ───────────────────────────────────────────────────

    private TimelineEventBuffer getBuffer(String sessionKey) {
        return eventBuffers.computeIfAbsent(sessionKey, k -> new TimelineEventBuffer());
    }

    /**
     * Flush buffered events to database.
     */
    @Transactional
    public void flushBuffer(String sessionKey) {
        TimelineEventBuffer buffer = eventBuffers.remove(sessionKey);
        if (buffer == null) return;

        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) return;

        List<TimelineEvent> events = buffer.getAndClear();
        log.debug("Flushing {} events for session {}", events.size(), sessionKey);

        // Events are already saved as snapshots, so nothing extra needed here
        // This is where we could add a timeline_event table if needed for replay
    }

    /**
     * Get all events for a session (for replay).
     */
    @Transactional(readOnly = true)
    public List<TimelineEvent> getEvents(String sessionKey) {
        SandboxSession session = sessionRepository.findBySessionKey(sessionKey).orElse(null);
        if (session == null) return List.of();

        List<SandboxSnapshot> snapshots = snapshotRepository.findBySessionOrderByIterationAsc(session);
        return snapshots.stream().map(this::snapshotToEvent).toList();
    }

    private TimelineEvent snapshotToEvent(SandboxSnapshot s) {
        return TimelineEvent.builder()
                .id(s.getId())
                .iteration(s.getIteration())
                .timestamp(s.getCreatedAt())
                .score(s.getScore())
                .coverage(s.getCoverageRate())
                .fairnessCv(s.getFairnessCv())
                .hardViolations(s.getViolations())
                .softViolations(0)
                .moveType(s.getMoveType())
                .staffId(s.getStaffId())
                .slotId(s.getSlotId())
                .accepted(s.getAccepted())
                .scoreDelta(s.getScoreDelta())
                .eventType(s.getIteration() == 0 ? TimelineEventType.STARTED : TimelineEventType.SNAPSHOT)
                .build();
    }

    // ─── Scheduled Cleanup ────────────────────────────────────────────────────

    /**
     * Flush all buffers periodically to prevent memory buildup.
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void flushAllBuffers() {
        for (String sessionKey : List.copyOf(eventBuffers.keySet())) {
            try {
                flushBuffer(sessionKey);
            } catch (Exception e) {
                log.warn("Failed to flush buffer for session {}", sessionKey, e);
            }
        }
    }

    // ─── Event Buffer ─────────────────────────────────────────────────────────

    private static class TimelineEventBuffer {
        private final java.util.concurrent.ConcurrentLinkedQueue<TimelineEvent> events = new java.util.concurrent.ConcurrentLinkedQueue<>();

        void addEvent(TimelineEvent event) {
            events.offer(event);
        }

        List<TimelineEvent> getAndClear() {
            List<TimelineEvent> result = new java.util.ArrayList<>();
            TimelineEvent e;
            while ((e = events.poll()) != null) {
                result.add(e);
            }
            return result;
        }
    }
}
