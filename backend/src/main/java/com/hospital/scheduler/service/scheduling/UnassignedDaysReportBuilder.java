package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.util.DateUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds unassigned days reports for a scheduling period.
 * Shows which requirements are not fully covered by assignments.
 */
@Service
public class UnassignedDaysReportBuilder {

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

    private String buildUnassignedReason(ShiftRequirement req, long assigned) {
        if (assigned == 0) {
            if ("L04".equals(req.getShiftType().getId()) && req.getSpecialty() != null) {
                return "Không còn nhân sự hợp lệ cho chuyên khoa " + req.getSpecialty().getName()
                        + " sau khi áp dụng nghỉ phép, nghỉ bù và xung đột.";
            }
            return "Không còn nhân sự hợp lệ sau khi áp dụng nghỉ phép, nghỉ bù và xung đột ca.";
        }
        return "Mục tiêu phân bổ từ cấu hình cao hơn số nhân sự hợp lệ còn lại; phần thiếu cần quản lý xử lý thủ công.";
    }

    private String buildUnassignedReasonCode(ShiftRequirement req, long assigned) {
        if (assigned == 0 && "L04".equals(req.getShiftType().getId()) && req.getSpecialty() != null) {
            return "NO_SPECIALTY_STAFF";
        }
        if (assigned == 0) {
            return "NO_ELIGIBLE_STAFF";
        }
        return "PARTIAL_COVERAGE";
    }

    private String buildUnassignedSeverity(int required, int assigned) {
        if (assigned <= 0) return "critical";
        double missingRatio = (double) (required - assigned) / Math.max(1, required);
        return missingRatio >= 0.5 ? "warning" : "info";
    }
}
