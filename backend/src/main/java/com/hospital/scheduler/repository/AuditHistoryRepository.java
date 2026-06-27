package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.AuditHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditHistoryRepository extends JpaRepository<AuditHistory, Integer> {
    List<AuditHistory> findByTableNameAndRecordId(String tableName, Integer recordId);
    List<AuditHistory> findByChangedBy(Integer changedById);

    Page<AuditHistory> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    @Query("SELECT ah FROM AuditHistory ah LEFT JOIN FETCH ah.changedBy WHERE ah.tableName = :tableName AND ah.recordId = :recordId")
    List<AuditHistory> findByTableNameAndRecordIdWithChangedBy(
            @Param("tableName") String tableName,
            @Param("recordId") Integer recordId);

    @Query("SELECT ah FROM AuditHistory ah LEFT JOIN FETCH ah.changedBy WHERE ah.createdAt BETWEEN :startDate AND :endDate ORDER BY ah.createdAt DESC")
    List<AuditHistory> findByDateRangeWithChangedBy(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT ah FROM AuditHistory ah LEFT JOIN FETCH ah.changedBy ORDER BY ah.createdAt DESC",
           countQuery = "SELECT COUNT(ah) FROM AuditHistory ah")
    Page<AuditHistory> findAllWithChangedBy(Pageable pageable);

    @Query("SELECT ah FROM AuditHistory ah WHERE ah.tableName = :tableName AND ah.recordId = :recordId ORDER BY ah.createdAt DESC")
    Page<AuditHistory> findByTableNameAndRecordId(
            @Param("tableName") String tableName,
            @Param("recordId") Integer recordId,
            Pageable pageable);

    Page<AuditHistory> findByChangedBy(Integer changedById, Pageable pageable);
}
