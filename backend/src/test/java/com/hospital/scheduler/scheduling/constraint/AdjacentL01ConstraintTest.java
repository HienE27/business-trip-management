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
 * BR-04 — AdjacentL01Constraint.
 *
 * <p>Soft penalty for consecutive L01 shifts (each pair counts once).
 */
class AdjacentL01ConstraintTest {

    private final AdjacentL01Constraint rule = new AdjacentL01Constraint();

    @Test
    void noL01_returnsZero() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L02");
        add(sol, 2, 1, LocalDate.of(2026, 6, 2), "L03");
        assertEquals(0, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void singleL01_returnsZero() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assertEquals(0, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void twoConsecutiveL01_penalizedOnce() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 2), "L01");
        assertEquals(1, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void threeConsecutiveL01_penalizedTwice() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 2), "L01");
        add(sol, 3, 1, LocalDate.of(2026, 6, 3), "L01");
        assertEquals(2, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void brokenL01Run_notCounted() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        // gap day
        add(sol, 3, 1, LocalDate.of(2026, 6, 3), "L01");
        assertEquals(0, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void separateStaff_separateRuns() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        add(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 2, 1, LocalDate.of(2026, 6, 2), "L01");
        add(sol, 3, 2, LocalDate.of(2026, 6, 1), "L01");
        add(sol, 4, 2, LocalDate.of(2026, 6, 2), "L01");
        assertEquals(2, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void constraintIsSoft() {
        assertTrue(!new AdjacentL01Constraint().isHard());
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