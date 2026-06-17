package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.dto.response.CoverageReportDTO;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.util.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final ShiftTypeRepository shiftTypeRepository;
    @Lazy
    private final EmailService emailService;

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        return detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, false, false);
    }

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, boolean skipCompensationDay) {
        return detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, skipCompensationDay, false);
    }

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, boolean skipCompensationDay, boolean skipShiftTypeConflict) {
        List<String> conflicts = new ArrayList<>();

        detectLeaveConflict(staffId, workDate).ifPresent(conflicts::add);
        if (!skipCompensationDay) {
            detectCompensationConflict(staffId, workDate).ifPresent(conflicts::add);
        }
        if (!skipShiftTypeConflict) {
            detectShiftTypeConflict(staffId, workDate, shiftTypeId, excludeScheduleId).ifPresent(conflicts::add);
        }

        return conflicts;
    }

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId,
                                           Integer excludeScheduleId, Integer periodId) {
        return detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, periodId, false, false);
    }

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId,
                                           Integer excludeScheduleId, Integer periodId, boolean skipCompensationDay) {
        return detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, periodId, skipCompensationDay, false);
    }

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId,
                                           Integer excludeScheduleId, Integer periodId, boolean skipCompensationDay, boolean skipShiftTypeConflict) {
        List<String> conflicts = detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, skipCompensationDay, skipShiftTypeConflict);
        detectMaxShiftsConflict(staffId, periodId, excludeScheduleId).ifPresent(conflicts::add);
        return conflicts;
    }

    /**
     * Check if staff has exceeded their max shifts per month for the given period.
     *
     * @param staffId   The staff member ID
     * @param periodId  The schedule period ID
     * @return true if the staff has reached or exceeded their maxShiftsPerMonth limit
     */
    public boolean hasExceededMaxShifts(Integer staffId, Integer periodId) {
        if (periodId == null) {
            return false;
        }

        return staffRepository.findById(staffId)
                .map(staff -> {
                    long currentCount = scheduleRepository.countByStaffIdAndPeriodId(staffId, periodId);
                    int maxShifts = staff.getMaxShiftsPerMonth() != null ? staff.getMaxShiftsPerMonth() : 5;
                    return currentCount >= maxShifts;
                })
                .orElse(false);
    }

    public boolean hasAnyConflict(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        return !detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId).isEmpty();
    }

    public boolean hasAnyConflict(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, boolean skipCompensationDay) {
        return hasAnyConflict(staffId, workDate, shiftTypeId, excludeScheduleId, skipCompensationDay, false);
    }

    public boolean hasAnyConflict(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, boolean skipCompensationDay, boolean skipShiftTypeConflict) {
        return !detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, skipCompensationDay, skipShiftTypeConflict).isEmpty();
    }

    public void validateAndThrow(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        validateAndThrow(staffId, workDate, shiftTypeId, excludeScheduleId, null, false, false);
    }

    public void validateAndThrow(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, Integer periodId) {
        validateAndThrow(staffId, workDate, shiftTypeId, excludeScheduleId, periodId, false, false);
    }

    public void validateAndThrow(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, Integer periodId, boolean skipCompensationDay) {
        validateAndThrow(staffId, workDate, shiftTypeId, excludeScheduleId, periodId, skipCompensationDay, false);
    }

    public void validateAndThrow(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, Integer periodId, boolean skipCompensationDay, boolean skipShiftTypeConflict) {
        List<String> conflicts = detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, periodId, skipCompensationDay, skipShiftTypeConflict);
        if (!conflicts.isEmpty()) {
            throw new ConflictException("Phát hiện xung đột: " + String.join("; ", conflicts));
        }
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

    @Transactional
    public void saveConflict(Schedule schedule, ScheduleConflict.ConflictType conflictType, String description) {
        ScheduleConflict conflict = ScheduleConflict.builder()
                .schedule(schedule)
                .conflictType(conflictType)
                .description(description)
                .isResolved(false)
                .build();
        scheduleConflictRepository.save(conflict);
        emailService.sendConflictAlert(schedule, description);
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

        com.hospital.scheduler.entity.ShiftType newShiftType = shiftTypeRepository.findById(shiftTypeId).orElse(null);
        boolean newIsOvernight = newShiftType != null && Boolean.TRUE.equals(newShiftType.getIsOvernight());

        for (Schedule s : existingSchedules) {
            if (excludeScheduleId != null && s.getId().equals(excludeScheduleId)) {
                continue;
            }

            boolean existingIsOvernight = s.getShiftType() != null && Boolean.TRUE.equals(s.getShiftType().getIsOvernight());

            // L01↔L02 conflict (overnight vs non-overnight)
            if (newIsOvernight != existingIsOvernight) {
                return java.util.Optional.of("Trùng loại ca: lịch trực 24/24 và ca thường không thể cùng ngày");
            }

            // L03↔L04 conflict (both non-overnight service shifts)
            if (!newIsOvernight && !existingIsOvernight) {
                String nid = newShiftType != null ? newShiftType.getId() : "";
                String eid = s.getShiftType() != null ? s.getShiftType().getId() : "";
                if (("L03".equals(nid) && "L04".equals(eid)) || ("L04".equals(nid) && "L03".equals(eid))) {
                    return java.util.Optional.of("Trùng phòng khám dịch vụ và phòng khám chuyên gia trong ngày");
                }
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
        return findReplacements(periodId, workDate, shiftTypeId, originalStaffId, requiredCount, excludedStaffIds, false);
    }

    public List<Staff> findReplacements(Integer periodId, LocalDate workDate, String shiftTypeId,
                                         Integer originalStaffId, Integer requiredCount,
                                         Set<Integer> excludedStaffIds, boolean skipCompensationDay) {
        List<Staff> replacements = new ArrayList<>();
        List<Staff> allStaff = staffRepository.findByIsActiveTrue();

        for (Staff staff : allStaff) {
            if (originalStaffId != null && staff.getId().equals(originalStaffId)) {
                continue;
            }
            if (excludedStaffIds != null && excludedStaffIds.contains(staff.getId())) {
                continue;
            }
            if (!hasAnyConflict(staff.getId(), workDate, shiftTypeId, null, skipCompensationDay)) {
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

    public CoverageReportDTO validateStaffingCoverage(Integer periodId) {
        SchedulePeriod period = scheduleRepository.findByPeriodId(periodId).stream()
                .findFirst()
                .map(Schedule::getPeriod)
                .orElse(null);

        if (period == null) {
            throw new IllegalArgumentException("Không tìm thấy kỳ lịch với ID: " + periodId);
        }

        List<ShiftRequirement> requirements = shiftRequirementRepository.findByPeriodId(periodId);
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        Map<String, Map<String, CoverageReportDTO.DayCoverage>> dailyCoverageMap = new LinkedHashMap<>();
        Map<String, CoverageReportDTO.ShiftTypeSummary> shiftTypeSummaryMap = new LinkedHashMap<>();
        Map<String, Integer> shiftTypeRequiredCount = new HashMap<>();
        Map<String, Integer> shiftTypeAssignedCount = new HashMap<>();
        Map<String, Integer> shiftTypeUnderstaffedDays = new HashMap<>();

        List<String> shiftTypeIds = Arrays.asList(SHIFT_TYPE_L01, SHIFT_TYPE_L02, SHIFT_TYPE_L03, SHIFT_TYPE_L04);
        for (String id : shiftTypeIds) {
            shiftTypeRequiredCount.put(id, 0);
            shiftTypeAssignedCount.put(id, 0);
            shiftTypeUnderstaffedDays.put(id, 0);
        }

        Map<String, List<ShiftRequirement>> requirementsByDate = requirements.stream()
                .collect(Collectors.groupingBy(r -> r.getWorkDate().toString()));

        Map<String, List<Schedule>> schedulesByDateAndShift = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getWorkDate() + "_" + s.getShiftType().getId()));

        LocalDate currentDate = period.getStartDate();
        int fullyCoveredDays = 0;
        int understaffedDays = 0;
        int overstaffedDays = 0;

        while (!currentDate.isAfter(period.getEndDate())) {
            String dateKey = currentDate.toString();
            Map<String, CoverageReportDTO.DayCoverage> dayCoverages = new LinkedHashMap<>();

            for (String shiftTypeId : shiftTypeIds) {
                String lookupKey = dateKey + "_" + shiftTypeId;
                List<ShiftRequirement> dayReqs = requirementsByDate.getOrDefault(dateKey, Collections.emptyList());
                ShiftRequirement requirement = dayReqs.stream()
                        .filter(r -> shiftTypeId.equals(r.getShiftType().getId()))
                        .findFirst()
                        .orElse(null);

                List<Schedule> daySchedules = schedulesByDateAndShift.getOrDefault(lookupKey, Collections.emptyList());
                int requiredStaff = requirement != null ? requirement.getRequiredStaffCount() : 0;
                int assignedStaff = daySchedules.size();
                int difference = assignedStaff - requiredStaff;

                CoverageReportDTO.CoverageStatus status;
                if (requirement == null) {
                    status = CoverageReportDTO.CoverageStatus.NO_REQUIREMENT;
                } else if (assignedStaff < requiredStaff) {
                    status = CoverageReportDTO.CoverageStatus.UNDERSTAFFED;
                    understaffedDays++;
                    shiftTypeUnderstaffedDays.merge(shiftTypeId, 1, Integer::sum);
                } else if (assignedStaff > requiredStaff) {
                    status = CoverageReportDTO.CoverageStatus.OVERSTAFFED;
                    overstaffedDays++;
                } else {
                    status = CoverageReportDTO.CoverageStatus.SUFFICIENT;
                    fullyCoveredDays++;
                }

                CoverageReportDTO.DayCoverage dayCoverage = CoverageReportDTO.DayCoverage.builder()
                        .date(currentDate)
                        .dayOfWeek(DateUtils.getDayOfWeekVietnamese(currentDate.getDayOfWeek()))
                        .shiftTypeId(shiftTypeId)
                        .shiftTypeName(requirement != null ? requirement.getShiftType().getName() : shiftTypeId)
                        .status(status)
                        .requiredStaff(requiredStaff)
                        .assignedStaff(assignedStaff)
                        .difference(difference)
                        .build();
                dayCoverages.put(shiftTypeId, dayCoverage);

                shiftTypeRequiredCount.merge(shiftTypeId, requiredStaff, Integer::sum);
                shiftTypeAssignedCount.merge(shiftTypeId, assignedStaff, Integer::sum);
            }

            dailyCoverageMap.put(dateKey, dayCoverages);
            currentDate = currentDate.plusDays(1);
        }

        for (String shiftTypeId : shiftTypeIds) {
            int totalRequired = shiftTypeRequiredCount.get(shiftTypeId);
            int totalAssigned = shiftTypeAssignedCount.get(shiftTypeId);
            BigDecimal coverageRate = totalRequired > 0
                    ? BigDecimal.valueOf((double) totalAssigned / totalRequired * 100).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.valueOf(100);

            ShiftType shiftType = shiftTypeRepository.findById(shiftTypeId).orElse(null);
            String shiftTypeName = shiftType != null ? shiftType.getName() : shiftTypeId;

            CoverageReportDTO.ShiftTypeSummary summary = CoverageReportDTO.ShiftTypeSummary.builder()
                    .shiftTypeId(shiftTypeId)
                    .shiftTypeName(shiftTypeName)
                    .totalRequired(totalRequired)
                    .totalAssigned(totalAssigned)
                    .coverageRate(coverageRate)
                    .understaffedDays(shiftTypeUnderstaffedDays.get(shiftTypeId))
                    .build();
            shiftTypeSummaryMap.put(shiftTypeId, summary);
        }

        int totalDays = dailyCoverageMap.size();
        int totalRequired = shiftTypeRequiredCount.values().stream().mapToInt(Integer::intValue).sum();
        int totalAssigned = shiftTypeAssignedCount.values().stream().mapToInt(Integer::intValue).sum();
        BigDecimal overallCoverageRate = totalRequired > 0
                ? BigDecimal.valueOf((double) totalAssigned / totalRequired * 100).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100);

        return CoverageReportDTO.builder()
                .periodId(periodId)
                .periodName(period.getPeriodName())
                .generatedAt(LocalDateTime.now())
                .totalDays(totalDays)
                .fullyCoveredDays(fullyCoveredDays)
                .understaffedDays(understaffedDays)
                .overstaffedDays(overstaffedDays)
                .overallCoverageRate(overallCoverageRate)
                .dailyCoverage(dailyCoverageMap)
                .shiftTypeSummary(shiftTypeSummaryMap)
                .build();
    }
}
