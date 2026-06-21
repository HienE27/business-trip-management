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
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.ScheduleTemplateRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.util.CompensationDateCalculator;
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
    private final ScheduleRepository scheduleRepository;
    private final com.hospital.scheduler.repository.CompensationDayRepository compensationDayRepository;
    private final CompensationDateCalculator compensationDateCalculator;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SpecialtyRepository specialtyRepository;
    private final SchedulePeriodRepository periodRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final StaffRepository staffRepository;
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

        // Handle GENERATED templates: copy the actual schedules from source period
        if ("GENERATED".equals(template.getTemplateType())) {
            return applyGeneratedTemplate(template, period);
        }

        if (template.getDayOfWeek() == null || template.getShiftTypeId() == null) {
            throw new BadRequestException(
                    "Mẫu lịch thiếu thông tin ngày trong tuần hoặc loại ca. Vui lòng chọn mẫu hợp lệ.");
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

    /**
     * Apply a GENERATED template: deserialize the schedule IDs from the source period,
     * load each Schedule entity, and copy (create new) each schedule into the target period.
     * Only schedules from the source period are copied; the target period must be DRAFT.
     * Returns the count of schedules successfully copied.
     */
    private int applyGeneratedTemplate(ScheduleTemplate template, com.hospital.scheduler.entity.SchedulePeriod period) {
        if (template.getGeneratedScheduleIds() == null || template.getGeneratedScheduleIds().isBlank()) {
            return 0;
        }

        ObjectMapper mapper = new ObjectMapper();
        List<Integer> sourceScheduleIds;
        try {
            sourceScheduleIds = mapper.readValue(template.getGeneratedScheduleIds(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Integer.class));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Không thể đọc danh sách lịch gốc từ mẫu: " + e.getMessage());
        }

        List<com.hospital.scheduler.entity.Schedule> sourceSchedules = scheduleRepository.findAllById(sourceScheduleIds);

        int appliedCount = 0;
        for (com.hospital.scheduler.entity.Schedule source : sourceSchedules) {
            com.hospital.scheduler.entity.Schedule copy = com.hospital.scheduler.entity.Schedule.builder()
                    .period(period)
                    .staff(source.getStaff())
                    .shiftType(source.getShiftType())
                    .workDate(source.getWorkDate())
                    .hasConflict(false)
                    .build();
            com.hospital.scheduler.entity.Schedule saved = scheduleRepository.save(copy);
            appliedCount++;

            // Auto-create compensation day for L01 (24/24 duty) schedules.
            if (Boolean.TRUE.equals(source.getShiftType().getIsOvernight())) {
                LocalDate compDate = compensationDateCalculator.calculate(saved.getWorkDate());
                if (compensationDayRepository.findByStaffIdAndCompensationDate(saved.getStaff().getId(), compDate).isEmpty()) {
                    CompensationDay compDay = CompensationDay.builder()
                            .schedule(saved)
                            .staff(saved.getStaff())
                            .period(period)
                            .shiftDate(saved.getWorkDate())
                            .compensationDate(compDate)
                            .note("Ngày nghỉ bù tự động từ mẫu lịch GENERATED")
                            .build();
                    compensationDayRepository.save(compDay);
                }
            }
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

        if ("GENERATED".equals(template.getTemplateType())) {
            return previewGeneratedTemplate(template);
        }

        if (template.getDayOfWeek() == null || template.getShiftTypeId() == null) {
            throw new BadRequestException(
                    "Mẫu lịch thiếu thông tin ngày trong tuần hoặc loại ca. Vui lòng chọn mẫu hợp lệ.");
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

    /**
     * Preview a GENERATED template: deserialize schedule IDs and build preview items
     * from the source schedules (without actually creating them in the target period).
     */
    private List<TemplatePreviewItem> previewGeneratedTemplate(ScheduleTemplate template) {
        if (template.getGeneratedScheduleIds() == null || template.getGeneratedScheduleIds().isBlank()) {
            return List.of();
        }

        ObjectMapper mapper = new ObjectMapper();
        List<Integer> sourceScheduleIds;
        try {
            sourceScheduleIds = mapper.readValue(template.getGeneratedScheduleIds(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Integer.class));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Không thể đọc danh sách lịch gốc từ mẫu: " + e.getMessage());
        }

        List<com.hospital.scheduler.entity.Schedule> sourceSchedules = scheduleRepository.findAllById(sourceScheduleIds);
        List<TemplatePreviewItem> items = new ArrayList<>();

        for (com.hospital.scheduler.entity.Schedule s : sourceSchedules) {
            int dowValue = s.getWorkDate().getDayOfWeek().getValue();
            items.add(TemplatePreviewItem.builder()
                    .id(s.getId())
                    .workDate(s.getWorkDate().toString())
                    .dayOfWeek(VIETNAMESE_DAYS[dowValue])
                    .shiftTypeId(s.getShiftType().getId())
                    .shiftTypeName(s.getShiftType().getName())
                    .specialtyName(s.getRequirement() != null && s.getRequirement().getSpecialty() != null ? s.getRequirement().getSpecialty().getName() : null)
                    .requiredStaffCount(1)
                    .assignedStaffId(s.getStaff().getId())
                    .assignedStaffName(s.getStaff().getFullName())
                    .build());
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

    /**
     * Apply a GENERATED template with user edits.
     * Deserializes the source schedule IDs, applies staff changes from the edits list,
     * then copies each (potentially edited) schedule into the target period.
     *
     * @param request contains templateId, periodId, and a list of edits
     * @return the number of schedules successfully created
     */
    @Transactional
    public int applyTemplateWithEdits(com.hospital.scheduler.dto.request.TemplateApplyWithEditsRequest request) {
        ScheduleTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu lịch với ID: " + request.getTemplateId()));

        var period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        if (!"GENERATED".equals(template.getTemplateType())) {
            throw new BadRequestException("Chỉ hỗ trợ áp dụng mẫu GENERATED với chỉnh sửa.");
        }

        if (period.getStatus() != com.hospital.scheduler.entity.SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể áp dụng mẫu lịch khi kỳ lịch ở trạng thái DRAFT");
        }

        if (template.getGeneratedScheduleIds() == null || template.getGeneratedScheduleIds().isBlank()) {
            return 0;
        }

        ObjectMapper mapper = new ObjectMapper();
        List<Integer> sourceScheduleIds;
        try {
            sourceScheduleIds = mapper.readValue(template.getGeneratedScheduleIds(),
                    mapper.getTypeFactory().constructCollectionType(List.class, Integer.class));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Không thể đọc danh sách lịch gốc từ mẫu: " + e.getMessage());
        }

        List<com.hospital.scheduler.entity.Schedule> sourceSchedules = scheduleRepository.findAllById(sourceScheduleIds);

        // Build edit lookup: sourceScheduleId -> newStaffId
        java.util.Map<Integer, Integer> editMap = new java.util.HashMap<>();
        if (request.getEdits() != null) {
            for (var edit : request.getEdits()) {
                if (edit.getSlotId() != null && edit.getAssignedStaffId() != null) {
                    editMap.put(edit.getSlotId(), edit.getAssignedStaffId());
                }
            }
        }

        int appliedCount = 0;
        for (com.hospital.scheduler.entity.Schedule source : sourceSchedules) {
            // Apply edit if present
            com.hospital.scheduler.entity.Staff targetStaff = source.getStaff();
            if (editMap.containsKey(source.getId())) {
                Integer newStaffId = editMap.get(source.getId());
                targetStaff = staffRepository.findById(newStaffId)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + newStaffId));
            }

            com.hospital.scheduler.entity.Schedule copy = com.hospital.scheduler.entity.Schedule.builder()
                    .period(period)
                    .staff(targetStaff)
                    .shiftType(source.getShiftType())
                    .workDate(source.getWorkDate())
                    .hasConflict(false)
                    .build();
            com.hospital.scheduler.entity.Schedule saved = scheduleRepository.save(copy);
            appliedCount++;

            // Auto-create compensation day for L01 schedules
            if (Boolean.TRUE.equals(source.getShiftType().getIsOvernight())) {
                LocalDate compDate = compensationDateCalculator.calculate(saved.getWorkDate());
                if (compensationDayRepository.findByStaffIdAndCompensationDate(saved.getStaff().getId(), compDate).isEmpty()) {
                    CompensationDay compDay = CompensationDay.builder()
                            .schedule(saved)
                            .staff(saved.getStaff())
                            .period(period)
                            .shiftDate(saved.getWorkDate())
                            .compensationDate(compDate)
                            .note("Ngày nghỉ bù tự động từ mẫu lịch GENERATED (có chỉnh sửa)")
                            .build();
                    compensationDayRepository.save(compDay);
                }
            }
        }

        return appliedCount;
    }
}
