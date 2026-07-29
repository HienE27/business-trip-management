package com.hospital.scheduler.service.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Manages per-period execution locks for auto-scheduling operations.
 * Uses {@link Semaphore} (not ReentrantLock) so the lock can be released
 * from any thread — this is essential for the cancel/refresh flow where
 * one HTTP request needs to free a lock held by another.
 *
 * <p>Concurrent autoSchedule / previewSchedule calls on the same period
 * are serialized so their delete-and-regenerate operations cannot interleave.</p>
 */
@Slf4j
@Service
public class SchedulingLockService {

    private final ConcurrentHashMap<Integer, Semaphore> periodLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Thread> runningThreads = new ConcurrentHashMap<>();

    /**
     * Acquire (or lazily create) a fair semaphore for the given period.
     * Fair mode = longest-waiting thread gets the permit first.
     */
    public Semaphore acquirePeriodLock(Integer periodId) {
        return periodLocks.computeIfAbsent(periodId, id -> new Semaphore(1, true));
    }

    /**
     * Non-blocking try-acquire. Returns true if the permit was acquired.
     */
    public boolean tryLock(Integer periodId) {
        Semaphore sem = acquirePeriodLock(periodId);
        return sem.tryAcquire();
    }

    /**
     * Release the permit for the given period. Safe to call from any thread.
     */
    public void unlock(Integer periodId) {
        Semaphore sem = periodLocks.get(periodId);
        if (sem != null) {
            sem.release();
        }
    }

    /** Register the current thread as the one running scheduling for this period. */
    public void registerRunningThread(Integer periodId) {
        runningThreads.put(periodId, Thread.currentThread());
    }

    /** Unregister the current running thread for this period. Idempotent. */
    public void unregisterRunningThread(Integer periodId) {
        runningThreads.remove(periodId, Thread.currentThread());
    }

    /**
     * Cancel a running scheduling operation for the given period.
     * Interrupt only: the owner keeps the semaphore until its finally block releases it.
     */
    public void cancel(Integer periodId) {
        if (periodId == null) {
            log.warn("cancel called with null periodId — ignoring");
            return;
        }
        Thread thread = runningThreads.get(periodId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            log.info("Interrupted running scheduling thread for period {}", periodId);
        }
    }

    /**
     * Cancel a scheduling run by interrupting its owner.
     * @deprecated Use {@link #cancel(Integer)} instead.
     */
    @Deprecated
    public void forceUnlock(Integer periodId) {
        cancel(periodId);
    }

    /**
     * Release a stale lock if the owning thread is dead or no thread is registered.
     * Safe to call before acquire — prevents "period is being scheduled by another request"
     * failures when cancel() had previously failed (e.g. HTTP proxy 500).
     *
     * @return true if a stale lock was force-released
     */
    public boolean forceReleaseStaleLock(Integer periodId) {
        Thread thread = runningThreads.get(periodId);
        if (thread != null && thread.isAlive()) {
            return false; // thread still alive — lock is valid
        }
        Semaphore sem = periodLocks.get(periodId);
        if (sem == null) {
            return false;
        }
        // No registered owner plus an unavailable permit may be a live thread between
        // acquire and registration. Never replace or release its semaphore.
        if (thread == null && sem.availablePermits() == 0) {
            return false;
        }
        if (!periodLocks.remove(periodId, sem)) {
            return false;
        }
        if (thread != null) {
            runningThreads.remove(periodId, thread);
        }
        if (thread != null && sem.availablePermits() == 0) {
            sem.release();
        }
        log.info("Removed stale lock for period {} (thread={})", periodId,
                thread == null ? "none" : "dead");
        return true;
    }
}
