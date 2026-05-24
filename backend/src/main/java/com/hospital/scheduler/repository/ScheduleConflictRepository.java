package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.ScheduleConflict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleConflictRepository extends JpaRepository<ScheduleConflict, Integer> {
    List<ScheduleConflict> findByScheduleId(Integer scheduleId);
    List<ScheduleConflict> findByIsResolvedFalse();
    List<ScheduleConflict> findByScheduleIdAndIsResolvedFalse(Integer scheduleId);

    @Query("SELECT sc FROM ScheduleConflict sc WHERE sc.schedule.period.id = :periodId AND sc.isResolved = false")
    List<ScheduleConflict> findUnresolvedByPeriodId(@Param("periodId") Integer periodId);
}
