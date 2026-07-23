package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.DashboardResponse;
import com.hospital.scheduler.dto.response.RolePermissionMatrixResponse;
import com.hospital.scheduler.dto.response.StaffShiftStatistics;
import com.hospital.scheduler.governance.service.ApprovalWorkflowService;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.AlgorithmMetricsService;
import com.hospital.scheduler.service.AutoSchedulingService;
import com.hospital.scheduler.service.CompensationDayService;
import com.hospital.scheduler.service.DashboardService;
import com.hospital.scheduler.service.RoleService;
import com.hospital.scheduler.service.SchedulePeriodService;
import com.hospital.scheduler.service.StatisticsService;
import com.hospital.scheduler.service.scheduling.SchedulingFeasibilityAnalyzer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Legacy path aliases — maps deprecated frontend URLs to canonical REST endpoints.
 *
 * <p>Many frontend pages still call paths like {@code /schedule-periods},
 * {@code /compensation-days}, {@code /scheduling/feasibility/period/1}, etc. that
 * no longer exist (or never did) on the backend. This controller reproduces the
 * legacy contract by delegating to canonical services so the frontend keeps
 * working without code changes.
 *
 * <p>Each method documents the canonical path it forwards to. Add new aliases
 * here rather than scattering {@code @GetMapping(...)} duplications across
 * domain controllers.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Legacy Alias", description = "Alias ngược cho các URL cũ của frontend")
public class LegacyPathAliasController {

    private final SchedulePeriodService periodService;
    private final CompensationDayService compensationDayService;
    private final DashboardService dashboardService;
    private final RoleService roleService;
    private final StatisticsService statisticsService;
    private final AlgorithmMetricsService metricsService;
    private final AutoSchedulingService autoSchedulingService;
    private final ApprovalWorkflowService approvalService;
    private final SchedulingFeasibilityAnalyzer feasibilityAnalyzer;

    // ── /api/v1/schedule-periods ────────────────────────────────────────────
    @GetMapping("/api/v1/schedule-periods")
    @Operation(summary = "Alias của GET /api/v1/periods")
    @PreAuthorize("hasAuthority('" + Permissions.PERIOD_VIEW + "')")
    public ResponseEntity<ApiResponse<Object>> getSchedulePeriodsAlias() {
        return ResponseEntity.ok(ApiResponse.success(periodService.getAllPeriods()));
    }

    // ── /api/v1/compensation-days ───────────────────────────────────────────
    @GetMapping("/api/v1/compensation-days")
    @Operation(summary = "Alias tổng hợp — frontend nên dùng /schedules/compensation-days/{periodId}")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<Map<Integer, Object>>> listAllCompensationDaysAlias() {
        var allPeriods = periodService.getAllPeriods();
        Map<Integer, Object> grouped = new LinkedHashMap<>();
        for (var p : allPeriods) {
            try {
                Integer pid = (Integer) p.getClass().getMethod("getId").invoke(p);
                grouped.put(pid, compensationDayService.getCompensationDaysByPeriod(pid));
            } catch (Exception ignored) {
                // skip periods that can't be introspected
            }
        }
        return ResponseEntity.ok(ApiResponse.success(grouped));
    }

