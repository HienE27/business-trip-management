package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.SaveTemplateRequest;
import com.hospital.scheduler.dto.request.ScheduleTemplateRequest;
import com.hospital.scheduler.dto.response.ScheduleTemplateResponse;
import com.hospital.scheduler.dto.response.TemplatePreviewItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.entity.ScheduleTemplate;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.exception.BadRequestException;
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
import java.util.ArrayList;
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
    private final ObjectMapper objectMapper;

    private static final String[] VIETNAMESE_DAYS = { "", "T2", "T3", "T4", "T5", "T6", "T7", "CN" };

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
            throw new BadRequestException("Chỉ có thể áp dụng mẫu lịch khi kỳ lịch ở trạng thái DRAFT");
        }

        if (template.getDayOfWeek() == null || template.getShiftTypeId() == null) {
            throw new BadRequestException(
                    "Mẫu lịch loại GENERATED không hỗ trợ áp dụng trực tiếp. Vui lòng chạy auto schedule và áp dụng lại.");
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

    public List<TemplatePreviewItem> previewTemplate(Integer templateId, Integer periodId) {
        ScheduleTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu lịch với ID: " + templateId));

        var period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + periodId));

        if (period.getStatus() != com.hospital.scheduler.entity.SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xem trước mẫu lịch khi kỳ lịch ở trạng thái DRAFT");
        }

        if (template.getDayOfWeek() == null || template.getShiftTypeId() == null) {
            throw new BadRequestException(
                    "Mẫu lịch loại GENERATED không hỗ trợ xem trước. Vui lòng chạy auto schedule và lưu mẫu PATTERN trước.");
        }

        ShiftType shiftType = shiftTypeRepository.findById(template.getShiftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + template.getShiftTypeId()));

        List<TemplatePreviewItem> items = new ArrayList<>();
        LocalDate currentDate = period.getStartDate();

        while (!currentDate.isAfter(period.getEndDate())) {
            int dowValue = currentDate.getDayOfWeek().getValue();
            if (dowValue == template.getDayOfWeek()) {
                items.add(TemplatePreviewItem.builder()
                        .id(0)
                        .workDate(currentDate.toString())
                        .dayOfWeek(VIETNAMESE_DAYS[dowValue])
                        .shiftTypeId(template.getShiftTypeId())
                        .shiftTypeName(shiftType.getName())
                        .specialtyName(template.getSpecialty() != null ? template.getSpecialty().getName() : null)
                        .requiredStaffCount(template.getRequiredStaffCount())
                        .build());
            }
            currentDate = currentDate.plusDays(1);
        }

        return items;
    }

    public ScheduleTemplateResponse saveTemplateFromGenerated(SaveTemplateRequest request) {
        var period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        String algorithmConfigJson = null;
        if (request.getAlgorithmConfig() != null && !request.getAlgorithmConfig().isEmpty()) {
            try {
                algorithmConfigJson = objectMapper.writeValueAsString(request.getAlgorithmConfig());
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Lỗi khi serialize cấu hình thuật toán: " + e.getMessage());
            }
        }

        String scheduleIdsJson = null;
        if (request.getScheduleIds() != null && !request.getScheduleIds().isEmpty()) {
            try {
                scheduleIdsJson = objectMapper.writeValueAsString(request.getScheduleIds());
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Lỗi khi serialize danh sách lịch: " + e.getMessage());
            }
        }

        ScheduleTemplate template = ScheduleTemplate.builder()
                .name(request.getTemplateName())
                .description(request.getDescription())
                .sourcePeriodId(request.getPeriodId())
                .sourcePeriodName(period.getPeriodName())
                .algorithmType(request.getAlgorithmType())
                .algorithmConfig(algorithmConfigJson)
                .templateType("GENERATED")
                .generatedScheduleIds(scheduleIdsJson)
                .dayOfWeek(null)
                .shiftTypeId(null)
                .requiredStaffCount(0)
                .isActive(true)
                .build();

        ScheduleTemplate saved = templateRepository.save(template);
        return ScheduleTemplateResponse.fromEntity(saved);
    }
}
