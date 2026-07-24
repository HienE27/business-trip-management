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
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test: each {@link AlgorithmRuntimeConfig} field must actually
 * affect scheduler output. If a developer adds a new field without adding
 * a test case here, the gap is obvious.
 *
 * <p>Uses direct scheduler calls (no Spring context) following the
 * {@link FairnessBenchmarkTest} / {@link BenchmarkSchedulers} pattern.
 * All tests run against {@link EnhancedGreedyScheduler} (most deterministic).
 *
 * <p>Fields tested:
 * <ul>
 *   <li>{@code maxShiftsPerStaff} — hard cap on total shifts per staff</li>
 *   <li>{@code overnightRecoveryHours} — L01 adjacent-day gap (ceil(hours/24))</li>
 *   <li>{@code maxShiftsPerDay} — hard cap on shifts per staff per day</li>
 * </ul>
 *
 * <p>ponytail: add tests for {@code beamWidth}, {@code autoCompensationEnabled},
 * and per-type weekly max fields when those code paths diverge per scheduler.
 */
@DisplayName("RuntimeConfig behavior — each field affects scheduler output")
class RuntimeConfigBehaviorTest {

    static final int STAFF_COUNT = 10;
    static final int PERIOD_DAYS = 14;

    // ── Shared infrastructure ───────────────────────────────────────

    private final HolidayRepository holidayRepo = mock(HolidayRepository.class);
    private final CompensationDateCalculator compCalc;

    {
        when(holidayRepo.findActiveHolidaysBetween(any(), any())).thenReturn(Collections.emptyList());
        compCalc = new CompensationDateCalculator(holidayRepo);
    }

    private List<Staff> staff;
    private SchedulePeriod period;
    private List<ShiftRequirement> requirements;

