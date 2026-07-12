package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hospital.scheduler.algorithm.CspConstants.DIRECT_24H;
import static com.hospital.scheduler.algorithm.CspConstants.SHIFT_ORDER;
import static com.hospital.scheduler.algorithm.CspConstants.getShiftTypeName;

/**
 * Turns a raw search {@link CspSearchEngine.Result} into a domain-shaped
 * {@link SchedulingResult}: assignments, compensation days, fairness,
 * coverage, unassigned-days report and preview data.
 */
@Component
@RequiredArgsConstructor
class CspResultBuilder {

    private final CompensationDateCalculator compensationDateCalculator;

    SchedulingResult build(
            CspSearchEngine.Result solution,
            ProblemData data,
            List<Staff> staffList,
            List<LocalDate> dates,
            long startTime) {

        if (!solution.isValid()) {
            return SchedulingResult.builder()
                    .valid(false)
                    .errors(solution.getErrors())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        Map<String, String> assignments = new HashMap<>();
        Set<String> compensationDays = new java.util.HashSet<>();

        for (String key : solution.getAssignment().keySet()) {
            String[] parts = key.split("\\|");
            if (parts.length != 3) continue;
            int staffIdx = Integer.parseInt(parts[0]);
            int dayIdx = Integer.parseInt(parts[1]);
            int shiftIdx = Integer.parseInt(parts[2]);

            int staffId = staffList.get(staffIdx).getId();
            LocalDate workDate = dates.get(dayIdx);
            String shiftType = SHIFT_ORDER[shiftIdx];

            assignments.put(staffId + "|" + workDate, shiftType);

            if (shiftType.equals(DIRECT_24H)) {
                LocalDate compDate = compensationDateCalculator.calculate(workDate);
                compensationDays.add(staffId + "|" + compDate);
            }
        }

        Map<Integer, Integer> shiftCounts = countShiftsPerStaff(assignments);
        double fairness = fairnessScore(assignments, data, staffList);
        double coverage = data.numVars > 0 ? (double) assignments.size() / data.numVars * 100 : 100;

        List<Map<String, Object>> unassignedDays = buildUnassignedDaysReport(data, assignments, dates);
        SchedulingResult.PreviewData previewData = buildPreviewData(assignments, staffList, dates, shiftCounts, unassignedDays);

        return SchedulingResult.builder()
                .valid(true)
                .partial(solution.isPartial())
                .assignments(assignments)
                .compensationDays(compensationDays)
                .errors(Collections.emptyList())
                .fairnessScore(BigDecimal.valueOf(fairness).setScale(2, RoundingMode.HALF_UP))
                .fatigueScore(BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP))
                .coverageScore(BigDecimal.valueOf(coverage).setScale(2, RoundingMode.HALF_UP))
                .totalScore(BigDecimal.valueOf(fairness).setScale(2, RoundingMode.HALF_UP))
                .scheduleCount(assignments.size())
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .unassignedDays(unassignedDays)
                .previewData(previewData)
                .build();
    }

    private Map<Integer, Integer> countShiftsPerStaff(Map<String, String> assignments) {
        Map<Integer, Integer> shiftCounts = new HashMap<>();
        for (String key : assignments.keySet()) {
            int staffId = Integer.parseInt(key.split("\\|")[0]);
            shiftCounts.merge(staffId, 1, (a, b) -> (a == null ? 0 : a) + (b == null ? 0 : b));
        }
        return shiftCounts;
    }

    /**
     * Look up the specialty id for an assignment on a specific day with a
     * specific shift type. varSpecialty is indexed by variable id (one per
     * (day, shift) slot), so we scan for the first matching variable.
     * Returns {@code null} when no matching variable exists or the specialty
     * is unset (0).
     */
    private static Integer lookupSpecialtyForAssignment(ProblemData data, int dayIdx, String shiftType) {
        if (dayIdx < 0 || dayIdx >= data.numDays) return null;
        int targetShift = java.util.Arrays.asList(SHIFT_ORDER).indexOf(shiftType);
        if (targetShift < 0) return null;
        for (int v = 0; v < data.numVars; v++) {
            if (data.varDay[v] == dayIdx && data.varShift[v] == targetShift) {
                int spec = data.varSpecialty[v];
                return spec != 0 ? spec : null;
            }
        }
        return null;
    }

