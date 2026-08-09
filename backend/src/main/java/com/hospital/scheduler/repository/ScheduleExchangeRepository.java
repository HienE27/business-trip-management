package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.ScheduleExchange;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleExchangeRepository extends JpaRepository<ScheduleExchange, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ScheduleExchange e WHERE e.id = :id")
    Optional<ScheduleExchange> findByIdWithLock(@Param("id") Integer id);
    List<ScheduleExchange> findByRequesterId(Integer requesterId);
    List<ScheduleExchange> findByTargetId(Integer targetId);
    List<ScheduleExchange> findByStatus(ScheduleExchange.ExchangeStatus status);

    long countByStatus(ScheduleExchange.ExchangeStatus status);

    /** Paginated query with optional status and keyword filters. */
    @org.springframework.data.jpa.repository.Query("SELECT e FROM ScheduleExchange e " +
           "JOIN FETCH e.requester r JOIN FETCH e.target t " +
           "WHERE (:status IS NULL OR e.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(r.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(t.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(e.reason) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    org.springframework.data.domain.Page<ScheduleExchange> findPageWithFilters(
            @org.springframework.data.repository.query.Param("status") ScheduleExchange.ExchangeStatus status,
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            org.springframework.data.domain.Pageable pageable);
    List<ScheduleExchange> findByPeriodIdAndStatus(Integer periodId, ScheduleExchange.ExchangeStatus status);
    List<ScheduleExchange> findPendingByRequesterIdOrTargetId(Integer requesterId, Integer targetId);

    @Query("SELECT e FROM ScheduleExchange e WHERE e.requester.id = :userId OR e.target.id = :userId ORDER BY e.createdAt DESC")
    List<ScheduleExchange> findAllByUserId(@Param("userId") Integer userId);

    /**
     * Paginated query for exchanges involving a specific user (as requester or
     * target) with optional status and keyword filters. Used by STAFF users.
     */
    @Query("SELECT e FROM ScheduleExchange e " +
           "JOIN FETCH e.requester r JOIN FETCH e.target t " +
           "WHERE (e.requester.id = :userId OR e.target.id = :userId) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(r.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(t.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(e.reason) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    org.springframework.data.domain.Page<ScheduleExchange> findByUserIdWithFilters(
            @Param("userId") Integer userId,
            @Param("status") ScheduleExchange.ExchangeStatus status,
            @Param("keyword") String keyword,
            org.springframework.data.domain.Pageable pageable);

    /** Count of exchanges involving a user by status (staff-scoped /status-counts). */
    @Query("SELECT count(e) FROM ScheduleExchange e " +
           "WHERE (e.requester.id = :userId OR e.target.id = :userId) " +
           "AND (:status IS NULL OR e.status = :status)")
    long countByUserIdAndStatus(@Param("userId") Integer userId, @Param("status") ScheduleExchange.ExchangeStatus status);

    List<ScheduleExchange> findByRequesterScheduleId(Integer scheduleId);
    List<ScheduleExchange> findByTargetScheduleId(Integer scheduleId);
    List<ScheduleExchange> findByRequesterScheduleIdOrTargetScheduleId(Integer requesterScheduleId, Integer targetScheduleId);

    /**
     * Tìm exchange có requester_schedule_id không còn tồn tại trong schedule.
     */
    @Query("SELECT e FROM ScheduleExchange e WHERE e.requesterSchedule IS NULL")
    List<ScheduleExchange> findOrphanByMissingRequesterSchedule();

    /**
     * Tìm exchange có target_schedule_id không còn tồn tại trong schedule.
     */
    @Query("SELECT e FROM ScheduleExchange e WHERE e.targetSchedule IS NULL")
    List<ScheduleExchange> findOrphanByMissingTargetSchedule();

    /**
     * Đếm số exchange có reference tới schedule đã xóa (dùng để báo cáo).
     */
    @Query("SELECT COUNT(e) FROM ScheduleExchange e WHERE e.requesterSchedule IS NULL OR e.targetSchedule IS NULL")
    long countOrphanScheduleReferences();

    /**
     * Lấy IDs các exchange có requester_schedule_id không tồn tại (native query, tránh load entity).
     */
    @Query(value = "SELECT se.id FROM schedule_exchange se " +
            "WHERE se.requester_schedule_id NOT IN (SELECT id FROM schedule) " +
            "AND se.status = 'PENDING'", nativeQuery = true)
    List<Integer> findOrphanIdsByRequesterScheduleNative();

    /**
     * Lấy IDs các exchange có target_schedule_id không tồn tại (native query).
     */
    @Query(value = "SELECT se.id FROM schedule_exchange se " +
            "WHERE se.target_schedule_id NOT IN (SELECT id FROM schedule) " +
            "AND se.status = 'PENDING'", nativeQuery = true)
    List<Integer> findOrphanIdsByTargetScheduleNative();

    /**
     * Cancel exchange theo ID dùng native update (không load entity, tránh issue với lazy + NotFound IGNORE).
     * Set status = CANCELLED, review_note = lý do, updated_at = NOW().
     */
    @Modifying
    @Query(value = "UPDATE schedule_exchange SET status = 'CANCELLED', " +
            "review_note = :reviewNote, updated_at = NOW() " +
            "WHERE id = :id AND status = 'PENDING'", nativeQuery = true)
    int cancelByIdNative(@Param("id") Integer id, @Param("reviewNote") String reviewNote);

    /**
     * Đếm số exchange đã bị cancel qua cleanup (dùng cho báo cáo sau).
     */
    @Query(value = "SELECT COUNT(*) FROM schedule_exchange WHERE status = 'CANCELLED' " +
            "AND review_note = :reviewNote", nativeQuery = true)
    long countCancelledWithNote(@Param("reviewNote") String reviewNote);
}
