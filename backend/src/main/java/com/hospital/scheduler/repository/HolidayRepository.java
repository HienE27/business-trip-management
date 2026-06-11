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
    Optional<Holiday> findByDate(LocalDate date);
    List<Holiday> findByIsActiveTrue();
    List<Holiday> findByDateBetween(LocalDate start, LocalDate end);
    boolean existsByDate(LocalDate date);

    @Query("SELECT h FROM Holiday h WHERE h.date BETWEEN :start AND :end AND h.isActive = true")
    List<Holiday> findActiveHolidaysBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
