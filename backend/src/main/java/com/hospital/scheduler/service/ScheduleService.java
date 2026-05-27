package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.ScheduleRequest;
import com.hospital.scheduler.dto.response.ScheduleResponse;
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
    private final LeaveRequestRepository leaveRequestRepository;

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

        // Check compensation day - cannot schedule on compensation day
        compensationDayRepository.findByStaffIdAndCompensationDate(request.getStaffId(), request.getWorkDate())
                .ifPresent(cd -> {
                    throw new ConflictException("Nhân sự đang có ngày nghỉ bù trong ngày này");
                });

        // Check leave request
        List<LeaveRequest> leaves = leaveRequestRepository.findByStaffIdAndDateRange(
                request.getStaffId(), request.getWorkDate(), request.getWorkDate());
        boolean hasApprovedLeave = leaves.stream().anyMatch(l -> l.getStatus() == LeaveRequest.LeaveStatus.APPROVED);
        if (hasApprovedLeave) {
            throw new ConflictException("Nhân sự đang có yêu cầu nghỉ phép được duyệt trong ngày này");
        }

        // Check L01 vs L02 conflict: same staff, same day, different shift types
        if (("L01".equals(request.getShiftTypeId()) || "L02".equals(request.getShiftTypeId()))) {
            String conflictingType = "L01".equals(request.getShiftTypeId()) ? "L02" : "L01";
            scheduleRepository.findByStaffIdAndWorkDate(request.getStaffId(), request.getWorkDate())
                    .stream()
                    .filter(s -> conflictingType.equals(s.getShiftType().getId()))
                    .findFirst()
                    .ifPresent(s -> {
                        throw new ConflictException("Nhân sự đã được phân công ca '" + conflictingType + "' trong ngày này");
                    });
        }

        // Check L03 vs L04 conflict
        if (("L03".equals(request.getShiftTypeId()) || "L04".equals(request.getShiftTypeId()))) {
            String conflictingType = "L03".equals(request.getShiftTypeId()) ? "L04" : "L03";
            scheduleRepository.findByStaffIdAndWorkDate(request.getStaffId(), request.getWorkDate())
                    .stream()
                    .filter(s -> conflictingType.equals(s.getShiftType().getId()))
                    .findFirst()
                    .ifPresent(s -> {
                        throw new ConflictException("Nhân sự đã được phân công ca '" + conflictingType + "' trong ngày này");
                    });
        }

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

        if (!schedule.getStaff().getId().equals(request.getStaffId()) || shiftTypeChanged) {
            Integer staffId = request.getStaffId();
            LocalDate workDate = request.getWorkDate();

            if (!schedule.getStaff().getId().equals(staffId)) {
                Staff newStaff = staffRepository.findById(staffId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + staffId));
                schedule.setStaff(newStaff);
            }

            scheduleRepository.findByStaffIdAndWorkDate(staffId, workDate)
                    .stream()
                    .filter(s -> !s.getId().equals(id))
                    .filter(s -> {
                        String sId = s.getShiftType().getId();
                        return ("L01".equals(request.getShiftTypeId()) && "L02".equals(sId)) ||
                               ("L02".equals(request.getShiftTypeId()) && "L01".equals(sId)) ||
                               ("L03".equals(request.getShiftTypeId()) && "L04".equals(sId)) ||
                               ("L04".equals(request.getShiftTypeId()) && "L03".equals(sId));
                    })
                    .findFirst()
                    .ifPresent(s -> {
                        throw new ConflictException("Nhân sự đã được phân công ca '" + s.getShiftType().getId() + "' trong ngày này");
                    });
        }

        if (wasL01 && shiftTypeChanged) {
            compensationDayRepository.findByScheduleId(id).ifPresent(compDay -> {
                compensationDayRepository.delete(compDay);
            });
        }

        if (!request.getWorkDate().equals(schedule.getWorkDate()) &&
                compensationDayRepository.findByStaffIdAndCompensationDate(schedule.getStaff().getId(), request.getWorkDate()).isPresent()) {
            throw new ConflictException("Nhân sự đang có ngày nghỉ bù trong ngày này");
        }

        schedule.setWorkDate(request.getWorkDate());

        ShiftType newShiftType = shiftTypeRepository.findById(request.getShiftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + request.getShiftTypeId()));
        schedule.setShiftType(newShiftType);

        if (!request.getPeriodId().equals(period.getId())) {
            SchedulePeriod newPeriod = periodRepository.findById(request.getPeriodId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));
            schedule.setPeriod(newPeriod);
            period = newPeriod;
        }

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

        scheduleRepository.delete(schedule);
    }

    public List<ScheduleResponse> getSchedulesByPeriodAndDate(Integer periodId, LocalDate date) {
        return scheduleRepository.findByPeriodIdAndWorkDate(periodId, date).stream()
                .map(this::toResponse)
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
            case SATURDAY -> shiftDate.plusDays(3); // T7 -> T3 tuần sau (T7→CN→T2→T3)
            case SUNDAY -> shiftDate.plusDays(1);   // CN -> T2
        };
    }

    private ScheduleResponse toResponse(Schedule schedule) {
        return ScheduleResponse.builder()
                .id(schedule.getId())
                .periodId(schedule.getPeriod().getId())
                .workDate(schedule.getWorkDate())
                .staff(ScheduleResponse.StaffSummary.builder()
                        .id(schedule.getStaff().getId())
                        .fullName(schedule.getStaff().getFullName())
                        .build())
                .shiftType(ScheduleResponse.ShiftTypeSummary.builder()
                        .id(schedule.getShiftType().getId())
                        .name(schedule.getShiftType().getName())
                        .isOvernight(schedule.getShiftType().getIsOvernight())
                        .build())
                .requirementId(schedule.getRequirement() != null ? schedule.getRequirement().getId() : null)
                .hasConflict(schedule.getHasConflict())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
