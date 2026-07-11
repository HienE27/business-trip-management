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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Regression: BR-06 must allow multi-slot L01 same-day for different staff.
 *
 * Bug history (pre-fix):
 *   BR-06 check missed the staff equality, so isConsistent() rejected
 *   every candidate once any L01 var on the same day was assigned,
 *   even for different staff. requiredStaffCount > 1 made CSP silently
 *   return 0 schedules.
 */
@DisplayName("CSP BR-06 regression — multi-slot L01 same day")
class CspBr06MultiSlotRegressionTest {

    private static List<Staff> nInternalStaff(int n) {
        List<Staff> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(Staff.builder()
                    .id(i + 1).username("s" + i).fullName("Staff " + i).isActive(true)
                    .specialty(Specialty.builder().id(8).name("Nội").build())
                    .maxShiftsPerMonth(20).build());
        }
        return list;
    }

    private static ProblemData buildData(int nStaff, int l01Slots) {
        HolidayRepository holidayRepo = mock(HolidayRepository.class);
        CompensationDateCalculator compCalc = new CompensationDateCalculator(holidayRepo);
        CspAc3Engine ac3 = new CspAc3Engine(compCalc);
        CspConstraints constraints = new CspConstraints(compCalc);
        CspDataBuilder builder = new CspDataBuilder(compCalc, ac3, constraints);

        List<Staff> staffList = nInternalStaff(nStaff);
        LocalDate start = LocalDate.of(2026, 8, 31);
        List<ShiftRequirementInfo> reqs = List.of(
                new ShiftRequirementInfo("L01", start, l01Slots)
        );
        List<LocalDate> dates = List.of(start);
        return builder.build(staffList, dates, reqs, Collections.emptyList(),
                List.of("Nội"));
    }

    private static CspSearchEngine.Result solve(int nStaff, int l01Slots) {
        ProblemData data = buildData(nStaff, l01Slots);
        CspSearchEngine engine = new CspSearchEngine(
                new CompensationDateCalculator(mock(HolidayRepository.class)),
                new CspNogoodStore());
        return engine.solve(data, System.currentTimeMillis(), 5_000L);
    }

    @Test
    @DisplayName("1 day, 2 L01 slots, 5 Nội staff => valid + 2 assignments")
    void twoSlots_onOneDay_findSolution() {
        CspSearchEngine.Result r = solve(5, 2);
        assertThat(r.isValid()).isTrue();
        assertThat(r.getAssignment()).hasSize(2);
    }

    @Test
    @DisplayName("1 day, 4 L01 slots, 14 Nội staff => valid + 4 assignments, 4 distinct staff")
    void fourSlots_onOneDay_findSolution() {
        CspSearchEngine.Result r = solve(14, 4);
        assertThat(r.isValid()).isTrue();
        assertThat(r.getAssignment()).hasSize(4);
        long distinctStaff = r.getAssignment().keySet().stream()
                .map(k -> k.split("\\|")[0])
                .distinct()
                .count();
        assertThat(distinctStaff).isEqualTo(4);
    }

    @Test
    @DisplayName("Single L01 slot still works")
    void singleSlot_stillWorks() {
        CspSearchEngine.Result r = solve(5, 1);
        assertThat(r.isValid()).isTrue();
        assertThat(r.getAssignment()).hasSize(1);
    }
}