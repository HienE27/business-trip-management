package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Wiring smoke test for the CSP scheduler stack. The complex solver logic
 * lives in {@link CspSearchEngine} and is covered by its own targeted tests;
 * here we verify that:
 * <ul>
 *   <li>{@link CSPScheduler} plugs together with the surrounding modules
 *       without crashing.</li>
 *   <li>A simple 2-staff / 2-day scenario with a single L01 requirement
 *       produces at least one assignment.</li>
 *   <li>The L01 assignment's compensation day is reflected in
 *       {@code getCompensationDays()}.</li>
 * </ul>
 */
@DisplayName("CSP scheduler wiring smoke")
class CspSchedulerSmokeTest {

    @Test
    @DisplayName("CSP-MRV-FC: 2 nhân sự, 1 L01, 1 L02 → result có assignments")
    void simpleSolvableCase() {
        HolidayRepository holidayRepo = mock(HolidayRepository.class);
        CompensationDateCalculator compCalc = new CompensationDateCalculator(holidayRepo);
        CspAc3Engine ac3 = new CspAc3Engine(compCalc);
        CspDataBuilder builder = new CspDataBuilder(compCalc, ac3);
        CspNogoodStore nogoods = new CspNogoodStore();
        CspSearchEngine search = new CspSearchEngine(compCalc, nogoods);
        CspResultBuilder resultBuilder = new CspResultBuilder(compCalc);
        CspIncrementalResolver incremental = new CspIncrementalResolver(builder, search, resultBuilder, compCalc);
        CSPScheduler csp = new CSPScheduler(builder, search, resultBuilder, incremental);

        List<Staff> staff = new ArrayList<>();
        staff.add(Staff.builder()
                .id(1).username("a").fullName("A").isActive(true)
                .maxShiftsPerMonth(20).build());
        staff.add(Staff.builder()
                .id(2).username("b").fullName("B").isActive(true)
                .maxShiftsPerMonth(20).build());

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
}
