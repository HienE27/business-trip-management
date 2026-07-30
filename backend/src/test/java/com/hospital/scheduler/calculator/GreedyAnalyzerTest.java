package com.hospital.scheduler.calculator;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.scheduling.config.ConfigDomain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for {@link GreedyAnalyzer} cap semantics.
 *
 * <p>Covers:
 * <ul>
 *   <li><b>cap behavior</b>: with a positive {@code maxShiftsPerStaff},
 *       assignment count is bounded by {@code min(reqCount, cap)} even when
 *       more eligible staff exist.</li>
 *   <li><b>unlimited behavior</b>: with {@code maxShiftsPerStaff == 0}
 *       (the documented "no cap" sentinel), every eligible staff can take a
 *       slot up to the requirement count.</li>
 * </ul>
 *
 * <p>The analyzer uses an in-memory round-robin simulation. The per-staff
 * cap is enforced by {@code collectEligibleStaff} against an incrementing
 * {@code staffShiftCount} map. These tests pin that behavior.
 */
@DisplayName("GreedyAnalyzer — per-staff cap behavior")
class GreedyAnalyzerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 7); // Monday

    private static SchedulePeriod oneDayPeriod() {
        return SchedulePeriod.builder()
                .id(1)
                .periodName("cap-test")
                .startDate(DATE)
                .endDate(DATE)
                .build();
    }

    private static List<Staff> fourEligibleStaff() {
        Specialty noi = Specialty.builder().id(1).name("Nội").build();
        List<Staff> list = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            list.add(Staff.builder()
                    .id(i)
                    .username("s" + i)
                    .fullName("Staff " + i)
                    .isActive(true)
                    .specialty(noi)
                    .maxShiftsPerMonth(20)
                    .build());
        }
        return list;
    }

    @Test
    @DisplayName("cap=2 + requirement=2 → exactly 2 assignments (cap is the binding limit)")
    void capsAssignmentsAtMaxStaffPerShift() {
        GreedyAnalyzer analyzer = new GreedyAnalyzer();

        ConfigDomain config = ConfigDomain.builder()
                .maxShiftsPerStaff(2)
                .build();

        AutoGenConfig autoGen = AutoGenConfig.builder()
                .l01MinPerDay(2)
                .l01MaxPerDay(2)
                .removedShiftTypes(List.of("L02", "L03", "L04"))
                .build();

        CapacityAnalysis result = analyzer.analyze(
                oneDayPeriod(),
                fourEligibleStaff(),
                List.<LeaveRequest>of(),
                List.<Holiday>of(),
                List.<Specialty>of(),
                List.of(),
                config,
                autoGen);

        assertThat(result.getTotalAssigned())
                .as("maxShiftsPerStaff=2 with reqCount=2 → exactly 2 assignments")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("cap=0 (unlimited) + requirement=4 → all 4 staff assigned (no cap binding)")
    void zeroMaxStaffPerShiftLeavesAssignmentsUnlimited() {
        GreedyAnalyzer analyzer = new GreedyAnalyzer();

        ConfigDomain config = ConfigDomain.builder()
                .maxShiftsPerStaff(0)
                .build();

        AutoGenConfig autoGen = AutoGenConfig.builder()
                .l01MinPerDay(4)
                .l01MaxPerDay(0)
                .removedShiftTypes(List.of("L02", "L03", "L04"))
                .build();

        CapacityAnalysis result = analyzer.analyze(
                oneDayPeriod(),
                fourEligibleStaff(),
                List.<LeaveRequest>of(),
                List.<Holiday>of(),
                List.<Specialty>of(),
                List.of(),
                config,
                autoGen);

        assertThat(result.getTotalAssigned())
                .as("maxShiftsPerStaff=0 (unlimited) with 4 eligible staff → all 4 assigned")
                .isEqualTo(4);
    }
}