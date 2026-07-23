package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * BR-03 — a staff member cannot be assigned any shift on a compensation day
 * (the day off owed after a 24/24 duty shift).
 *
 * <p>Compensation days are indexed per staff in {@link
 * com.hospital.scheduler.scheduling.domain.SchedulingProblem}. This constraint
 * re-checks after every move (defense-in-depth; eligibility filtering in the
 * problem should already exclude compensation days, but schedule edits in the
 * UI can bypass eligibility).
 *
 * <p>Hard constraint — a schedule on a compensation day is a non-negotiable
 * business-rule violation.
 */
public class CompensationDayConstraint implements Constraint {

    @Override
    public String id() {
        return "BR-03:CompensationDay";
    }

    @Override
    public boolean isHard() {
        return true;
    }

    @Override
    public double weight() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public ScoreDelta evaluate(WorkingSolution solution) {
        int violations = 0;
        for (MutableAssignment a : solution.getAssignments()) {
            if (a.staffId <= 0 || a.date == null) continue;
            if (solution.getDescriptor().getProblem().isOnCompensation(a.staffId, a.date)) {
                violations++;
            }
        }
        return new ScoreDelta(violations, 0, 0, 0, 0, 0, 0);
    }
}
