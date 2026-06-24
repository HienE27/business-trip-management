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
            LEFT JOIN FETCH st.staffRoles sr
            LEFT JOIN FETCH sr.role
            JOIN FETCH s.shiftType
            JOIN FETCH s.period
            LEFT JOIN FETCH s.requirement
            WHERE s.period.id = :periodId
            ORDER BY s.workDate
            """)
    List<Schedule> findByPeriodId(@Param("periodId") Integer periodId);

    @Query("""
            SELECT s
            FROM Schedule s
            JOIN FETCH s.staff st
            LEFT JOIN FETCH st.specialty
            LEFT JOIN FETCH st.staffRoles sr
            LEFT JOIN FETCH sr.role
            JOIN FETCH s.shiftType
            JOIN FETCH s.period
            LEFT JOIN FETCH s.requirement
            WHERE s.period.id = :periodId AND s.workDate = :workDate
            ORDER BY s.workDate
            """)
    List<Schedule> findByPeriodIdAndWorkDate(@Param("periodId") Integer periodId, @Param("workDate") LocalDate workDate);

    @Query("""
            SELECT s
            FROM Schedule s
            JOIN FETCH s.staff st
            LEFT JOIN FETCH st.specialty
            LEFT JOIN FETCH st.staffRoles sr
            LEFT JOIN FETCH sr.role
            JOIN FETCH s.shiftType
            JOIN FETCH s.period
            LEFT JOIN FETCH s.requirement
            WHERE s.id = :id
            """)
    Optional<Schedule> findByIdWithDetails(@Param("id") Integer id);

    @Query("""
            SELECT s
            FROM Schedule s
            JOIN FETCH s.staff st
            LEFT JOIN FETCH st.specialty
            LEFT JOIN FETCH st.staffRoles sr
            LEFT JOIN FETCH sr.role
            JOIN FETCH s.shiftType
            JOIN FETCH s.period
            LEFT JOIN FETCH s.requirement
            WHERE st.id = :staffId
            ORDER BY s.workDate
            """)
    List<Schedule> findByStaffId(@Param("staffId") Integer staffId);

    @Query("SELECT s FROM Schedule s WHERE s.staff.id = :staffId AND s.workDate = :workDate")
    List<Schedule> findByStaffIdAndWorkDate(@Param("staffId") Integer staffId, @Param("workDate") LocalDate workDate);

    @Query("SELECT s FROM Schedule s WHERE s.staff.id = :staffId AND s.workDate = :workDate AND s.period.id = :periodId")
    List<Schedule> findByStaffIdAndWorkDateAndPeriodId(@Param("staffId") Integer staffId, @Param("workDate") LocalDate workDate, @Param("periodId") Integer periodId);

    @Query("""
            SELECT s
            FROM Schedule s
            JOIN FETCH s.staff st
            LEFT JOIN FETCH st.specialty
            JOIN FETCH s.shiftType
            WHERE s.workDate = :workDate
            """)
    List<Schedule> findByWorkDateWithDetails(@Param("workDate") LocalDate workDate);

    @Query("SELECT s FROM Schedule s WHERE s.period.id = :periodId AND s.hasConflict = true")
    List<Schedule> findConflictsByPeriodId(@Param("periodId") Integer periodId);

    Optional<Schedule> findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
            @Param("periodId") Integer periodId,
            @Param("staffId") Integer staffId,
            @Param("shiftTypeId") String shiftTypeId,
            @Param("workDate") LocalDate workDate);

    @Query("SELECT COUNT(s) FROM Schedule s WHERE s.staff.id = :staffId AND s.period.id = :periodId")
    long countByStaffIdAndPeriodId(@Param("staffId") Integer staffId, @Param("periodId") Integer periodId);

    @Query("SELECT COUNT(s) FROM Schedule s WHERE s.staff.id = :staffId AND s.period.id = :periodId AND s.shiftType.id = :shiftTypeId")
    long countByStaffIdAndPeriodIdAndShiftTypeId(
            @Param("staffId") Integer staffId,
            @Param("periodId") Integer periodId,
            @Param("shiftTypeId") String shiftTypeId);

    @Query("SELECT COUNT(s) FROM Schedule s WHERE s.staff.id = :staffId AND s.period.id = :periodId AND s.shiftType.id = :shiftTypeId AND s.id <> :excludeScheduleId")
    long countByStaffIdAndPeriodIdAndShiftTypeIdExcluding(
            @Param("staffId") Integer staffId,
            @Param("periodId") Integer periodId,
            @Param("shiftTypeId") String shiftTypeId,
            @Param("excludeScheduleId") Integer excludeScheduleId);

    @Query("SELECT COUNT(s) FROM Schedule s WHERE s.staff.id = :staffId AND s.period.id = :periodId AND s.id <> :excludeScheduleId")
    long countByStaffIdAndPeriodIdExcluding(@Param("staffId") Integer staffId, @Param("periodId") Integer periodId, @Param("excludeScheduleId") Integer excludeScheduleId);

    boolean existsByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
            Integer periodId, Integer staffId, String shiftTypeId, LocalDate workDate);

    @Query("SELECT COUNT(s) FROM Schedule s WHERE s.period.id = :periodId AND s.workDate = :workDate AND s.shiftType.id = :shiftTypeId")
    long countByPeriodIdAndWorkDateAndShiftTypeId(
            @Param("periodId") Integer periodId,
            @Param("workDate") LocalDate workDate,
            @Param("shiftTypeId") String shiftTypeId);

    @Query("SELECT s FROM Schedule s WHERE s.staff.id = :staffId AND s.workDate BETWEEN :startDate AND :endDate ORDER BY s.workDate")
    List<Schedule> findByStaffIdAndDateRange(
            @Param("staffId") Integer staffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT s FROM Schedule s WHERE s.staff.id = :staffId AND s.period.id = :periodId AND s.workDate BETWEEN :startDate AND :endDate ORDER BY s.workDate")
    List<Schedule> findByStaffIdAndDateRangeAndPeriodId(
            @Param("staffId") Integer staffId,
            @Param("periodId") Integer periodId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT s
            FROM Schedule s
            JOIN FETCH s.staff st
            LEFT JOIN FETCH st.specialty
            LEFT JOIN FETCH st.staffRoles sr
            LEFT JOIN FETCH sr.role
            JOIN FETCH s.shiftType
            JOIN FETCH s.period
            WHERE s.period.id = :periodId
            AND s.shiftType.id = 'L04'
            AND (:specialtyId IS NULL OR st.specialty.id = :specialtyId)
            ORDER BY s.workDate
            """)
    List<Schedule> findExpertClinicByPeriodAndSpecialty(
            @Param("periodId") Integer periodId,
            @Param("specialtyId") Integer specialtyId);
}
