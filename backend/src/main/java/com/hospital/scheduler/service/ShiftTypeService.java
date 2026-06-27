package com.hospital.scheduler.service;

import com.hospital.scheduler.config.CacheConfig;
import com.hospital.scheduler.dto.request.ShiftTypeRequest;
import com.hospital.scheduler.dto.response.ShiftTypeResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.security.AuthContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ShiftTypeService {

    private final ShiftTypeRepository shiftTypeRepository;
    private final AuditHistoryService auditHistoryService;
    private final AuthContextService authContextService;

    @Cacheable(value = CacheConfig.SHIFT_TYPES_CACHE)
    @Transactional(readOnly = true)
    public List<ShiftTypeResponse> getAllShiftTypes() {
        return shiftTypeRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ShiftTypeResponse> getActiveShiftTypes() {
        return shiftTypeRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ShiftTypeResponse getShiftTypeById(String id) {
        ShiftType shiftType = shiftTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + id));
        return toResponse(shiftType);
    }

    @CacheEvict(value = CacheConfig.SHIFT_TYPES_CACHE, allEntries = true)
    public ShiftTypeResponse createShiftType(ShiftTypeRequest request) {
        if (shiftTypeRepository.existsById(request.getId())) {
            throw new ConflictException("Loại ca '" + request.getId() + "' đã tồn tại");
        }

        ShiftType shiftType = ShiftType.builder()
                .id(request.getId())
                .name(request.getName())
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .isOvernight(request.getIsOvernight() != null ? request.getIsOvernight() : false)
                .fatigueScore(request.getFatigueScore() != null ? request.getFatigueScore() : 1)
                .isActive(true)
                .build();

        ShiftType saved = shiftTypeRepository.save(shiftType);

        auditHistoryService.logAction("shift_type", saved.getId(), AuditHistory.ActionType.INSERT,
                null, saved, authContextService.getCurrentStaff().getId());

        return toResponse(saved);
    }

    @CacheEvict(value = CacheConfig.SHIFT_TYPES_CACHE, allEntries = true)
    public ShiftTypeResponse updateShiftType(String id, ShiftTypeRequest request) {
        ShiftType shiftType = shiftTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + id));

        shiftType.setName(request.getName());
        shiftType.setDescription(request.getDescription());
        shiftType.setStartTime(request.getStartTime());
        shiftType.setEndTime(request.getEndTime());
        if (request.getIsOvernight() != null) {
            shiftType.setIsOvernight(request.getIsOvernight());
        }
        if (request.getFatigueScore() != null) {
            shiftType.setFatigueScore(request.getFatigueScore());
        }

        ShiftType oldShiftType = ShiftType.builder()
                .id(shiftType.getId())
                .name(shiftType.getName())
                .description(shiftType.getDescription())
                .startTime(shiftType.getStartTime())
                .endTime(shiftType.getEndTime())
                .isOvernight(shiftType.getIsOvernight())
                .fatigueScore(shiftType.getFatigueScore())
                .build();

        ShiftType saved = shiftTypeRepository.save(shiftType);

        auditHistoryService.logAction("shift_type", saved.getId(), AuditHistory.ActionType.UPDATE,
                oldShiftType, saved, authContextService.getCurrentStaff().getId());

        return toResponse(saved);
    }

    @CacheEvict(value = CacheConfig.SHIFT_TYPES_CACHE, allEntries = true)
    public void deleteShiftType(String id) {
        ShiftType shiftType = shiftTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại ca với ID: " + id));

        auditHistoryService.logAction("shift_type", id, AuditHistory.ActionType.DELETE,
                shiftType, null, authContextService.getCurrentStaff().getId());

        shiftType.setIsActive(false);
        shiftTypeRepository.save(shiftType);
    }

    private ShiftTypeResponse toResponse(ShiftType shiftType) {
        return ShiftTypeResponse.builder()
                .id(shiftType.getId())
                .name(shiftType.getName())
                .description(shiftType.getDescription())
                .startTime(shiftType.getStartTime())
                .endTime(shiftType.getEndTime())
                .isOvernight(shiftType.getIsOvernight())
                .fatigueScore(shiftType.getFatigueScore())
                .isActive(shiftType.getIsActive())
                .createdAt(shiftType.getCreatedAt())
                .updatedAt(shiftType.getUpdatedAt())
                .build();
    }
}
