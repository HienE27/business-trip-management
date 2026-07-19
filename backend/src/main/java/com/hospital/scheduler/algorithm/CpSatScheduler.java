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
    private static final double TIME_LIMIT_BASE_SECONDS = 10.0;
    private static final double TIME_LIMIT_PER_DAY_SECONDS = 1.5;
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

        // Per (day, shift[, specialty]) required count — not max-over-days
        Map<String, Integer> requiredByDayShift = new HashMap<>();
        // Build shift requirements lookup: (day, shift) -> set of specialty_ids
        Map<String, Set<Integer>> shiftReqs = new HashMap<>();
        for (ShiftRequirement r : requirements) {
            String key = r.getWorkDate() + "|" + r.getShiftType().getId();
            requiredByDayShift.merge(key, r.getRequiredStaffCount(), Integer::sum);
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

        // Constraint 1: pairwise same-day conflicts only (L01↔L02, L03↔L04).
        // Non-conflicting combos (L01+L03, L01+L04, L02+L03, L02+L04) allowed.
        // L01=0, L02=1, L03=2, L04=3
        for (int s = 0; s < numStaff; s++) {
            for (int d = 0; d < numDays; d++) {
                model.addLessOrEqual(LinearExpr.newBuilder().add(x[s][d][0]).add(x[s][d][1]).build(), 1); // L01↔L02
                model.addLessOrEqual(LinearExpr.newBuilder().add(x[s][d][2]).add(x[s][d][3]).build(), 1); // L03↔L04
            }
        }

        // Constraint 2: Coverage — SOFT shortfall + HARD cap (no over-assign)
        IntVar totalShortfall = model.newIntVar(0, numDays * numStaff * WORK_SHIFTS.length, "total_shortfall");
        LinearExprBuilder shortfallSum = LinearExpr.newBuilder();
        // Per-type shortfall (L01 weighted higher in objective)
        IntVar[] shortfallByType = new IntVar[WORK_SHIFTS.length];
        LinearExprBuilder[] typeShortSums = new LinearExprBuilder[WORK_SHIFTS.length];
        for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
            typeShortSums[sh] = LinearExpr.newBuilder();
        }

        for (int d = 0; d < numDays; d++) {
            for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
                String shiftType = WORK_SHIFTS[sh];
                String key = dates.get(d) + "|" + shiftType;
                int minReq = requiredByDayShift.getOrDefault(key, 0);
                if (minReq <= 0) continue;

                LinearExprBuilder sum = LinearExpr.newBuilder();
                for (int s = 0; s < numStaff; s++) {
                    Set<Integer> requiredSpecs = shiftReqs.getOrDefault(key, Collections.emptySet());
                    if (!requiredSpecs.isEmpty()) {
                        Integer specId = staffSpecialty.get(staffIds.get(s));
                        if (specId != null && requiredSpecs.contains(specId)) {
                            sum.add(x[s][d][sh]);
                        }
                    } else if (staffSpecialty.containsKey(staffIds.get(s))) {
                        sum.add(x[s][d][sh]);
                    }
                }
                LinearExpr assigned = sum.build();
                // Cap: never assign more than required for this (day, type)
                model.addLessOrEqual(assigned, minReq);
                IntVar shortfall = model.newIntVar(0, minReq, "shortfall_" + d + "_" + sh);
                model.addGreaterOrEqual(LinearExpr.newBuilder().add(assigned).add(shortfall).build(), minReq);
                shortfallSum.add(shortfall);
                typeShortSums[sh].add(shortfall);
            }
        }
        model.addEquality(totalShortfall, shortfallSum.build());
        for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
            shortfallByType[sh] = model.newIntVar(0, numDays * numStaff, "sf_type_" + WORK_SHIFTS[sh]);
            model.addEquality(shortfallByType[sh], typeShortSums[sh].build());
        }

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

// Multi-shift/day allowed → upper bound is numDays * max types/day (2 pairs)
        int maxLoad = numDays * 2;

        IntVar[] totalShiftsPerStaff = new IntVar[numStaff];
        for (int s = 0; s < numStaff; s++) {
            LinearExprBuilder sum = LinearExpr.newBuilder();
            for (int d = 0; d < numDays; d++) {
                for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
                    sum.add(x[s][d][sh]);
                }
            }
            totalShiftsPerStaff[s] = model.newIntVar(0, maxLoad, "total_" + staffIds.get(s));
            model.addEquality(totalShiftsPerStaff[s], sum.build());
        }
        IntVar minShifts = model.newIntVar(0, maxLoad, "min_shifts");
        IntVar maxShiftsVar = model.newIntVar(0, maxLoad, "max_shifts");
        model.addMinEquality(minShifts, totalShiftsPerStaff);
        model.addMaxEquality(maxShiftsVar, totalShiftsPerStaff);

        // Per-type max (light fairness pressure)
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

        // Coverage first: L01 shortfall * 100, other types * 40, fairness light
        LinearExprBuilder objective = LinearExpr.newBuilder();
        objective.addTerm(shortfallByType[0], 100); // L01
        objective.addTerm(shortfallByType[1], 40);  // L02
        objective.addTerm(shortfallByType[2], 40);  // L03
        objective.addTerm(shortfallByType[3], 40);  // L04
        objective.addTerm(maxShiftsVar, 2);
        objective.addTerm(minShifts, -2);
        for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
            objective.addTerm(maxPerType[sh], 1);
        }
        model.minimize(objective.build());

        CpSolver solver = new CpSolver();
        double timeLimit = Math.min(TIME_LIMIT_MAX_SECONDS,
                TIME_LIMIT_BASE_SECONDS + numDays * TIME_LIMIT_PER_DAY_SECONDS);
        solver.getParameters().setMaxTimeInSeconds(timeLimit);
        solver.getParameters().setLogSearchProgress(false);
        solver.getParameters().setNumSearchWorkers(4);

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
        return ScheduleConflictUtils.findShiftType(id, reqs);
    }

    private ShiftRequirement findRequirement(Staff staff, LocalDate date,
            String shiftTypeId, List<ShiftRequirement> reqs) {
        return ScheduleConflictUtils.findMatchingRequirement(staff, date, shiftTypeId, reqs);
    }
}
