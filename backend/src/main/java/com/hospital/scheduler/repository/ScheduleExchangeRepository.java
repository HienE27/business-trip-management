package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.ScheduleExchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleExchangeRepository extends JpaRepository<ScheduleExchange, Integer> {
    List<ScheduleExchange> findByRequesterId(Integer requesterId);
    List<ScheduleExchange> findByTargetId(Integer targetId);
    List<ScheduleExchange> findByStatus(ScheduleExchange.ExchangeStatus status);
    List<ScheduleExchange> findByPeriodIdAndStatus(Integer periodId, ScheduleExchange.ExchangeStatus status);
    List<ScheduleExchange> findPendingByRequesterIdOrTargetId(Integer requesterId, Integer targetId);

    @Query("SELECT e FROM ScheduleExchange e WHERE e.requester.id = :userId OR e.target.id = :userId ORDER BY e.createdAt DESC")
    List<ScheduleExchange> findAllByUserId(@Param("userId") Integer userId);
}
