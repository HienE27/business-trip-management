package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.AlgoConfigRequest;
import com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO;
import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import com.hospital.scheduler.dto.request.SaveTemplateRequest;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.entity.AlgorithmConfig;
import com.hospital.scheduler.entity.AlgorithmMetrics;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import com.hospital.scheduler.repository.AlgorithmMetricsRepository;
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auto-schedule")
@RequiredArgsConstructor
@Tag(name = "Auto Scheduling", description = "Xếp lịch tự động")
public class AutoSchedulingController {

    private final AutoSchedulingService autoSchedulingService;
    private final ScheduleTemplateService scheduleTemplateService;
    private final AlgorithmMetricsRepository metricsRepository;
    private final AlgorithmConfigRepository configRepository;

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
    public ResponseEntity<ApiResponse<com.hospital.scheduler.dto.response.ScheduleTemplateResponse>> saveAsTemplate(
            @Valid @RequestBody SaveTemplateRequest request) {
        var result = scheduleTemplateService.saveTemplateFromGenerated(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Lưu mẫu lịch thành công"));
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorkloadChartData(@PathVariable Integer periodId) {
        Map<String, Object> chartData = autoSchedulingService.getWorkloadChartData(periodId);
        return ResponseEntity.ok(ApiResponse.success(chartData));
    }

    @GetMapping("/metrics/period/{periodId}")
    @Operation(summary = "Lấy lịch sử chạy thuật toán theo kỳ")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMetricsByPeriod(@PathVariable Integer periodId) {
        List<AlgorithmMetrics> metrics = metricsRepository.findByPeriodId(periodId);
        List<Map<String, Object>> result = metrics.stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "algorithmType", m.getAlgorithmType(),
                        "executionTimeMs", m.getExecutionTimeMs(),
                        "coverageRate", m.getCoverageRate(),
                        "balanceScore", m.getBalanceScore(),
                        "conflictCount", m.getConflictCount(),
                        "createdAt", m.getCreatedAt()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/metrics")
    @Operation(summary = "Lấy tất cả lịch sử chạy thuật toán")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllMetrics() {
        List<AlgorithmMetrics> metrics = metricsRepository.findAll();
        List<Map<String, Object>> result = metrics.stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getId(),
                        "algorithmType", m.getAlgorithmType(),
                        "executionTimeMs", m.getExecutionTimeMs(),
                        "coverageRate", m.getCoverageRate(),
                        "balanceScore", m.getBalanceScore(),
                        "conflictCount", m.getConflictCount(),
                        "createdAt", m.getCreatedAt()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    // ============================================================
    // AlgorithmConfig CRUD Endpoints
    // ============================================================

    @GetMapping("/config")
    @Operation(summary = "Lấy tất cả cấu hình thuật toán")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllConfigs() {
        List<AlgorithmConfig> configs = configRepository.findAll();
        List<Map<String, Object>> result = configs.stream()
                .map(c -> Map.<String, Object>of(
                        "paramKey", c.getParamKey(),
                        "paramValue", c.getParamValue(),
                        "valueType", c.getValueType().name(),
                        "description", c.getDescription() != null ? c.getDescription() : "",
                        "updatedBy", c.getUpdatedBy() != null ? c.getUpdatedBy().getFullName() : null,
                        "createdAt", c.getCreatedAt(),
                        "updatedAt", c.getUpdatedAt()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/config/{paramKey}")
    @Operation(summary = "Lấy cấu hình theo paramKey")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfigById(@PathVariable String paramKey) {
        AlgorithmConfig config = configRepository.findByParamKey(paramKey)
                .orElseThrow(() -> new com.hospital.scheduler.exception.ResourceNotFoundException(
                        "Không tìm thấy cấu hình với paramKey: " + paramKey));
        Map<String, Object> result = Map.of(
                "paramKey", config.getParamKey(),
                "paramValue", config.getParamValue(),
                "valueType", config.getValueType().name(),
                "description", config.getDescription() != null ? config.getDescription() : "",
                "updatedBy", config.getUpdatedBy() != null ? config.getUpdatedBy().getFullName() : null,
                "createdAt", config.getCreatedAt(),
                "updatedAt", config.getUpdatedAt()
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/config")
    @Operation(summary = "Tạo mới cấu hình thuật toán")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createConfig(
            @Valid @RequestBody AlgoConfigRequest request) {
        if (configRepository.findByParamKey(request.getParamKey()).isPresent()) {
            throw new com.hospital.scheduler.exception.BadRequestException(
                    "Cấu hình với paramKey '" + request.getParamKey() + "' đã tồn tại");
        }
        AlgorithmConfig config = AlgorithmConfig.builder()
                .paramKey(request.getParamKey())
                .paramValue(request.getParamValue())
                .valueType(request.getValueType())
                .description(request.getDescription())
                .build();
        AlgorithmConfig saved = configRepository.save(config);
        Map<String, Object> result = Map.of(
                "paramKey", saved.getParamKey(),
                "paramValue", saved.getParamValue(),
                "valueType", saved.getValueType().name(),
                "description", saved.getDescription() != null ? saved.getDescription() : "",
                "createdAt", saved.getCreatedAt(),
                "updatedAt", saved.getUpdatedAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Tạo cấu hình thành công"));
    }

    @PutMapping("/config/{paramKey}")
    @Operation(summary = "Cập nhật cấu hình thuật toán")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateConfig(
            @PathVariable String paramKey,
            @Valid @RequestBody AlgoConfigRequest request) {
        AlgorithmConfig config = configRepository.findByParamKey(paramKey)
                .orElseThrow(() -> new com.hospital.scheduler.exception.ResourceNotFoundException(
                        "Không tìm thấy cấu hình với paramKey: " + paramKey));
        config.setParamValue(request.getParamValue());
        config.setValueType(request.getValueType());
        config.setDescription(request.getDescription());
        AlgorithmConfig saved = configRepository.save(config);
        Map<String, Object> result = Map.of(
                "paramKey", saved.getParamKey(),
                "paramValue", saved.getParamValue(),
                "valueType", saved.getValueType().name(),
                "description", saved.getDescription() != null ? saved.getDescription() : "",
                "updatedBy", saved.getUpdatedBy() != null ? saved.getUpdatedBy().getFullName() : null,
                "createdAt", saved.getCreatedAt(),
                "updatedAt", saved.getUpdatedAt()
        );
        return ResponseEntity.ok(ApiResponse.success(result, "Cập nhật cấu hình thành công"));
    }

    @DeleteMapping("/config/{paramKey}")
    @Operation(summary = "Xóa cấu hình thuật toán")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable String paramKey) {
        if (!configRepository.existsById(paramKey)) {
            throw new com.hospital.scheduler.exception.ResourceNotFoundException(
                    "Không tìm thấy cấu hình với paramKey: " + paramKey);
        }
        configRepository.deleteById(paramKey);
        return ResponseEntity.ok(ApiResponse.success((Void) null, "Xóa cấu hình thành công"));
    }
}
