package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.SystemLogResponse;
import com.hospital.scheduler.service.SystemLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/system-logs")
@RequiredArgsConstructor
@Tag(name = "System Logs", description = "Nhật ký hệ thống")
public class SystemLogController {

    private final SystemLogService systemLogService;

    @GetMapping
    @Operation(summary = "Lấy tất cả nhật ký hệ thống")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SystemLogResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(systemLogService.getAllLogs()));
    }

    @GetMapping("/staff/{staffId}")
    @Operation(summary = "Lấy nhật ký theo nhân sự")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<SystemLogResponse>>> getByStaff(@PathVariable Integer staffId) {
        return ResponseEntity.ok(ApiResponse.success(systemLogService.getLogsByStaff(staffId)));
    }

    @GetMapping("/action-type/{actionType}")
    @Operation(summary = "Lấy nhật ký theo loại hành động")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SystemLogResponse>>> getByActionType(@PathVariable String actionType) {
        return ResponseEntity.ok(ApiResponse.success(systemLogService.getLogsByActionType(actionType)));
    }

    @GetMapping("/date-range")
    @Operation(summary = "Lấy nhật ký theo khoảng thời gian")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SystemLogResponse>>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(ApiResponse.success(
                systemLogService.getLogsByDateRange(startDate, endDate)));
    }
}
