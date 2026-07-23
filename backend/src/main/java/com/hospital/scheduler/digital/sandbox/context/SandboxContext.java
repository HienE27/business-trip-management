package com.hospital.scheduler.digital.sandbox.context;

import com.hospital.scheduler.digital.sandbox.domain.SimulationMode;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import com.hospital.scheduler.scheduling.config.ConfigDomain;
import com.hospital.scheduler.scheduling.replay.MoveLogRegistry;
import com.hospital.scheduler.scheduling.solution.IncrementalState;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.telemetry.TelemetryCollector;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SandboxContext is the execution context for scheduling simulations.
 *
 * <p>This is the core abstraction that makes sandboxing work:
 * <ul>
 *   <li>Scheduler runs on this context, not directly on production data</li>
 *   <li>All telemetry, moves, and statistics flow through this context</li>
 *   <li>Compare, Replay, Explain, and What-if all consume from this context</li>
 * </ul>
 *
 * <p>Design principle: Scheduler should NOT know it's running in sandbox mode.
 * It just receives a WorkingSolution and produces results. The context wraps all
 * the production services and redirects them to sandbox storage.
 *
 * <p>Key components:
 * <pre>
 * SandboxContext
 * ├── workingSolution     — the solution being optimized
 * ├── scoreDirector      — score calculation
 * ├── telemetryCollector  — metrics collection
 * ├── moveLogRegistry    — move history for replay
 * ├── incrementalState    — statistics state
 * ├── config             — algorithm configuration
 * └── sandboxSession     — session metadata
 * </pre>
 */
@Getter
public class SandboxContext {

    private final String sessionKey;
    private final SimulationMode mode;
    private final Long profileId;

    // Core solution
    private final WorkingSolution workingSolution;

    // Telemetry & statistics
    private final TelemetryCollector telemetryCollector;
    private final IncrementalState incrementalState;

    // Move history for replay
    private final MoveLogRegistry moveLogRegistry;

    // Algorithm configuration
    private final ConfigDomain config;

    // Session reference
    private final SandboxSession session;

    // Execution state
    private final AtomicReference<ExecutionState> executionState;
    private final AtomicInteger currentIteration;
    private final AtomicInteger acceptedMoves;
    private final AtomicInteger rejectedMoves;

    // Runtime metrics
    private final Map<String, Object> runtimeMetrics;
    private final List<Double> scoreHistory;

    @Builder
    public SandboxContext(
            String sessionKey,
            SimulationMode mode,
            Long profileId,
            WorkingSolution workingSolution,
            TelemetryCollector telemetryCollector,
            IncrementalState incrementalState,
            MoveLogRegistry moveLogRegistry,
            ConfigDomain config,
            SandboxSession session
    ) {
        this.sessionKey = sessionKey;
        this.mode = mode;
        this.profileId = profileId;
        this.workingSolution = workingSolution;
        this.telemetryCollector = telemetryCollector;
        this.incrementalState = incrementalState;
        this.moveLogRegistry = moveLogRegistry;
        this.config = config;
        this.session = session;

        this.executionState = new AtomicReference<>(ExecutionState.IDLE);
        this.currentIteration = new AtomicInteger(0);
        this.acceptedMoves = new AtomicInteger(0);
        this.rejectedMoves = new AtomicInteger(0);
        this.runtimeMetrics = new ConcurrentHashMap<>();
        this.scoreHistory = new java.util.concurrent.CopyOnWriteArrayList<>();
    }

    // ─── Execution state ─────────────────────────────────────────────────────

    public enum ExecutionState {
        IDLE,
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED
    }

    public void startExecution() {
        executionState.set(ExecutionState.RUNNING);
    }

    public void pauseExecution() {
        executionState.set(ExecutionState.PAUSED);
    }

    public void resumeExecution() {
        executionState.set(ExecutionState.RUNNING);
    }

    public void completeExecution() {
        executionState.set(ExecutionState.COMPLETED);
    }

