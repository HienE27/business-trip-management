package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.AuditHistoryResponse;
import com.hospital.scheduler.service.AuditHistoryService;
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
@RequestMapping("/api/v1/audit-history")
@RequiredArgsConstructor
@Tag(name = "Audit History", description = "Lịch sử thay đổi dữ liệu")
public class AuditHistoryController {

    private final AuditHistoryService auditHistoryService;

    @GetMapping
    @Operation(summary = "Lấy tất cả lịch sử thay đổi")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditHistoryResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(auditHistoryService.getAllAuditHistory()));
    }

    @GetMapping("/table/{tableName}/record/{recordId}")
    @Operation(summary = "Lấy lịch sử thay đổi theo bảng và bản ghi")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditHistoryResponse>>> getByTableAndRecord(
            @PathVariable String tableName,
            @PathVariable Integer recordId) {
        return ResponseEntity.ok(ApiResponse.success(
                auditHistoryService.getAuditHistoryByTableAndRecord(tableName, recordId)));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Lấy lịch sử thay đổi theo người dùng")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditHistoryResponse>>> getByUser(@PathVariable Integer userId) {
        return ResponseEntity.ok(ApiResponse.success(auditHistoryService.getAuditHistoryByUser(userId)));
    }

    @GetMapping("/date-range")
    @Operation(summary = "Lấy lịch sử thay đổi theo khoảng thời gian")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditHistoryResponse>>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(ApiResponse.success(
                auditHistoryService.getAuditHistoryByDateRange(startDate, endDate)));
    }
}
