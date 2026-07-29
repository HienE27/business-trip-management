package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.DashboardResponse;
import com.hospital.scheduler.dto.response.ScheduleAggregationResponse;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.security.Permissions;
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
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_VIEW + "')")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @RequestParam(required = false) Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboardSummary(periodId)));
    }

    @GetMapping("/shifts")
    @Operation(summary = "Lấy thống kê các loại ca")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_VIEW + "')")
    public ResponseEntity<ApiResponse<DashboardResponse.ShiftStatistics>> getShiftStatistics(
            @RequestParam(required = false) Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getShiftStatistics(periodId)));
    }

    @GetMapping("/leave-requests")
    @Operation(summary = "Lấy thống kê yêu cầu nghỉ phép")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_VIEW + "')")
    public ResponseEntity<ApiResponse<DashboardResponse.LeaveRequestStatistics>> getLeaveRequestStatistics() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getLeaveRequestStatistics()));
    }

    @GetMapping("/workload/period/{periodId}")
    @Operation(summary = "Lấy thống kê workload nhân sự theo kỳ")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_AGGREGATE + "')")
    public ResponseEntity<ApiResponse<List<DashboardResponse.StaffWorkloadStatistics>>> getStaffWorkload(
            @PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getStaffWorkloadByPeriod(periodId)));
    }

    @GetMapping("/workload/period/{periodId}/page")
    @Operation(summary = "Lấy thống kê workload nhân sự theo kỳ có phân trang")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_AGGREGATE + "')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<DashboardResponse.StaffWorkloadStatistics>>> getStaffWorkloadPage(
            @PathVariable Integer periodId,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getStaffWorkloadByPeriodPage(periodId, pageable)));
    }

    @GetMapping("/shift-type/{shiftTypeId}/statistics")
    @Operation(summary = "Thống kê chi tiết theo loại ca (L03/L04) theo tuần hoặc tháng — phục vụ M04-F05 và M05-F05")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_AGGREGATE + "')")
    public ResponseEntity<ApiResponse<DashboardResponse.ShiftTypeDetailStatistics>> getShiftTypeStatistics(
            @PathVariable String shiftTypeId,
            @RequestParam Integer periodId,
            @RequestParam(required = false, defaultValue = "month") String groupBy) {
        return ResponseEntity.ok(ApiResponse.success(
                dashboardService.getShiftTypeDetailStatistics(periodId, shiftTypeId, groupBy)));
    }

    @GetMapping("/periods")
    @Operation(summary = "Lấy tóm tắt các kỳ lịch")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_VIEW + "')")
    public ResponseEntity<ApiResponse<List<DashboardResponse.PeriodSummary>>> getPeriodSummaries() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getPeriodSummaries()));
    }

    @GetMapping("/periods/{periodId}")
    @Operation(summary = "Lấy tóm tắt 1 kỳ lịch (scheduleCount + staffCount aggregate) — dùng cho KPI page")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_VIEW + "')")
    public ResponseEntity<ApiResponse<DashboardResponse.PeriodSummary>> getPeriodSummary(
            @PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getPeriodSummary(periodId)));
    }

    @GetMapping("/heatmap/period/{periodId}")
    @Operation(summary = "Lấy dữ liệu heatmap lịch trực")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_AGGREGATE + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHeatmapData(@PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getScheduleHeatmapData(periodId)));
    }

    @GetMapping("/export/schedule/{periodId}")
    @Operation(summary = "Xuất báo cáo lịch công tác ra Excel với bộ lọc")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_EXPORT + "')")
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
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_EXPORT + "')")
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
    @PreAuthorize("hasAuthority('" + Permissions.REPORT_EXPORT + "')")
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
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_AGGREGATE + "')")
    public ApiResponse<ScheduleAggregationResponse> aggregateByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer staffId) {
        return ApiResponse.success(dashboardService.aggregateByDateRange(startDate, endDate, staffId));
    }
}
