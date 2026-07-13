package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.DataIntegrityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/data-integrity")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Data Integrity", description = "Sửa chữa & cleanup data")
public class DataIntegrityController {

    private final DataIntegrityService dataIntegrityService;
    private final AuthContextService authContextService;

    @PostMapping("/cleanup-staff-role-orphans")
    @Operation(summary = "Xóa orphan rows trong bảng staff_role (role_id hoặc staff_id không tồn tại)")
    @PreAuthorize("hasAuthority('" + Permissions.DATA_INTEGRITY_RUN + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cleanupStaffRoleOrphans() {
        log.warn("Admin triggered staff_role cleanup");
        Map<String, Object> report = dataIntegrityService.cleanupStaffRoleOrphans();
        return ResponseEntity.ok(ApiResponse.success(report,
                "Đã dọn orphan rows: " +
                "missing-role=" + report.get("removedMissingRole") +
                ", missing-staff=" + report.get("removedMissingStaff")));
    }

    @GetMapping("/orphan-schedule-exchanges")
    @Operation(summary = "Báo cáo schedule_exchange tham chiếu tới schedule đã xóa")
    @PreAuthorize("hasAuthority('" + Permissions.DATA_INTEGRITY_RUN + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reportOrphanScheduleExchanges() {
        return ResponseEntity.ok(ApiResponse.success(
                dataIntegrityService.reportOrphanScheduleExchanges()));
    }

    @PostMapping("/cancel-orphan-schedule-exchanges")
    @Operation(summary = "Cancel tất cả schedule_exchange có reference schedule đã xóa (lý do: 'Lịch trực liên quan đã bị xóa')")
    @PreAuthorize("hasAuthority('" + Permissions.DATA_INTEGRITY_RUN + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelOrphanScheduleExchanges() {
        Integer adminId = authContextService.getCurrentStaff() != null
                ? authContextService.getCurrentStaff().getId() : null;
        log.warn("Admin {} triggered cancel orphan exchanges", adminId);
        Map<String, Object> report = dataIntegrityService.cancelOrphanScheduleExchanges(adminId);
        return ResponseEntity.ok(ApiResponse.success(report,
                "Đã hủy " + report.get("cancelledCount") + " yêu cầu đổi ca có lịch trực đã bị xóa"));
    }
}