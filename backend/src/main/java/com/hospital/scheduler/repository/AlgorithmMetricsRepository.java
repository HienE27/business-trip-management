package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.AlgorithmMetrics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlgorithmMetricsRepository extends JpaRepository<AlgorithmMetrics, Integer> {
    List<AlgorithmMetrics> findByPeriodId(Integer periodId);
    Page<AlgorithmMetrics> findByPeriodId(Integer periodId, Pageable pageable);
    List<AlgorithmMetrics> findByAlgorithmType(String algorithmType);
}
