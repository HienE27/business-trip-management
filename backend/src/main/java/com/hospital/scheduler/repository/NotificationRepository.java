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
    @Query("SELECT n FROM Notification n WHERE n.staff.id = :staffId ORDER BY n.createdAt DESC")
    List<Notification> findByStaffIdOrderByCreatedAtDesc(@Param("staffId") Integer staffId);

    Page<Notification> findByStaffId(Integer staffId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.staff.id = :staffId AND n.isRead = false ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByStaffId(@Param("staffId") Integer staffId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.staff.id = :staffId AND n.isRead = false")
    int markAllAsReadBulk(@Param("staffId") Integer staffId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.staff.id = :staffId AND n.isRead = false")
    long countUnreadByStaffId(@Param("staffId") Integer staffId);

    long countByStaffId(Integer staffId);
}
