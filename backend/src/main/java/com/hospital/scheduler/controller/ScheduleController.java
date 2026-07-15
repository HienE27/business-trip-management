package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.BulkL01Request;
import com.hospital.scheduler.dto.request.BulkScheduleRequest;
import com.hospital.scheduler.dto.request.OverrideConflictRequest;
import com.hospital.scheduler.dto.request.ScheduleRequest;
import com.hospital.scheduler.dto.response.BulkL01Response;
import com.hospital.scheduler.dto.response.BulkScheduleResponse;
import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.dto.response.ExpertClinicWeeklyResponse;
import com.hospital.scheduler.dto.response.ScheduleResponse;
import com.hospital.scheduler.dto.response.StaffResponse;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.CompensationDayService;
import com.hospital.scheduler.service.ConflictDetectionService;
import com.hospital.scheduler.service.ScheduleDeleteService;
import com.hospital.scheduler.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "Schedule", description = "Quản lý lịch trực")
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleDeleteService scheduleDeleteService;
    private final ConflictDetectionService conflictDetectionService;
    private final CompensationDayService compensationDayService;
    private final AuthContextService authContextService;

    @GetMapping("/conflicts/check")
    @Operation(summary = "Kiểm tra xung đột lịch trong kỳ (query alias)")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<ConflictCheckResponse>> checkConflictsQuery(
            @RequestParam("periodId") Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.checkConflictsInPeriod(periodId)));
    }

    @GetMapping("/conflicts/check/{periodId}")
    @Operation(summary = "Kiểm tra xung đột lịch trong kỳ")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<ConflictCheckResponse>> checkConflicts(@PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.checkConflictsInPeriod(periodId)));
    }

    @GetMapping("/replacements/{periodId}")
    @Operation(summary = "Đề xuất người thay thế cho một ca")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_UPDATE + "')")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> findReplacements(
            @PathVariable Integer periodId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam String shiftTypeId,
            @RequestParam Integer originalStaffId,
            @RequestParam(required = false, defaultValue = "1") Integer requiredCount) {
        return ResponseEntity.ok(ApiResponse.success(
                scheduleService.findReplacements(periodId, workDate, shiftTypeId, originalStaffId, requiredCount)));
    }

    @GetMapping("/period/{periodId}")
    @Operation(summary = "Lấy danh sách lịch theo kỳ")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getSchedulesByPeriod(@PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getSchedulesByPeriod(periodId)));
    }

    @GetMapping("/compensation-days/{periodId}")
    @Operation(summary = "Lấy danh sách ngày nghỉ bù theo kỳ lịch")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<?>>> getCompensationDays(@PathVariable Integer periodId) {
        return ResponseEntity.ok(ApiResponse.success(compensationDayService.getCompensationDaysByPeriod(periodId)));
    }

    @PostMapping("/compensation-days")
    @Operation(summary = "Tạo thủ công ngày nghỉ bù cho một ca trực L01")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_CREATE + "')")
    public ResponseEntity<ApiResponse<?>> createCompensationDay(
            @RequestBody CompensationDayService.CreateCompensationDayRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(compensationDayService.createManual(req)));
    }

    @PutMapping("/compensation-days/{id}")
    @Operation(summary = "Cập nhật ngày nghỉ bù (đổi ngày bù hoặc ghi chú)")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_UPDATE + "')")
    public ResponseEntity<ApiResponse<?>> updateCompensationDay(
            @PathVariable Integer id,
            @RequestBody CompensationDayService.UpdateCompensationDayRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                compensationDayService.updateCompensationDate(id, req)));
    }

    @DeleteMapping("/compensation-days/{id}")
    @Operation(summary = "Xóa ngày nghỉ bù thủ công")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_DELETE + "')")
    public ResponseEntity<ApiResponse<?>> deleteCompensationDay(@PathVariable Integer id) {
        compensationDayService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa ngày nghỉ bù."));
    }

    @GetMapping("/period/{periodId}/date/{date}")
    @Operation(summary = "Lấy danh sách lịch theo kỳ và ngày")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getSchedulesByPeriodAndDate(
            @PathVariable Integer periodId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getSchedulesByPeriodAndDate(periodId, date)));
    }

    @GetMapping("/staff/{staffId}")
    @Operation(summary = "Lấy danh sách lịch theo nhân sự")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "') or @authContextService.isCurrentStaff(#staffId)")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getSchedulesByStaff(@PathVariable Integer staffId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getSchedulesByStaff(staffId)));
    }

    /**
     * Endpoint cá nhân hoá cho STAFF: chỉ cần {@code SCHEDULE_VIEW}, không cần
     * {@code PERIOD_VIEW} hay {@code STAFF_VIEW_ALL}. Trả về toàn bộ lịch của
     * currentUser theo kỳ + ca — dùng cho các trang Lịch trực 24/24, Lịch
     * thông tầm, Lịch PK dịch vụ, Lịch PK chuyên gia, Tóm tắt lịch khi user
     * chỉ có quyền xem cá nhân.
     *
     * <p>Theo M01-F05, nhân viên chỉ được "xem lịch cá nhân". Endpoint này
     * tự ràng buộc staffId = currentUser nên không thể xem lịch người khác.
     */
    @GetMapping("/me")
    @Operation(summary = "Lấy lịch cá nhân của currentUser (M01-F05 — dành cho STAFF)")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getMySchedule() {
        Integer me = authContextService.getCurrentStaffId();
        if (me == null) {
            throw new com.hospital.scheduler.exception.ResourceNotFoundException(
                "Không xác định được nhân sự hiện tại cho tài khoản này.");
        }
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getSchedulesByStaff(me)));
    }

    @GetMapping("/expert-clinic")
    @Operation(summary = "Lấy lịch phòng khám chuyên gia theo kỳ và chuyên khoa (M05-F04)")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> getExpertClinicSchedules(
            @RequestParam Integer periodId,
            @RequestParam(required = false) Integer specialtyId) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getExpertClinicSchedules(periodId, specialtyId)));
    }

    @GetMapping("/expert-clinic/weekly")
    @Operation(summary = "Lấy lịch phòng khám chuyên gia theo tuần (M05-F04)")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<ExpertClinicWeeklyResponse>> getExpertClinicWeeklyView(
            @RequestParam Integer periodId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) Integer specialtyId) {
        return ResponseEntity.ok(ApiResponse.success(
                scheduleService.getExpertClinicWeeklyView(periodId, weekStart, specialtyId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết lịch")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_VIEW + "')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> getScheduleById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.getScheduleById(id)));
    }

    @PostMapping
    @Operation(summary = "Tạo lịch mới")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_CREATE + "')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> createSchedule(@Valid @RequestBody ScheduleRequest request) {
        ScheduleResponse created = scheduleService.createSchedule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Tạo lịch thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật lịch")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_UPDATE + "')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> updateSchedule(
            @PathVariable Integer id,
            @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(scheduleService.updateSchedule(id, request), "Cập nhật lịch thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa lịch")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_DELETE + "')")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(@PathVariable Integer id) {
        scheduleDeleteService.deleteSchedule(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa lịch thành công"));
    }

    @DeleteMapping("/period/{periodId}")
    @Operation(summary = "Xóa tất cả lịch trong một kỳ lịch")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_DELETE + "')")
    public ResponseEntity<ApiResponse<Void>> deleteSchedulesByPeriod(@PathVariable Integer periodId) {
        scheduleService.deleteAllByPeriodId(periodId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa tất cả lịch của kỳ " + periodId));
    }

    @PutMapping("/{id}/override")
    @Operation(summary = "Override xung đột - giữ lịch bất chấp cảnh báo")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_UPDATE + "')")
    public ResponseEntity<ApiResponse<ScheduleResponse>> overrideConflict(
            @PathVariable Integer id,
            @Valid @RequestBody OverrideConflictRequest body) {
        return ResponseEntity.ok(ApiResponse.success(
                scheduleService.overrideConflict(id, body.getReason()), "Đã ghi nhận override xung đột"));
    }

    @PostMapping("/bulk-l01")
    @Operation(summary = "Bulk tạo lịch L01 (trực 24/24)")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_CREATE + "')")
    public ResponseEntity<ApiResponse<BulkL01Response>> createBulkL01(
            @Valid @RequestBody BulkL01Request request) {
        BulkL01Response response = scheduleService.createBulkL01(request);
        String msg = String.format("Tạo thành công %d/%d lịch L01",
                response.getSuccessCount(), response.getTotalCount());
        return ResponseEntity.ok(ApiResponse.success(response, msg));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk tạo lịch cho bất kỳ loại ca nào (L01/L02/L03/L04)")
    @PreAuthorize("hasAuthority('" + Permissions.SCHEDULE_CREATE + "')")
    public ResponseEntity<ApiResponse<BulkScheduleResponse>> createBulkSchedule(
            @RequestParam String shiftTypeId,
            @Valid @RequestBody BulkScheduleRequest request) {
        BulkScheduleResponse response = scheduleService.bulkCreateSchedules(request, shiftTypeId);
        String msg = String.format("Tạo thành công %d/%d lịch %s",
                response.getSuccessCount(), response.getTotalRequested(), shiftTypeId);
        return ResponseEntity.ok(ApiResponse.success(response, msg));
    }
}