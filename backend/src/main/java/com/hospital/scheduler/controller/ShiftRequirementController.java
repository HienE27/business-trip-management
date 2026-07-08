package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.ShiftRequirementRequest;
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

import java.time.LocalDate;
import java.util.List;

/**
 * M07-F01 "Cấu hình tham số đầu vào": quản lý yêu cầu nhân sự theo ca.
 *
 * URL pattern khớp với frontend test mock:
 *   - GET /api/v1/shift-requirements/period/{periodId}
 */
@RestController
@RequestMapping("/api/v1/shift-requirements")
@RequiredArgsConstructor
@Tag(name = "Shift Requirement", description = "Quản lý yêu cầu nhân sự theo ca (M07-F01)")
public class ShiftRequirementController {

    private final ShiftRequirementService shiftRequirementService;

    @GetMapping("/period/{periodId}")
    @Operation(summary = "Lấy danh sách yêu cầu nhân sự theo kỳ lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ShiftRequirementResponse>>> getByPeriod(@PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(shiftRequirementService.getByPeriod(periodId)));
    }

    @GetMapping("/period/{periodId}/date/{workDate}")
    @Operation(summary = "Lấy yêu cầu nhân sự theo kỳ lịch + ngày cụ thể")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ShiftRequirementResponse>>> getByPeriodAndDate(
            @PathVariable Integer periodId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate) {
        return ResponseEntity.ok(ApiResponse.success(shiftRequirementService.getByPeriodAndDate(periodId, workDate)));
    }

    @PostMapping("/period/{periodId}")
    @Operation(summary = "Bulk upsert yêu cầu nhân sự cho một kỳ lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ShiftRequirementResponse>>> bulkUpsert(
            @PathVariable Integer periodId,
            @Valid @RequestBody List<ShiftRequirementRequest> requests) {
        List<ShiftRequirementResponse> result = shiftRequirementService.upsert(periodId, requests);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result,
                        String.format("Đã lưu %d yêu cầu ca cho kỳ lịch %d", result.size(), periodId)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật 1 yêu cầu ca")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ShiftRequirementResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody ShiftRequirementRequest request) {
        return ResponseEntity.ok(ApiResponse.success(shiftRequirementService.update(id, request),
                "Cập nhật yêu cầu ca thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa 1 yêu cầu ca")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        shiftRequirementService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa yêu cầu ca"));
    }

    @DeleteMapping("/period/{periodId}")
    @Operation(summary = "Xóa tất cả yêu cầu ca của một kỳ lịch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> deleteAllByPeriod(@PathVariable Integer periodId) {
        int deleted = shiftRequirementService.deleteAllByPeriod(periodId);
        return ResponseEntity.ok(ApiResponse.success(deleted,
                String.format("Đã xóa %d yêu cầu ca của kỳ lịch %d", deleted, periodId)));
    }
}