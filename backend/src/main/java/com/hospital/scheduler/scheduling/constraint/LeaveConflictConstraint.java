package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * BR-05 — staff on approved leave cannot be assigned to any shift on that day.
 *
 * <p>Eligibility is already filtered by {@code SchedulingProblem.getEligibleStaff},
 * but defense-in-depth: this constraint re-checks after a move is applied.
 */
public class LeaveConflictConstraint implements Constraint {

    @Override
    public String id() {
        return "BR-05:LeaveConflict";
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
            if (solution.getDescriptor().getProblem().isOnLeave(a.staffId, a.date)) {
                violations++;
            }
            if (solution.getDescriptor().getProblem().isOnCompensation(a.staffId, a.date)) {
                violations++;
            }
        }
        return new ScoreDelta(violations, 0, 0, 0, 0, 0, 0);
    }
}