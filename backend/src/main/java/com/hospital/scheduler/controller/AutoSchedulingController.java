package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO;
import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.entity.AlgorithmMetrics;
import com.hospital.scheduler.repository.AlgorithmMetricsRepository;
import com.hospital.scheduler.service.AutoSchedulingService;
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
    private final AlgorithmMetricsRepository metricsRepository;

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
}
