package com.hospital.scheduler.scheduling.alns;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * ALNS destroy operator — removes a fraction of the current solution's
 * assignments, leaving them unassigned so a {@link RepairOperator} can reinsert
 * them.
 */
public interface DestroyOperator {
    String name();
    /** Returns the number of slots removed. */
    int destroy(WorkingSolution solution, int removeCount);
}