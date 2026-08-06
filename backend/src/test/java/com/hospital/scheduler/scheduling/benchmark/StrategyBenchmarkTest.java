package com.hospital.scheduler.scheduling.benchmark;

import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.constraint.ConstraintRegistry;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.search.*;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import com.hospital.scheduler.scheduling.strategy.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-12-03: Benchmark 6 acceptance strategies on a realistic problem.
 * Run with: {@code -Dbenchmark.strategies=true}
 */
@EnabledIfSystemProperty(named = "benchmark.strategies", matches = "true")
class StrategyBenchmarkTest {

    @Test
    void benchmark6Strategies() throws IOException {
        new java.io.File("target/benchmarks").mkdirs();

        // Build the problem once
        List<Staff> staff = buildStaff(80);
        List<ShiftRequirementInfo> reqs = buildRequirements(LocalDate.of(2026, 7, 1), 31);
        SchedulingProblem problem = SchedulingProblem.withRequirements(
                staff, reqs, java.util.List.of(),
                java.util.Map.of(), java.util.Set.of(), new SchedulingConfig());

        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(null);
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, hub);
        SchedulingConfig config = new SchedulingConfig();
        config.getSearch().setCandidateListSize(50);
        config.getSearch().setMaxIterations(300);
        config.getSearch().setTimeLimitSeconds(10);

        ConstraintRegistry registry = buildRegistry();

        // Seed a common initial solution for all strategies
        WorkingSolution seed = WorkingSolution.fromProblem(config, descriptor);
        Random rng = new Random(42);
        for (var req : problem.getRequirements()) {
            if (rng.nextDouble() < 0.3) {
                List<Integer> eligible = problem.getEligibleStaff(req.id());
                if (!eligible.isEmpty()) {
                    int sid = eligible.get(rng.nextInt(eligible.size()));
                    try { seed.assign(req.id(), sid); } catch (Exception ignored) {}
                }
            }
        }

        List<StrategyResult> results = new ArrayList<>();
        for (StrategyCase c : StrategyCase.values()) {
            results.add(runOne(c, config, problem, descriptor, seed, registry));
        }

        // Write CSV
        try (PrintWriter csv = new PrintWriter(new FileWriter("target/benchmarks/strategy-benchmark.csv"))) {
            csv.println("strategy,coverage,hard,fairness_cv,time_ms,iterations,accepted,rejected,terminated");
            for (StrategyResult r : results) {
                csv.printf("%s,%.4f,%d,%.4f,%d,%d,%d,%d,%s%n",
                        r.name, r.coverage, r.hardViolations,
                        r.fairnessCv, r.timeMs, r.iterations,
                        r.accepted, r.rejected, r.terminated);
            }
        }

