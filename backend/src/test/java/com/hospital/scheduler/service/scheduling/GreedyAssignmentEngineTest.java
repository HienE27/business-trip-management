package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GreedyAssignmentEngine}'s pure-function helpers.
 *
 * <p>These exercise the algorithm building blocks that were extracted during
 * the M07 refactor:
 * <ul>
 *   <li>{@code computeFairSharePerTypeWithStaff} — fair-share per shift type</li>
 *   <li>{@code groupRequirementsByDate} — requirement grouping for day iteration</li>
 *   <li>{@code sortRequirementsByPriority} — L01 → L02 → L03 → L04 order</li>
 *   <li>{@code getStaffCountForKey} / {@code getTotalStaffCount} — workload counts</li>
 * </ul>
 */
class GreedyAssignmentEngineTest {

    @Test
    void computeFairShareWithStaff_emptyRequirementsReturnsDefaultOnes() {
        Map<String, Integer> result = GreedyAssignmentEngine.computeFairSharePerTypeWithStaff(
                List.of(), 5, List.of());

        assertEquals(1, result.get("L01"));
        assertEquals(1, result.get("L02"));
        assertEquals(1, result.get("L03"));
        assertEquals(1, result.get("L04"));
    }

    @Test
    void computeFairShareWithStaff_balancedDemand_returnsFairShare() {
        List<ShiftRequirement> reqs = buildRequirements(Map.of(
                "L01", 10,
                "L02", 10,
                "L03", 5,
                "L04", 5));
        // 10 staff pool
        Map<String, Integer> result = GreedyAssignmentEngine.computeFairSharePerTypeWithStaff(
                reqs, 10, buildStaffList(10));

        // 10/10 = 1 fairShare
        assertEquals(1, result.get("L01"));
        assertEquals(1, result.get("L02"));
        // 5/10 = 0.5 → ceil = 1
        assertEquals(1, result.get("L03"));
        assertEquals(1, result.get("L04"));
    }

    @Test
    void computeFairShareWithStaff_highDemand_returnsCappedFairShare() {
        // demand > pool
        List<ShiftRequirement> reqs = buildRequirements(Map.of("L01", 25));
        Map<String, Integer> result = GreedyAssignmentEngine.computeFairSharePerTypeWithStaff(
                reqs, 10, buildStaffList(10));

        // ceil(25/10) = 3
        assertEquals(3, result.get("L01"));
    }

    @Test
    void computeFairShareWithStaff_handlesNullStaffList() {
        List<ShiftRequirement> reqs = buildRequirements(Map.of("L01", 4));
        Map<String, Integer> result = GreedyAssignmentEngine.computeFairSharePerTypeWithStaff(
                reqs, 4, null);

        // ceil(4/4) = 1
        assertEquals(1, result.get("L01"));
    }

    @Test
    void computeFairShareWithStaff_l04PerSpecialty_returnsPerSpecialtyKey() {
        Specialty ngoai = buildSpecialty(1, "Ngoại");
        Specialty noi = buildSpecialty(2, "Nội");
        ShiftType l04 = buildShiftType("L04");

        List<ShiftRequirement> reqs = List.of(
                buildRequirement(l04, ngoai, 5, LocalDate.of(2026, 1, 1)),
                buildRequirement(l04, noi, 3, LocalDate.of(2026, 1, 1))
        );
        Map<String, Integer> result = GreedyAssignmentEngine.computeFairSharePerTypeWithStaff(
                reqs, 10, List.of());

        assertTrue(result.containsKey("L04:1"), "Should include per-specialty key for Ngoại");
        assertTrue(result.containsKey("L04:2"), "Should include per-specialty key for Nội");
        assertTrue(result.containsKey("L04"), "Should also include aggregate L04 key");
    }

    @Test
    void groupRequirementsByDate_groupsCorrectly() {
        ShiftType l01 = buildShiftType("L01");
        ShiftType l02 = buildShiftType("L02");

        List<ShiftRequirement> reqs = List.of(
                buildRequirement(l01, LocalDate.of(2026, 1, 1)),
                buildRequirement(l02, LocalDate.of(2026, 1, 1)),
                buildRequirement(l01, LocalDate.of(2026, 1, 2))
        );

        Map<java.time.LocalDate, List<ShiftRequirement>> groups = GreedyAssignmentEngine.groupRequirementsByDate(reqs);

        assertEquals(2, groups.size());
        assertEquals(2, groups.get(LocalDate.of(2026, 1, 1)).size());
        assertEquals(1, groups.get(LocalDate.of(2026, 1, 2)).size());
    }

