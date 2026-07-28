package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AuditHistoryService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Persists individual schedules and handles compensation-day side effects.
 */
@Slf4j
@Component
public class SchedulePersistenceService {

    private final AuditHistoryService auditHistoryService;
    private final CompensationDateCalculator compensationDateCalculator;
    private final SchedulingStateAccessor stateAccessor;

    public SchedulePersistenceService(AuditHistoryService auditHistoryService,
                                      CompensationDateCalculator compensationDateCalculator,
                                      SchedulingStateAccessor stateAccessor) {
        this.auditHistoryService = auditHistoryService;
        this.compensationDateCalculator = compensationDateCalculator;
        this.stateAccessor = stateAccessor;
    }

    /**
     * Build a Schedule entity (does not save).
     */
    public Schedule buildSchedule(SchedulePeriod period, Staff staff, ShiftRequirement req, LocalDate workDate) {
        return Schedule.builder()
                .period(period)
                .staff(staff)
                .shiftType(req.getShiftType())
                .workDate(workDate)
                .requirement(req)
                .hasConflict(false)
                .build();
    }

    /**
     * Build a Schedule entity without a requirement (used by GA-fallback top-up).
     */
    public Schedule buildNewSchedule(Staff staff, ShiftRequirement req, LocalDate workDate) {
        return Schedule.builder()
                .staff(staff)
                .shiftType(req.getShiftType())
                .workDate(workDate)
                .period(req.getPeriod())
                .requirement(req)
                .hasConflict(false)
                .isPreview(false)
                .build();
    }

    /**
     * Track a staff→date→shift assignment in memory (used during preview/algorithm run).
     */
    public void trackAssignment(Staff staff, LocalDate workDate, String shiftTypeId) {
        stateAccessor.addAssignment(staff.getId(), workDate, shiftTypeId);
        // Also track compensation day for L01
        if ("L01".equals(shiftTypeId)) {
            LocalDate compDate = compensationDateCalculator.calculate(workDate);
            if (compDate != null) {
                stateAccessor.addCompensationShiftDate(staff.getId(), compDate);
            }
        }
    }

    /**
     * Create a compensation day for a saved L01 schedule.
     * Idempotent: checks both in-memory cache and DB before inserting.
     */
    public void createCompensationDayForAuto(com.hospital.scheduler.repository.CompensationDayRepository compensationDayRepository,
                                            Schedule schedule) {
        if (schedule == null || schedule.getWorkDate() == null) {
            log.warn("createCompensationDayForAuto: schedule or workDate is null");
            return;
        }
        LocalDate shiftDate = schedule.getWorkDate();
        LocalDate compensationDate = compensationDateCalculator.calculate(shiftDate);

        log.info("createCompensationDayForAuto: staffId={}, shiftDate={}, compDate={}",
                schedule.getStaff().getId(), shiftDate, compensationDate);

        String compKey = schedule.getStaff().getId() + "_" + compensationDate.toString();

        if (stateAccessor.getAllCompensationShiftDates().contains(compKey)) {
            log.debug("Compensation day already tracked in memory for {}", compKey);
            return;
        }

        // Check DB
        if (compensationDayRepository.existsByStaffIdAndCompensationDate(
                schedule.getStaff().getId(), compensationDate)) {
            log.warn("Compensation day already exists in DB for staff {} on {}",
                    schedule.getStaff().getId(), compensationDate);
            stateAccessor.addAllCompensationShiftDate(compKey);
            return;
        }

        if (schedule.getId() != null && compensationDayRepository.existsByScheduleId(schedule.getId())) {
            log.warn("Schedule {} already has a compensation day", schedule.getId());
            stateAccessor.addAllCompensationShiftDate(compKey);
            return;
        }

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
                stateAccessor.addAllCompensationShiftDate(compKey);
            } else {
                log.debug("Compensation day already existed (INSERT IGNORE): staffId={}, compDate={}",
                        schedule.getStaff().getId(), compensationDate);
                stateAccessor.addAllCompensationShiftDate(compKey);
            }
        } catch (Exception e) {
            log.warn("Failed to insert compensation day for staff {} on {}: {}",
                    schedule.getStaff().getId(), compensationDate, e.getMessage());
            stateAccessor.addAllCompensationShiftDate(compKey);
        }
    }

    /**
     * Overload with an explicit compensation date (bypasses calculator).
     * Used when the CSP has already chosen the best option among flexible
     * compensation days (e.g. Tue/Wed/Thu for Fri/Sat duty).
     */
    public void createCompensationDayForAuto(
            com.hospital.scheduler.repository.CompensationDayRepository compensationDayRepository,
            Schedule schedule,
            LocalDate compensationDate) {
        if (schedule == null || schedule.getWorkDate() == null || compensationDate == null) {
            log.warn("createCompensationDayForAuto(override): schedule or date is null");
            return;
        }
        LocalDate shiftDate = schedule.getWorkDate();

        log.info("createCompensationDayForAuto(override): staffId={}, shiftDate={}, compDate={}",
                schedule.getStaff().getId(), shiftDate, compensationDate);

        String compKey = schedule.getStaff().getId() + "_" + compensationDate.toString();

        if (stateAccessor.getAllCompensationShiftDates().contains(compKey)) {
            log.debug("Compensation day already tracked in memory for {}", compKey);
            return;
        }

        if (compensationDayRepository.existsByStaffIdAndCompensationDate(
                schedule.getStaff().getId(), compensationDate)) {
            log.warn("Compensation day already exists in DB for staff {} on {}",
                    schedule.getStaff().getId(), compensationDate);
            stateAccessor.addAllCompensationShiftDate(compKey);
            return;
        }

        if (schedule.getId() != null && compensationDayRepository.existsByScheduleId(schedule.getId())) {
            log.warn("Schedule {} already has a compensation day", schedule.getId());
            stateAccessor.addAllCompensationShiftDate(compKey);
            return;
        }

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
                log.info("Compensation day INSERTED (override): staffId={}, compDate={}",
                        schedule.getStaff().getId(), compensationDate);
                stateAccessor.addAllCompensationShiftDate(compKey);
            } else {
                log.debug("Compensation day already existed (override): staffId={}, compDate={}",
                        schedule.getStaff().getId(), compensationDate);
                stateAccessor.addAllCompensationShiftDate(compKey);
            }
        } catch (Exception e) {
            log.warn("Failed to insert compensation day (override) for staff {} on {}: {}",
                    schedule.getStaff().getId(), compensationDate, e.getMessage());
            stateAccessor.addAllCompensationShiftDate(compKey);
        }
    }
}
