package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.domain.StaffNode;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.util.List;

/**
 * BR-03: Rest day constraint.
 * 
 * <p>A staff cannot be assigned any shift on their compensation day.</p>
 */
public class RestDayConstraint implements Constraint {

    @Override
    public String name() {
        return "RestDayConstraint";
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
            if (a == null) continue;

            // Check if this date is a compensation day for this staff
            if (solution.getProblem().isCompensationDay(a.staffId, a.date)) {
                delta++;
            }
        }

        return delta == 0 ? ScoreDelta.ZERO : 
            ScoreDelta.builder().hardDelta(delta).build();
    }

    @Override
    public ViolationResult validate(WorkingSolution solution) {
        int count = 0;
        java.util.List<Violation> violations = new java.util.ArrayList<>();

        for (MutableAssignment a : solution.getAllAssignments()) {
            if (solution.getProblem().isCompensationDay(a.staffId, a.date)) {
                StaffNode staff = solution.getProblem().getStaff(a.staffId);
                String staffName = staff != null ? staff.getFullName() : "Staff#" + a.staffId;

                violations.add(new Violation(
                    name(),
                    Type.HARD,
                    a.staffId,
                    staffName,
                    a.date.toString(),
                    String.format("Lịch %s được xếp vào ngày nghỉ bù", a.shiftTypeId)
                ));
                count++;
            }
        }

        return new ViolationResult(count, violations);
    }
}
