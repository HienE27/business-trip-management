package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.SchedulingResult;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.repository.ScheduleRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads the previous SchedulingResult snapshot from the database for a period.
 * Mirrors the load logic in AutoSchedulingService.reschedulePeriodIncremental (L4398-4403).
 * Package-private — only ScheduleExchangeService and ScheduleDeleteService use it.
 */
@Component
class SchedulingResultLoader {

    SchedulingResult loadPreviousFromDb(Integer periodId, ScheduleRepository scheduleRepository) {
        Map<String, String> map = new HashMap<>();
        for (Schedule s : scheduleRepository.findByPeriodId(periodId)) {
            map.put(s.getStaff().getId() + "_" + s.getWorkDate().toString(),
                    s.getShiftType().getId());
        }
        if (map.isEmpty()) {
            return null;
        }
        return SchedulingResult.builder().assignments(map).valid(true).build();
    }
}
