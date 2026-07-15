package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.AlgorithmMetricsDTO;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * Spring @Service that holds reporting/analytics methods extracted from
 * AutoSchedulingService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSchedulingReportingService {

    private final ScheduleRepository scheduleRepository;
    private final StaffRepository staffRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final SchedulePeriodRepository periodRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final AlgorithmMetricsRepository metricsRepository;

    // ==================== M07-F06: Bao cao ngay chua phan cong ====================
    public Map<String, Object> getUnassignedDaysReport(Integer periodId) {
        SchedulePeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay ky lich voi ID: " + periodId));

        List<ShiftRequirement> requirements = requirementRepository.findByPeriodId(periodId);
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        Map<String, List<ShiftRequirement>> requirementsByDateAndShift = new LinkedHashMap<>();
        for (ShiftRequirement req : requirements) {
            String key = req.getWorkDate() + "_" + req.getShiftType().getId();
            requirementsByDateAndShift.computeIfAbsent(key, k -> new ArrayList<>()).add(req);
        }

        Map<String, List<Schedule>> schedulesByDateAndShift = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            String key = s.getWorkDate() + "_" + s.getShiftType().getId();
            schedulesByDateAndShift.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<Map<String, Object>> unassignedDays = new ArrayList<>();

        for (Map.Entry<String, List<ShiftRequirement>> entry : requirementsByDateAndShift.entrySet()) {
            String key = entry.getKey();
            List<ShiftRequirement> reqs = entry.getValue();
            List<Schedule> assigned = schedulesByDateAndShift.getOrDefault(key, Collections.emptyList());

            for (ShiftRequirement req : reqs) {
                int required = req.getRequiredStaffCount();
                int assignedCount = (int) assigned.stream()
                        .filter(s -> s.getShiftType().getId().equals(req.getShiftType().getId()))
                        .count();

                if (assignedCount < required) {
                    Map<String, Object> dayInfo = new LinkedHashMap<>();
                    dayInfo.put("workDate", req.getWorkDate());
                    dayInfo.put("dayOfWeek", DateUtils.getDayOfWeekVietnamese(req.getWorkDate().getDayOfWeek()));
                    dayInfo.put("shiftTypeId", req.getShiftType().getId());
                    dayInfo.put("shiftTypeName", req.getShiftType().getName());
                    dayInfo.put("specialty", req.getSpecialty() != null ? req.getSpecialty().getName() : null);
                    dayInfo.put("requiredStaffCount", required);
                    dayInfo.put("assignedStaffCount", assignedCount);
                    dayInfo.put("missingCount", required - assignedCount);
                    dayInfo.put("reason", buildUnassignedReason(req, assignedCount));
                    dayInfo.put("reasonCode", buildUnassignedReasonCode(req, assignedCount));
                    dayInfo.put("severity", buildUnassignedSeverity(required, assignedCount));
                    unassignedDays.add(dayInfo);
                }
            }
        }

        // Sort: 1. missingCount DESC (most understaffed first), 2. workDate ASC (earliest first)
        unassignedDays.sort(Comparator
                .comparing((Map<String, Object> m) -> -((Number) m.get("missingCount")).intValue())
                .thenComparing((Map<String, Object> m) -> (LocalDate) m.get("workDate")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodId", periodId);
        result.put("periodName", period.getPeriodName());
        result.put("startDate", period.getStartDate());
        result.put("endDate", period.getEndDate());
        result.put("totalUnassignedDays", unassignedDays.size());
        result.put("unassignedDays", unassignedDays);

        return result;
    }

    // ==================== M07-F08: De xuat nguoi thay the ====================
    public Map<String, Object> suggestReplacements(Integer scheduleId) {
        return suggestReplacements(scheduleId, null);
    }

    /**
     * Suggest replacement staff for a given schedule, optionally excluding a list of
     * staff IDs.
     */
    public Map<String, Object> suggestReplacements(Integer scheduleId, Set<Integer> excludedStaffIds) {
        Schedule original = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay lich voi ID: " + scheduleId));

        List<Staff> allStaff = staffRepository.findByIsActiveTrue();

        List<Map<String, Object>> suggestions = new ArrayList<>();

        for (Staff candidate : allStaff) {
            if (candidate.getId().equals(original.getStaff().getId())) continue;
            if (excludedStaffIds != null && excludedStaffIds.contains(candidate.getId())) continue;

            if (original.getStaff().getSpecialty() != null) {
                if (candidate.getSpecialty() == null ||
                        !candidate.getSpecialty().getId().equals(original.getStaff().getSpecialty().getId())) {
                    continue;
                }
            }

            // Skip the compensation-day check so staff on a day off can still be
            // surfaced as a replacement.
            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    candidate.getId(), original.getWorkDate(), original.getShiftType().getId(),
                    scheduleId, true);

            if (conflicts.isEmpty()) {
                long currentWorkload = scheduleRepository.countByStaffIdAndPeriodId(
                        candidate.getId(), original.getPeriod().getId());

                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("staffId", candidate.getId());
                suggestion.put("staffName", candidate.getFullName());
                suggestion.put("specialty", candidate.getSpecialty() != null ? candidate.getSpecialty().getName() : null);
                suggestion.put("currentWorkload", currentWorkload);
                suggestion.put("conflicts", conflicts);
                suggestion.put("isAvailable", true);
                suggestion.put("reason", "Khong co xung dot");
                suggestions.add(suggestion);
            } else {
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("staffId", candidate.getId());
                suggestion.put("staffName", candidate.getFullName());
                suggestion.put("specialty", candidate.getSpecialty() != null ? candidate.getSpecialty().getName() : null);
                suggestion.put("currentWorkload", scheduleRepository.countByStaffIdAndPeriodId(
                        candidate.getId(), original.getPeriod().getId()));
                suggestion.put("conflicts", conflicts);
                suggestion.put("isAvailable", false);
                suggestion.put("reason", String.join(", ", conflicts));
                suggestions.add(suggestion);
            }
        }

        suggestions.sort(Comparator.comparing(m -> !(Boolean) m.get("isAvailable")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalScheduleId", scheduleId);
        result.put("originalStaffId", original.getStaff().getId());
        result.put("originalStaffName", original.getStaff().getFullName());
        result.put("workDate", original.getWorkDate());
        result.put("shiftTypeId", original.getShiftType().getId());
        result.put("shiftTypeName", original.getShiftType().getName());
        result.put("totalCandidates", suggestions.size());
        result.put("availableCount", (int) suggestions.stream().filter(m -> (Boolean) m.get("isAvailable")).count());
        result.put("suggestions", suggestions);

        return result;
    }

    // ==================== M07-F09: Data bieu do can bang tai ====================
    public Map<String, Object> getWorkloadChartData(Integer periodId) {
        return getWorkloadChartData(periodId, null);
    }

    public Map<String, Object> getWorkloadChartData(Integer periodId, String shiftTypeId) {
        SchedulePeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Khong tim thay ky lich voi ID: " + periodId));

        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        // Filter schedules by shift type if specified (M04-F05 / M05-F05)
        if (shiftTypeId != null && !shiftTypeId.isBlank()) {
            schedules = schedules.stream()
                    .filter(s -> shiftTypeId.equals(s.getShiftType().getId()))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> staffWorkloadData = new ArrayList<>();

        for (Staff staff : activeStaff) {
            List<Schedule> staffSchedules = schedules.stream()
                    .filter(s -> s.getStaff().getId().equals(staff.getId()))
                    .collect(Collectors.toList());

            long L01Count = staffSchedules.stream().filter(s -> ConflictDetectionService.SHIFT_TYPE_L01.equals(s.getShiftType().getId())).count();
            long L02Count = staffSchedules.stream().filter(s -> ConflictDetectionService.SHIFT_TYPE_L02.equals(s.getShiftType().getId())).count();
            long L03Count = staffSchedules.stream().filter(s -> ConflictDetectionService.SHIFT_TYPE_L03.equals(s.getShiftType().getId())).count();
            long L04Count = staffSchedules.stream().filter(s -> ConflictDetectionService.SHIFT_TYPE_L04.equals(s.getShiftType().getId())).count();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("staffId", staff.getId());
            data.put("staffName", staff.getFullName());
            data.put("specialty", staff.getSpecialty() != null ? staff.getSpecialty().getName() : null);
            data.put("totalShifts", staffSchedules.size());
            data.put(ConflictDetectionService.SHIFT_TYPE_L01, L01Count);
            data.put(ConflictDetectionService.SHIFT_TYPE_L02, L02Count);
            data.put(ConflictDetectionService.SHIFT_TYPE_L03, L03Count);
            data.put(ConflictDetectionService.SHIFT_TYPE_L04, L04Count);
            double workloadPct;
            Integer maxShifts = staff.getMaxShiftsPerMonth();
            if (maxShifts != null && maxShifts > 0) {
                workloadPct = Math.round((double) staffSchedules.size() / maxShifts * 10000.0) / 100.0;
            } else if (!schedules.isEmpty()) {
                workloadPct = Math.round((double) staffSchedules.size() / schedules.size() * 10000.0) / 100.0;
            } else {
                workloadPct = 0.0;
            }
            data.put("workloadPercentage", workloadPct);

            staffWorkloadData.add(data);
        }

        if (shiftTypeId != null && !shiftTypeId.isBlank()) {
            staffWorkloadData = staffWorkloadData.stream()
                    .filter(m -> ((Number) m.get("totalShifts")).longValue() > 0)
                    .collect(Collectors.toList());
        }

        staffWorkloadData.sort((a, b) -> {
            int t1 = ((Number) a.get("totalShifts")).intValue();
            int t2 = ((Number) b.get("totalShifts")).intValue();
            return Integer.compare(t2, t1);
        });

        double avgWorkload = 0.0;
        if (!activeStaff.isEmpty()) {
            double totalUtil = staffWorkloadData.stream()
                    .mapToDouble(m -> ((Number) m.get("workloadPercentage")).doubleValue())
                    .sum();
            avgWorkload = Math.round(totalUtil / activeStaff.size() * 100.0) / 100.0;
        }

        long maxWorkload = (long) Math.round(staffWorkloadData.stream()
                .mapToDouble(m -> ((Number) m.get("workloadPercentage")).doubleValue())
                .max().orElse(0.0));
        long minWorkload = (long) Math.round(staffWorkloadData.stream()
                .mapToDouble(m -> ((Number) m.get("workloadPercentage")).doubleValue())
                .min().orElse(0.0));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodId", periodId);
        result.put("periodName", period.getPeriodName());
        result.put("startDate", period.getStartDate());
        result.put("endDate", period.getEndDate());
        result.put("totalSchedules", schedules.size());
        result.put("totalStaff", shiftTypeId != null && !shiftTypeId.isBlank()
                ? staffWorkloadData.size() : activeStaff.size());
        result.put("shiftTypeId", shiftTypeId);
        result.put("averageWorkload", avgWorkload);
        result.put("minWorkload", minWorkload);
        result.put("maxWorkload", maxWorkload);
        result.put("staffWorkloadData", staffWorkloadData);

        return result;
    }

    // ==================== BUILD METHODS ====================

    public List<Map<String, Object>> buildUnassignedDays(List<ShiftRequirement> requirements, List<Schedule> schedules) {
        Map<String, Long> assignedCount = schedules.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getWorkDate() + "_" + s.getShiftType().getId(),
                        Collectors.counting()));

        List<Map<String, Object>> unassigned = new ArrayList<>();
        for (ShiftRequirement req : requirements) {
            String key = req.getWorkDate() + "_" + req.getShiftType().getId();
            long assigned = assignedCount.getOrDefault(key, 0L);
            if (assigned < req.getRequiredStaffCount()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("workDate", req.getWorkDate());
                item.put("dayOfWeek", DateUtils.getDayOfWeekVietnamese(req.getWorkDate().getDayOfWeek()));
                item.put("shiftTypeId", req.getShiftType().getId());
                item.put("shiftTypeName", req.getShiftType().getName());
                item.put("specialty", req.getSpecialty() != null ? req.getSpecialty().getName() : null);
                item.put("requiredStaffCount", req.getRequiredStaffCount());
                item.put("assignedStaffCount", (int) assigned);
                item.put("missingCount", req.getRequiredStaffCount() - (int) assigned);
                item.put("reason", buildUnassignedReason(req, assigned));
                item.put("reasonCode", buildUnassignedReasonCode(req, assigned));
                item.put("severity", buildUnassignedSeverity(req.getRequiredStaffCount(), (int) assigned));
                unassigned.add(item);
            }
        }
        return unassigned;
    }

    public Map<String, AutoScheduleResponse.ShiftTypeBreakdown> buildByShiftTypeBreakdown(
            List<Schedule> schedules, List<ShiftRequirement> requirements) {

        Map<String, Map<String, Object>> typeStats = new LinkedHashMap<>();
        Set<String> shiftTypeIds = new HashSet<>();
        for (ShiftRequirement req : requirements) {
            String id = req.getShiftType().getId();
            shiftTypeIds.add(id);
            typeStats.computeIfAbsent(id, k -> {
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("shiftTypeId", id);
                stats.put("shiftTypeName", req.getShiftType().getName());
                stats.put("totalRequired", 0);
                stats.put("totalAssigned", 0);
                stats.put("unassignedDates", new ArrayList<String>());
                return stats;
            });
            Map<String, Object> stats = typeStats.get(id);
            stats.put("totalRequired", (int) stats.get("totalRequired") + req.getRequiredStaffCount());
        }

        Map<String, Long> assignedPerType = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getShiftType().getId(), Collectors.counting()));

        Map<String, Set<String>> unassignedDatesPerType = new HashMap<>();
        Map<String, Map<String, Long>> assignedCountByTypeAndDate = schedules.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getShiftType().getId(),
                        Collectors.groupingBy(s -> s.getWorkDate().toString(), Collectors.counting())));

        for (ShiftRequirement req : requirements) {
            String id = req.getShiftType().getId();
            String dateStr = req.getWorkDate().toString();
            long assigned = assignedCountByTypeAndDate
                    .getOrDefault(id, Collections.emptyMap())
                    .getOrDefault(dateStr, 0L);
            if (assigned < req.getRequiredStaffCount()) {
                unassignedDatesPerType
                        .computeIfAbsent(id, k -> new HashSet<>())
                        .add(dateStr);
            }
        }

        Map<String, AutoScheduleResponse.ShiftTypeBreakdown> result = new LinkedHashMap<>();
        for (String shiftTypeId : shiftTypeIds) {
            Map<String, Object> stats = typeStats.get(shiftTypeId);
            int totalRequired = (int) stats.get("totalRequired");
            int totalAssigned = assignedPerType.getOrDefault(shiftTypeId, 0L).intValue();
            double coverageRate = totalRequired > 0
                    ? Math.min(100.0, (double) totalAssigned / totalRequired * 100)
                    : 0.0;

            List<String> unassignedDates = new ArrayList<>(unassignedDatesPerType.getOrDefault(shiftTypeId, Collections.emptySet()));
            Collections.sort(unassignedDates);

            Set<Integer> distinctStaff = schedules.stream()
                    .filter(s -> s.getShiftType().getId().equals(shiftTypeId))
                    .map(s -> s.getStaff().getId())
                    .collect(Collectors.toSet());

            result.put(shiftTypeId, AutoScheduleResponse.ShiftTypeBreakdown.builder()
                    .shiftTypeId(shiftTypeId)
                    .shiftTypeName((String) stats.get("shiftTypeName"))
                    .totalAssigned(totalAssigned)
                    .totalRequired(totalRequired)
                    .coverageRate(Math.round(coverageRate * 100.0) / 100.0)
                    .unassignedDates(unassignedDates)
                    .distinctStaffAssigned(distinctStaff.size())
                    .build());
        }

        return result;
    }

    public List<String> buildWarnings(List<ShiftRequirement> requirements, List<Schedule> schedules) {
        List<String> warnings = new ArrayList<>();

        Map<String, Long> assignedCount = schedules.stream()
                .filter(s -> s != null && s.getWorkDate() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getWorkDate() + "_" + s.getShiftType().getId(),
                        Collectors.counting()));

        for (ShiftRequirement req : requirements) {
            String key = req.getWorkDate() + "_" + req.getShiftType().getId();
            long assigned = assignedCount.getOrDefault(key, 0L);
            if (assigned < req.getRequiredStaffCount()) {
                warnings.add(String.format("Ngay %s (%s), ca %s: thieu %d nhan su (co %d). %s",
                        req.getWorkDate(), DateUtils.getDayOfWeekVietnamese(req.getWorkDate().getDayOfWeek()),
                        req.getShiftType().getName(),
                        req.getRequiredStaffCount() - assigned, assigned,
                        buildUnassignedReason(req, assigned)));
            }
        }

        return warnings;
    }

    public String buildUnassignedReason(ShiftRequirement req, long assigned) {
        if (assigned == 0) {
            if (ConflictDetectionService.SHIFT_TYPE_L04.equals(req.getShiftType().getId()) && req.getSpecialty() != null) {
                return "Khong con nhan su hop le cho chuyen khoa " + req.getSpecialty().getName()
                        + " sau khi ap dung nghi phep, nghi bu va xung dot.";
            }
            return "Khong con nhan su hop le sau khi ap dung nghi phep, nghi bu va xung dot ca.";
        }
        return "Muc tieu phan bo tu cau hinh cao hon so nhan su hop le con lai; phan thieu can quan ly xu ly thu cong.";
    }

    public String buildUnassignedReasonCode(ShiftRequirement req, long assigned) {
        if (assigned == 0 && ConflictDetectionService.SHIFT_TYPE_L04.equals(req.getShiftType().getId())
                && req.getSpecialty() != null) {
            return "NO_SPECIALTY_STAFF";
        }
        if (assigned == 0) {
            return "NO_ELIGIBLE_STAFF";
        }
        return "PARTIAL_COVERAGE";
    }

    public String buildUnassignedSeverity(int required, int assigned) {
        if (assigned <= 0) return "critical";
        double missingRatio = (double) (required - assigned) / Math.max(1, required);
        return missingRatio >= 0.5 ? "warning" : "info";
    }

    public AlgorithmMetricsDTO metricsToDTO(AlgorithmMetrics m) {
        // This will be wired to AutoSchedulingMetricsService in the delegating method
        return null;
    }
}
