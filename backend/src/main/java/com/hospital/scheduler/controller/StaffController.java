package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.StaffRequest;
import com.hospital.scheduler.dto.request.StaffSearchRequest;
import com.hospital.scheduler.dto.response.ResetPasswordResponse;
import com.hospital.scheduler.dto.response.StaffResponse;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.AuthService;
import com.hospital.scheduler.service.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@Tag(name = "Staff", description = "Quản lý nhân sự")
public class StaffController {

    private final StaffService staffService;
    private final AuthService authService;

    @GetMapping
    @Operation(summary = "Lấy danh sách nhân sự")
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_VIEW + "')")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getAllStaff() {
        return ResponseEntity.ok(ApiResponse.success(staffService.getAllStaff()));
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách nhân sự đang hoạt động")
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_VIEW + "')")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getActiveStaff() {
        return ResponseEntity.ok(ApiResponse.success(staffService.getActiveStaff()));
    }

    @GetMapping("/status-counts")
    @Operation(summary = "Đếm nhân sự theo trạng thái (toàn DB, không phân trang)")
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_VIEW + "')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStatusCounts() {
        return ResponseEntity.ok(ApiResponse.success(staffService.getStatusCounts()));
    }

    @GetMapping("/specialty-counts")
    @Operation(summary = "Đếm nhân sự theo chuyên khoa (toàn DB, không phân trang)")
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_VIEW + "')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getSpecialtyCounts() {
        return ResponseEntity.ok(ApiResponse.success(staffService.getSpecialtyCounts()));
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm và lọc nhân sự")
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_VIEW_ALL + "')")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> searchStaffs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer specialtyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String position) {
        StaffSearchRequest request = StaffSearchRequest.builder()
                .keyword(keyword)
                .specialtyId(specialtyId)
                .status(status)
                .role(role)
                .position(position)
                .build();
        return ResponseEntity.ok(ApiResponse.success(staffService.searchStaffs(request)));
    }

    @GetMapping("/search-page")
    @Operation(summary = "Tìm kiếm nhân sự có phân trang")
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_VIEW_ALL + "')")
    public ResponseEntity<ApiResponse<Page<StaffResponse>>> searchStaffsPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer specialtyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String position,
            Pageable pageable) {
        StaffSearchRequest request = StaffSearchRequest.builder()
                .keyword(keyword)
                .specialtyId(specialtyId)
                .status(status)
                .role(role)
                .position(position)
                .build();
        return ResponseEntity.ok(ApiResponse.success(staffService.searchStaffsPage(request, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết nhân sự")
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_VIEW_ALL + "') or @authContextService.isCurrentStaff(#id)")
    public ResponseEntity<ApiResponse<StaffResponse>> getStaffById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(staffService.getStaffById(id)));
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy thông tin nhân sự hiện tại")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StaffResponse>> getCurrentStaff(
            @AuthenticationPrincipal String username) {
        return ResponseEntity.ok(ApiResponse.success(staffService.getStaffByUsername(username)));
    }

    @PostMapping
    @Operation(summary = "Tạo nhân sự mới")
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_CREATE + "')")
    public ResponseEntity<ApiResponse<StaffResponse>> createStaff(
            @Valid @RequestBody StaffRequest request,
            @RequestParam(required = false) List<String> roles) {
        List<String> finalRoles = roles;
        if (finalRoles == null || finalRoles.isEmpty()) {
            finalRoles = request.getRoles();
        }
        StaffResponse created = staffService.createStaff(request, finalRoles);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created, "Tạo nhân sự thành công"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật nhân sự")
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_UPDATE + "') or @authContextService.isCurrentStaff(#id)")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaff(
            @PathVariable Integer id,
            @Valid @RequestBody StaffRequest request) {
        return ResponseEntity.ok(ApiResponse.success(staffService.updateStaff(id, request), "Cập nhật nhân sự thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa nhân sự (soft delete)")
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_DELETE + "')")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(@PathVariable Integer id) {
        staffService.deleteStaff(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa nhân sự thành công"));
    }

    /**
     * Admin-only password reset. Generates a temporary password, hashes + persists
     * it, returns the plaintext ONCE so the admin can deliver it to the staff
     * member. The endpoint refuses to reset the caller's own password — admins
     * must use /auth/change-password for their own credentials so the current
     * password is verified.
     */
    @PostMapping("/{id}/reset-password")
    @Operation(
            summary = "Đặt lại mật khẩu nhân sự (admin)",
            description = "Tạo mật khẩu tạm thời và trả về mật khẩu dạng plaintext cho admin chuyển cho nhân sự. " +
                    "Audit-logged với actor là admin đang thao tác."
    )
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_UPDATE + "')")
    public ResponseEntity<ApiResponse<ResetPasswordResponse>> resetPassword(@PathVariable Integer id) {
        ResetPasswordResponse response = authService.resetPassword(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Đặt lại mật khẩu thành công"));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nhập danh sách nhân sự từ Excel hoặc CSV")
    @PreAuthorize("hasAuthority('" + Permissions.STAFF_IMPORT + "')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importStaffs(
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = staffService.importStaffs(file);
        return ResponseEntity.ok(ApiResponse.success(result, (String) result.get("message")));
    }
}