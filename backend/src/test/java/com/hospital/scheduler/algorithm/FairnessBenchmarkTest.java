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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fairness benchmark — measures max-min per shift-type across demand levels.
 *
 * <p>Print-only (no pass/fail). Output is used to decide whether a local-search
 * post-processing pass is needed: if any scheduler at demand=1000 shows max-min
 * span > 10% of mean load, a fairness-improving post-pass should be added.
 *
 * <p>GREEDY algorithm removed from whitelist in M07; 5 schedulers tested.
 * ponytail: add CP-SAT time limit config when demand > 600 to keep runtime bounded.
 */
@DisplayName("Fairness benchmark — max-min per shift type per demand level")
class FairnessBenchmarkTest {

    static final int[] DEMAND_LEVELS = {150, 300, 600, 1000};
    static final int STAFF_COUNT = 20;
    static final int PERIOD_DAYS = 30;
    static final int BEAM_WIDTH = 5;

    /** Algorithm names matching the whitelist in AutoSchedulingController. */
    static final List<String> ALGORITHMS = List.of(
            "ENHANCED_GREEDY", "BEAM_SEARCH", "RANDOM_RESTART_HC",
            "SIMULATED_ANNEALING", "CP_SAT"
    );

    @Test
    @DisplayName("Print fairness report per scheduler × demand")
    void benchmark_fairness_per_scheduler() {
        // Shared mocks
        HolidayRepository holidayRepo = mock(HolidayRepository.class);
        when(holidayRepo.findActiveHolidaysBetween(any(), any())).thenReturn(Collections.emptyList());
        CompensationDateCalculator compCalc = new CompensationDateCalculator(holidayRepo);

        // Shared staff pool
        List<Staff> allStaff = createStaff(STAFF_COUNT);

        // Shared shift types
        Map<String, ShiftType> shiftTypes = createShiftTypes();

        System.out.println("=== M07 Fairness Benchmark ===");
        System.out.printf("Staff=%d  PeriodDays=%d  BeamWidth=%d%n", STAFF_COUNT, PERIOD_DAYS, BEAM_WIDTH);
        System.out.println();

        for (String algo : ALGORITHMS) {
            for (int demand : DEMAND_LEVELS) {
                SchedulePeriod period = SchedulePeriod.builder()
                        .id(1)
                        .periodName("FairnessBench")
                        .startDate(LocalDate.of(2026, 8, 1))
                        .endDate(LocalDate.of(2026, 8, 1).plusDays(PERIOD_DAYS - 1))
                        .build();

                List<ShiftRequirement> reqs = createRequirements(period, shiftTypes, demand, allStaff);
                var cfg = createRuntimeConfig();

                // Prevent CP-SAT from running too long on large demands
                if ("CP_SAT".equals(algo) && demand >= 600) {
                    cfg.setBeamWidth(3); // reduce CP-SAT search space hint
                }

	                List<Schedule> schedules = runScheduler(algo, allStaff, reqs, period, cfg, compCalc);
	                Map<String, int[]> perType = computePerTypeMinMax(schedules, allStaff);
	                double[] interType = computeInterTypeDeviation(schedules, allStaff);

	                System.out.printf("[%-20s] demand=%4d  L01(mm=%2d-%2d) L02(mm=%2d-%2d) L03(mm=%2d-%2d) L04(mm=%2d-%2d)  inter(avg=%.1f,max=%2d)  total=%d%n",
	                        algo, demand,
	                        perType.get("L01")[0], perType.get("L01")[1],
	                        perType.get("L02")[0], perType.get("L02")[1],
	                        perType.get("L03")[0], perType.get("L03")[1],
	                        perType.get("L04")[0], perType.get("L04")[1],
	                        interType[0], (int) interType[1],
	                        schedules.size());
            }
            System.out.println();
        }

        System.out.println("=== End M07 Fairness Benchmark ===");
    }

    // ── Helpers ─────────────────────────────────────────────────────

