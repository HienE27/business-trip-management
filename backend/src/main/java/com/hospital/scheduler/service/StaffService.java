package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.StaffRequest;
import com.hospital.scheduler.dto.response.StaffResponse;
import com.hospital.scheduler.entity.AppRole;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.StaffRole;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.AppRoleRepository;
import com.hospital.scheduler.repository.SpecialtyRepository;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StaffService {

    private final StaffRepository staffRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AppRoleRepository appRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public List<StaffResponse> getAllStaff() {
        return staffRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<StaffResponse> getActiveStaff() {
        return staffRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public StaffResponse getStaffById(Integer id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + id));
        return toResponse(staff);
    }

    public StaffResponse createStaff(StaffRequest request, List<String> roles) {
        if (staffRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' đã tồn tại");
        }
        if (request.getEmail() != null && staffRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email '" + request.getEmail() + "' đã tồn tại");
        }

        Specialty specialty = null;
        if (request.getSpecialtyId() != null) {
            specialty = specialtyRepository.findById(request.getSpecialtyId()).orElse(null);
        }

        Staff staff = Staff.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .specialty(specialty)
                .maxShiftsPerMonth(request.getMaxShiftsPerMonth() != null ? request.getMaxShiftsPerMonth() : 5)
                .isActive(true)
                .staffRoles(new HashSet<>())
                .build();

        Staff saved = staffRepository.save(staff);

        // Assign roles
        if (roles != null && !roles.isEmpty()) {
            for (String roleName : roles) {
                AppRole role = appRoleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy role: " + roleName));
                StaffRole sr = StaffRole.builder()
                        .staffId(saved.getId())
                        .roleId(role.getId())
                        .build();
                saved.getStaffRoles().add(sr);
            }
            staffRepository.save(saved);
        }

        return toResponse(staffRepository.findByIdWithRoles(saved.getId()).orElse(saved));
    }

    public StaffResponse updateStaff(Integer id, StaffRequest request) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + id));

        staffRepository.findByUsername(request.getUsername())
                .filter(s -> !s.getId().equals(id))
                .ifPresent(s -> {
                    throw new ConflictException("Username '" + request.getUsername() + "' đã tồn tại");
                });

        if (request.getEmail() != null) {
            staffRepository.findByEmail(request.getEmail())
                    .filter(s -> !s.getId().equals(id))
                    .ifPresent(s -> {
                        throw new ConflictException("Email '" + request.getEmail() + "' đã tồn tại");
                    });
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            staff.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        staff.setUsername(request.getUsername());
        staff.setFullName(request.getFullName());
        staff.setPhone(request.getPhone());
        staff.setEmail(request.getEmail());
        staff.setMaxShiftsPerMonth(request.getMaxShiftsPerMonth() != null ? request.getMaxShiftsPerMonth() : 5);

        if (request.getSpecialtyId() != null) {
            Specialty specialty = specialtyRepository.findById(request.getSpecialtyId()).orElse(null);
            staff.setSpecialty(specialty);
        }

        return toResponse(staffRepository.save(staff));
    }

    public void deleteStaff(Integer id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + id));
        staff.setIsActive(false);
        staffRepository.save(staff);
    }

    public StaffResponse getStaffByUsername(String username) {
        Staff staff = staffRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự: " + username));
        return toResponse(staff);
    }

    private StaffResponse toResponse(Staff staff) {
        StaffResponse.SpecialtyResponse specialtyResp = null;
        if (staff.getSpecialty() != null) {
            specialtyResp = StaffResponse.SpecialtyResponse.builder()
                    .id(staff.getSpecialty().getId())
                    .name(staff.getSpecialty().getName())
                    .build();
        }

        List<String> roleNames = staff.getStaffRoles().stream()
                .map(sr -> sr.getRole() != null ? sr.getRole().getName() : null)
                .filter(r -> r != null)
                .collect(Collectors.toList());

        return StaffResponse.builder()
                .id(staff.getId())
                .username(staff.getUsername())
                .fullName(staff.getFullName())
                .phone(staff.getPhone())
                .email(staff.getEmail())
                .specialty(specialtyResp)
                .maxShiftsPerMonth(staff.getMaxShiftsPerMonth())
                .isActive(staff.getIsActive())
                .createdAt(staff.getCreatedAt())
                .updatedAt(staff.getUpdatedAt())
                .roles(roleNames)
                .build();
    }
}
