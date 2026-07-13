package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.SpecialtyRequest;
import com.hospital.scheduler.dto.response.SpecialtyResponse;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.SpecialtyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/specialties")
@RequiredArgsConstructor
@Tag(name = "Specialty", description = "Quản lý chuyên khoa")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    @GetMapping
    @Operation(summary = "Lấy danh sách chuyên khoa")
    @PreAuthorize("hasAuthority('" + Permissions.SPECIALTY_MANAGE + "') or hasAuthority('" + Permissions.STAFF_VIEW + "')")
    public ResponseEntity<ApiResponse<List<SpecialtyResponse>>> getAllSpecialties() {
        return ResponseEntity.ok(ApiResponse.success(specialtyService.getAllSpecialties()));
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách chuyên khoa đang hoạt động")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SpecialtyResponse>>> getActiveSpecialties() {
        return ResponseEntity.ok(ApiResponse.success(specialtyService.getActiveSpecialties()));
    }

    @GetMapping("/page")
    @Operation(summary = "Lấy danh sách chuyên khoa có phân trang")
    @PreAuthorize("hasAuthority('" + Permissions.SPECIALTY_MANAGE + "')")
    public ResponseEntity<ApiResponse<Page<SpecialtyResponse>>> getSpecialtiesPage(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(specialtyService.getSpecialtiesPage(pageable)));
    }

    @GetMapping("/status-counts")
    @Operation(summary = "Đếm chuyên khoa theo trạng thái (toàn DB, không phân trang)")
    @PreAuthorize("hasAuthority('" + Permissions.SPECIALTY_MANAGE + "')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStatusCounts() {
        return ResponseEntity.ok(ApiResponse.success(specialtyService.getStatusCounts()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết chuyên khoa")
    @PreAuthorize("hasAuthority('" + Permissions.SPECIALTY_MANAGE + "') or hasAuthority('" + Permissions.STAFF_VIEW + "')")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> getSpecialtyById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(specialtyService.getSpecialtyById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo chuyên khoa mới")
    @PreAuthorize("hasAuthority('" + Permissions.SPECIALTY_MANAGE + "')")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> createSpecialty(@Valid @RequestBody SpecialtyRequest request) {
        SpecialtyResponse created = specialtyService.createSpecialty(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Tạo chuyên khoa thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật chuyên khoa")
    @PreAuthorize("hasAuthority('" + Permissions.SPECIALTY_MANAGE + "')")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> updateSpecialty(
            @PathVariable Integer id,
            @Valid @RequestBody SpecialtyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(specialtyService.updateSpecialty(id, request), "Cập nhật chuyên khoa thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa chuyên khoa (soft delete)")
    @PreAuthorize("hasAuthority('" + Permissions.SPECIALTY_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> deleteSpecialty(@PathVariable Integer id) {
        specialtyService.deleteSpecialty(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa chuyên khoa thành công"));
    }
}