package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.StaffRequest;
import com.hospital.scheduler.dto.request.StaffSearchRequest;
import com.hospital.scheduler.dto.response.StaffResponse;
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

    @GetMapping
    @Operation(summary = "Lấy danh sách nhân sự")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getAllStaff() {
        return ResponseEntity.ok(ApiResponse.success(staffService.getAllStaff()));
    }

    @GetMapping("/active")
    @Operation(summary = "Lấy danh sách nhân sự đang hoạt động")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getActiveStaff() {
        return ResponseEntity.ok(ApiResponse.success(staffService.getActiveStaff()));
    }

    @GetMapping("/status-counts")
    @Operation(summary = "Đếm nhân sự theo trạng thái (toàn DB, không phân trang)")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStatusCounts() {
        return ResponseEntity.ok(ApiResponse.success(staffService.getStatusCounts()));
    }

    @GetMapping("/search")
    @Operation(summary = "Tìm kiếm và lọc nhân sự")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
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

    /**
     * Paginated variant — drives the &lt;Pagination&gt; widget on the Nhân sự page.
     * Same query params as /search; identical response shape apart from the
     * wrapping {@link Page} metadata that the pagination component needs.
     */
    @GetMapping("/search-page")
    @Operation(summary = "Tìm kiếm nhân sự có phân trang")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
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
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaff(
            @PathVariable Integer id,
            @Valid @RequestBody StaffRequest request) {
        return ResponseEntity.ok(ApiResponse.success(staffService.updateStaff(id, request), "Cập nhật nhân sự thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa nhân sự (soft delete)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteStaff(@PathVariable Integer id) {
        staffService.deleteStaff(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa nhân sự thành công"));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nhập danh sách nhân sự từ Excel hoặc CSV")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importStaffs(
            @RequestParam("file") MultipartFile file) {
        Map<String, Object> result = staffService.importStaffs(file);
        return ResponseEntity.ok(ApiResponse.success(result, (String) result.get("message")));
    }
}
