package com.hospital.scheduler.digital.sandbox.service;

import com.hospital.scheduler.digital.sandbox.dto.ReplayFrame;
import com.hospital.scheduler.digital.sandbox.dto.ReplayResponse;
import com.hospital.scheduler.digital.sandbox.entity.SandboxAssignment;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSnapshot;
import com.hospital.scheduler.digital.sandbox.repository.SandboxAssignmentRepository;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSessionRepository;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for replay functionality.
 *
 * <p>Loads snapshots and builds replay frames without re-running the algorithm.
 * Uses in-memory caching for performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxReplayService {

    private final SandboxSessionRepository sessionRepository;
    private final SandboxSnapshotRepository snapshotRepository;
    private final SandboxAssignmentRepository assignmentRepository;

    /** In-memory cache for replay data. */
    private final ConcurrentHashMap<String, ReplayCache> replayCache = new ConcurrentHashMap<>();

    /** Cache TTL: 30 minutes. */
    private static final long CACHE_TTL_MS = 30 * 60 * 1000;

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Load full replay for a session.
     */
    @Transactional(readOnly = true)
    public ReplayResponse loadReplay(String sessionKey) {
        ReplayCache cache = getOrCreateCache(sessionKey);
        if (cache.isLoaded()) {
            return cache.buildResponse();
        }

        SandboxSession session = sessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionKey));

        List<SandboxSnapshot> snapshots = snapshotRepository.findBySessionOrderByIterationAsc(session);

        // Build frames from snapshots
        List<ReplayFrame> frames = buildFrames(session, snapshots);

        // Cache the frames
        cache.setFrames(frames);

        // Build response
        return cache.buildResponse();
    }

    /**
     * Get a specific frame by iteration.
     */
    @Transactional(readOnly = true)
    public ReplayFrame getFrame(String sessionKey, int iteration) {
        ReplayCache cache = getOrCreateCache(sessionKey);

        if (!cache.isLoaded()) {
            loadReplay(sessionKey);
        }

        return cache.getFrame(iteration)
                .orElseThrow(() -> new IllegalArgumentException("Frame not found: iteration " + iteration));
    }

    /**
     * Get frames in a range (for pagination).
     */
    @Transactional(readOnly = true)
    public List<ReplayFrame> getFramesInRange(String sessionKey, int startIteration, int endIteration) {
        ReplayCache cache = getOrCreateCache(sessionKey);

        if (!cache.isLoaded()) {
            loadReplay(sessionKey);
        }

        return cache.getFramesInRange(startIteration, endIteration);
    }

    /**
     * Get score summary for chart.
     */
    @Transactional(readOnly = true)
    public ReplayResponse.ScoreSummary getScoreSummary(String sessionKey) {
        ReplayCache cache = getOrCreateCache(sessionKey);

        if (!cache.isLoaded()) {
            loadReplay(sessionKey);
        }

        return cache.buildScoreSummary();
    }

    /**
     * Export replay as JSON.
     */
    @Transactional(readOnly = true)
    public String exportAsJson(String sessionKey) {
        ReplayResponse response = loadReplay(sessionKey);
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            return mapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Failed to export replay as JSON", e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Export replay as CSV.
     */
    @Transactional(readOnly = true)
    public String exportAsCsv(String sessionKey) {
        ReplayResponse response = loadReplay(sessionKey);

        StringBuilder csv = new StringBuilder();
        csv.append("Iteration,Timestamp,Score,Coverage,FairnessCV,HardViolations,SoftViolations,MoveType,Accepted,ScoreDelta\n");

        for (ReplayFrame frame : response.getFrames()) {
            csv.append(String.format("%d,%s,%.2f,%.2f,%.3f,%d,%d,%s,%s,%.2f\n",
                    frame.getIteration(),
                    frame.getTimestamp(),
                    frame.getScore(),
                    frame.getCoverage(),
                    frame.getFairnessCv(),
                    frame.getHardViolations(),
                    frame.getSoftViolations(),
                    frame.getMoveType() != null ? frame.getMoveType() : "",
                    frame.isAccepted(),
                    frame.getScoreDelta()
            ));
        }

        return csv.toString();
    }

    // ─── Frame Building ─────────────────────────────────────────────────────

    private List<ReplayFrame> buildFrames(SandboxSession session, List<SandboxSnapshot> snapshots) {
        List<ReplayFrame> frames = new ArrayList<>();

        for (SandboxSnapshot snapshot : snapshots) {
            ReplayFrame frame = buildFrame(session, snapshot);
            frames.add(frame);
        }

        return frames;
    }

    private ReplayFrame buildFrame(SandboxSession session, SandboxSnapshot snapshot) {
        ReplayFrame.MoveStaffInfo staff = null;
        ReplayFrame.MoveStaffInfo targetStaff = null;
        ReplayFrame.SlotInfo slot = null;

        if (snapshot.getStaffId() != null) {
            staff = ReplayFrame.MoveStaffInfo.builder()
                    .id(snapshot.getStaffId())
                    .name(getStaffName(session, snapshot.getStaffId()))
                    .staffCode(getStaffCode(session, snapshot.getStaffId()))
                    .build();
        }

        if (snapshot.getTargetStaffId() != null) {
            targetStaff = ReplayFrame.MoveStaffInfo.builder()
                    .id(snapshot.getTargetStaffId())
                    .name(getStaffName(session, snapshot.getTargetStaffId()))
                    .staffCode(getStaffCode(session, snapshot.getTargetStaffId()))
                    .build();
        }

        if (snapshot.getSlotId() != null) {
            slot = ReplayFrame.SlotInfo.builder()
                    .id(snapshot.getSlotId())
                    .build();
        }

        String reason = buildReason(snapshot);

        return ReplayFrame.builder()
                .iteration(snapshot.getIteration())
                .timestamp(snapshot.getCreatedAt())
                .score(snapshot.getScore())
                .coverage(snapshot.getCoverageRate() != null ? snapshot.getCoverageRate() : 0)
                .fairnessCv(snapshot.getFairnessCv() != null ? snapshot.getFairnessCv() : 0)
                .hardViolations(snapshot.getViolations() != null ? snapshot.getViolations() : 0)
                .softViolations(0)
                .moveType(snapshot.getMoveType())
                .accepted(snapshot.getAccepted() != null ? snapshot.getAccepted() : false)
                .reason(reason)
                .scoreDelta(snapshot.getScoreDelta() != null ? snapshot.getScoreDelta() : 0)
                .coverageDelta(0)
                .staff(staff)
                .targetStaff(targetStaff)
                .slot(slot)
                .changedAssignments(buildChangedAssignments(session, snapshot))
                .durationMs(0)
                .isCheckpoint(snapshot.getIsCheckpoint() != null && snapshot.getIsCheckpoint())
                .build();
    }

    private String buildReason(SandboxSnapshot snapshot) {
        if (snapshot.getAccepted() == null) {
            return "Initial state";
        }

        if (snapshot.getAccepted()) {
            return "Move accepted";
        }

        // Parse constraint deltas if available
        if (snapshot.getConstraintDeltas() != null) {
            return "Rejected: constraint violation";
        }

        return "Rejected: no improvement";
    }

    private List<ReplayFrame.AssignmentChange> buildChangedAssignments(SandboxSession session, SandboxSnapshot snapshot) {
        // In a full implementation, this would track what changed
        // For now, return empty list
        return Collections.emptyList();
    }

    // ─── Cache Management ───────────────────────────────────────────────────

    private ReplayCache getOrCreateCache(String sessionKey) {
        return replayCache.compute(sessionKey, (k, existing) -> {
            if (existing != null && !existing.isExpired()) {
                return existing;
            }
            return new ReplayCache(sessionKey);
        });
    }

    /**
     * Clear cache for a session.
     */
    public void clearCache(String sessionKey) {
        replayCache.remove(sessionKey);
    }

    /**
     * Clear all caches.
     */
    public void clearAllCaches() {
        replayCache.clear();
    }

    // ─── Helper Methods ─────────────────────────────────────────────────────

    private String getStaffName(SandboxSession session, Integer staffId) {
        if (staffId == null) return null;
        // In a full implementation, this would look up from cache or DB
        return "Staff #" + staffId;
    }

    private String getStaffCode(SandboxSession session, Integer staffId) {
        if (staffId == null) return null;
        return "NV" + String.format("%03d", staffId);
    }

    // ─── Cache Class ────────────────────────────────────────────────────────

    private static class ReplayCache {
        private final String sessionKey;
        private final long createdAt;
        private List<ReplayFrame> frames;
        private ReplayResponse.ScoreSummary scoreSummary;

        ReplayCache(String sessionKey) {
            this.sessionKey = sessionKey;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isLoaded() {
            return frames != null;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }

        void setFrames(List<ReplayFrame> frames) {
            this.frames = frames;
            this.scoreSummary = buildScoreSummary();
        }

        Optional<ReplayFrame> getFrame(int iteration) {
            if (frames == null) return Optional.empty();
            return frames.stream()
                    .filter(f -> f.getIteration() == iteration)
                    .findFirst();
        }

        List<ReplayFrame> getFramesInRange(int start, int end) {
            if (frames == null) return Collections.emptyList();
            return frames.stream()
                    .filter(f -> f.getIteration() >= start && f.getIteration() <= end)
                    .collect(Collectors.toList());
        }

        ReplayResponse buildResponse() {
            if (frames == null) {
                return ReplayResponse.builder()
                        .sessionKey(sessionKey)
                        .totalIterations(0)
                        .totalFrames(0)
                        .fullyLoaded(false)
                        .frames(Collections.emptyList())
                        .build();
            }

            return ReplayResponse.builder()
                    .sessionKey(sessionKey)
                    .totalIterations(frames.size() > 0 ? frames.get(frames.size() - 1).getIteration() : 0)
                    .totalFrames(frames.size())
                    .fullyLoaded(true)
                    .frames(frames)
                    .scoreSummary(scoreSummary)
                    .build();
        }

        ReplayResponse.ScoreSummary buildScoreSummary() {
            if (frames == null || frames.isEmpty()) {
                return ReplayResponse.ScoreSummary.builder()
                        .iterations(Collections.emptyList())
                        .scores(Collections.emptyList())
                        .coverages(Collections.emptyList())
                        .fairnessCvs(Collections.emptyList())
                        .violations(Collections.emptyList())
                        .maxScore(0)
                        .minScore(0)
                        .maxCoverage(0)
                        .minCoverage(0)
                        .build();
            }

            List<Integer> iterations = new ArrayList<>();
            List<Double> scores = new ArrayList<>();
            List<Double> coverages = new ArrayList<>();
            List<Double> fairnessCvs = new ArrayList<>();
            List<Integer> violations = new ArrayList<>();

            for (ReplayFrame frame : frames) {
                iterations.add(frame.getIteration());
                scores.add(frame.getScore());
                coverages.add(frame.getCoverage());
                fairnessCvs.add(frame.getFairnessCv());
                violations.add(frame.getHardViolations());
            }

            return ReplayResponse.ScoreSummary.builder()
                    .iterations(iterations)
                    .scores(scores)
                    .coverages(coverages)
                    .fairnessCvs(fairnessCvs)
                    .violations(violations)
                    .maxScore(scores.stream().mapToInt(Double::intValue).max().orElse(0))
                    .minScore(scores.stream().mapToInt(Double::intValue).min().orElse(0))
                    .maxCoverage(coverages.stream().mapToDouble(Double::doubleValue).max().orElse(0))
                    .minCoverage(coverages.stream().mapToDouble(Double::doubleValue).min().orElse(0))
                    .build();
        }
    }
}
