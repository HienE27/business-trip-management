package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.ShiftRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

public interface ShiftRequirementRepository extends JpaRepository<ShiftRequirement, Integer> {
    List<ShiftRequirement> findByPeriodId(Integer periodId);

    @Query("SELECT sr FROM ShiftRequirement sr WHERE sr.period.id = :periodId AND sr.workDate = :workDate")
    List<ShiftRequirement> findByPeriodIdAndWorkDate(@Param("periodId") Integer periodId, @Param("workDate") LocalDate workDate);

    @Query("SELECT sr FROM ShiftRequirement sr WHERE sr.period.id = :periodId AND sr.workDate BETWEEN :startDate AND :endDate")
    List<ShiftRequirement> findByPeriodIdAndDateRange(
            @Param("periodId") Integer periodId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT sr FROM ShiftRequirement sr WHERE sr.period.id = :periodId AND sr.workDate = :workDate AND sr.shiftType.id = :shiftTypeId")
    java.util.Optional<ShiftRequirement> findByPeriodIdAndWorkDateAndShiftTypeId(
            @Param("periodId") Integer periodId,
            @Param("workDate") LocalDate workDate,
            @Param("shiftTypeId") String shiftTypeId);
}
