package com.hospital.scheduler.service;

import com.hospital.scheduler.config.CacheConfig;
import com.hospital.scheduler.dto.request.ShiftRequirementDTO;
import com.hospital.scheduler.dto.response.ShiftRequirementResponse;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.service.AuditHistoryService;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ShiftRequirementService {

    private final ShiftRequirementRepository requirementRepository;
    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository periodRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AuditHistoryService auditHistoryService;

    @Cacheable(value = CacheConfig.REQUIREMENTS_CACHE, key = "'all_requirements'")
    public List<ShiftRequirementResponse> getAllRequirements() {
        List<ShiftRequirement> requirements = requirementRepository.findAll();
        
        // OPTIMIZATION: batch load all counts in ONE query
        Map<String, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : scheduleRepository.countGroupedByPeriodWorkDateShiftType()) {
            Integer pid = (Integer) row[0];
            LocalDate date = (LocalDate) row[1];
            String shiftTypeId = (String) row[2];
            Long cnt = (Long) row[3];
            countMap.put(pid + "_" + date + "_" + shiftTypeId, cnt);
        }

        return requirements.stream()
                .map(req -> {
                    long count = countMap.getOrDefault(
                            req.getPeriod().getId() + "_" + req.getWorkDate() + "_" + req.getShiftType().getId(), 0L);
                    return ShiftRequirementResponse.fromEntityWithAssignedCount(req, count);
                })
                .collect(Collectors.toList());
    }

    public Page<ShiftRequirementResponse> getAllRequirements(Pageable pageable) {
        Page<ShiftRequirement> requirements = requirementRepository.findAll(pageable);

        // OPTIMIZATION: batch load all counts in ONE query
        Map<String, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : scheduleRepository.countGroupedByPeriodWorkDateShiftType()) {
            Integer pid = (Integer) row[0];
            LocalDate date = (LocalDate) row[1];
            String shiftTypeId = (String) row[2];
            Long cnt = (Long) row[3];
            countMap.put(pid + "_" + date + "_" + shiftTypeId, cnt);
        }

        return requirements.map(req -> {
            long count = countMap.getOrDefault(
                    req.getPeriod().getId() + "_" + req.getWorkDate() + "_" + req.getShiftType().getId(), 0L);
            return ShiftRequirementResponse.fromEntityWithAssignedCount(req, count);
        });
    }

    public List<ShiftRequirementResponse> getRequirementsByPeriod(Integer periodId) {
        List<ShiftRequirement> requirements = requirementRepository.findByPeriodId(periodId);
        // OPTIMIZATION: batch load all counts in ONE query
        Map<String, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : scheduleRepository.countGroupedByPeriodWorkDateShiftType(periodId)) {
            Integer pid = (Integer) row[0];
            LocalDate date = (LocalDate) row[1];
            String shiftTypeId = (String) row[2];
            Long cnt = (Long) row[3];
            countMap.put(pid + "_" + date + "_" + shiftTypeId, cnt);
        }

        return requirements.stream()
                .map(req -> {
                    long count = countMap.getOrDefault(
                            req.getPeriod().getId() + "_" + req.getWorkDate() + "_" + req.getShiftType().getId(), 0L);
                    return ShiftRequirementResponse.fromEntityWithAssignedCount(req, count);
                })
                .collect(Collectors.toList());
    }

    public List<ShiftRequirementResponse> getRequirementsByPeriodAndDate(Integer periodId, LocalDate date) {
        List<ShiftRequirement> requirements = requirementRepository.findByPeriodIdAndWorkDate(periodId, date);
        // OPTIMIZATION: use batch query instead of N individual count queries
        if (requirements.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Map<String, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : scheduleRepository.countGroupedByPeriodWorkDateShiftType(periodId)) {
            Integer pid = (Integer) row[0];
            LocalDate d = (LocalDate) row[1];
            String shiftTypeId = (String) row[2];
            Long cnt = (Long) row[3];
            countMap.put(pid + "_" + d + "_" + shiftTypeId, cnt);
        }

        return requirements.stream()
                .map(req -> {
                    long count = countMap.getOrDefault(
                            req.getPeriod().getId() + "_" + req.getWorkDate() + "_" + req.getShiftType().getId(), 0L);
                    return ShiftRequirementResponse.fromEntityWithAssignedCount(req, count);
                })
                .collect(Collectors.toList());
    }

    public List<ShiftRequirementResponse> getRequirementsByPeriodAndDateRange(Integer periodId, LocalDate startDate, LocalDate endDate) {
        List<ShiftRequirement> requirements = requirementRepository.findByPeriodIdAndDateRange(periodId, startDate, endDate);
        // OPTIMIZATION: batch load all counts in ONE query
        Map<String, Long> countMap = new java.util.HashMap<>();
        for (Object[] row : scheduleRepository.countGroupedByPeriodWorkDateShiftType(periodId)) {
            Integer pid = (Integer) row[0];
            LocalDate date = (LocalDate) row[1];
            String shiftTypeId = (String) row[2];
            Long cnt = (Long) row[3];
            countMap.put(pid + "_" + date + "_" + shiftTypeId, cnt);
        }

        return requirements.stream()
                .map(req -> {
                    long count = countMap.getOrDefault(
                            req.getPeriod().getId() + "_" + req.getWorkDate() + "_" + req.getShiftType().getId(), 0L);
                    return ShiftRequirementResponse.fromEntityWithAssignedCount(req, count);
                })
                .collect(Collectors.toList());
    }

    public ShiftRequirementResponse getRequirementById(Integer id) {
        ShiftRequirement requirement = requirementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu nhân sự với ID: " + id));
        long count = scheduleRepository.countByPeriodIdAndWorkDateAndShiftTypeId(
                requirement.getPeriod().getId(), requirement.getWorkDate(), requirement.getShiftType().getId());
        return ShiftRequirementResponse.fromEntityWithAssignedCount(requirement, count);
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

        // FIX: Check for duplicate before saving to avoid uk_requirement_unique violation
        requirementRepository.findByPeriodIdAndWorkDateAndShiftTypeIdAndSpecialtyId(
                period.getId(), dto.getWorkDate(), dto.getShiftTypeId(), dto.getSpecialtyId())
                .ifPresent(existing -> {
                    throw new BadRequestException(
                            "Yêu cầu nhân sự đã tồn tại cho ngày " + dto.getWorkDate() +
                            ", ca " + dto.getShiftTypeId() +
                            ", chuyên khoa ID " + dto.getSpecialtyId());
                });

        ShiftRequirement requirement = ShiftRequirement.builder()
                .period(period)
                .workDate(dto.getWorkDate())
                .shiftType(shiftType)
                .specialty(specialty)
                .requiredStaffCount(dto.getRequiredStaffCount())
                .note(dto.getNote())
                .build();

        ShiftRequirement saved = requirementRepository.save(requirement);
        auditHistoryService.logAction("shift_requirement", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, null);
        evictRequirementsCache();
        return ShiftRequirementResponse.fromEntityWithAssignedCount(saved, 0);
    }

    @CacheEvict(value = CacheConfig.REQUIREMENTS_CACHE, allEntries = true)
    public void evictRequirementsCache() {
        // Cache evicted automatically by @CacheEvict
    }

    public ShiftRequirementResponse updateRequirement(Integer id, ShiftRequirementDTO dto) {
        ShiftRequirement existing = requirementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu nhân sự với ID: " + id));

        if (existing.getPeriod().getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể cập nhật yêu cầu nhân sự khi kỳ lịch ở trạng thái DRAFT");
        }

        if (dto.getWorkDate().isBefore(existing.getPeriod().getStartDate()) || dto.getWorkDate().isAfter(existing.getPeriod().getEndDate())) {
            throw new BadRequestException("Ngày làm việc phải nằm trong kỳ lịch");
        }

        ShiftType shiftType = shiftTypeRepository.findById(dto.getShiftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + dto.getShiftTypeId()));

        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên môn với ID: " + dto.getSpecialtyId()));

        existing.setWorkDate(dto.getWorkDate());
        existing.setShiftType(shiftType);
        existing.setSpecialty(specialty);
        existing.setRequiredStaffCount(dto.getRequiredStaffCount());
        existing.setNote(dto.getNote());

        ShiftRequirement saved = requirementRepository.save(existing);
        auditHistoryService.logAction("shift_requirement", saved.getId(), AuditHistory.ActionType.UPDATE, existing, saved, null);
        evictRequirementsCache();

        long count = scheduleRepository.countByPeriodIdAndWorkDateAndShiftTypeId(
                saved.getPeriod().getId(), saved.getWorkDate(), saved.getShiftType().getId());
        return ShiftRequirementResponse.fromEntityWithAssignedCount(saved, count);
    }

    @CacheEvict(value = CacheConfig.REQUIREMENTS_CACHE, allEntries = true)
    public void deleteRequirement(Integer id) {
        ShiftRequirement existing = requirementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu nhân sự với ID: " + id));

        if (existing.getPeriod().getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xóa yêu cầu nhân sự khi kỳ lịch ở trạng thái DRAFT");
        }

        auditHistoryService.logAction("shift_requirement", id, AuditHistory.ActionType.DELETE, existing, null, null);
        requirementRepository.delete(existing);
    }
}
