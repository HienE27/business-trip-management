package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.AlgorithmMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlgorithmMetricsRepository extends JpaRepository<AlgorithmMetrics, Integer> {
    List<AlgorithmMetrics> findByPeriodId(Integer periodId);
    List<AlgorithmMetrics> findByAlgorithmType(String algorithmType);
}
