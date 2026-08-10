package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.Holiday;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Integer> {
    /**
     * Returns ALL holiday rows (active and inactive) for the given date.
     * Intentionally not scoped by {@code isActive}: this is a key-based lookup
     * used when callers need to know whether a row exists at all (e.g.
     * {@code DataSeeder.seedHolidays}). For the "is this date currently a
     * holiday?" question, use {@link #existsByHolidayDateAndIsActiveTrue}.
     */
    Optional<Holiday> findByHolidayDate(LocalDate holidayDate);

    List<Holiday> findByIsActiveTrue();
    List<Holiday> findByYear(Integer year);

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

    /**
     * Paginated query supporting optional year and isActive filters.
     * When year is null → returns all years. When isActive is null → returns both.
     * Results sorted by holidayDate descending.
     */
    @Query("SELECT h FROM Holiday h WHERE " +
           "(:year IS NULL OR h.year = :year) AND " +
           "(:isActive IS NULL OR h.isActive = :isActive) " +
           "ORDER BY h.holidayDate DESC")
    org.springframework.data.domain.Page<Holiday> findByYearAndIsActive(
            @Param("year") Integer year,
            @Param("isActive") Boolean isActive,
            Pageable pageable);
}
