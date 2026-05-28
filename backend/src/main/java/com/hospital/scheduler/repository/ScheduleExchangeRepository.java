package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.ScheduleExchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleExchangeRepository extends JpaRepository<ScheduleExchange, Integer> {
    List<ScheduleExchange> findByRequesterId(Integer requesterId);
    List<ScheduleExchange> findByTargetId(Integer targetId);
    List<ScheduleExchange> findByStatus(ScheduleExchange.ExchangeStatus status);
    List<ScheduleExchange> findByPeriodIdAndStatus(Integer periodId, ScheduleExchange.ExchangeStatus status);
    List<ScheduleExchange> findPendingByRequesterIdOrTargetId(Integer requesterId, Integer targetId);
}
