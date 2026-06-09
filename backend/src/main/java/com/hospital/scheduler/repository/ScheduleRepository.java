package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Integer> {

    @Query("""
            SELECT s
            FROM Schedule s
            JOIN FETCH s.staff st
            LEFT JOIN FETCH st.specialty
            JOIN FETCH s.shiftType
            JOIN FETCH s.period
            WHERE s.period.id = :periodId
            ORDER BY s.workDate
            """)
    List<Schedule> findByPeriodId(@Param("periodId") Integer periodId);

    @Query("SELECT s FROM Schedule s WHERE s.period.id = :periodId AND s.workDate = :workDate")
    List<Schedule> findByPeriodIdAndWorkDate(@Param("periodId") Integer periodId, @Param("workDate") LocalDate workDate);

    @Query("SELECT s FROM Schedule s WHERE s.staff.id = :staffId ORDER BY s.workDate")
    List<Schedule> findByStaffId(@Param("staffId") Integer staffId);

    @Query("SELECT s FROM Schedule s WHERE s.staff.id = :staffId AND s.workDate = :workDate")
    List<Schedule> findByStaffIdAndWorkDate(@Param("staffId") Integer staffId, @Param("workDate") LocalDate workDate);

    @Query("SELECT s FROM Schedule s WHERE s.period.id = :periodId AND s.hasConflict = true")
    List<Schedule> findConflictsByPeriodId(@Param("periodId") Integer periodId);

    Optional<Schedule> findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
            @Param("periodId") Integer periodId,
            @Param("staffId") Integer staffId,
            @Param("shiftTypeId") String shiftTypeId,
            @Param("workDate") LocalDate workDate);

    @Query("SELECT COUNT(s) FROM Schedule s WHERE s.staff.id = :staffId AND s.period.id = :periodId")
    long countByStaffIdAndPeriodId(@Param("staffId") Integer staffId, @Param("periodId") Integer periodId);

    @Query("SELECT s FROM Schedule s WHERE s.staff.id = :staffId AND s.workDate BETWEEN :startDate AND :endDate ORDER BY s.workDate")
    List<Schedule> findByStaffIdAndDateRange(
            @Param("staffId") Integer staffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
