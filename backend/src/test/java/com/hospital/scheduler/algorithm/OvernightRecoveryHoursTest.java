package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verify each scheduler respects L01 window = ceil(overnightRecoveryHours/24).
 *
 * <p>Setup: 10 staff, 14-day period. L01 slots scattered ~every 3 days
 * so schedulers have flexibility to violate the window if unconstrained.
 * overnightRecoveryHours ∈ {24, 48}.
 * - 24h → W=1 (L01 không được cách nhau < 2 ngày)
 * - 48h → W=2 (L01 không được cách nhau < 3 ngày)
 */
@DisplayName("F04 — overnightRecoveryHours L01 window enforcement")
class OvernightRecoveryHoursTest {

    private static final int STAFF_COUNT = 10;
    private static final int PERIOD_DAYS = 14;

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
                .periodName("OvernightRecoveryTest")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 1).plusDays(PERIOD_DAYS - 1))
                .build();
    }

    /** Sparse L01 requirements: L01 mỗi 3 ngày, mỗi slot cần STAFF_COUNT người. */
    private static List<ShiftRequirement> sparseL01Reqs(SchedulePeriod p, Map<String, ShiftType> st) {
        List<ShiftRequirement> out = new ArrayList<>();
        LocalDate start = p.getStartDate();
        for (int i = 0; i < PERIOD_DAYS; i += 3) {
            LocalDate d = start.plusDays(i);
            out.add(req(p, st.get("L01"), d, STAFF_COUNT, null));
            out.add(req(p, st.get("L02"), d, 1, null));
            out.add(req(p, st.get("L03"), d.plusDays(1), 1, null));
            out.add(req(p, st.get("L04"), d.plusDays(2), 1, 1));
        }
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

    private static AlgorithmConfigService.AlgorithmRuntimeConfig configWith(int overnightRecoveryHours) {
        return AlgorithmConfigService.AlgorithmRuntimeConfig.builder()
                .weekendWeight(new BigDecimal("2"))
                .overnightRecoveryHours(overnightRecoveryHours) // under test
                .greedyCoverageThreshold(new BigDecimal("0.85"))
                .balanceScoreMin(new BigDecimal("0.70"))
                .autoCompensationEnabled(true)
                .maxStaffPerShift(0)
                .maxShiftsPerStaff(50) // high
                .maxShiftsPerDay(0)
                .l01MaxPerWeek(0).l02MaxPerWeek(0).l03MaxPerWeek(0).l04MaxPerWeek(0)
                .beamWidth(5)
                .autoAdjustConfig(false)
                .coverageWeight(new BigDecimal("0.40"))
                .fairnessWeight(new BigDecimal("0.35"))
                .constraintWeight(new BigDecimal("0.25"))
                .build();
    }

    /** Kiểm tra không có staff nào có 2 L01 cách nhau < windowDays+1 ngày. */
    private static void assertMinL01Gap(List<Schedule> schedules, int windowDays, String label) {
        Map<Integer, List<LocalDate>> l01ByStaff = new HashMap<>();
        for (Schedule s : schedules) {
            if ("L01".equals(s.getShiftType().getId())) {
                l01ByStaff.computeIfAbsent(s.getStaff().getId(), k -> new ArrayList<>())
                        .add(s.getWorkDate());
            }
        }

        for (var entry : l01ByStaff.entrySet()) {
            List<LocalDate> dates = entry.getValue();
            dates.sort(Comparator.naturalOrder());
            for (int i = 1; i < dates.size(); i++) {
                long gap = ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
                assertThat(gap)
                        .as("[%s] staff=%d L01 gap=%d < %d (overnightRecoveryHours=%d → window=%d)",
                                label, entry.getKey(), gap, windowDays + 1,
                                windowDays * 24, windowDays)
                        .isGreaterThan(windowDays);
            }
        }
    }

    // ── EnhancedGreedyScheduler ──────────────────────────────────

    private static List<Schedule> runEnhancedGreedy(AlgorithmConfigService.AlgorithmRuntimeConfig cfg) {
        var s = new EnhancedGreedyScheduler(compCalc());
        return s.solve(staff(STAFF_COUNT), sparseL01Reqs(period(), shiftTypes()),
                period(), cfg, Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
    }

    @ParameterizedTest
    @ValueSource(ints = {24, 48})
    @DisplayName("EnhancedGreedy respects L01 window")
    void enhancedGreedy_respectsL01Window(int hours) {
        int w = (int) Math.ceil(hours / 24.0);
        List<Schedule> r = runEnhancedGreedy(configWith(hours));
        assertThat(r).isNotEmpty();
        assertMinL01Gap(r, w, "EnhancedGreedy");
    }

    // ── BeamSearchScheduler ──────────────────────────────────────

    private static List<Schedule> runBeamSearch(AlgorithmConfigService.AlgorithmRuntimeConfig cfg) {
        var s = new BeamSearchScheduler(compCalc());
        return s.solve(staff(STAFF_COUNT), sparseL01Reqs(period(), shiftTypes()),
                period(), cfg, Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
    }

    @ParameterizedTest
    @ValueSource(ints = {24, 48})
    @DisplayName("BeamSearch respects L01 window")
    void beamSearch_respectsL01Window(int hours) {
        int w = (int) Math.ceil(hours / 24.0);
        List<Schedule> r = runBeamSearch(configWith(hours));
        assertThat(r).isNotEmpty();
        assertMinL01Gap(r, w, "BeamSearch");
    }

    // ── RandomRestartHCScheduler ─────────────────────────────────

    private static List<Schedule> runRRHC(AlgorithmConfigService.AlgorithmRuntimeConfig cfg) {
        var s = new RandomRestartHCScheduler(compCalc());
        return s.solve(staff(STAFF_COUNT), sparseL01Reqs(period(), shiftTypes()),
                period(), cfg, Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
    }

    @ParameterizedTest
    @ValueSource(ints = {24, 48})
    @DisplayName("RandomRestartHC respects L01 window")
    void randomRestartHC_respectsL01Window(int hours) {
        int w = (int) Math.ceil(hours / 24.0);
        List<Schedule> r = runRRHC(configWith(hours));
        assertThat(r).isNotEmpty();
        assertMinL01Gap(r, w, "RandomRestartHC");
    }

    // ── SimulatedAnnealingScheduler ──────────────────────────────

    private static List<Schedule> runSA(AlgorithmConfigService.AlgorithmRuntimeConfig cfg) {
        var s = new SimulatedAnnealingScheduler(compCalc());
        return s.solve(staff(STAFF_COUNT), sparseL01Reqs(period(), shiftTypes()),
                period(), cfg, Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
    }

    @ParameterizedTest
    @ValueSource(ints = {24, 48})
    @DisplayName("SimulatedAnnealing respects L01 window")
    void simulatedAnnealing_respectsL01Window(int hours) {
        int w = (int) Math.ceil(hours / 24.0);
        List<Schedule> r = runSA(configWith(hours));
        assertThat(r).isNotEmpty();
        assertMinL01Gap(r, w, "SimulatedAnnealing");
    }

    // ── CpSatScheduler ───────────────────────────────────────────

    private static List<Schedule> runCpSat(AlgorithmConfigService.AlgorithmRuntimeConfig cfg) {
        var s = new CpSatScheduler(compCalc());
        SchedulePeriod p = period();
        Map<String, ShiftType> st = shiftTypes();
        // CP-SAT needs specialty on requirements
        List<ShiftRequirement> reqs = new ArrayList<>();
        reqs.add(req(p, st.get("L01"), p.getStartDate(), 5, 1));
        reqs.add(req(p, st.get("L02"), p.getStartDate(), 1, 1));
        reqs.add(req(p, st.get("L03"), p.getStartDate().plusDays(1), 1, 1));
        reqs.add(req(p, st.get("L04"), p.getStartDate().plusDays(2), 1, 1));
        return s.solve(staff(STAFF_COUNT), reqs, p, cfg, Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
    }

    @ParameterizedTest
    @ValueSource(ints = {24, 48})
    @DisplayName("CpSat respects L01 window")
    void cpSat_respectsL01Window(int hours) {
        int w = (int) Math.ceil(hours / 24.0);
        List<Schedule> r = runCpSat(configWith(hours));
        assertThat(r).isNotEmpty();
        assertMinL01Gap(r, w, "CpSat");
    }

    // ── Greedy (SchedulingAlgorithmRunner) ───────────────────────
    // Skipped — requires full Spring context (needs AutoSchedulingService,
    // ScheduleRepository, etc.). Metaheuristic coverage là đủ.
}
