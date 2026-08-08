package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.RolePermission;
import com.hospital.scheduler.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RolePermission rp WHERE rp.roleId = :roleId")
    int deleteByRoleId(@Param("roleId") Integer roleId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RolePermission rp WHERE rp.permissionId = :permissionId")
    int deleteByPermissionId(@Param("permissionId") Integer permissionId);

    // BUGFIX (was RBAC-N+1): single-role fetch — replaces the
    // findAll().stream().filter(...) pattern that triggered a full table scan
    // per role on every login. Used by PermissionService.permissionsOf().
    List<RolePermission> findAllByRoleId(Integer roleId);
}
