package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.AlgoConfigRequest;
import com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO;
import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import com.hospital.scheduler.dto.request.SaveAlgorithmTemplateRequest;
import com.hospital.scheduler.dto.request.SaveTemplateRequest;
import com.hospital.scheduler.dto.response.AlgorithmConfigDTO;
import com.hospital.scheduler.dto.response.AlgorithmConfigResponse;
import com.hospital.scheduler.dto.response.AlgorithmMetricsDTO;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.dto.response.ScheduleTemplateResponse;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.AutoSchedulingService;
import com.hospital.scheduler.service.ScheduleTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auto-schedule")
@RequiredArgsConstructor
@Tag(name = "Auto Scheduling", description = "Xếp lịch tự động")
public class AutoSchedulingController {

    private final AutoSchedulingService autoSchedulingService;
    private final ScheduleTemplateService scheduleTemplateService;
    private final AlgorithmConfigService configService;

    @PostMapping("/preview")
    @Operation(summary = "M07-F07: Xem trước lịch trước khi xác nhận")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AutoScheduleResponse>> previewSchedule(
            @Valid @RequestBody AutoScheduleRequestDTO request) {
        AutoScheduleResponse result = autoSchedulingService.previewSchedule(request);
        return ResponseEntity.ok(ApiResponse.success(result, "Xem trước lịch"));
    }

    @PostMapping
    @Operation(summary = "M07-F01-F05: Chạy thuật toán xếp lịch tự động (GREEDY/ROUND_ROBIN/BACKTRACKING)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AutoScheduleResponse>> autoSchedule(
            @Valid @RequestBody AutoScheduleRequestDTO request) {
        AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(result, "Xếp lịch tự động hoàn tất"));
    }

    @PostMapping("/apply-preview")
    @Operation(summary = "M07-F07: Áp dụng bản nháp đã chỉnh sửa thủ công")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AutoScheduleResponse>> applyPreviewSchedule(
            @Valid @RequestBody AutoScheduleApplyPreviewRequestDTO request) {
        AutoScheduleResponse result = autoSchedulingService.applyPreviewSchedule(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(result, "Đã áp dụng bản nháp đã chỉnh sửa"));
    }

    @PostMapping("/save-template")
    @Operation(summary = "M07-F10: Lưu lịch đã xếp tự động thành mẫu để tái sử dụng")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ScheduleTemplateResponse>> saveAsTemplate(
            @Valid @RequestBody SaveTemplateRequest request) {
        var result = scheduleTemplateService.saveTemplateFromGenerated(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Lưu mẫu lịch thành công"));
    }

    @GetMapping("/templates")
    @Operation(summary = "M07-F10c: Liệt kê tất cả mẫu lịch đang hoạt động")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<ScheduleTemplateResponse>>> listTemplates() {
        return ResponseEntity.ok(ApiResponse.success(scheduleTemplateService.getActiveTemplates()));
    }

    @GetMapping("/templates/{templateId}")
    @Operation(summary = "M07-F10d: Tải chi tiết một mẫu lịch theo ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ScheduleTemplateResponse>> getTemplate(@PathVariable Integer templateId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleTemplateService.getTemplateById(templateId)));
    }

    @PostMapping("/templates")
    @Operation(summary = "M07-F10b: Lưu cấu hình thuật toán thành mẫu có thể tái sử dụng")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AlgorithmConfigResponse>> saveAlgorithmAsTemplate(
            @Valid @RequestBody SaveAlgorithmTemplateRequest request) {
        AlgorithmConfigResponse result = configService.saveAsTemplate(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Lưu mẫu cấu hình thuật toán thành công"));
    }

    @GetMapping("/unassigned/{periodId}")
    @Operation(summary = "M07-F06: Báo cáo ngày chưa phân công được")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnassignedDaysReport(@PathVariable Integer periodId) {
        Map<String, Object> report = autoSchedulingService.getUnassignedDaysReport(periodId);
        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/suggest-replacements/{scheduleId}")
    @Operation(summary = "M07-F08: Đề xuất người thay thế khi có thay đổi đột xuất")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> suggestReplacements(@PathVariable Integer scheduleId) {
        Map<String, Object> suggestions = autoSchedulingService.suggestReplacements(scheduleId);
        return ResponseEntity.ok(ApiResponse.success(suggestions));
    }

    @GetMapping("/workload-chart/{periodId}")
    @Operation(summary = "M07-F09: Data biểu đồ cân bằng tải nhân sự")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorkloadChartData(
            @PathVariable Integer periodId,
            @RequestParam(required = false) String shiftTypeId) {
        Map<String, Object> chartData = autoSchedulingService.getWorkloadChartData(periodId, shiftTypeId);
        return ResponseEntity.ok(ApiResponse.success(chartData));
    }

    @GetMapping("/metrics/period/{periodId}")
    @Operation(summary = "Lấy lịch sử chạy thuật toán theo kỳ")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<AlgorithmMetricsDTO>>> getMetricsByPeriod(@PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(autoSchedulingService.getMetricsByPeriod(periodId)));
    }

    @GetMapping("/metrics")
    @Operation(summary = "Lấy tất cả lịch sử chạy thuật toán")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AlgorithmMetricsDTO>>> getAllMetrics() {
        return ResponseEntity.ok(ApiResponse.success(autoSchedulingService.getAllMetrics()));
    }

    // ============================================================
    // AlgorithmConfig CRUD Endpoints
    // ============================================================

    @GetMapping("/config")
    @Operation(summary = "Lấy tất cả cấu hình thuật toán")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AlgorithmConfigDTO>>> getAllConfigs() {
        return ResponseEntity.ok(ApiResponse.success(configService.getAllConfigs()));
    }

    @GetMapping("/config/{paramKey}")
    @Operation(summary = "Lấy cấu hình theo paramKey")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AlgorithmConfigDTO>> getConfigById(@PathVariable String paramKey) {
        return ResponseEntity.ok(ApiResponse.success(configService.getConfigByParamKey(paramKey)));
    }

    @PostMapping("/config")
    @Operation(summary = "Tạo mới cấu hình thuật toán")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AlgorithmConfigDTO>> createConfig(
            @Valid @RequestBody AlgoConfigRequest request) {
        AlgorithmConfigDTO created = configService.createConfig(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo cấu hình thành công"));
    }

    @PutMapping("/config/{paramKey}")
    @Operation(summary = "Cập nhật cấu hình thuật toán")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AlgorithmConfigDTO>> updateConfig(
            @PathVariable String paramKey,
            @Valid @RequestBody AlgoConfigRequest request) {
        AlgorithmConfigDTO updated = configService.updateConfig(paramKey, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật cấu hình thành công"));
    }

    @DeleteMapping("/config/{paramKey}")
    @Operation(summary = "Xóa cấu hình thuật toán")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable String paramKey) {
        configService.deleteConfig(paramKey);
        return ResponseEntity.ok(ApiResponse.success((Void) null, "Xóa cấu hình thành công"));
    }
}
