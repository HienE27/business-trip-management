package com.hospital.scheduler.dto.response;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionMatrixResponse {

    private List<RoleDto> roles;
    private List<PermissionDto> permissions;
    private List<RolePermissionEntry> matrix;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleDto {
        private Integer id;
        private String name;
        private String description;
        private Boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionDto {
        private Integer id;
        private String name;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RolePermissionEntry {
        private Integer roleId;
        private String roleName;
        private Integer permissionId;
        private String permissionName;
        private Boolean granted;
    }
}