    @GetMapping("/api/v1/compensation-days/period/{periodId}")
    @Operation(summary = "Alias của GET /api/v1/schedules/compensation-days/{periodId}")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<?>>> getCompensationDaysAlias(@PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(compensationDayService.getCompensationDaysByPeriod(periodId)));
    }

    // ── /api/v1/dashboard/summary ───────────────────────────────────────────
    @GetMapping("/api/v1/dashboard/summary")
    @Operation(summary = "Alias của GET /api/v1/dashboard")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_VIEW + "')")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboardSummaryAlias(
            @RequestParam(required = false) Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getDashboardSummary(periodId)));
    }

    // ── /api/v1/roles ───────────────────────────────────────────────────────
    @GetMapping("/api/v1/roles")
    @Operation(summary = "Alias của GET /api/v1/roles/permissions/matrix")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_VIEW + "')")
    public ResponseEntity<ApiResponse<RolePermissionMatrixResponse>> getRolesAlias() {
        return ResponseEntity.ok(ApiResponse.success(roleService.getPermissionMatrix()));
    }

    // ── /api/v1/app-config ──────────────────────────────────────────────────
    @GetMapping("/api/v1/app-config")
    @Operation(summary = "Alias tổng hợp cho /api/v1/app-config/*")
    @PreAuthorize("hasAuthority('" + Permissions.APP_CONFIG_VIEW + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAppConfigAlias() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("appName", "MedSchedule Pro");
        out.put("version", "1.1.0");
        out.put("availableModules", List.of(
                "M01-NhanSu", "M02-LichTruc2424", "M03-LichThongTam",
                "M04-PKDichVu", "M05-PKChuyenGia", "M06-Dashboard", "M07-AutoScheduling"
        ));
        out.put("endpointHint", Map.of(
                "emailConfig", "/api/v1/app-config/email",
                "dashboard", "/api/v1/dashboard",
                "periods", "/api/v1/periods"
        ));
        return ResponseEntity.ok(ApiResponse.success(out));
    }

    // ── /api/v1/shift-requirements ──────────────────────────────────────────
    @GetMapping("/api/v1/shift-requirements")
    @Operation(summary = "Alias — FE gọi không kèm periodId, trả mảng rỗng kèm hướng dẫn")
    @PreAuthorize("hasAuthority('" + Permissions.PERIOD_VIEW + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listAllShiftRequirementsAlias(
            @RequestParam(required = false) Integer periodId) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (periodId == null) {
            body.put("items", List.of());
            body.put("hint", "Provide ?periodId=<id> to load shift requirements.");
            body.put("canonical", "/api/v1/shift-requirements/period/{periodId}");
        } else {
            // Delegate to compensationDayService placeholder; FE should follow hint instead.
            body.put("items", List.of());
            body.put("periodId", periodId);
            body.put("canonical", "/api/v1/shift-requirements/period/{periodId}");
        }
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    // ── /api/v1/statistics ──────────────────────────────────────────────────
    @GetMapping("/api/v1/statistics")
    @Operation(summary = "Alias tổng hợp của /api/v1/statistics/staff")
    @PreAuthorize("hasAuthority('" + Permissions.REPORT_VIEW + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatisticsAlias(
            @RequestParam(required = false) Integer periodId,
            @RequestParam(required = false) String shiftTypeId) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (periodId != null) {
            List<StaffShiftStatistics> staff = statisticsService.getStaffShiftStatistics(periodId, shiftTypeId);
            body.put("staff", staff);
        } else {
            body.put("staff", List.of());
            body.put("hint", "Provide ?periodId=<id> to load staff statistics.");
        }
        body.put("shiftTypeId", shiftTypeId);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    // ── /api/v1/scheduling/heatmap ──────────────────────────────────────────
    @GetMapping("/api/v1/scheduling/heatmap")
    @Operation(summary = "Alias không có periodId — trả metadata")
    @PreAuthorize("hasAuthority('" + Permissions.DASHBOARD_VIEW + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> schedulingHeatmapRootAlias() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("available", true);
        body.put("endpoint", "/api/v1/scheduling/heatmap/{periodId}");
        body.put("metrics", List.of("load", "weekend", "consecutive"));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    // ── /api/v1/scheduling/feasibility/period/{periodId} ────────────────────
    @GetMapping("/api/v1/scheduling/feasibility/period/{periodId}")
    @Operation(summary = "Alias của GET /api/v1/auto-schedule/feasibility/{periodId}")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<Object>> feasibilityAlias(@PathVariable Integer periodId) {
        try {
            SchedulingFeasibilityAnalyzer.FeasibilityReport report =
                    feasibilityAnalyzer.analyzeFeasibility(periodId);
            return ResponseEntity.ok(ApiResponse.success(report));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.success(Map.of(
                    "feasible", false,
                    "periodId", periodId,
                    "error", e.getMessage()
            )));
        }
    }

    // ── /api/v1/scheduling/metrics ──────────────────────────────────────────
    @GetMapping("/api/v1/scheduling/metrics")
    @Operation(summary = "Alias của GET /api/v1/auto-schedule/metrics")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<Object>> schedulingMetricsAlias() {
        return ResponseEntity.ok(ApiResponse.success(autoSchedulingService.getAllMetrics()));
    }

    // ── /api/v1/scheduling/replay ───────────────────────────────────────────
    @GetMapping("/api/v1/scheduling/replay")
    @Operation(summary = "Alias root cho replay — cần sessionKey")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> schedulingReplayRootAlias() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("available", true);
        body.put("endpoints", Map.of(
                "session", "/api/v1/sandbox/{sessionKey}/replay",
                "frame", "/api/v1/sandbox/{sessionKey}/replay/{iteration}",
                "explain", "/api/v1/explain/replay/{sessionKey}/{iteration}"
        ));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    // ── /api/v1/auto-schedule/periods ───────────────────────────────────────
    @GetMapping("/api/v1/auto-schedule/periods")
    @Operation(summary = "Alias của GET /api/v1/periods")
    @PreAuthorize("hasAuthority('" + Permissions.PERIOD_VIEW + "')")
    public ResponseEntity<ApiResponse<Object>> autoSchedulePeriodsAlias() {
        return ResponseEntity.ok(ApiResponse.success(periodService.getAllPeriods()));
    }

    // ── /api/v1/auto-schedule/algorithms ────────────────────────────────────
    @GetMapping({"/api/v1/auto-schedule/algorithms", "/api/v1/auto-scheduling/algorithms"})
    @Operation(summary = "Alias — liệt kê các thuật toán auto-schedule được hỗ trợ + best algorithm")
    @PreAuthorize("hasAuthority('" + Permissions.AUTO_SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> autoScheduleAlgorithmsAlias() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supported", List.of(
                "GREEDY", "ROUND_ROBIN", "FAIR_ROUND_ROBIN", "FAIR", "FAIR_GREEDY",
                "CSP_MRV_FC", "CSP", "V10_LOCAL_SEARCH", "V10"
        ));
        try {
            String best = metricsService.getBestAlgorithm();
            double score = metricsService.calculatePerformanceScore(best);
            body.put("bestAlgorithm", Map.of(
                    "name", best,
                    "performanceScore", Math.round(score * 100.0) / 100.0
            ));
        } catch (Exception e) {
            body.put("bestAlgorithm", null);
        }
        body.put("configEndpoint", "/api/v1/auto-schedule/config");
        body.put("runtimeConfigEndpoint", "/api/v1/auto-schedule/runtime-config");
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    // ── /api/v1/explain/algorithms ──────────────────────────────────────────
    @GetMapping("/api/v1/explain/algorithms")
    @Operation(summary = "Alias — liệt kê các thuật toán mà Explain module hỗ trợ")
    public ResponseEntity<ApiResponse<Map<String, Object>>> explainAlgorithmsAlias() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("explainableAlgorithms", List.of(
                "GREEDY", "FAIR_GREEDY", "CSP_MRV_FC", "V10_LOCAL_SEARCH"
        ));
        body.put("endpoints", Map.of(
                "assignment", "/api/v1/explain/assignment/{assignmentId}",
                "whyNot", "/api/v1/explain/why-not?slotId=&staffId=",
                "ranking", "/api/v1/explain/ranking/{slotId}?sessionKey=",
                "replay", "/api/v1/explain/replay/{sessionKey}/{iteration}",
                "query", "POST /api/v1/explain/query"
        ));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    // ── /api/v1/explain (root) ──────────────────────────────────────────────
    @GetMapping("/api/v1/explain")
    @Operation(summary = "Alias root cho module explain")
    public ResponseEntity<ApiResponse<Map<String, Object>>> explainRootAlias() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("available", true);
        body.put("module", "explain");
        body.put("endpoints", Map.of(
                "algorithms", "/api/v1/explain/algorithms",
                "assignment", "/api/v1/explain/assignment/{assignmentId}",
                "whyNot", "/api/v1/explain/why-not",
                "ranking", "/api/v1/explain/ranking/{slotId}",
                "replay", "/api/v1/explain/replay/{sessionKey}/{iteration}",
                "query", "POST /api/v1/explain/query"
        ));
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    // ── /api/v1/governance (root) ───────────────────────────────────────────
    @GetMapping("/api/v1/governance")
    @Operation(summary = "Alias root cho module governance — trả tổng quan + endpoint chính")
    public ResponseEntity<ApiResponse<Map<String, Object>>> governanceRootAlias() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("available", true);
        body.put("module", "governance");
        body.put("endpoints", Map.of(
                "auditSearch", "/api/v1/governance/audit",
                "auditSummary", "/api/v1/governance/audit/summary",
                "approvalPending", "/api/v1/governance/approval/pending",
                "approvalPendingCount", "/api/v1/governance/approval/pending/count",
                "configVersions", "/api/v1/governance/config/versions",
                "configVersionsHistory", "/api/v1/governance/config/versions/period/{periodId}"
        ));
        try {
            long pending = approvalService.countPending();
            body.put("pendingApprovalCount", pending);
        } catch (Exception e) {
            body.put("pendingApprovalCount", 0);
        }
        return ResponseEntity.ok(ApiResponse.success(body));
    }
}