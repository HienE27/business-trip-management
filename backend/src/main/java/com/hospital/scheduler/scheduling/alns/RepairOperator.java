package com.hospital.scheduler.scheduling.alns;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * ALNS repair operator — reassigns previously destroyed slots using a heuristic.
 */
public interface RepairOperator {
    String name();
    int repair(WorkingSolution solution, int expectedInsertions);
}