package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.SchedulePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchedulePeriodRepository extends JpaRepository<SchedulePeriod, Integer> {
    List<SchedulePeriod> findByStatusOrderByStartDateDesc(SchedulePeriod.PeriodStatus status);
    Optional<SchedulePeriod> findByStartDateAndEndDate(java.time.LocalDate startDate, java.time.LocalDate endDate);
}
