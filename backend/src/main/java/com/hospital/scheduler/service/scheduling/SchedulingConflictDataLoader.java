package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pre-loads period-level conflict data (leaves, compensation days, schedules,
 * adjacent L01) in a single pass to avoid N+1 queries during the scheduling loop.
 *
 * <p>Loads:
 * <ul>
 *   <li>All approved leaves in the period</li>
 *   <li>All compensation days in the period</li>
 *   <li>All existing schedules for the period (grouped by date + staff)</li>
 *   <li>All adjacent L01 staff IDs (prev/next day of each date)</li>
 *   <li>Per-staff shift type counts</li>
 * </ul>
 */
@Slf4j
@Component
public class SchedulingConflictDataLoader {

    private final LeaveRequestRepository leaveRequestRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final ScheduleRepository scheduleRepository;
    private final EntityManager entityManager;
    private final SchedulingStateAccessor stateAccessor;
    private final AlgorithmConfigService algorithmConfigService;

    public SchedulingConflictDataLoader(LeaveRequestRepository leaveRequestRepository,
                                       CompensationDayRepository compensationDayRepository,
                                       ScheduleRepository scheduleRepository,
                                       EntityManager entityManager,
                                       SchedulingStateAccessor stateAccessor,
                                       AlgorithmConfigService algorithmConfigService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.compensationDayRepository = compensationDayRepository;
        this.scheduleRepository = scheduleRepository;
        this.entityManager = entityManager;
        this.stateAccessor = stateAccessor;
        this.algorithmConfigService = algorithmConfigService;
    }

    /**
     * Pre-loaded period-level conflict data (rebuilt each scheduling run).
     */
    public record BatchConflictData(
            Set<Integer> onLeaveStaffIds,
            Set<Integer> onCompDayStaffIds,
            Map<Integer, List<Schedule>> daySchedulesByStaff,
            Set<Integer> adjacentL01StaffIds
    ) {}

    /**
     * Period-level data pre-loaded once per scheduling run.
     */
    public record PeriodConflictData(
            Map<LocalDate, BatchConflictData> byDate,
            Map<Integer, Map<String, Long>> staffShiftTypeCounts,
            Set<Integer> allL01StaffIdsInRange,
            Map<Integer, Staff> staffMap
    ) {}

