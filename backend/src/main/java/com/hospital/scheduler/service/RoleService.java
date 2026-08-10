package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.RolePermissionMatrixResponse;
import com.hospital.scheduler.dto.response.RolePermissionMatrixResponse.*;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.security.PermissionVersionService;
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
    // BUGFIX (was BE#12): togglePermission was a security-critical mutation
    // without any audit trail. Inject AuditHistoryService + AuthContextService
    // so every grant/revoke gets logged with the actor that performed the change.
    private final AuditHistoryService auditHistoryService;
    private final AuthContextService authContextService;
    // Bumps the JWT permission-version stamp so every outstanding access token
    // (which carries the old permVer claim) becomes invalid for protected routes.
    // The next login or refresh re-issues a JWT stamped with the new version.
    private final PermissionVersionService permissionVersionService;

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

        // Build granted set with roleId|permissionName keys
        Set<String> grantedKeys = new HashSet<>();
        for (RolePermission rp : rolePermissionRepository.findAll()) {
            AppPermission perm = permissionRepository.findById(rp.getPermissionId()).orElse(null);
            if (perm != null) {
                grantedKeys.add(rp.getRoleId() + "|" + perm.getName());
            }
        }

        List<RolePermissionEntry> matrix = new ArrayList<>();
        for (AppRole role : roles) {
            for (AppPermission permission : permissions) {
                String key = role.getId() + "|" + permission.getName();
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
        // BUGFIX (was BE#12): the previous version silently granted/revoked
        // permissions with zero audit trail. Any post-incident review of "who
        // changed which permission" was impossible. Now we log every grant/revoke
        // with the actor and the exact (roleId, permissionId, granted) tuple so
        // auditors can see who flipped which cell.
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

        Integer actorId = resolveActorSafely();
        java.util.Map<String, Object> oldValue = java.util.Map.of(
                "roleId", roleId,
                "permissionId", permissionId,
                "granted", !Boolean.TRUE.equals(granted));
        java.util.Map<String, Object> newValue = java.util.Map.of(
                "roleId", roleId,
                "permissionId", permissionId,
                "granted", Boolean.TRUE.equals(granted));
        AuditHistory.ActionType action = Boolean.TRUE.equals(granted)
                ? AuditHistory.ActionType.INSERT : AuditHistory.ActionType.DELETE;

        auditHistoryService.logAction("role_permission",
                roleId, action, oldValue, newValue, actorId);

        // Invalidate every outstanding JWT — the granted/revoked cell is now
        // materialised in the permission matrix but the token still carries the
        // old permVer stamp. Bumping forces a re-auth on the next request so the
        // security context catches up immediately (instead of waiting up to 30
        // minutes for the access-token expiry).
        permissionVersionService.bump();
    }

    /**
     * Bulk grant / revoke a set of permissions for a single role in one
     * transaction. Bumps the permission-version stamp exactly once at the
     * end, instead of once per cell — saves N round-trips on the wire
     * and produces a single coherent audit-log entry per bulk action
     * (plus per-cell entries for forensics).
     *
     * <p>Used by the permission-matrix UI's "Cấp tất cả" / "Thu hồi tất cả"
     * actions.
     *
     * @param roleId       the role to mutate
     * @param permissionIds permissions to grant/revoke for that role
     * @param granted      true to insert all, false to delete all
     */
    @Transactional
    public void bulkTogglePermission(Integer roleId, java.util.List<Integer> permissionIds, Boolean granted) {
        if (roleId == null || permissionIds == null || permissionIds.isEmpty()) {
            return;
        }
        boolean grant = Boolean.TRUE.equals(granted);
        Integer actorId = resolveActorSafely();

        for (Integer permissionId : permissionIds) {
            if (permissionId == null) continue;

            if (grant) {
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

            // Per-cell audit entry — full forensic trail for "who changed
            // what".
            java.util.Map<String, Object> oldValue = java.util.Map.of(
                    "roleId", roleId,
                    "permissionId", permissionId,
                    "granted", !grant,
                    "bulk", true);
            java.util.Map<String, Object> newValue = java.util.Map.of(
                    "roleId", roleId,
                    "permissionId", permissionId,
                    "granted", grant,
                    "bulk", true);
            AuditHistory.ActionType action = grant
                    ? AuditHistory.ActionType.INSERT : AuditHistory.ActionType.DELETE;
            auditHistoryService.logAction("role_permission",
                    roleId, action, oldValue, newValue, actorId);
        }

        // Bump exactly once at the end. Calling bump() N times in the loop
        // would still produce the same final version (Math.max guard) but
        // spams the audit log and the algorithm_config table with N writes.
        permissionVersionService.bump();
    }

    private Integer resolveActorSafely() {
        try {
            return authContextService.getCurrentStaff().getId();
        } catch (Exception ex) {
            return null; // background job / no auth context — log without actor
        }
    }

}
