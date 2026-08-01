package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.LeaveRequestDTO;
import com.hospital.scheduler.dto.response.LeaveRequestResponse;
import com.hospital.scheduler.dto.response.ReplacementProposal;
import com.hospital.scheduler.entity.LeaveRequest;
import jakarta.annotation.Nullable;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.LeaveRequestService;
import com.hospital.scheduler.security.AuthContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/leave-requests")
@RequiredArgsConstructor
@Tag(name = "Leave Request", description = "Quản lý yêu cầu nghỉ phép")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final AuthContextService authContextService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả yêu cầu nghỉ phép")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getAllLeaveRequests() {
        return ResponseEntity.ok(ApiResponse.success(leaveRequestService.getAllLeaveRequests()));
    }

    @GetMapping("/page")
    @Operation(summary = "Lấy danh sách yêu cầu nghỉ phép có phân trang và filter")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_VIEW + "')")
    public ResponseEntity<ApiResponse<Page<LeaveRequestResponse>>> getLeaveRequestsPage(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Pageable pageable) {
        // If no filters, use the existing unfiltered pageable for optimal JPQL path
        if ((status == null || status.isBlank()) && (keyword == null || keyword.isBlank())) {
            return ResponseEntity.ok(ApiResponse.success(leaveRequestService.getLeaveRequestsPage(pageable)));
        }
        LeaveRequest.LeaveStatus parsedStatus = (status == null || status.isBlank()) ? null
                : LeaveRequest.LeaveStatus.valueOf(status.toUpperCase());
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword;
        return ResponseEntity.ok(ApiResponse.success(
                leaveRequestService.getLeaveRequestsPage(parsedStatus, kw,
                        org.springframework.data.domain.PageRequest.of(page, Math.min(size, 200)))));
    }

    @GetMapping("/status-counts")
    @Operation(summary = "Đếm yêu cầu nghỉ phép theo trạng thái (toàn DB, không phân trang)")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_VIEW + "')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStatusCounts() {
        return ResponseEntity.ok(ApiResponse.success(leaveRequestService.getStatusCounts()));
    }

    @GetMapping("/pending")
    @Operation(summary = "Lấy danh sách yêu cầu đang chờ duyệt")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_APPROVE + "')")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getPendingRequests() {
        return ResponseEntity.ok(ApiResponse.success(leaveRequestService.getPendingRequests()));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Lấy yêu cầu theo trạng thái")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(ApiResponse.success(
                leaveRequestService.getLeaveRequestsByStatus(LeaveRequest.LeaveStatus.valueOf(status.toUpperCase()))));
    }

    @GetMapping("/staff/{staffId}")
    @Operation(summary = "Lấy yêu cầu nghỉ phép theo nhân sự")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_VIEW + "') or @authContextService.isCurrentStaff(#staffId)")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getByStaff(@PathVariable Integer staffId) {
        authContextService.requireSelfOrManager(staffId);
        return ResponseEntity.ok(ApiResponse.success(leaveRequestService.getLeaveRequestsByStaff(staffId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết yêu cầu nghỉ phép")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_VIEW + "') or @authContextService.isCurrentStaffOwnerOfLeaveRequest(#id)")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(leaveRequestService.getLeaveRequestById(id)));
    }

    @PostMapping("/staff/{staffId}")
    @Operation(summary = "Tạo yêu cầu nghỉ phép mới")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_CREATE + "') and @authContextService.isCurrentStaff(#staffId)")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> create(
            @PathVariable Integer staffId,
            @Valid @RequestBody LeaveRequestDTO dto) {
        authContextService.requireSelfOrManager(staffId);
        LeaveRequestResponse created = leaveRequestService.createLeaveRequest(staffId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo yêu cầu nghỉ phép thành công"));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Duyệt yêu cầu nghỉ phép")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_APPROVE + "')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> approve(
            @PathVariable Integer id,
            @RequestParam Integer reviewerId,
            @RequestParam(required = false) String reviewNote) {
        authContextService.requireManagerLikeReviewer(reviewerId);
        LeaveRequestResponse approved = leaveRequestService.approveLeaveRequest(id, reviewerId, reviewNote);
        return ResponseEntity.ok(ApiResponse.success(approved, "Duyệt yêu cầu nghỉ phép thành công"));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Từ chối yêu cầu nghỉ phép")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_APPROVE + "')")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> reject(
            @PathVariable Integer id,
            @RequestParam Integer reviewerId,
            @RequestParam(required = false) String reviewNote) {
        authContextService.requireManagerLikeReviewer(reviewerId);
        LeaveRequestResponse rejected = leaveRequestService.rejectLeaveRequest(id, reviewerId, reviewNote);
        return ResponseEntity.ok(ApiResponse.success(rejected, "Từ chối yêu cầu nghỉ phép thành công"));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Hủy yêu cầu nghỉ phép")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_APPROVE + "') or (hasAuthority('" + Permissions.LEAVE_CANCEL_SELF + "') and @authContextService.isCurrentStaffOwnerOfLeaveRequest(#id))")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> cancel(@PathVariable Integer id) {
        LeaveRequestResponse cancelled = leaveRequestService.cancelLeaveRequest(id, authContextService.getCurrentStaff());
        return ResponseEntity.ok(ApiResponse.success(cancelled, "Hủy yêu cầu nghỉ phép thành công"));
    }

    @GetMapping("/{id}/replacements")
    @Operation(summary = "Tìm người thay thế cho các ca bị ảnh hưởng bởi nghỉ phép")
    @PreAuthorize("hasAuthority('" + Permissions.LEAVE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<ReplacementProposal>>> getReplacements(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(leaveRequestService.findReplacementsForLeave(id)));
    }
}