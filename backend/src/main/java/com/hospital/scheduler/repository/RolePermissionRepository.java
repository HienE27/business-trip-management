package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.RolePermission;
import com.hospital.scheduler.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
    void deleteByRoleId(Integer roleId);
}
