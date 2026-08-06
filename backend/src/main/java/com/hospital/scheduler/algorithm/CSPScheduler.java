package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Staff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Facade over the CSP algorithm pipeline. The 1914-line monolith has been
 * split into focused modules:
 * <ul>
 *   <li>{@link CspDataBuilder} - input snapshot + initial AC-3</li>
 *   <li>{@link CspAc3Engine} - arc-consistency primitives</li>
 *   <li>{@link CspNogoodStore} - conflict-clause learning</li>
 *   <li>{@link CspSearchEngine} - MRV backtracking with propagation</li>
 *   <li>{@link CspResultBuilder} - domain-shaped output</li>
 *   <li>{@link CspIncrementalResolver} - delta-based re-solve</li>
 *   <li>{@link CspConstants} - shared shift-type constants</li>
 * </ul>
 * This class only wires the modules together and implements the
 * {@link SchedulingAlgorithm} contract.
 *
 * <p>Business constraints encoded in the modules:
 * <ul>
 *   <li>BR-01: L01 + L02 same staff same day = CONFLICT</li>
 *   <li>BR-02: L03 + L04 same staff same day = CONFLICT</li>
 *   <li>BR-03: REST day blocks ALL shifts (L01/L02/L03/L04)</li>
 *   <li>BR-04: Holiday/Leave handling</li>
 *   <li>BR-05: Max shifts per staff</li>
 *   <li>BR-06: DIRECT_24H max 1 per day</li>
 * </ul>
 *
 * <p>L04 luôn strict-specialty (không cross-specialty) — được thay bằng
 * "mở PK theo ngày bs rảnh" + "đổi ngày mở thích ứng".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CSPScheduler implements SchedulingAlgorithm {

    private final CspDataBuilder dataBuilder;
    private final CspSearchEngine searchEngine;
    private final CspResultBuilder resultBuilder;
    private final CspIncrementalResolver incrementalResolver;

    @Override
    public String getName() {
        return "CSP-MRV-FC";
    }

    @Override
    public String getDescription() {
        return "Industrial CSP với đầy đủ ràng buộc nghiệp vụ - BR-01 đến BR-06";
    }

    @Override
    public SchedulingResult solve(
            List<Staff> staffList,
            LocalDate startDate,
            LocalDate endDate,
            List<ShiftRequirementInfo> requirements,
            Set<String> existingCompensationDays,
            List<LeaveRequest> leaveRequests,
            Set<Integer> excludedStaffIds) {
        return solve(staffList, startDate, endDate, requirements,
                existingCompensationDays, leaveRequests, excludedStaffIds, 0);
    }

    public SchedulingResult solve(
            List<Staff> staffList,
            LocalDate startDate,
            LocalDate endDate,
            List<ShiftRequirementInfo> requirements,
            Set<String> existingCompensationDays,
            List<LeaveRequest> leaveRequests,
            Set<Integer> excludedStaffIds,
            int maxShiftsPerStaff) {

        long startTime = System.currentTimeMillis();

        List<Staff> activeStaff = staffList.stream()
                .filter(Staff::getIsActive)
                .toList();
        if (excludedStaffIds != null && !excludedStaffIds.isEmpty()) {
            activeStaff = activeStaff.stream()
                    .filter(s -> !excludedStaffIds.contains(s.getId()))
                    .toList();
        }
        if (activeStaff.isEmpty()) {
            return SchedulingResult.builder()
                    .valid(false)
                    .errors(List.of("Không có nhân sự nào hoạt động"))
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        int numDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<LocalDate> dates = new ArrayList<>(numDays);
        for (int i = 0; i < numDays; i++) dates.add(startDate.plusDays(i));

        ProblemData data = dataBuilder.build(activeStaff, dates, requirements, leaveRequests,
                null, null, maxShiftsPerStaff);
        // OPT-003: use adaptive timeout — problem-size-based + progress extension
        CspSearchEngine.Result solution = searchEngine.solve(data, startTime);
        long elapsedMs = System.currentTimeMillis() - startTime;
        log.info("CSP solve completed in {}ms: valid={} partial={} assignments={}",
                elapsedMs, solution.isValid(), solution.isPartial(), solution.getScheduleCount());
        return resultBuilder.build(solution, data, activeStaff, dates, startTime);
    }

    @Override
    public boolean canReSolveIncrementally(ScheduleChange deltaChanges) {
        if (deltaChanges == null || !deltaChanges.hasChanges()) return false;
        return !deltaChanges.requiresFullReSolve();
    }

    /**
     * Preview-only solve with a wall-clock budget tuned for the 23-staff
     * Period 5 (Sept 2026) workload: ~25% L04 specialty variables dominate
     * the search space, so 8s was too tight and surfaced 0 schedules even
     * though a feasible plan exists. Bumped to 30s to match the production
     * path so the user sees the same coverage as the auto-schedule endpoint.
     * Production callers (auto-schedule, reschedule, incremental) MUST keep
     * using {@link #solve} to preserve the original timeout semantics.
     */
    public SchedulingResult solveForPreview(
            List<Staff> staffList,
            LocalDate startDate,
            LocalDate endDate,
            List<ShiftRequirementInfo> requirements,
            Set<String> existingCompensationDays,
            List<LeaveRequest> leaveRequests,
            Set<Integer> excludedStaffIds,
            int maxShiftsPerStaff) {
        long startTime = System.currentTimeMillis();

        List<Staff> activeStaff = staffList.stream()
                .filter(Staff::getIsActive)
                .toList();
        if (excludedStaffIds != null && !excludedStaffIds.isEmpty()) {
            activeStaff = activeStaff.stream()
                    .filter(s -> !excludedStaffIds.contains(s.getId()))
                    .toList();
        }
        if (activeStaff.isEmpty()) {
            return SchedulingResult.builder()
                    .valid(false)
                    .errors(List.of("Không có nhân sự nào hoạt động"))
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        int numDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<LocalDate> dates = new ArrayList<>(numDays);
        for (int i = 0; i < numDays; i++) dates.add(startDate.plusDays(i));

        ProblemData data = dataBuilder.build(activeStaff, dates, requirements, leaveRequests,
                null, null, maxShiftsPerStaff);
        // The preview path used to cap at 8s but Period 5 (23 staff, 6
        // specialties, ~899 required slots) needs more time to make a
        // meaningful first plan before falling back to Greedy. Bumped to
        // 30s for faster UX on preview, with option to increase for apply.
        CspSearchEngine.Result solution = searchEngine.solve(data, startTime, 30_000L);
        long elapsedMs = System.currentTimeMillis() - startTime;
        if (solution.isPartial()) {
            log.info("CSP solveForPreview: partial after {}ms, {} assignments (timeout hit)",
                    elapsedMs, solution.getScheduleCount());
        } else if (solution.isValid()) {
            log.info("CSP solveForPreview: complete in {}ms, {} assignments",
                    elapsedMs, solution.getScheduleCount());
        } else {
            log.warn("CSP solveForPreview: no solution found after {}ms", elapsedMs);
        }
        return resultBuilder.build(solution, data, activeStaff, dates, startTime);
    }

    @Override
    public SchedulingResult reSolve(
            SchedulingResult previousResult,
            ScheduleChange deltaChanges,
            List<Staff> staffList,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests) {
        return incrementalResolver.reSolve(previousResult, deltaChanges, staffList, requirements, leaveRequests);
    }
}
