package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    @Query("SELECT n FROM Notification n LEFT JOIN FETCH n.staff WHERE n.staff.id = :staffId ORDER BY n.createdAt DESC")
    List<Notification> findByStaffIdOrderByCreatedAtDesc(@Param("staffId") Integer staffId);

    Page<Notification> findByStaffId(Integer staffId, Pageable pageable);

    @Query("SELECT n FROM Notification n LEFT JOIN FETCH n.staff WHERE n.staff.id = :staffId AND n.isRead = false ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByStaffId(@Param("staffId") Integer staffId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.staff.id = :staffId AND n.isRead = false")
    int markAllAsReadBulk(@Param("staffId") Integer staffId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.staff.id = :staffId AND n.isRead = false")
    long countUnreadByStaffId(@Param("staffId") Integer staffId);

    long countByStaffId(Integer staffId);

    /** Paginated query with optional filters. tab values: all|unread|conflict|exchange|published|system */
    @Query("SELECT n FROM Notification n LEFT JOIN FETCH n.staff WHERE n.staff.id = :staffId " +
           "AND (:tab = 'all' OR (:tab = 'unread' AND n.isRead = false) " +
           "OR (:tab = 'conflict' AND (LOWER(n.title) LIKE '%xung%' OR LOWER(n.title) LIKE '%conflict%' " +
           "    OR LOWER(n.title) LIKE '%cảnh báo%' OR LOWER(n.title) LIKE '%warning%' " +
           "    OR LOWER(n.message) LIKE '%xung%' OR LOWER(n.message) LIKE '%conflict%' " +
           "    OR LOWER(n.message) LIKE '%trùng%' OR LOWER(n.message) LIKE '%overlap%' " +
           "    OR LOWER(n.message) LIKE '%nghỉ phép%' OR LOWER(n.message) LIKE '%leave%')) " +
           "OR (:tab = 'exchange' AND (LOWER(n.title) LIKE '%đổi%' OR LOWER(n.title) LIKE '%swap%' " +
           "    OR LOWER(n.title) LIKE '%thay ca%' OR LOWER(n.title) LIKE '%thay thế%' " +
           "    OR LOWER(n.message) LIKE '%đổi%' OR LOWER(n.message) LIKE '%swap%' " +
           "    OR LOWER(n.message) LIKE '%thay ca%' OR LOWER(n.message) LIKE '%thay thế%')) " +
           "OR (:tab = 'published' AND (LOWER(n.title) LIKE '%công bố%' OR LOWER(n.title) LIKE '%published%' " +
           "    OR LOWER(n.title) LIKE '%ban hành%' OR LOWER(n.title) LIKE '%phát hành%' " +
           "    OR LOWER(n.message) LIKE '%công bố%' OR LOWER(n.message) LIKE '%published%' " +
           "    OR LOWER(n.message) LIKE '%ban hành%' OR LOWER(n.message) LIKE '%phát hành%')) " +
           "OR (:tab = 'system' AND (LOWER(n.title) LIKE '%tự động%' OR LOWER(n.title) LIKE '%auto%' " +
           "    OR LOWER(n.title) LIKE '%hệ thống%' OR LOWER(n.title) LIKE '%system%' " +
           "    OR LOWER(n.message) LIKE '%tự động%' OR LOWER(n.message) LIKE '%auto%' " +
           "    OR LOWER(n.message) LIKE '%hệ thống%' OR LOWER(n.message) LIKE '%system%'))) " +
           "ORDER BY n.createdAt DESC")
    Page<Notification> findPageWithFilters(
            @Param("staffId") Integer staffId,
            @Param("tab") String tab,
            Pageable pageable);
}
