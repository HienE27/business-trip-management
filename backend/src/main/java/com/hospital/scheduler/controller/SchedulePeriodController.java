package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.BulkPeriodRequest;
import com.hospital.scheduler.dto.request.SchedulePeriodRequest;
import com.hospital.scheduler.dto.response.BulkPeriodResponse;
import com.hospital.scheduler.dto.response.PublishDryRunResponse;
import com.hospital.scheduler.dto.response.SchedulePeriodResponse;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.service.ConflictDetectionService;
import com.hospital.scheduler.service.SchedulePeriodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ConflictDetectionService conflictDetectionService;

    @GetMapping
    @Operation(summary = "Lấy danh sách kỳ lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<SchedulePeriodResponse>>> getAllPeriods() {
        return ResponseEntity.ok(ApiResponse.success(periodService.getAllPeriods()));
    }

    /**
     * Paginated variant — drives the &lt;Pagination&gt; control on the kỳ-lịch page.
     * Sorted DESC by startDate so newest periods surface first.
     */
    @GetMapping("/page")
    @Operation(summary = "Lấy danh sách kỳ lịch có phân trang")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<SchedulePeriodResponse>>> getPeriodsPage(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(periodService.getPeriodsPage(pageable)));
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

    @GetMapping("/{id}/publish/dry-run")
    @Operation(summary = "Kiểm tra trước khi công bố kỳ lịch (dry-run)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<PublishDryRunResponse>> dryRunPublish(@PathVariable Integer id) {
        PublishDryRunResponse response = periodService.dryRunPublish(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Lưu trữ kỳ lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<SchedulePeriodResponse>> archivePeriod(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(periodService.archivePeriod(id), "Lưu trữ kỳ lịch thành công"));
    }

    @PostMapping("/bulk/publish")
    @Operation(summary = "Công bố hàng loạt nhiều kỳ lịch DRAFT")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BulkPeriodResponse>> bulkPublish(
            @Valid @RequestBody BulkPeriodRequest request,
            @RequestParam(required = false) Integer publishedById) {
        BulkPeriodResponse result = periodService.bulkPublish(request.getPeriodIds(), publishedById);
        return ResponseEntity.ok(ApiResponse.success(result,
                String.format("Đã xử lý %d kỳ lịch: %d thành công, %d thất bại",
                        result.getTotalRequested(), result.getSuccessCount(), result.getFailureCount())));
    }

    @PostMapping("/bulk/archive")
    @Operation(summary = "Lưu trữ hàng loạt nhiều kỳ lịch PUBLISHED")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<BulkPeriodResponse>> bulkArchive(
            @Valid @RequestBody BulkPeriodRequest request) {
        BulkPeriodResponse result = periodService.bulkArchive(request.getPeriodIds());
        return ResponseEntity.ok(ApiResponse.success(result,
                String.format("Đã xử lý %d kỳ lịch: %d thành công, %d thất bại",
                        result.getTotalRequested(), result.getSuccessCount(), result.getFailureCount())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa kỳ lịch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePeriod(@PathVariable Integer id) {
        periodService.deletePeriod(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa kỳ lịch thành công"));
    }
}
