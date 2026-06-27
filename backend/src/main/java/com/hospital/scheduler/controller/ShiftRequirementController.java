package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.ShiftRequirementDTO;
import com.hospital.scheduler.dto.response.ShiftRequirementResponse;
import com.hospital.scheduler.service.ShiftRequirementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/shift-requirements")
@RequiredArgsConstructor
@Tag(name = "Shift Requirement", description = "Quản lý yêu cầu nhân sự cho ca")
public class ShiftRequirementController {

    private final ShiftRequirementService requirementService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả yêu cầu nhân sự (có phân trang)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<ShiftRequirementResponse>>> getAllRequirements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(ApiResponse.success(requirementService.getAllRequirements(page, size)));
    }

    @GetMapping("/period/{periodId}")
    @Operation(summary = "Lấy yêu cầu nhân sự theo kỳ lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ShiftRequirementResponse>>> getByPeriod(@PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(requirementService.getRequirementsByPeriod(periodId)));
    }

    @GetMapping("/period/{periodId}/date/{date}")
    @Operation(summary = "Lấy yêu cầu nhân sự theo kỳ lịch và ngày")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ShiftRequirementResponse>>> getByPeriodAndDate(
            @PathVariable Integer periodId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(requirementService.getRequirementsByPeriodAndDate(periodId, date)));
    }

    @GetMapping("/period/{periodId}/range")
    @Operation(summary = "Lấy yêu cầu nhân sự theo kỳ lịch và khoảng ngày")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ShiftRequirementResponse>>> getByPeriodAndDateRange(
            @PathVariable Integer periodId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success(
                requirementService.getRequirementsByPeriodAndDateRange(periodId, startDate, endDate)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết yêu cầu nhân sự")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ShiftRequirementResponse>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(requirementService.getRequirementById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo yêu cầu nhân sự mới")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ShiftRequirementResponse>> create(
            @Valid @RequestBody ShiftRequirementDTO dto) {
        ShiftRequirementResponse created = requirementService.createRequirement(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo yêu cầu nhân sự thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật yêu cầu nhân sự")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ShiftRequirementResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody ShiftRequirementDTO dto) {
        return ResponseEntity.ok(ApiResponse.success(
                requirementService.updateRequirement(id, dto), "Cập nhật yêu cầu nhân sự thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa yêu cầu nhân sự")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        requirementService.deleteRequirement(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa yêu cầu nhân sự thành công"));
    }
}