        // Print summary
        System.out.println("\n=== Strategy Benchmark (P4: 80 staff, ~750 req, 300 iters) ===");
        System.out.printf("%-22s %10s %8s %10s %10s%n",
                "Strategy", "Coverage", "Hard", "Fairness", "Time(ms)");
        System.out.println("────────────────────────────────────────────────────────────────────");
        for (StrategyResult r : results) {
            System.out.printf("%-22s %10.4f %8d %10.4f %10d%n",
                    r.name, r.coverage, r.hardViolations,
                    r.fairnessCv, r.timeMs);
        }
    }

    private StrategyResult runOne(StrategyCase c, SchedulingConfig config,
                                  SchedulingProblem problem, SolutionDescriptor descriptor,
                                  WorkingSolution seed, ConstraintRegistry registry) {
        // Fresh hub + descriptor per strategy (no cross-pollution)
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);
        SolutionDescriptor localDesc = new SolutionDescriptor(problem, hub);

        // Build fresh solution and copy seed assignments
        WorkingSolution sol = WorkingSolution.fromProblem(config, localDesc);
        for (var a : seed.getAssignments()) {
            if (a.staffId > 0) {
                try { sol.assign(a.slotId, a.staffId); } catch (Exception ignored) {}
            }
        }

        ScoreDirector scoreDirector = new ScoreDirector(localDesc);
        SearchDirector director = new SearchDirector(scoreDirector, hub);
        SampledMoveSelector selector = new SampledMoveSelector(localDesc, config);
        CompositeTermination termination = new CompositeTermination(config);

        StrategyProperties props = new StrategyProperties();
        props.setStrategy(c.strategyType.name());
        props.setTabuTenureMin(5);
        props.setTabuTenureMax(15);
        props.setSaT0(c.temperature);
        props.setSaCooling(0.995);
        props.setSaTmin(1.0);
        props.setLaMemory(100);
        props.setGdDecay(0.999);
        props.setGdInitialLevel(1.0);

        MoveAcceptor acceptor = StrategyAcceptorFactory.build(props, config.getSearch().getMaxIterations());

        LocalSearchAlgorithm algorithm = new LocalSearchAlgorithm(
                config, selector, acceptor, termination, director,
                scoreDirector, registry, hub);

        long t0 = System.nanoTime();
        var result = algorithm.search(sol);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        var score = result.getScore();
        return new StrategyResult(
                c.displayName,
                sol.getCoverage(),
                score.getHardViolations(),
                score.getCvTotal(),
                (int) elapsedMs,
                result.getIterations(),
                result.getAcceptedMoves(),
                result.getRejectedMoves(),
                result.getTerminationReason());
    }

    private ConstraintRegistry buildRegistry() {
        ConstraintRegistry r = new ConstraintRegistry();
        r.register(new com.hospital.scheduler.scheduling.constraint.ShiftConflictConstraint());
        r.register(new com.hospital.scheduler.scheduling.constraint.LeaveConflictConstraint());
        r.register(new com.hospital.scheduler.scheduling.constraint.DuplicateShiftConstraint());
        r.register(new com.hospital.scheduler.scheduling.constraint.AdjacentL01Constraint());
        r.register(new com.hospital.scheduler.scheduling.constraint.MaxShiftsConstraint());
        r.register(new com.hospital.scheduler.scheduling.constraint.RestDayConstraint());
        return r;
    }

    private List<Staff> buildStaff(int count) {
        List<Staff> staff = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Staff s = new Staff();
            s.setId(i);
            s.setFullName("BS" + i);
            s.setIsActive(true);
            s.setMaxShiftsPerMonth(10);
            staff.add(s);
        }
        return staff;
    }

    private List<ShiftRequirementInfo> buildRequirements(LocalDate start, int days) {
        List<ShiftRequirementInfo> reqs = new ArrayList<>();
        String[] types = {"L01", "L02", "L03", "L04"};
        int[] counts = {2, 5, 3, 4};
        int id = 1;
        for (int day = 0; day < days; day++) {
            LocalDate date = start.plusDays(day);
            for (int t = 0; t < 4; t++) {
                for (int c = 0; c < counts[t]; c++) {
                    reqs.add(new ShiftRequirementInfo(id++, date, types[t], null, 1));
                }
            }
        }
        return reqs;
    }

    private enum StrategyCase {
        HILL_CLIMBING(AcceptanceStrategy.HILL_CLIMBING, "HillClimbing", 100.0),
        TABU(AcceptanceStrategy.TABU, "TabuSearch", 100.0),
        SIMULATED_ANNEALING(AcceptanceStrategy.SIMULATED_ANNEALING, "SimAnnealing", 10.0),
        LATE_ACCEPTANCE(AcceptanceStrategy.LATE_ACCEPTANCE, "LateAcceptance", 100.0),
        GREAT_DELUGE(AcceptanceStrategy.GREAT_DELUGE, "GreatDeluge", 100.0),
        VNS(AcceptanceStrategy.VARIABLE_NEIGHBORHOOD_SEARCH, "VNS", 100.0);

        final AcceptanceStrategy strategyType;
        final String displayName;
        final double temperature;

        StrategyCase(AcceptanceStrategy t, String name, double temp) {
            this.strategyType = t;
            this.displayName = name;
            this.temperature = temp;
        }
    }

    private record StrategyResult(
            String name, double coverage, int hardViolations,
            double fairnessCv, int timeMs, int iterations,
            int accepted, int rejected, String terminated) {}
}
