package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.ScheduleRequest;
import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.dto.response.ScheduleResponse;
import com.hospital.scheduler.dto.response.StaffResponse;
import com.hospital.scheduler.service.ScheduleService;
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

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "Schedule", description = "Quản lý lịch trực")
public class ScheduleController {

    private final ScheduleService scheduleService;


    @GetMapping("/conflicts/check/{periodId}")
    @Operation(summary = "Kiểm tra xung đột lịch trong kỳ")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ConflictCheckResponse>> checkConflicts(@PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.checkConflictsInPeriod(periodId)));
    }

    @GetMapping("/replacements/{periodId}")
    @Operation(summary = "Đề xuất người thay thế cho một ca")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> findReplacements(
            @PathVariable Integer periodId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam String shiftTypeId,
            @RequestParam Integer originalStaffId,
            @RequestParam(required = false, defaultValue = "1") Integer requiredCount) {
        return ResponseEntity.ok(ApiResponse.success(
                scheduleService.findReplacements(periodId, workDate, shiftTypeId, originalStaffId, requiredCount)));
    }

    @GetMapping("/period/{periodId}")
    @Operation(summary = "Lấy danh sách lịch theo kỳ")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getSchedulesByPeriod(@PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getSchedulesByPeriod(periodId)));
    }

    @GetMapping("/period/{periodId}/date/{date}")
    @Operation(summary = "Lấy danh sách lịch theo kỳ và ngày")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getSchedulesByPeriodAndDate(
            @PathVariable Integer periodId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getSchedulesByPeriodAndDate(periodId, date)));
    }

    @GetMapping("/staff/{staffId}")
    @Operation(summary = "Lấy danh sách lịch theo nhân sự")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getSchedulesByStaff(@PathVariable Integer staffId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getSchedulesByStaff(staffId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết lịch")
    public ResponseEntity<ApiResponse<ScheduleResponse>> getScheduleById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getScheduleById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo lịch mới")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> createSchedule(@Valid @RequestBody ScheduleRequest request) {
        ScheduleResponse created = scheduleService.createSchedule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Tạo lịch thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> updateSchedule(
            @PathVariable Integer id,
            @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.updateSchedule(id, request), "Cập nhật lịch thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(@PathVariable Integer id) {
        scheduleService.deleteSchedule(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa lịch thành công"));
    }
}
