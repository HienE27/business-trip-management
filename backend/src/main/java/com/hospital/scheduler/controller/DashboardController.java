package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.DashboardResponse;
import com.hospital.scheduler.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Thống kê và báo cáo")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Lấy tổng quan dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboardSummary()));
    }

    @GetMapping("/shifts")
    @Operation(summary = "Lấy thống kê các loại ca")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<DashboardResponse.ShiftStatistics>> getShiftStatistics() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getShiftStatistics()));
    }

    @GetMapping("/leave-requests")
    @Operation(summary = "Lấy thống kê yêu cầu nghỉ phép")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<DashboardResponse.LeaveRequestStatistics>> getLeaveRequestStatistics() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getLeaveRequestStatistics()));
    }

    @GetMapping("/workload/period/{periodId}")
    @Operation(summary = "Lấy thống kê workload nhân sự theo kỳ")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<DashboardResponse.StaffWorkloadStatistics>>> getStaffWorkload(
            @PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getStaffWorkloadByPeriod(periodId)));
    }

    @GetMapping("/periods")
    @Operation(summary = "Lấy tóm tắt các kỳ lịch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<DashboardResponse.PeriodSummary>>> getPeriodSummaries() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getPeriodSummaries()));
    }

    @GetMapping("/heatmap/period/{periodId}")
    @Operation(summary = "Lấy dữ liệu heatmap lịch trực")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHeatmapData(@PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getScheduleHeatmapData(periodId)));
    }
}
