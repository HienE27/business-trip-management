package com.hospital.scheduler.scheduling;

import com.hospital.scheduler.algorithm.ShiftRequirementInfo;
import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.scheduling.config.ConfigDefaults;
import com.hospital.scheduler.scheduling.config.ConfigService;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.constraint.AdjacentL01Constraint;
import com.hospital.scheduler.scheduling.constraint.CompensationDayConstraint;
import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.constraint.DuplicateShiftConstraint;
import com.hospital.scheduler.scheduling.constraint.ShiftConflictConstraint;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.search.CompositeTermination;
import com.hospital.scheduler.scheduling.search.LocalSearchAlgorithm;
import com.hospital.scheduler.scheduling.search.SampledMoveSelector;
import com.hospital.scheduler.scheduling.search.SearchDirector;
import com.hospital.scheduler.scheduling.search.TabuAcceptor;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BUGFIX (M08-PRIORITY-V10 + M08-COMPDAY-V10): the search used to unassign
 * L01/L02 slots to "spend" staff on L04 (preview collapsed L01=15 / L02=10 vs
 * ~145 demanded) and never enforced compensation days earned by L01 slots
 * placed during the run. These tests pin the spec behavior from
 * QuanLyLichCongTac_v5.md: M07-B3 priority order (L01→L02→L03→L04), M07-F01/F02
 * even distribution, and 1.4 no-shift-on-comp-day.
 *
 * <p>Counts are read from the lossless {@link WorkingSolution} (not the
 * {@link com.hospital.scheduler.algorithm.SchedulingResult} assignments map,
 * whose "staff|date" key cannot hold two shift types on the same day).
 */
class LocalSearchSchedulerBalanceTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1); // Tuesday

    private SchedulingConfig searchConfig() {
        SchedulingConfig c = new SchedulingConfig();
        c.getSearch().setMaxIterations(300);
        c.getSearch().setMaxNoImprove(80);
        return c;
    }

    private CompensationDateCalculator realCompCalc() {
        HolidayRepository holidays = mock(HolidayRepository.class);
        when(holidays.findAll()).thenReturn(List.<Holiday>of());
        return new CompensationDateCalculator(holidays);
    }

    private List<Staff> twentyStaff() {
        List<Staff> staff = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            Staff s = new Staff();
            s.setId(i);
            s.setFullName("NV" + i);
            s.setIsActive(true);
            staff.add(s);
        }
        return staff;
    }

    private List<ShiftRequirementInfo> tenDayDemand() {
        List<ShiftRequirementInfo> reqs = new ArrayList<>();
        for (LocalDate d = START; !d.isAfter(START.plusDays(9)); d = d.plusDays(1)) {
            reqs.add(new ShiftRequirementInfo("L01", d, 5));  // demand 50
            reqs.add(new ShiftRequirementInfo("L02", d, 5));  // demand 50
            reqs.add(new ShiftRequirementInfo("L03", d, 4));  // demand 40
            reqs.add(new ShiftRequirementInfo("L04", d, 10)); // demand 100
        }
        return reqs;
    }

    /** Mirror of LocalSearchScheduler.solve() problem-building (comp day map included). */
    private SchedulingProblem buildProblem(SchedulingConfig config,
                                           List<ShiftRequirementInfo> reqs,
                                           CompensationDateCalculator compCalc) {
        List<com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo> v10Reqs = new ArrayList<>();
        int seq = 1;
        for (ShiftRequirementInfo sr : reqs) {
            for (int k = 0; k < Math.max(1, sr.requiredCount()); k++) {
                v10Reqs.add(new com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo(
                        seq++, sr.workDate(), sr.shiftTypeId(), sr.specialtyId(), 1));
            }
        }
        Map<LocalDate, LocalDate> compDayOf = new HashMap<>();
        for (com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo r : v10Reqs) {
            if ("L01".equals(r.shiftTypeId())) {
                LocalDate c = compCalc.calculate(r.date());
                if (c != null) compDayOf.putIfAbsent(r.date(), c);
            }
        }
        return SchedulingProblem.withRequirementsAndCompDayMap(
                twentyStaff(), v10Reqs, List.of(), new HashMap<>(),
                compDayOf, java.util.Set.of(), config);
    }

    private static int countType(WorkingSolution sol, String type) {
        int n = 0;
        for (MutableAssignment a : sol.getAssignments()) {
            if (a.staffId > 0 && type.equals(a.shiftTypeId)) n++;
        }
        return n;
    }

    private static int hardViolations(WorkingSolution sol) {
        ConstraintRegistry reg = new ConstraintRegistry();
        reg.register(new ShiftConflictConstraint());
        reg.register(new DuplicateShiftConstraint());
        reg.register(new AdjacentL01Constraint());
        reg.register(new CompensationDayConstraint());
        int v = 0;
        for (var c : reg.all()) v += c.evaluate(sol).hardDelta();
        return v;
    }

    /** Per-type (max−min) gap for one shift type across all staff. */
    private static int typeGap(WorkingSolution sol, String type) {
        int min = Integer.MAX_VALUE, max = 0;
        for (var s : sol.getDescriptor().getProblem().getStaffList()) {
            int c = sol.getShiftCountOfType(s.getId(), type);
            if (c < min) min = c;
            if (c > max) max = c;
        }
        return max - min;
    }

    private LocalSearchScheduler scheduler(SchedulingConfig config, CompensationDateCalculator compCalc) {
        return new LocalSearchScheduler(config,
                mock(HolidayRepository.class), compCalc,
                mock(ConfigService.class));
    }

    @Test
    void greedy_stagedPriority_fillsL01L02L03BeforeL04_andNeverViolatesHardRules() {
        SchedulingConfig config = searchConfig();
        CompensationDateCalculator compCalc = realCompCalc();
        SchedulingProblem problem = buildProblem(config, tenDayDemand(), compCalc);
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);

        WorkingSolution sol = scheduler(config, compCalc)
                .buildInitialSolution(problem, descriptor, config);

        // M07-B3 priority: L01/L02 saturated before L04 consumes staff.
        // L02 < 50 allowed — e.g. Sep 8 leaves only L01-duty staff free after
        // 15 staff sit out on compensation days (trực T6/T7/T2 tuần trước) —
        // but it must be near demand, not starved (the pre-fix collapse hit 26).
        assertEquals(50, countType(sol, "L01"), "all L01 slots must be filled first");
        assertTrue(countType(sol, "L02") >= 40, "L02 must stay near demand; got " + countType(sol, "L02"));
        assertTrue(countType(sol, "L03") >= 30, "L03 mostly filled");
        assertTrue(countType(sol, "L04") > 0, "L04 gets residual capacity");
        assertEquals(0, hardViolations(sol),
                "greedy must be hard-free incl. derived comp days");
        // M07-F01/F02: per-type spread stays even (50 L01 / 20 staff → 2-3 each).
        assertTrue(typeGap(sol, "L01") <= 2, "L01 per-staff spread must be even; gap=" + typeGap(sol, "L01"));
        assertTrue(typeGap(sol, "L02") <= 2, "L02 per-staff spread must be even; gap=" + typeGap(sol, "L02"));
        assertTrue(typeGap(sol, "L03") <= 3, "L03 per-staff spread must be even; gap=" + typeGap(sol, "L03"));
    }

    @Test
    void search_keepsPriorityTypes_filled_andDerivedCompDaysRespected() {
        SchedulingConfig config = searchConfig();
        CompensationDateCalculator compCalc = realCompCalc();
        SchedulingProblem problem = buildProblem(config, tenDayDemand(), compCalc);
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);

        ConstraintRegistry registry = new ConstraintRegistry();
        registry.register(new ShiftConflictConstraint());
        registry.register(new DuplicateShiftConstraint());
        registry.register(new AdjacentL01Constraint());
        registry.register(new CompensationDayConstraint());

        ScoreDirector scoreDirector = new ScoreDirector(descriptor);
        SearchDirector searchDirector = new SearchDirector(scoreDirector, hub);
        SampledMoveSelector selector = new SampledMoveSelector(descriptor, config);
        TabuAcceptor acceptor = new TabuAcceptor(config);
        CompositeTermination termination = new CompositeTermination(config);
        LocalSearchAlgorithm algo = new LocalSearchAlgorithm(
                config, selector, acceptor, termination, searchDirector,
                scoreDirector, registry, hub);

        WorkingSolution initial = scheduler(config, compCalc)
                .buildInitialSolution(problem, descriptor, config);
        WorkingSolution finalSol = algo.search(initial).getSolution();

        // The search may not unassign L01/L02/L03 — only L04 is churnable.
        assertTrue(countType(finalSol, "L01") >= 40, "L01 must stay near demand after search");
        assertTrue(countType(finalSol, "L02") >= 40, "L02 must stay near demand after search");
        assertTrue(countType(finalSol, "L03") >= 30, "L03 must stay near demand after search");
        assertTrue(countType(finalSol, "L04") > 0, "L04 keeps residual capacity");
        assertEquals(0, hardViolations(finalSol),
                "search result must be hard-free incl. derived comp days");
        // M08-BALANCE-V10: the search's mix tiebreak must not worsen the
        // per-staff L01/L02/L03 mix produced by the greedy.
        assertTrue(finalSol.mixDeviation() <= initial.mixDeviation() + 1e-9,
                "search must not worsen per-staff mix: initial=" + initial.mixDeviation()
                        + " final=" + finalSol.mixDeviation());
        // Per-type gap may wiggle ±1 (a move that tightens L02/L03 can let L01
        // drift) — hard constraints (adjacent L01, comp days) bound the headroom.
        assertTrue(typeGap(finalSol, "L01") <= 3, "L01 spread stays near-even; gap=" + typeGap(finalSol, "L01"));
    }
}
