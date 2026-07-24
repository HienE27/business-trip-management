package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.SaveTemplateRequest;
import com.hospital.scheduler.dto.request.ScheduleTemplateRequest;
import com.hospital.scheduler.dto.response.ScheduleTemplateResponse;
import com.hospital.scheduler.dto.response.TemplatePreviewItem;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.entity.AuditHistory;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleTemplateService {

    private final ScheduleTemplateRepository templateRepository;
    private final ScheduleRepository scheduleRepository;
    private final com.hospital.scheduler.repository.CompensationDayRepository compensationDayRepository;
    private final com.hospital.scheduler.repository.LeaveRequestRepository leaveRequestRepository;
    private final CompensationDateCalculator compensationDateCalculator;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SpecialtyRepository specialtyRepository;
    private final SchedulePeriodRepository periodRepository;
    private final StaffRepository staffRepository;
    private final ObjectMapper objectMapper;
    // BUGFIX (was BE#13): inject audit dependencies so template CRUD writes
    // to audit_history. Without these, template mutations were invisible.
    private final AuditHistoryService auditHistoryService;
    private final com.hospital.scheduler.security.AuthContextService authContextService;
    // BUGFIX (template-pattern-apply): required to persist pattern slots as
    // shift_requirement rows so the count returned to the caller reflects
    // actual inserts (previously the method only incremented a counter and
    // told the user 'N ca được tạo' while writing nothing).
    private final ShiftRequirementRepository shiftRequirementRepository;

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
        // BUGFIX (was BE#13): createTemplate previously bypassed audit_history
        // entirely. Template mutations are admin/manager actions that must be
        // traceable. Log the INSERT with the new state.
        auditCreateTemplate(saved);
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

        // BUGFIX (was BE#13): capture pre-update snapshot for audit diff.
        ScheduleTemplate before = ScheduleTemplate.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .dayOfWeek(template.getDayOfWeek())
                .shiftTypeId(template.getShiftTypeId())
                .specialty(template.getSpecialty())
                .requiredStaffCount(template.getRequiredStaffCount())
                .isActive(template.getIsActive())
                .build();

        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setDayOfWeek(request.getDayOfWeek());
        template.setShiftTypeId(request.getShiftTypeId());
        template.setSpecialty(specialty);
        template.setRequiredStaffCount(request.getRequiredStaffCount() != null ? request.getRequiredStaffCount() : 1);

        ScheduleTemplateResponse response = ScheduleTemplateResponse.fromEntity(templateRepository.save(template));
        // BUGFIX (was BE#13): audit UPDATE with both snapshots.
        auditUpdateTemplate(template.getId(), before, template);
        return response;
    }

    public void deleteTemplate(Integer id) {
        ScheduleTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu lịch với ID: " + id));
        // BUGFIX (was BE#13): capture pre-delete snapshot before mutation.
        ScheduleTemplate before = ScheduleTemplate.builder()
                .id(template.getId())
                .name(template.getName())
                .description(template.getDescription())
                .dayOfWeek(template.getDayOfWeek())
                .shiftTypeId(template.getShiftTypeId())
                .specialty(template.getSpecialty())
                .requiredStaffCount(template.getRequiredStaffCount())
                .isActive(template.getIsActive())
                .build();
        template.setIsActive(false);
        templateRepository.save(template);
        // BUGFIX (was BE#13): audit DELETE (soft-delete via isActive=false).
        auditDeleteTemplate(template.getId(), before, template);
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

        // Handle PATTERN templates: extract day-of-week + shift-type → apply to every matching date
        if ("PATTERN".equals(template.getTemplateType()) && template.getPatternConfig() != null
                && !template.getPatternConfig().isBlank()) {
            return applyPatternTemplate(template, period);
        }

        if (template.getDayOfWeek() == null || template.getShiftTypeId() == null) {
            throw new BadRequestException(
                    "Mẫu lịch thiếu thông tin ngày trong tuần hoặc loại ca. Vui lòng chọn mẫu hợp lệ.");
        }

        Specialty specialty = template.getSpecialty();
        Integer specialtyId = specialty != null ? specialty.getId() : null;

        // BUGFIX (was BE#17): legacy single-weekday templates previously only
        // counted matching dates without inserting anything, so callers saw
        // "N ca được tạo" but the database stayed unchanged. Wrap the legacy
        // fields in a single-entry pattern and route through the same insert
        // path so the returned appliedCount always reflects actual rows.
        PatternEntry legacyEntry = new PatternEntry();
        legacyEntry.dayOfWeek = template.getDayOfWeek();
        legacyEntry.shiftTypeId = template.getShiftTypeId();
        legacyEntry.specialtyId = specialtyId;
        legacyEntry.requiredStaffCount = template.getRequiredStaffCount() != null
                ? template.getRequiredStaffCount()
                : 1;
        ScheduleTemplate wrapped = new ScheduleTemplate();
        wrapped.setId(template.getId());
        wrapped.setName(template.getName());
        wrapped.setPatternConfig(serializePatternConfig(java.util.List.of(legacyEntry)));
        return applyPatternTemplate(wrapped, period);
    }

    private String serializePatternConfig(java.util.List<PatternEntry> entries) {
        try {
            return new ObjectMapper().writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Không thể serialize pattern cũ: " + e.getMessage());
        }
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

        // Ensure all shift types referenced exist
        java.util.Set<String> referencedShiftTypeIds = sourceSchedules.stream()
                .map(s -> s.getShiftType().getId())
                .collect(java.util.stream.Collectors.toSet());
        for (String shiftTypeId : referencedShiftTypeIds) {
            if (shiftTypeRepository.findById(shiftTypeId).isEmpty()) {
                throw new BadRequestException("Loại ca " + shiftTypeId + " không tồn tại trong hệ thống");
            }
        }

        int appliedCount = 0;
        int skipped = 0;
        // Track staff/date slots already added in this apply pass so we don't produce
        // duplicate shifts when the source period carries multiple schedules for the
        // same staff/date/type combination (e.g. legacy data pre-V9 unique-constraint drop).
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (com.hospital.scheduler.entity.Schedule source : sourceSchedules) {
            // Validate that the source schedule's date still sits inside the target
            // period's date window — templates store concrete workDate, so copying a
            // May source into a June period would create shifts that lie completely
            // outside the target period. Skip + log instead of inserting junk data.
            LocalDate sourceDate = source.getWorkDate();
            if (sourceDate == null
                    || sourceDate.isBefore(period.getStartDate())
                    || sourceDate.isAfter(period.getEndDate())) {
                skipped++;
                continue;
            }

            String slotKey = source.getStaff().getId() + "|" + source.getShiftType().getId() + "|" + sourceDate;
            if (!taken.add(slotKey)) {
                skipped++;
                continue;
            }

            // Skip if schedule already exists (avoid duplicate constraint violation)
            if (scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                    period.getId(), source.getStaff().getId(), source.getShiftType().getId(), sourceDate).isPresent()) {
                skipped++;
                continue;
            }

            // BUGFIX (was M07 #11): the previous version inserted ANY source
            // schedule into the target period as long as the unique constraint
            // wasn't violated. That bypassed every business rule we care about:
            //   1. Staff has APPROVED/PENDING leave on this date — should skip.
            //   2. This date is the staff's compensation day — should skip.
            //   3. The staff already has a different same-day shift that conflicts
            //      with this one (L01 vs L02, or L03 vs L04 per Project Context
            //      CRITICAL constraints) — should skip.
            // Running these checks here keeps generated-template apply consistent
            // with how the manual and auto-scheduling paths validate before insert.
            if (hasApprovedOrPendingLeaveOn(source.getStaff().getId(), sourceDate)) {
                log.info("applyGeneratedTemplate skip staff={} date={}: có yêu cầu nghỉ phép APPROVED/PENDING",
                        source.getStaff().getId(), sourceDate);
                skipped++;
                continue;
            }
            if (isCompensationDayFor(source.getStaff().getId(), sourceDate)) {
                log.info("applyGeneratedTemplate skip staff={} date={}: trùng ngày nghỉ bù",
                        source.getStaff().getId(), sourceDate);
                skipped++;
                continue;
            }
            if (hasConflictingSameDayShift(source.getStaff().getId(), sourceDate,
                    source.getShiftType().getId(), taken)) {
                log.info("applyGeneratedTemplate skip staff={} date={} shift={}: đã có ca khác trong ngày xung đột",
                        source.getStaff().getId(), sourceDate, source.getShiftType().getId());
                skipped++;
                continue;
            }

            // NOTE: legacy path kept the same workDate because generated templates are
            // saved from the same period (the user re-runs auto-scheduling from a baseline
            // template). Cross-period templates were never supported here — see
            // extractPatternTemplate() for the legitimate pattern-based cross-period flow.
            com.hospital.scheduler.entity.Schedule copy = com.hospital.scheduler.entity.Schedule.builder()
                    .period(period)
                    .staff(source.getStaff())
                    .shiftType(source.getShiftType())
                    .workDate(sourceDate)
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
                    try {
                        compensationDayRepository.save(compDay);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        org.slf4j.LoggerFactory.getLogger(ScheduleTemplateService.class)
                                .warn("Compensation day already exists for staff {} on {}: {}", saved.getStaff().getId(), compDate, e.getMessage());
                    }
                }
            }
        }

        if (skipped > 0) {
            log.warn("applyGeneratedTemplate skipped {} out-of-window or duplicate schedules (period {})",
                    skipped, period.getId());
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

        // Handle PATTERN templates: preview from patternConfig
        if ("PATTERN".equals(template.getTemplateType()) && template.getPatternConfig() != null
                && !template.getPatternConfig().isBlank()) {
            return previewPatternTemplate(template, period);
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
                    .specialtyName(s.getStaff().getSpecialty() != null ? s.getStaff().getSpecialty().getName() : null)
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

        List<com.hospital.scheduler.entity.Schedule> sourceSchedules = List.of();
        if (request.getScheduleIds() != null && !request.getScheduleIds().isEmpty()) {
            // Priority 1: schedules from auto-schedule preview
            sourceSchedules = scheduleRepository.findAllById(request.getScheduleIds());
        }

        // Fallback: if no scheduleIds, get existing schedules from the period
        if (sourceSchedules.isEmpty()) {
            sourceSchedules = scheduleRepository.findByPeriodId(request.getPeriodId());
        }

        // Extract pattern data: group by (dayOfWeek, shiftTypeId, specialtyId) → requiredStaffCount.
        // The template is intended to be reusable across any period, so each pattern entry
        // describes how many staff we need on a SINGLE matching date (not the cumulative
        // count across the source period).
        //
        // BUGFIX (was M07 #13): the previous implementation used `patternMap.merge` to
        // sum ALL occurrences of (dow, shiftType, specialty) across the source period.
        // Example failure: a January source has 4 Mondays × 2 Nội khoa staff on L01 duty.
        // The merge would store `requiredStaffCount = 8` — meaning "every Monday needs
        // 8 Nội khoa on L01" — instead of the correct "each Monday needs 2 Nội khoa".
        //
        // New approach (single pass, O(N)):
        //   1. For each schedule, compute its (dow, shiftType, specialty) bucket key.
        //   2. Within the bucket, tally per-date totals: how many staff on THIS date match.
        //   3. The bucket's requiredStaffCount = the MODE of those per-date totals
        //      (most common per-date staffing for this dow+shift+specialty combo).
        java.util.Map<String, int[]> bucketMeta = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Map<LocalDate, int[]>> perBucketPerDate = new java.util.LinkedHashMap<>();
        for (com.hospital.scheduler.entity.Schedule s : sourceSchedules) {
            int dow = s.getWorkDate().getDayOfWeek().getValue();
            String shiftId = s.getShiftType().getId();
            Integer specId = s.getStaff().getSpecialty() != null ? s.getStaff().getSpecialty().getId() : null;
            String key = dow + "_" + shiftId + "_" + (specId != null ? specId : "null");

            if (!bucketMeta.containsKey(key)) {
                bucketMeta.put(key, new int[]{ dow, 0 /* placeholder */ });
                perBucketPerDate.put(key, new java.util.LinkedHashMap<>());
                bucketMeta.get(key)[0] = dow;
            }
            perBucketPerDate.get(key)
                    .computeIfAbsent(s.getWorkDate(), d -> new int[1])[0]++;
        }

        java.util.Map<String, PatternEntry> patternMap = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, java.util.Map<LocalDate, int[]>> e : perBucketPerDate.entrySet()) {
            String key = e.getKey();
            java.util.Map<LocalDate, int[]> perDate = e.getValue();
            int mode = pickMode(perDate.values());

            // Recover bucket metadata (dow / shiftType / specialty) from a representative schedule.
            com.hospital.scheduler.entity.Schedule sample = sourceSchedules.stream()
                    .filter(s -> (s.getWorkDate().getDayOfWeek().getValue() + "_" + s.getShiftType().getId()
                            + "_" + (s.getStaff().getSpecialty() != null
                                    ? s.getStaff().getSpecialty().getId() : "null")).equals(key))
                    .findFirst().orElse(null);
            if (sample == null) continue;

            Integer specId = sample.getStaff().getSpecialty() != null
                    ? sample.getStaff().getSpecialty().getId() : null;
            PatternEntry entry = new PatternEntry(
                    sample.getWorkDate().getDayOfWeek().getValue(),
                    sample.getShiftType().getId(),
                    specId,
                    mode);
            patternMap.put(key, entry);
        }

        if (patternMap.isEmpty()) {
            throw new BadRequestException(
                    "Không có lịch nào để tạo mẫu. Vui lòng thêm lịch trực trong kỳ này trước.");
        }

        String patternConfigJson = null;
        try {
            patternConfigJson = objectMapper.writeValueAsString(
                    patternMap.values().stream().toList());
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Lỗi khi serialize cấu hình pattern: " + e.getMessage());
        }

        // Save schedule IDs as a GENERATED companion template (for reference / undo)
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
                // Primary: PATTERN — stores day-of-week + shift-type + count → fully reusable
                .templateType("PATTERN")
                // Backward-compatible: also store schedule IDs as a reference for undo/history
                .generatedScheduleIds(scheduleIdsJson)
                // Pattern extraction: store first pattern entry (caller can split if multiple types needed)
                .patternConfig(patternConfigJson)
                .isActive(true)
                .build();

        ScheduleTemplate saved = templateRepository.save(template);
        return ScheduleTemplateResponse.fromEntity(saved);
    }

    /**
     * Internal record for pattern extraction.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties
    private static class PatternEntry {
        @com.fasterxml.jackson.annotation.JsonProperty
        int dayOfWeek;
        @com.fasterxml.jackson.annotation.JsonProperty
        String shiftTypeId;
        @com.fasterxml.jackson.annotation.JsonProperty
        Integer specialtyId;
        @com.fasterxml.jackson.annotation.JsonProperty
        int requiredStaffCount;

        // Jackson yêu cầu default constructor để deserialize
        PatternEntry() {
        }

        PatternEntry(int dayOfWeek, String shiftTypeId, Integer specialtyId, int requiredStaffCount) {
            this.dayOfWeek = dayOfWeek;
            this.shiftTypeId = shiftTypeId;
            this.specialtyId = specialtyId;
            this.requiredStaffCount = requiredStaffCount;
        }
    }

    /**
     * Pick the most common value from a collection of int[] singletons. If there
     * is a tie, the smallest value wins (consistent & deterministic). Returns 0
     * for an empty input.
     *
     * <p>BUGFIX (was M07 #13) helper: the original pattern extractor summed all
     * occurrences — fixing the bucket count requires selecting the modal per-date
     * staffing rather than the cumulative total.
     */
    private static int pickMode(java.util.Collection<int[]> values) {
        if (values == null || values.isEmpty()) return 0;
        java.util.Map<Integer, Integer> freq = new java.util.HashMap<>();
        for (int[] arr : values) {
            int v = arr[0];
            freq.merge(v, 1, Integer::sum);
        }
        int bestVal = 0;
        int bestCount = -1;
        for (java.util.Map.Entry<Integer, Integer> e : freq.entrySet()) {
            int v = e.getKey();
            int c = e.getValue();
            if (c > bestCount || (c == bestCount && v < bestVal)) {
                bestVal = v;
                bestCount = c;
            }
        }
        return bestVal;
    }

    // ─── Audit helpers (BE#13) ──────────────────────────────────────────────────
    // Template CRUD previously bypassed audit_history entirely. These helpers
    // log INSERT/UPDATE/DELETE entries with full before/after snapshots so
    // administrators can answer "who changed template X and how".
    private void auditCreateTemplate(ScheduleTemplate saved) {
        safeAudit("schedule_template", saved.getId(),
                AuditHistory.ActionType.INSERT, null, saved);
    }

    private void auditUpdateTemplate(Integer id, ScheduleTemplate before, ScheduleTemplate after) {
        safeAudit("schedule_template", id,
                AuditHistory.ActionType.UPDATE, before, after);
    }

    private void auditDeleteTemplate(Integer id, ScheduleTemplate before, ScheduleTemplate after) {
        safeAudit("schedule_template", id,
                AuditHistory.ActionType.DELETE, before, after);
    }

    private void safeAudit(String table, Integer recordId,
                           AuditHistory.ActionType action,
                           Object oldValue, Object newValue) {
        try {
            Integer actorId = authContextService.getCurrentStaff().getId();
            auditHistoryService.logAction(table, recordId, action, oldValue, newValue, actorId);
        } catch (Exception ex) {
            // Never fail a business transaction because auditing hiccupped.
            log.warn("safeAudit({}/{} {}) skipped: {}", table, recordId, action, ex.getMessage());
        }
    }

    // ─── M07 #11 helpers ────────────────────────────────────────────────────────
    // Generated-template apply previously bypassed every business rule we enforce
    // for the manual & auto-scheduling paths. These helpers let applyGeneratedTemplate
    // consult the same checks before inserting a copied schedule. All helpers are
    // read-only and silent on failure (return false) so a transient DB hiccup
    // doesn't cascade into the apply pass.

    private boolean hasApprovedOrPendingLeaveOn(Integer staffId, LocalDate workDate) {
        try {
            java.util.List<com.hospital.scheduler.entity.LeaveRequest> leaves =
                    leaveRequestRepository.findByStaffIdAndDateRange(staffId, workDate, workDate);
            return leaves.stream().anyMatch(lr ->
                    lr.getStatus() == com.hospital.scheduler.entity.LeaveRequest.LeaveStatus.APPROVED
                            || lr.getStatus() == com.hospital.scheduler.entity.LeaveRequest.LeaveStatus.PENDING);
        } catch (Exception ex) {
            log.warn("hasApprovedOrPendingLeaveOn({}, {}) failed: {}", staffId, workDate, ex.getMessage());
            return false;
        }
    }

    private boolean isCompensationDayFor(Integer staffId, LocalDate workDate) {
        try {
            return compensationDayRepository.existsByStaffIdAndCompensationDate(staffId, workDate);
        } catch (Exception ex) {
            log.warn("isCompensationDayFor({}, {}) failed: {}", staffId, workDate, ex.getMessage());
            return false;
        }
    }

    /**
     * Project Context CRITICAL constraint: the same staff cannot be on two
     * shifts that conflict on the same day. Concretely:
     * <ul>
     *   <li>L01 (24/24 duty, overnight) vs L02 (thông tầm) — mutually exclusive.</li>
     *   <li>L03 (PK dịch vụ) vs L04 (PK chuyên gia) — mutually exclusive.</li>
     * </ul>
     * The simplest signal is "any other shift on the same date for this staff
     * within the target period", which covers both rules since each rule only
     * fires when the conflicting shift type would already be on the day.
     *
     * <p>{@code takenShiftsForSession} carries the in-progress slots from the
     * current apply pass so we don't insert two conflicting shifts before
     * persisting either.
     */
    private boolean hasConflictingSameDayShift(Integer staffId, LocalDate workDate,
                                               String newShiftTypeId,
                                               java.util.Set<String> takenShiftsForSession) {
        try {
            java.util.List<com.hospital.scheduler.entity.Schedule> existing =
                    scheduleRepository.findByStaffIdAndWorkDate(staffId, workDate);
            for (com.hospital.scheduler.entity.Schedule s : existing) {
                if (!s.getShiftType().getId().equals(newShiftTypeId)) {
                    return true;
                }
            }
            // In-progress slots from the same apply pass (avoid inserting two same-day
            // shifts back-to-back).
            if (takenShiftsForSession != null) {
                String slotKey = staffId + "|" + workDate;
                for (String taken : takenShiftsForSession) {
                    String[] parts = taken.split("\\|");
                    if (parts.length >= 3
                            && parts[0].equals(String.valueOf(staffId))
                            && parts[2].equals(workDate.toString())
                            && !parts[1].equals(newShiftTypeId)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("hasConflictingSameDayShift({}, {}, {}) failed: {}",
                    staffId, workDate, newShiftTypeId, ex.getMessage());
            return false;
        }
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

        if (!"GENERATED".equals(template.getTemplateType()) && !"PATTERN".equals(template.getTemplateType())) {
            throw new BadRequestException("Chỉ hỗ trợ áp dụng mẫu GENERATED hoặc PATTERN.");
        }

        // PATTERN templates: use applyPatternTemplate directly
        if ("PATTERN".equals(template.getTemplateType())) {
            return applyPatternTemplateForWithEdits(request, template, period);
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

            // Skip if schedule already exists (avoid duplicate constraint violation)
            if (scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                    period.getId(), targetStaff.getId(), source.getShiftType().getId(), source.getWorkDate()).isPresent()) {
                continue;
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
                    try {
                        compensationDayRepository.save(compDay);
                    } catch (org.springframework.dao.DataIntegrityViolationException e) {
                        org.slf4j.LoggerFactory.getLogger(ScheduleTemplateService.class)
                                .warn("Compensation day already exists for staff {} on {}: {}", saved.getStaff().getId(), compDate, e.getMessage());
                    }
                }
            }
        }

        return appliedCount;
    }

    /**
     * Apply a PATTERN template: deserialize patternConfig (list of dayOfWeek+shiftTypeId+specialty+count),
     * iterate through the target period, and for each date that matches a pattern entry,
     * create a ShiftRequirement for every required slot.
     */
    private int applyPatternTemplate(ScheduleTemplate template,
                                     com.hospital.scheduler.entity.SchedulePeriod period) {
        ObjectMapper mapper = new ObjectMapper();
        List<PatternEntry> entries;
        try {
            entries = mapper.readValue(template.getPatternConfig(),
                    mapper.getTypeFactory().constructCollectionType(List.class, PatternEntry.class));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Không thể đọc cấu hình pattern từ mẫu: " + e.getMessage());
        }

        // OPTIMIZATION: pre-load all ShiftTypes and Specialties in ONE query each
        java.util.Set<String> shiftTypeIds = entries.stream().map(e -> e.shiftTypeId).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Integer> specialtyIds = entries.stream().map(e -> e.specialtyId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, ShiftType> shiftTypeMap = shiftTypeRepository.findAllById(shiftTypeIds).stream()
                .collect(java.util.stream.Collectors.toMap(ShiftType::getId, s -> s));
        java.util.Map<Integer, Specialty> specialtyMap = specialtyIds.isEmpty() ? java.util.Collections.emptyMap()
                : specialtyRepository.findAllById(specialtyIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Specialty::getId, s -> s));

        // BUGFIX (template-pattern-apply): the legacy code only incremented a
        // counter and told the caller 'N ca được tạo' while writing nothing.
        // Now we insert a shift_requirement row per matching slot so the returned
        // count reflects actual DB state. Existing rows are detected via
        // uk_shift_requirement_unique to skip duplicates without relying on
        // throwing DataIntegrityViolationException.
        java.util.Set<String> existingKeys = new java.util.HashSet<>();
        for (ShiftRequirement existing : shiftRequirementRepository.findByPeriodId(period.getId())) {
            existingKeys.add(reqKey(existing.getWorkDate(),
                    existing.getShiftType().getId(),
                    existing.getSpecialty() != null ? existing.getSpecialty().getId() : null));
        }

        int appliedCount = 0;
        int skipped = 0;
        LocalDate current = period.getStartDate();

        while (!current.isAfter(period.getEndDate())) {
            int dow = current.getDayOfWeek().getValue();
            for (PatternEntry entry : entries) {
                if (entry.dayOfWeek == dow) {
                    Specialty specialty = entry.specialtyId != null ? specialtyMap.get(entry.specialtyId) : null;
                    ShiftType shiftType = shiftTypeMap.get(entry.shiftTypeId);
                    if (shiftType == null) continue;

                    String key = reqKey(current, entry.shiftTypeId,
                            specialty != null ? specialty.getId() : null);
                    if (!existingKeys.add(key)) {
                        skipped++;
                        continue;
                    }

                    ShiftRequirement req = ShiftRequirement.builder()
                            .period(period)
                            .workDate(current)
                            .shiftType(shiftType)
                            .specialty(specialty)
                            .requiredStaffCount(entry.requiredStaffCount)
                            .note("Tự động tạo từ mẫu lịch PATTERN")
                            .build();
                    shiftRequirementRepository.save(req);
                    appliedCount++;
                }
            }
            current = current.plusDays(1);
        }

        log.info("applyPatternTemplate template={} period={}: inserted {} requirement rows, skipped {} duplicates",
                template.getId(), period.getId(), appliedCount, skipped);
        return appliedCount;
    }

    private static String reqKey(LocalDate date, String shiftTypeId, Integer specialtyId) {
        return date + "|" + shiftTypeId + "|" + (specialtyId == null ? "_" : specialtyId);
    }

    /**
     * Apply a PATTERN template with edits support.
     * Edits can change the required staff count for specific pattern entries.
     */
    private int applyPatternTemplateForWithEdits(
            com.hospital.scheduler.dto.request.TemplateApplyWithEditsRequest request,
            ScheduleTemplate template,
            com.hospital.scheduler.entity.SchedulePeriod period) {

        if (template.getPatternConfig() == null || template.getPatternConfig().isBlank()) {
            return 0;
        }

        ObjectMapper mapper = new ObjectMapper();
        List<PatternEntry> entries;
        try {
            entries = mapper.readValue(template.getPatternConfig(),
                    mapper.getTypeFactory().constructCollectionType(List.class, PatternEntry.class));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Không thể đọc cấu hình pattern từ mẫu: " + e.getMessage());
        }

        // Build edit lookup: patternEntryKey (dayOfWeek_shiftTypeId) -> newCount
        java.util.Map<String, Integer> editMap = new java.util.HashMap<>();
        if (request.getEdits() != null) {
            for (var edit : request.getEdits()) {
                String key = String.valueOf(edit.getSlotId());
                if (key.contains("_")) {
                    editMap.put(key, edit.getAssignedStaffId());
                }
            }
        }

        // OPTIMIZATION: pre-load all ShiftTypes and Specialties
        java.util.Set<String> shiftTypeIds = entries.stream().map(e -> e.shiftTypeId).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Integer> specialtyIds = entries.stream().map(e -> e.specialtyId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, ShiftType> shiftTypeMap = shiftTypeRepository.findAllById(shiftTypeIds).stream()
                .collect(java.util.stream.Collectors.toMap(ShiftType::getId, s -> s));
        java.util.Map<Integer, Specialty> specialtyMap = specialtyIds.isEmpty() ? java.util.Collections.emptyMap()
                : specialtyRepository.findAllById(specialtyIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Specialty::getId, s -> s));

        // BUGFIX (template-pattern-apply): same fix as applyPatternTemplate —
        // persist shift_requirement rows so the returned appliedCount reflects
        // actual inserts instead of being a synthetic slot count.
        java.util.Set<String> existingKeys = new java.util.HashSet<>();
        for (ShiftRequirement existing : shiftRequirementRepository.findByPeriodId(period.getId())) {
            existingKeys.add(reqKey(existing.getWorkDate(),
                    existing.getShiftType().getId(),
                    existing.getSpecialty() != null ? existing.getSpecialty().getId() : null));
        }

        int appliedCount = 0;
        int skipped = 0;
        LocalDate current = period.getStartDate();

        while (!current.isAfter(period.getEndDate())) {
            int dow = current.getDayOfWeek().getValue();
            for (int i = 0; i < entries.size(); i++) {
                PatternEntry entry = entries.get(i);
                if (entry.dayOfWeek == dow) {
                    Specialty specialty = entry.specialtyId != null ? specialtyMap.get(entry.specialtyId) : null;
                    ShiftType shiftType = shiftTypeMap.get(entry.shiftTypeId);
                    if (shiftType == null) continue;

                    // Get staff count from edit or use default
                    int requiredCount = entry.requiredStaffCount;
                    String editKey = dow + "_" + entry.shiftTypeId;
                    if (editMap.containsKey(editKey)) {
                        requiredCount = editMap.get(editKey);
                    }

                    // Skip if count is 0 or negative
                    if (requiredCount <= 0) continue;

                    String key = reqKey(current, entry.shiftTypeId,
                            specialty != null ? specialty.getId() : null);
                    if (!existingKeys.add(key)) {
                        skipped++;
                        continue;
                    }

                    ShiftRequirement req = ShiftRequirement.builder()
                            .period(period)
                            .workDate(current)
                            .shiftType(shiftType)
                            .specialty(specialty)
                            .requiredStaffCount(requiredCount)
                            .note("Tự động tạo từ mẫu lịch PATTERN (có chỉnh sửa)")
                            .build();
                    shiftRequirementRepository.save(req);
                    appliedCount++;
                }
            }
            current = current.plusDays(1);
        }

        log.info("applyPatternTemplateForWithEdits template={} period={}: inserted {} requirement rows, skipped {} duplicates",
                template.getId(), period.getId(), appliedCount, skipped);
        return appliedCount;
    }

    /**
     * Preview a PATTERN template: deserialize patternConfig and list every matching date
     * in the target period without creating anything.
     */
    private List<TemplatePreviewItem> previewPatternTemplate(ScheduleTemplate template,
                                                            com.hospital.scheduler.entity.SchedulePeriod period) {
        ObjectMapper mapper = new ObjectMapper();
        List<PatternEntry> entries;
        try {
            entries = mapper.readValue(template.getPatternConfig(),
                    mapper.getTypeFactory().constructCollectionType(List.class, PatternEntry.class));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Không thể đọc cấu hình pattern từ mẫu: " + e.getMessage());
        }

        // OPTIMIZATION: pre-load all ShiftTypes and Specialties in ONE query each
        java.util.Set<String> shiftTypeIds = entries.stream().map(e -> e.shiftTypeId).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Integer> specialtyIds = entries.stream().map(e -> e.specialtyId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, ShiftType> shiftTypeMap = shiftTypeRepository.findAllById(shiftTypeIds).stream()
                .collect(java.util.stream.Collectors.toMap(ShiftType::getId, s -> s));
        java.util.Map<Integer, Specialty> specialtyMap = specialtyIds.isEmpty() ? java.util.Collections.emptyMap()
                : specialtyRepository.findAllById(specialtyIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Specialty::getId, s -> s));

        List<TemplatePreviewItem> items = new ArrayList<>();
        LocalDate current = period.getStartDate();

        while (!current.isAfter(period.getEndDate())) {
            int dow = current.getDayOfWeek().getValue();
            for (PatternEntry entry : entries) {
                if (entry.dayOfWeek == dow) {
                    ShiftType shiftType = shiftTypeMap.get(entry.shiftTypeId);
                    String specialtyName = null;
                    if (entry.specialtyId != null) {
                        Specialty specialty = specialtyMap.get(entry.specialtyId);
                        if (specialty != null) specialtyName = specialty.getName();
                    }
                    int dowValue = current.getDayOfWeek().getValue();
                    items.add(TemplatePreviewItem.builder()
                            .id(0)
                            .workDate(current.toString())
                            .dayOfWeek(VIETNAMESE_DAYS[dowValue])
                            .shiftTypeId(entry.shiftTypeId)
                            .shiftTypeName(shiftType != null ? shiftType.getName() : entry.shiftTypeId)
                            .specialtyName(specialtyName)
                            .requiredStaffCount(entry.requiredStaffCount)
                            .build());
                }
            }
            current = current.plusDays(1);
        }

        return items;
    }
}
