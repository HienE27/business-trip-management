package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.LeaveRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Integer> {

    /** Paginated query with optional status and keyword filters. */
    @Query("SELECT lr FROM LeaveRequest lr LEFT JOIN FETCH lr.staff s " +
           "WHERE (:status IS NULL OR lr.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(lr.reason) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<LeaveRequest> findPageWithFilters(
            @Param("status") LeaveRequest.LeaveStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** Count with optional status filter (for stats refresh). */
    @Query("SELECT count(lr) FROM LeaveRequest lr " +
           "WHERE (:status IS NULL OR lr.status = :status) AND " +
           "(:keyword IS NULL OR LOWER(lr.staff.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(lr.reason) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    long countWithFilters(
            @Param("status") LeaveRequest.LeaveStatus status,
            @Param("keyword") String keyword);
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

    /**
     * Overlap query that excludes a specific leave request id. Used when
     * updating an existing leave so the row being edited is not counted as
     * a collision with itself.
     *
     * BUGFIX (was BE#3): previously the validator only ran the inclusive
     * variant, so updating a leave (e.g. extending the date range) would
     * collide with its own current row and the update was rejected with a
     * "Nhân sự đã có yêu cầu nghỉ phép trùng" error. Pass {@code excludeId}
     * = the request being updated to avoid this false positive.
     */
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.staff.id = :staffId AND " +
           "(lr.startDate <= :endDate AND lr.endDate >= :startDate) AND " +
           "lr.id <> :excludeId")
    List<LeaveRequest> findByStaffIdAndDateRangeExcluding(
            @Param("staffId") Integer staffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Integer excludeId);

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
