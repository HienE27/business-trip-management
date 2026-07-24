package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F03 hard-cap verification: ensure each scheduler respects
 * {@code AlgorithmRuntimeConfig.maxShiftsPerStaff} on a tight instance
 * (small staff pool, daily multi-shift requirements, low cap).
 *
 * <p>Output of each test is also echoed to stdout so the actual per-staff
 * counts become part of the test execution record.
 */
@DisplayName("F03 — maxShiftsPerStaff hard cap enforcement per scheduler")
class MaxShiftsPerStaffHardCapTest {

    private static final int STAFF_COUNT = 5;
    private static final int PERIOD_DAYS = 14;
    private static final int MAX_SHIFTS_PER_STAFF = 5;

    private static CompensationDateCalculator compCalc() {
        HolidayRepository holidayRepo = mock(HolidayRepository.class);
        when(holidayRepo.findActiveHolidaysBetween(any(), any())).thenReturn(Collections.emptyList());
        return new CompensationDateCalculator(holidayRepo);
    }

    private static List<Staff> staff(int n) {
        List<Staff> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            // All staff share one specialty so L04 (which is specialty-bound) is also assignable.
            Specialty sp = Specialty.builder().id(1).name("Nội").build();
            list.add(Staff.builder()
                    .id(i).username("s" + i).fullName("Staff " + i).isActive(true)
                    .specialty(sp)
                    .maxShiftsPerMonth(20).build());
        }
        return list;
    }

    private static Map<String, ShiftType> shiftTypes() {
        Map<String, ShiftType> m = new LinkedHashMap<>();
        for (String id : new String[]{"L01", "L02", "L03", "L04"}) {
            m.put(id, ShiftType.builder().id(id).name(id).build());
        }
        return m;
    }

    private static SchedulePeriod period() {
        return SchedulePeriod.builder()
                .id(1)
                .periodName("HardCapTest")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 1).plusDays(PERIOD_DAYS - 1))
                .build();
    }

    private static List<ShiftRequirement> denseReqs(SchedulePeriod p, Map<String, ShiftType> st) {
        // Per day we generate L02, L03, L04 + L01 on alternate days (avoid back-to-back).
        // Each requirement request count = STAFF_COUNT so the solver has plenty of demand.
        // The total demand (3*14 + 7 = 49 slots * 5 staff each = ~245 capacity) vastly
        // exceeds the available cap (5 staff * 5 shifts = 25), so any staff hitting the
        // cap should be excluded rather than over-assigned.
        List<ShiftRequirement> out = new ArrayList<>();
        LocalDate start = p.getStartDate();
        for (int i = 0; i < PERIOD_DAYS; i++) {
            LocalDate d = start.plusDays(i);
            if (i % 2 == 0) {
                out.add(req(p, st.get("L01"), d, STAFF_COUNT, null));
            }
            out.add(req(p, st.get("L02"), d, STAFF_COUNT, null));
            out.add(req(p, st.get("L03"), d, STAFF_COUNT, null));
            out.add(req(p, st.get("L04"), d, STAFF_COUNT,
                    Specialty.builder().id(1).name("Nội").build()));
        }
        return out;
    }

    private static ShiftRequirement req(SchedulePeriod p, ShiftType st, LocalDate d, int count, Specialty sp) {
        return ShiftRequirement.builder()
                .period(p).workDate(d).shiftType(st).requiredStaffCount(count).specialty(sp).build();
    }

    private static AlgorithmConfigService.AlgorithmRuntimeConfig tightCapConfig() {
        return AlgorithmConfigService.AlgorithmRuntimeConfig.builder()
                .weekendWeight(new java.math.BigDecimal("2"))
                .overnightRecoveryHours(24)
                .greedyCoverageThreshold(new java.math.BigDecimal("0.85"))
                .balanceScoreMin(new java.math.BigDecimal("0.70"))
                .autoCompensationEnabled(true)
                .maxStaffPerShift(5)
                .maxShiftsPerStaff(MAX_SHIFTS_PER_STAFF) // ← the cap under test
                .maxShiftsPerDay(0)
                .l01MaxPerWeek(0).l02MaxPerWeek(0).l03MaxPerWeek(0).l04MaxPerWeek(0)
                .beamWidth(5)
                .autoAdjustConfig(false) // off so cap stays at 5
                .coverageWeight(new java.math.BigDecimal("0.40"))
                .fairnessWeight(new java.math.BigDecimal("0.35"))
                .constraintWeight(new java.math.BigDecimal("0.25"))
                .build();
    }

    private static void assertHardCapRespected(String label, List<Schedule> schedules) {
        Map<Integer, Integer> perStaff = new TreeMap<>();
        for (Schedule s : schedules) {
            perStaff.merge(s.getStaff().getId(), 1, Integer::sum);
        }
        int max = perStaff.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Integer> over = perStaff.entrySet().stream()
                .filter(e -> e.getValue() > MAX_SHIFTS_PER_STAFF)
                .map(Map.Entry::getKey).toList();

        // Echo per-staff distribution so the test execution log shows the actual behaviour
        // of each scheduler — used as evidence in the acceptance report.
        System.out.printf("[%s] total=%d  unique_staff=%d  perStaff=%s  max=%d  over_cap=%s%n",
                label, schedules.size(), perStaff.size(), perStaff, max,
                over.isEmpty() ? "NONE" : over);

        assertThat(over)
                .as("[%s] staff exceeding maxShiftsPerStaff=%d: %s",
                        label, MAX_SHIFTS_PER_STAFF, over)
                .isEmpty();
    }

    @Test
    @DisplayName("EnhancedGreedy — respects maxShiftsPerStaff")
    void enhancedGreedy_respectsCap() {
        EnhancedGreedyScheduler s = new EnhancedGreedyScheduler(compCalc());
        List<Schedule> r = s.solve(staff(STAFF_COUNT), denseReqs(period(), shiftTypes()),
                period(), tightCapConfig(), Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
        assertThat(r).isNotNull();
        assertHardCapRespected("EnhancedGreedy", r);
    }

    @Test
    @DisplayName("BeamSearch — respects maxShiftsPerStaff")
    void beamSearch_respectsCap() {
        BeamSearchScheduler s = new BeamSearchScheduler(compCalc());
        List<Schedule> r = s.solve(staff(STAFF_COUNT), denseReqs(period(), shiftTypes()),
                period(), tightCapConfig(), Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
        assertThat(r).isNotNull();
        assertHardCapRespected("BeamSearch", r);
    }

    @Test
    @DisplayName("RandomRestartHC — respects maxShiftsPerStaff")
    void randomRestartHC_respectsCap() {
        RandomRestartHCScheduler s = new RandomRestartHCScheduler(compCalc());
        List<Schedule> r = s.solve(staff(STAFF_COUNT), denseReqs(period(), shiftTypes()),
                period(), tightCapConfig(), Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
        assertThat(r).isNotNull();
        assertHardCapRespected("RandomRestartHC", r);
    }

    @Test
    @DisplayName("SimulatedAnnealing — respects maxShiftsPerStaff")
    void simulatedAnnealing_respectsCap() {
        SimulatedAnnealingScheduler s = new SimulatedAnnealingScheduler(compCalc());
        List<Schedule> r = s.solve(staff(STAFF_COUNT), denseReqs(period(), shiftTypes()),
                period(), tightCapConfig(), Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
        assertThat(r).isNotNull();
        assertHardCapRespected("SimulatedAnnealing", r);
    }

    @Test
    @DisplayName("CpSat — respects maxShiftsPerStaff")
    void cpSat_respectsCap() {
        CpSatScheduler s = new CpSatScheduler(compCalc());
        List<Schedule> r = s.solve(staff(STAFF_COUNT), denseReqs(period(), shiftTypes()),
                period(), tightCapConfig(), Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
        assertThat(r).isNotNull();
        assertHardCapRespected("CpSat", r);
    }
}
