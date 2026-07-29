package com.hospital.scheduler.scheduling.alns;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DestroyOperatorsTest {

    @Test
    void worstRemovesHighestCostsFirst() {
        WorkingSolution solution = solution(10, 20, 30);

        int removed = DestroyOperators.worst(index -> new int[]{1, 9, 5}[index])
                .destroy(solution, 2);

        assertEquals(2, removed);
        assertEquals(1, solution.getAssignedStaff(10));
        assertEquals(-1, solution.getAssignedStaff(20));
        assertEquals(-1, solution.getAssignedStaff(30));
    }

    @Test
    void worstBreaksTiesByLowestIndex() {
        WorkingSolution solution = solution(10, 20, 30);

        DestroyOperators.worst(index -> 7).destroy(solution, 2);

        assertEquals(-1, solution.getAssignedStaff(10));
        assertEquals(-1, solution.getAssignedStaff(20));
        assertEquals(1, solution.getAssignedStaff(30));
    }

    @Test
    void worstClampsRemovalToSafeBounds() {
        WorkingSolution solution = solution(10, 20, 30);
        var operator = DestroyOperators.worst(index -> index);

        assertEquals(0, operator.destroy(solution, -1));
        assertEquals(3, operator.destroy(solution, 99));
        assertEquals(0, operator.destroy(solution, 99));
    }

    private static WorkingSolution solution(int... slotIds) {
        LocalDate date = LocalDate.of(2026, 1, 1);
        List<ShiftRequirementInfo> requirements = java.util.Arrays.stream(slotIds)
                .mapToObj(id -> new ShiftRequirementInfo(id, date, "L02", null, 1))
                .toList();
        SchedulingConfig config = new SchedulingConfig();
        SchedulingProblem problem = SchedulingProblem.withRequirements(
                List.of(), requirements, List.of(), Set.of(), Set.of(), config);
        WorkingSolution solution = WorkingSolution.fromProblem(config, new SolutionDescriptor(problem, null));
        for (int slotId : slotIds) solution.assign(slotId, 1);
        return solution;
    }
}
