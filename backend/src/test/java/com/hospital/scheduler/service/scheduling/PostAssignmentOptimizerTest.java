package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.service.ConflictDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Phase 5D: tests for {@link PostAssignmentOptimizer#optimizeFairnessBySafeReassignment}.
 *
 * <p>Pre-existing coverage was zero for this entry point. These tests pin
 * down the contract that the algorithm MUST:
 * <ul>
 *   <li>Reassign a shift from overloaded staff to underloaded staff when there is a gap</li>
 *   <li>Skip L01 shifts (compensation-day side effects)</li>
 *   <li>Skip moves that would violate same-day business conflicts (L01↔L02, L03↔L04)</li>
 *   <li>Skip moves that would violate adjacent-day L01 rules</li>
 *   <li>Prefer moves with larger load gaps (load-aware scoring)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PostAssignmentOptimizerTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private StaffEligibilityFilter eligibilityFilter;

    private PostAssignmentOptimizer optimizer;
    private SchedulingStateAccessor stateAccessor;

    @BeforeEach
    void setUp() {
        optimizer = new PostAssignmentOptimizer(scheduleRepository, leaveRequestRepository, eligibilityFilter);
        stateAccessor = new SchedulingStateAccessor();
        stateAccessor.reset();
        lenient().when(leaveRequestRepository.findByStaffIdAndDateRange(anyInt(), any(), any()))
                .thenReturn(List.of());
        lenient().when(eligibilityFilter.isBusinessShiftConflict(any(), any())).thenReturn(false);
        lenient().when(eligibilityFilter.isStrictMatchForStaff(any(), any())).thenReturn(true);
    }

    // ─── Test 1: basic rebalance moves overloaded to underloaded ─────

    @Test
    void optimizeFairness_reassignsOverloadedToUnderloaded() {
        // staff 1 has 4 L02 shifts, staff 2 has 0 L02 shifts → expect 1 move per round
        // The algorithm picks the largest gap (>=0.5) per round. With 4 vs 0, gap = 4.0 → 1 move.
        // After move: 3 vs 1, gap = 2.0 → still > 0.5 → another move. So 2 moves bring 4→2, 0→2.
        LocalDate d1 = LocalDate.of(2026, 8, 1);
        LocalDate d2 = LocalDate.of(2026, 8, 2);
        LocalDate d3 = LocalDate.of(2026, 8, 3);
        LocalDate d4 = LocalDate.of(2026, 8, 4);

        Staff staff1 = buildStaff(1, "Staff 1");
        Staff staff2 = buildStaff(2, "Staff 2");
        ShiftType l02 = buildShiftType("L02");

        Schedule s1 = buildSchedule(100, staff1, l02, d1, null);
        Schedule s2 = buildSchedule(101, staff1, l02, d2, null);
        Schedule s3 = buildSchedule(102, staff1, l02, d3, null);
        Schedule s4 = buildSchedule(103, staff1, l02, d4, null);

        int moves = optimizer.optimizeFairnessBySafeReassignment(
                new ArrayList<>(List.of(s1, s2, s3, s4)),
                List.of(staff1, staff2),
                List.of(),
                1,
                stateAccessor);

        assertEquals(1, moves, "1 round should produce 1 move (largest gap wins per round)");
        long staff1Shifts = List.of(s1, s2, s3, s4).stream()
                .filter(s -> s.getStaff().getId() == 1).count();
        long staff2Shifts = List.of(s1, s2, s3, s4).stream()
                .filter(s -> s.getStaff().getId() == 2).count();
        assertEquals(3, staff1Shifts);
        assertEquals(1, staff2Shifts);
    }

    // ─── Test 2: L01 shifts are locked (no rebalance) ──────────────

    @Test
    void optimizeFairness_skipsL01Schedules() {
        // 5 L01 shifts on staff 1, 0 on staff 2 → no moves (L01 locked)
        LocalDate d1 = LocalDate.of(2026, 8, 1);
        LocalDate d2 = LocalDate.of(2026, 8, 2);
        LocalDate d3 = LocalDate.of(2026, 8, 3);
        LocalDate d4 = LocalDate.of(2026, 8, 4);
        LocalDate d5 = LocalDate.of(2026, 8, 5);

        Staff staff1 = buildStaff(1, "Staff 1");
        Staff staff2 = buildStaff(2, "Staff 2");
        ShiftType l01 = buildShiftType("L01");

        List<Schedule> schedules = new ArrayList<>(List.of(
                buildSchedule(100, staff1, l01, d1, null),
                buildSchedule(101, staff1, l01, d2, null),
                buildSchedule(102, staff1, l01, d3, null),
                buildSchedule(103, staff1, l01, d4, null),
                buildSchedule(104, staff1, l01, d5, null)
        ));

        int moves = optimizer.optimizeFairnessBySafeReassignment(
                schedules, List.of(staff1, staff2), List.of(), 10, stateAccessor);

        assertEquals(0, moves, "L01 schedules must not be rebalanced");
        // All schedules still belong to staff 1
        assertTrue(schedules.stream().allMatch(s -> s.getStaff().getId() == 1));
    }

    // ─── Test 3: balanced distribution → no moves ──────────────

    @Test
    void optimizeFairness_balancedDistribution_returnsZero() {
        // Both staff have 2 L02 shifts → no gap → no moves
        LocalDate d1 = LocalDate.of(2026, 8, 1);
        LocalDate d2 = LocalDate.of(2026, 8, 2);
        LocalDate d3 = LocalDate.of(2026, 8, 3);
        LocalDate d4 = LocalDate.of(2026, 8, 4);

        Staff staff1 = buildStaff(1, "Staff 1");
        Staff staff2 = buildStaff(2, "Staff 2");
        ShiftType l02 = buildShiftType("L02");

        List<Schedule> schedules = new ArrayList<>(List.of(
                buildSchedule(100, staff1, l02, d1, null),
                buildSchedule(101, staff1, l02, d2, null),
                buildSchedule(102, staff2, l02, d3, null),
                buildSchedule(103, staff2, l02, d4, null)
        ));

        int moves = optimizer.optimizeFairnessBySafeReassignment(
                schedules, List.of(staff1, staff2), List.of(), 10, stateAccessor);

        assertEquals(0, moves, "balanced distribution should produce no moves");
    }

    // ─── Test 4: candidate with conflict is rejected ──────────────

    @Test
    void optimizeFairness_candidateWithSameDayConflict_skipped() {
        // Staff 1 has L02 on d1, d2, d3 (overloaded).
        // Staff 2 has L01 on d1 — moving L02 d1 to staff 2 would create L01↔L02 conflict.
        // d2 / d3 schedules are safe to move.
        LocalDate d1 = LocalDate.of(2026, 8, 1);
        LocalDate d2 = LocalDate.of(2026, 8, 2);
        LocalDate d3 = LocalDate.of(2026, 8, 3);

        Staff staff1 = buildStaff(1, "Staff 1");
        Staff staff2 = buildStaff(2, "Staff 2");
        ShiftType l02 = buildShiftType("L02");
        ShiftType l01 = buildShiftType("L01");

        when(eligibilityFilter.isBusinessShiftConflict("L02", "L01")).thenReturn(true);

        Schedule existingL01OnStaff2 = buildSchedule(200, staff2, l01, d1, null);
        List<Schedule> schedules = new ArrayList<>(List.of(
                existingL01OnStaff2,
                buildSchedule(100, staff1, l02, d1, null),
                buildSchedule(101, staff1, l02, d2, null),
                buildSchedule(102, staff1, l02, d3, null)
        ));

        int moves = optimizer.optimizeFairnessBySafeReassignment(
                schedules, List.of(staff1, staff2), List.of(), 1, stateAccessor);

        // 1 round picks the largest gap (always 1 L02 with 3 vs 0 → gap 3 in any d).
        // All three candidates have the same gap, so the first movable one wins.
        // That candidate is d2 or d3 (d1 is unsafe).
        assertEquals(1, moves, "1 round should pick one safe move");
        // Verify the moved schedule is NOT d1 (which would conflict)
        Schedule movedSchedule = schedules.stream()
                .filter(s -> s.getStaff().getId() == 2)
                .filter(s -> "L02".equals(s.getShiftType().getId()))
                .findFirst().orElse(null);
        assertNotNull(movedSchedule);
        assertNotEquals(d1, movedSchedule.getWorkDate(),
                "move on d1 should be blocked due to L01 conflict");
    }

    // ─── Test 5: empty input returns zero ──────────────

    @Test
    void optimizeFairness_emptySchedules_returnsZero() {
        int moves = optimizer.optimizeFairnessBySafeReassignment(
                List.of(), List.of(buildStaff(1, "S1")), List.of(), 10, stateAccessor);
        assertEquals(0, moves);
    }

    // ─── Test 6: load-aware scoring prefers L04 over L02 when both have gaps ──────────────

    @Test
    void optimizeFairness_loadAwarePrefersL04LargerGap() {
        // Staff 1 has 3 L02 and 3 L04 (same raw count).
        // Staff 2 has 0 L02 and 0 L04.
        // Load gap per type: L02 = 3*1.0 = 3.0, L04 = 3*1.5 = 4.5
        // 1 round: algorithm picks the LARGEST load gap → L04 move.
        LocalDate d1 = LocalDate.of(2026, 8, 1);
        LocalDate d2 = LocalDate.of(2026, 8, 2);
        LocalDate d3 = LocalDate.of(2026, 8, 3);
        LocalDate d4 = LocalDate.of(2026, 8, 4);
        LocalDate d5 = LocalDate.of(2026, 8, 5);
        LocalDate d6 = LocalDate.of(2026, 8, 6);

        Staff staff1 = buildStaff(1, "Staff 1");
        Staff staff2 = buildStaff(2, "Staff 2");
        ShiftType l02 = buildShiftType("L02");
        ShiftType l04 = buildShiftType("L04");

        List<Schedule> schedules = new ArrayList<>(List.of(
                buildSchedule(100, staff1, l02, d1, null),
                buildSchedule(101, staff1, l02, d2, null),
                buildSchedule(102, staff1, l02, d3, null),
                buildSchedule(103, staff1, l04, d4, null),
                buildSchedule(104, staff1, l04, d5, null),
                buildSchedule(105, staff1, l04, d6, null)
        ));

        int moves = optimizer.optimizeFairnessBySafeReassignment(
                schedules, List.of(staff1, staff2), List.of(), 1, stateAccessor);

        assertEquals(1, moves, "1 round should produce 1 move");
        Schedule movedSchedule = schedules.stream()
                .filter(s -> s.getStaff().getId() == 2)
                .findFirst().orElse(null);
        assertNotNull(movedSchedule, "staff 2 should have received one schedule");
        assertEquals("L04", movedSchedule.getShiftType().getId(),
                "load-aware scoring should pick L04 (load 4.5) over L02 (load 3.0)");
    }

    // ─── Test 7: adjacent L01 conflict blocks reassignment ──────────────

    @Test
    void optimizeFairness_isSafeReassignmentBlocksAdjacentL01() {
        // Staff 1 has L02 on d1, d2, d3 (overloaded).
        // Staff 2 has L01 on d1, d2, d3 — moving L02 to staff 2 on d1/d2/d3 would:
        //   - For d2: existing L01 on d1 (d2-1) → conflict → skip
        //   - For d3: existing L01 on d2 (d3-1) → conflict → skip
        //   - For d1: same-day L01 conflict → skip
        LocalDate d1 = LocalDate.of(2026, 8, 1);
        LocalDate d2 = LocalDate.of(2026, 8, 2);
        LocalDate d3 = LocalDate.of(2026, 8, 3);

        Staff staff1 = buildStaff(1, "Staff 1");
        Staff staff2 = buildStaff(2, "Staff 2");
        ShiftType l02 = buildShiftType("L02");
        ShiftType l01 = buildShiftType("L01");

        // Pre-set L01 on d1, d2, d3 for staff 2 → all candidates should be blocked
        List<Schedule> schedules = new ArrayList<>(List.of(
                buildSchedule(200, staff2, l01, d1, null),
                buildSchedule(201, staff2, l01, d2, null),
                buildSchedule(202, staff2, l01, d3, null),
                buildSchedule(100, staff1, l02, d1, null),
                buildSchedule(101, staff1, l02, d2, null),
                buildSchedule(102, staff1, l02, d3, null)
        ));

        int moves = optimizer.optimizeFairnessBySafeReassignment(
                schedules, List.of(staff1, staff2), List.of(), 10, stateAccessor);

        assertEquals(0, moves, "every L02 candidate on d1/d2/d3 collides with staff 2's L01 schedule");
    }

    // ─── Test 8: 2-way swap is preferred when both sides can move ──────────────

    @Test
    void optimizeFairness_swapPreferredOverOneWayReassign() {
        // Staff 1 has 3 L02 (overloaded), staff 2 has 1 L02 (underloaded).
        // Gap = 2. Swap exists: staff 1's slot on d1 swaps with staff 2's slot on d2.
        // After swap: staff 1 still has 3 L02 (1 lost, 1 gained), staff 2 still has 1 L02.
        // The swap's *value* is that it exchanges the dates each staff works, which
        // can unlock other moves in subsequent rounds. The contract tested here is
        // that the swap is performed (2 moves in 1 round) instead of a 1-way move.
        LocalDate d1 = LocalDate.of(2026, 8, 1);
        LocalDate d2 = LocalDate.of(2026, 8, 2);
        LocalDate d3 = LocalDate.of(2026, 8, 3);
        LocalDate d4 = LocalDate.of(2026, 8, 4);

        Staff staff1 = buildStaff(1, "Staff 1");
        Staff staff2 = buildStaff(2, "Staff 2");
        ShiftType l02 = buildShiftType("L02");

        List<Schedule> schedules = new ArrayList<>(List.of(
                buildSchedule(100, staff1, l02, d1, null),
                buildSchedule(101, staff2, l02, d2, null),
                buildSchedule(102, staff1, l02, d3, null),
                buildSchedule(103, staff1, l02, d4, null)
        ));

        int moves = optimizer.optimizeFairnessBySafeReassignment(
                schedules, List.of(staff1, staff2), List.of(), 1, stateAccessor);

        // Swap performs 2 moves in 1 round (vs 1-way = 1 move)
        assertEquals(2, moves, "swap should produce 2 moves in 1 round");
        // Each schedule's owner has changed (swap completed)
        Schedule s100 = schedules.stream().filter(s -> s.getId() == 100).findFirst().orElseThrow();
        Schedule s101 = schedules.stream().filter(s -> s.getId() == 101).findFirst().orElseThrow();
        assertEquals(2, s100.getStaff().getId(), "s100 now owned by staff 2");
        assertEquals(1, s101.getStaff().getId(), "s101 now owned by staff 1");
    }

    // ─── Test 9: swap not chosen when 1-way reassign gives better gap ──────────────

    @Test
    void optimizeFairness_swapFallsBackToOneWayWhenLopsided() {
        // Staff 1 has 4 L02 (overloaded), staff 2 has 0 L02 (underloaded).
        // Gap = 4. No swap candidate exists (staff 2 has 0 L02), so fall back to 1-way reassign.
        // 1 round → 1 move.
        LocalDate d1 = LocalDate.of(2026, 8, 1);
        LocalDate d2 = LocalDate.of(2026, 8, 2);
        LocalDate d3 = LocalDate.of(2026, 8, 3);
        LocalDate d4 = LocalDate.of(2026, 8, 4);

        Staff staff1 = buildStaff(1, "Staff 1");
        Staff staff2 = buildStaff(2, "Staff 2");
        ShiftType l02 = buildShiftType("L02");

        List<Schedule> schedules = new ArrayList<>(List.of(
                buildSchedule(100, staff1, l02, d1, null),
                buildSchedule(101, staff1, l02, d2, null),
                buildSchedule(102, staff1, l02, d3, null),
                buildSchedule(103, staff1, l02, d4, null)
        ));

        int moves = optimizer.optimizeFairnessBySafeReassignment(
                schedules, List.of(staff1, staff2), List.of(), 1, stateAccessor);

        assertEquals(1, moves, "1-way fallback when no swap candidate exists");
    }

    // ─── Test fixtures ──────────────────────────────────────────────

    private Staff buildStaff(int id, String name) {
        Staff s = new Staff();
        s.setId(id);
        s.setFullName(name);
        s.setMaxShiftsPerMonth(35);
        s.setIsActive(true);
        Specialty sp = new Specialty();
        sp.setId(id);
        sp.setName("Ngoại");
        s.setSpecialty(sp);
        return s;
    }

    private ShiftType buildShiftType(String id) {
        ShiftType st = new ShiftType();
        st.setId(id);
        st.setName(id);
        return st;
    }

    private Schedule buildSchedule(int id, Staff staff, ShiftType shiftType,
                                   LocalDate workDate, ShiftRequirement req) {
        return Schedule.builder()
                .id(id)
                .staff(staff)
                .shiftType(shiftType)
                .workDate(workDate)
                .requirement(req)
                .hasConflict(false)
                .isPreview(false)
                .build();
    }
}
