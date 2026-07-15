package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Beam Search scheduler with fairness optimisation.
 *
 * Keeps K best partial schedules (the "beam") at each step, expands each by
 * assigning eligible staff, and prunes back to K using a combined score that
 * rewards both coverage (number of filled slots) AND fairness (low workload
 * variance among staff).
 *
 * Beam width configurable via runtimeConfig.getBeamWidth() (default 5).
 * Larger beam = better quality but slower.
 */
@Slf4j
@Component
public class BeamSearchScheduler {

    private static final int DEFAULT_BEAM_WIDTH = 5;
    /** Weight for coverage (vs fairness) in scoring — 0.7 = 70% coverage, 30% fairness. */
    private static final double COVERAGE_WEIGHT = 0.6;
    private static final double FAIRNESS_WEIGHT = 0.4;

    public List<Schedule> solve(
            List<Staff> activeStaff,
            List<ShiftRequirement> requirements,
            SchedulePeriod period,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            Set<Integer> excludedStaffIds) {

        long start = System.currentTimeMillis();
        int beamWidth = runtimeConfig != null && runtimeConfig.getBeamWidth() > 0
                ? runtimeConfig.getBeamWidth() : DEFAULT_BEAM_WIDTH;

        // Group requirements by date
        Map<LocalDate, List<ShiftRequirement>> byDate = requirements.stream()
                .collect(Collectors.groupingBy(ShiftRequirement::getWorkDate,
                        TreeMap::new, Collectors.toList()));

        Map<Integer, Staff> staffMap = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        // Staff max shifts
        int maxShiftsPerStaff = runtimeConfig != null && runtimeConfig.getMaxShiftsPerStaff() > 0
                ? runtimeConfig.getMaxShiftsPerStaff() : Integer.MAX_VALUE;

        // Initialize beam with empty schedule
        List<Map<String, String>> beam = new ArrayList<>();
        beam.add(new HashMap<>());

        int totalRequirements = requirements.size();

        for (Map.Entry<LocalDate, List<ShiftRequirement>> dateEntry : byDate.entrySet()) {
            LocalDate date = dateEntry.getKey();
            List<ShiftRequirement> dayReqs = dateEntry.getValue();

            dayReqs.sort(Comparator.comparingInt(r -> {
                String id = r.getShiftType().getId();
                return "L01".equals(id) ? 0 : "L02".equals(id) ? 1
                     : "L03".equals(id) ? 2 : "L04".equals(id) ? 3 : 4;
            }));

            for (ShiftRequirement req : dayReqs) {
                String shiftTypeId = req.getShiftType().getId();
                int required = req.getRequiredStaffCount();
                Integer requiredSpecialtyId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;

                List<ScoredBeam> candidates = new ArrayList<>();

                for (Map<String, String> partial : beam) {
                    // Count assignments per staff
                    Map<Integer, Integer> staffCount = new HashMap<>();
                    for (String key : partial.keySet()) {
                        staffCount.merge(Integer.parseInt(key.split("\\|")[0]), 1, Integer::sum);
                    }

                    List<Integer> eligible = findEligible(activeStaff, partial,
                            date, shiftTypeId, requiredSpecialtyId, staffCount,
                            excludedStaffIds, maxShiftsPerStaff);

                    for (int i = 0; i < Math.min(required, eligible.size()); i++) {
                        int sid = eligible.get(i);
                        Map<String, String> np = new HashMap<>(partial);
                        np.put(sid + "|" + date, shiftTypeId);

                        // Score: coverage + fairness
                        Map<Integer, Integer> newCount = new HashMap<>(staffCount);
                        newCount.merge(sid, 1, Integer::sum);
                        double score = scoreBeam(np, newCount, totalRequirements, activeStaff.size());

                        candidates.add(new ScoredBeam(np, score));
                    }
                }

                // Prune to top K by score
                if (!candidates.isEmpty()) {
                    candidates.sort((a, b) -> Double.compare(b.score, a.score));
                    beam = candidates.subList(0, Math.min(beamWidth, candidates.size()))
                            .stream().map(sb -> sb.assignments).collect(Collectors.toList());
                }
            }
        }

        // Best result
        Map<String, String> best = beam.isEmpty() ? new HashMap<>() : beam.get(0);

        // Convert to Schedule entities
        List<Schedule> result = new ArrayList<>();
        for (Map.Entry<String, String> e : best.entrySet()) {
            String[] p = e.getKey().split("\\|");
            Staff s = staffMap.get(Integer.parseInt(p[0]));
            if (s == null) continue;
            Schedule sch = new Schedule();
            sch.setStaff(s);
            sch.setPeriod(period);
            sch.setWorkDate(LocalDate.parse(p[1]));
            sch.setShiftType(findShiftType(e.getValue(), requirements));
            sch.setHasConflict(false);
            result.add(sch);
        }

        log.info("BeamSearch: {} schedules in {}ms (beam={})",
                result.size(), System.currentTimeMillis() - start, beamWidth);
        return result;
    }

    /**
     * Score a partial schedule: higher is better.
     * Combines coverage (% of requirements filled) and fairness (inverse of CV).
     */
    private double scoreBeam(Map<String, String> assignments,
                             Map<Integer, Integer> staffCount,
                             int totalRequired, int numStaff) {
        double coverage = totalRequired > 0 ? (double) assignments.size() / totalRequired : 0;

        // Fairness: lower CV (coefficient of variation) = more fair
        double fairness = 0;
        if (numStaff > 0 && !staffCount.isEmpty()) {
            double mean = staffCount.values().stream().mapToInt(Integer::intValue).average().orElse(0);
            if (mean > 0) {
                double variance = staffCount.values().stream()
                        .mapToDouble(c -> Math.pow(c - mean, 2))
                        .sum() / numStaff;
                double cv = Math.sqrt(variance) / mean;
                fairness = Math.max(0, 1 - cv); // 1 = perfectly fair, 0 = unfair
            } else {
                fairness = 1; // All zero — perfectly fair (empty)
            }
        }

        return COVERAGE_WEIGHT * coverage + FAIRNESS_WEIGHT * fairness;
    }

    private List<Integer> findEligible(
            List<Staff> staffList, Map<String, String> current,
            LocalDate date, String shiftTypeId, Integer requiredSpecialtyId,
            Map<Integer, Integer> staffCount, Set<Integer> excludedIds,
            int maxShiftsPerStaff) {

        return staffList.stream()
                .filter(s -> excludedIds == null || !excludedIds.contains(s.getId()))
                .filter(s -> !current.containsKey(s.getId() + "|" + date))
                .filter(s -> staffCount.getOrDefault(s.getId(), 0) < maxShiftsPerStaff)
                .filter(s -> {
                    if (requiredSpecialtyId == null) return true;
                    return s.getSpecialty() != null
                            && s.getSpecialty().getId().equals(requiredSpecialtyId);
                })
                .sorted(Comparator.comparingInt(s -> staffCount.getOrDefault(s.getId(), 0)))
                .map(Staff::getId)
                .collect(Collectors.toList());
    }

    private com.hospital.scheduler.entity.ShiftType findShiftType(
            String id, List<ShiftRequirement> reqs) {
        return reqs.stream().filter(r -> r.getShiftType().getId().equals(id))
                .findFirst().map(ShiftRequirement::getShiftType).orElse(null);
    }

    /** A beam entry with its score. */
    private static class ScoredBeam {
        final Map<String, String> assignments;
        final double score;
        ScoredBeam(Map<String, String> a, double s) { assignments = a; score = s; }
    }
}
