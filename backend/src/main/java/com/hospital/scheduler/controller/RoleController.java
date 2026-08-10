package com.hospital.scheduler.controller;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.response.RolePermissionMatrixResponse;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/permissions/matrix")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_VIEW + "')")
    public ResponseEntity<ApiResponse<RolePermissionMatrixResponse>> getPermissionMatrix() {
        RolePermissionMatrixResponse matrix = roleService.getPermissionMatrix();
        return ResponseEntity.ok(ApiResponse.success(matrix));
    }

    @PostMapping("/permissions/toggle")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_EDIT + "')")
    public ResponseEntity<ApiResponse<Void>> togglePermission(
            @Valid @RequestBody TogglePermissionRequest request) {
        roleService.togglePermission(request.getRoleId(), request.getPermissionId(), request.getGranted());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/permissions/bulk-toggle")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_EDIT + "')")
    public ResponseEntity<ApiResponse<Void>> bulkTogglePermission(
            @Valid @RequestBody BulkTogglePermissionRequest request) {
        roleService.bulkTogglePermission(
                request.getRoleId(),
                request.getPermissionIds(),
                request.getGranted());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    public static class TogglePermissionRequest {
        @jakarta.validation.constraints.NotNull(message = "roleId không được để trống")
        private Integer roleId;
        @jakarta.validation.constraints.NotNull(message = "permissionId không được để trống")
        private Integer permissionId;
        @jakarta.validation.constraints.NotNull(message = "granted không được để trống")
        private Boolean granted;

        public Integer getRoleId() { return roleId; }
        public void setRoleId(Integer roleId) { this.roleId = roleId; }
        public Integer getPermissionId() { return permissionId; }
        public void setPermissionId(Integer permissionId) { this.permissionId = permissionId; }
        public Boolean getGranted() { return granted; }
        public void setGranted(Boolean granted) { this.granted = granted; }
    }

    public static class BulkTogglePermissionRequest {
        @jakarta.validation.constraints.NotNull(message = "roleId không được để trống")
        private Integer roleId;
        @jakarta.validation.constraints.NotEmpty(message = "permissionIds không được rỗng")
        private java.util.List<Integer> permissionIds;
        @jakarta.validation.constraints.NotNull(message = "granted không được để trống")
        private Boolean granted;

        public Integer getRoleId() { return roleId; }
        public void setRoleId(Integer roleId) { this.roleId = roleId; }
        public java.util.List<Integer> getPermissionIds() { return permissionIds; }
        public void setPermissionIds(java.util.List<Integer> permissionIds) { this.permissionIds = permissionIds; }
        public Boolean getGranted() { return granted; }
        public void setGranted(Boolean granted) { this.granted = granted; }
    }
}
