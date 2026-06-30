package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.ScheduleExchange;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleExchangeRepository extends JpaRepository<ScheduleExchange, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM ScheduleExchange e WHERE e.id = :id")
    Optional<ScheduleExchange> findByIdWithLock(@Param("id") Integer id);
    List<ScheduleExchange> findByRequesterId(Integer requesterId);
    List<ScheduleExchange> findByTargetId(Integer targetId);
    List<ScheduleExchange> findByStatus(ScheduleExchange.ExchangeStatus status);
    List<ScheduleExchange> findByPeriodIdAndStatus(Integer periodId, ScheduleExchange.ExchangeStatus status);
    List<ScheduleExchange> findPendingByRequesterIdOrTargetId(Integer requesterId, Integer targetId);

    @Query("SELECT e FROM ScheduleExchange e WHERE e.requester.id = :userId OR e.target.id = :userId ORDER BY e.createdAt DESC")
    List<ScheduleExchange> findAllByUserId(@Param("userId") Integer userId);

    List<ScheduleExchange> findByRequesterScheduleId(Integer scheduleId);
    List<ScheduleExchange> findByTargetScheduleId(Integer scheduleId);
    List<ScheduleExchange> findByRequesterScheduleIdOrTargetScheduleId(Integer requesterScheduleId, Integer targetScheduleId);
}
