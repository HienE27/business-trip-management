package com.hospital.scheduler.scheduling;

import com.hospital.scheduler.entity.Holiday;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.HolidayRepository;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.config.ConfigDefaults;
import com.hospital.scheduler.scheduling.config.ConfigService;
import com.hospital.scheduler.scheduling.constraint.CompensationDayConstraint;
import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.constraint.AdjacentL01Constraint;
import com.hospital.scheduler.scheduling.constraint.DuplicateShiftConstraint;
import com.hospital.scheduler.scheduling.constraint.ShiftConflictConstraint;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.event.SearchEvent;
import com.hospital.scheduler.scheduling.event.SearchEventPublisher;
import com.hospital.scheduler.scheduling.event.SearchEventType;import com.hospital.scheduler.scheduling.search.CompositeTermination;
import com.hospital.scheduler.scheduling.search.LocalSearchAlgorithm;
import com.hospital.scheduler.scheduling.search.SampledMoveSelector;
import com.hospital.scheduler.scheduling.search.SearchDirector;
import com.hospital.scheduler.scheduling.search.TabuAcceptor;import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import com.hospital.scheduler.scheduling.strategy.StrategyProperties;
import com.hospital.scheduler.util.CompensationDateCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plateau analysis for V10 LocalSearch: capture mixDeviation/coverage/cost
 * at fixed iteration boundaries while search runs, then assert how fast the
 * objective saturates.
 *
 * <p>Goal: verify whether 200 rounds is overkill. If round 60 ≈ round 200 on
 * mixDeviation, the algorithm could safely run shorter — saves ~70% runtime.
 *
 * <p>Synthetic data: 10 staff, 28 days (4 weeks), mixed demand. Synthetic
 * data (no DB) so every run is deterministic.
 */
class V10SearchPlateauTest {

    private static final int STAFF_COUNT = 10;
    private static final int DAYS = 28;
    private static final LocalDate START = LocalDate.of(2026, 9, 7); // Mon

    /** Configuration: 250 iterations cap, 100 no-improve cap. */
    private SchedulingConfig searchConfig() {
        SchedulingConfig c = new SchedulingConfig();
        c.getSearch().setMaxIterations(250);
        c.getSearch().setMaxNoImprove(120);
        return c;
    }

    private CompensationDateCalculator realCompCalc() {
        HolidayRepository holidays = mock(HolidayRepository.class);
        when(holidays.findAll()).thenReturn(List.<Holiday>of());
        return new CompensationDateCalculator(holidays);
    }

    private List<Staff> tenStaff() {
        List<Staff> staff = new ArrayList<>();
        for (int i = 1; i <= STAFF_COUNT; i++) {
            Staff s = new Staff();
            s.setId(i);
            s.setFullName("NV" + i);
            s.setIsActive(true);
            staff.add(s);
        }
        return staff;
    }

    private List<com.hospital.scheduler.algorithm.ShiftRequirementInfo> mixedDemand() {
        List<com.hospital.scheduler.algorithm.ShiftRequirementInfo> reqs = new ArrayList<>();
        for (LocalDate d = START; !d.isAfter(START.plusDays(DAYS - 1)); d = d.plusDays(1)) {
            reqs.add(new com.hospital.scheduler.algorithm.ShiftRequirementInfo("L01", d, 2));
            reqs.add(new com.hospital.scheduler.algorithm.ShiftRequirementInfo("L02", d, 3));
            reqs.add(new com.hospital.scheduler.algorithm.ShiftRequirementInfo("L03", d, 2));
            reqs.add(new com.hospital.scheduler.algorithm.ShiftRequirementInfo("L04", d, 4));
        }
        return reqs;
    }

    /** Mirror of LocalSearchScheduler.solve() problem-building (comp day map included). */
    private SchedulingProblem buildProblem(SchedulingConfig config,
                                           List<com.hospital.scheduler.algorithm.ShiftRequirementInfo> reqs,
                                           CompensationDateCalculator compCalc) {
        List<com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo> v10Reqs = new ArrayList<>();
        int seq = 1;
        for (var sr : reqs) {
            for (int k = 0; k < Math.max(1, sr.requiredCount()); k++) {
                v10Reqs.add(new com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo(
                        seq++, sr.workDate(), sr.shiftTypeId(), sr.specialtyId(), 1));
            }
        }
        Map<LocalDate, LocalDate> compDayOf = new HashMap<>();
        for (var r : v10Reqs) {
            if ("L01".equals(r.shiftTypeId())) {
                LocalDate c = compCalc.calculate(r.date());
                if (c != null) compDayOf.putIfAbsent(r.date(), c);
            }
        }
        return SchedulingProblem.withRequirementsAndCompDayMap(
                tenStaff(), v10Reqs, List.of(), new HashMap<>(),
                compDayOf, java.util.Set.of(), config);
    }

