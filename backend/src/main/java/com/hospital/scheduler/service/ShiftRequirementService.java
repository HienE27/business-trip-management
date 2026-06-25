package com.hospital.scheduler.service;

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

    public List<ShiftRequirementResponse> getAllRequirements() {
        List<ShiftRequirement> requirements = requirementRepository.findAll();
        // OPTIMIZATION: batch load all counts in ONE query per period, then per-period lookup
        // Group requirements by periodId for efficient count loading
        Map<Integer, List<ShiftRequirement>> byPeriod = requirements.stream()
                .collect(Collectors.groupingBy(r -> r.getPeriod().getId()));

        // Load counts per period
        Map<Integer, Map<String, Long>> countMaps = new java.util.HashMap<>();
        for (Integer periodId : byPeriod.keySet()) {
            Map<String, Long> countMap = new java.util.HashMap<>();
            for (Object[] row : scheduleRepository.countGroupedByPeriodWorkDateShiftType(periodId)) {
                Integer pid = (Integer) row[0];
                LocalDate date = (LocalDate) row[1];
                String shiftTypeId = (String) row[2];
                Long cnt = (Long) row[3];
                countMap.put(pid + "_" + date + "_" + shiftTypeId, cnt);
            }
            countMaps.put(periodId, countMap);
        }

        return requirements.stream()
                .map(req -> {
                    Map<String, Long> countMap = countMaps.get(req.getPeriod().getId());
                    long count = countMap != null ? countMap.getOrDefault(
                            req.getPeriod().getId() + "_" + req.getWorkDate() + "_" + req.getShiftType().getId(), 0L) : 0L;
                    return ShiftRequirementResponse.fromEntityWithAssignedCount(req, count);
                })
                .collect(Collectors.toList());
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
        return requirementRepository.findByPeriodIdAndWorkDate(periodId, date).stream()
                .map(req -> {
                    long count = scheduleRepository.countByPeriodIdAndWorkDateAndShiftTypeId(
                            periodId, date, req.getShiftType().getId());
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
        return ShiftRequirementResponse.fromEntityWithAssignedCount(saved, 0);
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

        long count = scheduleRepository.countByPeriodIdAndWorkDateAndShiftTypeId(
                saved.getPeriod().getId(), saved.getWorkDate(), saved.getShiftType().getId());
        return ShiftRequirementResponse.fromEntityWithAssignedCount(saved, count);
    }

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
