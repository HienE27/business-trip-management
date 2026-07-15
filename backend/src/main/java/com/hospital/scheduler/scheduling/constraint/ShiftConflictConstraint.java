package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.domain.StaffNode;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.util.List;

/**
 * BR-01 and BR-02: Shift type conflict constraint.
 * 
 * <ul>
 *   <li>BR-01 (HARD): L01 + L02 same staff same day = VIOLATION</li>
 *   <li>BR-02 (HARD): L03 + L04 same staff same day = VIOLATION</li>
 * </ul>
 */
public class ShiftConflictConstraint implements Constraint {

    @Override
    public String name() {
        return "ShiftConflictConstraint";
    }

    @Override
    public Type type() {
        return Type.HARD;
    }

    @Override
    public boolean isApplicable(Move move) {
        return move.type() == Move.MoveType.ASSIGN ||
               move.type() == Move.MoveType.MOVE ||
               move.type() == Move.MoveType.SWAP;
    }

    @Override
    public ScoreDelta calculateDelta(Move move, WorkingSolution solution) {
        int delta = 0;

        for (int slotId : move.affectedSlotIdsAsList()) {
            MutableAssignment a = solution.getAssignment(slotId);
            if (a == null) continue;

            String typeA = a.shiftTypeId;
            if (!conflictsWithOther(typeA)) continue;

            // Check other assignments for same staff on same day
            List<MutableAssignment> sameDay = solution.getAssignmentsByDate(a.date);
            for (MutableAssignment other : sameDay) {
                if (other == a || other.staffId != a.staffId) continue;
                
                String typeB = other.shiftTypeId;
                if (conflicts(typeA, typeB)) {
                    delta++;
                    break;
                }
            }
        }

        return delta == 0 ? ScoreDelta.ZERO : 
            ScoreDelta.builder().hardDelta(delta).build();
    }

    private boolean conflictsWithOther(String type) {
        return "L01".equals(type) || "L02".equals(type) || 
               "L03".equals(type) || "L04".equals(type);
    }

    private boolean conflicts(String t1, String t2) {
        if (t1.equals(t2)) return false;
        // BR-01: L01 ↔ L02
        if (("L01".equals(t1) && "L02".equals(t2)) ||
            ("L02".equals(t1) && "L01".equals(t2))) return true;
        // BR-02: L03 ↔ L04
        if (("L03".equals(t1) && "L04".equals(t2)) ||
            ("L04".equals(t1) && "L03".equals(t2))) return true;
        return false;
    }

    @Override
    public ViolationResult validate(WorkingSolution solution) {
        int count = 0;
        java.util.List<Violation> violations = new java.util.ArrayList<>();

        for (MutableAssignment a : solution.getAllAssignments()) {
            if (!conflictsWithOther(a.shiftTypeId)) continue;

            List<MutableAssignment> sameDay = solution.getAssignmentsByDate(a.date);
            for (MutableAssignment other : sameDay) {
                if (other == a || other.staffId != a.staffId) continue;
                
                String typeB = other.shiftTypeId;
                if (conflicts(a.shiftTypeId, typeB)) {
                    String rule = ("L01".equals(a.shiftTypeId) || "L01".equals(typeB)) 
                        ? "BR-01" : "BR-02";
                    
                    StaffNode staff = solution.getProblem().getStaff(a.staffId);
                    String staffName = staff != null ? staff.getFullName() : "Staff#" + a.staffId;

                    violations.add(new Violation(
                        name(),
                        Type.HARD,
                        a.staffId,
                        staffName,
                        a.date.toString(),
                        String.format("Xung đột %s↔%s cùng ngày", a.shiftTypeId, typeB)
                    ));
                    count++;
                }
            }
        }

        return new ViolationResult(count, violations);
    }
}
