package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConflictDetectionService {

    public static final String SHIFT_TYPE_L01 = "L01";
    public static final String SHIFT_TYPE_L02 = "L02";
    public static final String SHIFT_TYPE_L03 = "L03";
    public static final String SHIFT_TYPE_L04 = "L04";

    private final LeaveRequestRepository leaveRequestRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleConflictRepository scheduleConflictRepository;
    private final StaffRepository staffRepository;
    private final ShiftRequirementRepository shiftRequirementRepository;

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        List<String> conflicts = new ArrayList<>();

        detectLeaveConflict(staffId, workDate).ifPresent(conflicts::add);
        detectCompensationConflict(staffId, workDate).ifPresent(conflicts::add);
        detectShiftTypeConflict(staffId, workDate, shiftTypeId, excludeScheduleId).ifPresent(conflicts::add);

        return conflicts;
    }

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId,
                                           Integer excludeScheduleId, Integer periodId) {
        List<String> conflicts = detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId);
        detectMaxShiftsConflict(staffId, periodId, excludeScheduleId).ifPresent(conflicts::add);
        return conflicts;
    }

    public boolean hasAnyConflict(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        return !detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId).isEmpty();
    }

    public void validateAndThrow(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        validateAndThrow(staffId, workDate, shiftTypeId, excludeScheduleId, null);
    }

    public void validateAndThrow(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, Integer periodId) {
        List<String> conflicts = detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, periodId);
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

    public ConflictCheckResponse checkPeriodConflicts(Integer periodId) {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);
        List<ConflictCheckResponse.ConflictDetail> conflictDetails = new ArrayList<>();

        for (Schedule schedule : schedules) {
            Staff staff = schedule.getStaff();
            LocalDate workDate = schedule.getWorkDate();
            String shiftTypeId = schedule.getShiftType().getId();

            List<String> conflicts = detectAllConflicts(staff.getId(), workDate, shiftTypeId, schedule.getId());
            if (!conflicts.isEmpty()) {
                schedule.setHasConflict(true);
                scheduleRepository.save(schedule);

                conflictDetails.add(ConflictCheckResponse.ConflictDetail.builder()
                        .scheduleId(schedule.getId())
                        .staffName(staff.getFullName())
                        .workDate(workDate)
                        .shiftTypeId(shiftTypeId)
                        .shiftTypeName(schedule.getShiftType().getName())
                        .conflictReasons(conflicts)
                        .build());
            }
        }

        List<String> coverageGaps = detectCoverageGaps(periodId);

        return ConflictCheckResponse.builder()
                .periodId(periodId)
                .hasConflicts(!conflictDetails.isEmpty())
                .totalConflicts(conflictDetails.size())
                .conflicts(conflictDetails)
                .coverageGaps(coverageGaps)
                .hasCoverageGaps(!coverageGaps.isEmpty())
                .totalCoverageGaps(coverageGaps.size())
                .build();
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

            if (SHIFT_TYPE_L01.equals(shiftTypeId) && SHIFT_TYPE_L02.equals(existingTypeId)) {
                return java.util.Optional.of("Trùng với lịch thông tầm (L02) trong ngày này");
            }
            if (SHIFT_TYPE_L02.equals(shiftTypeId) && SHIFT_TYPE_L01.equals(existingTypeId)) {
                return java.util.Optional.of("Trùng với lịch trực 24/24 (L01) trong ngày này");
            }
            if (SHIFT_TYPE_L03.equals(shiftTypeId) && SHIFT_TYPE_L04.equals(existingTypeId)) {
                return java.util.Optional.of("Trùng với lịch phòng khám chuyên gia (L04) trong ngày này");
            }
            if (SHIFT_TYPE_L04.equals(shiftTypeId) && SHIFT_TYPE_L03.equals(existingTypeId)) {
                return java.util.Optional.of("Trùng với lịch phòng khám dịch vụ (L03) trong ngày này");
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<String> detectMaxShiftsConflict(Integer staffId, Integer periodId, Integer excludeScheduleId) {
        if (periodId == null) {
            return java.util.Optional.empty();
        }

        return staffRepository.findById(staffId)
                .map(staff -> {
                    long currentCount;
                    if (excludeScheduleId != null) {
                        currentCount = scheduleRepository.countByStaffIdAndPeriodIdExcluding(staffId, periodId, excludeScheduleId);
                    } else {
                        currentCount = scheduleRepository.countByStaffIdAndPeriodId(staffId, periodId);
                    }
                    int maxShifts = staff.getMaxShiftsPerMonth() != null ? staff.getMaxShiftsPerMonth() : 5;

                    if (currentCount >= maxShifts) {
                        return java.util.Optional.of("Nhân sự đã đạt giới hạn " + maxShifts + " ngày trực/tháng");
                    }
                    return java.util.Optional.<String>empty();
                })
                .orElse(java.util.Optional.empty());
    }

    public List<Staff> findReplacements(Integer periodId, LocalDate workDate, String shiftTypeId,
                                         Integer originalStaffId, Integer requiredCount,
                                         Set<Integer> excludedStaffIds) {
        List<Staff> replacements = new ArrayList<>();
        List<Staff> allStaff = staffRepository.findByIsActiveTrue();

        for (Staff staff : allStaff) {
            if (originalStaffId != null && staff.getId().equals(originalStaffId)) {
                continue;
            }
            if (excludedStaffIds != null && excludedStaffIds.contains(staff.getId())) {
                continue;
            }
            if (!hasAnyConflict(staff.getId(), workDate, shiftTypeId, null)) {
                replacements.add(staff);
                if (replacements.size() >= requiredCount) {
                    break;
                }
            }
        }

        return replacements;
    }

    public List<String> detectCoverageGaps(Integer periodId) {
        List<String> gaps = new ArrayList<>();
        List<ShiftRequirement> requirements = shiftRequirementRepository.findByPeriodId(periodId);

        for (ShiftRequirement requirement : requirements) {
            LocalDate workDate = requirement.getWorkDate();
            String shiftTypeId = requirement.getShiftType().getId();
            int requiredCount = requirement.getRequiredStaffCount();

            long assignedCount = scheduleRepository.countByPeriodIdAndWorkDateAndShiftTypeId(
                    periodId, workDate, shiftTypeId);

            if (assignedCount < requiredCount) {
                gaps.add(String.format("Ngày %s, %s: cần %d nhân sự nhưng chỉ có %d",
                        workDate, shiftTypeId, requiredCount, assignedCount));
            }
        }

        return gaps;
    }
}
