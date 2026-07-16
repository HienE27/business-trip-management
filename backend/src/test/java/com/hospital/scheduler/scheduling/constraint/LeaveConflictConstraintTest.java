package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static com.hospital.scheduler.scheduling.constraint.ConstraintTestSupport.problemWithLeaves;
import static com.hospital.scheduler.scheduling.constraint.ConstraintTestSupport.wrapSolution;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BR-05 — LeaveConflictConstraint.
 *
 * <p>Verifies the defense-in-depth check that flags any assignment landing on
 * a date the staff is on approved leave. Compensation days are checked by the
 * same rule (extra coverage).
 */
class LeaveConflictConstraintTest {

    private final LeaveConflictConstraint rule = new LeaveConflictConstraint();

    @Test
    void noLeaves_emptySolution_returnsZero() {
        WorkingSolution sol = wrapSolution(problemWithLeaves(java.util.Map.of()));
        assertEquals(0, rule.evaluate(sol).hardDelta());
    }

    @Test
    void noConflict_staffWorksOnNonLeaveDay_returnsZero() {
        WorkingSolution sol = wrapSolution(problemWithLeaves(java.util.Map.of(
                1, Set.of(LocalDate.of(2026, 6, 5)))));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assertEquals(0, rule.evaluate(sol).hardDelta());
    }

    @Test
    void conflict_staffWorksOnLeaveDay_violates() {
        WorkingSolution sol = wrapSolution(problemWithLeaves(java.util.Map.of(
                1, Set.of(LocalDate.of(2026, 6, 5)))));
        add(sol, 1, 1, LocalDate.of(2026, 6, 5), "L01");
        assertEquals(1, rule.evaluate(sol).hardDelta());
    }

    @Test
    void multipleConflictsAcrossMultipleStaff() {
        WorkingSolution sol = wrapSolution(problemWithLeaves(java.util.Map.of(
                1, Set.of(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 6)),
                2, Set.of(LocalDate.of(2026, 6, 5)))));
        add(sol, 1, 1, LocalDate.of(2026, 6, 5), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 6), "L02");
        add(sol, 3, 2, LocalDate.of(2026, 6, 5), "L03");
        add(sol, 4, 2, LocalDate.of(2026, 6, 7), "L03"); // not on leave
        assertEquals(3, rule.evaluate(sol).hardDelta());
    }

    @Test
    void constraintIsHard() {
        assertTrue(new LeaveConflictConstraint().isHard());
    }

    private static void add(WorkingSolution sol, int slotId, int staffId, LocalDate date, String shift) {
        MutableAssignment a = new MutableAssignment();
        a.slotId = slotId;
        a.staffId = staffId;
        a.date = date;
        a.shiftTypeId = shift;
        sol.getAssignments().add(a);
    }
}