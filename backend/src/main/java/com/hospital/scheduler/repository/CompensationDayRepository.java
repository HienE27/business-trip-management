package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.CompensationDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompensationDayRepository extends JpaRepository<CompensationDay, Integer> {
    List<CompensationDay> findByStaffId(Integer staffId);
    List<CompensationDay> findByCompensationDate(LocalDate compensationDate);
    Optional<CompensationDay> findByStaffIdAndCompensationDate(Integer staffId, LocalDate compensationDate);
    @Query("SELECT cd FROM CompensationDay cd WHERE cd.staff.id = :staffId AND cd.period.id = :periodId")
    List<CompensationDay> findByStaffIdAndPeriodId(@Param("staffId") Integer staffId, @Param("periodId") Integer periodId);
    @Query("SELECT cd FROM CompensationDay cd WHERE cd.schedule.id = :scheduleId")
    List<CompensationDay> findByScheduleId(@Param("scheduleId") Integer scheduleId);

    @Query("SELECT cd FROM CompensationDay cd WHERE cd.period.id = :periodId")
    List<CompensationDay> findByPeriodId(@Param("periodId") Integer periodId);

    @Query("SELECT cd FROM CompensationDay cd JOIN FETCH cd.staff WHERE cd.period.id = :periodId")
    List<CompensationDay> findByPeriodIdWithStaff(@Param("periodId") Integer periodId);

    @Query("SELECT cd FROM CompensationDay cd WHERE cd.staff.id = :staffId AND cd.compensationDate BETWEEN :startDate AND :endDate")
    List<CompensationDay> findByStaffIdAndDateRange(
            @Param("staffId") Integer staffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /** Batch query: all compensation days for a specific date (no staff filter). */
    @Query("SELECT cd FROM CompensationDay cd WHERE cd.compensationDate = :date")
    List<CompensationDay> findByDate(@Param("date") LocalDate date);
}
