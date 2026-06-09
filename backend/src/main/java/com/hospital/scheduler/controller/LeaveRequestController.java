package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.LeaveRequestDTO;
import com.hospital.scheduler.dto.response.LeaveRequestResponse;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.service.LeaveRequestService;
import com.hospital.scheduler.security.AuthContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/leave-requests")
@RequiredArgsConstructor
@Tag(name = "Leave Request", description = "Quản lý yêu cầu nghỉ phép")
public class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;
    private final AuthContextService authContextService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả yêu cầu nghỉ phép")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getAllLeaveRequests() {
        return ResponseEntity.ok(ApiResponse.success(leaveRequestService.getAllLeaveRequests()));
    }

    @GetMapping("/pending")
    @Operation(summary = "Lấy danh sách yêu cầu đang chờ duyệt")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getPendingRequests() {
        return ResponseEntity.ok(ApiResponse.success(leaveRequestService.getPendingRequests()));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Lấy yêu cầu theo trạng thái")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(ApiResponse.success(
                leaveRequestService.getLeaveRequestsByStatus(LeaveRequest.LeaveStatus.valueOf(status.toUpperCase()))));
    }

    @GetMapping("/staff/{staffId}")
    @Operation(summary = "Lấy yêu cầu nghỉ phép theo nhân sự")
    public ResponseEntity<ApiResponse<List<LeaveRequestResponse>>> getByStaff(@PathVariable Integer staffId) {
        authContextService.requireSelfOrManager(staffId);
        return ResponseEntity.ok(ApiResponse.success(leaveRequestService.getLeaveRequestsByStaff(staffId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết yêu cầu nghỉ phép")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(leaveRequestService.getLeaveRequestById(id)));
    }

    @PostMapping("/staff/{staffId}")
    @Operation(summary = "Tạo yêu cầu nghỉ phép mới")
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
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> cancel(@PathVariable Integer id) {
        LeaveRequestResponse cancelled = leaveRequestService.cancelLeaveRequest(id, authContextService.getCurrentStaff());
        return ResponseEntity.ok(ApiResponse.success(cancelled, "Hủy yêu cầu nghỉ phép thành công"));
    }
}
