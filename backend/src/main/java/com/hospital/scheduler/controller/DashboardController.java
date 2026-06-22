package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.DashboardResponse;
import com.hospital.scheduler.dto.response.ScheduleAggregationResponse;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.service.DashboardService;
import com.hospital.scheduler.service.ReportExportService;
import com.hospital.scheduler.service.SchedulePdfExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Thống kê và báo cáo")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ReportExportService reportExportService;
    private final Optional<SchedulePdfExportService> schedulePdfExportService;

    @GetMapping
    @Operation(summary = "Lấy tổng quan dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @RequestParam(required = false) Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboardSummary(periodId)));
    }

    @GetMapping("/shifts")
    @Operation(summary = "Lấy thống kê các loại ca")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<DashboardResponse.ShiftStatistics>> getShiftStatistics(
            @RequestParam(required = false) Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getShiftStatistics(periodId)));
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
    @Operation(summary = "Xuất báo cáo lịch công tác ra Excel với bộ lọc")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportScheduleToExcel(
            @PathVariable Integer periodId,
            @RequestParam(required = false) String shiftTypeId,
            @RequestParam(required = false) Integer staffId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws Exception {
        byte[] excelData = reportExportService.exportScheduleToExcel(periodId, shiftTypeId, staffId, startDate, endDate);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "lich_cong_tac_" + periodId + ".xlsx");
        return ResponseEntity.ok().headers(headers).body(excelData);
    }

    @GetMapping("/export/schedule/{periodId}/pdf")
    @Operation(summary = "Xuất báo cáo lịch công tác ra PDF với bộ lọc")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportScheduleToPdf(
            @PathVariable Integer periodId,
            @RequestParam(required = false) String shiftTypeId,
            @RequestParam(required = false) Integer staffId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) throws Exception {
        SchedulePdfExportService pdfExportService = schedulePdfExportService
                .orElseThrow(() -> new BadRequestException("PDF export chưa khả dụng trong môi trường hiện tại"));

        byte[] pdfData = pdfExportService.exportScheduleToPdf(periodId, shiftTypeId, staffId, startDate, endDate);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "lich_cong_tac_" + periodId + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdfData);
    }

    @GetMapping("/export/workload/{periodId}")
    @Operation(summary = "Xuất báo cáo thống kê tải nhân sự ra Excel với bộ lọc")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<byte[]> exportWorkloadReportToExcel(
            @PathVariable Integer periodId,
            @RequestParam(required = false) String shiftTypeId,
            @RequestParam(required = false) Integer staffId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) throws Exception {
        byte[] excelData = reportExportService.exportWorkloadReportToExcel(periodId, shiftTypeId, staffId, startDate);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "thong_ke_tai_" + periodId + ".xlsx");
        return ResponseEntity.ok().headers(headers).body(excelData);
    }

    @GetMapping("/aggregate")
    @Operation(summary = "Tổng hợp lịch theo khoảng ngày (dùng cho view tuần/tháng không theo kỳ lịch)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
    public ApiResponse<ScheduleAggregationResponse> aggregateByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer staffId) {
        return ApiResponse.success(dashboardService.aggregateByDateRange(startDate, endDate, staffId));
    }
}
