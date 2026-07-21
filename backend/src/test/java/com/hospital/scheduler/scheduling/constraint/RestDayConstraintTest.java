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
 * BR-03 — RestDayConstraint (max 6 consecutive working days).
 *
 * <p>Soft constraint. Penalty = (max_run - 6) summed across all staff.
 */
class RestDayConstraintTest {

    private final RestDayConstraint rule = new RestDayConstraint();

    @Test
    void emptySolution_noPenalty() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        assertEquals(0, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void exactlySixDays_noPenalty() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        LocalDate base = LocalDate.of(2026, 6, 1);
        for (int i = 0; i < 6; i++) {
            add(sol, i + 1, 1, base.plusDays(i), "L01");
        }
        assertEquals(0, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void sevenDays_penalizedByOne() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        LocalDate base = LocalDate.of(2026, 6, 1);
        for (int i = 0; i < 7; i++) {
            add(sol, i + 1, 1, base.plusDays(i), "L01");
        }
        assertEquals(1, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void tenDays_penalizedByFour() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        LocalDate base = LocalDate.of(2026, 6, 1);
        for (int i = 0; i < 10; i++) {
            add(sol, i + 1, 1, base.plusDays(i), "L01");
        }
        assertEquals(4, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void brokenRunByRestDay_notCountedAsConsecutive() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        LocalDate base = LocalDate.of(2026, 6, 1);
        // 5 days, gap, 5 more days → two runs of 5
        for (int i = 0; i < 5; i++) {
            add(sol, i + 1, 1, base.plusDays(i), "L01");
        }
        for (int i = 0; i < 5; i++) {
            add(sol, i + 10, 1, base.plusDays(i + 7), "L01"); // skip day 5/6
        }
        assertEquals(0, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void separateStaff_separateRuns() {
        WorkingSolution sol = wrapSolution(emptyProblem(3));
        LocalDate base = LocalDate.of(2026, 6, 1);
        for (int i = 0; i < 8; i++) {
            add(sol, i + 1, 1, base.plusDays(i), "L01");
        }
        // staff 2: 3 days only
        for (int i = 0; i < 3; i++) {
            add(sol, i + 20, 2, base.plusDays(i), "L02");
        }
        assertEquals(2, rule.evaluate(sol).consecutiveDelta());
    }

    @Test
    void constraintIsSoft() {
        assertTrue(!new RestDayConstraint().isHard());
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