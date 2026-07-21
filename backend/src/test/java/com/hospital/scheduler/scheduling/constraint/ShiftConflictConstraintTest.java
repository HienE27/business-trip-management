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
 * BR-01 / BR-02 / BR-07 — ShiftConflictConstraint.
 *
 * <ul>
 *   <li>BR-01: same staff, same day, both L01 and L02 → violation</li>
 *   <li>BR-02: same staff, same day, both L03 and L04 → violation</li>
 *   <li>BR-07 (subset): same staff, same day, same shift type twice → counted by DuplicateShift</li>
 * </ul>
 */
class ShiftConflictConstraintTest {

    private final ShiftConflictConstraint rule = new ShiftConflictConstraint();

    @Test
    void noConflict_emptySolution_returnsZero() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        ScoreDelta d = rule.evaluate(sol);
        assertEquals(0, d.hardDelta());
        assertTrue(Double.isFinite(d.coverageDelta()));
    }

    @Test
    void noConflict_singleShiftPerDay_returnsZero() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 2), "L02");
        assertEquals(0, rule.evaluate(sol).hardDelta());
    }

    @Test
    void br01_l01AndL02SameDay_sameStaff_violates() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 1), "L02");
        assertEquals(1, rule.evaluate(sol).hardDelta());
    }

    @Test
    void br01_l01AndL02SameDay_differentStaff_noViolation() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 2, LocalDate.of(2026, 6, 1), "L02");
        assertEquals(0, rule.evaluate(sol).hardDelta());
    }

    @Test
    void br02_l03AndL04SameDay_sameStaff_violates() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L03");
        add(sol, 2, 1, LocalDate.of(2026, 6, 1), "L04");
        assertEquals(1, rule.evaluate(sol).hardDelta());
    }

    @Test
    void br02_l03AndL04SameDay_differentStaff_noViolation() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L03");
        add(sol, 2, 2, LocalDate.of(2026, 6, 1), "L04");
        assertEquals(0, rule.evaluate(sol).hardDelta());
    }

    @Test
    void br01AndBr02_independentDays_noCrossDayViolation() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 2), "L02");
        add(sol, 3, 1, LocalDate.of(2026, 6, 3), "L03");
        add(sol, 4, 1, LocalDate.of(2026, 6, 3), "L04");
        assertEquals(1, rule.evaluate(sol).hardDelta());
    }

    @Test
    void br01AndBr02_multipleViolationsCountedSeparately() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        // Day 1: staff 1 has both L01 + L02 → 1 violation
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 1), "L02");
        // Day 2: staff 1 has both L03 + L04 → 1 violation
        add(sol, 3, 1, LocalDate.of(2026, 6, 2), "L03");
        add(sol, 4, 1, LocalDate.of(2026, 6, 2), "L04");
        // Day 3: staff 2 has both L01 + L02 → 1 violation
        add(sol, 5, 2, LocalDate.of(2026, 6, 3), "L01");
        add(sol, 6, 2, LocalDate.of(2026, 6, 3), "L02");
        assertEquals(3, rule.evaluate(sol).hardDelta());
    }

    @Test
    void constraintIsHard() {
        assertTrue(new ShiftConflictConstraint().isHard());
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