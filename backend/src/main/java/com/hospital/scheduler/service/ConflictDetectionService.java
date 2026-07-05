package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.dto.response.CoverageReportDTO;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.util.DateUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
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
    private final AuthContextService authContextService;
    @Lazy
    private final EmailService emailService;
    @Lazy
    private final ConflictBroadcastService conflictBroadcastService;
    private final SystemLogService systemLogService;

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        return detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, false, false);
    }

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, boolean skipCompensationDay) {
        return detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, null, skipCompensationDay, false);
    }

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, boolean skipCompensationDay, boolean skipShiftTypeConflict) {
        return detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, null, skipCompensationDay, skipShiftTypeConflict);
    }

    public List<String> detectAllConflicts(Integer staffId, LocalDate workDate, String shiftTypeId,
                                           Integer excludeScheduleId, Integer periodId, boolean skipCompensationDay, boolean skipShiftTypeConflict) {
        List<String> conflicts = new ArrayList<>();

        detectLeaveConflict(staffId, workDate).ifPresent(conflicts::add);
        if (!skipCompensationDay) {
            detectCompensationConflict(staffId, workDate).ifPresent(conflicts::add);
        }
        if (!skipShiftTypeConflict) {
            detectShiftTypeConflict(staffId, workDate, shiftTypeId, excludeScheduleId, periodId).ifPresent(conflicts::add);
        }
        return conflicts;
    }

    /**
     * Batch-aware conflict detection for checkPeriodConflicts.
     * Uses pre-loaded data maps to avoid O(N) individual DB queries per schedule.
     */
    private List<String> detectAllConflictsWithBatch(
            Integer staffId, LocalDate workDate, String shiftTypeId,
            Integer excludeScheduleId, Integer periodId,
            Map<Integer, List<LeaveRequest>> leavesByStaff,
            Map<Integer, List<CompensationDay>> compDaysByStaff,
            Map<LocalDate, Map<Integer, List<Schedule>>> schedulesByDateByStaff,
            Map<String, ShiftType> shiftTypeById) {

        List<String> conflicts = new ArrayList<>();

        // Leave conflict — check pre-loaded leaves
        List<LeaveRequest> staffLeaves = leavesByStaff.getOrDefault(staffId, Collections.emptyList());
        boolean hasApprovedLeave = staffLeaves.stream()
                .anyMatch(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED
                        && !l.getStartDate().isAfter(workDate) && !l.getEndDate().isBefore(workDate));
        if (hasApprovedLeave) {
            conflicts.add("Nhân sự có ngày nghỉ phép được duyệt trong ngày này");
        }

        // Compensation day conflict — check pre-loaded comp days
        List<CompensationDay> staffCompDays = compDaysByStaff.getOrDefault(staffId, Collections.emptyList());
        boolean hasCompDay = staffCompDays.stream()
                .anyMatch(cd -> cd.getCompensationDate() != null && cd.getCompensationDate().equals(workDate));
        if (hasCompDay) {
            conflicts.add("Ngày này là ngày nghỉ bù của nhân sự");
        }

        // Shift-type conflict — check pre-loaded same-day schedules
        Map<Integer, List<Schedule>> sameDaySchedules = schedulesByDateByStaff.get(workDate);
        if (sameDaySchedules != null) {
            List<Schedule> staffDaySchedules = sameDaySchedules.getOrDefault(staffId, Collections.emptyList());
            ShiftType newShiftType = shiftTypeById.get(shiftTypeId);
            boolean newIsOvernight = newShiftType != null && Boolean.TRUE.equals(newShiftType.getIsOvernight());

            for (Schedule s : staffDaySchedules) {
                if (excludeScheduleId != null && s.getId().equals(excludeScheduleId)) continue;

                boolean existingIsOvernight = s.getShiftType() != null && Boolean.TRUE.equals(s.getShiftType().getIsOvernight());
                if (newIsOvernight != existingIsOvernight) {
                    conflicts.add("Trùng loại ca: lịch trực 24/24 và ca thường không thể cùng ngày");
                    break;
                }
                if (!newIsOvernight && !existingIsOvernight) {
                    String nid = newShiftType != null ? newShiftType.getId() : "";
                    String eid = s.getShiftType() != null ? s.getShiftType().getId() : "";
                    if (("L03".equals(nid) && "L04".equals(eid)) || ("L04".equals(nid) && "L03".equals(eid))) {
                        conflicts.add("Trùng phòng khám dịch vụ và phòng khám chuyên gia trong ngày");
                        break;
                    }
                }
            }
        }

        return conflicts;
    }

    public boolean hasAnyConflict(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        return !detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId).isEmpty();
    }

    public boolean hasAnyConflict(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, boolean skipCompensationDay) {
        return hasAnyConflict(staffId, workDate, shiftTypeId, excludeScheduleId, skipCompensationDay, false);
    }

    public boolean hasAnyConflict(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, boolean skipCompensationDay, boolean skipShiftTypeConflict) {
        return !detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, null, skipCompensationDay, skipShiftTypeConflict).isEmpty();
    }

    public void validateAndThrow(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId) {
        validateAndThrow(staffId, workDate, shiftTypeId, excludeScheduleId, null);
    }

    public void validateAndThrow(Integer staffId, LocalDate workDate, String shiftTypeId, Integer excludeScheduleId, Integer periodId) {
        validateAndThrow(staffId, workDate, shiftTypeId, excludeScheduleId, periodId, false);
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

    /**
     * Validate conflicts and send email alert to the staff member if conflicts are found.
     * Used in CRUD operations to provide immediate notification on schedule create/update.
     */
    public void validateAndThrowWithEmail(Integer staffId, LocalDate workDate, String shiftTypeId,
                                         Integer excludeScheduleId, Integer periodId) {
        List<String> conflicts = detectAllConflicts(staffId, workDate, shiftTypeId, excludeScheduleId, periodId, false, false);
        if (!conflicts.isEmpty()) {
            String description = String.join("; ", conflicts);
            staffRepository.findById(staffId).ifPresent(staff -> {
                try {
                    emailService.sendConflictAlertToStaff(staff, null, description);
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(ConflictDetectionService.class)
                            .warn("Failed to send conflict email for staff {}: {}", staffId, e.getMessage());
                    try {
                        systemLogService.logSystem("EMAIL_FAILURE",
                                "Failed to send conflict alert email for staff ID=" + staffId +
                                ", staff=" + staff.getFullName() + ", reason: " + e.getMessage(),
                                authContextService.getCurrentStaff().getId(), null, null);
                    } catch (Exception logEx) {
                        org.slf4j.LoggerFactory.getLogger(ConflictDetectionService.class)
                                .error("Failed to log email failure for staff {}: {}", staffId, logEx.getMessage());
                    }
                }
            });
            throw new ConflictException("Phát hiện xung đột: " + description);
        }
    }

    @Transactional
    public ConflictCheckResponse checkPeriodConflicts(Integer periodId) {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);
        List<ConflictCheckResponse.ConflictDetail> conflictDetails = new ArrayList<>();

        // Collect schedules for batch update (performance optimization)
        List<Schedule> schedulesToUpdate = new ArrayList<>();

        // ── Batch-load all conflict data upfront (4 queries) instead of O(N) per-schedule ──
        // Collect all unique dates in the period for adjacent-day queries
        Set<LocalDate> periodDates = schedules.stream()
                .map(Schedule::getWorkDate)
                .collect(java.util.stream.Collectors.toSet());

        // Batch 0: pre-load all shift types (only 4, but needed for batch conflict check)
        Map<String, ShiftType> shiftTypeById = new java.util.HashMap<>();
        for (ShiftType st : shiftTypeRepository.findAll()) {
            shiftTypeById.put(st.getId(), st);
        }

        // Batch 1: all approved leaves within the period's date range
        LocalDate minDate = periodDates.stream().min(LocalDate::compareTo).orElse(null);
        LocalDate maxDate = periodDates.stream().max(LocalDate::compareTo).orElse(null);

        Map<Integer, List<LeaveRequest>> leavesByStaff = new java.util.HashMap<>();
        if (minDate != null && maxDate != null) {
            for (LeaveRequest lr : leaveRequestRepository.findApprovedInRange(minDate, maxDate)) {
                leavesByStaff.computeIfAbsent(lr.getStaff().getId(), k -> new java.util.ArrayList<>()).add(lr);
            }
        }

        // Batch 2: all compensation days within the period
        Map<Integer, List<CompensationDay>> compDaysByStaff = new java.util.HashMap<>();
        if (minDate != null && maxDate != null) {
            for (CompensationDay cd : compensationDayRepository.findInRange(minDate, maxDate)) {
                compDaysByStaff.computeIfAbsent(cd.getStaff().getId(), k -> new java.util.ArrayList<>()).add(cd);
            }
        }

        // Batch 3: all schedules (period + adjacent days) for shift-type and back-to-back checks
        Set<LocalDate> adjacentDates = new java.util.HashSet<>(periodDates);
        if (minDate != null) adjacentDates.add(minDate.minusDays(1));
        if (maxDate != null) adjacentDates.add(maxDate.plusDays(1));

        Map<LocalDate, Map<Integer, List<Schedule>>> schedulesByDateByStaff = new java.util.HashMap<>();
        for (LocalDate d : adjacentDates) {
            for (Schedule s : scheduleRepository.findByWorkDateWithDetails(d)) {
                schedulesByDateByStaff.computeIfAbsent(d, k -> new java.util.HashMap<>())
                        .computeIfAbsent(s.getStaff().getId(), k -> new java.util.ArrayList<>()).add(s);
            }
        }

        for (Schedule schedule : schedules) {
            Staff staff = schedule.getStaff();
            LocalDate workDate = schedule.getWorkDate();
            String shiftTypeId = schedule.getShiftType().getId();

            List<String> conflicts = detectAllConflictsWithBatch(
                    staff.getId(), workDate, shiftTypeId, schedule.getId(), periodId,
                    leavesByStaff, compDaysByStaff, schedulesByDateByStaff, shiftTypeById);
            if (!conflicts.isEmpty()) {
                schedule.setHasConflict(true);
                schedulesToUpdate.add(schedule);

                String description = String.join("; ", conflicts);
                ConflictCheckResponse.ConflictDetail conflictDetail = ConflictCheckResponse.ConflictDetail.builder()
                        .scheduleId(schedule.getId())
                        .staffName(staff.getFullName())
                        .workDate(workDate)
                        .shiftTypeId(shiftTypeId)
                        .shiftTypeName(schedule.getShiftType().getName())
                        .conflictReasons(conflicts)
                        .periodId(periodId)
                        .originalStaffId(staff.getId())
                        .build();
                conflictDetails.add(conflictDetail);

                // Persist the conflict record so the resolution flow can find it later, and
                // notify both the staff member and the conflict channel.
                ConflictSaveResult saveResult = saveConflictInternal(schedule, ScheduleConflict.ConflictType.OTHER, description);
                // Fire-and-forget: email is @Async so this call just submits to the thread pool
                // without blocking the transaction. Catch any exception to prevent the email
                // failure from affecting the conflict persistence flow.
                try {
                    emailService.sendConflictAlertToStaff(staff, schedule, description);
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(ConflictDetectionService.class)
                            .warn("Failed to send conflict email for schedule {}: {}", schedule.getId(), e.getMessage());
                    // Log failure to system log for manual follow-up
                    try {
                        systemLogService.logSystem("EMAIL_FAILURE",
                                "Failed to send conflict alert email for schedule ID=" + schedule.getId() +
                                ", staff=" + staff.getFullName() + " (" + staff.getId() + ")" +
                                ", reason: " + e.getMessage(),
                                authContextService.getCurrentStaff().getId(), null, null);
                    } catch (Exception logEx) {
                        org.slf4j.LoggerFactory.getLogger(ConflictDetectionService.class)
                                .error("Failed to log email failure for schedule {}: {}", schedule.getId(), logEx.getMessage());
                    }
                }

                // Only broadcast when the conflict is genuinely new — re-running the
                // periodic conflict check on a schedule that already has an unresolved
                // conflict would otherwise spam every connected client with duplicate
                // notifications on every dashboard refresh.
                if (saveResult.created()) {
                    try {
                        conflictBroadcastService.broadcastConflict(saveResult.conflict(), conflictDetail);
                    } catch (Exception e) {
                        org.slf4j.LoggerFactory.getLogger(ConflictDetectionService.class)
                                .warn("Failed to broadcast conflict for schedule {}: {}", schedule.getId(), e.getMessage());
                    }
                }
            } else if (Boolean.TRUE.equals(schedule.getHasConflict())) {
                // Conflict was resolved since the last check — clear the flag.
                schedule.setHasConflict(false);
                schedulesToUpdate.add(schedule);
            }
        }

        // Batch save all schedule updates (performance optimization)
        if (!schedulesToUpdate.isEmpty()) {
            scheduleRepository.saveAll(schedulesToUpdate);
        }

        List<String> coverageGaps = detectCoverageGaps(periodId);

        ConflictCheckResponse response = ConflictCheckResponse.builder()
                .periodId(periodId)
                .hasConflicts(!conflictDetails.isEmpty())
                .totalConflicts(conflictDetails.size())
                .conflicts(conflictDetails)
                .coverageGaps(coverageGaps)
                .hasCoverageGaps(!coverageGaps.isEmpty())
                .totalCoverageGaps(coverageGaps.size())
                .build();

        // Broadcast the batch to all connected dashboard clients.
        // Each conflict in the payload already carries its own scheduleId and staffName,
        // so listeners can individually track which schedules remain unresolved.
        if (!conflictDetails.isEmpty()) {
            try {
                conflictBroadcastService.broadcastConflictBatch(conflictDetails, periodId);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ConflictDetectionService.class)
                        .warn("Failed to broadcast conflict batch for period {}: {}", periodId, e.getMessage());
            }
        }

        return response;
    }

    /**
     * Persist a new conflict record for the given schedule, deduplicating
     * against any unresolved conflict that already exists for the same
     * schedule. Returns both the conflict entity (new or pre-existing)
     * and a boolean indicating whether a brand-new row was actually
     * written to the database — callers use this to decide whether to
     * broadcast over WebSocket so we don't keep re-firing the same
     * notification on every conflict re-check.
     */
    @Transactional
    public ConflictSaveResult saveConflictInternal(Schedule schedule, ScheduleConflict.ConflictType conflictType, String description) {
        List<ScheduleConflict> existing = scheduleConflictRepository.findByScheduleIdAndIsResolvedFalse(schedule.getId());
        if (!existing.isEmpty()) {
            return new ConflictSaveResult(existing.get(0), false);
        }
        ScheduleConflict conflict = ScheduleConflict.builder()
                .schedule(schedule)
                .conflictType(conflictType)
                .description(description)
                .isResolved(false)
                .build();
        ScheduleConflict saved = scheduleConflictRepository.save(conflict);
        return new ConflictSaveResult(saved, true);
    }

    /** Pair returned by {@link #saveConflictInternal}: the conflict row + whether it's freshly created. */
    public record ConflictSaveResult(ScheduleConflict conflict, boolean created) {}

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
            conflict.setResolvedAt(LocalDateTime.now());
            scheduleConflictRepository.save(conflict);

            // Broadcast CONFLICT_RESOLVED so all connected clients update their badge/toast.
            conflictBroadcastService.broadcastConflictResolved(conflictId, conflict.getSchedule().getId());
        });
    }

    private java.util.Optional<String> detectLeaveConflict(Integer staffId, LocalDate workDate) {
        List<LeaveRequest> leaves = leaveRequestRepository.findByStaffIdAndDateRange(staffId, workDate, workDate);
        boolean hasApprovedLeave = leaves.stream()
                .anyMatch(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED);
        if (hasApprovedLeave) {
            return java.util.Optional.of("Nhân sự có ngày nghỉ phép được duyệt trong ngày này");
        }
        // Note: PENDING leaves are NOT blocked here - they are handled through the approval flow
        // when leave is approved, the schedule becomes conflicted and replacement is found
        return java.util.Optional.empty();
    }

    private java.util.Optional<String> detectCompensationConflict(Integer staffId, LocalDate workDate) {
        return compensationDayRepository.findByStaffIdAndCompensationDate(staffId, workDate)
                .map(cd -> "Ngày này là ngày nghỉ bù của nhân sự");
    }

    private java.util.Optional<String> detectShiftTypeConflict(Integer staffId, LocalDate workDate,
                                                                String shiftTypeId, Integer excludeScheduleId) {
        return detectShiftTypeConflict(staffId, workDate, shiftTypeId, excludeScheduleId, null);
    }

    private java.util.Optional<String> detectShiftTypeConflict(Integer staffId, LocalDate workDate,
                                                                String shiftTypeId, Integer excludeScheduleId, Integer periodId) {
        List<Schedule> existingSchedules;
        if (periodId != null) {
            existingSchedules = scheduleRepository.findByStaffIdAndWorkDateAndPeriodId(staffId, workDate, periodId);
        } else {
            existingSchedules = scheduleRepository.findByStaffIdAndWorkDate(staffId, workDate);
        }

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

    public List<Staff> findReplacements(Integer periodId, LocalDate workDate, String shiftTypeId,
                                         Integer originalStaffId, Integer requiredCount,
                                         Set<Integer> excludedStaffIds) {
        return findReplacements(periodId, workDate, shiftTypeId, originalStaffId, requiredCount, excludedStaffIds, false);
    }

    public List<Staff> findReplacements(Integer periodId, LocalDate workDate, String shiftTypeId,
                                         Integer originalStaffId, Integer requiredCount,
                                         Set<Integer> excludedStaffIds, boolean skipCompensationDay) {
        // Batch-fetch all conflict data upfront — 4 queries total instead of O(N) queries.
        LocalDate prevDay = workDate.minusDays(1);
        LocalDate nextDay = workDate.plusDays(1);

        Set<Integer> onLeaveStaffIds = new java.util.HashSet<>();
        for (LeaveRequest lr : leaveRequestRepository.findApprovedByDate(workDate)) {
            onLeaveStaffIds.add(lr.getStaff().getId());
        }

        Set<Integer> onCompDayStaffIds = new java.util.HashSet<>();
        if (!skipCompensationDay) {
            // FIX: Query compensation days for workDate ± 1 to catch boundary cases.
            // Example: L01 on Friday (prev period) → comp on Tuesday (new period).
            LocalDate compStart = workDate.minusDays(1);
            LocalDate compEnd = workDate.plusDays(1);
            for (CompensationDay cd : compensationDayRepository.findInRange(compStart, compEnd)) {
                onCompDayStaffIds.add(cd.getStaff().getId());
            }
        }

        Map<Integer, List<Schedule>> schedulesByStaff = new java.util.HashMap<>();
        for (Schedule s : scheduleRepository.findByWorkDateWithDetails(workDate)) {
            schedulesByStaff.computeIfAbsent(s.getStaff().getId(), k -> new java.util.ArrayList<>()).add(s);
        }

        // FIX: Use findL01SchedulesInRange instead of findByStaffIdAndDateRange(null,...)
        // Also extend to workDate ± 2 to catch compensation-day chain: L01(N-2)→comp(N-1)→L01(N)
        Set<Integer> hasAdjacentL01 = new java.util.HashSet<>();
        LocalDate adjStart = workDate.minusDays(2);
        LocalDate adjEnd = workDate.plusDays(1);
        for (Schedule s : scheduleRepository.findL01SchedulesInRange(adjStart, adjEnd)) {
            hasAdjacentL01.add(s.getStaff().getId());
        }

        ShiftType shiftType = shiftTypeRepository.findById(shiftTypeId).orElse(null);
        boolean newIsOvernight = shiftType != null && Boolean.TRUE.equals(shiftType.getIsOvernight());

        // NOTE: maxShiftsPerMonth is handled as a SOFT constraint by the sort comparator.
        // Hard-filtering on max shifts would incorrectly block scheduling when no under-limit
        // staff are available, contradicting M07-F01 "không giới hạn cố định".
        // See filterAndSortEligibleStaffBatch for the soft sort implementation.

        List<Staff> replacements = new ArrayList<>();
        List<Staff> allActive = staffRepository.findByIsActiveTrue();
        if (log.isDebugEnabled()) {
            log.debug("findReplacements date={} type={} requiredCount={} activeStaff={} onLeave={} onCompDay={} adjacentL01={}",
                    workDate, shiftTypeId, requiredCount, allActive.size(), onLeaveStaffIds.size(), onCompDayStaffIds.size(), hasAdjacentL01.size());
        }
        for (Staff staff : staffRepository.findByIsActiveTrue()) {
            if (originalStaffId != null && staff.getId().equals(originalStaffId)) continue;
            if (excludedStaffIds != null && excludedStaffIds.contains(staff.getId())) continue;
            if (onLeaveStaffIds.contains(staff.getId())) continue;
            if (onCompDayStaffIds.contains(staff.getId())) continue;
            // Adjacent restriction only applies to L01
            if (SHIFT_TYPE_L01.equals(shiftTypeId) && hasAdjacentL01.contains(staff.getId())) continue;

            // NOTE: maxShiftsPerMonth is handled as a SOFT constraint in the algorithm's
            // sort comparator (filterAndSortEligibleStaffBatch). Hard-filtering here would
            // incorrectly exclude staff when no one under limit is available, contradicting
            // M07-F01 "không giới hạn cố định". The sort comparator places over-limit staff
            // last so they are only assigned when necessary.

            // Same-day shift-type conflict: L01↔L02 or L03↔L04
            List<Schedule> daySchedules = schedulesByStaff.get(staff.getId());
            if (daySchedules != null) {
                boolean hasConflict = false;
                for (Schedule s : daySchedules) {
                    boolean existingIsOvernight = s.getShiftType() != null && Boolean.TRUE.equals(s.getShiftType().getIsOvernight());
                    if (newIsOvernight != existingIsOvernight) { hasConflict = true; break; }
                    if (!newIsOvernight && !existingIsOvernight) {
                        String nid = shiftType != null ? shiftType.getId() : "";
                        String eid = s.getShiftType() != null ? s.getShiftType().getId() : "";
                        if (("L03".equals(nid) && "L04".equals(eid)) || ("L04".equals(nid) && "L03".equals(eid))) {
                            hasConflict = true; break;
                        }
                    }
                }
                if (hasConflict) continue;
            }

            replacements.add(staff);
            if (replacements.size() >= requiredCount) break;
        }

        return replacements;
    }

    public List<String> detectCoverageGaps(Integer periodId) {
        List<String> gaps = new ArrayList<>();
        List<ShiftRequirement> requirements = shiftRequirementRepository.findByPeriodId(periodId);

        if (requirements.isEmpty()) {
            return gaps;
        }

        // OPTIMIZATION: batch load all counts in ONE query instead of N individual queries
        Map<String, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : scheduleRepository.countGroupedByPeriodWorkDateShiftType(periodId)) {
            Integer pid = (Integer) row[0];
            LocalDate date = (LocalDate) row[1];
            String shiftTypeId = (String) row[2];
            Long cnt = (Long) row[3];
            countMap.put(pid + "_" + date + "_" + shiftTypeId, cnt);
        }

        for (ShiftRequirement requirement : requirements) {
            LocalDate workDate = requirement.getWorkDate();
            String shiftTypeId = requirement.getShiftType().getId();
            int requiredCount = requirement.getRequiredStaffCount();

            long assignedCount = countMap.getOrDefault(
                    periodId + "_" + workDate + "_" + shiftTypeId, 0L);

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
            // Cap coverage at 100% to avoid >100% when algorithm assigns more than required
            double coverageRatio = totalRequired > 0 ? (double) totalAssigned / totalRequired : 0;
            BigDecimal coverageRate = BigDecimal.valueOf(Math.min(coverageRatio, 1.0) * 100).setScale(2, RoundingMode.HALF_UP);

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
        // Cap coverage at 100% to avoid >100% when algorithm assigns more than required
        double coverageRatio = totalRequired > 0 ? (double) totalAssigned / totalRequired : 0;
        BigDecimal overallCoverageRate = BigDecimal.valueOf(Math.min(coverageRatio, 1.0) * 100).setScale(2, RoundingMode.HALF_UP);

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
