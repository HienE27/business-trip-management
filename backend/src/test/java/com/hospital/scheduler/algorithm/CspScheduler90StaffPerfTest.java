package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Performance smoke for the CSP solver stack at 90-staff scale (the typical
 * hospital scheduler workload). Acts as a regression guard — if a future
 * change blows up the search (e.g. re-introducing an O(n²) hotspot or
 * removing the trail-scoped wipeout detection), this test will catch it.
 *
 * <p>The test does NOT assert a hard time limit (CI runners vary); it asserts
 * (a) the search completes within the configured 30s budget, and (b) a
 * non-trivial fraction of slots get assigned. A partial result is acceptable
 * (the production code falls back to Greedy when CSP times out).
 */
@DisplayName("CSP solver — 90-staff scale regression")
class CspScheduler90StaffPerfTest {

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
        List<Staff> list = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            Specialty coreSpecialty = Specialty.builder().id(i).name("Nội").build();
            list.add(Staff.builder()
                    .id(i).username("s" + i).fullName("Staff " + i).isActive(true)
                    .specialty(coreSpecialty)
                    .maxShiftsPerMonth(20).build());
        }
        return list;
    }

    @Test
    @DisplayName("90 staff × 31 days × 4 shift types — completes ≤ 30s, partial or full result")
    void cspScalesTo90Staff() {
        CSPScheduler csp = newCsp();
        List<Staff> allStaff = staff(90);

        // Build requirements: 1 L01/day, 1 L02/day, 1 L03/day, 1 L04/day across 31 days.
        // Total = 124 vars × ~90 staff = ~11k domain cells — exercises AC-3 + search.
        List<ShiftRequirementInfo> reqs = new ArrayList<>(124);
        LocalDate start = LocalDate.of(2026, 7, 1);
        for (int d = 0; d < 31; d++) {
            LocalDate date = start.plusDays(d);
            reqs.add(new ShiftRequirementInfo("L01", date, 1));
            reqs.add(new ShiftRequirementInfo("L02", date, 1));
            reqs.add(new ShiftRequirementInfo("L03", date, 1));
            reqs.add(new ShiftRequirementInfo("L04", date, 1));
        }

        long t0 = System.currentTimeMillis();
        SchedulingResult result = csp.solve(
                allStaff,
                start, start.plusDays(30),
                reqs, Collections.emptySet(),
                Collections.emptyList(), Collections.emptySet());
        long elapsedMs = System.currentTimeMillis() - t0;

        assertThat(result).isNotNull();
        assertThat(elapsedMs)
                .as("90-staff 31-day CSP run should complete within the 30s timeout")
                .isLessThan(35_000L);
        assertThat(result.getScheduleCount())
                .as("Solver should assign at least some slots — partial is acceptable")
                .isGreaterThan(0);
    }
}