package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.AlgorithmMetrics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlgorithmMetricsRepository extends JpaRepository<AlgorithmMetrics, Integer> {
    List<AlgorithmMetrics> findByPeriodId(Integer periodId);
    Page<AlgorithmMetrics> findByPeriodId(Integer periodId, Pageable pageable);
    List<AlgorithmMetrics> findByAlgorithmType(String algorithmType);

    /**
     * Bulk delete by id list. Returns the number of rows actually removed.
     * Used by the bulk-delete UI on /auto-scheduling/history.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM AlgorithmMetrics m WHERE m.id IN :ids")
    int deleteByIdInBatch(@Param("ids") java.util.List<Integer> ids);

    /** Delete every metric whose createdAt falls inside [start, end). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM AlgorithmMetrics m WHERE m.createdAt >= :start AND m.createdAt < :end")
    int deleteByCreatedAtRange(@Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    /** Paginated query with optional keyword + algoType + coverage filters. */
    @Query("SELECT m FROM AlgorithmMetrics m LEFT JOIN m.period p " +
           "WHERE (:periodId IS NULL OR m.period.id = :periodId) " +
           "AND (:algoType IS NULL OR m.algorithmType = :algoType) " +
           "AND (:keyword IS NULL OR LOWER(m.algorithmType) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(p.periodName) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:coverageMin IS NULL OR m.coverageRate >= :coverageMin) " +
           "AND (:coverageMax IS NULL OR m.coverageRate < :coverageMax)")
    Page<AlgorithmMetrics> findPageWithFilters(
            @Param("periodId") Integer periodId,
            @Param("algoType") String algoType,
            @Param("keyword") String keyword,
            @Param("coverageMin") java.math.BigDecimal coverageMin,
            @Param("coverageMax") java.math.BigDecimal coverageMax,
            Pageable pageable);
}
