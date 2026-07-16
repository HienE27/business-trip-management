package com.hospital.scheduler.scheduling.benchmark;

import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.constraint.AdjacentL01Constraint;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1.4 — Benchmark suite for the v10 LocalSearchScheduler.
 *
 * <p>Runs 5 datasets (D1..D5) and writes per-dataset CSV into
 * {@code target/benchmarks/v10-{dataset}.csv}. Disabled by default — run with
 * {@code -Dbenchmark=true}.
 */
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class BenchmarkSuiteTest {

    private record Dataset(String name, int staffCount, int days, int totalShifts) {}

    private static final List<Dataset> DATASETS = List.of(
            new Dataset("D1", 20, 31, 124),
            new Dataset("D2", 100, 30, 500),
            new Dataset("D3", 100, 30, 1000),
            new Dataset("D4", 200, 30, 3000),
            new Dataset("D5", 250, 30, 5000)
    );

    @Test
    void runAllDatasets() throws IOException {
        new java.io.File("target/benchmarks").mkdirs();
        try (PrintWriter summary = new PrintWriter(new FileWriter("target/benchmarks/summary.csv", true))) {
            summary.println("dataset,staff,days,shifts,timeMs,heapMb,iterations,accepted,rejected,score");
            for (Dataset d : DATASETS) {
                runOne(d, summary);
            }
        }
    }

    private void runOne(Dataset d, PrintWriter summary) throws IOException {
        SchedulingConfig config = new SchedulingConfig();
        config.getSearch().setCandidateListSize(50);
        config.getSearch().setMaxIterations(2_000);
        config.getSearch().setTimeLimitSeconds(30);

        SchedulingProblem problem = buildProblem(d);
        SolutionDescriptor descriptor = new SolutionDescriptor(problem, null);
        IncrementalStatisticsHub hub = IncrementalStatisticsHub.create(descriptor);
        SolutionDescriptor wired = new SolutionDescriptor(problem, hub);
        WorkingSolution sol = WorkingSolution.fromProblem(config, wired);
        seedAssignments(sol, d, new Random(d.staffCount * 31L + d.totalShifts));

        ConstraintRegistry registry = new ConstraintRegistry();
        registry.register(new ShiftConflictConstraint());
        registry.register(new LeaveConflictConstraint());
        registry.register(new DuplicateShiftConstraint());
        registry.register(new AdjacentL01Constraint());
        registry.register(new MaxShiftsConstraint());
        registry.register(new RestDayConstraint());

        ScoreDirector scoreDirector = new ScoreDirector(wired);
        SearchDirector director = new SearchDirector(scoreDirector, hub);
        SampledMoveSelector selector = new SampledMoveSelector(wired, config);
        TabuAcceptor acceptor = new TabuAcceptor(config);
        CompositeTermination termination = new CompositeTermination(config);

        System.gc();
        long memBefore = usedHeap();
        long t0 = System.nanoTime();

        LocalSearchAlgorithm algorithm = new LocalSearchAlgorithm(
                config, selector, acceptor, termination, director,
                scoreDirector, registry, hub);
        var result = algorithm.search(sol);

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        long heapMb = Math.max(0, (usedHeap() - memBefore) / (1024 * 1024));
        double score = result.getScore().getHardViolations() > 0
                ? Double.POSITIVE_INFINITY
                : (result.getScore().getCoverage()
                        + result.getScore().getCvTotal()
                        + result.getScore().getGini()
                        + result.getScore().getGap());
        int iterations = result.getIterations();
        int accepted = result.getAcceptedMoves();
        int rejected = result.getRejectedMoves();
        String terminationReason = result.getTerminationReason();

        try (PrintWriter csv = new PrintWriter(new FileWriter(
                "target/benchmarks/v10-" + d.name + ".csv"))) {
            csv.println("metric,value");
            csv.println("staff," + d.staffCount);
            csv.println("days," + d.days);
            csv.println("total_shifts," + d.totalShifts);
            csv.println("time_ms," + elapsedMs);
            csv.println("heap_delta_mb," + heapMb);
            csv.println("iterations," + iterations);
            csv.println("accepted_moves," + accepted);
            csv.println("rejected_moves," + rejected);
            csv.println("score," + score);
            csv.println("termination," + terminationReason);
        }
        summary.printf("%s,%d,%d,%d,%d,%d,%d,%d,%d,%s%n",
                d.name, d.staffCount, d.days, d.totalShifts,
                elapsedMs, heapMb, iterations, accepted, rejected, score);
        System.out.printf("Benchmark %s: %d ms, %d iterations, heap %d MB%n",
                d.name, elapsedMs, iterations, heapMb);
        assertTrue(elapsedMs < 60_000,
                "Benchmark " + d.name + " took " + elapsedMs + "ms — exceeds 60s budget");
    }

    private static SchedulingProblem buildProblem(Dataset d) {
        List<Staff> staff = new ArrayList<>();
        for (int i = 0; i < d.staffCount; i++) {
            Staff s = new Staff();
            s.setId(i + 1);
            s.setFullName("S" + (i + 1));
            s.setIsActive(true);
            s.setMaxShiftsPerMonth(5);
            staff.add(s);
        }
        List<ShiftRequirement> reqs = new ArrayList<>();
        String[] types = {"L01", "L02", "L03", "L04"};
        LocalDate start = LocalDate.of(2026, 7, 1);
        int total = d.totalShifts;
        for (int i = 0; i < total; i++) {
            ShiftRequirement sr = new ShiftRequirement();
            sr.setId(i + 1);
            sr.setWorkDate(start.plusDays(i % d.days));
            ShiftType st = new ShiftType();
            st.setId(types[i % types.length]);
            sr.setShiftType(st);
            sr.setRequiredStaffCount(1);
            reqs.add(sr);
        }
        return SchedulingProblem.from(staff, reqs, new ArrayList<>(),
                new ArrayList<>(), new HashSet<>(), new SchedulingConfig());
    }

    private static void seedAssignments(WorkingSolution sol, Dataset d, Random rng) {
        int n = Math.min(d.totalShifts, sol.getAssignments().size());
        for (int i = 0; i < n; i++) {
            int staffId = rng.nextInt(d.staffCount) + 1;
            sol.assign(i + 1, staffId);
        }
    }

    private static long usedHeap() {
        return ManagementFactory.getMemoryMXBean()
                .getHeapMemoryUsage().getUsed();
    }
}