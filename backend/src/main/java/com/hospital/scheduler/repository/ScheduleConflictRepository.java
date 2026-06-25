package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.ScheduleConflict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleConflictRepository extends JpaRepository<ScheduleConflict, Integer> {
    @Query("SELECT sc FROM ScheduleConflict sc WHERE sc.schedule.id = :scheduleId ORDER BY sc.createdAt DESC")
    List<ScheduleConflict> findByScheduleId(@Param("scheduleId") Integer scheduleId);
    
    @Query("SELECT sc FROM ScheduleConflict sc WHERE sc.isResolved = false ORDER BY sc.createdAt DESC")
    List<ScheduleConflict> findByIsResolvedFalse();
    
    @Query("SELECT sc FROM ScheduleConflict sc WHERE sc.schedule.id = :scheduleId AND sc.isResolved = false ORDER BY sc.createdAt DESC")
    List<ScheduleConflict> findByScheduleIdAndIsResolvedFalse(@Param("scheduleId") Integer scheduleId);

    @Query("SELECT sc FROM ScheduleConflict sc JOIN FETCH sc.schedule WHERE sc.schedule.period.id = :periodId AND sc.isResolved = false ORDER BY sc.createdAt DESC")
    List<ScheduleConflict> findUnresolvedByPeriodId(@Param("periodId") Integer periodId);

    @Query("SELECT sc FROM ScheduleConflict sc JOIN FETCH sc.schedule WHERE sc.schedule.id IN :scheduleIds ORDER BY sc.createdAt DESC")
    List<ScheduleConflict> findByScheduleIdsIn(@Param("scheduleIds") List<Integer> scheduleIds);

    @Query("SELECT sc FROM ScheduleConflict sc JOIN FETCH sc.schedule WHERE sc.schedule.id IN :scheduleIds AND sc.isResolved = false ORDER BY sc.createdAt DESC")
    List<ScheduleConflict> findUnresolvedByScheduleIdsIn(@Param("scheduleIds") List<Integer> scheduleIds);
}
