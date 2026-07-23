package com.hospital.scheduler.governance.repository;

import com.hospital.scheduler.governance.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for approval requests.
 */
@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Integer> {

    /**
     * Find pending approvals.
     */
    List<ApprovalRequest> findByStatusOrderByPriorityAscCreatedAtAsc(
            ApprovalRequest.ApprovalStatus status);

    /**
     * Find by submitter.
     */
    List<ApprovalRequest> findBySubmittedByOrderByCreatedAtDesc(Integer submittedBy);

    /**
     * Find by entity.
     */
    List<ApprovalRequest> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId);

    /**
     * Count pending.
     */
    long countByStatus(ApprovalRequest.ApprovalStatus status);

    /**
     * Find overdue.
     */
    List<ApprovalRequest> findByStatusAndDueDateBefore(
            ApprovalRequest.ApprovalStatus status, java.time.LocalDateTime dueDate);
}
