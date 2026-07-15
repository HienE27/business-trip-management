package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Integer> {

    @Modifying
    @Query("DELETE FROM Schedule s WHERE s.period.id = :periodId")
    void deleteAllByPeriodId(@Param("periodId") Integer periodId);

    @Modifying
    @Query("DELETE FROM Schedule s WHERE s.id = :id")
    void deleteByIdQuery(@Param("id") Integer id);

    /**
     * Clear requirement FK references for schedules linked to specific requirements.
     * Uses native query for immediate execution to avoid batch processing issues.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE schedule SET requirement_id = NULL WHERE requirement_id IN :requirementIds", nativeQuery = true)
    void clearRequirementReferencesByRequirementIds(@Param("requirementIds") List<Integer> requirementIds);

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

    /** Count upcoming schedules for a staff member on or after the given date. */
    long countByStaffIdAndWorkDateGreaterThanEqual(Integer staffId, LocalDate workDate);

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

    @Query("SELECT s FROM Schedule s WHERE s.period.id = :periodId AND s.shiftType.id = :shiftTypeId")
    List<Schedule> findByPeriodIdAndShiftTypeId(@Param("periodId") Integer periodId, @Param("shiftTypeId") String shiftTypeId);

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

    /**
     * Batch load all L01 schedules within a date range in a single query.
     * Replaces 2 queries per day (prevDay + nextDay) with 1 query per period.
     */
    @Query("SELECT s FROM Schedule s WHERE s.shiftType.id = 'L01' AND s.workDate BETWEEN :startDate AND :endDate")
    List<Schedule> findL01SchedulesInRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Batch load all shift counts for all staff in a single query.
     * Returns Object[] arrays: [staffId, shiftTypeId, count].
     * Replaces 4 queries per staff (N×4 → 1 query total).
     */
    @Query("SELECT s.staff.id, s.shiftType.id, COUNT(s) FROM Schedule s WHERE s.period.id = :periodId GROUP BY s.staff.id, s.shiftType.id")
    List<Object[]> countAllByPeriodIdGroupByStaffAndShiftType(@Param("periodId") Integer periodId);

    /**
     * Batch query: count schedules grouped by period, workDate, and shiftType.
     * Replaces N individual countByPeriodIdAndWorkDateAndShiftTypeId calls → 1 query.
     */
    @Query("SELECT s.period.id, s.workDate, s.shiftType.id, COUNT(s) FROM Schedule s WHERE s.period.id = :periodId GROUP BY s.period.id, s.workDate, s.shiftType.id")
    List<Object[]> countGroupedByPeriodWorkDateShiftType(@Param("periodId") Integer periodId);

    /**
     * Batch query: count ALL schedules grouped by period, workDate, and shiftType.
     * For use with paginated shift requirements.
     */
    @Query("SELECT s.period.id, s.workDate, s.shiftType.id, COUNT(s) FROM Schedule s GROUP BY s.period.id, s.workDate, s.shiftType.id")
    List<Object[]> countGroupedByPeriodWorkDateShiftType();

    /**
     * BUGFIX (was BE#20) helper: aggregate per-period (totalSchedules, distinctStaff)
     * in a single grouped query, replacing the N+1 of getPeriodSummaries().
     *
     * <p>Returns Object[] rows of {@code [periodId(Integer), totalSchedules(Long),
     * distinctStaff(Long)]}. Periods with zero schedules are absent from the
     * result — callers must combine this with their own period list and treat
     * missing keys as 0.
     */
    @Query("SELECT s.period.id, COUNT(s), COUNT(DISTINCT s.staff.id) " +
           "FROM Schedule s GROUP BY s.period.id")
    List<Object[]> aggregateByPeriod();
}
