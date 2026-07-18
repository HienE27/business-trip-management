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
 * Beam Search scheduler with fairness + shift-type rotation.
 *
 * Keeps K best partial schedules, scoring by coverage, workload fairness,
 * AND shift-type variety (each staff should rotate through L01-L04).
 * Beam width configurable via runtimeConfig.getBeamWidth() (default 5).
 */
@Slf4j
@Component
public class BeamSearchScheduler {

    private static final int DEFAULT_BEAM_WIDTH = 15; // Tăng từ 10→15
    private static final double COVERAGE_WEIGHT = 0.30;
    private static final double FAIRNESS_WEIGHT = 0.20;
    private static final double VARIETY_WEIGHT = 0.20;
    private static final double BALANCE_WEIGHT = 0.30; // Tăng: ưu tiên balance hơn
    private static final String[] SHIFT_TYPES = {"L01", "L02", "L03", "L04"};

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
        int beamWidth = runtimeConfig != null && runtimeConfig.getBeamWidth() > 0
                ? runtimeConfig.getBeamWidth() : DEFAULT_BEAM_WIDTH;
        int maxShifts = runtimeConfig != null && runtimeConfig.getMaxShiftsPerStaff() > 0
                ? runtimeConfig.getMaxShiftsPerStaff() : Integer.MAX_VALUE;

        // Group requirements by date
        Map<LocalDate, List<ShiftRequirement>> byDate = requirements.stream()
                .collect(Collectors.groupingBy(ShiftRequirement::getWorkDate,
                        TreeMap::new, Collectors.toList()));

