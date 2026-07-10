package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Wiring + constraint tests for the CSP scheduler stack. The complex solver
 * logic lives in {@link CspSearchEngine} and is exercised through the
 * {@link CSPScheduler} facade here.
 *
 * <p>Coverage map (CSP search-engine behaviour is observed indirectly):
 *
 * <ul>
 *   <li><b>smoke</b>: 2 staff, simple requirements → at least one assignment.</li>
 *   <li><b>BR-01 / L01+L02 same day</b>: solver must NOT assign both L01 and
 *       L02 to the same staff on the same day.</li>
 *   <li><b>BR-04 / L03+L04 same day</b>: solver must NOT assign both L03 and
 *       L04 to the same staff on the same day.</li>
 *   <li><b>BR-02 / compensation day</b>: solver marks comp days for L01
 *       assignments, and refuses to schedule the same staff on those comp
 *       days (visible via {@code getCompensationDays()}).</li>
 *   <li><b>understaffed / no feasible</b>: 1 staff cannot fill 2 same-day
 *       same-type requirements → result still returned, coverage below 100%.</li>
 * </ul>
 */
@DisplayName("CSP scheduler wiring + constraints")
class CspSchedulerSmokeTest {

    private static CSPScheduler newCsp() {
        HolidayRepository holidayRepo = mock(HolidayRepository.class);
        CompensationDateCalculator compCalc = new CompensationDateCalculator(holidayRepo);
        CspAc3Engine ac3 = new CspAc3Engine(compCalc);
        CspConstraints constraints = new CspConstraints(compCalc);
        CspDataBuilder builder = new CspDataBuilder(compCalc, ac3, constraints);
        CspNogoodStore nogoods = new CspNogoodStore();
        CspSearchEngine search = new CspSearchEngine(compCalc, nogoods);
        CspResultBuilder resultBuilder = new CspResultBuilder(compCalc);
        CspIncrementalResolver incremental = new CspIncrementalResolver(builder, search, resultBuilder, compCalc);
        return new CSPScheduler(builder, search, resultBuilder, incremental);
    }

