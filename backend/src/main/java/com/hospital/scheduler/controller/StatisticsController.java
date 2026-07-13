package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.StaffShiftStatistics;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
@Tag(name = "Statistics", description = "Thống kê lịch trực")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/staff")
    @Operation(summary = "Lấy thống kê ca trực theo nhân sự (M02-F05, M04-F05, M05-F05)")
    @PreAuthorize("hasAuthority('" + Permissions.REPORT_VIEW + "')")
    public ResponseEntity<ApiResponse<List<StaffShiftStatistics>>> getStaffStatistics(
            @RequestParam Integer periodId,
            @RequestParam(required = false) String shiftTypeId) {
        List<StaffShiftStatistics> stats = statisticsService.getStaffShiftStatistics(periodId, shiftTypeId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}