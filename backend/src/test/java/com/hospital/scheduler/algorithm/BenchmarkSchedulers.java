package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.algorithm.scoring.ScheduleQualityReport;
import com.hospital.scheduler.algorithm.scoring.ScheduleQualityScorer;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full fairness benchmark across 5 schedulers at 4 demand levels.
 *
 * Metrics: coverage, constraint violations, intra-type span (L01/L02/L03/L04),
 * inter-type deviation, quality score, runtime.
 *
 * NOTE: This benchmark is a standalone main() method — not a JUnit test.
 * It simulates the scheduling problem in-memory (mocked dependencies).
 */
public class BenchmarkSchedulers {
    static final int BEAM_WIDTH = 5;
    static final int WARMUP = 1;
    static final int MEASURED = 2;

    static final int[] DEMANDS = {150, 300, 600, 1000};

    interface Solver {
        List<Schedule> solve(List<Staff> staff, List<ShiftRequirement> reqs,
                             SchedulePeriod period, AlgorithmConfigService.AlgorithmRuntimeConfig cfg,
                             Set<Integer> exclude, L04CrossSpecialtyConfig l04);
    }

    // Real specialty names matching StaffShiftTypeEligibility constants
    static final String[] SPEC_NAMES_CORE = {"Ngoại", "Nội"};
    static final String[] SPEC_NAMES_ALL  = {"Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng"};

