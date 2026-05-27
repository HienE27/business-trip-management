package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
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

    @PostMapping
    @Operation(summary = "Chạy thuật toán xếp lịch tự động")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<AutoScheduleResponse>> autoSchedule(
            @Valid @RequestBody AutoScheduleRequestDTO request) {
        AutoScheduleResponse result = autoSchedulingService.autoSchedule(request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(result, "Xếp lịch tự động hoàn tất"));
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
