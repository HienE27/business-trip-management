package com.hospital.scheduler.scheduling;

import com.hospital.scheduler.algorithm.SchedulingAlgorithm;
import com.hospital.scheduler.algorithm.SchedulingResult;
import com.hospital.scheduler.algorithm.ScheduleChange;
import com.hospital.scheduler.algorithm.ShiftRequirementInfo;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.domain.StaffNode;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfoMapper;
import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.search.*;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Local Search Scheduler - v10 implementation.
 * 
 * <p>This is a new scheduling algorithm that uses:
 * <ul>
 *   <li>Modular statistics tracking</li>
 *   <li>Plugin-based constraints</li>
 *   <li>Tabu search with iteration-based tenure</li>
 *   <li>Incremental score updates</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocalSearchScheduler implements SchedulingAlgorithm {

    private final SchedulingConfig config;

    @Override
    public String getName() {
        return "LocalSearch-Tabu";
    }

    @Override
    public String getDescription() {
        return "Local Search với Tabu Search - Incremental statistics, Plugin constraints";
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

        // 1. Filter active staff
        List<Staff> activeStaff = staffList.stream()
                .filter(Staff::getIsActive)
                .filter(s -> excludedStaffIds == null || !excludedStaffIds.contains(s.getId()))
                .toList();

        if (activeStaff.isEmpty()) {
            return SchedulingResult.builder()
                    .valid(false)
                    .errors(List.of("Không có nhân sự nào hoạt động"))
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }

        // 2. Build problem
        SchedulingProblem problem = SchedulingProblem.from(
                activeStaff, requirements, leaveRequests, existingCompensationDays, config);

        // 3. Create working solution
        WorkingSolution solution = WorkingSolution.fromProblem(problem, config);

        // 4. Generate initial solution
        solution.generateInitialSolution();

        // 5. Create components
        SolutionDescriptor descriptor = solution.getDescriptor();
        IncrementalStatisticsHub statisticsHub = solution.getStatistics();
        ConstraintRegistry constraintRegistry = ConstraintRegistry.createDefault();
        ScoreDirector scoreDirector = new ScoreDirector(constraintRegistry, statisticsHub, descriptor);

        // 6. Initialize score
        scoreDirector.initialize(solution);

        // 7. Create search components
        MoveSelector moveSelector = new SampledMoveSelector(config);
        TabuAcceptor tabuAcceptor = new TabuAcceptor(config);
        CompositeTermination termination = CompositeTermination.standard(config);
        SearchDirector searchDirector = new SearchDirector(scoreDirector, statisticsHub, null);

        LocalSearchAlgorithm searchAlgorithm = new LocalSearchAlgorithm(
                config, moveSelector, tabuAcceptor, termination, searchDirector);

        // 8. Run search
        LocalSearchAlgorithm.SearchResult result = searchAlgorithm.search(solution);

        // 9. Build result
        return buildSchedulingResult(result, solution, startTime);
    }

    @Override
    public boolean canReSolveIncrementally(ScheduleChange deltaChanges) {
        // Local search doesn't support incremental re-solve yet
        return false;
    }

    @Override
    public SchedulingResult reSolve(
            SchedulingResult previousResult,
            ScheduleChange deltaChanges,
            List<Staff> staffList,
            List<ShiftRequirementInfo> requirements,
            List<LeaveRequest> leaveRequests) {
        // Full re-solve for now
        return solve(staffList, null, null, requirements, null, leaveRequests, null);
    }

    /**
     * Preview solve with shorter timeout.
     */
    public SchedulingResult solveForPreview(
            List<Staff> staffList,
            LocalDate startDate,
            LocalDate endDate,
            List<ShiftRequirementInfo> requirements,
            Set<String> existingCompensationDays,
            List<LeaveRequest> leaveRequests,
            Set<Integer> excludedStaffIds) {

        // Use shorter time limit for preview
        long originalLimit = config.getSearch().getTimeLimitSeconds();
        try {
            config.getSearch().setTimeLimitSeconds(15);
            return solve(staffList, startDate, endDate, requirements,
                    existingCompensationDays, leaveRequests, excludedStaffIds);
        } finally {
            config.getSearch().setTimeLimitSeconds(originalLimit);
        }
    }

    private SchedulingResult buildSchedulingResult(
            LocalSearchAlgorithm.SearchResult result,
            WorkingSolution solution,
            long startTime) {

        long executionTime = System.currentTimeMillis() - startTime;

        var snapshot = result.bestSnapshot();
        if (snapshot == null) {
            return SchedulingResult.builder()
                    .valid(false)
                    .errors(List.of("Không tìm được lời giải"))
                    .executionTimeMs(executionTime)
                    .build();
        }

        // Convert assignments to result format
        var assignments = solution.toImmutableAssignments();
        Map<String, Integer> assignmentsMap = new java.util.HashMap<>();
        for (var a : assignments) {
            String key = a.getStaffId() + "_" + a.getDate().toString();
            assignmentsMap.put(key, a.getStaffId());
        }

        return SchedulingResult.builder()
                .valid(true)
                .assignments(assignmentsMap)
                .compensationDays(java.util.Collections.emptySet())
                .totalScore(snapshot.totalScore())
                .coverageScore(snapshot.coverage())
                .fairnessScore(snapshot.cvTotal() > 0 ? (100 - snapshot.cvTotal() * 100) : 100)
                .constraintScore(100 - snapshot.hardViolations() * 25)
                .unassignedCount(solution.getUnassignedCount())
                .executionTimeMs(executionTime)
                .build();
    }
}
