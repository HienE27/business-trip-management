package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConflictDetectionService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleConflictRepository scheduleConflictRepository;

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        List<String> conflicts = new ArrayList<>();

        detectLeaveConflict(staffId, workDate).ifPresent(conflicts::add);
        detectCompensationConflict(staffId, workDate).ifPresent(conflicts::add);
        detectShiftTypeConflict(staffId, workDate, shiftTypeId, excludeScheduleId).ifPresent(conflicts::add);

        return conflicts;
    }

    public boolean hasAnyConflict(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        return !detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId).isEmpty();
    }

    public void validateAndThrow(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        List<String> conflicts = detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId);
        if (!conflicts.isEmpty()) {
            throw new ConflictException("Phát hiện xung đột: " + String.join("; ", conflicts));
        }
    }

    public List<String> detectAllConflictsForPeriod(Integer periodId) {
        List<String> allConflicts = new ArrayList<>();
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        for (Schedule schedule : schedules) {
            Staff staff = schedule.getStaff();
            LocalDate workDate = schedule.getWorkDate();
            String shiftTypeId = schedule.getShiftType().getId();

            List<String> conflicts = detectAllConflicts(staff.getId(), workDate, shiftTypeId, schedule.getId());
            if (!conflicts.isEmpty()) {
                allConflicts.add("Staff " + staff.getFullName() + " ngày " + workDate + " (" + shiftTypeId + "): " + String.join("; ", conflicts));
            }
        }

        return allConflicts;
    }

    public void saveConflict(Schedule schedule, ScheduleConflict.ConflictType conflictType, String description) {
        ScheduleConflict conflict = ScheduleConflict.builder()
                .schedule(schedule)
                .conflictType(conflictType)
                .description(description)
                .isResolved(false)
                .build();
        scheduleConflictRepository.save(conflict);
    }

    public List<ScheduleConflict> getUnresolvedConflictsByPeriod(Integer periodId) {
        return scheduleConflictRepository.findUnresolvedByPeriodId(periodId);
    }

    public List<ScheduleConflict> getConflictsBySchedule(Integer scheduleId) {
        return scheduleConflictRepository.findByScheduleIdAndIsResolvedFalse(scheduleId);
    }

    @Transactional
    public void resolveConflict(Integer conflictId, Staff resolvedBy) {
        scheduleConflictRepository.findById(conflictId).ifPresent(conflict -> {
            conflict.setIsResolved(true);
            conflict.setResolvedBy(resolvedBy);
            conflict.setResolvedAt(java.time.LocalDateTime.now());
            scheduleConflictRepository.save(conflict);
        });
    }

    private java.util.Optional<String> detectLeaveConflict(Integer staffId, LocalDate workDate) {
        List<LeaveRequest> leaves = leaveRequestRepository.findByStaffIdAndDateRange(staffId, workDate, workDate);
        boolean hasApprovedLeave = leaves.stream()
                .anyMatch(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED);
        if (hasApprovedLeave) {
            return java.util.Optional.of("Nhân sự có ngày nghỉ phép được duyệt trong ngày này");
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<String> detectCompensationConflict(Integer staffId, LocalDate workDate) {
        return compensationDayRepository.findByStaffIdAndCompensationDate(staffId, workDate)
                .map(cd -> "Ngày này là ngày nghỉ bù của nhân sự");
    }

    private java.util.Optional<String> detectShiftTypeConflict(Integer staffId, LocalDate workDate,
                                                                String shiftTypeId, Integer excludeScheduleId) {
        List<Schedule> existingSchedules = scheduleRepository.findByStaffIdAndWorkDate(staffId, workDate);

        for (Schedule s : existingSchedules) {
            if (excludeScheduleId != null && s.getId().equals(excludeScheduleId)) {
                continue;
            }
            String existingTypeId = s.getShiftType().getId();

            if ("L01".equals(shiftTypeId) && "L02".equals(existingTypeId)) {
                return java.util.Optional.of("Trùng với lịch thông tầm (L02) trong ngày này");
            }
            if ("L02".equals(shiftTypeId) && "L01".equals(existingTypeId)) {
                return java.util.Optional.of("Trùng với lịch trực 24/24 (L01) trong ngày này");
            }
            if ("L03".equals(shiftTypeId) && "L04".equals(existingTypeId)) {
                return java.util.Optional.of("Trùng với lịch phòng khám chuyên gia (L04) trong ngày này");
            }
            if ("L04".equals(shiftTypeId) && "L03".equals(existingTypeId)) {
                return java.util.Optional.of("Trùng với lịch phòng khám dịch vụ (L03) trong ngày này");
            }
        }
        return java.util.Optional.empty();
    }

    public List<Staff> findReplacements(Integer periodId, LocalDate workDate, String shiftTypeId,
                                         Integer originalStaffId, Integer requiredCount) {
        List<Staff> replacements = new ArrayList<>();
        List<Staff> allStaff = scheduleRepository.findByPeriodId(periodId).stream()
                .map(Schedule::getStaff)
                .filter(s -> !s.getId().equals(originalStaffId))
                .distinct()
                .toList();

        for (Staff staff : allStaff) {
            if (!hasAnyConflict(staff.getId(), workDate, shiftTypeId, null)) {
                replacements.add(staff);
                if (replacements.size() >= requiredCount) {
                    break;
                }
            }
        }

        return replacements;
    }
}
