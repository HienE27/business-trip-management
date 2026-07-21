package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.hospital.scheduler.scheduling.constraint.ConstraintTestSupport.emptyProblem;
import static com.hospital.scheduler.scheduling.constraint.ConstraintTestSupport.wrapSolution;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BR-07 — DuplicateShiftConstraint (defense-in-depth on top of DB UNIQUE).
 *
 * <p>Penalizes any case where the same staff is assigned the same shift type
 * twice on the same date.
 */
class DuplicateShiftConstraintTest {

    private final DuplicateShiftConstraint rule = new DuplicateShiftConstraint();

    @Test
    void emptySolution_noPenalty() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        assertEquals(0, rule.evaluate(sol).hardDelta());
    }

    @Test
    void distinctShifts_noPenalty() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 1), "L02"); // different type, same staff/day
        assertEquals(0, rule.evaluate(sol).hardDelta());
    }

    @Test
    void sameStaffSameShiftSameDayTwice_violates() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 1), "L01");
        assertEquals(1, rule.evaluate(sol).hardDelta());
    }

    @Test
    void sameShiftDifferentDay_noPenalty() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 2), "L01");
        assertEquals(0, rule.evaluate(sol).hardDelta());
    }

    @Test
    void sameShiftDifferentStaff_noPenalty() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 2, LocalDate.of(2026, 6, 1), "L01");
        assertEquals(0, rule.evaluate(sol).hardDelta());
    }

    @Test
    void multipleDuplicatesCountedSeparately() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 1), "L01"); // dup
        add(sol, 3, 1, LocalDate.of(2026, 6, 1), "L01"); // dup
        add(sol, 4, 2, LocalDate.of(2026, 6, 1), "L02");
        add(sol, 5, 2, LocalDate.of(2026, 6, 1), "L02"); // dup
        assertEquals(3, rule.evaluate(sol).hardDelta());
    }

    @Test
    void constraintIsHard() {
        assertTrue(new DuplicateShiftConstraint().isHard());
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