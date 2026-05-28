package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.AlgorithmConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AlgorithmConfigRepository extends JpaRepository<AlgorithmConfig, String> {
    Optional<AlgorithmConfig> findByParamKey(String paramKey);
}
