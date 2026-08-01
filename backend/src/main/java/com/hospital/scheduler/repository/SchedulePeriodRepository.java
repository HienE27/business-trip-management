package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.SchedulePeriod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Paginated query with optional status + keyword filters. */
    @Query("SELECT p FROM SchedulePeriod p " +
           "WHERE (:status IS NULL OR p.status = :status) " +
           "AND (:keyword IS NULL OR LOWER(p.periodName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<SchedulePeriod> findPageWithFilters(
            @Param("status") SchedulePeriod.PeriodStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);
}
