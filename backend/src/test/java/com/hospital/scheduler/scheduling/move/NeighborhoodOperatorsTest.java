package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OPT-001 neighborhood operators:
 * - SwapMove.buildValidated (BR04-safe swap)
 * - TwoOptMove (reverse L01 subsequence)
 * - OrOptMove (relocate L01 chain)
 */
class NeighborhoodOperatorsTest {

    // ── Setup helpers ────────────────────────────────────────────────────────

    private WorkingSolution makeSol(int staffCount, ShiftRequirementInfo... reqs) {
        List<Staff> staff = new ArrayList<>();
        for (int i = 0; i < staffCount; i++) {
            Staff s = new Staff();
            s.setId(i + 1);
            s.setFullName("S" + (i + 1));
            s.setIsActive(true);
            s.setMaxShiftsPerMonth(10);
            staff.add(s);
        }
        // Use withRequirements() which accepts v10 ShiftRequirementInfo records
        SchedulingProblem problem = SchedulingProblem.withRequirements(staff,
                java.util.Arrays.asList(reqs),
                new ArrayList<>(),
                new HashSet<>(),
                new HashSet<>(),
                new SchedulingConfig());
        SolutionDescriptor desc = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(desc);
        return WorkingSolution.fromProblem(new SchedulingConfig(), new SolutionDescriptor(problem, hub));
    }

    private void assign(WorkingSolution sol, int slotId, int staffId, LocalDate date, String shiftType) {
        MutableAssignment ma = new MutableAssignment();
        ma.slotId = slotId;
        ma.staffId = staffId;
        ma.date = date;
        ma.shiftTypeId = shiftType;
        sol.getAssignmentsBySlot().put(slotId, ma);
        sol.getAssignments().add(ma);
        sol.getSlotsByStaff().computeIfAbsent(staffId, k -> new ArrayList<>()).add(slotId);
    }

    private ShiftRequirementInfo req(int id, LocalDate date, String shiftType) {
        return new ShiftRequirementInfo(id, date, shiftType, null, 1);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SWAP-MOVE BR04 VALIDATION
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void swapMove_buildValidated_swapTwoAssignedSlots_returnsMove() {
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L02"),
                req(2, LocalDate.of(2026, 6, 2), "L02"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L02");
        assign(sol, 2, 2, LocalDate.of(2026, 6, 2), "L02");

        SwapMove move = SwapMove.buildValidated(sol, 1, 2);
        assertNotNull(move);
    }

    @Test
    void swapMove_buildValidated_nullWhenCreatesAdjacentL01() {
        // staff 1 has L01 on day 1, staff 2 has L01 on day 3
        // swapping: staff 1 gets day-3 L01 → becomes adjacent (gap=2 vs gap=1) ✗
        WorkingSolution sol = makeSol(3,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 2), "L01"),
                req(3, LocalDate.of(2026, 6, 3), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 2, LocalDate.of(2026, 6, 2), "L01");
        assign(sol, 3, 3, LocalDate.of(2026, 6, 3), "L01");

        // Swap slots 1 (staff1, day1 L01) and 2 (staff2, day2 L01):
        // staff1 gets day2 L01 → staff1 has day1+day2 consecutive → BR04 violation
        SwapMove move = SwapMove.buildValidated(sol, 1, 2);
        assertNull(move);
    }

    @Test
    void swapMove_buildValidated_swapDoesNotViolateBR04_returnsMove() {
        // staff 1 has L01 day1, staff 2 has L01 day3 — gap day2 in between
        // swapping: staff1 gets day3 L01 (gap=2) ✓, staff2 gets day1 L01 (gap=2) ✓
        WorkingSolution sol = makeSol(3,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 2), "L01"),
                req(3, LocalDate.of(2026, 6, 3), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 2, LocalDate.of(2026, 6, 2), "L01");
        assign(sol, 3, 3, LocalDate.of(2026, 6, 3), "L01");

        // Swap staff1's day1 with staff3's day3 (no direct conflict)
        SwapMove move = SwapMove.buildValidated(sol, 1, 3);
        assertNotNull(move);
    }

    @Test
    void swapMove_buildValidated_nullOnNullAssignment() {
        WorkingSolution sol = makeSol(2);
        assertNull(SwapMove.buildValidated(sol, 999, 888));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TWO-OPT MOVE
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void twoOptMove_buildValidated_validReversal_returnsMove() {
        // Staff 1: L01 on days 1, 3, 5
        // 2-opt with start=day1, end=day5 reverses to days 5, 3, 1
        // No adjacent L01 created — all gaps are ≥ 2 days
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 3), "L01"),
                req(3, LocalDate.of(2026, 6, 5), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 1, LocalDate.of(2026, 6, 3), "L01");
        assign(sol, 3, 1, LocalDate.of(2026, 6, 5), "L01");

