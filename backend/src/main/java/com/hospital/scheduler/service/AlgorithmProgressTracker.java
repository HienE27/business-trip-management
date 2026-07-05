package com.hospital.scheduler.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory tracker for algorithm execution progress.
 * Used by frontend to poll real-time status during long-running scheduling operations.
 *
 * Status lifecycle:
 *   - start(periodId): initialize entry with status=RUNNING
 *   - update(periodId, step, percent): update progress
 *   - finish(periodId, status, message): mark complete/failed
 *
 * Status values: RUNNING, COMPLETED, FAILED
 * Entries auto-expire after 5 minutes to prevent memory leak.
 */
@Service
@Slf4j
public class AlgorithmProgressTracker {

    public enum Status { RUNNING, COMPLETED, FAILED }

    public static class Progress {
        private final Integer periodId;
        private volatile Status status;
        private volatile String step;
        private volatile int percent;
        private volatile String message;
        private volatile String resultJson; // Serialized AutoScheduleResponse for frontend to fetch
        private final Instant startedAt;
        private volatile Instant updatedAt;

        public Progress(Integer periodId) {
            this.periodId = periodId;
            this.status = Status.RUNNING;
            this.step = "Khởi tạo";
            this.percent = 0;
            this.startedAt = Instant.now();
            this.updatedAt = this.startedAt;
        }

        public Integer getPeriodId() { return periodId; }
        public Status getStatus() { return status; }
        public String getStep() { return step; }
        public int getPercent() { return percent; }
        public String getMessage() { return message; }
        public String getResultJson() { return resultJson; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getUpdatedAt() { return updatedAt; }
    }

    private final ConcurrentHashMap<Integer, Progress> progressMap = new ConcurrentHashMap<>();

    public Progress start(Integer periodId) {
        Progress p = new Progress(periodId);
        progressMap.put(periodId, p);
        log.debug("Algorithm progress started for period {}", periodId);
        return p;
    }

    public void update(Integer periodId, String step, int percent, String message) {
        Progress p = progressMap.get(periodId);
        if (p != null) {
            p.step = step;
            p.percent = Math.max(0, Math.min(100, percent));
            p.message = message;
            p.updatedAt = Instant.now();
        }
    }

    public void complete(Integer periodId, String message) {
        Progress p = progressMap.get(periodId);
        if (p != null) {
            p.status = Status.COMPLETED;
            p.percent = 100;
            p.step = "Hoàn tất";
            p.message = message;
            p.updatedAt = Instant.now();
            log.debug("Algorithm progress completed for period {}", periodId);
        }
    }

    public void completeWithResult(Integer periodId, String message, String resultJson) {
        Progress p = progressMap.get(periodId);
        if (p != null) {
            p.status = Status.COMPLETED;
            p.percent = 100;
            p.step = "Hoàn tất";
            p.message = message;
            p.resultJson = resultJson;
            p.updatedAt = Instant.now();
            log.debug("Algorithm progress completed for period {} with result", periodId);
        }
    }

    public void fail(Integer periodId, String errorMessage) {
        Progress p = progressMap.get(periodId);
        if (p != null) {
            p.status = Status.FAILED;
            p.step = "Lỗi";
            p.message = errorMessage;
            p.updatedAt = Instant.now();
            log.warn("Algorithm progress failed for period {}: {}", periodId, errorMessage);
        }
    }

    public Progress get(Integer periodId) {
        Progress p = progressMap.get(periodId);
        if (p != null && p.updatedAt.isBefore(Instant.now().minusSeconds(300))) {
            progressMap.remove(periodId);
            return null;
        }
        return p;
    }

    public void clear(Integer periodId) {
        progressMap.remove(periodId);
    }
}