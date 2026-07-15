package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.CompensationDayRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompensationDayService {

    private final CompensationDayRepository compensationDayRepository;
    private final ScheduleRepository scheduleRepository;
    private final StaffRepository staffRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;

    public record CompensationDayDTO(
            Integer id,
            Integer staffId,
            String staffName,
            String staffCode,
            Integer scheduleId,
            String shiftDate,
            String compensationDate,
            String note
    ) {}

    public record StaffOption(Integer id, String fullName, String staffCode) {}

    public record CreateCompensationDayRequest(
            Integer scheduleId,
            LocalDate compensationDate,
            String note
    ) {}

    public record UpdateCompensationDayRequest(
            LocalDate compensationDate,
            String note
    ) {}

    public List<CompensationDayDTO> getCompensationDaysByPeriod(Integer periodId) {
        return compensationDayRepository.findByPeriodId(periodId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public CompensationDayDTO createManual(CreateCompensationDayRequest req) {
        if (req.scheduleId() == null || req.compensationDate() == null) {
            throw new BadRequestException("scheduleId và compensationDate là bắt buộc.");
        }

        Schedule schedule = scheduleRepository.findById(req.scheduleId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Schedule not found with id: " + req.scheduleId()));

        // Compensation days only apply to L01 (24/24) shifts by convention.
        if (schedule.getShiftType() != null && !"L01".equals(schedule.getShiftType().getId())) {
            throw new BadRequestException(
                    "Chỉ có thể tạo ngày nghỉ bù cho ca trực L01 (24/24). Ca hiện tại: "
                            + schedule.getShiftType().getId());
        }

        if (compensationDayRepository.existsByScheduleId(schedule.getId())) {
            throw new ConflictException(
                    "Lịch trực #" + schedule.getId() + " đã có ngày nghỉ bù.");
        }

        if (compensationDayRepository.existsByStaffIdAndCompensationDate(
                schedule.getStaff().getId(), req.compensationDate())) {
            throw new ConflictException(
                    "Nhân sự " + schedule.getStaff().getFullName()
                            + " đã có ngày nghỉ bù vào " + req.compensationDate() + ".");
        }

        // Validate compensation date within period range
        SchedulePeriod period = schedule.getPeriod();
        if (period != null
                && (req.compensationDate().isBefore(period.getStartDate())
                || req.compensationDate().isAfter(period.getEndDate()))) {
            throw new BadRequestException(
                    "Ngày nghỉ bù (" + req.compensationDate()
                            + ") phải nằm trong khoảng thời gian của kỳ lịch ("
                            + period.getStartDate() + " → " + period.getEndDate() + ").");
        }

        CompensationDay entity = CompensationDay.builder()
                .schedule(schedule)
                .staff(schedule.getStaff())
                .period(period)
                .shiftDate(schedule.getWorkDate())
                .compensationDate(req.compensationDate())
                .note(req.note() == null || req.note().isBlank() ? null : req.note().trim())
                .build();

        CompensationDay saved = compensationDayRepository.save(entity);
        return toDTO(saved);
    }

    @Transactional
    public CompensationDayDTO updateCompensationDate(Integer id, UpdateCompensationDayRequest req) {
        CompensationDay existing = compensationDayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompensationDay not found with id: " + id));

        if (req.compensationDate() != null
                && !req.compensationDate().equals(existing.getCompensationDate())) {
            if (compensationDayRepository.existsByStaffIdAndCompensationDate(
                    existing.getStaff().getId(), req.compensationDate())) {
                throw new ConflictException(
                        "Nhân sự " + existing.getStaff().getFullName()
                                + " đã có ngày nghỉ bù vào " + req.compensationDate() + ".");
            }
            SchedulePeriod period = existing.getPeriod();
            if (period != null
                    && (req.compensationDate().isBefore(period.getStartDate())
                    || req.compensationDate().isAfter(period.getEndDate()))) {
                throw new BadRequestException(
                        "Ngày nghỉ bù phải nằm trong khoảng thời gian của kỳ lịch.");
            }
            existing.setCompensationDate(req.compensationDate());
        }

        if (req.note() != null) {
            existing.setNote(req.note().isBlank() ? null : req.note().trim());
        }

        CompensationDay saved = compensationDayRepository.save(existing);
        return toDTO(saved);
    }

    @Transactional
    public void delete(Integer id) {
        if (!compensationDayRepository.existsById(id)) {
            throw new ResourceNotFoundException("CompensationDay not found with id: " + id);
        }
        compensationDayRepository.deleteById(id);
    }

    /**
     * Lightweight read-only view used by the manual-create modal to populate
     * the staff dropdown. Returns active staff only.
     */
    public List<StaffOption> findActiveStaff() {
        return staffRepository.findByIsActiveTrue().stream()
                .map(s -> new StaffOption(s.getId(), s.getFullName(), s.getStaffCode()))
                .toList();
    }

    private CompensationDayDTO toDTO(CompensationDay cd) {
        return new CompensationDayDTO(
                cd.getId(),
                cd.getStaff() != null ? cd.getStaff().getId() : null,
                cd.getStaff() != null ? cd.getStaff().getFullName() : null,
                cd.getStaff() != null ? cd.getStaff().getStaffCode() : null,
                cd.getSchedule() != null ? cd.getSchedule().getId() : null,
                cd.getShiftDate() != null ? cd.getShiftDate().toString() : null,
                cd.getCompensationDate() != null ? cd.getCompensationDate().toString() : null,
                cd.getNote()
        );
    }
}