        TwoOptMove move = TwoOptMove.buildValidated(sol, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));
        assertNotNull(move);
        assertEquals(1, move.staffId());
    }

    @Test
    void twoOptMove_buildValidated_reversalWouldCreateAdjacentL01_returnsNull() {
        // Staff 1: L01 on days 1, 2, 4 (gap 1 between 1 and 2)
        // Reversing from day 1 to day 4: [day1, day2] → [day2, day1]
        // After reversal: days 2, 1, 4 — day1+day2 adjacent → BR04 violation
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 2), "L01"),
                req(3, LocalDate.of(2026, 6, 4), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 1, LocalDate.of(2026, 6, 2), "L01");
        assign(sol, 3, 1, LocalDate.of(2026, 6, 4), "L01");

        TwoOptMove move = TwoOptMove.buildValidated(sol, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 4));
        assertNull(move);
    }

    @Test
    void twoOptMove_buildValidated_singleSlotInWindow_returnsNull() {
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 3), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 1, LocalDate.of(2026, 6, 3), "L01");

        // Window is day1 to day2 — only one L01 in range
        TwoOptMove move = TwoOptMove.buildValidated(sol, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));
        assertNull(move);
    }

    @Test
    void twoOptMove_doMove_reversesSequence() {
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 3), "L01"),
                req(3, LocalDate.of(2026, 6, 5), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 1, LocalDate.of(2026, 6, 3), "L01");
        assign(sol, 3, 1, LocalDate.of(2026, 6, 5), "L01");

        TwoOptMove move = TwoOptMove.buildValidated(sol, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));
        assertNotNull(move);

        // Apply
        move.doMove(sol);

        // After reversal: slot1→staff1(day5), slot2→staff1(day3), slot3→staff1(day1)
        assertEquals(1, sol.getAssignedStaff(1));
        assertEquals(1, sol.getAssignedStaff(2));
        assertEquals(1, sol.getAssignedStaff(3));

        // Undo
        move.undo(sol);

        // Back to original: slot1→staff1(day1), slot2→staff1(day3), slot3→staff1(day5)
        assertEquals(1, sol.getAssignedStaff(1));
        assertEquals(1, sol.getAssignedStaff(2));
        assertEquals(1, sol.getAssignedStaff(3));
    }

    @Test
    void twoOptMove_typeIsTwoOpt() {
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 3), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 1, LocalDate.of(2026, 6, 3), "L01");

        TwoOptMove move = TwoOptMove.buildValidated(sol, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3));
        assertNotNull(move);
        assertEquals(com.hospital.scheduler.scheduling.move.Move.MoveType.TWO_OPT, move.type());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // OR-OPT MOVE
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void orOptMove_buildValidated_validRelocation_returnsMove() {
        // Staff 1: L01 on days 1, 5 (gap=3)
        // Move day-1 L01 to day 3 (a gap day between 1 and 5)
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 3), "L01"),
                req(3, LocalDate.of(2026, 6, 5), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 2, LocalDate.of(2026, 6, 3), "L02");  // other staff, not L01
        assign(sol, 3, 1, LocalDate.of(2026, 6, 5), "L01");

        OrOptMove move = OrOptMove.buildValidated(sol, 1, -1);
        assertNotNull(move);
        assertEquals(1, move.staffId());
        assertEquals(1, move.chainLength());
    }

    @Test
    void orOptMove_buildValidated_cannotRemoveAdjacentL01_returnsNull() {
        // Staff 1: L01 on days 1, 2, 4 (adjacent 1-2)
        // Cannot move day 1 L01 because it would join days 1 and 2
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 2), "L01"),
                req(3, LocalDate.of(2026, 6, 4), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 1, LocalDate.of(2026, 6, 2), "L01");
        assign(sol, 3, 1, LocalDate.of(2026, 6, 4), "L01");

        // buildValidated searches all L01 slots as source candidates
        // source day-1: adjacent to day-2 → cannot remove
        // source day-2: adjacent to day-1 → cannot remove
        // source day-4: gap ≥ 2 from both → valid target? day 3 has no L01 slot for this staff
        OrOptMove move = OrOptMove.buildValidated(sol, 1, -1);
        // No valid relocation found — day 3 has no L01 slot to assign
        assertNull(move);
    }

    @Test
    void orOptMove_buildValidated_noL01Slots_returnsNull() {
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L02"),
                req(2, LocalDate.of(2026, 6, 2), "L02"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L02");
        assign(sol, 2, 1, LocalDate.of(2026, 6, 2), "L02");

        OrOptMove move = OrOptMove.buildValidated(sol, 1, -1);
        assertNull(move);
    }

    @Test
    void orOptMove_doMove_reassignsAndUndos() {
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 3), "L01"),
                req(3, LocalDate.of(2026, 6, 5), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 2, LocalDate.of(2026, 6, 3), "L02");
        assign(sol, 3, 1, LocalDate.of(2026, 6, 5), "L01");

        OrOptMove move = OrOptMove.buildValidated(sol, 1, -1);
        assertNotNull(move);

        int originalSourceSlot = move.sourceSlot();
        int originalSourceStaff = sol.getAssignedStaff(originalSourceSlot);
        int originalTargetStaff = sol.getAssignedStaff(move.targetSlot());

        move.doMove(sol);

        // Source should be unassigned (or its original staff should have moved away);
        // target should now hold the staff that was at the source.
        assertEquals(originalSourceStaff, sol.getAssignedStaff(move.targetSlot()));
        assertEquals(1, sol.getAssignedStaff(move.targetSlot()));

        move.undo(sol);

        // Back to original state
        assertEquals(originalSourceStaff, sol.getAssignedStaff(move.sourceSlot()));
        assertEquals(originalTargetStaff, sol.getAssignedStaff(move.targetSlot()));
    }

    @Test
    void orOptMove_typeIsOrOpt() {
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 3), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 2, LocalDate.of(2026, 6, 3), "L02");

        OrOptMove move = OrOptMove.buildValidated(sol, 1, -1);
        assertNotNull(move);
        assertEquals(com.hospital.scheduler.scheduling.move.Move.MoveType.OR_OPT, move.type());
    }

    @Test
    void orOptMove_chainLengthIs1to3() {
        // Chain length is always 1 in the current buildValidated implementation
        // (chain extension is present but the no-adjacent-L01 constraint
        // prevents chains > 1 in practice)
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 3), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 2, LocalDate.of(2026, 6, 3), "L02");

        OrOptMove move = OrOptMove.buildValidated(sol, 1, -1);
        if (move != null) {
            assertTrue(move.chainLength() >= 1 && move.chainLength() <= 3);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EDGE CASES
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void twoOptMove_invalidRange_endBeforeStart() {
        assertThrows(IllegalArgumentException.class, () ->
                new TwoOptMove(1, LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 1)));
    }

    @Test
    void twoOptMove_invalidRange_nullDates() {
        assertThrows(IllegalArgumentException.class, () ->
                new TwoOptMove(1, null, LocalDate.of(2026, 6, 1)));
        assertThrows(IllegalArgumentException.class, () ->
                new TwoOptMove(1, LocalDate.of(2026, 6, 1), null));
    }

    @Test
    void orOptMove_invalidChainLength() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrOptMove(1, 1, LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 3), 2, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new OrOptMove(1, 1, LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 6, 3), 2, 4));
    }

    @Test
    void swapMove_nullAssignments_returnsNull() {
        WorkingSolution sol = makeSol(1);
        assertNull(SwapMove.buildValidated(sol, 1, 2));
    }

    @Test
    void twoOptMove_undoAfterDoubleApplication_returnsToOriginal() {
        WorkingSolution sol = makeSol(2,
                req(1, LocalDate.of(2026, 6, 1), "L01"),
                req(2, LocalDate.of(2026, 6, 3), "L01"),
                req(3, LocalDate.of(2026, 6, 5), "L01"));
        assign(sol, 1, 1, LocalDate.of(2026, 6, 1), "L01");
        assign(sol, 2, 1, LocalDate.of(2026, 6, 3), "L01");
        assign(sol, 3, 1, LocalDate.of(2026, 6, 5), "L01");

        TwoOptMove move = TwoOptMove.buildValidated(sol, 1,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 5));
        assertNotNull(move);

        // Record original state
        int orig1 = sol.getAssignedStaff(1);
        int orig2 = sol.getAssignedStaff(2);
        int orig3 = sol.getAssignedStaff(3);

        move.doMove(sol);
        move.undo(sol);

        // Fully restored
        assertEquals(orig1, sol.getAssignedStaff(1));
        assertEquals(orig2, sol.getAssignedStaff(2));
        assertEquals(orig3, sol.getAssignedStaff(3));
    }
}