    public static void main(String[] args) {
        var h = mock(HolidayRepository.class);
        when(h.findActiveHolidaysBetween(any(), any())).thenReturn(Collections.emptyList());
        var cc = new CompensationDateCalculator(h);

        var scorer = new ScheduleQualityScorer();
        scorer.withWeights(0.40, 0.35, 0.25)
              .withViolationPenalties(25.0, 5.0)
              .withCvTargets(0.10, 0.50);

        System.out.println("=== Fairness Benchmark ===");
        System.out.printf("Warmup=%d  Measured=%d  BeamWidth=%d%n%n", WARMUP, MEASURED, BEAM_WIDTH);

        Map<String, Solver> solvers = new LinkedHashMap<>();
        solvers.put("EnhancedGreedy",     (s, r, p, c, e, l) -> new EnhancedGreedyScheduler(cc).solve(s, r, p, c, e, l));
        solvers.put("BeamSearch",         (s, r, p, c, e, l) -> new BeamSearchScheduler(cc).solve(s, r, p, c, e, l));
        solvers.put("RandomRestartHC",    (s, r, p, c, e, l) -> new RandomRestartHCScheduler(cc).solve(s, r, p, c, e, l));
        solvers.put("SimulatedAnnealing", (s, r, p, c, e, l) -> new SimulatedAnnealingScheduler(cc).solve(s, r, p, c, e, l));
        solvers.put("CpSat",              (s, r, p, c, e, l) -> new CpSatScheduler(cc).solve(s, r, p, c, e, l));

        var l04cfg = L04CrossSpecialtyConfig.DISABLED;
        var emptyExclude = Collections.<Integer>emptySet();

        for (int demand : DEMANDS) {
            System.out.println("=".repeat(150));
            System.out.printf(">>> DEMAND = %d total required slots%n", demand);

            // Scale: 31-day period, staff ~ demand/8, per-day target rounded to match demand
            int numDays = 31;
            int numStaff = Math.max(10, (int) Math.ceil(demand / 7.0));
            // Distribute demand across days: fractional, round totals to match
            int perDayBase = demand / numDays;
            int remainder = demand % numDays;

            var allStaff = buildStaff(numStaff);
            var st = buildShiftTypes();
            var period = buildPeriod(numDays);
            var cfg = buildRuntimeConfig();

            // Build requirements: L01+L02 use core specialties, L03+L04 use all specialties
            List<ShiftRequirement> reqs = new ArrayList<>();
            LocalDate start = period.getStartDate();
            for (int i = 0; i < numDays; i++) {
                LocalDate d = start.plusDays(i);
                // Distribute per-day demand: L01=25%, L02=25%, L03=20%, L04=30%
                int dayTotal = i < remainder ? perDayBase + 1 : perDayBase;
                if (dayTotal <= 0) dayTotal = 4; // minimum
                int l01c = Math.max(1, (int) Math.round(dayTotal * 0.25));
                int l02c = Math.max(1, (int) Math.round(dayTotal * 0.25));
                int l03c = Math.max(1, (int) Math.round(dayTotal * 0.20));
                int l04c = Math.max(1, dayTotal - l01c - l02c - l03c); // remainder to L04
                reqs.add(req(period, st.get("L01"), d, l01c, null));
                reqs.add(req(period, st.get("L02"), d, l02c, null));
                reqs.add(req(period, st.get("L03"), d, l03c, null));
                // L04 requirement with Ngoại specialty (id=1)
                reqs.add(req(period, st.get("L04"), d, l04c, Specialty.builder().id(1).name("Ngoại").build()));
            }

            int actualDemand = reqs.stream().mapToInt(ShiftRequirement::getRequiredStaffCount).sum();
            System.out.printf("  Staff=%d, Days=%d, Actual total demand=%d%n", numStaff, numDays, actualDemand);

            // ── Warmup ──
            for (int i = 0; i < WARMUP; i++) {
                for (var solver : solvers.values()) {
                    try { solver.solve(allStaff, reqs, period, cfg, emptyExclude, l04cfg); }
                    catch (Exception e) { /* warmup may fail for CP-SAT */ }
                }
            }

            // ── Table header ──
            System.out.printf("%-18s | %8s | %7s | %7s | %7s | %7s | %7s | %8s | %7s | %7s | %8s%n",
                    "Algorithm", "Coverage%", "Violatns", "L01span", "L02span", "L03span", "L04span",
                    "InterAvgΔ", "InterMxΔ", "QualScr", "Run(ms)");
            System.out.println("─".repeat(150));

            // ── Measured ──
            for (var entry : solvers.entrySet()) {
                String name = entry.getKey();
                Solver solver = entry.getValue();

                // Timing runs: collect all results, compute metrics on last
                List<Long> runTimes = new ArrayList<>();
                List<Schedule> lastResult = null;
                int numRuns = "CpSat".equals(name) ? 1 : MEASURED; // CP-SAT: single run (too slow)
                for (int i = 0; i < numRuns + WARMUP; i++) {
                    long t0 = System.nanoTime();
                    try {
                        List<Schedule> result = solver.solve(allStaff, reqs, period, cfg, emptyExclude, l04cfg);
                        if (i >= WARMUP) {
                            runTimes.add((System.nanoTime() - t0) / 1_000_000);
                            lastResult = result;
                        }
                    } catch (Exception e) {
                        if (i >= WARMUP) {
                            runTimes.add(Long.MAX_VALUE);
                            lastResult = List.of();
                        }
                    }
                }

                double avgMs = runTimes.isEmpty() ? 0 : runTimes.stream().mapToLong(Long::longValue).average().orElse(0);
                if (lastResult == null) lastResult = List.of();

                // Score
                ScheduleQualityReport report;
                try {
                    report = scorer.score(lastResult, reqs, allStaff, List.of(), List.of(),
                            ScheduleQualityScorer.ScoringMeta.of(name, (long) avgMs));
                } catch (Exception e) {
                    System.out.printf("%-18s | DEMAND %d SCORE FAILED: %s%n", name, demand, e.getMessage());
                    continue;
                }

                double coverage = report.getCoverageScore();
                int violations = report.getHardViolationCount();

                var fd = report.getFairnessByType();
                int l01Span = fd.containsKey("L01") ? fd.get("L01").getMaxDeviation() : -1;
                int l02Span = fd.containsKey("L02") ? fd.get("L02").getMaxDeviation() : -1;
                int l03Span = fd.containsKey("L03") ? fd.get("L03").getMaxDeviation() : -1;
                // L04 spans: keys like "L04:1" for specialty-id=1
                int l04Span = -1;
                int l04MaxDev = 0;
                for (var e : fd.entrySet()) {
                    if (e.getKey().startsWith("L04")) {
                        l04MaxDev = Math.max(l04MaxDev, e.getValue().getMaxDeviation());
                        if (l04Span == -1) l04Span = 0;
                    }
                }
                if (l04MaxDev > 0) l04Span = l04MaxDev;

                var totalShifts = report.getTotalShiftsByStaff();
                int interMaxDev = report.getMaxDeviationTotal();
                double interAvgDev = 0;
                if (totalShifts != null && !totalShifts.isEmpty()) {
                    double mean = totalShifts.values().stream().mapToInt(Integer::intValue).average().orElse(0);
                    interAvgDev = totalShifts.values().stream()
                            .mapToDouble(v -> Math.abs(v - mean)).average().orElse(0);
                }

                double quality = report.getTotalScore();

                // Print timing for each run
                String timesStr = runTimes.stream()
                        .map(t -> t == Long.MAX_VALUE ? "ERR" : String.valueOf(t))
                        .collect(Collectors.joining(","));

                System.out.printf("%-18s | %8.2f | %7d | %7d | %7d | %7d | %7d | %8.2f | %7d | %7.1f | %8.0f  [%s]%n",
                        name, coverage, violations, l01Span, l02Span, l03Span, l04Span,
                        interAvgDev, interMaxDev, quality, avgMs, timesStr);
            }
            System.out.println();
        }
    }