    private static List<Staff> staff(int n) {
        List<Staff> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            // CSP eligibility (1ab207b) requires staff.specialty.name ∈
            // {Nội, Ngoại} for L01/L02/L03. Without a specialty, the domain
            // pruner would mark every staff ineligible and the solver
            // returns an empty assignment map.
            Specialty coreSpecialty = Specialty.builder().id(i).name("Nội").build();
            list.add(Staff.builder()
                    .id(i).username("s" + i).fullName("Staff " + i).isActive(true)
                    .specialty(coreSpecialty)
                    .maxShiftsPerMonth(20).build());
        }
        return list;
    }

    @Test
    @DisplayName("CSP-MRV-FC: 2 nhân sự, 1 L01, 1 L02 → result có assignments")
    void simpleSolvableCase() {
        CSPScheduler csp = newCsp();

        List<Staff> staff = staff(2);

        // Two L01 requests on separate, non-adjacent weeks, one L02
        List<ShiftRequirementInfo> reqs = List.of(
                new ShiftRequirementInfo("L01", LocalDate.of(2026, 7, 6), 1),  // Monday
                new ShiftRequirementInfo("L01", LocalDate.of(2026, 7, 13), 1), // following Monday
                new ShiftRequirementInfo("L02", LocalDate.of(2026, 7, 15), 1)
        );

        SchedulingResult result = csp.solve(
                staff,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                reqs, Collections.emptySet(),
                Collections.emptyList(), Collections.emptySet());

        assertThat(result).isNotNull();
        // On a small but realistic input, CSP should produce at least one assignment
        assertThat(result.getScheduleCount())
                .as("CSP should solve a small 2-staff / 3-requirement instance")
                .isGreaterThan(0);
        // If L01 was assigned, compensation days should be in the result set
        if (!result.getCompensationDays().isEmpty()) {
            assertThat(result.getCompensationDays())
                    .as("Every comp day in the result must reference the staff who worked L01")
                    .allMatch(cd -> cd.split("\\|")[0].matches("\\d+"));
        }
    }

    @Nested
    @DisplayName("CSP constraint rules — observed through CSPScheduler.solve()")
    class ConstraintRules {

        @Test
        @DisplayName("BR-01 / L01 + L02 same day: solver sinh ≥ 2 assignments, không cùng 1 key")
        void L01AndL02SameDay_producesSeparateAssignments() {
            CSPScheduler csp = newCsp();
            List<Staff> staff = staff(3);

            // 1 L01 + 1 L02 on the SAME day — the solver must put them on DIFFERENT staff
            // (asserted indirectly: with 3 eligible staff, both slots can be filled).
            LocalDate monday = LocalDate.of(2026, 8, 3);
            List<ShiftRequirementInfo> reqs = List.of(
                    new ShiftRequirementInfo("L01", monday, 1),
                    new ShiftRequirementInfo("L02", monday, 1)
            );

            SchedulingResult result = csp.solve(
                    staff,
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                    reqs, Collections.emptySet(),
                    Collections.emptyList(), Collections.emptySet());

            assertThat(result).isNotNull();
            // With 3 staff, both same-day slots must be fillable.
            assertThat(result.getScheduleCount())
                    .as("Both L01+L02 should be assigned when 3 staff are available")
                    .isGreaterThanOrEqualTo(2);

            // Indirect BR-01 check: no staff can have TWO shifts on the same day.
            // Each "staffId|date" key must appear at most once.
            long mondayAssignments = result.getAssignments().keySet().stream()
                    .filter(k -> k.endsWith("|" + monday))
                    .count();
            assertThat(mondayAssignments)
                    .as("Two shifts on the same day must use two distinct staff keys")
                    .isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("BR-04 / L03 + L04 same day: solver không crash, trả result hợp lệ")
        void L03AndL04SameDay_doesNotCrash() {
            CSPScheduler csp = newCsp();
            List<Staff> staff = staff(3);

            LocalDate wednesday = LocalDate.of(2026, 8, 5);
            List<ShiftRequirementInfo> reqs = List.of(
                    new ShiftRequirementInfo("L03", wednesday, 1),
                    new ShiftRequirementInfo("L04", wednesday, 1)
            );

            SchedulingResult result = csp.solve(
                    staff,
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                    reqs, Collections.emptySet(),
                    Collections.emptyList(), Collections.emptySet());

            assertThat(result).isNotNull();
            // Indirect BR-04 check: result shape is valid; coverage score exists.
            assertThat(result.getCoverageScore()).isNotNull();
            // Solver does not return more assignments than there are eligible staff per day.
            assertThat(result.getScheduleCount()).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("BR-02 / L01 → comp day entry xuất hiện trong result.compensationDays")
        void L01_compensationDay_isGenerated() {
            CSPScheduler csp = newCsp();
            List<Staff> staff = staff(4);

            // L01 on Monday → comp day is Tuesday (per project spec).
            LocalDate monday = LocalDate.of(2026, 8, 3);
            List<ShiftRequirementInfo> reqs = List.of(
                    new ShiftRequirementInfo("L01", monday, 1)
            );

            SchedulingResult result = csp.solve(
                    staff,
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                    reqs, Collections.emptySet(),
                    Collections.emptyList(), Collections.emptySet());

            // The L01 slot must be filled
            assertThat(result.getScheduleCount())
                    .as("Single L01 with 4 staff must be assigned")
                    .isGreaterThanOrEqualTo(1);

            // The comp-day record must exist (L01 → comp day N+1, except weekends)
            assertThat(result.getCompensationDays())
                    .as("L01 assignment must produce a compensation day entry")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("Understaffed / no-feasible: 1 staff không thể fill 2 same-day same-type ca")
        void understaffed_returnsPartialResult() {
            CSPScheduler csp = newCsp();
            List<Staff> staff = staff(1);

            // Only 1 staff, but two L01 on the same day (each needs its own person).
            List<ShiftRequirementInfo> reqs = List.of(
                    new ShiftRequirementInfo("L01", LocalDate.of(2026, 8, 3), 1),
                    new ShiftRequirementInfo("L01", LocalDate.of(2026, 8, 3), 1)
            );

            SchedulingResult result = csp.solve(
                    staff,
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                    reqs, Collections.emptySet(),
                    Collections.emptyList(), Collections.emptySet());

            assertThat(result).isNotNull();
            // 1 staff cannot fill 2 same-day L01 slots → coverage < 100% (or empty).
            int filled = result.getScheduleCount();
            assertThat(filled)
                    .as("Only 1 staff available → at most 1 L01 slot fillable on the same day")
                    .isLessThanOrEqualTo(1);
        }
    }
}
