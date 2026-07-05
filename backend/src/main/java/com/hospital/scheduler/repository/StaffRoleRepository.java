package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.StaffRole;
import com.hospital.scheduler.entity.StaffRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StaffRoleRepository extends JpaRepository<StaffRole, StaffRoleId> {
    void deleteByStaffId(Integer staffId);

    /**
     * Tìm StaffRole rows mà role_id không tồn tại trong app_role.
     * Dùng để cleanup orphan rows gây lỗi "No row with the given identifier exists".
     */
    @Query("SELECT sr FROM StaffRole sr WHERE sr.role IS NULL")
    List<StaffRole> findOrphanedByMissingRole();

    /**
     * Xóa StaffRole rows mà role_id không tồn tại trong app_role.
     * Trả về số row đã xóa.
     */
    @Modifying
    @Query("DELETE FROM StaffRole sr WHERE sr.roleId NOT IN " +
            "(SELECT ar.id FROM AppRole ar)")
    int deleteOrphanedByMissingRole();

    /**
     * Xóa StaffRole rows mà staff_id không tồn tại trong staff.
     */
    @Modifying
    @Query("DELETE FROM StaffRole sr WHERE sr.staffId NOT IN " +
            "(SELECT s.id FROM Staff s)")
    int deleteOrphanedByMissingStaff();
}

