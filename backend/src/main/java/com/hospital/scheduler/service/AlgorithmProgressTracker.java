package com.hospital.scheduler.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory tracker for algorithm execution progress.
 * Used by frontend to poll real-time status during long-running scheduling operations.
 *
 * <p>Each call to {@link #start(Integer)} mints a unique {@code runToken} and registers
 * it as the active run for the {@code periodId}. Subsequent {@code update}/{@code complete}/{@code fail}
 * calls must pass the same token — otherwise they are treated as stale writes for an
 * older run and silently no-op. This prevents run A from completing run B's slot when
 * two requests overlap on the same period, which the previous version suffered from.
 *
 * <p>Status lifecycle:
 * <pre>
 *   start(periodId) → Progress + runToken
 *   update(periodId, runToken, step, percent)
 *   complete(periodId, runToken, message)
 *   completeWithResult(periodId, runToken, message, resultJson)
 *   fail(periodId, runToken, errorMessage)
 * </pre>
 *
 * <p>Status values: {@code RUNNING}, {@code COMPLETED}, {@code FAILED}.
 * Entries auto-expire after 5 minutes; a scheduled sweep runs every minute to keep the
 * map bounded even when callers forget to poll.
 */
@Service
@Slf4j
public class AlgorithmProgressTracker {

    public enum Status { RUNNING, COMPLETED, FAILED }

    /** Auto-expiry window for terminal entries (COMPLETED / FAILED). */
    private static final long AUTO_EXPIRY_SECONDS = 300;

    public static class Progress {
        private final Integer periodId;
        private final String runToken;
        private volatile Status status;
        private volatile String step;
        private volatile int percent;
        private volatile String message;
        private volatile String resultJson; // Serialized AutoScheduleResponse for frontend to fetch
        private final Instant startedAt;
        private volatile Instant updatedAt;

        public Progress(Integer periodId, String runToken) {
            this.periodId = periodId;
            this.runToken = runToken;
            this.status = Status.RUNNING;
            this.step = "Khởi tạo";
            this.percent = 0;
            this.startedAt = Instant.now();
            this.updatedAt = this.startedAt;
        }

        public Integer getPeriodId() { return periodId; }
        public String getRunToken() { return runToken; }
        public Status getStatus() { return status; }
        public String getStep() { return step; }
        public int getPercent() { return percent; }
        public String getMessage() { return message; }
        public String getResultJson() { return resultJson; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getUpdatedAt() { return updatedAt; }
    }

    /**
     * Active run per period. Atomically replaced by {@link #start(Integer)}; guards in
     * update/complete/fail ensure stale writes from a previous run never mutate a newer
     * run's state.
     */
    private final ConcurrentHashMap<Integer, Progress> progressMap = new ConcurrentHashMap<>();

    public Progress start(Integer periodId) {
        String token = UUID.randomUUID().toString();
        Progress p = new Progress(periodId, token);
        progressMap.put(periodId, p);
        log.debug("Algorithm progress started for period {} (runToken={})", periodId, token);
        return p;
    }

    /** Token-aware variant used by callers that captured the token at start time. */
    public void update(Integer periodId, String runToken, String step, int percent, String message) {
        Progress p = progressMap.get(periodId);
        if (p == null || !p.runToken.equals(runToken)) {
            log.debug("Ignoring stale update for period {} (expected token={}, current={})",
                    periodId, runToken, p == null ? "<none>" : p.runToken);
            return;
        }
        p.step = step;
        p.percent = Math.max(0, Math.min(100, percent));
        p.message = message;
        p.updatedAt = Instant.now();
    }

    /** Legacy single-arg overload: protected — no longer mutates state without a matching token. */
    public void update(Integer periodId, String step, int percent, String message) {
        Progress p = progressMap.get(periodId);
        if (p == null) return;
        update(periodId, p.runToken, step, percent, message);
    }

    public void complete(Integer periodId, String runToken, String message) {
        Progress p = progressMap.get(periodId);
        if (p == null || !p.runToken.equals(runToken)) {
            log.debug("Ignoring stale complete for period {} (expected token={})",
                    periodId, runToken);
            return;
        }
        p.status = Status.COMPLETED;
        p.percent = 100;
        p.step = "Hoàn tất";
        p.message = message;
        p.updatedAt = Instant.now();
        log.debug("Algorithm progress completed for period {} (runToken={})", periodId, runToken);
    }

    public void completeWithResult(Integer periodId, String runToken, String message, String resultJson) {
        Progress p = progressMap.get(periodId);
        if (p == null || !p.runToken.equals(runToken)) {
            log.debug("Ignoring stale completeWithResult for period {} (expected token={})",
                    periodId, runToken);
            return;
        }
        p.status = Status.COMPLETED;
        p.percent = 100;
        p.step = "Hoàn tất";
        p.message = message;
        p.resultJson = resultJson;
        p.updatedAt = Instant.now();
        log.debug("Algorithm progress completed for period {} with result (runToken={})",
                periodId, runToken);
    }

    public void fail(Integer periodId, String runToken, String errorMessage) {
        Progress p = progressMap.get(periodId);
        if (p == null || !p.runToken.equals(runToken)) {
            log.debug("Ignoring stale fail for period {} (expected token={})",
                    periodId, runToken);
            return;
        }
        p.status = Status.FAILED;
        p.step = "Lỗi";
        p.message = errorMessage;
        p.updatedAt = Instant.now();
        log.warn("Algorithm progress failed for period {} (runToken={}): {}",
                periodId, runToken, errorMessage);
    }

    /** Token-free convenience overload — auto-resolves the current token. */
    public void fail(Integer periodId, String errorMessage) {
        Progress p = progressMap.get(periodId);
        if (p == null) return;
        fail(periodId, p.runToken, errorMessage);
    }

    public Progress get(Integer periodId) {
        Progress p = progressMap.get(periodId);
        if (p != null && p.updatedAt.isBefore(Instant.now().minusSeconds(AUTO_EXPIRY_SECONDS))) {
            // Lazy expiry: terminal entry older than the window — drop it so a future
            // /progress/{periodId} poll sees IDLE.
            progressMap.remove(periodId, p);
            return null;
        }
        return p;
    }

    public void clear(Integer periodId) {
        progressMap.remove(periodId);
    }

    /**
     * Scheduled cleanup that removes terminal entries older than the auto-expiry window
     * even if no client polls them. Runs every minute. Bounded so a long-lived server
     * with thousands of distinct period IDs cannot leak memory indefinitely.
     */
    @Scheduled(fixedDelayString = "PT1M")
    public void sweepExpiredEntries() {
        Instant cutoff = Instant.now().minusSeconds(AUTO_EXPIRY_SECONDS);
        int removed = 0;
        for (java.util.Map.Entry<Integer, Progress> e : progressMap.entrySet()) {
            if (e.getValue().updatedAt.isBefore(cutoff)) {
                progressMap.remove(e.getKey(), e.getValue());
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Swept {} expired algorithm-progress entries", removed);
        }
    }
}