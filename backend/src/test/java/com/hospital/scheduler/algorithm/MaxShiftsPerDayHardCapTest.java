package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F01/F03 hard-cap verification: ensure each scheduler respects
 * {@code AlgorithmRuntimeConfig.maxShiftsPerDay} on a tight instance.
 *
 * <p>Setup: 5 staff, 7 days period, every day has L01+L02+L03+L04 each with
 * requiredStaffCount=5 (high demand per slot). Without maxShiftsPerDay,
 * a single staff could end up with 4+ shifts/day. With cap={1,2,3}, no staff
 * should have more than cap shifts on any day.
 *
 * <p>Each parameterized run prints per-staff-per-day max to stdout
 * for evidence in the audit log.
 */
@DisplayName("F03 — maxShiftsPerDay hard cap enforcement per scheduler")
class MaxShiftsPerDayHardCapTest {

    private static final int STAFF_COUNT = 5;
    private static final int PERIOD_DAYS = 7;

    private static CompensationDateCalculator compCalc() {
        HolidayRepository holidayRepo = mock(HolidayRepository.class);
        when(holidayRepo.findActiveHolidaysBetween(any(), any())).thenReturn(Collections.emptyList());
        return new CompensationDateCalculator(holidayRepo);
    }

    private static List<Staff> staff(int n) {
        List<Staff> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Specialty sp = Specialty.builder().id(1).name("Nội").build();
            list.add(Staff.builder()
                    .id(i).username("s" + i).fullName("Staff " + i).isActive(true)
                    .specialty(sp)
                    .maxShiftsPerMonth(50)
                    .build());
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
                .periodName("MaxShiftsPerDayTest")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 1).plusDays(PERIOD_DAYS - 1))
                .build();
    }

    private static List<ShiftRequirement> denseReqs(SchedulePeriod p, Map<String, ShiftType> st) {
        List<ShiftRequirement> out = new ArrayList<>();
        LocalDate start = p.getStartDate();
        for (int i = 0; i < PERIOD_DAYS; i++) {
            LocalDate d = start.plusDays(i);
            out.add(req(p, st.get("L01"), d, STAFF_COUNT, null));
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

    private static AlgorithmConfigService.AlgorithmRuntimeConfig configWith(int maxShiftsPerDay) {
        return AlgorithmConfigService.AlgorithmRuntimeConfig.builder()
                .weekendWeight(new BigDecimal("2"))
                .overnightRecoveryHours(24)
                .greedyCoverageThreshold(new BigDecimal("0.85"))
                .balanceScoreMin(new BigDecimal("0.70"))
                .autoCompensationEnabled(true)
                .maxStaffPerShift(0)
                .maxShiftsPerStaff(50) // high so it doesn't bind
                .maxShiftsPerDay(maxShiftsPerDay) // ← the cap under test
                .l01MaxPerWeek(0).l02MaxPerWeek(0).l03MaxPerWeek(0).l04MaxPerWeek(0)
                .beamWidth(5)
                .autoAdjustConfig(false)
                .coverageWeight(new BigDecimal("0.40"))
                .fairnessWeight(new BigDecimal("0.35"))
                .constraintWeight(new BigDecimal("0.25"))
                .build();
    }

    private static void assertMaxShiftsPerDayRespected(String label, int cap, List<Schedule> schedules) {
        Map<Integer, Map<LocalDate, Integer>> perStaffDayCount = new HashMap<>();
        for (Schedule s : schedules) {
            perStaffDayCount
                    .computeIfAbsent(s.getStaff().getId(), k -> new HashMap<>())
                    .merge(s.getWorkDate(), 1, Integer::sum);
        }
        int maxObserved = 0;
        List<String> violations = new ArrayList<>();
        for (var entry : perStaffDayCount.entrySet()) {
            int sid = entry.getKey();
            for (var dayEntry : entry.getValue().entrySet()) {
                int c = dayEntry.getValue();
                if (c > maxObserved) maxObserved = c;
                if (c > cap) {
                    violations.add("staff=" + sid + " date=" + dayEntry.getKey() + " count=" + c);
                }
            }
        }
        System.out.printf("[%s] cap=%d total=%d staffDays=%d max=%d violations=%s%n",
                label, cap, schedules.size(), perStaffDayCount.size(), maxObserved,
                violations.isEmpty() ? "NONE" : violations);

        assertThat(violations)
                .as("[%s] staff exceeding maxShiftsPerDay=%d: %s", label, cap, violations)
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("EnhancedGreedy — respects maxShiftsPerDay")
    void enhancedGreedy_respectsCap(int cap) {
        EnhancedGreedyScheduler s = new EnhancedGreedyScheduler(compCalc());
        List<Schedule> r = s.solve(staff(STAFF_COUNT), denseReqs(period(), shiftTypes()),
                period(), configWith(cap), Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
        assertThat(r).isNotNull();
        assertMaxShiftsPerDayRespected("EnhancedGreedy", cap, r);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("BeamSearch — respects maxShiftsPerDay")
    void beamSearch_respectsCap(int cap) {
        BeamSearchScheduler s = new BeamSearchScheduler(compCalc());
        List<Schedule> r = s.solve(staff(STAFF_COUNT), denseReqs(period(), shiftTypes()),
                period(), configWith(cap), Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
        assertThat(r).isNotNull();
        assertMaxShiftsPerDayRespected("BeamSearch", cap, r);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("RandomRestartHC — respects maxShiftsPerDay")
    void randomRestartHC_respectsCap(int cap) {
        RandomRestartHCScheduler s = new RandomRestartHCScheduler(compCalc());
        List<Schedule> r = s.solve(staff(STAFF_COUNT), denseReqs(period(), shiftTypes()),
                period(), configWith(cap), Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
        assertThat(r).isNotNull();
        assertMaxShiftsPerDayRespected("RandomRestartHC", cap, r);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("SimulatedAnnealing — respects maxShiftsPerDay")
    void simulatedAnnealing_respectsCap(int cap) {
        SimulatedAnnealingScheduler s = new SimulatedAnnealingScheduler(compCalc());
        List<Schedule> r = s.solve(staff(STAFF_COUNT), denseReqs(period(), shiftTypes()),
                period(), configWith(cap), Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
        assertThat(r).isNotNull();
        assertMaxShiftsPerDayRespected("SimulatedAnnealing", cap, r);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("CpSat — respects maxShiftsPerDay")
    void cpSat_respectsCap(int cap) {
        CpSatScheduler s = new CpSatScheduler(compCalc());
        List<Schedule> r = s.solve(staff(STAFF_COUNT), denseReqs(period(), shiftTypes()),
                period(), configWith(cap), Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
        assertThat(r).isNotNull();
        assertMaxShiftsPerDayRespected("CpSat", cap, r);
    }

    @Test
    @DisplayName("Sanity: cap=0 (unlimited) — scheduler runs without error")
    void sanity_capZero_unlimited() {
        CpSatScheduler s = new CpSatScheduler(compCalc());
        List<Schedule> r = s.solve(staff(STAFF_COUNT), denseReqs(period(), shiftTypes()),
                period(), configWith(0), Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
        assertThat(r).isNotNull();
        System.out.printf("[CpSat cap=0 sanity] total=%d%n", r.size());
    }
}
