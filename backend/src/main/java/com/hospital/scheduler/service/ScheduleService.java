package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.ScheduleRequest;
import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.dto.response.ScheduleResponse;
import com.hospital.scheduler.dto.response.StaffResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository periodRepository;
    private final StaffRepository staffRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final AuditHistoryService auditHistoryService;

    public List<ScheduleResponse> getSchedulesByPeriod(Integer periodId) {
        return scheduleRepository.findByPeriodId(periodId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ScheduleResponse> getSchedulesByStaff(Integer staffId) {
        return scheduleRepository.findByStaffId(staffId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ScheduleResponse getScheduleById(Integer id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch với ID: " + id));
        return toResponse(schedule);
    }

    public ScheduleResponse createSchedule(ScheduleRequest request) {
        SchedulePeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        if (!request.getWorkDate().isBefore(period.getStartDate()) && !request.getWorkDate().isAfter(period.getEndDate())) {
            // ok
        } else if (request.getWorkDate().isBefore(period.getStartDate()) || request.getWorkDate().isAfter(period.getEndDate())) {
            throw new BadRequestException("Ngày làm việc phải nằm trong kỳ lịch");
        }

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể thêm lịch khi kỳ lịch ở trạng thái DRAFT");
        }

        Staff staff = staffRepository.findById(request.getStaffId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + request.getStaffId()));

        ShiftType shiftType = shiftTypeRepository.findById(request.getShiftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + request.getShiftTypeId()));

        // Check unique constraint
        scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                request.getPeriodId(), request.getStaffId(), request.getShiftTypeId(), request.getWorkDate())
                .ifPresent(s -> {
                    throw new ConflictException("Nhân sự đã được phân công ca này trong ngày");
                });

        conflictDetectionService.validateAndThrow(
                request.getStaffId(),
                request.getWorkDate(),
                request.getShiftTypeId(),
                null
        );

        ShiftRequirement requirement = null;
        if (request.getRequirementId() != null) {
            requirement = requirementRepository.findById(request.getRequirementId()).orElse(null);
        }

        Schedule schedule = Schedule.builder()
                .period(period)
                .workDate(request.getWorkDate())
                .staff(staff)
                .shiftType(shiftType)
                .requirement(requirement)
                .hasConflict(false)
                .build();

        Schedule saved = scheduleRepository.save(schedule);

        // Auto create compensation day for L01
        if ("L01".equals(request.getShiftTypeId())) {
            createCompensationDay(saved);
        }

        auditHistoryService.logAction("schedule", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, null);

        return toResponse(saved);
    }

    public ScheduleResponse updateSchedule(Integer id, ScheduleRequest request) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch với ID: " + id));

        SchedulePeriod period = schedule.getPeriod();
        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể cập nhật lịch khi kỳ lịch ở trạng thái DRAFT");
        }

        String oldShiftTypeId = schedule.getShiftType().getId();
        boolean wasL01 = "L01".equals(oldShiftTypeId);
        boolean willBeL01 = "L01".equals(request.getShiftTypeId());
        boolean shiftTypeChanged = !oldShiftTypeId.equals(request.getShiftTypeId());

        if (!request.getWorkDate().isBefore(period.getStartDate()) && !request.getWorkDate().isAfter(period.getEndDate())) {
            // ok
        } else if (request.getWorkDate().isBefore(period.getStartDate()) || request.getWorkDate().isAfter(period.getEndDate())) {
            throw new BadRequestException("Ngày làm việc phải nằm trong kỳ lịch");
        }

        Integer targetStaffId = request.getStaffId();
        LocalDate targetWorkDate = request.getWorkDate();
        String targetShiftTypeId = request.getShiftTypeId();

        if (!schedule.getStaff().getId().equals(targetStaffId)) {
            Staff newStaff = staffRepository.findById(targetStaffId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + targetStaffId));
            schedule.setStaff(newStaff);
        }

        if (!request.getPeriodId().equals(period.getId())) {
            SchedulePeriod newPeriod = periodRepository.findById(request.getPeriodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));
            schedule.setPeriod(newPeriod);
            period = newPeriod;
        }

        conflictDetectionService.validateAndThrow(targetStaffId, targetWorkDate, targetShiftTypeId, id);

        if (wasL01 && shiftTypeChanged) {
            List<CompensationDay> compDays = compensationDayRepository.findByScheduleId(id);
            compensationDayRepository.deleteAll(compDays);
        }

        schedule.setWorkDate(targetWorkDate);

        ShiftType newShiftType = shiftTypeRepository.findById(targetShiftTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + targetShiftTypeId));
        schedule.setShiftType(newShiftType);

        if (request.getRequirementId() != null) {
            ShiftRequirement req = requirementRepository.findById(request.getRequirementId()).orElse(null);
            schedule.setRequirement(req);
        }

        Schedule updated = scheduleRepository.save(schedule);

        if (!wasL01 && willBeL01) {
            createCompensationDay(updated);
        }

        return toResponse(updated);
    }

    public void deleteSchedule(Integer id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch với ID: " + id));

        SchedulePeriod period = schedule.getPeriod();
        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xóa lịch khi kỳ lịch ở trạng thái DRAFT");
        }

        // If L01, delete related compensation days first
        if ("L01".equals(schedule.getShiftType().getId())) {
            compensationDayRepository.deleteAll(compensationDayRepository.findByScheduleId(id));
        }

        auditHistoryService.logAction("schedule", id, AuditHistory.ActionType.DELETE, schedule, null, null);
        scheduleRepository.delete(schedule);
    }

    public List<ScheduleResponse> getSchedulesByPeriodAndDate(Integer periodId, LocalDate date) {
        return scheduleRepository.findByPeriodIdAndWorkDate(periodId, date).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ConflictCheckResponse checkConflictsInPeriod(Integer periodId) {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);
        List<ConflictCheckResponse.ConflictDetail> conflictDetails = new java.util.ArrayList<>();

        for (Schedule schedule : schedules) {
            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    schedule.getStaff().getId(),
                    schedule.getWorkDate(),
                    schedule.getShiftType().getId(),
                    schedule.getId()
            );

            if (!conflicts.isEmpty()) {
                schedule.setHasConflict(true);
                scheduleRepository.save(schedule);

                conflictDetails.add(ConflictCheckResponse.ConflictDetail.builder()
                        .scheduleId(schedule.getId())
                        .staffName(schedule.getStaff().getFullName())
                        .workDate(schedule.getWorkDate())
                        .shiftTypeId(schedule.getShiftType().getId())
                        .shiftTypeName(schedule.getShiftType().getName())
                        .conflictReasons(conflicts)
                        .build());
            }
        }

        return ConflictCheckResponse.builder()
                .periodId(periodId)
                .hasConflicts(!conflictDetails.isEmpty())
                .totalConflicts(conflictDetails.size())
                .conflicts(conflictDetails)
                .build();
    }

    public List<StaffResponse> findReplacements(Integer periodId, LocalDate workDate, String shiftTypeId,
                                                 Integer originalStaffId, Integer requiredCount) {
        List<Staff> replacements = conflictDetectionService.findReplacements(
                periodId, workDate, shiftTypeId, originalStaffId, requiredCount);
        return replacements.stream().map(s -> StaffResponse.builder()
                    .id(s.getId())
                    .fullName(s.getFullName())
                    .phone(s.getPhone())
                    .specialty(s.getSpecialty() != null ? StaffResponse.SpecialtyResponse.builder()
                            .id(s.getSpecialty().getId())
                            .name(s.getSpecialty().getName())
                            .build() : null)
                    .maxShiftsPerMonth(s.getMaxShiftsPerMonth())
                    .isActive(s.getIsActive())
                    .build())
                .collect(Collectors.toList());
    }

    private void createCompensationDay(Schedule schedule) {
        LocalDate shiftDate = schedule.getWorkDate();
        LocalDate compensationDate = calculateCompensationDate(shiftDate);

        // Check if compensation date already exists for this staff
        if (compensationDayRepository.findByStaffIdAndCompensationDate(schedule.getStaff().getId(), compensationDate).isPresent()) {
            return; // Already has compensation day
        }

        CompensationDay compDay = CompensationDay.builder()
                .schedule(schedule)
                .staff(schedule.getStaff())
                .period(schedule.getPeriod())
                .shiftDate(shiftDate)
                .compensationDate(compensationDate)
                .note("Ngày nghỉ bù tự động từ ca L01")
                .build();

        compensationDayRepository.save(compDay);
    }

    private LocalDate calculateCompensationDate(LocalDate shiftDate) {
        DayOfWeek dow = shiftDate.getDayOfWeek();
        return switch (dow) {
            case MONDAY -> shiftDate.plusDays(1);    // T2 -> T3
            case TUESDAY -> shiftDate.plusDays(1);   // T3 -> T4
            case WEDNESDAY -> shiftDate.plusDays(1); // T4 -> T5
            case THURSDAY -> shiftDate.plusDays(1);  // T5 -> T6
            case FRIDAY -> shiftDate.plusDays(4); // T6 -> T3 tuần sau (T6→T7→CN→T2→T3)
            case SATURDAY -> shiftDate.plusDays(4); // T7 -> T3 tuần sau (T7→CN→T2→T3)
            case SUNDAY -> shiftDate.plusDays(1);   // CN -> T2
        };
    }

    private ScheduleResponse toResponse(Schedule schedule) {
        List<String> staffRoles = schedule.getStaff().getStaffRoles().stream()
                .map(StaffRole::getRole)
                .filter(java.util.Objects::nonNull)
                .map(AppRole::getName)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<String> conflictReasons = conflictDetectionService.detectAllConflicts(
                schedule.getStaff().getId(),
                schedule.getWorkDate(),
                schedule.getShiftType().getId(),
                schedule.getId()
        );

        LocalDate compensationDate = compensationDayRepository.findByScheduleId(schedule.getId()).stream()
                .map(CompensationDay::getCompensationDate)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .findFirst()
                .orElseGet(() -> "L01".equals(schedule.getShiftType().getId()) ? calculateCompensationDate(schedule.getWorkDate()) : null);

        return ScheduleResponse.builder()
                .id(schedule.getId())
                .periodId(schedule.getPeriod().getId())
                .period(ScheduleResponse.PeriodSummary.builder()
                        .id(schedule.getPeriod().getId())
                        .periodName(schedule.getPeriod().getPeriodName())
                        .startDate(schedule.getPeriod().getStartDate())
                        .endDate(schedule.getPeriod().getEndDate())
                        .status(schedule.getPeriod().getStatus().name())
                        .build())
                .workDate(schedule.getWorkDate())
                .staff(ScheduleResponse.StaffSummary.builder()
                        .id(schedule.getStaff().getId())
                        .username(schedule.getStaff().getUsername())
                        .fullName(schedule.getStaff().getFullName())
                        .specialtyName(schedule.getStaff().getSpecialty() != null ? schedule.getStaff().getSpecialty().getName() : null)
                        .roles(staffRoles)
                        .build())
                .shiftType(ScheduleResponse.ShiftTypeSummary.builder()
                        .id(schedule.getShiftType().getId())
                        .name(schedule.getShiftType().getName())
                        .description(schedule.getShiftType().getDescription())
                        .startTime(schedule.getShiftType().getStartTime())
                        .endTime(schedule.getShiftType().getEndTime())
                        .isOvernight(schedule.getShiftType().getIsOvernight())
                        .fatigueScore(schedule.getShiftType().getFatigueScore())
                        .build())
                .requirementId(schedule.getRequirement() != null ? schedule.getRequirement().getId() : null)
                .compensationDate(compensationDate)
                .conflictReasons(conflictReasons)
                .notes(schedule.getRequirement() != null ? schedule.getRequirement().getNote() : null)
                .hasConflict(schedule.getHasConflict())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
