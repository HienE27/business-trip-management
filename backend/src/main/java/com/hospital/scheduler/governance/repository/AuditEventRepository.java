package com.hospital.scheduler.governance.repository;

import com.hospital.scheduler.governance.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for audit events.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {

    Page<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId, Pageable pageable);

    Page<AuditEvent> findByStaffIdOrderByCreatedAtDesc(Integer staffId, Pageable pageable);

    Page<AuditEvent> findByActionOrderByCreatedAtDesc(
            AuditEvent.AuditAction action, Pageable pageable);

    Page<AuditEvent> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    long countByAction(AuditEvent.AuditAction action);

    long countByEntityType(String entityType);

    @Query("SELECT COUNT(e) FROM AuditEvent e WHERE e.createdAt >= :today")
    long countToday(LocalDateTime today);

    @Query("SELECT COUNT(e) FROM AuditEvent e WHERE e.createdAt >= :weekStart")
    long countThisWeek(LocalDateTime weekStart);

    @Query("SELECT COUNT(e) FROM AuditEvent e WHERE e.createdAt >= :monthStart")
    long countThisMonth(LocalDateTime monthStart);

    @Query("SELECT e.action, COUNT(e) FROM AuditEvent e GROUP BY e.action")
    List<Object[]> countByActionGroup();

    @Query("SELECT e.entityType, COUNT(e) FROM AuditEvent e GROUP BY e.entityType")
    List<Object[]> countByEntityTypeGroup();

    List<AuditEvent> findTop20ByOrderByCreatedAtDesc();
}
