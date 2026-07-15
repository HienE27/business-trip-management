package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.CompensationDay;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.repository.CompensationDayRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service responsible for auto-creating compensation days for L01 shifts.
 * Extracted from the monolithic AutoSchedulingService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationDayAutoService {

    private final CompensationDayRepository compensationDayRepository;
    private final ScheduleRepository scheduleRepository;
    private final CompensationDateCalculator compensationDateCalculator;

    // Thread-local so concurrent requests don't share state
    private final ThreadLocal<Set<String>> allCompensationShiftDates = ThreadLocal.withInitial(HashSet::new);

    /**
     * Get the thread-local compensation shift dates tracker.
     */
    public ThreadLocal<Set<String>> getAllCompensationShiftDates() {
        return allCompensationShiftDates;
    }

    /**
     * Create a compensation day for an L01 schedule automatically.
     * Each L01 shift requires one compensation day (24h recovery rule).
     */
    public void createCompensationDayForAuto(Schedule schedule) {
        if (schedule == null || schedule.getWorkDate() == null) {
            log.warn("createCompensationDayForAuto: schedule or workDate is null");
            return;
        }
        LocalDate shiftDate = schedule.getWorkDate();
        LocalDate compensationDate = compensationDateCalculator.calculate(shiftDate);

        log.info("createCompensationDayForAuto: staffId={}, shiftDate={}, compDate={}",
                schedule.getStaff().getId(), shiftDate, compensationDate);

        String compKey = schedule.getStaff().getId() + "_" + compensationDate.toString();

        // Check in-memory cache first (for current run)
        if (allCompensationShiftDates.get().contains(compKey)) {
            log.debug("Compensation day already tracked in memory for {}", compKey);
            return;
        }

        // CRITICAL FIX: Also check database for existing compensation day
        // This prevents duplicate entries when re-running the algorithm
        if (compensationDayRepository.existsByStaffIdAndCompensationDate(
                schedule.getStaff().getId(), compensationDate)) {
            log.warn("Compensation day already exists in DB for staff {} on {}",
                    schedule.getStaff().getId(), compensationDate);
            // Add to in-memory cache to prevent duplicate checks
            allCompensationShiftDates.get().add(compKey);
            return;
        }

        // Also check if this schedule already has a compensation day (by schedule_id)
        if (schedule.getId() != null && compensationDayRepository.existsByScheduleId(schedule.getId())) {
            log.warn("Schedule {} already has a compensation day", schedule.getId());
            allCompensationShiftDates.get().add(compKey);
            return;
        }

        // Use INSERT IGNORE to avoid duplicate key errors
        // This is the proper fix from commit 5d080c1 - prevents Hibernate assertion failures
        try {
            int inserted = compensationDayRepository.insertIgnoreCompensationDay(
                    schedule.getStaff().getId(),
                    schedule.getPeriod().getId(),
                    schedule.getId(),
                    shiftDate,
                    compensationDate,
                    "Ngày nghỉ bù tự động từ ca L01"
            );
            if (inserted > 0) {
                log.info("Compensation day INSERTED via INSERT IGNORE: staffId={}, compDate={}",
                        schedule.getStaff().getId(), compensationDate);
                allCompensationShiftDates.get().add(compKey);
            } else {
                log.debug("Compensation day already existed (INSERT IGNORE): staffId={}, compDate={}",
                        schedule.getStaff().getId(), compensationDate);
                allCompensationShiftDates.get().add(compKey);
            }
        } catch (Exception e) {
            log.warn("Failed to insert compensation day for staff {} on {}: {}",
                    schedule.getStaff().getId(), compensationDate, e.getMessage());
            // Still add to cache to prevent further attempts
            allCompensationShiftDates.get().add(compKey);
        }
    }

    /**
     * Create compensation days for all L01 schedules in a period.
     * CRITICAL: Each L01 shift requires ONE compensation day (24h recovery rule).
     * Each schedule -> 1 compensation day mapping.
     */
    public void createCompensationDaysForL01InPeriod(Integer periodId) {
        log.info("Creating compensation days for all L01 schedules in period {}", periodId);

        List<Schedule> l01Schedules = scheduleRepository.findByPeriodIdAndShiftTypeId(periodId, "L01");
        log.info("Found {} L01 schedules in period {}", l01Schedules.size(), periodId);

        int created = 0;
        int skipped = 0;
        int errors = 0;

        for (Schedule schedule : l01Schedules) {
            try {
                LocalDate shiftDate = schedule.getWorkDate();
                LocalDate compensationDate = compensationDateCalculator.calculate(shiftDate);

                // Use INSERT IGNORE to avoid duplicate key errors - this is the proper fix
                int inserted = compensationDayRepository.insertIgnoreCompensationDay(
                        schedule.getStaff().getId(),
                        schedule.getPeriod().getId(),
                        schedule.getId(),
                        shiftDate,
                        compensationDate,
                        "Ngày nghỉ bù tự động từ ca L01 (shift_id=" + schedule.getId() + ")"
                );
                if (inserted > 0) {
                    created++;
                } else {
                    skipped++;
                }

            } catch (Exception e) {
                log.warn("Error creating compensation day for schedule {}: {}", schedule.getId(), e.getMessage());
                errors++;
            }
        }

        log.info("Compensation day creation complete: created={}, skipped={}, errors={}", created, skipped, errors);
    }

    /**
     * Clear the in-memory compensation shift dates cache.
     */
    public void clearCache() {
        allCompensationShiftDates.get().clear();
    }

    /**
     * Add a compensation key to the in-memory cache.
     */
    public void addToCache(String compKey) {
        allCompensationShiftDates.get().add(compKey);
    }

    /**
     * Check if a compensation key exists in the in-memory cache.
     */
    public boolean isInCache(String compKey) {
        return allCompensationShiftDates.get().contains(compKey);
    }

    /**
     * Remove all thread-local state for this service.
     */
    public void removeThreadLocal() {
        allCompensationShiftDates.remove();
    }
}
