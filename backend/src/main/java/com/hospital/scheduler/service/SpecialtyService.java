package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.SpecialtyRequest;
import com.hospital.scheduler.dto.response.SpecialtyResponse;
import com.hospital.scheduler.entity.AuditHistory;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.security.AuthContextService;
import lombok.RequiredArgsConstructor;
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

    public List<SpecialtyResponse> getAllSpecialties() {
        return specialtyRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<SpecialtyResponse> getActiveSpecialties() {
        return specialtyRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public SpecialtyResponse getSpecialtyById(Integer id) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên khoa với ID: " + id));
        return toResponse(specialty);
    }

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
