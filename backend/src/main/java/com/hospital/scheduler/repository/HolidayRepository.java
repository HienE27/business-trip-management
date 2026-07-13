package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Integer> {
    Optional<Holiday> findByHolidayDate(LocalDate holidayDate);
    List<Holiday> findByIsActiveTrue();
    List<Holiday> findByYear(Integer year);
    List<Holiday> findByHolidayDateBetween(LocalDate start, LocalDate end);
    /**
     * BUGFIX (was BE#16): original {@code existsByHolidayDate} matched the row regardless
     * of {@code isActive}. After soft-delete (isActive=false), users could never
     * re-create a holiday on the same date — the row was still there blocking the
     * insert via UNIQUE constraint. Now we check active rows only, so soft-deleted
     * rows no longer block re-creation.
     */
    boolean existsByHolidayDateAndIsActiveTrue(LocalDate holidayDate);

    /**
     * BUGFIX (was BE#16): find the soft-deleted row for a given date so
     * {@code createHoliday} can reactivate it (upsert) instead of throwing a
     * UNIQUE constraint violation. Returns the most recently deactivated row.
     */
    @Query("SELECT h FROM Holiday h WHERE h.holidayDate = :date AND h.isActive = false ORDER BY h.id DESC")
    List<Holiday> findInactiveByHolidayDate(@Param("date") LocalDate date);

    @Query("SELECT h FROM Holiday h WHERE h.holidayDate BETWEEN :start AND :end AND h.isActive = true")
    List<Holiday> findActiveHolidaysBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
