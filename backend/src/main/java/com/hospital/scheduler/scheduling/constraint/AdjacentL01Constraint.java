package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.domain.StaffNode;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.util.List;

/**
 * BR-04: Adjacent L01 constraint.
 * 
 * <p>A staff cannot have L01 (24/24) on consecutive days.
 * This ensures proper rest between heavy shifts.</p>
 */
public class AdjacentL01Constraint implements Constraint {

    @Override
    public String name() {
        return "AdjacentL01Constraint";
    }

    @Override
    public Type type() {
        return Type.HARD;
    }

    @Override
    public boolean isApplicable(Move move) {
        return move.type() == Move.MoveType.ASSIGN ||
               move.type() == Move.MoveType.MOVE;
    }

    @Override
    public ScoreDelta calculateDelta(Move move, WorkingSolution solution) {
        int delta = 0;

        for (int slotId : move.affectedSlotIdsAsList()) {
            MutableAssignment a = solution.getAssignment(slotId);
            if (a == null || !a.isL01()) continue;

            // Check previous day
            if (hasAdjacentL01(a.staffId, a.date.minusDays(1), solution)) {
                delta++;
            }
            // Check next day
            if (hasAdjacentL01(a.staffId, a.date.plusDays(1), solution)) {
                delta++;
            }
        }

        return delta == 0 ? ScoreDelta.ZERO : 
            ScoreDelta.builder().hardDelta(delta).build();
    }

    private boolean hasAdjacentL01(int staffId, java.time.LocalDate date, WorkingSolution solution) {
        List<MutableAssignment> assignments = solution.getAssignmentsByDate(date);
        return assignments.stream()
                .anyMatch(a -> a.staffId == staffId && a.isL01());
    }

    @Override
    public ViolationResult validate(WorkingSolution solution) {
        int count = 0;
        java.util.List<Violation> violations = new java.util.ArrayList<>();

        for (MutableAssignment a : solution.getAllAssignments()) {
            if (!a.isL01()) continue;

            // Check previous day
            List<MutableAssignment> prevDay = solution.getAssignmentsByDate(a.date.minusDays(1));
            for (MutableAssignment prev : prevDay) {
                if (prev.staffId == a.staffId && prev.isL01()) {
                    StaffNode staff = solution.getProblem().getStaff(a.staffId);
                    String staffName = staff != null ? staff.getFullName() : "Staff#" + a.staffId;

                    violations.add(new Violation(
                        name(),
                        Type.HARD,
                        a.staffId,
                        staffName,
                        a.date.toString(),
                        String.format("Trực 24/24 liên tiếp (%s và %s)", 
                            prev.date, a.date)
                    ));
                    count++;
                    break;
                }
            }

            // Check next day
            List<MutableAssignment> nextDay = solution.getAssignmentsByDate(a.date.plusDays(1));
            for (MutableAssignment next : nextDay) {
                if (next.staffId == a.staffId && next.isL01()) {
                    // Only add if not already counted from previous day
                    final int finalCount = count;
                    if (violations.stream().noneMatch(v -> 
                            v.staffId() == a.staffId && v.date().equals(a.date.toString()))) {
                        StaffNode staff = solution.getProblem().getStaff(a.staffId);
                        String staffName = staff != null ? staff.getFullName() : "Staff#" + a.staffId;

                        violations.add(new Violation(
                            name(),
                            Type.HARD,
                            a.staffId,
                            staffName,
                            a.date.toString(),
                            String.format("Trực 24/24 liên tiếp (%s và %s)", 
                                a.date, next.date)
                        ));
                        count++;
                    }
                    break;
                }
            }
        }

        return new ViolationResult(count, violations);
    }
}
