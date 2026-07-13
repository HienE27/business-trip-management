package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.SystemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SystemLogRepository extends JpaRepository<SystemLog, Integer> {
    List<SystemLog> findByStaffId(Integer staffId);
    List<SystemLog> findByActionType(String actionType);

    /**
     * BUGFIX (was BE#18) Pageable overloads. The previous read endpoints
     * returned unbounded lists which would OOM the JSON serializer on a
     * large system_log table. Callers must now supply a {@link Pageable}.
     */
    Page<SystemLog> findByStaffId(Integer staffId, Pageable pageable);
    Page<SystemLog> findByActionType(String actionType, Pageable pageable);

    @Query("SELECT sl FROM SystemLog sl WHERE sl.createdAt BETWEEN :startDate AND :endDate ORDER BY sl.createdAt DESC")
    List<SystemLog> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT sl FROM SystemLog sl WHERE sl.createdAt BETWEEN :startDate AND :endDate")
    Page<SystemLog> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
}
