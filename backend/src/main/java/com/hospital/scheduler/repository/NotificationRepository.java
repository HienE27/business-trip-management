package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    @Query("SELECT n FROM Notification n WHERE n.staff.id = :staffId ORDER BY n.createdAt DESC")
    List<Notification> findByStaffIdOrderByCreatedAtDesc(@Param("staffId") Integer staffId);

    @Query("SELECT n FROM Notification n WHERE n.staff.id = :staffId AND n.isRead = false ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByStaffId(@Param("staffId") Integer staffId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.staff.id = :staffId AND n.isRead = false")
    long countUnreadByStaffId(@Param("staffId") Integer staffId);
}
