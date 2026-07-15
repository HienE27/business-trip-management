package com.hospital.scheduler.service.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * Manages per-period execution locks for auto-scheduling operations.
 * Concurrent autoSchedule / previewSchedule calls on the same period are serialized
 * so their delete-and-regenerate operations cannot interleave.
 */
@Slf4j
@Service
public class SchedulingLockService {

    private final ConcurrentHashMap<Integer, Lock> periodLocks = new ConcurrentHashMap<>();

    /**
     * Acquire (or lazily create) a non-fair lock for the given period.
     *
     * <p>The map is unbounded on purpose -- period IDs are small bounded integers
     * from a separate table, and locks are JVM-scoped which matches the
     * request-scoped transaction boundary.
     */
    public Lock acquirePeriodLock(Integer periodId) {
        return periodLocks.computeIfAbsent(periodId,
                id -> new java.util.concurrent.locks.ReentrantLock());
    }

    public boolean tryLock(Integer periodId) {
        Lock lock = acquirePeriodLock(periodId);
        return lock.tryLock();
    }

    public void unlock(Integer periodId) {
        Lock lock = periodLocks.get(periodId);
        if (lock != null && lock.tryLock()) {
            lock.unlock();
        }
    }
}
