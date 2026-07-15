package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.StaffNode;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AssignMove.
 */
class AssignMoveTest {

    private SolutionDescriptor descriptor;
    private WorkingSolution solution;

    @BeforeEach
    void setUp() {
        SchedulingConfig config = new SchedulingConfig();
        
        List<StaffNode> staffList = List.of(
                StaffNode.builder().id(1).fullName("Staff 1").eligibleShiftTypes(java.util.Set.of("L01", "L02", "L03", "L04")).isActive(true).build(),
                StaffNode.builder().id(2).fullName("Staff 2").eligibleShiftTypes(java.util.Set.of("L01", "L02", "L03", "L04")).isActive(true).build()
        );
        
        LocalDate date = LocalDate.of(2026, 7, 15);
        
        List<ShiftRequirementInfo> requirements = List.of(
                ShiftRequirementInfo.builder()
                        .slotId(1)
                        .date(date)
                        .shiftTypeId("L01")
                        .hours(24)
                        .requiredStaffCount(1)
                        .build()
        );
        
        SchedulingProblem problem = SchedulingProblem.builder()
                .staffList(staffList)
                .requirements(requirements)
                .config(config)
                .build();
        
        descriptor = new SolutionDescriptor(problem);
        solution = WorkingSolution.fromProblem(problem, config);
    }

    @Test
    void testMoveKey() {
        AssignMove move = new AssignMove(1, 1, descriptor);
        
        String key = move.moveKey();
        
        assertNotNull(key);
        assertTrue(key.startsWith("A:"));
    }

    @Test
    void testMoveType() {
        AssignMove move = new AssignMove(1, 1, descriptor);
        
        assertEquals(Move.MoveType.ASSIGN, move.type());
    }

    @Test
    void testAffectedSlots() {
        AssignMove move = new AssignMove(1, 1, descriptor);
        
        int[] slots = move.affectedSlotIndices();
        
        assertEquals(1, slots.length);
    }

    @Test
    void testDoAndUndo() {
        AssignMove move = new AssignMove(1, 1, descriptor);
        
        // Initially unassigned
        assertNull(solution.getAssignment(1));
        
        // Apply
        move.doMove(solution);
        assertNotNull(solution.getAssignment(1));
        assertEquals(1, solution.getAssignment(1).staffId);
        
        // Undo
        move.undo(solution);
        assertNull(solution.getAssignment(1));
    }

    @Test
    void testReassignment() {
        AssignMove move1 = new AssignMove(1, 1, descriptor);
        AssignMove move2 = new AssignMove(1, 2, descriptor);
        
        // Assign to staff 1
        move1.doMove(solution);
        assertEquals(1, solution.getAssignment(1).staffId);
        
        // Reassign to staff 2
        move2.doMove(solution);
        assertEquals(2, solution.getAssignment(1).staffId);
    }

    @Test
    void testEstimatedImprovement() {
        AssignMove move = new AssignMove(1, 1, descriptor);
        
        double improvement = move.estimatedImprovement();
        
        assertTrue(improvement > 0);
    }
}
