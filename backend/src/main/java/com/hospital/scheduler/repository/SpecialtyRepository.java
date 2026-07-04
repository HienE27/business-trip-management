package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Integer>, JpaSpecificationExecutor<Specialty> {
    Optional<Specialty> findByName(String name);
    List<Specialty> findByIsActiveTrue();
}
