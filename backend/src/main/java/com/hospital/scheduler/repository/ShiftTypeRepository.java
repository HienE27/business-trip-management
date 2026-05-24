package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.ShiftType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShiftTypeRepository extends JpaRepository<ShiftType, String> {
    List<ShiftType> findByIsActiveTrue();
}