    @Test
    void groupRequirementsByDate_emptyInput_returnsEmptyMap() {
        Map<java.time.LocalDate, List<ShiftRequirement>> result = GreedyAssignmentEngine.groupRequirementsByDate(null);
        assertTrue(result.isEmpty());

        result = GreedyAssignmentEngine.groupRequirementsByDate(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void sortRequirementsByPriority_ordersL01ToL04() {
        ShiftType l01 = buildShiftType("L01");
        ShiftType l02 = buildShiftType("L02");
        ShiftType l03 = buildShiftType("L03");
        ShiftType l04 = buildShiftType("L04");

        List<ShiftRequirement> reqs = new ArrayList<>(List.of(
                buildRequirement(l04, LocalDate.of(2026, 1, 1)),
                buildRequirement(l01, LocalDate.of(2026, 1, 1)),
                buildRequirement(l03, LocalDate.of(2026, 1, 1)),
                buildRequirement(l02, LocalDate.of(2026, 1, 1))
        ));

        List<ShiftRequirement> sorted = GreedyAssignmentEngine.sortRequirementsByPriority(reqs);

        assertEquals("L01", sorted.get(0).getShiftType().getId());
        assertEquals("L02", sorted.get(1).getShiftType().getId());
        assertEquals("L03", sorted.get(2).getShiftType().getId());
        assertEquals("L04", sorted.get(3).getShiftType().getId());
    }

    @Test
    void sortRequirementsByPriority_nullInput_returnsInput() {
        assertNull(GreedyAssignmentEngine.sortRequirementsByPriority(null));
        assertTrue(GreedyAssignmentEngine.sortRequirementsByPriority(List.of()).isEmpty());
    }

    @Test
    void getStaffCountForKey_sumsDbAndRunning() {
        Map<Integer, Map<String, Long>> dbCounts = Map.of(
                1, Map.of("L01", 2L, "L02", 1L)
        );
        Map<Integer, Map<String, Long>> running = Map.of(
                1, Map.of("L01", 3L)
        );
        assertEquals(5L, GreedyAssignmentEngine.getStaffCountForKey(1, "L01", dbCounts, running));
        // L02 has only DB count
        assertEquals(1L, GreedyAssignmentEngine.getStaffCountForKey(1, "L02", dbCounts, running));
        // L03 unknown → 0
        assertEquals(0L, GreedyAssignmentEngine.getStaffCountForKey(1, "L03", dbCounts, running));
    }

    @Test
    void getStaffCountForKey_l04SpecialtyKey_mergesDbL04Baseline() {
        Map<Integer, Map<String, Long>> dbCounts = Map.of(
                1, Map.of("L04", 4L)
        );
        Map<Integer, Map<String, Long>> running = Map.of(
                1, Map.of("L04:5", 2L)
        );
        // L04:5 should be DB(L04) + running(L04:5) = 4 + 2 = 6
        assertEquals(6L, GreedyAssignmentEngine.getStaffCountForKey(1, "L04:5", dbCounts, running));
    }

    @Test
    void getStaffCountForKey_unknownStaff_returnsZero() {
        Map<Integer, Map<String, Long>> dbCounts = Map.of();
        Map<Integer, Map<String, Long>> running = Map.of();
        assertEquals(0L, GreedyAssignmentEngine.getStaffCountForKey(99, "L01", dbCounts, running));
    }

    @Test
    void getTotalStaffCount_sumsAllTypesFromBothSources() {
        Map<Integer, Map<String, Long>> dbCounts = Map.of(
                1, Map.of("L01", 1L, "L02", 2L, "L03", 3L, "L04", 4L)
        );
        Map<Integer, Map<String, Long>> running = Map.of(
                1, Map.of("L01", 1L, "L02", 1L)
        );
        // 1+2+3+4 + 1+1 = 12
        assertEquals(12L, GreedyAssignmentEngine.getTotalStaffCount(1, dbCounts, running));
    }

    @Test
    void getTotalStaffCount_missingStaff_returnsZero() {
        assertEquals(0L, GreedyAssignmentEngine.getTotalStaffCount(99, Map.of(), Map.of()));
    }

    // ─── test fixtures ──────────────────────────────────────────────────────

    private ShiftType buildShiftType(String id) {
        ShiftType st = new ShiftType();
        st.setId(id);
        st.setName(id);
        return st;
    }

    private Specialty buildSpecialty(int id, String name) {
        Specialty sp = new Specialty();
        sp.setId(id);
        sp.setName(name);
        return sp;
    }

    private ShiftRequirement buildRequirement(ShiftType shiftType, LocalDate date) {
        return buildRequirement(shiftType, null, 1, date);
    }

    private ShiftRequirement buildRequirement(ShiftType shiftType, Specialty specialty,
                                              int requiredCount, LocalDate date) {
        ShiftRequirement req = new ShiftRequirement();
        req.setShiftType(shiftType);
        req.setSpecialty(specialty);
        req.setRequiredStaffCount(requiredCount);
        req.setWorkDate(date);
        return req;
    }

    private List<ShiftRequirement> buildRequirements(Map<String, Integer> typeToCount) {
        List<ShiftRequirement> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : typeToCount.entrySet()) {
            ShiftType shiftType = buildShiftType(entry.getKey());
            ShiftRequirement req = new ShiftRequirement();
            req.setShiftType(shiftType);
            req.setRequiredStaffCount(entry.getValue());
            req.setWorkDate(LocalDate.of(2026, 1, 1));
            result.add(req);
        }
        return result;
    }

    private List<Staff> buildStaffList(int n) {
        List<Staff> staff = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Staff s = new Staff();
            s.setId(i + 1);
            s.setFullName("Staff " + (i + 1));
            staff.add(s);
        }
        return staff;
    }
}
