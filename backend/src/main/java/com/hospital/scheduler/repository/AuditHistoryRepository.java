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

    @Query("SELECT ah FROM AuditHistory ah WHERE ah.changedBy.id = :changedById")
    List<AuditHistory> findByChangedBy(@Param("changedById") Integer changedById);

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

    @Query("SELECT ah FROM AuditHistory ah WHERE ah.changedBy.id = :changedById")
    Page<AuditHistory> findByChangedBy(@Param("changedById") Integer changedById, Pageable pageable);

    List<AuditHistory> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Count audit history records grouped by action type.
     * Returns rows like [CREATE, 123], [UPDATE, 456], [DELETE, 789].
     */
    @Query("SELECT ah.actionType, COUNT(ah) FROM AuditHistory ah GROUP BY ah.actionType")
    List<Object[]> countAllGroupedByAction();

    /**
     * Count audit history records grouped by action type, filtered by date range (inclusive).
     * Used for KPI summary so the numbers stay in sync with the user-selected period.
     */
    @Query("SELECT ah.actionType, COUNT(ah) FROM AuditHistory ah " +
           "WHERE ah.createdAt >= :start AND ah.createdAt < :end " +
           "GROUP BY ah.actionType")
    List<Object[]> countGroupedByActionBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Count audit history records grouped by action type with all client-side filters
     * (date range, table, action, free-text search across user name, user id, table,
     *  action and record id).
     *
     * All filter parameters except date range are optional — pass null to skip a filter.
     * The date range is inclusive start, exclusive end.
     */
    @Query("SELECT ah.actionType, COUNT(ah) FROM AuditHistory ah " +
           "LEFT JOIN ah.changedBy cb " +
           "WHERE (:start IS NULL OR ah.createdAt >= :start) " +
           "AND (:end IS NULL OR ah.createdAt < :end) " +
           "AND (:tableName IS NULL OR ah.tableName = :tableName) " +
           "AND (:action IS NULL OR ah.actionType = :action) " +
           "AND (" +
           "  :search IS NULL OR :search = '' OR " +
           "  LOWER(COALESCE(cb.fullName, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  CAST(cb.id AS string) LIKE CONCAT('%', :search, '%') OR " +
           "  LOWER(ah.tableName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(CAST(ah.actionType AS string)) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  CAST(ah.recordId AS string) LIKE CONCAT('%', :search, '%')" +
           ") " +
           "GROUP BY ah.actionType")
    List<Object[]> countGroupedByActionFiltered(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("tableName") String tableName,
            @Param("action") com.hospital.scheduler.entity.AuditHistory.ActionType action,
            @Param("search") String search);
}
