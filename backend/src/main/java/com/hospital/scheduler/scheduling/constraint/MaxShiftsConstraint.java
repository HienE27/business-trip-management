package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.domain.StaffNode;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.LoadStatistics;

/**
 * BR-06: Max shifts constraint (SOFT).
 * 
 * <p>A staff should not exceed their maximum allowed shifts per month.</p>
 */
public class MaxShiftsConstraint implements Constraint {

    @Override
    public String name() {
        return "MaxShiftsConstraint";
    }

    @Override
    public Type type() {
        return Type.SOFT;
    }

    @Override
    public boolean isApplicable(Move move) {
        return move.type() == Move.MoveType.ASSIGN ||
               move.type() == Move.MoveType.MOVE ||
               move.type() == Move.MoveType.SWAP;
    }

    @Override
    public ScoreDelta calculateDelta(Move move, WorkingSolution solution) {
        // Simplified: just count violations after the move
        // Full implementation would track per-staff max shifts changes
        return ScoreDelta.ZERO;
    }

    @Override
    public ViolationResult validate(WorkingSolution solution) {
        int count = 0;
        java.util.List<Violation> violations = new java.util.ArrayList<>();

        LoadStatistics loadStats = solution.getStatistics().get(LoadStatistics.class);
        if (loadStats == null) return new ViolationResult(0, violations);

        for (MutableAssignment a : solution.getAllAssignments()) {
            StaffNode staff = solution.getProblem().getStaff(a.staffId);
            if (staff == null) continue;

            Integer maxShifts = staff.getMaxShiftsPerMonth();
            if (maxShifts == null) continue;

            int currentShifts = loadStats.getCountById(a.staffId);
            if (currentShifts > maxShifts) {
                String staffName = staff.getFullName();

                violations.add(new Violation(
                    name(),
                    Type.SOFT,
                    a.staffId,
                    staffName,
                    "MONTH",
                    String.format("Vượt maxShiftsPerMonth: %d > %d", currentShifts, maxShifts)
                ));
                count++;
            }
        }

        return new ViolationResult(count, violations);
    }
}
