package com.hospital.scheduler.service;

import com.hospital.scheduler.config.CacheConfig;
import com.hospital.scheduler.dto.request.SpecialtyRequest;
import com.hospital.scheduler.dto.response.SpecialtyResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.SpecialtyRepository;
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
public class SpecialtyService {

    private final SpecialtyRepository specialtyRepository;
    private final AuditHistoryService auditHistoryService;
    private final AuthContextService authContextService;

    @Cacheable(value = CacheConfig.SPECIALTIES_CACHE)
    @Transactional(readOnly = true)
    public List<SpecialtyResponse> getAllSpecialties() {
        return specialtyRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Paginated variant of {@link #getAllSpecialties()}. Sorted by ID DESC so
     * newest specialties come first; aligns with the convention used by other
     * paginated admin endpoints.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SpecialtyResponse> getSpecialtiesPage(
            org.springframework.data.domain.Pageable pageable) {
        return specialtyRepository.findAll(
                org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "id")))
                .map(this::toResponse);
    }

    /**
     * Aggregate counts by active flag for the entire table.
     * Powers dashboard summary cards so values stay accurate across pages.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Long> getStatusCounts() {
        long active = 0L;
        long inactive = 0L;
        for (Specialty s : specialtyRepository.findAll()) {
            if (Boolean.TRUE.equals(s.getIsActive())) active++; else inactive++;
        }
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        counts.put("total", specialtyRepository.count());
        counts.put("ACTIVE", active);
        counts.put("INACTIVE", inactive);
        return counts;
    }

    @Transactional(readOnly = true)
    public List<SpecialtyResponse> getActiveSpecialties() {
        return specialtyRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SpecialtyResponse getSpecialtyById(Integer id) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên khoa với ID: " + id));
        return toResponse(specialty);
    }

    @CacheEvict(value = {CacheConfig.SPECIALTIES_CACHE, CacheConfig.HOSPITAL_ELIGIBLE_SPECIALTIES_CACHE}, allEntries = true)
    public SpecialtyResponse createSpecialty(SpecialtyRequest request) {
        if (specialtyRepository.findByName(request.getName()).isPresent()) {
            throw new ConflictException("Chuyên khoa '" + request.getName() + "' đã tồn tại");
        }

        Specialty specialty = Specialty.builder()
                .name(request.getName())
                .description(request.getDescription())
                .isActive(true)
                .build();

        Specialty saved = specialtyRepository.save(specialty);

        auditHistoryService.logAction("specialty", saved.getId(), AuditHistory.ActionType.INSERT,
                null, saved, authContextService.getCurrentStaff().getId());

        return toResponse(saved);
    }

    @CacheEvict(value = {CacheConfig.SPECIALTIES_CACHE, CacheConfig.HOSPITAL_ELIGIBLE_SPECIALTIES_CACHE}, allEntries = true)
    public SpecialtyResponse updateSpecialty(Integer id, SpecialtyRequest request) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên khoa với ID: " + id));

        specialtyRepository.findByName(request.getName())
                .filter(s -> !s.getId().equals(id))
                .ifPresent(s -> {
                    throw new ConflictException("Chuyên khoa '" + request.getName() + "' đã tồn tại");
                });

        specialty.setName(request.getName());
        specialty.setDescription(request.getDescription());

        Specialty oldSpecialty = Specialty.builder()
                .name(specialty.getName())
                .description(specialty.getDescription())
                .build();

        Specialty saved = specialtyRepository.save(specialty);

        auditHistoryService.logAction("specialty", id, AuditHistory.ActionType.UPDATE,
                oldSpecialty, saved, authContextService.getCurrentStaff().getId());

        return toResponse(saved);
    }

    @CacheEvict(value = {CacheConfig.SPECIALTIES_CACHE, CacheConfig.HOSPITAL_ELIGIBLE_SPECIALTIES_CACHE}, allEntries = true)
    public void deleteSpecialty(Integer id) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên khoa với ID: " + id));

        auditHistoryService.logAction("specialty", id, AuditHistory.ActionType.DELETE,
                specialty, null, authContextService.getCurrentStaff().getId());

        specialty.setIsActive(false);
        specialtyRepository.save(specialty);
    }

    private SpecialtyResponse toResponse(Specialty specialty) {
        return SpecialtyResponse.builder()
                .id(specialty.getId())
                .name(specialty.getName())
                .description(specialty.getDescription())
                .isActive(specialty.getIsActive())
                .createdAt(specialty.getCreatedAt())
                .updatedAt(specialty.getUpdatedAt())
                .build();
    }
}
