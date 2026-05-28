package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.DashboardResponse;
import com.hospital.scheduler.service.DashboardService;
import com.hospital.scheduler.service.ReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final ReportExportService reportExportService;

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

    @GetMapping("/export/schedule/{periodId}")
    @Operation(summary = "Xuất báo cáo lịch công tác ra Excel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportScheduleToExcel(@PathVariable Integer periodId) throws Exception {
        byte[] excelData = reportExportService.exportScheduleToExcel(periodId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "lich_cong_tac_" + periodId + ".xlsx");
        return ResponseEntity.ok().headers(headers).body(excelData);
    }

    @GetMapping("/export/workload/{periodId}")
    @Operation(summary = "Xuất báo cáo thống kê tải nhân sự ra Excel")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportWorkloadReportToExcel(@PathVariable Integer periodId) throws Exception {
        byte[] excelData = reportExportService.exportWorkloadReportToExcel(periodId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "thong_ke_tai_" + periodId + ".xlsx");
        return ResponseEntity.ok().headers(headers).body(excelData);
    }
}
