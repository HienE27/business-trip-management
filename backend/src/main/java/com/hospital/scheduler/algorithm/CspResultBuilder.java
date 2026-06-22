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
        double fairness = fairnessScore(shiftCounts);
        double coverage = data.numVars > 0 ? (double) assignments.size() / data.numVars * 100 : 100;

        List<Map<String, Object>> unassignedDays = buildUnassignedDaysReport(data, assignments, dates);
        SchedulingResult.PreviewData previewData = buildPreviewData(assignments, staffList, dates, shiftCounts, unassignedDays);

        return SchedulingResult.builder()
                .valid(true)
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
            shiftCounts.merge(staffId, 1, Integer::sum);
        }
        return shiftCounts;
    }

    /**
     * Fairness heuristic: 100 - 10 × variance of per-staff shift counts.
     * Higher = more balanced. Capped at 0 (variance so high that score
     * would go negative).
     */
    private double fairnessScore(Map<Integer, Integer> shiftCounts) {
        if (shiftCounts.isEmpty()) return 100;
        double avg = shiftCounts.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        if (avg <= 0) return 100;
        double variance = shiftCounts.values().stream()
                .mapToDouble(c -> (c - avg) * (c - avg))
                .average().orElse(0);
        return Math.max(0, 100 - variance * 10);
    }

    /**
     * M07-F06: list (date, shift) tuples where the assigned count is below
     * the required count, sorted by date.
     */
    private List<Map<String, Object>> buildUnassignedDaysReport(
            ProblemData data, Map<String, String> assignments, List<LocalDate> dates) {

        List<Map<String, Object>> unassignedDays = new ArrayList<>();
        for (int d = 0; d < data.numDays; d++) {
            for (int s = 0; s < data.numShifts; s++) {
                int required = data.slotCount[d][s];
                if (required <= 0) continue;

                int assigned = 0;
                for (Map.Entry<String, String> e : assignments.entrySet()) {
                    String[] parts = e.getKey().split("\\|");
                    if (parts.length != 2) continue;
                    LocalDate assignDate = LocalDate.parse(parts[1]);
                    if (assignDate.equals(dates.get(d)) && e.getValue().equals(SHIFT_ORDER[s])) {
                        assigned++;
                    }
                }

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
