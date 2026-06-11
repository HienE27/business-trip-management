package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.SchedulePeriodRequest;
import com.hospital.scheduler.dto.response.SchedulePeriodResponse;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.service.SchedulePeriodService;
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
@RequestMapping("/api/v1/periods")
@RequiredArgsConstructor
@Tag(name = "SchedulePeriod", description = "Quản lý kỳ lịch")
public class SchedulePeriodController {

    private final SchedulePeriodService periodService;

    @GetMapping
    @Operation(summary = "Lấy danh sách kỳ lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<SchedulePeriodResponse>>> getAllPeriods() {
        return ResponseEntity.ok(ApiResponse.success(periodService.getAllPeriods()));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Lấy danh sách kỳ lịch theo trạng thái")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<SchedulePeriodResponse>>> getPeriodsByStatus(@PathVariable String status) {
        SchedulePeriod.PeriodStatus periodStatus = SchedulePeriod.PeriodStatus.valueOf(status.toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(periodService.getPeriodsByStatus(periodStatus)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết kỳ lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<SchedulePeriodResponse>> getPeriodById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(periodService.getPeriodById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo kỳ lịch mới")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<SchedulePeriodResponse>> createPeriod(
            @Valid @RequestBody SchedulePeriodRequest request,
            @RequestParam(required = false) Integer generatedById) {
        SchedulePeriodResponse created = periodService.createPeriod(request, generatedById);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Tạo kỳ lịch thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật kỳ lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<SchedulePeriodResponse>> updatePeriod(
            @PathVariable Integer id,
            @Valid @RequestBody SchedulePeriodRequest request) {
        return ResponseEntity.ok(ApiResponse.success(periodService.updatePeriod(id, request), "Cập nhật kỳ lịch thành công"));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Công bố kỳ lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<SchedulePeriodResponse>> publishPeriod(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer publishedById) {
        return ResponseEntity.ok(ApiResponse.success(periodService.publishPeriod(id, publishedById), "Công bố kỳ lịch thành công"));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Lưu trữ kỳ lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<SchedulePeriodResponse>> archivePeriod(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(periodService.archivePeriod(id), "Lưu trữ kỳ lịch thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa kỳ lịch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePeriod(@PathVariable Integer id) {
        periodService.deletePeriod(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa kỳ lịch thành công"));
    }
}
