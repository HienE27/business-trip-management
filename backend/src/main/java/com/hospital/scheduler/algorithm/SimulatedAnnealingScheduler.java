package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simulated Annealing scheduler — ported from algorithm_comparison.ipynb.
 * Uses greedy initial solution then SA refinement.
 */
@Slf4j
@Component
public class SimulatedAnnealingScheduler {

    private static final String[] SHIFT_TYPES = {"L01", "L02", "L03", "L04"};
    private static final int MAX_ITER = 500;
    private static final double INITIAL_TEMP = 50.0;
    private static final double COOLING_RATE = 0.99;

    @Autowired
    private com.hospital.scheduler.util.CompensationDateCalculator compensationDateCalculator;

    @Autowired
    private com.hospital.scheduler.repository.ScheduleRepository scheduleRepository;

    @Autowired
    private com.hospital.scheduler.repository.CompensationDayRepository compensationDayRepository;

    public List<Schedule> solve(
            List<Staff> activeStaff,
            List<ShiftRequirement> requirements,
            SchedulePeriod period,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            Set<Integer> excludedStaffIds) {

        long start = System.currentTimeMillis();
        Random rng = new Random();
        Map<Integer, Staff> staffMap = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        // Load database pre-existing L01 schedules and compensation days
        LocalDate periodStart = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();
        
        List<CompensationDay> compDays = compensationDayRepository.findInRange(periodStart, periodEnd);
        List<Schedule> l01Schedules = scheduleRepository.findL01SchedulesInRange(periodStart.minusDays(7), periodStart.minusDays(1));
        
        Map<Integer, Set<LocalDate>> blockedDates = new HashMap<>();
        for (CompensationDay cd : compDays) {
            blockedDates.computeIfAbsent(cd.getStaff().getId(), k -> new HashSet<>())
                    .add(cd.getCompensationDate());
        }
        for (Schedule s : l01Schedules) {
            int staffId = s.getStaff().getId();
            LocalDate workDate = s.getWorkDate();
            blockedDates.computeIfAbsent(staffId, k -> new HashSet<>()).add(workDate.plusDays(1));
            LocalDate compDate = compensationDateCalculator.calculate(workDate);
            if (compDate != null) {
                blockedDates.computeIfAbsent(staffId, k -> new HashSet<>()).add(compDate);
            }
        }

        // Phase 1: Initial greedy solution
        List<Schedule> current = greedyInitial(activeStaff, requirements, period,
                runtimeConfig, excludedStaffIds, staffMap, rng);
        if (current.isEmpty()) return current;

        // Initialize dynamic blockedDates from greedy initial schedules
        Map<Integer, Set<LocalDate>> runBlockedDates = new HashMap<>();
        for (var entry : blockedDates.entrySet()) {
            runBlockedDates.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        for (Schedule s : current) {
            if ("L01".equals(s.getShiftType().getId())) {
                int sid = s.getStaff().getId();
                runBlockedDates.computeIfAbsent(sid, k -> new HashSet<>()).add(s.getWorkDate().plusDays(1));
                LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
                if (compDate != null) {
                    runBlockedDates.computeIfAbsent(sid, k -> new HashSet<>()).add(compDate);
                }
            }
        }

        int currentScore = current.size();
        List<Schedule> bestSolution = new ArrayList<>(current);
        int bestScore = currentScore;

        double T = INITIAL_TEMP;

        // Phase 2: Simulated Annealing
        for (int iter = 0; iter < MAX_ITER && !current.isEmpty(); iter++) {
            Schedule s = current.get(rng.nextInt(current.size()));
            String oldType = s.getShiftType().getId();
            String newType = SHIFT_TYPES[rng.nextInt(SHIFT_TYPES.length)];
            if (newType.equals(oldType)) continue;

            if (hasConflict(current, s.getStaff().getId(), s.getWorkDate(), newType, runBlockedDates)) continue;

            // Temp update to block list for evaluation
            if ("L01".equals(oldType)) {
                Set<LocalDate> blocked = runBlockedDates.computeIfAbsent(s.getStaff().getId(), k -> new HashSet<>());
                blocked.remove(s.getWorkDate().plusDays(1));
                LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
                if (compDate != null) blocked.remove(compDate);
            }
            if ("L01".equals(newType)) {
                Set<LocalDate> blocked = runBlockedDates.computeIfAbsent(s.getStaff().getId(), k -> new HashSet<>());
                blocked.add(s.getWorkDate().plusDays(1));
                LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
                if (compDate != null) blocked.add(compDate);
            }

            s.setShiftType(findShiftType(newType, requirements));
            ShiftRequirement newReq = findMatchingRequirement(
                    s.getStaff(), s.getWorkDate(), newType, requirements);
            if (newReq != null) s.setRequirement(newReq);

            int newScore = current.size();
            double delta = newScore - currentScore;

            if (delta > 0 || rng.nextDouble() < Math.exp(delta / Math.max(T, 0.01))) {
                currentScore = newScore;
                if (newScore > bestScore) {
                    bestScore = newScore;
                    bestSolution = new ArrayList<>(current);
                }
            } else {
                // Revert type change
                s.setShiftType(findShiftType(oldType, requirements));
                ShiftRequirement oldReq = findMatchingRequirement(
                        s.getStaff(), s.getWorkDate(), oldType, requirements);
                if (oldReq != null) s.setRequirement(oldReq);

                // Revert block list change
                if ("L01".equals(newType)) {
                    Set<LocalDate> blocked = runBlockedDates.computeIfAbsent(s.getStaff().getId(), k -> new HashSet<>());
                    blocked.remove(s.getWorkDate().plusDays(1));
                    LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
                    if (compDate != null) blocked.remove(compDate);
                }
                if ("L01".equals(oldType)) {
                    Set<LocalDate> blocked = runBlockedDates.computeIfAbsent(s.getStaff().getId(), k -> new HashSet<>());
                    blocked.add(s.getWorkDate().plusDays(1));
                    LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
                    if (compDate != null) blocked.add(compDate);
                }
            }

            T *= COOLING_RATE;
        }

        log.info("SimulatedAnnealing: {} schedules (best={}) in {}ms",
                bestSolution.size(), bestScore, System.currentTimeMillis() - start);
        return bestSolution;
    }

    private List<Schedule> greedyInitial(List<Staff> staff, List<ShiftRequirement> reqs,
            SchedulePeriod period, AlgorithmConfigService.AlgorithmRuntimeConfig config,
            Set<Integer> excluded, Map<Integer, Staff> staffMap, Random rng) {
        List<Schedule> result = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
        Map<Integer, Integer> counts = new HashMap<>();
        int maxShifts = config != null && config.getMaxShiftsPerStaff() > 0
                ? config.getMaxShiftsPerStaff() : Integer.MAX_VALUE;

        for (ShiftRequirement req : reqs) {
            int required = req.getRequiredStaffCount();
            Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;
            String shiftType = req.getShiftType().getId();

            List<Integer> eligible = staff.stream()
                    .filter(s -> excluded == null || !excluded.contains(s.getId()))
                    .filter(s -> !assigned.contains(s.getId() + "|" + req.getWorkDate()))
                    .filter(s -> counts.getOrDefault(s.getId(), 0) < maxShifts)
                    .filter(s -> specId == null || (s.getSpecialty() != null && s.getSpecialty().getId().equals(specId)))
                    .map(Staff::getId)
                    .collect(Collectors.toList());

            Collections.shuffle(eligible, rng);
            for (int i = 0; i < Math.min(required, eligible.size()); i++) {
                int sid = eligible.get(i);
                String key = sid + "|" + req.getWorkDate();
                if (!assigned.contains(key)) {
                    assigned.add(key);
                    counts.merge(sid, 1, Integer::sum);
                    Schedule s = new Schedule();
                    s.setStaff(staffMap.get(sid));
                    s.setPeriod(period);
                    s.setWorkDate(req.getWorkDate());
                    s.setShiftType(req.getShiftType());
                    s.setRequirement(req);
                    s.setHasConflict(false);
                    result.add(s);
                }
            }
        }
        return result;
    }

    private boolean isConflictPair(String t1, String t2) {
        if ("L01".equals(t1) && !"L01".equals(t2)) return true;
        if ("L01".equals(t2) && !"L01".equals(t1)) return true;
        if (("L03".equals(t1) && "L04".equals(t2)) || ("L04".equals(t1) && "L03".equals(t2))) return true;
        return false;
    }

    private boolean hasConflict(List<Schedule> schedules, int staffId,
            LocalDate workDate, String newType, Map<Integer, Set<LocalDate>> blockedDates) {
        if (blockedDates != null) {
            Set<LocalDate> blocked = blockedDates.get(staffId);
            if (blocked != null && blocked.contains(workDate)) return true;
        }

        for (Schedule s : schedules) {
            if (s.getStaff().getId() != staffId) continue;
            
            // Same day same type or conflict
            if (s.getWorkDate().equals(workDate)) {
                String existingType = s.getShiftType().getId();
                if (isConflictPair(newType, existingType)) {
                    return true;
                }
            }

            // Consecutive L01 & Day-after rest / compensation day
            if ("L01".equals(newType)) {
                long diff = Math.abs(s.getWorkDate().toEpochDay() - workDate.toEpochDay());
                if (diff == 1) return true;
            }
            if ("L01".equals(s.getShiftType().getId())) {
                long diff = workDate.toEpochDay() - s.getWorkDate().toEpochDay();
                if (diff == 1) return true;
                LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
                if (workDate.equals(compDate)) return true;
            }
        }
        return false;
    }

    private com.hospital.scheduler.entity.ShiftType findShiftType(
            String id, List<ShiftRequirement> reqs) {
        return reqs.stream().filter(r -> r.getShiftType().getId().equals(id))
                .findFirst().map(ShiftRequirement::getShiftType).orElse(null);
    }

    private ShiftRequirement findMatchingRequirement(Staff staff, LocalDate workDate,
            String shiftTypeId, List<ShiftRequirement> reqs) {
        List<ShiftRequirement> candidates = reqs.stream()
                .filter(r -> r.getShiftType().getId().equals(shiftTypeId) && r.getWorkDate().equals(workDate))
                .toList();
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);
        if (staff.getSpecialty() != null) {
            for (ShiftRequirement r : candidates)
                if (r.getSpecialty() != null && r.getSpecialty().getId().equals(staff.getSpecialty().getId()))
                    return r;
        }
        return candidates.get(0);
    }
}