    private List<Staff> createStaff(int count) {
        List<Staff> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Specialty sp = Specialty.builder()
                    .id(i % 4 + 1)
                    .name("Spec" + (i % 4 + 1))
                    .isActive(true)
                    .build();
            list.add(Staff.builder()
                    .id(i)
                    .username("s" + i)
                    .fullName("Staff " + i)
                    .isActive(true)
                    .specialty(sp)
                    .maxShiftsPerMonth(PERIOD_DAYS)
                    .build());
        }
        return list;
    }

    private Map<String, ShiftType> createShiftTypes() {
        Map<String, ShiftType> map = new LinkedHashMap<>();
        for (String id : new String[]{"L01", "L02", "L03", "L04"}) {
            map.put(id, ShiftType.builder()
                    .id(id).name(id)
                    .isOvernight("L01".equals(id))
                    .build());
        }
        return map;
    }

    /**
     * Create requirements for a given demand level.
     * Distributes demand roughly evenly across L01-L04 and across days.
     */
    private List<ShiftRequirement> createRequirements(
            SchedulePeriod period, Map<String, ShiftType> shiftTypes,
            int totalDemand, List<Staff> allStaff) {

        List<ShiftRequirement> reqs = new ArrayList<>();
        int days = PERIOD_DAYS;
        LocalDate start = period.getStartDate();
        String[] types = {"L01", "L02", "L03", "L04"};

        // Distribute demand: 30% L01, 25% L02, 25% L03, 20% L04
        int[] weights = {30, 25, 25, 20};
        int[] perTypeCount = new int[4];
        for (int t = 0; t < 4; t++) {
            perTypeCount[t] = Math.max(1, totalDemand * weights[t] / 100);
        }

        for (int t = 0; t < 4; t++) {
            int count = perTypeCount[t];
            int perDay = Math.max(1, count / days);
            int remainder = count - perDay * days;
            for (int d = 0; d < days; d++) {
                int daily = perDay + (d < remainder ? 1 : 0);
                if (daily > 0) {
                    Specialty sp = "L04".equals(types[t])
                            ? Specialty.builder().id(d % 4 + 1).name("Spec" + (d % 4 + 1)).isActive(true).build()
                            : null;
                    reqs.add(ShiftRequirement.builder()
                            .period(period)
                            .workDate(start.plusDays(d))
                            .shiftType(shiftTypes.get(types[t]))
                            .requiredStaffCount(daily)
                            .specialty(sp)
                            .build());
                }
            }
        }
        return reqs;
    }

    private AlgorithmConfigService.AlgorithmRuntimeConfig createRuntimeConfig() {
        return AlgorithmConfigService.AlgorithmRuntimeConfig.builder()
                .weekendWeight(new BigDecimal("2"))
                .overnightRecoveryHours(24)
                .greedyCoverageThreshold(new BigDecimal("0.85"))
                .balanceScoreMin(new BigDecimal("0.70"))
                .autoCompensationEnabled(true)
                .maxStaffPerShift(Math.max(1, STAFF_COUNT / 4))
                .maxShiftsPerStaff(PERIOD_DAYS)
                .maxShiftsPerDay(3)
                .l01MaxPerWeek(6).l02MaxPerWeek(6).l03MaxPerWeek(6).l04MaxPerWeek(6)
                .beamWidth(BEAM_WIDTH)
                .autoAdjustConfig(false) // off for deterministic benchmark
                .coverageWeight(new BigDecimal("0.40"))
                .fairnessWeight(new BigDecimal("0.35"))
                .constraintWeight(new BigDecimal("0.25"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Schedule> runScheduler(
            String algo, List<Staff> staff, List<ShiftRequirement> reqs,
            SchedulePeriod period, AlgorithmConfigService.AlgorithmRuntimeConfig cfg,
            CompensationDateCalculator compCalc) {

        Set<Integer> emptyExclude = Collections.emptySet();

        switch (algo) {
            case "ENHANCED_GREEDY":
                return new EnhancedGreedyScheduler(compCalc)
                        .solve(staff, reqs, period, cfg, emptyExclude, L04CrossSpecialtyConfig.DISABLED);
            case "BEAM_SEARCH":
                return new BeamSearchScheduler(compCalc)
                        .solve(staff, reqs, period, cfg, emptyExclude, L04CrossSpecialtyConfig.DISABLED);
            case "RANDOM_RESTART_HC":
                return new RandomRestartHCScheduler(compCalc)
                        .solve(staff, reqs, period, cfg, emptyExclude, L04CrossSpecialtyConfig.DISABLED);
            case "SIMULATED_ANNEALING":
                return new SimulatedAnnealingScheduler(compCalc)
                        .solve(staff, reqs, period, cfg, emptyExclude, L04CrossSpecialtyConfig.DISABLED);
            case "CP_SAT":
                return new CpSatScheduler(compCalc)
                        .solve(staff, reqs, period, cfg, emptyExclude, L04CrossSpecialtyConfig.DISABLED);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algo);
        }
    }

    /**
     * Compute per-shift-type min and max counts across staff.
     * Returns Map<shiftTypeId, int[2]> where [0]=min, [1]=max.
     */
    static Map<String, int[]> computePerTypeMinMax(List<Schedule> schedules, List<Staff> allStaff) {
        // Initialize with all staff having 0 for each shift type
        Set<Integer> allStaffIds = allStaff.stream().map(Staff::getId).collect(Collectors.toSet());

        Map<String, Map<Integer, Integer>> counts = new HashMap<>();
        for (String type : new String[]{"L01", "L02", "L03", "L04"}) {
            Map<Integer, Integer> staffCounts = new HashMap<>();
            for (Staff s : allStaff) {
                staffCounts.put(s.getId(), 0);
            }
            counts.put(type, staffCounts);
        }

        // Count schedules per staff per shift type
        for (Schedule s : schedules) {
            String type = s.getShiftType().getId();
            if (counts.containsKey(type)) {
                counts.get(type).merge(s.getStaff().getId(), 1, Integer::sum);
            }
        }

        Map<String, int[]> result = new LinkedHashMap<>();
        for (String type : new String[]{"L01", "L02", "L03", "L04"}) {
            var staffCounts = counts.get(type);
            int min = staffCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            int max = staffCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            result.put(type, new int[]{min, max});
        }
	        return result;
	    }

	    /**
	     * Compute inter-type deviation per staff: max(L01,L02,L03) - min(L01,L02,L03).
	     * Returns double[2] = {averageDeviation, maxDeviation}.
	     */
	    static double[] computeInterTypeDeviation(List<Schedule> schedules, List<Staff> allStaff) {
	        // Count per type per staff
	        Map<String, Map<Integer, Integer>> counts = new HashMap<>();
	        for (String type : new String[]{"L01", "L02", "L03"}) {
	            Map<Integer, Integer> staffCounts = new HashMap<>();
	            for (Staff s : allStaff) {
	                staffCounts.put(s.getId(), 0);
	            }
	            counts.put(type, staffCounts);
	        }
	        for (Schedule s : schedules) {
	            String type = s.getShiftType().getId();
	            if (counts.containsKey(type)) {
	                counts.get(type).merge(s.getStaff().getId(), 1, Integer::sum);
	            }
	        }

	        double sumDev = 0;
	        int maxDev = 0;
	        int n = allStaff.size();
	        for (Staff st : allStaff) {
	            int sid = st.getId();
	            int l01 = counts.get("L01").getOrDefault(sid, 0);
	            int l02 = counts.get("L02").getOrDefault(sid, 0);
	            int l03 = counts.get("L03").getOrDefault(sid, 0);
	            int dev = Math.max(Math.max(l01, l02), l03) - Math.min(Math.min(l01, l02), l03);
	            sumDev += dev;
	            if (dev > maxDev) maxDev = dev;
	        }
	        return new double[]{n > 0 ? sumDev / n : 0, maxDev};
	    }
	}