    // ── Build helpers ──

    static List<Staff> buildStaff(int count) {
        List<Staff> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String spName = SPEC_NAMES_ALL[i % SPEC_NAMES_ALL.length];
            Specialty sp = Specialty.builder()
                    .id(i % 6 + 1)
                    .name(spName)
                    .isActive(true)
                    .build();
            list.add(Staff.builder()
                    .id(i).username("s" + i).fullName("Staff " + i).isActive(true)
                    .specialty(sp).maxShiftsPerMonth(30).build());
        }
        return list;
    }

    static Map<String, ShiftType> buildShiftTypes() {
        Map<String, ShiftType> m = new LinkedHashMap<>();
        for (String id : new String[]{"L01", "L02", "L03", "L04"}) {
            m.put(id, ShiftType.builder().id(id).name(id).build());
        }
        return m;
    }

    static SchedulePeriod buildPeriod(int days) {
        return SchedulePeriod.builder()
                .id(1).periodName("Bench Period")
                .startDate(LocalDate.of(2026, 7, 1))
                .endDate(LocalDate.of(2026, 7, 1).plusDays(days - 1))
                .build();
    }

    static AlgorithmConfigService.AlgorithmRuntimeConfig buildRuntimeConfig() {
        return AlgorithmConfigService.AlgorithmRuntimeConfig.builder()
                .weekendWeight(new BigDecimal("2"))
                .overnightRecoveryHours(24)
                .greedyCoverageThreshold(new BigDecimal("0.85"))
                .balanceScoreMin(new BigDecimal("0.70"))
                .autoCompensationEnabled(true)
                .maxStaffPerShift(10)
                .maxShiftsPerStaff(20)
                .maxShiftsPerDay(0)
                .l01MaxPerWeek(0).l02MaxPerWeek(0).l03MaxPerWeek(0).l04MaxPerWeek(0)
                .beamWidth(BEAM_WIDTH)
                .autoAdjustConfig(true)
                .coverageWeight(new BigDecimal("0.40"))
                .fairnessWeight(new BigDecimal("0.35"))
                .constraintWeight(new BigDecimal("0.25"))
                .build();
    }

    static ShiftRequirement req(SchedulePeriod p, ShiftType st, LocalDate d, int count, Specialty sp) {
        return ShiftRequirement.builder()
                .period(p).workDate(d).shiftType(st).requiredStaffCount(count).specialty(sp).build();
    }
}
