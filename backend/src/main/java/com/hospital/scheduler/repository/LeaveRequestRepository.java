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
    List<LeaveRequest> findByStaffId(Integer staffId);
    List<LeaveRequest> findByStatus(LeaveRequest.LeaveStatus status);
    List<LeaveRequest> findByReviewedBy(Integer reviewedById);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.staff.id = :staffId AND " +
           "(lr.startDate <= :endDate AND lr.endDate >= :startDate)")
    List<LeaveRequest> findByStaffIdAndDateRange(
            @Param("staffId") Integer staffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.status = 'PENDING' ORDER BY lr.createdAt")
    List<LeaveRequest> findPendingRequests();
}