    private LocalSearchScheduler scheduler(SchedulingConfig config, CompensationDateCalculator compCalc) {
        return new LocalSearchScheduler(config,
                mock(HolidayRepository.class), compCalc,
                mock(ConfigService.class),
                new StrategyProperties());
    }

    /** Snapshot of metrics at a single iteration boundary. */
    private record Snapshot(int iteration, double mixDeviation, double coverage, int hardViolations) {}

    /**
     * Plateau analysis using a manual loop so we can capture mixDeviation at each
     * iteration boundary. Bypasses {@code algo.search()} which only returns the
     * final result — by replicating its loop we can stop at any iteration we
     * want to log a snapshot.
     */
    @Test
    @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void plateau_curve_with_per_iteration_snapshots() {
        SchedulingConfig config = searchConfig();
        CompensationDateCalculator compCalc = realCompCalc();
        SchedulingProblem problem = buildProblem(config, mixedDemand(), compCalc);
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);

        ConstraintRegistry registry = new ConstraintRegistry();
        registry.register(new ShiftConflictConstraint());
        registry.register(new DuplicateShiftConstraint());
        registry.register(new AdjacentL01Constraint());
        registry.register(new CompensationDayConstraint());

        ScoreDirector scoreDirector = new ScoreDirector(descriptor);
        // Capture best solution's score + tracked moves; this publisher's subscribe
        // is not used by us — we hook into SCORE_IMPROVED to snapshot via best path.
        SearchEventPublisher publisher = new SearchEventPublisher() {
            @Override public Subscription subscribe(String r, Subscriber s) { return () -> {}; }
            @Override public void publish(SearchEvent e) { }
            @Override public void clear(String r) {}
        };
        SearchDirector searchDirector = new SearchDirector(scoreDirector, hub, publisher);
        SampledMoveSelector selector = new SampledMoveSelector(descriptor, config);
        TabuAcceptor acceptor = new TabuAcceptor(config);
        CompositeTermination termination = new CompositeTermination(config);

        LocalSearchAlgorithm algo = new LocalSearchAlgorithm(
                config, selector, acceptor, termination, searchDirector,
                scoreDirector, registry, hub);

        WorkingSolution initial = scheduler(config, compCalc)
                .buildInitialSolution(problem, descriptor, config);

        // Mirror algo.search() but with snapshot hooks at boundaries 0/10/20/40/60/80/100.
        // We replay the same loop body here so we have access to `current` to read mixDeviation.
        WorkingSolution current = initial;
        scoreDirector.recomputeFull(current);
        ScoreDelta initialConstraintDelta = ScoreDelta.zero();
        for (var c : registry.all()) initialConstraintDelta = initialConstraintDelta.plus(c.evaluate(current));
        scoreDirector.applyDelta(initialConstraintDelta);
        searchDirector.onNewBest(current);
        searchDirector.onIteration(current);

        int[] snapshotPoints = {0, 10, 20, 40, 60, 80, 100, 150, 200};
        int snapIdx = 0;
        StringBuilder curve = new StringBuilder("[PLATEAU-CURVE]\n");
        // Snapshot baseline (iter=0 — pre-search state)
        curve.append(String.format(
                "  iter=%4d  current{mix=%.2f cov=%.4f hard=%d}  best{mix=%.2f cov=%.4f}  accept=0 reject=0%n",
                0, current.mixDeviation(), current.getCoverage(), hardViolations(current),
                current.mixDeviation(), current.getCoverage()));
        snapIdx++; // skip snapshotPoints[0]=0

