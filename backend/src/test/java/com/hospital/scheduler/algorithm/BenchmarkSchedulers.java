package com.hospital.scheduler.algorithm;

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

public class BenchmarkSchedulers {
    static final int BEAM_WIDTH = 5;
    static final int WARMUP = 3;
    static final int MEASURED = 10;

    public static void main(String[] args) {
        HolidayRepository holidayRepo = mock(HolidayRepository.class);
        when(holidayRepo.findActiveHolidaysBetween(any(), any())).thenReturn(Collections.emptyList());
        CompensationDateCalculator compCalc = new CompensationDateCalculator(holidayRepo);

        // Larger data: 20 staff, 3 weeks, ~60 requirements
        List<Staff> allStaff = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            Specialty sp = Specialty.builder().id(i % 4 + 1).name("Spec" + (i % 4 + 1)).build();
            allStaff.add(Staff.builder()
                    .id(i).username("s" + i).fullName("Staff " + i).isActive(true)
                    .specialty(sp).maxShiftsPerMonth(20).build());
        }

        Map<String, ShiftType> st = new LinkedHashMap<>();
        for (String id : new String[]{"L01", "L02", "L03", "L04"}) {
            st.put(id, ShiftType.builder().id(id).name(id).build());
        }

        SchedulePeriod p = SchedulePeriod.builder()
                .id(1).periodName("Test Period")
                .startDate(LocalDate.of(2026, 7, 6))
                .endDate(LocalDate.of(2026, 7, 26))
                .build();

        var cfg = new AlgorithmConfigService.AlgorithmRuntimeConfig(
                new BigDecimal("2"), 0, new BigDecimal("0.85"), new BigDecimal("0.70"),
                true, 1, 5, 0, 12, 0,
                2, 2, 2, 2, BEAM_WIDTH
        );

        // Build requirements: each day L01+L02 (1 each), + L03 daily, L04 every other day
        List<ShiftRequirement> reqs = new ArrayList<>();
        LocalDate start = p.getStartDate();
        for (int i = 0; i < 21; i++) {
            LocalDate d = start.plusDays(i);
            reqs.add(req(p, st.get("L01"), d, 2, null));
            reqs.add(req(p, st.get("L02"), d, 2, null));
            reqs.add(req(p, st.get("L03"), d, 1, null));
            if (i % 2 == 0) {
                reqs.add(req(p, st.get("L04"), d, 1, Specialty.builder().id(1).name("Spec1").build()));
            }
        }
        // Total: 21 * (2+2+1+0.5) = ~115 requirements
        Set<Integer> emptyExclude = Collections.emptySet();

        // Warmup
        for (int i = 0; i < WARMUP; i++) {
            new EnhancedGreedyScheduler(compCalc).solve(allStaff, reqs, p, cfg, emptyExclude);
            new BeamSearchScheduler(compCalc).solve(allStaff, reqs, p, cfg, emptyExclude);
            new RandomRestartHCScheduler(compCalc).solve(allStaff, reqs, p, cfg, emptyExclude);
            new SimulatedAnnealingScheduler(compCalc).solve(allStaff, reqs, p, cfg, emptyExclude);
        }
        // CpSat warmup once (slower)
        try { new CpSatScheduler(compCalc).solve(allStaff, reqs, p, cfg, emptyExclude); } catch (Exception e) {}

        // Measured
        System.out.println("=== Benchmark (ms) avg over " + MEASURED + " runs ===");
        bench("EnhancedGreedy", () -> new EnhancedGreedyScheduler(compCalc).solve(allStaff, reqs, p, cfg, emptyExclude), MEASURED);
        bench("BeamSearch",    () -> new BeamSearchScheduler(compCalc).solve(allStaff, reqs, p, cfg, emptyExclude), MEASURED);
        bench("RandomRestartHC", () -> new RandomRestartHCScheduler(compCalc).solve(allStaff, reqs, p, cfg, emptyExclude), MEASURED);
        bench("SimulatedAnnealing", () -> new SimulatedAnnealingScheduler(compCalc).solve(allStaff, reqs, p, cfg, emptyExclude), MEASURED);
        bench("CpSat",         () -> new CpSatScheduler(compCalc).solve(allStaff, reqs, p, cfg, emptyExclude), Math.max(1, MEASURED / 3));
    }

    static ShiftRequirement req(SchedulePeriod p, ShiftType st, LocalDate d, int count, Specialty sp) {
        return ShiftRequirement.builder()
                .period(p).workDate(d).shiftType(st).requiredStaffCount(count).specialty(sp).build();
    }

    static void bench(String name, Runnable task, int n) {
        long total = 0;
        long min = Long.MAX_VALUE, max = 0;
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            task.run();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            total += ms;
            if (ms < min) min = ms;
            if (ms > max) max = ms;
        }
        System.out.printf("%-18s avg=%4dms  min=%4dms  max=%4dms  (n=%d)%n",
                name, total / n, min, max, n);
    }
}
