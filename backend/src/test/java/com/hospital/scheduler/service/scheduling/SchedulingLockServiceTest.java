package com.hospital.scheduler.service.scheduling;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.*;
/**
 * Unit tests for {@link SchedulingLockService}.
 *
 * <p>Verifies that concurrent callers on the same period are serialized and
 * callers on different periods proceed in parallel.
 */
class SchedulingLockServiceTest {

    private final SchedulingLockService lockService = new SchedulingLockService();

    @Test
    void acquirePeriodLock_returnsSameSemaphoreForSamePeriod() {
        Semaphore sem1 = lockService.acquirePeriodLock(1);
        Semaphore sem2 = lockService.acquirePeriodLock(1);
        assertSame(sem1, sem2, "Same period must reuse the same Semaphore instance");
    }

    @Test
    void acquirePeriodLock_returnsDistinctSemaphoresForDifferentPeriods() {
        Semaphore sem1 = lockService.acquirePeriodLock(1);
        Semaphore sem2 = lockService.acquirePeriodLock(2);
        assertNotSame(sem1, sem2, "Different periods must have independent semaphores");
    }

    @Test
    void tryLock_succeedsWhenNoContention() {
        assertTrue(lockService.tryLock(100));
        lockService.unlock(100);
    }

    @Test
    void tryLock_failsWhenAlreadyHeld() throws InterruptedException {
        assertTrue(lockService.tryLock(101));
        // Verify contention from a different thread, which is what the production
        // caller (Tomcat worker thread A vs Tomcat worker thread B) actually cares about.
        AtomicInteger otherResult = new AtomicInteger(-1);
        Thread other = new Thread(() -> otherResult.set(lockService.tryLock(101) ? 1 : 0));
        other.start();
        other.join(2000);
        assertEquals(0, otherResult.get(),
                "tryLock from a different thread must fail while the first thread holds it");
        lockService.unlock(101);
        assertTrue(lockService.tryLock(101),
                "After unlock the lock must be acquirable again");
        lockService.unlock(101);
    }

    @Test
    void unlock_releasesLockForSameThread() {
        Integer periodId = 300;
        assertTrue(lockService.tryLock(periodId));
        lockService.unlock(periodId);
        // After unlock, the lock must be acquirable again from this thread.
        assertTrue(lockService.tryLock(periodId), "unlock must actually release the lock");
        lockService.unlock(periodId);
    }

    @Test
    void unlock_isNoOpWhenLockNotHeldByCurrentThread() {
        Integer periodId = 301;
        // Never acquired on this thread — must not throw or acquire.
        assertDoesNotThrow(() -> lockService.unlock(periodId));
    }

    @Test
    void concurrentSamePeriod_serialized() throws InterruptedException {
        Integer periodId = 200;
        int threadCount = 4;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(threadCount);
        AtomicInteger maxConcurrent = new AtomicInteger();
        AtomicInteger currentConcurrent = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startGate.await();
                    Semaphore sem = lockService.acquirePeriodLock(periodId);
                    sem.acquire();
                    try {
                        int now = currentConcurrent.incrementAndGet();
                        maxConcurrent.updateAndGet(prev -> Math.max(prev, now));
                        Thread.sleep(50);
                    } finally {
                        currentConcurrent.decrementAndGet();
                        sem.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishGate.countDown();
                }
            }, "lock-test-" + i).start();
        }
        startGate.countDown();
        assertTrue(finishGate.await(5, TimeUnit.SECONDS), "All threads should finish");
        assertEquals(1, maxConcurrent.get(),
                "At most one thread may hold the lock at a time, observed peak=" + maxConcurrent.get());
    }
}