    public PeriodConflictData loadPeriodConflictData(SchedulePeriod period,
                                                    List<ShiftRequirement> requirements,
                                                    List<Staff> activeStaff) {
        LocalDate periodStart = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();

        // 1. Load all approved leaves (single query)
        Set<Integer> allOnLeave = new HashSet<>();
        for (LeaveRequest lr : leaveRequestRepository.findApprovedInRange(periodStart, periodEnd)) {
            allOnLeave.add(lr.getStaff().getId());
        }

        // 2. Load all compensation days (single query)
        Set<Integer> allOnCompDay = new HashSet<>();
        for (CompensationDay cd : compensationDayRepository.findInRange(periodStart, periodEnd)) {
            allOnCompDay.add(cd.getStaff().getId());
            String compKey = cd.getStaff().getId() + "_" + cd.getCompensationDate().toString();
            stateAccessor.addAllCompensationShiftDate(compKey);
        }

        // 3. Load all schedules for the period (single query)
        Map<Integer, List<Schedule>> allSchedulesByStaff = new HashMap<>();
        for (Schedule s : scheduleRepository.findByPeriodId(period.getId())) {
            allSchedulesByStaff.computeIfAbsent(s.getStaff().getId(), k -> new ArrayList<>()).add(s);
        }

        // 4. Collect all unique dates from requirements + schedules
        Set<LocalDate> allDates = new HashSet<>();
        for (ShiftRequirement req : requirements) {
            allDates.add(req.getWorkDate());
        }
        for (List<Schedule> staffSchedules : allSchedulesByStaff.values()) {
            for (Schedule s : staffSchedules) {
                allDates.add(s.getWorkDate());
            }
        }

        // 5. Build date range for adjacent L01 check (+2 to catch compensation day chains)
        LocalDate adjStart = periodStart.minusDays(1);
        LocalDate adjEnd = periodEnd.plusDays(2);

        // 6. Pre-load all L01 schedules in adjacent range
        Set<Integer> allL01StaffIds = new HashSet<>();
        for (Schedule s : scheduleRepository.findL01SchedulesInRange(adjStart, adjEnd)) {
            allL01StaffIds.add(s.getStaff().getId());
        }

        // 7. Build per-date maps for leaves/compensation by date
        Map<LocalDate, Set<Integer>> leavesByDate = new HashMap<>();
        for (LeaveRequest lr : leaveRequestRepository.findApprovedInRange(periodStart, periodEnd)) {
            LocalDate start = lr.getStartDate();
            LocalDate end = lr.getEndDate();
            LocalDate cursor = start.isBefore(periodStart) ? periodStart : start;
            LocalDate endLimit = end.isAfter(periodEnd) ? periodEnd : end;
            while (!cursor.isAfter(endLimit)) {
                leavesByDate.computeIfAbsent(cursor, k -> new HashSet<>()).add(lr.getStaff().getId());
                cursor = cursor.plusDays(1);
            }
        }

        Map<LocalDate, Set<Integer>> compDaysByDate = new HashMap<>();
        // ±1 day to catch boundary compensation days
        for (CompensationDay cd : compensationDayRepository.findInRange(periodStart.minusDays(1), periodEnd.plusDays(1))) {
            compDaysByDate.computeIfAbsent(cd.getCompensationDate(), k -> new HashSet<>()).add(cd.getStaff().getId());
            String compKey = cd.getStaff().getId() + "_" + cd.getCompensationDate().toString();
            stateAccessor.addAllCompensationShiftDate(compKey);
        }

        // Build prev/next L01 lookup per date
        Map<LocalDate, Set<Integer>> adjacentL01ByDate = new HashMap<>();
        for (Schedule s : scheduleRepository.findL01SchedulesInRange(adjStart, adjEnd)) {
            LocalDate adj = s.getWorkDate();
            adjacentL01ByDate.computeIfAbsent(adj.minusDays(1), k -> new HashSet<>()).add(s.getStaff().getId());
            adjacentL01ByDate.computeIfAbsent(adj.plusDays(1), k -> new HashSet<>()).add(s.getStaff().getId());
        }

        Map<LocalDate, BatchConflictData> byDate = new HashMap<>();
        for (LocalDate date : allDates) {
            Set<Integer> onLeave = leavesByDate.getOrDefault(date, Collections.emptySet());
            Set<Integer> onComp = compDaysByDate.getOrDefault(date, Collections.emptySet());

            Map<Integer, List<Schedule>> daySchedulesByStaff = new HashMap<>();
            for (Map.Entry<Integer, List<Schedule>> entry : allSchedulesByStaff.entrySet()) {
                for (Schedule s : entry.getValue()) {
                    if (s.getWorkDate().equals(date)) {
                        daySchedulesByStaff.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(s);
                    }
                }
            }

            Set<Integer> adjacentL01 = adjacentL01ByDate.getOrDefault(date, Collections.emptySet());
            byDate.put(date, new BatchConflictData(onLeave, onComp, daySchedulesByStaff, adjacentL01));
        }

        // 8. Build shift type counts per staff
        Map<Integer, Map<String, Long>> staffShiftTypeCounts = new HashMap<>();
        for (Map.Entry<Integer, List<Schedule>> entry : allSchedulesByStaff.entrySet()) {
            Map<String, Long> counts = new HashMap<>();
            counts.put("L01", 0L);
            counts.put("L02", 0L);
            counts.put("L03", 0L);
            counts.put("L04", 0L);
            for (Schedule s : entry.getValue()) {
                counts.merge(s.getShiftType().getId(), 1L, Long::sum);
                // Per-specialty L04 tracking
                if (ConflictDetectionService.SHIFT_TYPE_L04.equals(s.getShiftType().getId())
                        && s.getRequirement() != null
                        && s.getRequirement().getSpecialty() != null) {
                    String specKey = "L04:" + s.getRequirement().getSpecialty().getId();
                    counts.merge(specKey, 1L, Long::sum);
                }
            }
            staffShiftTypeCounts.put(entry.getKey(), counts);
        }

        // 9. Build staff map for maxShiftsPerMonth lookup
        Map<Integer, Staff> staffMap = new HashMap<>();
        for (Staff s : activeStaff) {
            staffMap.put(s.getId(), s);
        }

        return new PeriodConflictData(byDate, staffShiftTypeCounts, allL01StaffIds, staffMap);
    }
}
