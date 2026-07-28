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

    /** Unregister the running thread for this period. Idempotent. */
    public void unregisterRunningThread(Integer periodId) {
        runningThreads.remove(periodId);
    }

    /**
     * Cancel a running scheduling operation for the given period.
     * 1. Interrupts the running thread (if any).
     * 2. Removes the semaphore from the map and releases it, so a new
     *    request can immediately acquire a fresh semaphore.
     *
     * <p>Unlike the old ReentrantLock-based forceUnlock, this does NOT
     * wait for the previous thread to finish — it allows a new scheduling
     * request to start right away while the old thread is still winding down.
     */
    public void cancel(Integer periodId) {
        if (periodId == null) {
            log.warn("cancel called with null periodId — ignoring");
            return;
        }
        // Interrupt the running thread so it stops wasting resources
        Thread thread = runningThreads.remove(periodId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            log.info("Interrupted running scheduling thread for period {}", periodId);
        }
        // Release the lock so a new request can proceed immediately.
        // Removing the entry first means the next caller gets a fresh Semaphore(1).
        Semaphore sem = periodLocks.remove(periodId);
        if (sem != null) {
            sem.release();
            log.info("Released scheduling lock for period {}", periodId);
        }
    }

    /**
     * Force-unlock for admin cleanup of stale locks (e.g. after a crashed scheduling run).
     * @deprecated Use {@link #cancel(Integer)} instead — it interrupts the running thread
     *             and releases the lock without waiting.
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
        // No thread or dead thread — remove and release the semaphore
        Semaphore sem = periodLocks.remove(periodId);
        if (sem != null) {
            sem.release();
            log.info("Force-released stale lock for period {} (thread={})", periodId,
                    thread == null ? "none" : "dead");
            return true;
        }
        return false; // no semaphore to release
    }
}
