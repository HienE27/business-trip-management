package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.ScheduleExchangeDTO;
import com.hospital.scheduler.dto.response.ScheduleExchangeResponse;
import com.hospital.scheduler.entity.ScheduleExchange;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.ScheduleExchangeService;
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
@RequestMapping("/api/v1/schedule-exchanges")
@RequiredArgsConstructor
@Tag(name = "Schedule Exchange", description = "Quản lý yêu cầu đổi ca")
public class ScheduleExchangeController {

    private final ScheduleExchangeService exchangeService;
    private final AuthContextService authContextService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả yêu cầu đổi ca")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<ScheduleExchangeResponse>>> getAllExchanges() {
        return ResponseEntity.ok(ApiResponse.success(exchangeService.getAllExchanges()));
    }

    @GetMapping("/page")
    @Operation(summary = "Lấy danh sách yêu cầu đổi ca có phân trang")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_VIEW + "')")
    public ResponseEntity<ApiResponse<Page<ScheduleExchangeResponse>>> getExchangesPage(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(exchangeService.getExchangesPage(pageable)));
    }

    @GetMapping("/status-counts")
    @Operation(summary = "Đếm yêu cầu đổi ca theo trạng thái (toàn DB, không phân trang)")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_VIEW + "')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStatusCounts() {
        return ResponseEntity.ok(ApiResponse.success(exchangeService.getStatusCounts()));
    }

    @GetMapping("/pending")
    @Operation(summary = "Lấy danh sách yêu cầu đang chờ duyệt")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_APPROVE + "')")
    public ResponseEntity<ApiResponse<List<ScheduleExchangeResponse>>> getPendingExchanges() {
        return ResponseEntity.ok(ApiResponse.success(exchangeService.getPendingExchanges()));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Lấy yêu cầu theo trạng thái")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_VIEW + "')")
    public ResponseEntity<ApiResponse<List<ScheduleExchangeResponse>>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(ApiResponse.success(
                exchangeService.getExchangesByStatus(ScheduleExchange.ExchangeStatus.valueOf(status.toUpperCase()))));
    }

    @GetMapping("/requester/{requesterId}")
    @Operation(summary = "Lấy yêu cầu đổi ca theo người yêu cầu")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_VIEW + "') or @authContextService.isCurrentStaff(#requesterId)")
    public ResponseEntity<ApiResponse<List<ScheduleExchangeResponse>>> getByRequester(@PathVariable Integer requesterId) {
        return ResponseEntity.ok(ApiResponse.success(exchangeService.getExchangesByRequester(requesterId)));
    }

    @GetMapping("/target/{targetId}")
    @Operation(summary = "Lấy yêu cầu đổi ca theo người được đổi")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_VIEW + "') or @authContextService.isCurrentStaff(#targetId)")
    public ResponseEntity<ApiResponse<List<ScheduleExchangeResponse>>> getByTarget(@PathVariable Integer targetId) {
        return ResponseEntity.ok(ApiResponse.success(exchangeService.getExchangesByTarget(targetId)));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Lấy yêu cầu đổi ca liên quan đến người dùng")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_VIEW + "') or @authContextService.isCurrentStaff(#userId)")
    public ResponseEntity<ApiResponse<List<ScheduleExchangeResponse>>> getForUser(@PathVariable Integer userId) {
        authContextService.requireManagerOrSelfForUserData(userId);
        return ResponseEntity.ok(ApiResponse.success(exchangeService.getExchangesForUser(userId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết yêu cầu đổi ca")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_VIEW + "') or @authContextService.isCurrentStaffOwnerOfExchange(#id)")
    public ResponseEntity<ApiResponse<ScheduleExchangeResponse>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(exchangeService.getExchangeById(id)));
    }

    @PostMapping("/requester/{requesterId}")
    @Operation(summary = "Tạo yêu cầu đổi ca mới")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_CREATE + "') and @authContextService.isCurrentStaff(#requesterId)")
    public ResponseEntity<ApiResponse<ScheduleExchangeResponse>> create(
            @PathVariable Integer requesterId,
            @Valid @RequestBody ScheduleExchangeDTO dto) {
        authContextService.requireSelfOrManager(requesterId);
        ScheduleExchangeResponse created = exchangeService.createExchange(requesterId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Tạo yêu cầu đổi ca thành công"));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Duyệt yêu cầu đổi ca")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_APPROVE + "')")
    public ResponseEntity<ApiResponse<ScheduleExchangeResponse>> approve(
            @PathVariable Integer id,
            @RequestParam Integer reviewerId,
            @RequestParam(required = false) String reviewNote) {
        authContextService.requireManagerLikeReviewer(reviewerId);
        ScheduleExchangeResponse approved = exchangeService.approveExchange(id, reviewerId, reviewNote);
        return ResponseEntity.ok(ApiResponse.success(approved, "Duyệt yêu cầu đổi ca thành công"));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Từ chối yêu cầu đổi ca")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_APPROVE + "')")
    public ResponseEntity<ApiResponse<ScheduleExchangeResponse>> reject(
            @PathVariable Integer id,
            @RequestParam Integer reviewerId,
            @RequestParam(required = false) String reviewNote) {
        authContextService.requireManagerLikeReviewer(reviewerId);
        ScheduleExchangeResponse rejected = exchangeService.rejectExchange(id, reviewerId, reviewNote);
        return ResponseEntity.ok(ApiResponse.success(rejected, "Từ chối yêu cầu đổi ca thành công"));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Hủy yêu cầu đổi ca")
    @PreAuthorize("hasAuthority('" + Permissions.EXCHANGE_APPROVE + "') or (hasAuthority('" + Permissions.EXCHANGE_CANCEL_SELF + "') and @authContextService.isCurrentStaffOwnerOfExchange(#id))")
    public ResponseEntity<ApiResponse<ScheduleExchangeResponse>> cancel(@PathVariable Integer id) {
        ScheduleExchangeResponse cancelled = exchangeService.cancelExchange(id, authContextService.getCurrentStaff());
        return ResponseEntity.ok(ApiResponse.success(cancelled, "Hủy yêu cầu đổi ca thành công"));
    }
}