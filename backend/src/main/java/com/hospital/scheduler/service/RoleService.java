package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.RolePermissionMatrixResponse;
import com.hospital.scheduler.dto.response.RolePermissionMatrixResponse.*;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final AppRoleRepository roleRepository;
    private final AppPermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * Returns the full role-permission matrix for the admin UI.
     *
     * <p>The matrix entries only contain the GRANTED cells (role-permission pairs that
     * exist in the join table). The frontend rebuilds the full N×M grid and marks
     * cells as granted=true/false based on whether the pair appears here.
     *
     * @see RolePermissionMatrixResponse
     */
    public RolePermissionMatrixResponse getPermissionMatrix() {
        List<AppRole> roles = roleRepository.findAll();
        List<AppPermission> permissions = permissionRepository.findAll();

        List<RolePermission> granted = rolePermissionRepository.findAll();

        Set<String> grantedKeys = granted.stream()
                .map(rp -> rp.getRoleId() + "|" + rp.getPermissionId())
                .collect(Collectors.toSet());

        List<RolePermissionEntry> matrix = new ArrayList<>();
        for (AppRole role : roles) {
            for (AppPermission permission : permissions) {
                String key = role.getId() + "|" + permission.getId();
                matrix.add(RolePermissionEntry.builder()
                        .roleId(role.getId())
                        .roleName(role.getName().name())
                        .permissionId(permission.getId())
                        .permissionName(permission.getName())
                        .granted(grantedKeys.contains(key))
                        .build());
            }
        }

        return RolePermissionMatrixResponse.builder()
                .roles(roles.stream()
                        .map(r -> RoleDto.builder()
                                .id(r.getId())
                                .name(r.getName().name())
                                .description(r.getDescription())
                                .isActive(r.getIsActive())
                                .build())
                        .collect(Collectors.toList()))
                .permissions(permissions.stream()
                        .map(p -> PermissionDto.builder()
                                .id(p.getId())
                                .name(p.getName())
                                .description(p.getDescription())
                                .build())
                        .collect(Collectors.toList()))
                .matrix(matrix)
                .build();
    }

    /**
     * Grants or revokes a permission for a role.
     */
    @Transactional
    public void togglePermission(Integer roleId, Integer permissionId, Boolean granted) {
        if (Boolean.TRUE.equals(granted)) {
            if (!rolePermissionRepository.existsById(
                    new RolePermissionId(roleId, permissionId))) {
                RolePermission rp = RolePermission.builder()
                        .roleId(roleId)
                        .permissionId(permissionId)
                        .build();
                rolePermissionRepository.save(rp);
            }
        } else {
            rolePermissionRepository.deleteById(
                    new RolePermissionId(roleId, permissionId));
        }
    }

}
