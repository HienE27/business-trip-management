package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.ShiftRequirementDTO;
import com.hospital.scheduler.dto.response.ShiftRequirementResponse;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ShiftRequirementService {

    private final ShiftRequirementRepository requirementRepository;
    private final SchedulePeriodRepository periodRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SpecialtyRepository specialtyRepository;

    public List<ShiftRequirementResponse> getAllRequirements() {
        return requirementRepository.findAll().stream()
                .map(ShiftRequirementResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ShiftRequirementResponse> getRequirementsByPeriod(Integer periodId) {
        return requirementRepository.findByPeriodId(periodId).stream()
                .map(ShiftRequirementResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ShiftRequirementResponse> getRequirementsByPeriodAndDate(Integer periodId, LocalDate date) {
        return requirementRepository.findByPeriodIdAndWorkDate(periodId, date).stream()
                .map(ShiftRequirementResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ShiftRequirementResponse> getRequirementsByPeriodAndDateRange(Integer periodId, LocalDate startDate, LocalDate endDate) {
        return requirementRepository.findByPeriodIdAndDateRange(periodId, startDate, endDate).stream()
                .map(ShiftRequirementResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public ShiftRequirementResponse getRequirementById(Integer id) {
        ShiftRequirement requirement = requirementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu nhân sự với ID: " + id));
        return ShiftRequirementResponse.fromEntity(requirement);
    }

    public ShiftRequirementResponse createRequirement(ShiftRequirementDTO dto) {
        SchedulePeriod period = periodRepository.findById(dto.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + dto.getPeriodId()));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể thêm yêu cầu nhân sự khi kỳ lịch ở trạng thái DRAFT");
        }

        if (dto.getWorkDate().isBefore(period.getStartDate()) || dto.getWorkDate().isAfter(period.getEndDate())) {
            throw new BadRequestException("Ngày làm việc phải nằm trong kỳ lịch");
        }

        ShiftType shiftType = shiftTypeRepository.findById(dto.getShiftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + dto.getShiftTypeId()));

        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên môn với ID: " + dto.getSpecialtyId()));

        ShiftRequirement requirement = ShiftRequirement.builder()
                .period(period)
                .workDate(dto.getWorkDate())
                .shiftType(shiftType)
                .specialty(specialty)
                .requiredStaffCount(dto.getRequiredStaffCount())
                .note(dto.getNote())
                .build();

        ShiftRequirement saved = requirementRepository.save(requirement);
        return ShiftRequirementResponse.fromEntity(saved);
    }

    public ShiftRequirementResponse updateRequirement(Integer id, ShiftRequirementDTO dto) {
        ShiftRequirement requirement = requirementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu nhân sự với ID: " + id));

        if (requirement.getPeriod().getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể cập nhật yêu cầu nhân sự khi kỳ lịch ở trạng thái DRAFT");
        }

        if (dto.getWorkDate().isBefore(requirement.getPeriod().getStartDate()) || dto.getWorkDate().isAfter(requirement.getPeriod().getEndDate())) {
            throw new BadRequestException("Ngày làm việc phải nằm trong kỳ lịch");
        }

        ShiftType shiftType = shiftTypeRepository.findById(dto.getShiftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + dto.getShiftTypeId()));

        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên môn với ID: " + dto.getSpecialtyId()));

        requirement.setWorkDate(dto.getWorkDate());
        requirement.setShiftType(shiftType);
        requirement.setSpecialty(specialty);
        requirement.setRequiredStaffCount(dto.getRequiredStaffCount());
        requirement.setNote(dto.getNote());

        ShiftRequirement saved = requirementRepository.save(requirement);
        return ShiftRequirementResponse.fromEntity(saved);
    }

    public void deleteRequirement(Integer id) {
        ShiftRequirement requirement = requirementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu nhân sự với ID: " + id));

        if (requirement.getPeriod().getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xóa yêu cầu nhân sự khi kỳ lịch ở trạng thái DRAFT");
        }

        requirementRepository.delete(requirement);
    }
}
