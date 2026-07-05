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
import com.hospital.scheduler.dto.response.AlgorithmConfigAuditDTO;
import com.hospital.scheduler.entity.AlgorithmConfigAudit;
import com.hospital.scheduler.repository.AlgorithmConfigAuditRepository;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.AlgorithmProgressTracker;
import com.hospital.scheduler.service.AutoSchedulingService;
import com.hospital.scheduler.service.ScheduleTemplateService;
import com.hospital.scheduler.service.AlgorithmMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auto-schedule")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auto Scheduling", description = "Xếp lịch tự động")
public class AutoSchedulingController {

    private final AutoSchedulingService autoSchedulingService;
    private final ScheduleTemplateService scheduleTemplateService;
    private final AlgorithmConfigService configService;
    private final AlgorithmMetricsService metricsService;
    private final AlgorithmProgressTracker progressTracker;
    private final AlgorithmConfigAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return null;
        }
    }

    @PostMapping("/preview")
    @Operation(summary = "M07-F07: Xem trước lịch trước khi xác nhận")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AutoScheduleResponse>> previewSchedule(
            @Valid @RequestBody AutoScheduleRequestDTO request) {
        // Start progress tracking
        progressTracker.start(request.getPeriodId());
        try {
            AutoScheduleResponse result = autoSchedulingService.previewSchedule(request);
            log.info("Preview completed for period {}: {} schedules, coverage={}, conflicts={}",
                    request.getPeriodId(), result.getTotalSchedulesCreated(),
                    result.getCoverageRate(), result.getConflictCount());
            // Store result in progress tracker for polling
            progressTracker.completeWithResult(request.getPeriodId(),
                    "Xem trước lịch thành công", serializeToJson(result));
            return ResponseEntity.ok(ApiResponse.success(result, "Xem trước lịch"));
        } catch (Exception e) {
            log.error("Preview failed for period {}: {}", request.getPeriodId(), e.getMessage(), e);
            progressTracker.fail(request.getPeriodId(), e.getMessage());
            throw e;
        }
    }

    @PostMapping
    @Operation(summary = "M07-F01-F05: Chạy thuật toán xếp lịch tự động (GREEDY/ROUND_ROBIN/BACKTRACKING)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AutoScheduleResponse>> autoSchedule(
            @Valid @RequestBody AutoScheduleRequestDTO request) {
        AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);
        // Ensure compensation days are created for all L01 schedules
        autoSchedulingService.createCompensationDaysForL01InPeriod(result.getPeriodId());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(result, "Xếp lịch tự động hoàn tất"));
    }

    @GetMapping("/progress/{periodId}")
    @Operation(summary = "Lấy tiến độ chạy thuật toán theo period (polling endpoint)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProgress(@PathVariable Integer periodId) {
        AlgorithmProgressTracker.Progress p = progressTracker.get(periodId);
        if (p == null) {
            // No active/incomplete progress → return null status so client stops polling
            Map<String, Object> payload = Map.of(
                "status", "IDLE",
                "periodId", periodId,
                "message", "Không có tiến độ đang chạy"
            );
            return ResponseEntity.ok(ApiResponse.success(payload));
        }
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("status", p.getStatus().name());
        payload.put("periodId", p.getPeriodId());
        payload.put("step", p.getStep() == null ? "" : p.getStep());
        payload.put("percent", p.getPercent());
        payload.put("message", p.getMessage() == null ? "" : p.getMessage());
        payload.put("startedAt", p.getStartedAt().toString());
        payload.put("updatedAt", p.getUpdatedAt().toString());
        // Include cached result if available
        if (p.getResultJson() != null) {
            payload.put("resultJson", p.getResultJson());
        }
        return ResponseEntity.ok(ApiResponse.success(payload));
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

    /**
     * Server-paginated metrics endpoint.
     * Sorted DESC by createdAt so newest runs surface first.
     */
    @GetMapping("/metrics/page")
    @Operation(summary = "Lấy danh sách lịch sử chạy thuật toán có phân trang")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<AlgorithmMetricsDTO>>> getMetricsPage(
            @RequestParam(required = false) Integer periodId,
            Pageable pageable) {
        Page<AlgorithmMetricsDTO> page = autoSchedulingService.getMetricsPage(periodId,
                PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(page));
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

    @GetMapping("/config/page")
    @Operation(summary = "Lấy danh sách cấu hình thuật toán có phân trang")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<AlgorithmConfigDTO>>> getConfigsPage(
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(configService.getConfigsPage(pageable)));
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

    @PostMapping("/config/sync-descriptions")
    @Operation(summary = "Đồng bộ mô tả các tham số thuật toán theo phiên bản code hiện tại")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> syncDescriptions() {
        Map<String, String> result = configService.syncDescriptions();
        return ResponseEntity.ok(ApiResponse.success(result, "Đã đồng bộ " + result.size() + " mô tả"));
    }

    // ============================================================
    // Runtime Config Endpoints (Convenience endpoints)
    // ============================================================

    @GetMapping("/runtime-config")
    @Operation(summary = "Lấy cấu hình runtime của thuật toán (tất cả tham số chính)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AlgorithmConfigService.AlgorithmRuntimeConfig>> getRuntimeConfig() {
        AlgorithmConfigService.AlgorithmRuntimeConfig config = configService.getRuntimeConfig();
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/runtime-config")
    @Operation(summary = "Cập nhật cấu hình runtime của thuật toán")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AlgorithmConfigService.AlgorithmRuntimeConfig>> updateRuntimeConfig(
            @RequestBody AlgorithmConfigService.AlgorithmRuntimeConfig config) {
        configService.saveRuntimeConfig(config);
        return ResponseEntity.ok(ApiResponse.success(config, "Cập nhật cấu hình runtime thành công"));
    }

    @GetMapping("/auto-gen-config")
    @Operation(summary = "Lấy cấu hình tạo yêu cầu tự động (L01–L04 limits)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.hospital.scheduler.algorithm.AutoGenConfig>> getAutoGenConfig() {
        return ResponseEntity.ok(ApiResponse.success(configService.getAutoGenConfig().orElse(null)));
    }

    @PutMapping("/auto-gen-config")
    @Operation(summary = "Cập nhật cấu hình tạo yêu cầu tự động")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<com.hospital.scheduler.algorithm.AutoGenConfig>> saveAutoGenConfig(
            @RequestBody com.hospital.scheduler.algorithm.AutoGenConfig config) {
        configService.saveAutoGenConfig(config);
        return ResponseEntity.ok(ApiResponse.success(config, "Cập nhật cấu hình tự động thành công"));
    }

    // ============================================================
    // Algorithm Metrics Endpoints
    // ============================================================

    @GetMapping("/metrics/stats")
    @Operation(summary = "Lấy thống kê hiệu suất thuật toán")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAlgorithmStats() {
        Map<String, Object> stats = metricsService.getAlgorithmStatsSummary();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/metrics/best-algorithm")
    @Operation(summary = "Lấy thuật toán có hiệu suất tốt nhất")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBestAlgorithm() {
        String best = metricsService.getBestAlgorithm();
        double score = metricsService.calculatePerformanceScore(best);
        Map<String, Object> result = Map.of(
            "algorithmType", best,
            "performanceScore", Math.round(score * 100.0) / 100.0,
            "recommendation", "Thuật toán " + best + " có hiệu suất tốt nhất dựa trên coverage và balance"
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/config/audit")
    @Operation(summary = "Feature E: Lấy lịch sử thay đổi AlgorithmConfig")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<AlgorithmConfigAuditDTO>>> getConfigAudit(
            @RequestParam(required = false) String paramKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        var result = auditRepository.search(paramKey, pageable)
                .map(AlgorithmConfigAuditDTO::from);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
