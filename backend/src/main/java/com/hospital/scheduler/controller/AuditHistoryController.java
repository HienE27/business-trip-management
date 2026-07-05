package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.AuditHistoryResponse;
import com.hospital.scheduler.dto.response.AuditHistorySummaryResponse;
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
import java.util.List;

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

    @GetMapping("/summary")
    @Operation(summary = "Lấy thống kê số lượng sự kiện theo từng loại (CREATE/UPDATE/DELETE)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AuditHistorySummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(auditHistoryService.getActionCounts()));
    }

    @GetMapping("/summary/date-range")
    @Operation(summary = "Lấy thống kê số lượng sự kiện theo loại trong khoảng thời gian")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AuditHistorySummaryResponse>> getSummaryByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(ApiResponse.success(
                auditHistoryService.getActionCountsBetween(startDate, endDate)));
    }

    /**
     * Filtered KPI summary that mirrors every filter on the audit list page (date range +
     * module + action + search). Calling this with no filters is equivalent to /summary.
     */
    @GetMapping("/summary/filter")
    @Operation(summary = "Lấy thống kê số lượng sự kiện theo loại với các bộ lọc (dateRange/module/action/search)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AuditHistorySummaryResponse>> getSummaryFiltered(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String search) {
        com.hospital.scheduler.entity.AuditHistory.ActionType actionEnum = null;
        if (action != null && !action.isBlank()) {
            // Accept either UI labels (CREATE/UPDATE/DELETE) or enum names (INSERT/UPDATE/DELETE)
            String normalized = action.trim().toUpperCase();
            if (normalized.equals("CREATE")) normalized = "INSERT";
            try {
                actionEnum = com.hospital.scheduler.entity.AuditHistory.ActionType.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                // Unknown action string — treat as no filter, counts will not be constrained
            }
        }
        return ResponseEntity.ok(ApiResponse.success(
                auditHistoryService.getActionCountsFiltered(startDate, endDate, module, actionEnum, search)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa một bản ghi nhật ký")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        auditHistoryService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa bản ghi nhật ký."));
    }

    @DeleteMapping
    @Operation(summary = "Xóa nhiều bản ghi nhật ký")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> deleteMultiple(@RequestBody List<Integer> ids) {
        int count = auditHistoryService.deleteByIds(ids);
        return ResponseEntity.ok(ApiResponse.success(count, "Đã xóa " + count + " bản ghi."));
    }

    @DeleteMapping("/date-range")
    @Operation(summary = "Xóa nhật ký theo khoảng thời gian")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> deleteByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay().minusNanos(1);
        int count = auditHistoryService.deleteByDateRange(start, end);
        return ResponseEntity.ok(ApiResponse.success(count, "Đã xóa " + count + " bản ghi nhật ký trong khoảng thời gian."));
    }

    /**
     * Wipe the entire audit_history table. Restricted to ADMIN role and intended
     * to be paired with a typed confirmation on the client side ("XÓA" + record count).
     */
    @DeleteMapping("/all")
    @Operation(summary = "Xóa toàn bộ nhật ký (ADMIN only — yêu cầu xác nhận typed trên UI)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> deleteAll() {
        int count = auditHistoryService.deleteAll();
        return ResponseEntity.ok(ApiResponse.success(count, "Đã xóa toàn bộ " + count + " bản ghi nhật ký."));
    }
}
