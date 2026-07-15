package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.domain.StaffNode;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BR-07: Duplicate shift constraint (SOFT).
 * 
 * <p>A staff should not have multiple assignments of the same shift type on the same day.</p>
 */
public class DuplicateShiftConstraint implements Constraint {

    @Override
    public String name() {
        return "DuplicateShiftConstraint";
    }

    @Override
    public Type type() {
        return Type.SOFT;
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
            if (a == null) continue;

            // Count other assignments for same staff on same day with same type
            List<MutableAssignment> sameDay = solution.getAssignmentsByDate(a.date);
            long count = sameDay.stream()
                    .filter(other -> other != a)
                    .filter(other -> other.staffId == a.staffId)
                    .filter(other -> a.shiftTypeId.equals(other.shiftTypeId))
                    .count();

            if (count > 0) {
                delta++;
            }
        }

        return delta == 0 ? ScoreDelta.ZERO : 
            ScoreDelta.builder().softDelta(delta).build();
    }

    @Override
    public ViolationResult validate(WorkingSolution solution) {
        int count = 0;
        java.util.List<Violation> violations = new java.util.ArrayList<>();

        // Group by staff and date
        Map<String, List<MutableAssignment>> byStaffDate = solution.getAllAssignments().stream()
                .collect(Collectors.groupingBy(a -> a.staffId + "|" + a.date));

        for (var entry : byStaffDate.entrySet()) {
            List<MutableAssignment> assignments = entry.getValue();
            
            // Count duplicates by shift type
            Map<String, Long> byType = assignments.stream()
                    .collect(Collectors.groupingBy(a -> a.shiftTypeId, Collectors.counting()));

            for (var typeEntry : byType.entrySet()) {
                if (typeEntry.getValue() > 1) {
                    // Get first assignment for context
                    MutableAssignment first = assignments.get(0);
                    StaffNode staff = solution.getProblem().getStaff(first.staffId);
                    String staffName = staff != null ? staff.getFullName() : "Staff#" + first.staffId;

                    violations.add(new Violation(
                        name(),
                        Type.SOFT,
                        first.staffId,
                        staffName,
                        first.date.toString(),
                        String.format("Trùng lịch %s cùng ngày (%d lần)", 
                            typeEntry.getKey(), typeEntry.getValue())
                    ));
                    count++;
                }
            }
        }

        return new ViolationResult(count, violations);
    }
}
