package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.ScheduleTemplateRequest;
import com.hospital.scheduler.dto.response.ScheduleTemplateResponse;
import com.hospital.scheduler.entity.ScheduleTemplate;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ScheduleTemplateRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleTemplateService {

    private final ScheduleTemplateRepository templateRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SpecialtyRepository specialtyRepository;
    private final SchedulePeriodRepository periodRepository;
    private final ShiftRequirementRepository requirementRepository;

    public List<ScheduleTemplateResponse> getAllTemplates() {
        return templateRepository.findAll().stream()
                .map(ScheduleTemplateResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<ScheduleTemplateResponse> getActiveTemplates() {
        return templateRepository.findByIsActiveTrue().stream()
                .map(ScheduleTemplateResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public ScheduleTemplateResponse getTemplateById(Integer id) {
        ScheduleTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu lịch với ID: " + id));
        return ScheduleTemplateResponse.fromEntity(template);
    }

    public ScheduleTemplateResponse createTemplate(ScheduleTemplateRequest request) {
        shiftTypeRepository.findById(request.getShiftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + request.getShiftTypeId()));

        Specialty specialty = null;
        if (request.getSpecialtyId() != null) {
            specialty = specialtyRepository.findById(request.getSpecialtyId()).orElse(null);
        }

        ScheduleTemplate template = ScheduleTemplate.builder()
                .name(request.getName())
                .description(request.getDescription())
                .dayOfWeek(request.getDayOfWeek())
                .shiftTypeId(request.getShiftTypeId())
                .specialty(specialty)
                .requiredStaffCount(request.getRequiredStaffCount() != null ? request.getRequiredStaffCount() : 1)
                .isActive(true)
                .build();

        ScheduleTemplate saved = templateRepository.save(template);
        return ScheduleTemplateResponse.fromEntity(saved);
    }

    public ScheduleTemplateResponse updateTemplate(Integer id, ScheduleTemplateRequest request) {
        ScheduleTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu lịch với ID: " + id));

        shiftTypeRepository.findById(request.getShiftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + request.getShiftTypeId()));

        Specialty specialty = null;
        if (request.getSpecialtyId() != null) {
            specialty = specialtyRepository.findById(request.getSpecialtyId()).orElse(null);
        }

        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setDayOfWeek(request.getDayOfWeek());
        template.setShiftTypeId(request.getShiftTypeId());
        template.setSpecialty(specialty);
        template.setRequiredStaffCount(request.getRequiredStaffCount() != null ? request.getRequiredStaffCount() : 1);

        return ScheduleTemplateResponse.fromEntity(templateRepository.save(template));
    }

    public void deleteTemplate(Integer id) {
        ScheduleTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu lịch với ID: " + id));
        template.setIsActive(false);
        templateRepository.save(template);
    }

    public int applyTemplateToPeriod(Integer templateId, Integer periodId) {
        ScheduleTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu lịch với ID: " + templateId));

        var period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + periodId));

        if (period.getStatus() != com.hospital.scheduler.entity.SchedulePeriod.PeriodStatus.DRAFT) {
            throw new com.hospital.scheduler.exception.BadRequestException("Chỉ có thể áp dụng mẫu lịch khi kỳ lịch ở trạng thái DRAFT");
        }

        ShiftType shiftType = shiftTypeRepository.findById(template.getShiftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + template.getShiftTypeId()));

        int appliedCount = 0;
        LocalDate currentDate = period.getStartDate();

        while (!currentDate.isAfter(period.getEndDate())) {
            int dayOfWeek = currentDate.getDayOfWeek().getValue();

            if (dayOfWeek == template.getDayOfWeek()) {
                ShiftRequirement requirement = ShiftRequirement.builder()
                        .period(period)
                        .workDate(currentDate)
                        .shiftType(shiftType)
                        .specialty(template.getSpecialty())
                        .requiredStaffCount(template.getRequiredStaffCount())
                        .build();
                requirementRepository.save(requirement);
                appliedCount++;
            }
            currentDate = currentDate.plusDays(1);
        }

        return appliedCount;
    }
}
