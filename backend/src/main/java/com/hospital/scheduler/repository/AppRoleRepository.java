package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.AppRole;
import com.hospital.scheduler.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppRoleRepository extends JpaRepository<AppRole, Integer> {
    Optional<AppRole> findByName(RoleName name);
    Optional<AppRole> findByName(String name);
    boolean existsByName(String name);
    boolean existsByName(RoleName name);
}
