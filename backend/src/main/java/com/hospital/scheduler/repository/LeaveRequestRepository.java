package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Integer> {
    @Query("SELECT lr FROM LeaveRequest lr LEFT JOIN FETCH lr.staff WHERE lr.staff.id = :staffId")
    List<LeaveRequest> findByStaffId(@Param("staffId") Integer staffId);

    @Query("SELECT lr FROM LeaveRequest lr LEFT JOIN FETCH lr.staff WHERE lr.status = :status")
    List<LeaveRequest> findByStatus(@Param("status") LeaveRequest.LeaveStatus status);

    @Query("SELECT lr FROM LeaveRequest lr LEFT JOIN FETCH lr.reviewedBy WHERE lr.reviewedBy.id = :reviewedById")
    List<LeaveRequest> findByReviewedBy(@Param("reviewedById") Integer reviewedById);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.staff.id = :staffId AND " +
           "(lr.startDate <= :endDate AND lr.endDate >= :startDate)")
    List<LeaveRequest> findByStaffIdAndDateRange(
            @Param("staffId") Integer staffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'PENDING' ORDER BY lr.createdAt")
    List<LeaveRequest> findPendingRequests();

    /** Batch query: all approved leave requests overlapping a date (no staff filter). */
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'APPROVED' AND lr.startDate <= :date AND lr.endDate >= :date")
    List<LeaveRequest> findApprovedByDate(@Param("date") LocalDate date);

    /** Batch query: all approved leaves within a date range (no staff filter, for period-level batch checks). */
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'APPROVED' AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
    List<LeaveRequest> findApprovedInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    long countByStatus(LeaveRequest.LeaveStatus status);

    /** Batch query: approved leaves in a period date range (for scoring / conflict scan). */
    @Query("SELECT lr FROM LeaveRequest lr LEFT JOIN FETCH lr.staff " +
           "WHERE lr.status = :status " +
           "AND lr.period.id = :periodId")
    List<LeaveRequest> findByPeriodIdAndStatus(
            @Param("periodId") Integer periodId,
            @Param("status") LeaveRequest.LeaveStatus status);
}
