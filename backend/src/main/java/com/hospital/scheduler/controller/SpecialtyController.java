package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.SpecialtyRequest;
import com.hospital.scheduler.dto.response.SpecialtyResponse;
import com.hospital.scheduler.service.SpecialtyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/specialties")
@RequiredArgsConstructor
@Tag(name = "Specialty", description = "Quản lý chuyên khoa")
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    @GetMapping
    @Operation(summary = "Lấy danh sách chuyên khoa")
    public ResponseEntity<ApiResponse<List<SpecialtyResponse>>> getAllSpecialties() {
        return ResponseEntity.ok(ApiResponse.success(specialtyService.getAllSpecialties()));
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách chuyên khoa đang hoạt động")
    public ResponseEntity<ApiResponse<List<SpecialtyResponse>>> getActiveSpecialties() {
        return ResponseEntity.ok(ApiResponse.success(specialtyService.getActiveSpecialties()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết chuyên khoa")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> getSpecialtyById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(specialtyService.getSpecialtyById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo chuyên khoa mới")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> createSpecialty(@Valid @RequestBody SpecialtyRequest request) {
        SpecialtyResponse created = specialtyService.createSpecialty(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Tạo chuyên khoa thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật chuyên khoa")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> updateSpecialty(
            @PathVariable Integer id,
            @Valid @RequestBody SpecialtyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(specialtyService.updateSpecialty(id, request), "Cập nhật chuyên khoa thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa chuyên khoa (soft delete)")
    public ResponseEntity<ApiResponse<Void>> deleteSpecialty(@PathVariable Integer id) {
        specialtyService.deleteSpecialty(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa chuyên khoa thành công"));
    }
}