    /**
     * Fairness score: average of per-type balance scores (0-100 scale).
     * For each shift type, compute 1 - normalized_stddev.
     * For L04, compute per-specialty balance independently.
     * This is more meaningful than global CV because it penalizes
     * imbalanced distribution within each shift type.
     */
    private double fairnessScore(Map<String, String> assignments, ProblemData data, List<Staff> staffList) {
        if (assignments.isEmpty()) return 100;
        
        // Build per-type, per-staff counts (L04 uses per-specialty key)
        Map<String, Map<Integer, Integer>> typeStaffCounts = new HashMap<>();
        for (Map.Entry<String, String> e : assignments.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            if (parts.length != 2) continue;
            int staffId = Integer.parseInt(parts[0]);
            LocalDate workDate = LocalDate.parse(parts[1]);
            String shiftType = e.getValue();
            
            // Find specialty for L04 from ProblemData: varSpecialty is indexed
            // by VARIABLE id (one variable per (day, shift) slot), not by day.
            // Look up the matching variable via its key in the assignments map.
            String balanceKey = shiftType;
            if ("L04".equals(shiftType)) {
                int dayIdx = (int) java.time.temporal.ChronoUnit.DAYS.between(data.baseDate, workDate);
                Integer specId = lookupSpecialtyForAssignment(data, dayIdx, shiftType);
                if (specId != null) {
                    balanceKey = "L04:" + specId;
                }
            }
            
            typeStaffCounts.computeIfAbsent(balanceKey, k -> new HashMap<>())
                    .merge(staffId, 1, Integer::sum);
        }
        
        if (typeStaffCounts.isEmpty()) return 100;
        
        double totalScore = 0.0;
        int activeTypes = 0;
        int poolSize = staffList.size();
        
        for (Map.Entry<String, Map<Integer, Integer>> entry : typeStaffCounts.entrySet()) {
            Map<Integer, Integer> staffCounts = entry.getValue();
            double total = staffCounts.values().stream().mapToInt(Integer::intValue).sum();
            if (total == 0) continue;
            
            double mean = total / poolSize;
            double variance = 0;
            for (Staff s : staffList) {
                int count = staffCounts.getOrDefault(s.getId(), 0);
                variance += (count - mean) * (count - mean);
            }
            variance /= poolSize;
            double stdDev = Math.sqrt(variance);
            // Score: 1 - (stdDev / (mean * 2)), capped to [0, 1], then scale to 0-100
            double typeScore = Math.max(0.0, Math.min(1.0, 1.0 - (stdDev / (mean * 2)))) * 100;
            totalScore += typeScore;
            activeTypes++;
        }
        
        return activeTypes > 0 ? totalScore / activeTypes : 100;
    }
    
    /**
     * Backward-compatible fairness score: 100 - CV (coefficient of variation).
     * Used when full assignment data is not available.
     */
    private double fairnessScore(Map<Integer, Integer> shiftCounts) {
        if (shiftCounts.isEmpty()) return 100;
        double avg = shiftCounts.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        if (avg <= 0) return 100;
        double variance = shiftCounts.values().stream()
                .mapToDouble(c -> (c - avg) * (c - avg))
                .average().orElse(0);
        double stdDev = Math.sqrt(variance);
        double cv = (stdDev / avg) * 100;
        return Math.max(0, 100 - cv);
    }

