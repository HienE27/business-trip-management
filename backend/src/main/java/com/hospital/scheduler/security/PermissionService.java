package com.hospital.scheduler.security;

import com.hospital.scheduler.entity.AppPermission;
import com.hospital.scheduler.entity.AppRole;
import com.hospital.scheduler.entity.RolePermission;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.AppPermissionRepository;
import com.hospital.scheduler.repository.AppRoleRepository;
import com.hospital.scheduler.repository.RolePermissionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the current user's permission set from the database (or from the
 * JWT principal when {@link #getPermissionsOfCurrentUser()} is called from
 * {@link com.hospital.scheduler.security.JwtService}).
 *
 * <p>Two read paths are supported on purpose:
 * <ul>
 *   <li>{@link #permissionsOf(Staff)} — full DB read; used when issuing tokens
 *       so the claim reflects the live matrix even right after an ADMIN
 *       tweaks the role-permission table.</li>
 *   <li>{@link #permissionsOfCurrentPrincipal()} — JWT-backed read; fast path
 *       used by controllers that only need a coarse check (we still rely on
 *       {@code @PreAuthorize} for security).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final AppRoleRepository appRoleRepository;
    private final AppPermissionRepository appPermissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Read flattened permission names from the database for the given staff,
     * walking the {@code staff_role} -> {@code role_permission} ->
     * {@code app_permission} chain. Inactive permissions and inactive roles
     * are filtered out.
     */
    @Transactional(readOnly = true)
    public List<String> permissionsOf(Staff staff) {
        if (staff == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (var sr : staff.getStaffRoles()) {
            AppRole role = sr.getRole();
            if (role == null || role.getId() == null) continue;
            // BUGFIX: skip roles that have been deactivated in the matrix — the
            // staff row still has the staff_role link but the role is inert.
            if (Boolean.FALSE.equals(role.getIsActive())) continue;
            // BUGFIX (was RBAC-N+1): fetch one role's permissions in a single
            // indexed query instead of findAll().filter() which scanned the
            // entire join table for every role on every login.
            List<Integer> permIds = rolePermissionRepository.findAllByRoleId(role.getId()).stream()
                    .map(RolePermission::getPermissionId)
                    .toList();
            if (permIds.isEmpty()) continue;
            List<AppPermission> perms = appPermissionRepository.findAllById(permIds);
            for (AppPermission p : perms) {
                if (Boolean.TRUE.equals(p.getIsActive())) {
                    result.add(p.getName());
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * Convenience overload — looks up the staff by username.
     */
    @Transactional(readOnly = true)
    public List<String> permissionsOf(String username) {
        if (username == null) return List.of();
        var staff = entityManager.createQuery(
                        "SELECT s FROM Staff s LEFT JOIN FETCH s.staffRoles sr LEFT JOIN FETCH sr.role WHERE s.username = :u",
                        Staff.class)
                .setParameter("u", username)
                .getResultStream()
                .findFirst()
                .orElse(null);
        return permissionsOf(staff);
    }

    /**
     * Read permissions from the JWT principal in the current security context.
     * Cheap — no DB round-trip. Returns an empty list when there is no
     * authenticated principal (e.g. anonymous filter chain).
     */
    public List<String> permissionsOfCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (var authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            if (name != null && !name.startsWith("ROLE_")) {
                result.add(name);
            }
        }
        return List.copyOf(result);
    }
}