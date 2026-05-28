package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.StaffRole;
import com.hospital.scheduler.entity.StaffRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffRoleRepository extends JpaRepository<StaffRole, StaffRoleId> {
    void deleteByStaffId(Integer staffId);
}
