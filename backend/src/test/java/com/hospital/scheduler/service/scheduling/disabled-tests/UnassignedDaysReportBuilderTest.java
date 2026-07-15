package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UnassignedDaysReportBuilder}.
 *
 * <p>Verifies that partially-covered requirements surface in the report and
 * that the wrapped report carries period metadata.
 */
class UnassignedDaysReportBuilderTest {

    private final UnassignedDaysReportBuilder builder = new UnassignedDaysReportBuilder();

    private ShiftRequirement req(LocalDate date, String shiftTypeId, int required) {
        ShiftType type = new ShiftType();
        type.setId(shiftTypeId);
        type.setName("Name-" + shiftTypeId);
        ShiftRequirement r = new ShiftRequirement();
        r.setWorkDate(date);
        r.setShiftType(type);
        r.setRequiredStaffCount(required);
        return r;
    }

    private Schedule sched(LocalDate date, String shiftTypeId, int staffId) {
        ShiftType type = new ShiftType();
        type.setId(shiftTypeId);
        Staff staff = new Staff();
        staff.setId(staffId);
        Schedule s = new Schedule();
        s.setWorkDate(date);
        s.setShiftType(type);
        s.setStaff(staff);
        return s;
    }

    @Test
    void buildUnassignedDays_understaffedRequirementSurfacesWithReason() {
        LocalDate monday = LocalDate.of(2026, 7, 6);
        ShiftRequirement r = req(monday, "L01", 3);
        Schedule s1 = sched(monday, "L01", 1);
        Schedule s2 = sched(monday, "L01", 2);

        List<Map<String, Object>> result = builder.buildUnassignedDays(List.of(r), List.of(s1, s2));

        assertEquals(1, result.size());
        Map<String, Object> entry = result.get(0);
        assertEquals(monday, entry.get("workDate"));
        assertEquals(3, entry.get("requiredStaffCount"));
        assertEquals(2, entry.get("assignedStaffCount"));
        assertEquals(1, entry.get("missingCount"));
    }

    @Test
    void buildUnassignedDays_fullyCoveredRequirementIsAbsent() {
        LocalDate d = LocalDate.of(2026, 7, 6);
        ShiftRequirement r = req(d, "L01", 2);
        Schedule s1 = sched(d, "L01", 1);
        Schedule s2 = sched(d, "L01", 2);

        List<Map<String, Object>> result = builder.buildUnassignedDays(List.of(r), List.of(s1, s2));

        assertTrue(result.isEmpty());
    }

    @Test
    void buildUnassignedDays_severityCriticalWhenAssignedIsZero() {
        LocalDate d = LocalDate.of(2026, 7, 6);
        ShiftRequirement r = req(d, "L01", 2);

        List<Map<String, Object>> result = builder.buildUnassignedDays(List.of(r), List.of());

        assertEquals(1, result.size());
        assertEquals("critical", result.get(0).get("severity"));
        assertEquals("NO_ELIGIBLE_STAFF", result.get(0).get("reasonCode"));
    }

    @Test
    void buildUnassignedDays_l04SpecialtyUsesNoSpecialtyStaffCode() {
        LocalDate d = LocalDate.of(2026, 7, 6);
        Specialty specialty = new Specialty();
        specialty.setName("Mắt");
        ShiftRequirement r = req(d, "L04", 1);
        r.setSpecialty(specialty);

        List<Map<String, Object>> result = builder.buildUnassignedDays(List.of(r), List.of());

        assertEquals("NO_SPECIALTY_STAFF", result.get(0).get("reasonCode"));
    }

    @Test
    void buildReport_carriesPeriodMetadataAndSortsByMissingDesc() {
        SchedulePeriod period = new SchedulePeriod();
        period.setId(7);
        period.setPeriodName("Tháng 7");
        period.setStartDate(LocalDate.of(2026, 7, 1));
        period.setEndDate(LocalDate.of(2026, 7, 31));

        ShiftRequirement r1 = req(LocalDate.of(2026, 7, 6), "L01", 3);  // 0 assigned -> missing=3
        ShiftRequirement r2 = req(LocalDate.of(2026, 7, 7), "L02", 2);  // 1 assigned -> missing=1
        Schedule partial = sched(LocalDate.of(2026, 7, 7), "L02", 99);

        Map<String, Object> report = builder.buildReport(period, List.of(r1, r2), List.of(partial));

        assertEquals(7, report.get("periodId"));
        assertEquals("Tháng 7", report.get("periodName"));
        assertEquals(2, report.get("totalUnassignedDays"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) report.get("unassignedDays");
        assertEquals(3, days.get(0).get("missingCount"), "Higher missing count should sort first");
        assertEquals(1, days.get(1).get("missingCount"));
    }

    @Test
    void buildReport_sameMissingCount_breaksTieByDateAsc() {
        SchedulePeriod period = new SchedulePeriod();
        period.setId(1);
        period.setPeriodName("P");
        period.setStartDate(LocalDate.of(2026, 7, 1));
        period.setEndDate(LocalDate.of(2026, 7, 31));

        ShiftRequirement r1 = req(LocalDate.of(2026, 7, 10), "L01", 2);
        ShiftRequirement r2 = req(LocalDate.of(2026, 7, 5),  "L01", 2);

        Map<String, Object> report = builder.buildReport(period, List.of(r1, r2), List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> days = (List<Map<String, Object>>) report.get("unassignedDays");
        assertEquals(LocalDate.of(2026, 7, 5), days.get(0).get("workDate"),
                "Tie-break: earlier date first");
        assertEquals(LocalDate.of(2026, 7, 10), days.get(1).get("workDate"));
    }
}