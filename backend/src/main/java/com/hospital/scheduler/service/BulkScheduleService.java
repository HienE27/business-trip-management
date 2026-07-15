package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.BulkL01Request;
import com.hospital.scheduler.dto.request.BulkScheduleRequest;
import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.dto.response.BulkL01Response;
import com.hospital.scheduler.dto.response.BulkScheduleResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.CompensationDayRepository;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bulk-schedule orchestration extracted from {@link ScheduleService} in
 * SERVICE_AUDIT.md P3. Owns {@code createBulkL01} and {@code bulkCreateSchedules}
 * — both operate on N entries in one transaction, batch-load staff once, and
 * perform in-loop conflict tracking to catch sibling conflicts within the same
 * batch.
 *
 * <p>{@link ScheduleService} delegates to this class via thin wrappers so the
 * controller surface and test expectations are unchanged.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class BulkScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository periodRepository;
    private final StaffRepository staffRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final HolidayRepository holidayRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final AuditHistoryService auditHistoryService;
    private final AuthContextService authContextService;
    private final CompensationDateCalculator compensationDateCalculator;
    private final NotificationService notificationService;
    @Lazy
    private final ConflictBroadcastService conflictBroadcastService;

    /**
     * Bulk create L01 (trực 24/24) schedules.
     * Validates: all entries must use L01, all staff must be ACTIVE.
     * For each entry: creates schedule + auto-creates compensation_day.
     */
    public BulkL01Response createBulkL01(BulkL01Request request) {
        List<String> errors = new ArrayList<>();
        List<BulkL01Response.BulkL01ScheduleResult> results = new ArrayList<>();
        int successCount = 0;

        SchedulePeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể thêm lịch khi kỳ lịch ở trạng thái DRAFT");
        }

        ShiftType l01ShiftType = shiftTypeRepository.findById(ConflictDetectionService.SHIFT_TYPE_L01)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca L01"));

        // OPTIMIZATION: batch load all staff upfront to avoid N individual findById calls
        List<Integer> staffIds = request.getEntries().stream()
                .map(BulkL01Request.L01Entry::getStaffId)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, Staff> staffMap = staffIds.isEmpty() ? Collections.emptyMap()
                : staffRepository.findAllById(staffIds).stream()
                        .collect(Collectors.toMap(Staff::getId, s -> s));

        // Track in-loop assignments to catch sibling L01↔L02 conflicts
        Map<String, Set<String>> inLoopAssignments = new HashMap<>();

        for (BulkL01Request.L01Entry entry : request.getEntries()) {
            Integer staffId = entry.getStaffId();
            LocalDate workDate = entry.getWorkDate();
            String key = staffId + "_" + workDate;

            // Validate period date range
            if (workDate.isBefore(period.getStartDate()) || workDate.isAfter(period.getEndDate())) {
                errors.add("Ngày " + workDate + " nằm ngoài kỳ lịch");
                results.add(BulkL01Response.BulkL01ScheduleResult.builder()
                        .staffId(staffId)
                        .workDate(workDate.toString())
                        .error("Ngày nằm ngoài kỳ lịch")
                        .build());
                continue;
            }

            // Validate: do not schedule on holidays
            if (holidayRepository.existsByHolidayDateAndIsActiveTrue(workDate)) {
                errors.add("Ngày " + workDate + " là ngày nghỉ lễ");
                results.add(BulkL01Response.BulkL01ScheduleResult.builder()
                        .staffId(staffId)
                        .workDate(workDate.toString())
                        .error("Ngày nghỉ lễ, không thể xếp lịch")
                        .build());
                continue;
            }

            // Validate staff exists and is ACTIVE — OPTIMIZATION: use pre-loaded staff map
            Staff staff = staffMap.get(staffId);
            if (staff == null) {
                errors.add("Nhân sự ID " + staffId + " không tồn tại");
                results.add(BulkL01Response.BulkL01ScheduleResult.builder()
                        .staffId(staffId)
                        .workDate(workDate.toString())
                        .error("Nhân sự không tồn tại")
                        .build());
                continue;
            }
            if (!Boolean.TRUE.equals(staff.getIsActive())) {
                errors.add("Nhân sự ID " + staffId + " không hoạt động");
                results.add(BulkL01Response.BulkL01ScheduleResult.builder()
                        .staffId(staffId)
                        .workDate(workDate.toString())
                        .error("Nhân sự không hoạt động")
                        .build());
                continue;
            }

            // Check unique constraint
            if (scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                    request.getPeriodId(), staffId, ConflictDetectionService.SHIFT_TYPE_L01, workDate).isPresent()) {
                errors.add("Nhân sự " + staffId + " đã có L01 ngày " + workDate);
                results.add(BulkL01Response.BulkL01ScheduleResult.builder()
                        .staffId(staffId)
                        .workDate(workDate.toString())
                        .error("Đã có L01 trong ngày")
                        .build());
                continue;
            }

            // Check in-loop conflict (L01 vs L02 in same batch)
            Set<String> existingShifts = inLoopAssignments.get(key);
            if (existingShifts != null && !existingShifts.isEmpty()) {
                String errMsg = "Nhân sự " + staffId + " đã có lịch xung đột trong batch ngày " + workDate;
                errors.add(errMsg);
                results.add(BulkL01Response.BulkL01ScheduleResult.builder()
                        .staffId(staffId)
                        .workDate(workDate.toString())
                        .error(errMsg)
                        .build());
                continue;
            }

            // Validate conflicts against DB state
            try {
                conflictDetectionService.validateAndThrow(staffId, workDate,
                        ConflictDetectionService.SHIFT_TYPE_L01, null, request.getPeriodId());
            } catch (ConflictException e) {
                errors.add("Nhân sự " + staffId + " ngày " + workDate + ": " + e.getMessage());
                results.add(BulkL01Response.BulkL01ScheduleResult.builder()
                        .staffId(staffId)
                        .workDate(workDate.toString())
                        .error(e.getMessage())
                        .build());
                continue;
            }

            // Create schedule
            Schedule schedule = Schedule.builder()
                    .period(period)
                    .workDate(workDate)
                    .staff(staff)
                    .shiftType(l01ShiftType)
                    .hasConflict(false)
                    .build();

            Schedule saved = scheduleRepository.save(schedule);
            inLoopAssignments.computeIfAbsent(key, k -> new HashSet<>()).add(ConflictDetectionService.SHIFT_TYPE_L01);

            // Auto-create compensation day
            createCompensationDay(saved);

            // OPTIMIZATION: calculate compDate directly without extra DB query
            LocalDate compDate = compensationDateCalculator.calculate(saved.getWorkDate());

            auditHistoryService.logAction("schedule", saved.getId(), AuditHistory.ActionType.INSERT,
                    null, saved, authContextService.getCurrentStaff().getId());

            // Notify staff with compensation date if applicable
            if (compDate != null) {
                notificationService.createNotification(staffId,
                        new NotificationDTO("Phân công lịch mới",
                                "Bạn được phân công lịch L01 ngày " + workDate + ". Ngày nghỉ bù: " + compDate + "."));
            } else {
                notificationService.createNotification(staffId,
                        new NotificationDTO("Phân công lịch mới",
                                "Bạn được phân công lịch L01 ngày " + workDate + "."));
            }

            results.add(BulkL01Response.BulkL01ScheduleResult.builder()
                    .scheduleId(saved.getId())
                    .staffId(staffId)
                    .workDate(workDate.toString())
                    .build());
            successCount++;
        }

        return BulkL01Response.builder()
                .successCount(successCount)
                .failureCount(request.getEntries().size() - successCount)
                .totalCount(request.getEntries().size())
                .errors(errors)
                .results(results)
                .build();
    }

    /**
     * Bulk create schedules for any shift type (L01/L02/L03/L04).
     * Validates: staff exists, active, not already assigned same type+date,
     * not a compensation day, not a holiday, and no cross-type conflicts.
     * Creates compensation days automatically for L01 entries.
     */
    public BulkScheduleResponse bulkCreateSchedules(BulkScheduleRequest request, String shiftTypeId) {
        List<BulkScheduleResponse.BulkResultEntry> results = new ArrayList<>();
        int successCount = 0;

        SchedulePeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể thêm lịch khi kỳ lịch ở trạng thái DRAFT");
        }

        ShiftType shiftType = shiftTypeRepository.findById(shiftTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + shiftTypeId));

        // OPTIMIZATION: batch load all staff upfront to avoid N individual findById calls
        List<Integer> bulkStaffIds = request.getEntries().stream()
                .map(BulkScheduleRequest.BulkScheduleEntry::getStaffId)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, Staff> bulkStaffMap = bulkStaffIds.isEmpty() ? Collections.emptyMap()
                : staffRepository.findAllById(bulkStaffIds).stream()
                        .collect(Collectors.toMap(Staff::getId, s -> s));

        Map<String, Set<String>> inLoopAssignments = new HashMap<>();

        for (BulkScheduleRequest.BulkScheduleEntry entry : request.getEntries()) {
            Integer staffId = entry.getStaffId();
            LocalDate workDate = entry.getWorkDate();
            String key = staffId + "_" + workDate;

            if (workDate.isBefore(period.getStartDate()) || workDate.isAfter(period.getEndDate())) {
                results.add(BulkScheduleResponse.BulkResultEntry.builder()
                        .workDate(workDate.toString())
                        .staffId(staffId)
                        .error("Ngày nằm ngoài kỳ lịch")
                        .build());
                continue;
            }

            // OPTIMIZATION: use pre-loaded staff map
            Staff staff = bulkStaffMap.get(staffId);
            if (staff == null) {
                results.add(BulkScheduleResponse.BulkResultEntry.builder()
                        .workDate(workDate.toString())
                        .staffId(staffId)
                        .error("Nhân sự không tồn tại")
                        .build());
                continue;
            }
            if (!Boolean.TRUE.equals(staff.getIsActive())) {
                results.add(BulkScheduleResponse.BulkResultEntry.builder()
                        .workDate(workDate.toString())
                        .staffId(staffId)
                        .error("Nhân sự không hoạt động")
                        .build());
                continue;
            }

            if (scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                    request.getPeriodId(), staffId, shiftTypeId, workDate).isPresent()) {
                results.add(BulkScheduleResponse.BulkResultEntry.builder()
                        .workDate(workDate.toString())
                        .staffId(staffId)
                        .error("Đã có lịch " + shiftTypeId + " trong ngày")
                        .build());
                continue;
            }

            Set<String> existingShifts = inLoopAssignments.get(key);
            if (existingShifts != null && !existingShifts.isEmpty()) {
                results.add(BulkScheduleResponse.BulkResultEntry.builder()
                        .workDate(workDate.toString())
                        .staffId(staffId)
                        .error("Nhân sự đã có lịch xung đột trong batch ngày " + workDate)
                        .build());
                continue;
            }

            if (!compensationDayRepository.findByStaffIdAndCompensationDate(staffId, workDate).isEmpty()) {
                results.add(BulkScheduleResponse.BulkResultEntry.builder()
                        .workDate(workDate.toString())
                        .staffId(staffId)
                        .error("Ngày này là ngày nghỉ bù của nhân sự")
                        .build());
                continue;
            }

            if (holidayRepository.existsByHolidayDateAndIsActiveTrue(workDate)) {
                results.add(BulkScheduleResponse.BulkResultEntry.builder()
                        .workDate(workDate.toString())
                        .staffId(staffId)
                        .error("Ngày là ngày nghỉ lễ")
                        .build());
                continue;
            }

            try {
                conflictDetectionService.validateAndThrow(staffId, workDate, shiftTypeId, null, request.getPeriodId());
            } catch (ConflictException e) {
                results.add(BulkScheduleResponse.BulkResultEntry.builder()
                        .workDate(workDate.toString())
                        .staffId(staffId)
                        .error(e.getMessage())
                        .build());
                continue;
            }

            Schedule schedule = Schedule.builder()
                    .period(period)
                    .workDate(workDate)
                    .staff(staff)
                    .shiftType(shiftType)
                    .hasConflict(false)
                    .build();

            Schedule saved = scheduleRepository.save(schedule);
            inLoopAssignments.computeIfAbsent(key, k -> new HashSet<>()).add(shiftTypeId);

            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                createCompensationDay(saved);
            }

            // OPTIMIZATION: calculate compDate directly without extra DB query
            LocalDate compDate = null;
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                compDate = compensationDateCalculator.calculate(saved.getWorkDate());
            }

            if (compDate != null) {
                notificationService.createNotification(staffId,
                        new NotificationDTO("Phân công lịch mới",
                                "Bạn được phân công lịch " + shiftType.getName() + " vào ngày " + workDate + ". Ngày nghỉ bù: " + compDate + "."));
            } else {
                notificationService.createNotification(staffId,
                        new NotificationDTO("Phân công lịch mới",
                                "Bạn được phân công lịch " + shiftType.getName() + " vào ngày " + workDate + "."));
            }

            results.add(BulkScheduleResponse.BulkResultEntry.builder()
                    .workDate(workDate.toString())
                    .staffId(staffId)
                    .scheduleId(saved.getId())
                    .build());
            successCount++;
        }

        return BulkScheduleResponse.builder()
                .totalRequested(request.getEntries().size())
                .successCount(successCount)
                .failureCount(request.getEntries().size() - successCount)
                .results(results)
                .build();
    }

    /**
     * Local copy of {@code ScheduleService.createCompensationDay}. Kept
     * private here so the bulk path doesn't depend on the parent service
     * (which would create a circular bean wiring through the controller).
     * Behaviour matches the single-schedule path byte-for-byte.
     */
    private void createCompensationDay(Schedule schedule) {
        if (!compensationDayRepository.findByScheduleId(schedule.getId()).isEmpty()) {
            return;
        }

        LocalDate shiftDate = schedule.getWorkDate();
        LocalDate compensationDate = compensationDateCalculator.calculate(shiftDate);

        CompensationDay compDay = CompensationDay.builder()
                .schedule(schedule)
                .staff(schedule.getStaff())
                .period(schedule.getPeriod())
                .shiftDate(shiftDate)
                .compensationDate(compensationDate)
                .note("Ngày nghỉ bù tự động từ ca L01")
                .build();

        CompensationDay saved = null;
        try {
            saved = compensationDayRepository.save(compDay);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Compensation day already exists for schedule {} (race condition): {}",
                    schedule.getId(), e.getMessage());
            return;
        }
        if (saved != null && saved.getId() != null) {
            auditHistoryService.logAction("compensation_day", saved.getId(), AuditHistory.ActionType.INSERT,
                    null, saved, authContextService.getCurrentStaff().getId());
        }
    }
}