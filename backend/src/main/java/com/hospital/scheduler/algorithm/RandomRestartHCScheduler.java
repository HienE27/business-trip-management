package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Random Restart Hill Climbing — multiple random greedy starts + hill climbing.
 * Ported from solve_random_restart_hc.
 */
@Slf4j
@Component
public class RandomRestartHCScheduler {

    private static final int NUM_RESTARTS = 4;
    private static final int MAX_ITER = 150;
    private static final String[] SHIFT_TYPES = {"L01", "L02", "L03", "L04"};

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

        List<Schedule> bestSchedules = new ArrayList<>();
        int bestCount = 0;

        for (int restart = 0; restart < NUM_RESTARTS; restart++) {
            List<Schedule> current = randomGreedy(activeStaff, requirements, period,
                    runtimeConfig, excludedStaffIds, staffMap, rng);
            if (current.isEmpty()) continue;

            for (int iter = 0; iter < MAX_ITER && !current.isEmpty(); iter++) {
                Schedule s = current.get(rng.nextInt(current.size()));
                String newType = SHIFT_TYPES[rng.nextInt(SHIFT_TYPES.length)];
                if (newType.equals(s.getShiftType().getId())) continue;

                s.setShiftType(findShiftType(newType, requirements));
                int score = current.size();
                if (score > bestCount) {
                    bestCount = score;
                    bestSchedules = new ArrayList<>(current);
                }
            }
        }

        log.info("RandomRestartHC: {} schedules in {}ms", bestSchedules.size(),
                System.currentTimeMillis() - start);
        return bestSchedules;
    }

    private List<Schedule> randomGreedy(List<Staff> staff, List<ShiftRequirement> reqs,
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
                    s.setHasConflict(false);
                    result.add(s);
                }
            }
        }
        return result;
    }

    private com.hospital.scheduler.entity.ShiftType findShiftType(
            String id, List<ShiftRequirement> reqs) {
        return reqs.stream().filter(r -> r.getShiftType().getId().equals(id))
                .findFirst().map(ShiftRequirement::getShiftType).orElse(null);
    }
}