        Map<Integer, Staff> staffMap = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));

        // Load database pre-existing L01 schedules and compensation days
        LocalDate periodStart = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();
        
        List<CompensationDay> compDays = compensationDayRepository.findInRange(periodStart, periodEnd);
        List<Schedule> l01Schedules = scheduleRepository.findL01SchedulesInRange(periodStart.minusDays(7), periodStart.minusDays(1));
        
        Map<Integer, Set<LocalDate>> initialBlocked = new HashMap<>();
        for (CompensationDay cd : compDays) {
            initialBlocked.computeIfAbsent(cd.getStaff().getId(), k -> new HashSet<>())
                    .add(cd.getCompensationDate());
        }
        for (Schedule s : l01Schedules) {
            int staffId = s.getStaff().getId();
            LocalDate workDate = s.getWorkDate();
            initialBlocked.computeIfAbsent(staffId, k -> new HashSet<>()).add(workDate.plusDays(1));
            LocalDate compDate = compensationDateCalculator.calculate(workDate);
            if (compDate != null) {
                initialBlocked.computeIfAbsent(staffId, k -> new HashSet<>()).add(compDate);
            }
        }

        // Initialize beam
        List<PartialState> beam = new ArrayList<>();
        beam.add(new PartialState(new HashMap<>(), new HashMap<>(), initialBlocked));

        int totalReqs = requirements.size();

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
                Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;

                List<ScoredEntry> candidates = new ArrayList<>();

                for (PartialState state : beam) {
                    Map<Integer, Integer> count = new HashMap<>();
                    Map<Integer, Set<String>> typeMap = new HashMap<>();
                    for (String key : state.assignments.keySet()) {
                        String[] p = key.split("\\|");
                        int sid = Integer.parseInt(p[0]);
                        String st = state.assignments.get(key);
                        count.merge(sid, 1, Integer::sum);
                        typeMap.computeIfAbsent(sid, k -> new HashSet<>()).add(st);
                    }

                    List<Integer> eligible = findEligible(activeStaff, state.assignments, state.blockedDates,
                            date, shiftTypeId, specId, count, excludedStaffIds, maxShifts);

                    for (int sid : eligible) {
                        // Copy & assign
                        Map<String, String> newAssign = new HashMap<>(state.assignments);
                        Map<Integer, Set<String>> newTypes = new HashMap<>();
                        for (var e : typeMap.entrySet()) {
                            newTypes.put(e.getKey(), new HashSet<>(e.getValue()));
                        }
                        newAssign.put(sid + "|" + date, shiftTypeId);
                        newTypes.computeIfAbsent(sid, k -> new HashSet<>()).add(shiftTypeId);

                        // Copy & propagate blocked dates
                        Map<Integer, Set<LocalDate>> newBlocked = new HashMap<>();
                        for (var e : state.blockedDates.entrySet()) {
                            newBlocked.put(e.getKey(), new HashSet<>(e.getValue()));
                        }
                        if ("L01".equals(shiftTypeId)) {
                            newBlocked.computeIfAbsent(sid, k -> new HashSet<>()).add(date.plusDays(1));
                            LocalDate compDate = compensationDateCalculator.calculate(date);
                            if (compDate != null) {
                                newBlocked.computeIfAbsent(sid, k -> new HashSet<>()).add(compDate);
                            }
                        }

                        // Score
                        double score = scoreState(newAssign, newTypes, count, totalReqs, activeStaff.size());
                        candidates.add(new ScoredEntry(newAssign, newTypes, newBlocked, score));
                    }
                }

                if (!candidates.isEmpty()) {
                    candidates.sort((a, b) -> Double.compare(b.score, a.score));
                    beam = candidates.subList(0, Math.min(beamWidth, candidates.size()))
                            .stream().map(e -> new PartialState(e.assignments, e.types, e.blocked))
                            .collect(Collectors.toList());
                }
            }
        }

        PartialState best = beam.isEmpty() ? new PartialState(new HashMap<>(), new HashMap<>(), initialBlocked) : beam.get(0);

        // Convert to Schedule entities
        List<Schedule> result = new ArrayList<>();
        for (Map.Entry<String, String> e : best.assignments.entrySet()) {
            String[] p = e.getKey().split("\\|");
            Staff s = staffMap.get(Integer.parseInt(p[0]));
            if (s == null) continue;
            LocalDate workDate = LocalDate.parse(p[1]);
            String shiftTypeId = e.getValue();
            // Find matching requirement (by shiftType + date + specialty if applicable)
            ShiftRequirement matchedReq = findMatchingRequirement(s, workDate, shiftTypeId, requirements);
            Schedule sch = new Schedule();
            sch.setStaff(s);
            sch.setPeriod(period);
            sch.setWorkDate(workDate);
            sch.setShiftType(findShiftType(shiftTypeId, requirements));
            sch.setRequirement(matchedReq);
            sch.setHasConflict(false);
            result.add(sch);
        }

        // Post-processing: resolve conflicts by swapping conflicting shifts
        resolveConflicts(result, requirements, staffMap);

        log.info("BeamSearch: {} schedules in {}ms (beam={})",
                result.size(), System.currentTimeMillis() - start, beamWidth);
        return result;
    }

    private boolean isConflictPair(String t1, String t2) {
        if ("L01".equals(t1) && !"L01".equals(t2)) return true;
        if ("L01".equals(t2) && !"L01".equals(t1)) return true;
        if (("L03".equals(t1) && "L04".equals(t2)) || ("L04".equals(t1) && "L03".equals(t2))) return true;
        return false;
    }

    /** Resolve L01 vs all and L03+L04 conflicts by swapping one shift to a non-conflicting type */
    private void resolveConflicts(List<Schedule> result, List<ShiftRequirement> reqs,
                                  Map<Integer, Staff> staffMap) {
        // Group by staffId|date
        Map<String, List<Schedule>> byStaffDate = new HashMap<>();
        for (Schedule s : result) {
            String key = s.getStaff().getId() + "|" + s.getWorkDate();
            byStaffDate.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        for (var e : byStaffDate.entrySet()) {
            List<Schedule> daySchedules = e.getValue();
            if (daySchedules.size() <= 1) continue;

            boolean hasConflict = false;
            for (int i = 0; i < daySchedules.size(); i++) {
                for (int j = i + 1; j < daySchedules.size(); j++) {
                    if (isConflictPair(daySchedules.get(i).getShiftType().getId(), daySchedules.get(j).getShiftType().getId())) {
                        hasConflict = true;
                        break;
                    }
                }
            }

            if (hasConflict) {
                // Swap one conflicting shift to a non-conflicting type
                Schedule toSwap = null;
                String[] availableTypes = {"L01", "L02", "L03", "L04"};
                for (Schedule s : daySchedules) {
                    Set<String> currentTypes = new HashSet<>();
                    for (Schedule other : daySchedules) if (other != s) currentTypes.add(other.getShiftType().getId());
                    // Find a non-conflicting type
                    for (String t : availableTypes) {
                        if (currentTypes.contains(t)) continue; // would create same-type conflict
                        if (isConflictPair(s.getShiftType().getId(), t)) continue;
                        // Found a valid swap
                        toSwap = s;
                        break;
                    }
                }
                if (toSwap != null) {
                    // Find the new type
                    Set<String> currentTypes = new HashSet<>();
                    for (Schedule s : daySchedules) currentTypes.add(s.getShiftType().getId());
                    String newType = null;
                    for (String t : availableTypes) {
                        if (currentTypes.contains(t)) continue;
                        if (isConflictPair(toSwap.getShiftType().getId(), t)) continue;
                        newType = t;
                        break;
                    }
                    if (newType != null) {
                        toSwap.setShiftType(findShiftType(newType, reqs));
                    }
                }
            }
        }
    }

    private double scoreState(Map<String, String> assignments,
                               Map<Integer, Set<String>> staffTypes,
                               Map<Integer, Integer> staffCount,
                               int totalRequired, int numStaff) {
        double coverage = totalRequired > 0 ? (double) assignments.size() / totalRequired : 0;

        // Fairness (tổng)
        double fairness = 0;
        if (numStaff > 0 && !staffCount.isEmpty()) {
            double mean = staffCount.values().stream().mapToInt(Integer::intValue).average().orElse(0);
            if (mean > 0) {
                double var = staffCount.values().stream()
                        .mapToDouble(c -> Math.pow(c - mean, 2)).sum() / numStaff;
                fairness = Math.max(0, 1 - Math.sqrt(var) / mean);
            } else { fairness = 1; }
        }

        // Variety
        double variety = 0;
        int totalVariety = 0;
        for (Set<String> types : staffTypes.values()) {
            totalVariety += types.size();
        }
        if (!staffTypes.isEmpty()) {
            double avgTypes = (double) totalVariety / staffTypes.size();
            variety = Math.min(1, avgTypes / SHIFT_TYPES.length);
        }

        // Per-type balance: tính CV cho từng loại shift
        double balanceScore = 0;
        if (!staffTypes.isEmpty()) {
            double totalCv = 0;
            int typesWithData = 0;
            for (String type : SHIFT_TYPES) {
                String t = type;
                Map<Integer, Integer> perStaff = new HashMap<>();
                for (var e : assignments.entrySet()) {
                    if (e.getValue().equals(t)) {
                        int sid = Integer.parseInt(e.getKey().split("\\|")[0]);
                        perStaff.merge(sid, 1, Integer::sum);
                    }
                }
                if (!perStaff.isEmpty()) {
                    int poolSize = Math.max(staffTypes.size(), 1);
                    long total = perStaff.values().stream().mapToInt(Integer::intValue).sum();
                    double m = (double) total / poolSize;
                    if (m > 0) {
                        double sumSq = perStaff.values().stream()
                                .mapToDouble(c -> Math.pow(c - m, 2)).sum();
                        sumSq += (poolSize - perStaff.size()) * m * m;
                        double stdDev = Math.sqrt(sumSq / poolSize);
                        double cv = stdDev / m;
                        totalCv += cv;
                        typesWithData++;
                    }
                }
            }
            if (typesWithData > 0) {
                double avgCv = totalCv / typesWithData;
                balanceScore = Math.max(0, 1 - avgCv * 2); // CV=0 → 1, CV=0.5 → 0
            } else {
                balanceScore = 1;
            }
        } else {
            balanceScore = 1;
        }

        // Conflict penalty
        int conflicts = countConflicts(assignments);
        double conflictPenalty = Math.min(1.0, conflicts * 0.1);

        return COVERAGE_WEIGHT * coverage + FAIRNESS_WEIGHT * fairness 
             + VARIETY_WEIGHT * variety + BALANCE_WEIGHT * balanceScore - conflictPenalty;
    }

    /** Count L01 vs all and L03+L04 conflicts in the assignment */
    private int countConflicts(Map<String, String> assignments) {
        int conflicts = 0;
        Map<String, String> byStaffDay = new HashMap<>();
        for (var e : assignments.entrySet()) {
            String[] p = e.getKey().split("\\|");
            int staffId = Integer.parseInt(p[0]);
            LocalDate date = LocalDate.parse(p[1]);
            String shiftType = e.getValue();
            String key = staffId + "|" + date;
            String existing = byStaffDay.get(key);
            if (existing != null) {
                if (isConflictPair(shiftType, existing)) {
                    conflicts++;
                }
            }
            byStaffDay.put(key, shiftType);
        }
        return conflicts;
    }

    private List<Integer> findEligible(
            List<Staff> staffList, Map<String, String> current,
            Map<Integer, Set<LocalDate>> blockedDates,
            LocalDate date, String shiftTypeId, Integer specId,
            Map<Integer, Integer> staffCount, Set<Integer> excludedIds,
            int maxShifts) {

        // Prefer: least total count first, then least of THIS shift type, then least variety
        Map<Integer, Set<String>> currentTypes = new HashMap<>();
        for (Map.Entry<String, String> e : current.entrySet()) {
            int sid = Integer.parseInt(e.getKey().split("\\|")[0]);
            currentTypes.computeIfAbsent(sid, k -> new HashSet<>()).add(e.getValue());
        }

        return staffList.stream()
                .filter(s -> excludedIds == null || !excludedIds.contains(s.getId()))
                .filter(s -> !current.containsKey(s.getId() + "|" + date))
                .filter(s -> staffCount.getOrDefault(s.getId(), 0) < maxShifts)
                .filter(s -> {
                    Set<LocalDate> blocked = blockedDates.get(s.getId());
                    return blocked == null || !blocked.contains(date);
                })
                .filter(s -> specId == null || (s.getSpecialty() != null && s.getSpecialty().getId().equals(specId)))
                .filter(s -> !hasConflictWithExisting(s.getId(), date, shiftTypeId, current))
                .sorted(Comparator
                        .comparingInt((Staff s) -> staffCount.getOrDefault(s.getId(), 0))
                        .thenComparingInt(s -> currentTypes.getOrDefault(s.getId(), Collections.emptySet()).contains(shiftTypeId) ? 1 : 0))
                .map(Staff::getId)
                .collect(Collectors.toList());
    }

    /** Kiểm tra ràng buộc: L01 vs all và L03+L04 cùng ngày = conflict */
    private boolean hasConflictWithExisting(int staffId, LocalDate date, String newType,
                                           Map<String, String> current) {
        String targetKey = staffId + "|" + date;
        String existingType = current.get(targetKey);
        if (existingType == null) return false;
        
        return isConflictPair(newType, existingType);
    }

    /**
     * Find the matching requirement for a staff assignment.
     * Matches by shiftType + date, and prefers the requirement whose specialty
     * matches the staff's specialty (for L04 specialty-bound assignments).
     */
    private ShiftRequirement findMatchingRequirement(Staff staff, LocalDate workDate,
            String shiftTypeId, List<ShiftRequirement> reqs) {
        List<ShiftRequirement> candidates = reqs.stream()
                .filter(r -> r.getShiftType().getId().equals(shiftTypeId)
                        && r.getWorkDate().equals(workDate))
                .toList();
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);
        // Prefer requirement whose specialty matches the staff's specialty
        if (staff.getSpecialty() != null) {
            for (ShiftRequirement r : candidates) {
                if (r.getSpecialty() != null && r.getSpecialty().getId().equals(staff.getSpecialty().getId())) {
                    return r;
                }
            }
        }
        // Fallback: first requirement (may have null specialty)
        return candidates.get(0);
    }

    private com.hospital.scheduler.entity.ShiftType findShiftType(String id, List<ShiftRequirement> reqs) {
        return reqs.stream().filter(r -> r.getShiftType().getId().equals(id))
                .findFirst().map(ShiftRequirement::getShiftType).orElse(null);
    }

    private record PartialState(Map<String, String> assignments, Map<Integer, Set<String>> staffTypes, Map<Integer, Set<LocalDate>> blockedDates) {}
    private record ScoredEntry(Map<String, String> assignments, Map<Integer, Set<String>> types, Map<Integer, Set<LocalDate>> blocked, double score) {}
}
