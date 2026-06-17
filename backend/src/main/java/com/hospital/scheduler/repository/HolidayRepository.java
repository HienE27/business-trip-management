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
    boolean existsByHolidayDate(LocalDate holidayDate);

    @Query("SELECT h FROM Holiday h WHERE h.holidayDate BETWEEN :start AND :end AND h.isActive = true")
    List<Holiday> findActiveHolidaysBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
