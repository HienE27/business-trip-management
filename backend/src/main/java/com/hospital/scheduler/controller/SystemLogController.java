package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.SystemLogResponse;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.SystemLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    /**
     * BUGFIX (was BE#18) helper: cap page-size so a malicious or buggy
     * client can't ask for {@code size=Integer.MAX_VALUE} and OOM the DB or
     * the JSON serializer.
     */
    private static final int MAX_PAGE_SIZE = 200;

    private static int clampPageSize(int requested) {
        if (requested <= 0) return 50; // sensible default
        if (requested > MAX_PAGE_SIZE) return MAX_PAGE_SIZE;
        return requested;
    }

    @GetMapping
    @Operation(summary = "Lấy tất cả nhật ký hệ thống (phân trang)")
    @PreAuthorize("hasAuthority('" + Permissions.SYSTEM_LOG_VIEW + "')")
    public ResponseEntity<ApiResponse<Page<SystemLogResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        // BUGFIX (was BE#18): the previous version returned the entire system
        // log table with no upper bound — a single user with SYSTEM_LOG_VIEW
        // could dump millions of rows and exhaust memory. Always paginate.
        return ResponseEntity.ok(ApiResponse.success(
                systemLogService.getAllLogsPage(page, clampPageSize(size))));
    }

    @GetMapping("/staff/{staffId}")
    @Operation(summary = "Lấy nhật ký theo nhân sự (phân trang)")
    @PreAuthorize("hasAuthority('" + Permissions.SYSTEM_LOG_VIEW + "')")
    public ResponseEntity<ApiResponse<Page<SystemLogResponse>>> getByStaff(
            @PathVariable Integer staffId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        // BUGFIX (was BE#18): same pagination guard as /all.
        return ResponseEntity.ok(ApiResponse.success(
                systemLogService.getLogsByStaffPage(staffId, page, clampPageSize(size))));
    }

    @GetMapping("/action-type/{actionType}")
    @Operation(summary = "Lấy nhật ký theo loại hành động (phân trang)")
    @PreAuthorize("hasAuthority('" + Permissions.SYSTEM_LOG_VIEW + "')")
    public ResponseEntity<ApiResponse<Page<SystemLogResponse>>> getByActionType(
            @PathVariable String actionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        // BUGFIX (was BE#18): same pagination guard.
        return ResponseEntity.ok(ApiResponse.success(
                systemLogService.getLogsByActionTypePage(actionType, page, clampPageSize(size))));
    }

    @GetMapping("/date-range")
    @Operation(summary = "Lấy nhật ký theo khoảng thời gian (phân trang)")
    @PreAuthorize("hasAuthority('" + Permissions.SYSTEM_LOG_VIEW + "')")
    public ResponseEntity<ApiResponse<Page<SystemLogResponse>>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        // BUGFIX (was BE#19): reject inverted ranges with 400 instead of
        // returning empty silently.
        if (endDate.isBefore(startDate)) {
            throw new com.hospital.scheduler.exception.BadRequestException(
                    "Khoảng thời gian không hợp lệ: endDate (" + endDate + ") phải >= startDate (" + startDate + ")");
        }
        // BUGFIX (was BE#18): paginate to prevent unbounded result.
        return ResponseEntity.ok(ApiResponse.success(
                systemLogService.getLogsByDateRangePage(startDate, endDate, page, clampPageSize(size))));
    }
}