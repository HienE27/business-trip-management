package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.SpecialtyRequest;
import com.hospital.scheduler.dto.response.SpecialtyResponse;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.SpecialtyRepository;
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

        return toResponse(specialtyRepository.save(specialty));
    }

    public void deleteSpecialty(Integer id) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên khoa với ID: " + id));
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
