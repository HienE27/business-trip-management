package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.AuditHistoryResponse;
import com.hospital.scheduler.service.AuditHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/audit-history")
@RequiredArgsConstructor
@Tag(name = "Audit History", description = "Lịch sử thay đổi dữ liệu")
public class AuditHistoryController {

    private final AuditHistoryService auditHistoryService;

    @GetMapping
    @Operation(summary = "Lấy tất cả lịch sử thay đổi (có phân trang)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<AuditHistoryResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditHistoryService.getAllAuditHistory(page, size)));
    }

    @GetMapping("/table/{tableName}/record/{recordId}")
    @Operation(summary = "Lấy lịch sử thay đổi theo bảng và bản ghi")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<AuditHistoryResponse>>> getByTableAndRecord(
            @PathVariable String tableName,
            @PathVariable Integer recordId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                auditHistoryService.getAuditHistoryByTableAndRecord(tableName, recordId, page, size)));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Lấy lịch sử thay đổi theo người dùng")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<AuditHistoryResponse>>> getByUser(
            @PathVariable Integer userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(auditHistoryService.getAuditHistoryByUser(userId, page, size)));
    }

    @GetMapping("/date-range")
    @Operation(summary = "Lấy lịch sử thay đổi theo khoảng thời gian (có phân trang)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<AuditHistoryResponse>>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                auditHistoryService.getAuditHistoryByDateRange(startDate, endDate, page, size)));
    }
}