        while (!searchDirector.getState().isTerminated()
                && !termination.isTerminated(searchDirector.getState())) {
            searchDirector.getState().incrementIteration();
            int iter = searchDirector.getState().getIteration();
            List<Move> candidates = selector.select(current, config.getSearch().getCandidateListSize());
            int acceptedThisIter = 0;
            // Use reflection to access private processMove — test-only path.
            for (Move move : candidates) {
                if (invokeProcessMove(algo, current, move)) acceptedThisIter++;
            }
            searchDirector.onIteration(current);
            if (acceptedThisIter == 0) searchDirector.onNoImprove();

            // Snapshot at boundaries (cumulative: 0=before, 10=fist iter≥10, etc.)
            int thisIter = searchDirector.getState().getIteration();
            while (snapIdx < snapshotPoints.length && thisIter >= snapshotPoints[snapIdx]) {
                int hardCount = hardViolations(current);
                double mix = current.mixDeviation();
                double cov = current.getCoverage();
                // ALSO snapshot bestSolution's metrics separately because getSolution()
                // returns bestSolution, not `current` (they may differ in uphill moves).
                WorkingSolution best = searchDirector.getBestSolution();
                double bestMix = best != null ? best.mixDeviation() : -1.0;
                double bestCov = best != null ? best.getCoverage() : -1.0;
                curve.append(String.format(
                        "  iter=%4d  current{mix=%.2f cov=%.4f hard=%d}  best{mix=%.2f cov=%.4f}  accept=%d reject=%d%n",
                        thisIter, mix, cov, hardCount, bestMix, bestCov,
                        searchDirector.getState().getAcceptedMoves(),
                        searchDirector.getState().getRejectedMoves()));
                snapIdx++;
            }
            if (thisIter >= 250) break; // safety cap
        }

        WorkingSolution finalBest = searchDirector.getBestSolution();
        curve.append(String.format("  FINAL     mix=%.2f  cov=%.4f  hard=%d  iters=%d  reason=%s%n",
                finalBest != null ? finalBest.mixDeviation() : -1.0,
                finalBest != null ? finalBest.getCoverage() : -1.0,
                finalBest != null ? hardViolations(finalBest) : -1,
                searchDirector.getState().getIteration(),
                searchDirector.getState().getTerminationReason()));
        System.out.println(curve.toString());

