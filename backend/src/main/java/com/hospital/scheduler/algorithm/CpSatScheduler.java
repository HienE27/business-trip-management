package com.hospital.scheduler.algorithm;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OR-Tools CP-SAT scheduler — ported from algorithm_comparison.ipynb.
 * Uses Google OR-Tools Constraint Programming solver for optimal scheduling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CpSatScheduler {

    private final CompensationDateCalculator compensationDateCalculator;

    static {
        Loader.loadNativeLibraries();
    }

    private static final String[] WORK_SHIFTS = {"L01", "L02", "L03", "L04"};
    // CP-SAT solver wall-clock cap (seconds). Scales with problem size: 5s base
    // + 0.5s per day, capped at 60s. OR-Tools returns the best feasible solution
    // found when this fires. Tune up for larger periods / more staff.
    private static final double TIME_LIMIT_BASE_SECONDS = 5.0;
    private static final double TIME_LIMIT_PER_DAY_SECONDS = 0.5;
    private static final double TIME_LIMIT_MAX_SECONDS = 60.0;

    public List<Schedule> solve(
            List<Staff> activeStaff,
            List<ShiftRequirement> requirements,
            SchedulePeriod period,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            Set<Integer> excludedStaffIds) {

        long start = System.currentTimeMillis();

        // Prepare data
        List<Staff> staffList = activeStaff.stream()
                .filter(s -> excludedStaffIds == null || !excludedStaffIds.contains(s.getId()))
                .collect(Collectors.toList());
        List<Integer> staffIds = staffList.stream().map(Staff::getId).collect(Collectors.toList());
        List<LocalDate> dates = requirements.stream()
                .map(ShiftRequirement::getWorkDate)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        int numDays = dates.size();
        int numStaff = staffList.size();

        // Build min staff per day per shift
        Map<String, Integer> minStaffPerDay = new HashMap<>();
        for (String shift : WORK_SHIFTS) {
            int max = 0;
            for (ShiftRequirement r : requirements) {
                if (r.getShiftType().getId().equals(shift)) {
                    max = Math.max(max, r.getRequiredStaffCount());
                }
            }
            minStaffPerDay.put(shift, max);
        }

        // Build shift requirements lookup: (day, shift) -> set of specialty_ids
        Map<String, Set<Integer>> shiftReqs = new HashMap<>();
        for (ShiftRequirement r : requirements) {
            String key = r.getWorkDate() + "|" + r.getShiftType().getId();
            shiftReqs.computeIfAbsent(key, k -> new HashSet<>());
            if (r.getSpecialty() != null) {
                shiftReqs.get(key).add(r.getSpecialty().getId());
            }
        }

        // Build staff specialty map
        Map<Integer, Integer> staffSpecialty = new HashMap<>();
        for (Staff s : staffList) {
            if (s.getSpecialty() != null) {
                staffSpecialty.put(s.getId(), s.getSpecialty().getId());
            }
        }

        Map<Integer, Staff> staffMap = staffList.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        // Create the model
        CpModel model = new CpModel();

        // Variables: x[sid][d][shift] = 1 if staff s works shift on day d
        IntVar[][][] x = new IntVar[numStaff][numDays][WORK_SHIFTS.length];
        for (int s = 0; s < numStaff; s++) {
            for (int d = 0; d < numDays; d++) {
                for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
                    x[s][d][sh] = model.newBoolVar(
                            String.format("x_%d_%d_%s", staffIds.get(s), d, WORK_SHIFTS[sh]));
                }
            }
        }

        // Constraint 1: Each staff at most 1 shift per day
        for (int s = 0; s < numStaff; s++) {
            for (int d = 0; d < numDays; d++) {
                LinearExprBuilder sum = LinearExpr.newBuilder();
                for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
                    sum.add(x[s][d][sh]);
                }
                model.addLessOrEqual(sum.build(), 1);
            }
        }

        // Constraint 2: Coverage — SOFT CONSTRAINT (nới lỏng, phạt khi thiếu)
        // Thay vì hard constraint, dùng penalty variable cho mỗi slot thiếu
        IntVar totalShortfall = model.newIntVar(0, numDays * numStaff * WORK_SHIFTS.length, "total_shortfall");
        LinearExprBuilder shortfallSum = LinearExpr.newBuilder();
        
        for (int d = 0; d < numDays; d++) {
            for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
                String shiftType = WORK_SHIFTS[sh];
                int minReq = minStaffPerDay.getOrDefault(shiftType, 1);

                LinearExprBuilder sum = LinearExpr.newBuilder();
                for (int s = 0; s < numStaff; s++) {
                    // Check specialty match for L04
                    String key = dates.get(d) + "|" + shiftType;
                    Set<Integer> requiredSpecs = shiftReqs.getOrDefault(key, new HashSet<>());
                    if (!requiredSpecs.isEmpty()) {
                        Integer specId = staffSpecialty.get(staffIds.get(s));
                        if (specId != null && requiredSpecs.contains(specId)) {
                            sum.add(x[s][d][sh]);
                        }
                    } else {
                        sum.add(x[s][d][sh]);
                    }
                }
                // assigned + shortfall >= minReq (shortfall = max(0, minReq - assigned))
                IntVar shortfall = model.newIntVar(0, minReq, "shortfall_" + d + "_" + sh);
                LinearExprBuilder covWithShortfall = LinearExpr.newBuilder();
                covWithShortfall.add(sum.build());
                covWithShortfall.add(shortfall);
                model.addGreaterOrEqual(covWithShortfall.build(), minReq);
                shortfallSum.add(shortfall);
            }
        }
        model.addEquality(totalShortfall, shortfallSum.build());

        // Constraint 3: No consecutive L01
        for (int s = 0; s < numStaff; s++) {
            for (int d = 0; d < numDays - 1; d++) {
                LinearExprBuilder sum = LinearExpr.newBuilder();
                sum.add(x[s][d][0]); // L01 index
                sum.add(x[s][d + 1][0]);
                model.addLessOrEqual(sum.build(), 1);
            }
        }

        // Constraint 4: Compensation day after L01 — không gán ca nào vào ngày nghỉ bù
        // Nếu staff có L01 ngày d, không được làm bất kỳ ca nào vào ngày nghỉ bù
        for (int s = 0; s < numStaff; s++) {
            for (int d = 0; d < numDays; d++) {
                LocalDate shiftDate = dates.get(d);
                LocalDate compDate = compensationDateCalculator.calculateWithoutHolidays(shiftDate);
                if (compDate == null) continue;
                // Find day index for compDate
                int compDayIndex = -1;
                for (int d2 = 0; d2 < numDays; d2++) {
                    if (dates.get(d2).equals(compDate)) { compDayIndex = d2; break; }
                }
                if (compDayIndex < 0) continue; // compensation day outside period

                // x[s][d][L01] + sum_{sh} x[s][compDayIndex][sh] <= 1
                LinearExprBuilder sum = LinearExpr.newBuilder();
                sum.add(x[s][d][0]); // L01 index
                for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
                    sum.add(x[s][compDayIndex][sh]);
                }
                model.addLessOrEqual(sum.build(), 1);
            }
        }

        // Objective: minimize shortfall (coverage) + balance fairness + per-type balance
        IntVar[] totalShiftsPerStaff = new IntVar[numStaff];
        for (int s = 0; s < numStaff; s++) {
            LinearExprBuilder sum = LinearExpr.newBuilder();
            for (int d = 0; d < numDays; d++) {
                for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
                    sum.add(x[s][d][sh]);
                }
            }
            totalShiftsPerStaff[s] = model.newIntVar(0, numDays, "total_" + staffIds.get(s));
            model.addEquality(totalShiftsPerStaff[s], sum.build());
        }
        IntVar minShifts = model.newIntVar(0, numDays, "min_shifts");
        IntVar maxShifts = model.newIntVar(0, numDays, "max_shifts");
        model.addMinEquality(minShifts, totalShiftsPerStaff);
        model.addMaxEquality(maxShifts, totalShiftsPerStaff);
        
        // Per-type balance: tạo max-per-type variables
        // L01 index=0, L02=1, L03=2, L04=3
        IntVar[] maxPerType = new IntVar[WORK_SHIFTS.length];
        for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
            IntVar[] perStaff = new IntVar[numStaff];
            for (int s = 0; s < numStaff; s++) {
                perStaff[s] = model.newIntVar(0, numDays, "type_" + WORK_SHIFTS[sh] + "_" + staffIds.get(s));
                LinearExprBuilder sum = LinearExpr.newBuilder();
                for (int d = 0; d < numDays; d++) {
                    sum.add(x[s][d][sh]);
                }
                model.addEquality(perStaff[s], sum.build());
            }
            maxPerType[sh] = model.newIntVar(0, numDays, "max_type_" + WORK_SHIFTS[sh]);
            model.addMaxEquality(maxPerType[sh], perStaff);
        }
        
        // Objective: coverage + total balance + per-type balance
        LinearExprBuilder objective = LinearExpr.newBuilder();
        objective.addTerm(totalShortfall, 20);       // coverage: 20pt per missing slot
        objective.addTerm(maxShifts, 5);              // total max: 5pt per shift
        objective.addTerm(minShifts, -5);             // total min: -5pt → maximize min
        for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
            objective.addTerm(maxPerType[sh], 2);     // per-type max: 2pt per shift
        }
        model.minimize(objective.build());

        // Solve
        CpSolver solver = new CpSolver();
        double timeLimit = Math.min(TIME_LIMIT_MAX_SECONDS,
                TIME_LIMIT_BASE_SECONDS + numDays * TIME_LIMIT_PER_DAY_SECONDS);
        solver.getParameters().setMaxTimeInSeconds(timeLimit);
        solver.getParameters().setLogSearchProgress(false);

        CpSolverStatus status = solver.solve(model);

        // Build result
        List<Schedule> result = new ArrayList<>();
        if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
            for (int s = 0; s < numStaff; s++) {
                int sid = staffIds.get(s);
                for (int d = 0; d < numDays; d++) {
                    for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
                        if (solver.value(x[s][d][sh]) > 0) {
                            LocalDate workDate = dates.get(d);
                            String shiftTypeId = WORK_SHIFTS[sh];

                            Schedule schedule = new Schedule();
                            schedule.setStaff(staffMap.get(sid));
                            schedule.setPeriod(period);
                            schedule.setWorkDate(workDate);
                            schedule.setShiftType(findShiftType(shiftTypeId, requirements));
                            schedule.setRequirement(findRequirement(staffMap.get(sid), workDate, shiftTypeId, requirements));
                            schedule.setHasConflict(false);
                            result.add(schedule);
                        }
                    }
                }
            }
        }

        log.info("CpSatScheduler: status={} schedules={} in {}ms",
                status, result.size(), System.currentTimeMillis() - start);
        return result;
    }

    private com.hospital.scheduler.entity.ShiftType findShiftType(
            String id, List<ShiftRequirement> reqs) {
        return reqs.stream().filter(r -> r.getShiftType().getId().equals(id))
                .findFirst().map(ShiftRequirement::getShiftType).orElse(null);
    }

    private ShiftRequirement findRequirement(Staff staff, LocalDate date,
            String shiftTypeId, List<ShiftRequirement> reqs) {
        return reqs.stream()
                .filter(r -> r.getShiftType().getId().equals(shiftTypeId)
                        && r.getWorkDate().equals(date))
                .findFirst().orElse(null);
    }
}
