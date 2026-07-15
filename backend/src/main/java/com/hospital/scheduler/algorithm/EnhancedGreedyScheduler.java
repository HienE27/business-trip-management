package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced Greedy — Greedy + fatigue awareness.
 * Avoids assigning consecutive-day shifts to the same staff when possible.
 * Ported from algorithm_comparison.ipynb (generate_enhanced_greedy_schedule).
 */
@Slf4j
@Component
public class EnhancedGreedyScheduler {

    public List<Schedule> solve(
            List<Staff> activeStaff,
            List<ShiftRequirement> requirements,
            SchedulePeriod period,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            Set<Integer> excludedStaffIds) {

        long start = System.currentTimeMillis();
        Map<Integer, Staff> staffMap = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));
        Map<Integer, List<LocalDate>> staffLastWork = new HashMap<>();
        Map<Integer, Integer> staffCount = new HashMap<>();
        Set<String> assigned = new HashSet<>(); // "staffId_date"

        // Group by date+shift
        Map<LocalDate, List<ShiftRequirement>> byDate = requirements.stream()
                .collect(Collectors.groupingBy(ShiftRequirement::getWorkDate, TreeMap::new, Collectors.toList()));

        List<Schedule> result = new ArrayList<>();

        for (Map.Entry<LocalDate, List<ShiftRequirement>> e : byDate.entrySet()) {
            LocalDate date = e.getKey();
            for (ShiftRequirement req : e.getValue()) {
                String shiftTypeId = req.getShiftType().getId();
                int required = req.getRequiredStaffCount();
                Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;

                // Score candidates: fatigue = gap since last work
                List<ScoredStaff> candidates = new ArrayList<>();
                for (Staff s : activeStaff) {
                    if (excludedStaffIds != null && excludedStaffIds.contains(s.getId())) continue;
                    if (assigned.contains(s.getId() + "|" + date)) continue;
                    if (specId != null && (s.getSpecialty() == null || !s.getSpecialty().getId().equals(specId))) continue;

                    int cnt = staffCount.getOrDefault(s.getId(), 0);
                    int maxShifts = runtimeConfig != null && runtimeConfig.getMaxShiftsPerStaff() > 0
                            ? runtimeConfig.getMaxShiftsPerStaff() : Integer.MAX_VALUE;
                    if (cnt >= maxShifts) continue;

                    // Fatigue bonus: more gap = better
                    double fatigueBonus = 0;
                    List<LocalDate> lastDates = staffLastWork.get(s.getId());
                    if (lastDates != null && !lastDates.isEmpty()) {
                        LocalDate last = lastDates.get(lastDates.size() - 1);
                        long gap = date.toEpochDay() - last.toEpochDay();
                        if (gap >= 1) fatigueBonus = Math.min(gap * 10, 30.0);
                    }

                    double score = 100 - cnt * 10 + fatigueBonus;
                    candidates.add(new ScoredStaff(s.getId(), score));
                }

                candidates.sort((a, b) -> Double.compare(b.score, a.score));
                int assign = Math.min(required, candidates.size());
                for (int i = 0; i < assign; i++) {
                    int sid = candidates.get(i).staffId;
                    assigned.add(sid + "|" + date);
                    staffCount.merge(sid, 1, Integer::sum);
                    staffLastWork.computeIfAbsent(sid, k -> new ArrayList<>()).add(date);

                    Schedule sch = new Schedule();
                    sch.setStaff(staffMap.get(sid));
                    sch.setPeriod(period);
                    sch.setWorkDate(date);
                    sch.setShiftType(req.getShiftType());
                    sch.setHasConflict(false);
                    result.add(sch);
                }
            }
        }

        log.info("EnhancedGreedy: {} schedules in {}ms", result.size(), System.currentTimeMillis() - start);
        return result;
    }

    private record ScoredStaff(int staffId, double score) {}
}
