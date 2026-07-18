package com.hospital.scheduler.algorithm;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

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
public class CpSatScheduler {

    static {
        Loader.loadNativeLibraries();
    }

    private static final String[] WORK_SHIFTS = {"L01", "L02", "L03", "L04"};
    private static final double TIME_LIMIT_SECONDS = 30.0;
    
    @Autowired
    private com.hospital.scheduler.util.CompensationDateCalculator compensationDateCalculator;

    @Autowired
    private com.hospital.scheduler.repository.ScheduleRepository scheduleRepository;

    @Autowired
    private com.hospital.scheduler.repository.CompensationDayRepository compensationDayRepository;

    @Autowired
    private com.hospital.scheduler.repository.LeaveRequestRepository leaveRequestRepository;

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

        // Load database pre-existing L01 schedules and compensation days
        LocalDate periodStart = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();
        
        List<CompensationDay> compDays = compensationDayRepository.findInRange(periodStart, periodEnd);
        List<Schedule> l01Schedules = scheduleRepository.findL01SchedulesInRange(periodStart.minusDays(7), periodStart.minusDays(1));
        
        Map<Integer, Set<LocalDate>> dbBlockedDates = new HashMap<>();
        for (CompensationDay cd : compDays) {
            dbBlockedDates.computeIfAbsent(cd.getStaff().getId(), k -> new HashSet<>())
                    .add(cd.getCompensationDate());
        }
        for (Schedule s : l01Schedules) {
            int staffId = s.getStaff().getId();
            LocalDate workDate = s.getWorkDate();
            dbBlockedDates.computeIfAbsent(staffId, k -> new HashSet<>()).add(workDate.plusDays(1));
            LocalDate compDate = compensationDateCalculator.calculate(workDate);
            if (compDate != null) {
                dbBlockedDates.computeIfAbsent(staffId, k -> new HashSet<>()).add(compDate);
            }
        }

        // Add approved leave requests to blocked dates
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedInRange(periodStart, periodEnd);
        for (LeaveRequest lr : approvedLeaves) {
            int staffId = lr.getStaff().getId();
            LocalDate start = lr.getStartDate().isBefore(periodStart) ? periodStart : lr.getStartDate();
            LocalDate end = lr.getEndDate().isAfter(periodEnd) ? periodEnd : lr.getEndDate();
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                dbBlockedDates.computeIfAbsent(staffId, k -> new HashSet<>()).add(cursor);
                cursor = cursor.plusDays(1);
            }
        }

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

        // Constraint 1b: Database pre-loaded blocked dates (comp days & day-after-L01 rest)
        for (int s = 0; s < numStaff; s++) {
            int staffId = staffIds.get(s);
            Set<LocalDate> blocked = dbBlockedDates.get(staffId);
            if (blocked != null) {
                for (int d = 0; d < numDays; d++) {
                    if (blocked.contains(dates.get(d))) {
                        for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
                            model.addEquality(x[s][d][sh], 0);
                        }
                    }
                }
            }
        }

        // Constraint 2: Coverage - each shift type needs enough staff
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
                model.addGreaterOrEqual(sum.build(), minReq);
            }
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

        // Constraint 3b: Rest day N+1 after L01 (within scheduling period)
        for (int s = 0; s < numStaff; s++) {
            for (int d = 0; d < numDays - 1; d++) {
                LinearExprBuilder sum = LinearExpr.newBuilder();
                sum.add(x[s][d][0]); // L01 on day d
                for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
                    sum.add(x[s][d + 1][sh]); // any shift on day d+1
                }
                model.addLessOrEqual(sum.build(), 1);
            }
        }

        // Constraint 3c: Compensation day rest (for L01 shifts within period)
        for (int s = 0; s < numStaff; s++) {
            for (int d = 0; d < numDays; d++) {
                LocalDate compDate = compensationDateCalculator.calculate(dates.get(d));
                if (compDate != null && dates.contains(compDate)) {
                    int dComp = dates.indexOf(compDate);
                    LinearExprBuilder sum = LinearExpr.newBuilder();
                    sum.add(x[s][d][0]); // L01 on day d
                    for (int sh = 0; sh < WORK_SHIFTS.length; sh++) {
                        sum.add(x[s][dComp][sh]); // any shift on compensation day
                    }
                    model.addLessOrEqual(sum.build(), 1);
                }
            }
        }

        // Objective: Balance fairness (minimize max-min gap) + maximize coverage
        // Coverage already ensured by constraint 2 (min staff per shift)
        // Fairness: minimize the gap between most-loaded and least-loaded staff
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
        for (int s = 0; s < numStaff; s++) {
            model.addLessOrEqual(minShifts, totalShiftsPerStaff[s]);
            model.addGreaterOrEqual(maxShifts, totalShiftsPerStaff[s]);
        }
        
        // Objective: minimize max shifts per staff (balance) while satisfying coverage
        model.minimize(maxShifts);

        // Solve
        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(TIME_LIMIT_SECONDS);
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
