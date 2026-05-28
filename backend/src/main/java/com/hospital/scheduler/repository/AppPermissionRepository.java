package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.AppPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppPermissionRepository extends JpaRepository<AppPermission, Integer> {
    Optional<AppPermission> findByName(String name);
    boolean existsByName(String name);
}
