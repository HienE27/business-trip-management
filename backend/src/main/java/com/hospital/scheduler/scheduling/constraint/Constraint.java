package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * SPI for v10 constraint plugins.
 *
 * <p>Constraints evaluate a {@link WorkingSolution} (or a proposed move) and
 * produce a {@link ScoreDelta}. They are registered with the
 * {@link ConstraintRegistry} and invoked by the search loop.
 *
 * <p>Each constraint has a fixed weight. Hard constraints (e.g., BR-01
 * shift conflicts) carry infinite weight; soft constraints (e.g., BR-06 max
 * shifts) carry a tunable weight in {@code application.properties}.
 */
public interface Constraint {

    /** Identifier — used in logs, telemetry, and config to enable/disable. */
    String id();

    /** True if this is a hard constraint (always weight = ∞). */
    boolean isHard();

    /** Weight for soft constraints (ignored for hard). */
    double weight();

    /**
     * Evaluate {@code solution} and return a delta to the current score.
     * Called once at initialization to compute the initial delta.
     */
    ScoreDelta evaluate(WorkingSolution solution);

    /**
     * Optional fast-path — compute the delta for a single proposed move
     * WITHOUT fully re-evaluating. Returns null if no fast path is available
     * (caller will fall back to {@link #evaluate(WorkingSolution)}).
     */
    default ScoreDelta evaluateMove(WorkingSolution solution, int slotId, int newStaffId) {
        return null;
    }
}