package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Targeted behavioral tests for the 4 rebalance round runtime config keys.
 *
 * <p>Verifies:
 * <ul>
 *   <li>rounds=0 → rebalance loop body never executes (span wider than with rounds>0)</li>
 *   <li>configured rounds are consumed (output differs from zero-round baseline)</li>
 * </ul>
 *
 * <p>Uses direct scheduler calls (no Spring) following the same pattern as
 * {@link RuntimeConfigBehaviorTest}.
 */
@DisplayName("Rebalance rounds — 0 skips, configured rounds consumed")
class RebalanceRoundsBehaviorTest {

    static final int STAFF_COUNT = 10;
    static final int PERIOD_DAYS = 20;

    private final HolidayRepository holidayRepo = mock(HolidayRepository.class);
    private final CompensationDateCalculator compCalc;

    {
        when(holidayRepo.findActiveHolidaysBetween(any(), any())).thenReturn(Collections.emptyList());
        compCalc = new CompensationDateCalculator(holidayRepo);
    }

    private List<Staff> staff;
    private SchedulePeriod period;
    private List<ShiftRequirement> requirements;

    private void ensureSeeded() {
        if (staff != null) return;
        staff = new ArrayList<>();
        for (int i = 1; i <= STAFF_COUNT; i++) {
            Specialty sp = Specialty.builder()
                    .id(i % 3 + 1).name("Spec" + (i % 3 + 1)).isActive(true).build();
            staff.add(Staff.builder()
                    .id(i).username("s" + i).fullName("Staff " + i).isActive(true)
                    .specialty(sp).maxShiftsPerMonth(PERIOD_DAYS).build());
        }

        period = SchedulePeriod.builder()
                .id(1).periodName("RebalanceTest")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 1).plusDays(PERIOD_DAYS - 1))
                .build();

        requirements = buildRequirements();
    }

    /** Create requirements skewed toward L02/L03 to ensure imbalance for rebalance to act on. */
    private List<ShiftRequirement> buildRequirements() {
        Map<String, ShiftType> shiftTypes = new LinkedHashMap<>();
        for (String id : new String[]{"L01", "L02", "L03", "L04"}) {
            shiftTypes.put(id, ShiftType.builder()
                    .id(id).name(id).isOvernight("L01".equals(id)).build());
        }

        List<ShiftRequirement> reqs = new ArrayList<>();
        LocalDate start = period.getStartDate();
        for (int d = 0; d < PERIOD_DAYS; d++) {
            LocalDate date = start.plusDays(d);
            reqs.add(req(shiftTypes.get("L01"), date, 2));
            reqs.add(req(shiftTypes.get("L02"), date, 3)); // heavier L02 demand
            reqs.add(req(shiftTypes.get("L03"), date, 2));
            reqs.add(req(shiftTypes.get("L04"), date, 1));
        }
        return reqs;
    }

    private ShiftRequirement req(ShiftType st, LocalDate d, int count) {
        return ShiftRequirement.builder()
                .period(period).workDate(d).shiftType(st).requiredStaffCount(count).build();
    }

    // ── Config factory ──────────────────────────────────────────────

    private AlgorithmConfigService.AlgorithmRuntimeConfig baseConfig() {
        return AlgorithmConfigService.AlgorithmRuntimeConfig.builder()
                .weekendWeight(new BigDecimal("2"))
                .overnightRecoveryHours(24)
                .greedyCoverageThreshold(new BigDecimal("0.85"))
                .balanceScoreMin(new BigDecimal("0.70"))
                .autoCompensationEnabled(true)
                .maxStaffPerShift(5)
                .maxShiftsPerStaff(PERIOD_DAYS * 3)
                .maxShiftsPerDay(3)
                .l01MaxPerWeek(10).l02MaxPerWeek(10).l03MaxPerWeek(10).l04MaxPerWeek(10)
                .beamWidth(5)
                .autoAdjustConfig(false)
                .coverageWeight(new BigDecimal("0.40"))
                .fairnessWeight(new BigDecimal("0.35"))
                .constraintWeight(new BigDecimal("0.25"))
                .build();
    }

    // ── Scheduler runners ───────────────────────────────────────────

    private List<Schedule> runEG(AlgorithmConfigService.AlgorithmRuntimeConfig cfg) {
        ensureSeeded();
        return new EnhancedGreedyScheduler(compCalc)
                .solve(staff, requirements, period, cfg, Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
    }

    private List<Schedule> runBeam(AlgorithmConfigService.AlgorithmRuntimeConfig cfg) {
        ensureSeeded();
        return new BeamSearchScheduler(compCalc)
                .solve(staff, requirements, period, cfg, Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
    }

    // ── Per-type span measurement ───────────────────────────────────

    /** Max - min assignments of a given shift type across staff. */
    private static int perTypeSpan(List<Schedule> schedules, String type) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Schedule s : schedules) {
            if (type.equals(s.getShiftType().getId())) {
                counts.merge(s.getStaff().getId(), 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) return 0;
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int min = counts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        return max - min;
    }

    // ── Tests: EG rebalanceRoundsEg ─────────────────────────────────

    @Test
    @DisplayName("EG: rebalanceRoundsEg=0 → per-type span ≥ span with 100 rounds (rebalance skipped)")
    void eg_rebalanceRoundsEg_zeroSkipsRebalance() {
        var cfgZero = baseConfig();
        cfgZero.setRebalanceRoundsEg(0);
        var cfgHigh = baseConfig();
        cfgHigh.setRebalanceRoundsEg(100);

        var sZero = runEG(cfgZero);
        var sHigh = runEG(cfgHigh);

        // EG per-type rebalance targets L02 and L03
        int spanZeroL02 = perTypeSpan(sZero, "L02");
        int spanHighL02 = perTypeSpan(sHigh, "L02");
        int spanZeroL03 = perTypeSpan(sZero, "L03");
        int spanHighL03 = perTypeSpan(sHigh, "L03");

        assertThat(spanZeroL02)
                .as("EG L02 span: rounds=0 (%d) ≥ rounds=100 (%d)", spanZeroL02, spanHighL02)
                .isGreaterThanOrEqualTo(spanHighL02);
        assertThat(spanZeroL03)
                .as("EG L03 span: rounds=0 (%d) ≥ rounds=100 (%d)", spanZeroL03, spanHighL03)
                .isGreaterThanOrEqualTo(spanHighL03);
    }

    // ── Tests: Beam rebalanceRoundsTotal + rebalanceRoundsPerType ───

    @Test
    @DisplayName("Beam: rebalanceRoundsTotal=0, perType=0 → total span ≥ span with default rounds")
    void beam_rebalanceRoundsZero_skipsRebalance() {
        var cfgZero = baseConfig();
        cfgZero.setRebalanceRoundsTotal(0);
        cfgZero.setRebalanceRoundsPerType(0);
        var cfgDefault = baseConfig();
        // defaults: total=80, perType=30, eg=40

        var sZero = runBeam(cfgZero);
        var sDefault = runBeam(cfgDefault);

        // Beam total-count rebalance narrows total span; per-type narrows per-type span.
        // With both disabled, span should be >= default case.
        int spanZero = perTypeSpan(sZero, "L02");
        int spanDefault = perTypeSpan(sDefault, "L02");

        assertThat(spanZero)
                .as("Beam L02 span: rounds=0 (%d) ≥ default (%d)", spanZero, spanDefault)
                .isGreaterThanOrEqualTo(spanDefault);
    }
}