    /**
     * M07-F06: list (date, shift) tuples where the assigned count is below
     * the required count, sorted by date.
     */
    private List<Map<String, Object>> buildUnassignedDaysReport(
            ProblemData data, Map<String, String> assignments, List<LocalDate> dates) {

        // Pre-compute assigned counts per (dayIdx, shiftIdx) for O(1) lookup
        Map<String, Integer> assignedCountMap = new HashMap<>();
        for (Map.Entry<String, String> e : assignments.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            if (parts.length != 2) continue;
            LocalDate assignDate = LocalDate.parse(parts[1]);
            int dayIdx = (int) java.time.temporal.ChronoUnit.DAYS.between(dates.get(0), assignDate);
            String shiftType = e.getValue();
            int shiftIdx = CspConstants.getShiftIdx(shiftType);
            if (shiftIdx < 0) continue;
            String key = dayIdx + "|" + shiftIdx;
            assignedCountMap.merge(key, 1, (a, b) -> (a == null ? 0 : a) + (b == null ? 0 : b));
        }

        List<Map<String, Object>> unassignedDays = new ArrayList<>();
        for (int d = 0; d < data.numDays; d++) {
            for (int s = 0; s < data.numShifts; s++) {
                int required = data.slotCount[d][s];
                if (required <= 0) continue;

                String key = d + "|" + s;
                int assigned = assignedCountMap.getOrDefault(key, 0);

                if (assigned < required) {
                    Map<String, Object> day = new HashMap<>();
                    day.put("date", dates.get(d));
                    day.put("shiftType", SHIFT_ORDER[s]);
                    day.put("shiftTypeName", getShiftTypeName(SHIFT_ORDER[s]));
                    day.put("required", required);
                    day.put("assigned", assigned);
                    day.put("shortfall", required - assigned);
                    day.put("dayOfWeek", dates.get(d).getDayOfWeek().toString());
                    unassignedDays.add(day);
                }
            }
        }
        return unassignedDays;
    }

    /**
     * M07-F07: structured payload for the confirmation preview screen.
     */
    private SchedulingResult.PreviewData buildPreviewData(
            Map<String, String> assignments,
            List<Staff> staffList,
            List<LocalDate> dates,
            Map<Integer, Integer> shiftCounts,
            List<Map<String, Object>> unassignedDays) {

        List<Map<String, Object>> assignmentList = new ArrayList<>();
        for (Map.Entry<String, String> e : assignments.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            if (parts.length != 2) continue;
            int staffId = Integer.parseInt(parts[0]);
            LocalDate workDate = LocalDate.parse(parts[1]);
            String shiftType = e.getValue();

            Map<String, Object> detail = new HashMap<>();
            detail.put("staffId", staffId);
            detail.put("staffName", getStaffName(staffList, staffId));
            detail.put("date", workDate);
            detail.put("shiftType", shiftType);
            detail.put("shiftTypeName", getShiftTypeName(shiftType));
            detail.put("dayOfWeek", workDate.getDayOfWeek().toString());
            assignmentList.add(detail);
        }

        List<Map<String, Object>> staffStats = new ArrayList<>();
        for (int staffId : shiftCounts.keySet()) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("staffId", staffId);
            stat.put("staffName", getStaffName(staffList, staffId));
            stat.put("totalShifts", shiftCounts.get(staffId));
            staffStats.add(stat);
        }
        staffStats.sort((a, b) -> ((Integer) b.get("totalShifts")).compareTo((Integer) a.get("totalShifts")));

        List<Map<String, Object>> dayStats = new ArrayList<>();
        for (LocalDate date : dates) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("date", date);
            stat.put("dayOfWeek", date.getDayOfWeek().toString());
            int count = 0;
            for (String key : assignments.keySet()) {
                if (key.endsWith("|" + date.toString())) count++;
            }
            stat.put("totalAssignments", count);
            dayStats.add(stat);
        }

        List<String> warnings = new ArrayList<>();
        if (!unassignedDays.isEmpty()) {
            warnings.add("Có " + unassignedDays.size() + " ca chưa đủ nhân sự. Cần xử lý thủ công.");
            for (Map<String, Object> day : unassignedDays) {
                warnings.add("- " + day.get("date") + " " + day.get("shiftTypeName")
                        + ": thiếu " + day.get("shortfall") + " người");
            }
        }

        return SchedulingResult.PreviewData.builder()
                .assignments(assignmentList)
                .staffStats(staffStats)
                .dayStats(dayStats)
                .warnings(warnings)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String getStaffName(List<Staff> staffList, int staffId) {
        return staffList.stream()
                .filter(s -> s.getId() == staffId)
                .map(Staff::getFullName)
                .findFirst()
                .orElse("Unknown");
    }
}