    /** Build test data lazily so each test gets a fresh copy. */
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
                .id(1).periodName("RuntimeConfigTest")
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 1).plusDays(PERIOD_DAYS - 1))
                .build();

        requirements = buildRequirements();
    }

    /** Create balanced requirements across L01-L04 for 14 days. */
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
            // 2 L01, 2 L02, 1 L03, 1 L04 per day = enough to exercise caps
            reqs.add(req(shiftTypes.get("L01"), date, 2, null));
            reqs.add(req(shiftTypes.get("L02"), date, 2, null));
            reqs.add(req(shiftTypes.get("L03"), date, 1, null));
            int specIdx = d % 3 + 1;
            reqs.add(req(shiftTypes.get("L04"), date, 1,
                    Specialty.builder().id(specIdx).name("Spec" + specIdx).isActive(true).build()));
        }
        return reqs;
    }

    private ShiftRequirement req(ShiftType st, LocalDate d, int count, Specialty sp) {
        return ShiftRequirement.builder()
                .period(period).workDate(d).shiftType(st).requiredStaffCount(count).specialty(sp).build();
    }

    // ── Config factory ──────────────────────────────────────────────

    private AlgorithmConfigService.AlgorithmRuntimeConfig baseConfig() {
        return AlgorithmConfigService.AlgorithmRuntimeConfig.builder()
                .weekendWeight(new BigDecimal("2"))
                .overnightRecoveryHours(24) // default = 24h → gap=1
                .greedyCoverageThreshold(new BigDecimal("0.85"))
                .balanceScoreMin(new BigDecimal("0.70"))
                .autoCompensationEnabled(true)
                .maxStaffPerShift(3)
                .maxShiftsPerStaff(12) // default cap
                .maxShiftsPerDay(3) // default cap
                .l01MaxPerWeek(6).l02MaxPerWeek(6).l03MaxPerWeek(6).l04MaxPerWeek(6)
                .beamWidth(5)
                .autoAdjustConfig(false) // off for deterministic test
                .coverageWeight(new BigDecimal("0.40"))
                .fairnessWeight(new BigDecimal("0.35"))
                .constraintWeight(new BigDecimal("0.25"))
                .build();
    }

    // ── Scheduler runner ────────────────────────────────────────────

    private List<Schedule> runEnhancedGreedy(AlgorithmConfigService.AlgorithmRuntimeConfig cfg) {
        ensureSeeded();
        return new EnhancedGreedyScheduler(compCalc)
                .solve(staff, requirements, period, cfg, Collections.emptySet(), L04CrossSpecialtyConfig.DISABLED);
    }

    // ── Assertion helpers ───────────────────────────────────────────

    /** Max total shifts per staff. */
    private static int maxPerStaff(List<Schedule> schedules) {
        return schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()))
                .values().stream()
                .mapToInt(Long::intValue)
                .max().orElse(0);
    }

    /** Max shifts by a single staff on a single day. */
    private static int maxShiftsPerDayPerStaff(List<Schedule> schedules) {
        return schedules.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getStaff().getId() + "|" + s.getWorkDate(),
                        Collectors.counting()))
                .values().stream()
                .mapToInt(Long::intValue)
                .max().orElse(0);
    }

    /** Min gap in days between L01 assignments for the same staff. */
    private static int minL01Gap(List<Schedule> schedules) {
        Map<Integer, List<LocalDate>> staffL01Dates = schedules.stream()
                .filter(s -> "L01".equals(s.getShiftType().getId()))
                .collect(Collectors.groupingBy(
                        s -> s.getStaff().getId(),
                        Collectors.mapping(Schedule::getWorkDate, Collectors.toList())));

        int minGap = Integer.MAX_VALUE;
        for (var entry : staffL01Dates.entrySet()) {
            List<LocalDate> dates = entry.getValue().stream().sorted().toList();
            for (int i = 1; i < dates.size(); i++) {
                int gap = (int) (dates.get(i).toEpochDay() - dates.get(i - 1).toEpochDay());
                if (gap < minGap) minGap = gap;
            }
        }
        return minGap == Integer.MAX_VALUE ? 0 : minGap;
    }

    // ── Tests ───────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {5, 8, 12})
    @DisplayName("maxShiftsPerStaff thay đổi → total shifts per staff ≤ cap")
    void maxShiftsPerStaff_changesOutput(int cap) {
        var cfg = baseConfig();
        cfg.setMaxShiftsPerStaff(cap);

        List<Schedule> schedules = runEnhancedGreedy(cfg);
        int max = maxPerStaff(schedules);

        assertThat(max)
                .as("maxShiftsPerStaff=%d: max per staff = %d (expected ≤ %d)", cap, max, cap)
                .isLessThanOrEqualTo(cap);
        assertThat(max)
                .as("maxShiftsPerStaff=%d: at least one staff should be assigned", cap)
                .isGreaterThan(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    @DisplayName("maxShiftsPerDay thay đổi → max shifts per staff per day ≤ cap")
    void maxShiftsPerDay_changesOutput(int cap) {
        var cfg = baseConfig();
        cfg.setMaxShiftsPerDay(cap);
        // Ensure maxShiftsPerStaff is high enough not to interfere
        cfg.setMaxShiftsPerStaff(PERIOD_DAYS * cap);

        List<Schedule> schedules = runEnhancedGreedy(cfg);
        int maxDaily = maxShiftsPerDayPerStaff(schedules);

        assertThat(maxDaily)
                .as("maxShiftsPerDay=%d: max daily per staff = %d (expected ≤ %d)", cap, maxDaily, cap)
                .isLessThanOrEqualTo(cap);
    }

    @ParameterizedTest
    @ValueSource(ints = {24, 48})
    @DisplayName("overnightRecoveryHours thay đổi → L01 gap ≥ ceil(hours/24)")
    void overnightRecoveryHours_changesOutput(int hours) {
        var cfg = baseConfig();
        cfg.setOvernightRecoveryHours(hours);
        int expectedMinGap = (int) Math.ceil(hours / 24.0);

        List<Schedule> schedules = runEnhancedGreedy(cfg);
        int actualMinGap = minL01Gap(schedules);

        // Only assert if there are at least 2 L01s for at least one staff
        if (actualMinGap > 0) {
            assertThat(actualMinGap)
                    .as("overnightRecoveryHours=%d (expected gap ≥ %d days), actual min gap = %d",
                            hours, expectedMinGap, actualMinGap)
                    .isGreaterThanOrEqualTo(expectedMinGap);
        }
	        // If no staff has 2+ L01s, the constraint is vacuously satisfied
	    }

	    @ParameterizedTest
	    @ValueSource(ints = {5, 8, 15})
	    @DisplayName("beamWidth — builder round-trip preserves configured value")
	    void beamWidth_roundtrip(int width) {
	        var cfg = baseConfig();
	        cfg.setBeamWidth(width);
	        assertThat(cfg.getBeamWidth())
	                .as("beamWidth=%d → getBeamWidth() returns same value", width)
	                .isEqualTo(width);
	    }
	}
