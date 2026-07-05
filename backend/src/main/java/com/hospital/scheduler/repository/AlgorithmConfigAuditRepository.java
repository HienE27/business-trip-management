package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.AlgorithmConfigAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlgorithmConfigAuditRepository extends JpaRepository<AlgorithmConfigAudit, Long> {

    Page<AlgorithmConfigAudit> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AlgorithmConfigAudit> findByParamKeyOrderByCreatedAtDesc(String paramKey);

    @Query("SELECT a FROM AlgorithmConfigAudit a WHERE " +
            "(:paramKey IS NULL OR a.paramKey = :paramKey) " +
            "ORDER BY a.createdAt DESC")
    Page<AlgorithmConfigAudit> search(@Param("paramKey") String paramKey, Pageable pageable);
}