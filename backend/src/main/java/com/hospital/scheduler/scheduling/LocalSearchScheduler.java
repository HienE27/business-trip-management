package com.hospital.scheduler.scheduling;

import com.hospital.scheduler.algorithm.ScheduleChange;
import com.hospital.scheduler.algorithm.SchedulingAlgorithm;
import com.hospital.scheduler.algorithm.SchedulingResult;
import com.hospital.scheduler.algorithm.ShiftRequirementInfo;
import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import com.hospital.scheduler.scheduling.constraint.AdjacentL01Constraint;
import com.hospital.scheduler.scheduling.constraint.CompensationDayConstraint;
import com.hospital.scheduler.scheduling.constraint.Constraint;
import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.constraint.DuplicateShiftConstraint;
import com.hospital.scheduler.scheduling.constraint.LeaveConflictConstraint;
import com.hospital.scheduler.scheduling.constraint.MaxShiftsConstraint;
import com.hospital.scheduler.scheduling.constraint.RestDayConstraint;
import com.hospital.scheduler.scheduling.constraint.ShiftConflictConstraint;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.search.CompositeTermination;
import com.hospital.scheduler.scheduling.search.LocalSearchAlgorithm;
import com.hospital.scheduler.scheduling.search.SampledMoveSelector;
import com.hospital.scheduler.scheduling.search.SearchDirector;
import com.hospital.scheduler.scheduling.search.TabuAcceptor;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * v10 entry point. Implements {@link SchedulingAlgorithm} so it can drop
 * into {@code AutoSchedulingService} alongside {@code CSPScheduler} as an
 * alternative strategy.
 *
 * <p>Builds the {@link SchedulingProblem} from inputs, wires up the
 * constraint registry + statistics hub + score director, then runs
 * {@link LocalSearchAlgorithm} and converts the resulting
 * {@link WorkingSolution} back into a {@link SchedulingResult}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalSearchScheduler implements SchedulingAlgorithm {

    private final SchedulingConfig config;
    private final HolidayRepository holidayRepository;
    private final CompensationDateCalculator compensationDateCalculator;
    /**
     * BUGFIX (M07-CROSSCONFIG-V10): injected so the V10 problem can read the
     * user's L04 cross-specialty toggle and apply it during candidate
     * generation. Without this, the V10 search would happily assign
     * non-matching-specialty staff to L04 slots and surface a non-zero
     * "Cross L04" KPI even when the toggle is OFF.
     */
    private final AlgorithmConfigService algorithmConfigService;

    @Override
    public SchedulingResult solve(List<Staff> staffList,
                                   LocalDate startDate,
                                   LocalDate endDate,
                                   List<ShiftRequirementInfo> requirements,
                                   Set<String> existingCompensationDays,
                                   List<LeaveRequest> leaveRequests,
                                   Set<Integer> excludedStaffIds) {
        log.info("v10 LocalSearchScheduler.solve called: {} staff, {} requirements",
                staffList.size(), requirements.size());

        // ── 1. Build SchedulingProblem ────────────────────────────────────────
        List<Staff> activeStaff = staffList.stream()
                .filter(s -> !excludedStaffIds.contains(s.getId()))
                .toList();

        Set<LocalDate> holidays = holidayRepository != null
                ? holidayRepository.findAll().stream()
                    .map(Holiday::getHolidayDate)
                    .collect(Collectors.toSet())
                : new HashSet<>();

        // The entity-package ShiftRequirementInfo carries the actual scheduling
        // requirements; convert them to the v10 record format used by the
        // search layer.
        List<com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo> v10Reqs =
                convertRequirements(requirements);

        // Convert flat "staffId_date" strings to per-staff date sets
        Map<Integer, Set<LocalDate>> compDaysByStaff = new HashMap<>();
        if (existingCompensationDays != null) {
            for (String key : existingCompensationDays) {
                String[] parts = key.split("_");
                if (parts.length < 2) continue;
                try {
                    int staffId = Integer.parseInt(parts[0]);
                    LocalDate compDate = LocalDate.parse(parts[1]);
                    compDaysByStaff.computeIfAbsent(staffId, k -> new HashSet<>()).add(compDate);
                } catch (Exception e) {
                    log.warn("Skipping malformed existingCompDay key: {}", key);
                }
            }
        }

        SchedulingProblem problem = SchedulingProblem.withRequirements(
                activeStaff,
                v10Reqs,
                leaveRequests,
                compDaysByStaff,
                holidays,
                config,
                isL04CrossSpecialtyEnabled());

        // ── 2. Build SolutionDescriptor + StatisticsHub ───────────────────────
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);

        // ── 3. Register constraints ───────────────────────────────────────────
        ConstraintRegistry registry = new ConstraintRegistry();
        registry.register(new ShiftConflictConstraint());
        registry.register(new LeaveConflictConstraint());
        registry.register(new DuplicateShiftConstraint());
        registry.register(new RestDayConstraint());
        registry.register(new AdjacentL01Constraint());
        registry.register(new MaxShiftsConstraint());
        registry.register(new CompensationDayConstraint());

        // ── 4. Wire director + algorithm ──────────────────────────────────────
        ScoreDirector scoreDirector = new ScoreDirector(descriptor);
        SearchDirector searchDirector = new SearchDirector(scoreDirector, hub);
        SampledMoveSelector selector = new SampledMoveSelector(descriptor, config);
        TabuAcceptor acceptor = new TabuAcceptor(config);
        CompositeTermination termination = new CompositeTermination(config);

        LocalSearchAlgorithm algo = new LocalSearchAlgorithm(
                config, selector, acceptor, termination, searchDirector,
                scoreDirector, registry, hub);

        // ── 5. Build initial solution (round-robin greedy for unassigned slots) ──
        WorkingSolution initial = buildInitialSolution(problem, descriptor);

        // ── 6. Run search ─────────────────────────────────────────────────────
        LocalSearchAlgorithm.SearchResult result = algo.search(initial);

        // ── 7. Convert result back to SchedulingResult ────────────────────────
        return toSchedulingResult(result, staffList);
    }

    @Override
    public SchedulingResult solve(List<Staff> staffList,
                                   LocalDate startDate,
                                   LocalDate endDate,
                                   List<ShiftRequirementInfo> requirements,
                                   Set<String> existingCompensationDays,
                                   List<LeaveRequest> leaveRequests,
                                   Set<Integer> excludedStaffIds,
                                   List<String> l04AllowedSpecialties) {
        // L04 override not honored at v10 layer yet — fall through to default.
        return solve(staffList, startDate, endDate, requirements,
                existingCompensationDays, leaveRequests, excludedStaffIds);
    }

    @Override
    public SchedulingResult reSolve(SchedulingResult previousResult,
                                     ScheduleChange deltaChanges,
                                     List<Staff> staffList,
                                     List<ShiftRequirementInfo> requirements,
                                     List<LeaveRequest> leaveRequests) {
        // Incremental re-solve is not yet supported at v10 layer — fall back to full solve.
        log.warn("v10 LocalSearchScheduler.reSolve not implemented — falling back to full solve");
        return solve(staffList, null, null, requirements,
                new HashSet<>(), leaveRequests, new HashSet<>());
    }

    @Override
    public boolean canReSolveIncrementally(ScheduleChange deltaChanges) {
        return false; // v10 layer always falls back to full solve
    }

    @Override
    public String getName() {
        return "v10-LocalSearch";
    }

    @Override
    public String getDescription() {
        return "v10 local-search scheduler with tabu, sampled neighborhood, "
                + "incremental statistics, and pluggable constraints (BR-01..07).";
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Convert algorithm-package {@link com.hospital.scheduler.algorithm.ShiftRequirementInfo}
     * to v10-package equivalent.
     */
    private List<com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo> convertRequirements(
            List<ShiftRequirementInfo> src) {
        java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger(1);
        return src.stream().map(sr ->
                new com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo(
                        seq.getAndIncrement(),   // v10 needs an int id; assign synthetic
                        sr.workDate(),
                        sr.shiftTypeId(),
                        sr.specialtyId(),
                        Math.max(1, sr.requiredCount())))
                .collect(Collectors.toList());
    }

    /**
     * Build the initial solution: for each unassigned slot, assign the
     * round-robin least-loaded eligible staff. Round-robin seed keeps
     * fairness initial state simple.
     */
    private WorkingSolution buildInitialSolution(SchedulingProblem problem, SolutionDescriptor descriptor) {
        WorkingSolution sol = WorkingSolution.fromProblem(config, descriptor);
        int nextStaffIdx = 0;
        int staffCount = problem.getStaffList().size();
        for (com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo req
                : problem.getRequirements()) {
            List<Integer> eligible = problem.getEligibleStaff(req.id());
            if (eligible.isEmpty()) continue;
            // Pick the least-loaded eligible staff deterministically
            int bestIdx = -1;
            int bestLoad = Integer.MAX_VALUE;
            // Try a few candidates starting from nextStaffIdx
            for (int i = 0; i < Math.min(eligible.size(), 3); i++) {
                int idx = (nextStaffIdx + i) % eligible.size();
                int staffId = eligible.get(idx);
                int load = sol.getShiftCount(staffId);
                if (load < bestLoad) {
                    bestLoad = load;
                    bestIdx = staffId;
                }
            }
            if (bestIdx > 0) {
                sol.assign(req.id(), bestIdx);
                nextStaffIdx = (nextStaffIdx + 1) % Math.max(staffCount, 1);
            }
        }
        return sol;
    }

    /** Convert {@link LocalSearchAlgorithm.SearchResult} to {@link SchedulingResult}. */
    private SchedulingResult toSchedulingResult(LocalSearchAlgorithm.SearchResult src,
                                                  List<Staff> staffList) {
        SchedulingResult out = new SchedulingResult();
        Map<String, String> assignments = new HashMap<>();
        int scheduleCount = 0;
        if (src.getSolution() != null) {
            for (var a : src.getSolution().getAssignments()) {
                if (a.staffId > 0) {
                    // BUGFIX (V25 #3): use "|" separator so split("\\|") in
                    // runCspWithResult correctly yields [staffId, date, shiftTypeId].
                    // underscore split would break ISO dates (2026-07-01 → 4 parts).
                    assignments.put(a.staffId + "|" + a.date, a.shiftTypeId);
                    scheduleCount++;
                }
            }
        }
        out.setAssignments(assignments);

        // Calculate compensation days from L01 assignments
        Set<String> compDays = new HashSet<>();
        if (src.getSolution() != null) {
            for (var a : src.getSolution().getAssignments()) {
                if (a.staffId > 0 && "L01".equals(a.shiftTypeId)) {
                    LocalDate compDate = compensationDateCalculator.calculate(a.date);
                    if (compDate != null) {
                        compDays.add(a.staffId + "_" + compDate);
                    }
                }
            }
        }
        out.setCompensationDays(compDays);
        out.setErrors(new java.util.ArrayList<>());
        out.setValid(src.getScore() != null && src.getScore().getHardViolations() == 0);
        out.setPartial(src.getScore() == null || src.getScore().getCoverage() < 0.999);
        out.setScheduleCount(scheduleCount);
        out.setExecutionTimeMs(src.getElapsedMillis());
        if (src.getScore() != null) {
            out.setCoverageScore(java.math.BigDecimal.valueOf(src.getScore().getCoverage())
                    .multiply(java.math.BigDecimal.valueOf(100)));
            out.setFairnessScore(java.math.BigDecimal.valueOf(
                    Math.max(0, 1.0 - src.getScore().getGini())
                    * 100));
        } else {
            out.setCoverageScore(java.math.BigDecimal.ZERO);
            out.setFairnessScore(java.math.BigDecimal.ZERO);
        }
        return out;
    }

    /**
     * BUGFIX (M07-CROSSCONFIG-V10): read the user's L04 cross-specialty toggle
     * from {@link AlgorithmConfigService}. Defaults to {@code true} when the
     * config is unavailable so legacy callers retain the prior (cross-allowed)
     * behavior — flipping the fix to opt-in rather than opt-out.
     */
    private boolean isL04CrossSpecialtyEnabled() {
        try {
            return algorithmConfigService.getAutoGenConfig()
                    .map(cfg -> cfg.l04CrossSpecialty())
                    .orElse(true);
        } catch (Exception ex) {
            log.warn("Failed to read l04CrossSpecialty config for V10 problem; defaulting to enabled: {}",
                    ex.getMessage());
            return true;
        }
    }
}