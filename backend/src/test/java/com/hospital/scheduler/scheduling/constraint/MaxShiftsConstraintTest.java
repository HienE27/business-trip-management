package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BR-06 — MaxShiftsConstraint.
 *
 * <p>Each staff member with shifts above their {@code maxShiftsPerMonth} cap
 * adds a soft penalty equal to the overflow count.
 */
class MaxShiftsConstraintTest {

    private final MaxShiftsConstraint rule = new MaxShiftsConstraint();

    @Test
    void emptySolution_noPenalty() {
        WorkingSolution sol = wrap(problemWithCap(5));
        assertEquals(0, rule.evaluate(sol).gapDelta());
    }

    @Test
    void atCap_noPenalty() {
        WorkingSolution sol = wrap(problemWithCap(5));
        for (int i = 0; i < 5; i++) {
            add(sol, i + 1, 1, LocalDate.of(2026, 6, 1 + i), "L01");
        }
        assertEquals(0, rule.evaluate(sol).gapDelta());
    }

    @Test
    void overCapByOne_penalizedByOne() {
        WorkingSolution sol = wrap(problemWithCap(5));
        for (int i = 0; i < 6; i++) {
            add(sol, i + 1, 1, LocalDate.of(2026, 6, 1 + i), "L01");
        }
        assertEquals(1, rule.evaluate(sol).gapDelta());
    }

    @Test
    void overCapByThree_penalizedByThree() {
        WorkingSolution sol = wrap(problemWithCap(5));
        for (int i = 0; i < 8; i++) {
            add(sol, i + 1, 1, LocalDate.of(2026, 6, 1 + i), "L01");
        }
        assertEquals(3, rule.evaluate(sol).gapDelta());
    }

    @Test
    void multipleStaffEachOverCap() {
        WorkingSolution sol = wrap(problemWithCaps(java.util.Map.of(1, 3, 2, 2)));
        // Staff 1: 4 shifts → +1
        for (int i = 0; i < 4; i++) add(sol, i + 1, 1, LocalDate.of(2026, 6, 1 + i), "L01");
        // Staff 2: 5 shifts → +3
        for (int i = 0; i < 5; i++) add(sol, i + 10, 2, LocalDate.of(2026, 6, 1 + i), "L02");
        assertEquals(4, rule.evaluate(sol).gapDelta());
    }

    @Test
    void constraintIsSoft() {
        assertTrue(!new MaxShiftsConstraint().isHard());
    }

    private static SchedulingProblem problemWithCap(int cap) {
        return problemWithCaps(java.util.Map.of(1, cap));
    }

    private static SchedulingProblem problemWithCaps(java.util.Map<Integer, Integer> caps) {
        List<Staff> staff = new ArrayList<>();
        for (var entry : caps.entrySet()) {
            Staff s = new Staff();
            s.setId(entry.getKey());
            s.setFullName("Staff " + entry.getKey());
            s.setIsActive(true);
            s.setMaxShiftsPerMonth(entry.getValue());
            staff.add(s);
        }
        return SchedulingProblem.from(
                staff, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new HashSet<>(), new SchedulingConfig());
    }

    private static WorkingSolution wrap(SchedulingProblem problem) {
        return ConstraintTestSupport.wrapSolution(problem);
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