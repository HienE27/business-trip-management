package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Staff;
import lombok.RequiredArgsConstructor;
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
 */
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
                existingCompensationDays, leaveRequests, excludedStaffIds, null);
    }

    @Override
    public SchedulingResult solve(
            List<Staff> staffList,
            LocalDate startDate,
            LocalDate endDate,
            List<ShiftRequirementInfo> requirements,
            Set<String> existingCompensationDays,
            List<LeaveRequest> leaveRequests,
            Set<Integer> excludedStaffIds,
            List<String> l04AllowedSpecialties) {

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

        ProblemData data = dataBuilder.build(activeStaff, dates, requirements, leaveRequests, l04AllowedSpecialties);
        CspSearchEngine.Result solution = searchEngine.solve(data, startTime);
        return resultBuilder.build(solution, data, activeStaff, dates, startTime);
    }

    @Override
    public boolean canReSolveIncrementally(ScheduleChange deltaChanges) {
        if (deltaChanges == null || !deltaChanges.hasChanges()) return false;
        return !deltaChanges.requiresFullReSolve();
    }

    /**
     * Preview-only solve with a tighter wall-clock budget.  Uses 8 s instead
     * of the default 30 s so the preview endpoint returns fast — a partial /
     * empty plan is acceptable in preview mode, the user just wants a quick
     * "does this look right" snapshot.
     *
     * <p>Production callers (auto-schedule, reschedule, incremental) MUST
     * keep using {@link #solve} to preserve the full 30 s budget.
     */
    public SchedulingResult solveForPreview(
            List<Staff> staffList,
            LocalDate startDate,
            LocalDate endDate,
            List<ShiftRequirementInfo> requirements,
            Set<String> existingCompensationDays,
            List<LeaveRequest> leaveRequests,
            Set<Integer> excludedStaffIds,
            List<String> l04AllowedSpecialties) {
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

        ProblemData data = dataBuilder.build(activeStaff, dates, requirements, leaveRequests, l04AllowedSpecialties);
        CspSearchEngine.Result solution = searchEngine.solve(data, startTime, 8_000L);
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

    /**
     * Overload that threads L04 allowed specialties into the incremental
     * fallback full re-solve (so the eligibility used during a rebuild
     * matches what was used during the original batch solve).
     */
    public SchedulingResult reSolve(
            SchedulingResult previousResult,
            ScheduleChange deltaChanges,
            List<Staff> staffList,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests,
            List<String> l04AllowedSpecialties) {
        return incrementalResolver.reSolve(previousResult, deltaChanges, staffList, requirements,
                leaveRequests, l04AllowedSpecialties);
    }
}
