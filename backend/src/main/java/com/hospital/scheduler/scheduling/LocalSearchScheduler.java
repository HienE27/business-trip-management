package com.hospital.scheduler.scheduling;

import com.hospital.scheduler.algorithm.ScheduleChange;
import com.hospital.scheduler.algorithm.SchedulingAlgorithm;
import com.hospital.scheduler.algorithm.SchedulingResult;
import com.hospital.scheduler.algorithm.ShiftRequirementInfo;
import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.scheduling.config.ConfigService;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
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
     * BUGFIX (M08-DBCONFIG-V10): DB-backed config source. The static
     * {@link SchedulingConfig} bean only carries application.properties/Java
     * defaults, while the UI writes {@code scheduling_*} params to the
     * algorithm_config table via this service — without reloading here, every
     * UI config edit silently had no effect on the search.
     */
    private final ConfigService configService;

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

        // BUGFIX (M08-DBCONFIG-V10): rebuild config from DB per solve so UI
        // edits to scheduling_* params take effect on the next run.
        SchedulingConfig effectiveConfig = loadEffectiveConfig();

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

        // BUGFIX (M08-COMPDAY-V10): precompute duty-date → compensation-date
        // once per solve (unique dates only, holiday-adjusted via the shared
        // calculator) so the search can derive comp days from L01 slots placed
        // during THIS run and enforce BR-03 while searching — doc 1.4: no
        // shift of any kind on a comp day.
        Map<LocalDate, LocalDate> compDayOfDutyDate = new HashMap<>();
        for (com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo r : v10Reqs) {
            if ("L01".equals(r.shiftTypeId()) && r.date() != null) {
                LocalDate comp = compensationDateCalculator.calculate(r.date());
                if (comp != null) {
                    compDayOfDutyDate.putIfAbsent(r.date(), comp);
                }
            }
        }

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

        SchedulingProblem problem = SchedulingProblem.withRequirementsAndCompDayMap(
                activeStaff,
                v10Reqs,
                leaveRequests,
                compDaysByStaff,
                compDayOfDutyDate,
                holidays,
                effectiveConfig);

        // ── 2. Build SolutionDescriptor + StatisticsHub ───────────────────────
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);

        // ── 3. Register constraints ───────────────────────────────────────────
        // BUGFIX (V10-HARDCAP): wire runtime max_shifts_per_staff as the global
        // HARD cap so V10 honors the same ceiling as Greedy
        // (AutoSchedulingService.filterAndSortEligibleStaffBatch). 0 = disabled.
        int globalMaxShiftsCap = loadGlobalMaxShiftsCap();
        ConstraintRegistry registry = new ConstraintRegistry();
        registry.register(new ShiftConflictConstraint());
        registry.register(new LeaveConflictConstraint());
        registry.register(new DuplicateShiftConstraint());
        registry.register(new RestDayConstraint());
        registry.register(new AdjacentL01Constraint());
        registry.register(new MaxShiftsConstraint(globalMaxShiftsCap));
        registry.register(new CompensationDayConstraint());

        // ── 4. Wire director + algorithm ──────────────────────────────────────
        ScoreDirector scoreDirector = new ScoreDirector(descriptor);
        SearchDirector searchDirector = new SearchDirector(scoreDirector, hub);
        SampledMoveSelector selector = new SampledMoveSelector(descriptor, effectiveConfig);
        TabuAcceptor acceptor = new TabuAcceptor(effectiveConfig);
        CompositeTermination termination = new CompositeTermination(effectiveConfig);

        LocalSearchAlgorithm algo = new LocalSearchAlgorithm(
                effectiveConfig, selector, acceptor, termination, searchDirector,
                scoreDirector, registry, hub);

        // ── 5. Build initial solution (round-robin greedy for unassigned slots) ──
        WorkingSolution initial = buildInitialSolution(problem, descriptor, effectiveConfig, globalMaxShiftsCap);

        // ── 6. Run search ─────────────────────────────────────────────────────
        LocalSearchAlgorithm.SearchResult result = algo.search(initial);

        // ── 7. Convert result back to SchedulingResult ────────────────────────
        return toSchedulingResult(result, staffList);
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
     *
     * <p>BUGFIX (M08-EXPAND-V10): each requirement demands {@code requiredCount}
     * staff (3-5 per shift), but the search model is 1 slot = 1 staff. Expand
     * each requirement into that many slots so the search targets the real
     * demand instead of capping at 1 staff per requirement.
     */
    private List<com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo> convertRequirements(
            List<ShiftRequirementInfo> src) {
        java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger(1);
        List<com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo> out = new java.util.ArrayList<>();
        for (ShiftRequirementInfo sr : src) {
            int need = Math.max(1, sr.requiredCount());
            for (int k = 0; k < need; k++) {
                out.add(new com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo(
                        seq.getAndIncrement(),   // synthetic slot id, unique per expanded slot
                        sr.workDate(),
                        sr.shiftTypeId(),
                        sr.specialtyId(),
                        1));                     // each expanded slot needs exactly 1 staff
            }
        }
        return out;
    }

    /**
     * Build the initial solution in STAGES, in the priority order required by
     * the spec (QuanLyLichCongTac_v5.md, M07-B3): 24/24 duty (L01) →
     * thong-tam (L02) → phong-kham-dich-vu (L03) → phong-kham-chuyen-gia (L04).
     *
     * <p>Each stage assigns its slots to the eligible staff with the FEWEST
     * shifts of that same type first (M07-F02: "số ngày trực đều nhau"), tie-
     * broken by lowest total load (M07-F01: "phân bổ đều số ngày cho 20 nhân
     * sự"), skipping anyone who would introduce a hard violation.
     *
     * <p>BUGFIX (M08-COMPDAY-V10): the old single-pass greedy processed slots
     * in list order and the search then unassigned high-priority types to make
     * room for L04 — the preview showed L01=15 / L02=10 vs ~145 demanded.
     * Staging by type priority guarantees L01/L02/L03 are saturated before L04
     * consumes any staff, and L01 comp days derived during stage 1 block the
     * same staff in later stages.
     */
    /** Backward-compatible: no global cap (per-staff entity caps only). */
    WorkingSolution buildInitialSolution(SchedulingProblem problem,
                                          SolutionDescriptor descriptor,
                                          SchedulingConfig effectiveConfig) {
        return buildInitialSolution(problem, descriptor, effectiveConfig, 0);
    }

    WorkingSolution buildInitialSolution(SchedulingProblem problem,
                                          SolutionDescriptor descriptor,
                                          SchedulingConfig effectiveConfig,
                                          int globalMaxShiftsCap) {
        WorkingSolution sol = WorkingSolution.fromProblem(effectiveConfig, descriptor);
        // Effective cap per staff: runtime global cap wins, else per-staff entity cap.
        java.util.Map<Integer, Integer> capByStaffId = new java.util.HashMap<>();
        for (var s : problem.getStaffList()) {
            Integer entityCap = s.getMaxShiftsPerMonth();
            capByStaffId.put(s.getId(), globalMaxShiftsCap > 0 ? globalMaxShiftsCap
                    : (entityCap != null && entityCap > 0 ? entityCap : Integer.MAX_VALUE));
        }
        for (String stageType : List.of("L01", "L02", "L03", "L04")) {
            // MRV order (L01 stage only): process slots with FEWEST eligible staff
            // first so constrained days (leave/comp-day/adjacency collisions) are
            // filled before flexible ones. Without it, easy days soak up staff and
            // hard days concentrate the remainder on the same few people → per-type
            // spread widens (L01 3-6 instead of 3-5). L02-L04 stay in list order:
            // reordering them shifts the adaptive-L04 phase's open-day availability
            // and costs coverage (measured 98.5→96.1 with full-MRV).
            // Ordering change only — constraints untouched; a slot is never left
            // empty while an eligible staff exists.
            var stageReqs = ("L01".equals(stageType))
                    ? problem.getRequirements().stream()
                        .filter(r -> stageType.equals(r.shiftTypeId()))
                        .sorted(java.util.Comparator.comparingInt(r -> problem.getEligibleStaff(r.id()).size()))
                        .toList()
                    : problem.getRequirements().stream()
                        .filter(r -> stageType.equals(r.shiftTypeId()))
                        .toList();
            for (com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo req : stageReqs) {
                List<Integer> eligible = problem.getEligibleStaff(req.id());
                if (eligible.isEmpty()) continue;
                // L04 strict-specialty-only: getEligibleStaff đã lọc đúng chuyên khoa
                // (cross-specialty đã bị thay thế bằng "đổi ngày mở thích ứng").
                int bestStaff = -1;
                int bestTypeCount = Integer.MAX_VALUE;
                int bestTotal = Integer.MAX_VALUE;
                for (int staffId : eligible) {
                    if (sol.getShiftCount(staffId)
                            >= capByStaffId.getOrDefault(staffId, Integer.MAX_VALUE)) continue;
                    if (wouldViolateHard(sol, staffId, req.date(), req.shiftTypeId())) continue;
                    if (sol.isOnDerivedCompDay(staffId, req.date())) continue;
                    int typeCount = sol.getShiftCountOfType(staffId, stageType);
                    int total = sol.getShiftCount(staffId);
                    if (typeCount < bestTypeCount
                            || (typeCount == bestTypeCount && total < bestTotal)) {
                        bestTypeCount = typeCount;
                        bestTotal = total;
                        bestStaff = staffId;
                    }
                }
                if (bestStaff > 0) {
                    sol.assign(req.id(), bestStaff);
                }
            }
        }
        return sol;
    }

    /**
     * True if assigning {@code shiftType} on {@code date} to {@code staffId}
     * would break a HARD rule given the assignments already in {@code sol}:
     * duplicate shift, L01↔L02 / L03↔L04 same day, adjacent L01, or a derived
     * compensation day (L01 placed earlier in this run blocks the comp date).
     */
    private boolean wouldViolateHard(WorkingSolution sol, int staffId,
                                     java.time.LocalDate date, String shiftType) {
        if (sol.isOnDerivedCompDay(staffId, date)) return true;
        for (int slotId : sol.getSlotsAssignedTo(staffId)) {
            var a = sol.getAssignment(slotId);
            if (a == null || a.staffId <= 0 || a.date == null) continue;
            if (a.date.equals(date)) {
                if (a.shiftTypeId.equals(shiftType)) return true;       // duplicate shift
                if (isHardConflictPair(a.shiftTypeId, shiftType)) return true; // L01↔L02, L03↔L04
            }
            if ("L01".equals(shiftType) && "L01".equals(a.shiftTypeId)
                    && (a.date.equals(date.minusDays(1)) || a.date.equals(date.plusDays(1)))) {
                return true;                                             // adjacent L01
            }
        }
        return false;
    }

    private static boolean isHardConflictPair(String a, String b) {
        return ("L01".equals(a) && "L02".equals(b)) || ("L01".equals(b) && "L02".equals(a))
                || ("L03".equals(a) && "L04".equals(b)) || ("L03".equals(b) && "L04".equals(a));
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
                    // BUGFIX (M08-DISPLAY-V10): append shiftTypeId to the key —
                    // the old "staffId|date" key silently dropped one shift when
                    // the same staff legitimately holds two types the same day
                    // (L01+L04, L02+L04 are allowed pairs), so previews showed
                    // L01=15 / L02=10 instead of the ~145 the search produced.
                    assignments.put(a.staffId + "|" + a.date + "|" + a.shiftTypeId, a.shiftTypeId);
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
     * BUGFIX (M08-DBCONFIG-V10): build the effective {@link SchedulingConfig}
     * from the DB-backed {@link ConfigService} so UI edits to {@code scheduling_*}
     * params take effect on the next solve. Falls back to the static bean
     * (application.properties/Java defaults) when the DB is unavailable.
     */
    private SchedulingConfig loadEffectiveConfig() {
        try {
            return SchedulingConfig.from(configService.load());
        } catch (Exception ex) {
            log.warn("Failed to load scheduling config from DB; falling back to static defaults: {}",
                    ex.getMessage());
            return config;
        }
    }

    /**
     * BUGFIX (V10-HARDCAP): read runtime {@code max_shifts_per_staff} from the
     * DB-backed config. {@link SchedulingConfig} has no slot for it, so read the
     * {@code ConfigDomain} directly. 0 = no global cap (fall back to per-staff
     * entity caps, enforced softly by {@link MaxShiftsConstraint}).
     */
    private int loadGlobalMaxShiftsCap() {
        try {
            return Math.max(0, configService.load().maxShiftsPerStaff());
        } catch (Exception ex) {
            log.warn("Failed to load maxShiftsPerStaff from DB; global cap disabled: {}",
                    ex.getMessage());
            return 0;
        }
    }
}