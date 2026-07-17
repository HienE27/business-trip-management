package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Simulated Annealing scheduler — ported from algorithm_comparison.ipynb.
 * Uses greedy initial solution then SA refinement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulatedAnnealingScheduler {

    private final CompensationDateCalculator compensationDateCalculator;
    private static final String[] SHIFT_TYPES = {"L01", "L02", "L03", "L04"};
    private static final int MAX_ITER = 500;
    private static final double INITIAL_TEMP = 50.0;
    private static final double COOLING_RATE = 0.99;

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

        // Phase 1: Initial greedy solution
        List<Schedule> current = greedyInitial(activeStaff, requirements, period,
                runtimeConfig, excludedStaffIds, staffMap, rng);
        if (current.isEmpty()) return current;

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

            if (hasConflict(current, s.getStaff().getId(), s.getWorkDate(), newType)) continue;

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
                s.setShiftType(findShiftType(oldType, requirements));
                ShiftRequirement oldReq = findMatchingRequirement(
                        s.getStaff(), s.getWorkDate(), oldType, requirements);
                if (oldReq != null) s.setRequirement(oldReq);
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
        Map<Integer, Set<LocalDate>> staffCompDays = new HashMap<>();
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
                    .filter(s -> {
                        if (specId == null && !"L04".equals(shiftType) && s.getSpecialty() == null) return false;
                        return true;
                    })
                    .filter(s -> {
                        Set<LocalDate> compDays = staffCompDays.get(s.getId());
                        return compDays == null || !compDays.contains(req.getWorkDate());
                    })
                    .map(Staff::getId)
                    .collect(Collectors.toList());

            Collections.shuffle(eligible, rng);
            for (int i = 0; i < Math.min(required, eligible.size()); i++) {
                int sid = eligible.get(i);
                String key = sid + "|" + req.getWorkDate();
                if (!assigned.contains(key)) {
                    assigned.add(key);
                    counts.merge(sid, 1, Integer::sum);
                    // Compensation day: nếu gán L01, tính ngày nghỉ bù
                    if ("L01".equals(shiftType)) {
                        LocalDate compDate = compensationDateCalculator.calculate(req.getWorkDate());
                        if (compDate != null) {
                            staffCompDays.computeIfAbsent(sid, k -> new HashSet<>()).add(compDate);
                        }
                    }
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

    private boolean hasConflict(List<Schedule> schedules, int staffId,
            LocalDate workDate, String newType) {
        for (Schedule s : schedules) {
            if (s.getStaff().getId() != staffId) continue;
            if (s.getWorkDate().equals(workDate)) {
                String existingType = s.getShiftType().getId();
                if (("L01".equals(newType) && "L02".equals(existingType))
                        || ("L02".equals(newType) && "L01".equals(existingType))
                        || ("L03".equals(newType) && "L04".equals(existingType))
                        || ("L04".equals(newType) && "L03".equals(existingType)))
                    return true;
            }
            // Consecutive L01 check
            if ("L01".equals(newType) && "L01".equals(s.getShiftType().getId())) {
                if (Math.abs(s.getWorkDate().toEpochDay() - workDate.toEpochDay()) == 1)
                    return true;
            }
            // Compensation day check: nếu staff có L01 mà ngày nghỉ bù trùng workDate → block
            if ("L01".equals(s.getShiftType().getId())) {
                LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
                if (compDate != null && compDate.equals(workDate)) return true;
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