    public void failExecution(String reason) {
        runtimeMetrics.put("error", reason);
        executionState.set(ExecutionState.FAILED);
    }

    public boolean isRunning() {
        return executionState.get() == ExecutionState.RUNNING;
    }

    public boolean isPaused() {
        return executionState.get() == ExecutionState.PAUSED;
    }

    public boolean isCompleted() {
        return executionState.get() == ExecutionState.COMPLETED;
    }

    // ─── Iteration tracking ───────────────────────────────────────────────────

    public int nextIteration() {
        return currentIteration.incrementAndGet();
    }

    public int getCurrentIteration() {
        return currentIteration.get();
    }

    public void recordAcceptedMove() {
        acceptedMoves.incrementAndGet();
    }

    public void recordRejectedMove() {
        rejectedMoves.incrementAndGet();
    }

    public double getAcceptanceRate() {
        int accepted = acceptedMoves.get();
        int total = accepted + rejectedMoves.get();
        return total > 0 ? (double) accepted / total : 0.0;
    }

    // ─── Score history ───────────────────────────────────────────────────────

    public void recordScore(double score) {
        scoreHistory.add(score);
    }

    public List<Double> getScoreHistory() {
        return List.copyOf(scoreHistory);
    }

    public double getInitialScore() {
        return scoreHistory.isEmpty() ? 0.0 : scoreHistory.get(0);
    }

    public double getBestScore() {
        return scoreHistory.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    public double getLatestScore() {
        return scoreHistory.isEmpty() ? 0.0 : scoreHistory.get(scoreHistory.size() - 1);
    }

    // ─── Runtime metrics ─────────────────────────────────────────────────────

    public void setMetric(String key, Object value) {
        runtimeMetrics.put(key, value);
    }

    public Object getMetric(String key) {
        return runtimeMetrics.get(key);
    }

    public Map<String, Object> getAllMetrics() {
        return Map.copyOf(runtimeMetrics);
    }

    // ─── Snapshot helpers ─────────────────────────────────────────────────────

    /**
     * Get coverage rate from working solution.
     */
    public double getCoverageRate() {
        if (workingSolution == null) return 0.0;
        // Coverage = assigned slots / total slots
        int total = workingSolution.getDescriptor().slotCount();
        int assigned = workingSolution.getAssignments().size();
        return total > 0 ? (double) assigned / total * 100 : 0.0;
    }

    /**
     * Get fairness CV from telemetry.
     */
    public double getFairnessCv() {
        if (incrementalState == null) return 0.0;
        // CV = stdDev / mean from IncrementalState
        return incrementalState.getCV();
    }

    /**
     * Get total violations from telemetry.
     */
    public int getTotalViolations() {
        // Violations tracked separately or derived from score
        if (telemetryCollector == null) return 0;
        return 0; // Default to 0 if not tracked
    }

    // ─── Progress snapshot ─────────────────────────────────────────────────────

    /**
     * Get current progress snapshot for UI updates.
     */
    public ProgressSnapshot getProgressSnapshot() {
        return ProgressSnapshot.builder()
                .sessionKey(sessionKey)
                .iteration(getCurrentIteration())
                .executionState(executionState.get())
                .score(getLatestScore())
                .bestScore(getBestScore())
                .coverageRate(getCoverageRate())
                .fairnessCv(getFairnessCv())
                .violations(getTotalViolations())
                .acceptedMoves(acceptedMoves.get())
                .rejectedMoves(rejectedMoves.get())
                .acceptanceRate(getAcceptanceRate())
                .runtimeMetrics(Map.copyOf(runtimeMetrics))
                .build();
    }

    @lombok.Builder
    @lombok.Value
    public static class ProgressSnapshot {
        String sessionKey;
        int iteration;
        ExecutionState executionState;
        double score;
        double bestScore;
        double coverageRate;
        double fairnessCv;
        int violations;
        int acceptedMoves;
        int rejectedMoves;
        double acceptanceRate;
        Map<String, Object> runtimeMetrics;
    }
}
