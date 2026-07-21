package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Smoke tests for the 5 metaheuristic schedulers. Verifies each solver
 * runs without exception on a tiny instance and produces a non-null result
 * that respects the L01↔L02 / L03↔L04 same-day business conflict rule.
 */
@DisplayName("Metaheuristic schedulers — smoke")
class MetaheuristicSchedulersSmokeTest {

    private static final int BEAM_WIDTH = 5;

    private static CompensationDateCalculator compCalc() {
        HolidayRepository holidayRepo = mock(HolidayRepository.class);
        when(holidayRepo.findActiveHolidaysBetween(any(), any())).thenReturn(Collections.emptyList());
        return new CompensationDateCalculator(holidayRepo);
    }

    private static List<Staff> staff(int n) {
        List<Staff> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            Specialty sp = Specialty.builder().id(i).name("Nội").build();
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

    private static List<ShiftRequirement> reqs(SchedulePeriod period, Map<String, ShiftType> st) {
        List<ShiftRequirement> out = new ArrayList<>();
        // 3 dates, 1 each of L01+L02 (forces BR-01 check), L03+L04 (forces BR-04 check).
        LocalDate d1 = period.getStartDate();
        out.add(req(period, st.get("L01"), d1, 1, null));
        out.add(req(period, st.get("L02"), d1, 1, null));
        out.add(req(period, st.get("L03"), d1.plusDays(1), 1, null));
        out.add(req(period, st.get("L04"), d1.plusDays(2), 1, 1));
        return out;
    }

    private static ShiftRequirement req(SchedulePeriod period, ShiftType st, LocalDate date,
                                        int count, Integer specialtyId) {
        Specialty sp = specialtyId == null ? null : Specialty.builder().id(specialtyId).name("Nội").build();
        return ShiftRequirement.builder()
                .period(period)
                .workDate(date)
                .shiftType(st)
                .requiredStaffCount(count)
                .specialty(sp)
                .build();
    }

    private static SchedulePeriod period() {
        return SchedulePeriod.builder()
                .id(1)
                .periodName("Test Period")
                .startDate(LocalDate.of(2026, 7, 6))
                .endDate(LocalDate.of(2026, 7, 12))
                .build();
    }

    private static AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig() {
        return new AlgorithmConfigService.AlgorithmRuntimeConfig(
                new java.math.BigDecimal("2"),      // weekendWeight
                24,                                  // overnightRecoveryHours
                new java.math.BigDecimal("0.85"),   // greedyCoverageThreshold
                new java.math.BigDecimal("0.70"),   // balanceScoreMin
                true,                                // autoCompensationEnabled
                1,                                   // minStaffPerShift
                5,                                   // maxStaffPerShift
                0,                                   // minShiftsPerStaff
                12,                                  // maxShiftsPerStaff
                0,                                   // maxShiftsPerDay
	                0, 0, 0, 0,                          // l01-l04 max per week
	                BEAM_WIDTH,                          // beamWidth
	                true                                 // autoAdjustConfig
	        );
    }

    /** No staff-day may hold both L01+L02 or L03+L04. */
    private static void assertNoBusinessConflict(List<Schedule> schedules) {
        Map<String, Set<String>> perDay = new HashMap<>();
        for (Schedule s : schedules) {
            String key = s.getStaff().getId() + "|" + s.getWorkDate();
            Set<String> types = perDay.computeIfAbsent(key, k -> new HashSet<>());
            types.add(s.getShiftType().getId());
        }
        for (Map.Entry<String, Set<String>> e : perDay.entrySet()) {
            Set<String> types = e.getValue();
            String key = e.getKey();
            // L01+L02 same-day conflict
            assertThat(types.contains("L01") && types.contains("L02"))
                    .as("staff-day %s: no L01+L02", key).isFalse();
            // L03+L04 same-day conflict
            assertThat(types.contains("L03") && types.contains("L04"))
                    .as("staff-day %s: no L03+L04", key).isFalse();
        }
    }

    private static List<Schedule> solveFor(List<Schedule> result) {
        return result == null ? List.of() : result;
    }

    @Test
    @DisplayName("BeamSearch — runs, returns non-null, no L01+L02 / L03+L04 conflicts")
    void beamSearch_runs() {
        BeamSearchScheduler s = new BeamSearchScheduler(compCalc());
        List<Schedule> r = s.solve(staff(3), reqs(period(), shiftTypes()),
                period(), runtimeConfig(), Collections.emptySet());
        assertThat(solveFor(r)).isNotNull();
        assertThat(r).allSatisfy(x -> {
            assertThat(x.getStaff()).isNotNull();
            assertThat(x.getWorkDate()).isNotNull();
            assertThat(x.getShiftType()).isNotNull();
        });
        assertNoBusinessConflict(r);
    }

    @Test
    @DisplayName("RandomRestartHC — runs, returns non-null, no L01+L02 / L03+L04 conflicts")
    void randomRestartHC_runs() {
        RandomRestartHCScheduler s = new RandomRestartHCScheduler(compCalc());
        List<Schedule> r = s.solve(staff(3), reqs(period(), shiftTypes()),
                period(), runtimeConfig(), Collections.emptySet());
        assertThat(solveFor(r)).isNotNull();
        assertNoBusinessConflict(r);
    }

    @Test
    @DisplayName("EnhancedGreedy — runs, returns non-null, no L01+L02 / L03+L04 conflicts")
    void enhancedGreedy_runs() {
        EnhancedGreedyScheduler s = new EnhancedGreedyScheduler(compCalc());
        List<Schedule> r = s.solve(staff(3), reqs(period(), shiftTypes()),
                period(), runtimeConfig(), Collections.emptySet());
        assertThat(solveFor(r)).isNotNull();
        assertNoBusinessConflict(r);
    }

    @Test
    @DisplayName("SimulatedAnnealing — runs, returns non-null, no L01+L02 / L03+L04 conflicts")
    void simulatedAnnealing_runs() {
        SimulatedAnnealingScheduler s = new SimulatedAnnealingScheduler(compCalc());
        List<Schedule> r = s.solve(staff(3), reqs(period(), shiftTypes()),
                period(), runtimeConfig(), Collections.emptySet());
        assertThat(solveFor(r)).isNotNull();
        assertNoBusinessConflict(r);
    }

    @Test
    @DisplayName("CpSat — runs, returns non-null, no L01+L02 / L03+L04 conflicts")
    void cpSat_runs() {
        CpSatScheduler s = new CpSatScheduler(compCalc());
        // CP-SAT needs a SpecialtyId set on requirements for specialty-bound L04 to find matches.
        SchedulePeriod p = period();
        Map<String, ShiftType> st = shiftTypes();
        List<ShiftRequirement> reqs = new ArrayList<>();
        reqs.add(req(p, st.get("L01"), p.getStartDate(), 1, 1));
        reqs.add(req(p, st.get("L02"), p.getStartDate(), 1, 1));
        reqs.add(req(p, st.get("L03"), p.getStartDate().plusDays(1), 1, 1));
        reqs.add(req(p, st.get("L04"), p.getStartDate().plusDays(2), 1, 1));
        List<Schedule> r = s.solve(staff(3), reqs, p, runtimeConfig(), Collections.emptySet());
        assertThat(solveFor(r)).isNotNull();
        assertNoBusinessConflict(r);
    }
}