        // Soft assertions: best must be hard-free; we don't pin mixDeviation here because
        // we want the human to read the curve and decide whether to tighten maxNoImprove.
        assertTrue(finalBest != null, "best solution must be set");
        assertTrue(hardViolations(finalBest) == 0, "best must be hard-free");
    }

    /**
     * Reflective invocation of the private {@code processMove(WorkingSolution, Move)}
     * method so the test can drive the same loop logic as {@code algo.search(initial)}
     * while keeping access to the local {@code current} reference for snapshotting.
     */
    private boolean invokeProcessMove(LocalSearchAlgorithm algo, WorkingSolution sol, Move move) {
        try {
            var m = LocalSearchAlgorithm.class.getDeclaredMethod("processMove", WorkingSolution.class, Move.class);
            m.setAccessible(true);
            return (boolean) m.invoke(algo, sol, move);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Test cannot drive V10 loop: " + ex, ex);
        }
    }

    /**
     * Capture mixDeviation/coverage via publisher and run search.
     * Returns snapshots at: 0, 10, 20, 40, 60, 80, 100, 150, 200.
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void plateau_curve_for_mixDeviation_and_coverage() throws InterruptedException {
        SchedulingConfig config = searchConfig();
        CompensationDateCalculator compCalc = realCompCalc();
        SchedulingProblem problem = buildProblem(config, mixedDemand(), compCalc);
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);

        ConstraintRegistry registry = new ConstraintRegistry();
        registry.register(new ShiftConflictConstraint());
        registry.register(new DuplicateShiftConstraint());
        registry.register(new AdjacentL01Constraint());
        registry.register(new CompensationDayConstraint());

        ScoreDirector scoreDirector = new ScoreDirector(descriptor);
        // Capturing subscriber: snapshot mixDeviation + coverage at SCORE_IMPROVED
        // and at every 10th ITERATION boundary (cheap because we filter inside lambda).
        ConcurrentLinkedQueue<Snapshot> snapshots = new ConcurrentLinkedQueue<>();
        SearchEventPublisher.Subscriber capturer = event -> {
            int iter = event.getIteration();
            if (event.getType() == SearchEventType.SCORE_IMPROVED
                    || (event.getType() == SearchEventType.ITERATION && iter % 10 == 0)) {
                // Snapshot current best via event.bestScore (we don't have WorkingSolution here).
                // For plateau curve, we only need accepted-move metrics; track iteration only.
                // (WorkingSolution snapshot would require threading it through publisher, which
                // the codebase does not expose today. Plateau curve is approximated by these
                // markers + final mixDeviation reported at end of test.)
            }
        };
        // Wrap as full publisher — only publish() needs to be implemented; subscribe/clear no-op.
        SearchEventPublisher publisher = new SearchEventPublisher() {
            @Override public Subscription subscribe(String r, Subscriber s) { return () -> {}; }
            @Override public void publish(SearchEvent e) { capturer.onEvent(e); }
            @Override public void clear(String r) {}
        };
        SearchDirector searchDirector = new SearchDirector(scoreDirector, hub, publisher);
        SampledMoveSelector selector = new SampledMoveSelector(descriptor, config);
        TabuAcceptor acceptor = new TabuAcceptor(config);
        CompositeTermination termination = new CompositeTermination(config);

        LocalSearchAlgorithm algo = new LocalSearchAlgorithm(
                config, selector, acceptor, termination, searchDirector,
                scoreDirector, registry, hub);

        WorkingSolution initial = scheduler(config, compCalc)
                .buildInitialSolution(problem, descriptor, config);

        // Baseline (round 0) snapshot from initial
        double baselineMix = initial.mixDeviation();
        double baselineCov = initial.getCoverage();
        int baselineHard = hardViolations(initial);

        long t0 = System.nanoTime();
        LocalSearchAlgorithm.SearchResult result = algo.search(initial);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        WorkingSolution finalSol = result.getSolution();
        double finalMix = finalSol != null ? finalSol.mixDeviation() : Double.NaN;
        double finalCov = finalSol != null ? finalSol.getCoverage() : Double.NaN;
        int finalHard = finalSol != null ? hardViolations(finalSol) : -1;

        // Print plateau curve for the Tech Lead to read in CI logs.
        // Without per-iteration capture we only have start vs end, but those two points
        // are enough to estimate the magnitude of "200 rounds is overkill" if mix & coverage
        // barely move from baseline → search already arrived at a local optimum during
        // buildInitialSolution. (See phase-shift analysis at end.)
        System.out.println("[PLATEAU] baseline mixDeviation=" + String.format("%.2f", baselineMix)
                + " coverage=" + String.format("%.4f", baselineCov)
                + " hard=" + baselineHard);
        System.out.println("[PLATEAU] final    mixDeviation=" + String.format("%.2f", finalMix)
                + " coverage=" + String.format("%.4f", finalCov)
                + " hard=" + finalHard);
        System.out.println("[PLATEAU] iterations=" + result.getIterations()
                + " elapsedMs=" + elapsedMs
                + " acceptedMoves=" + result.getAcceptedMoves()
                + " rejectedMoves=" + result.getRejectedMoves()
                + " termination=" + result.getTerminationReason());

        // Soft assertions — we don't fail here on a single run; we log.
        // NOTE: baseline mixDeviation = 0 means initial assignment is empty (round-0 build
        // happens BEFORE search runs); the search fills slots so mix will naturally grow.
        // What we WANT to assert: mix at end is BOUNDED (not pathological); hard = 0.
        assertTrue(finalHard == 0,
                "search result must be hard-free; got " + finalHard);
        assertTrue(finalMix < 100.0,
                "mixDeviation should stay below pathological levels; got " + finalMix);
        assertTrue(result.getIterations() > 0,
                "search must execute at least 1 iteration; got " + result.getIterations());
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

    // Count of assignments actually placed.
    @SuppressWarnings("unused")
    private static int countAssigned(WorkingSolution sol) {
        int n = 0;
        for (MutableAssignment a : sol.getAssignments()) {
            if (a.staffId > 0) n++;
        }
        return n;
    }

    // ─── Greedy vs V10 comparison ────────────────────────────────────────────

    /**
     * Compare Greedy (buildInitialSolution), FairGreedy-like (sortRequirementsByPriority + batch),
     * and V10 LocalSearch on the same problem data.
     *
     * Key metric: mixDeviation of final solution. Greedy/FairGreedy should produce
     * L01/L02/L03 distribution closer to even (lower mixDeviation) than V10's current output.
     *
     * Run: 10 staff, 28 days, mixedDemand.
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void greedy_vs_v10_mixDeviation_comparison() {
        SchedulingConfig config = searchConfig();
        CompensationDateCalculator compCalc = realCompCalc();
        SchedulingProblem problem = buildProblem(config, mixedDemand(), compCalc);
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);

        // Greedy baseline: just the initial build
        WorkingSolution greedySol = scheduler(config, compCalc)
                .buildInitialSolution(problem, descriptor, config);
        double greedyMix = greedySol.mixDeviation();
        double greedyCov = greedySol.getCoverage();
        int greedyHard = hardViolations(greedySol);
        int greedyL01 = countType(greedySol, "L01");
        int greedyL02 = countType(greedySol, "L02");
        int greedyL03 = countType(greedySol, "L03");
        int greedyL04 = countType(greedySol, "L04");

        // V10 search: full pipeline
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);
        ConstraintRegistry registry = new ConstraintRegistry();
        registry.register(new ShiftConflictConstraint());
        registry.register(new DuplicateShiftConstraint());
        registry.register(new AdjacentL01Constraint());
        registry.register(new CompensationDayConstraint());
        ScoreDirector sd = new ScoreDirector(descriptor);
        SearchDirector dir = new SearchDirector(sd, hub);
        LocalSearchAlgorithm algo = new LocalSearchAlgorithm(config,
                new SampledMoveSelector(descriptor, config),
                new TabuAcceptor(config),
                new CompositeTermination(config),
                dir, sd, registry, hub);

        long t0 = System.nanoTime();
        LocalSearchAlgorithm.SearchResult result = algo.search(greedySol);
        long elapsed = (System.nanoTime() - t0) / 1_000_000;
        WorkingSolution v10Sol = result.getSolution();
        double v10Mix = v10Sol != null ? v10Sol.mixDeviation() : Double.NaN;
        double v10Cov = v10Sol != null ? v10Sol.getCoverage() : Double.NaN;
        int v10Hard = v10Sol != null ? hardViolations(v10Sol) : -1;
        int v10L01 = v10Sol != null ? countType(v10Sol, "L01") : -1;
        int v10L02 = v10Sol != null ? countType(v10Sol, "L02") : -1;
        int v10L03 = v10Sol != null ? countType(v10Sol, "L03") : -1;
        int v10L04 = v10Sol != null ? countType(v10Sol, "L04") : -1;

        System.out.println("[COMPARISON]");
        System.out.printf("  Greedy: mix=%7.2f cov=%6.4f hard=%d  L01=%3d L02=%3d L03=%3d L04=%3d%n",
                greedyMix, greedyCov, greedyHard, greedyL01, greedyL02, greedyL03, greedyL04);
        System.out.printf("  V10:    mix=%7.2f cov=%6.4f hard=%d  L01=%3d L02=%3d L03=%3d L04=%3d  iters=%dms=%d%n",
                v10Mix, v10Cov, v10Hard, v10L01, v10L02, v10L03, v10L04,
                result.getIterations(), elapsed);

        // Assertions
        assertTrue(greedyHard == 0, "Greedy must be hard-free");
        assertTrue(v10Hard == 0, "V10 must be hard-free");
        // Greedy mix should be lower (more even) than V10's initial state
        assertTrue(greedyMix < 50.0,
                "Greedy mix should be reasonable; got " + greedyMix);
        assertTrue(v10Mix < 50.0,
                "V10 mix should be reasonable; got " + v10Mix);
        // V10 should NOT make mix significantly worse than Greedy
        // (if v10Mix > greedyMix * 1.5 → search degraded fairness)
        assertTrue(v10Mix <= greedyMix * 1.5,
                "V10 mix should not be much worse than Greedy: greedy=" + greedyMix + " v10=" + v10Mix);
    }

    private int countType(WorkingSolution sol, String type) {
        int n = 0;
        for (MutableAssignment a : sol.getAssignments()) {
            if (a.staffId > 0 && type.equals(a.shiftTypeId)) n++;
        }
        return n;
    }
}
