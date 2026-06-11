package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.ShiftTypeRequest;
import com.hospital.scheduler.dto.response.ShiftTypeResponse;
import com.hospital.scheduler.service.ShiftTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shift-types")
@RequiredArgsConstructor
@Tag(name = "ShiftType", description = "Quản lý loại ca trực")
public class ShiftTypeController {

    private final ShiftTypeService shiftTypeService;

    @GetMapping
    @Operation(summary = "Lấy danh sách loại ca")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ShiftTypeResponse>>> getAllShiftTypes() {
        return ResponseEntity.ok(ApiResponse.success(shiftTypeService.getAllShiftTypes()));
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách loại ca đang hoạt động")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ShiftTypeResponse>>> getActiveShiftTypes() {
        return ResponseEntity.ok(ApiResponse.success(shiftTypeService.getActiveShiftTypes()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết loại ca")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ShiftTypeResponse>> getShiftTypeById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(shiftTypeService.getShiftTypeById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo loại ca mới")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShiftTypeResponse>> createShiftType(@Valid @RequestBody ShiftTypeRequest request) {
        ShiftTypeResponse created = shiftTypeService.createShiftType(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Tạo loại ca thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật loại ca")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ShiftTypeResponse>> updateShiftType(
            @PathVariable String id,
            @Valid @RequestBody ShiftTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(shiftTypeService.updateShiftType(id, request), "Cập nhật loại ca thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa loại ca (soft delete)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteShiftType(@PathVariable String id) {
        shiftTypeService.deleteShiftType(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa loại ca thành công"));
    }
}
