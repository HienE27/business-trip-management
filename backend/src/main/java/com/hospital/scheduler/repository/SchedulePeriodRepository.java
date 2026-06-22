package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.SchedulePeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchedulePeriodRepository extends JpaRepository<SchedulePeriod, Integer> {
    List<SchedulePeriod> findByStatusOrderByStartDateDesc(SchedulePeriod.PeriodStatus status);
    Optional<SchedulePeriod> findByStartDateAndEndDate(LocalDate startDate, LocalDate endDate);
    List<SchedulePeriod> findAllByStartDateAndEndDate(LocalDate startDate, LocalDate endDate);

    List<SchedulePeriod> findAllByIdIn(List<Integer> ids);
}
