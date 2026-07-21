package com.hospital.scheduler.scheduling.tune;

import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import java.util.List;
import java.util.function.BiFunction;

/**
 * AutoTuner — runs a small Latin hypercube of parameter samples on a copy of
 * the working solution for the first {@code warmupFraction} of iterations,
 * picks the sample with the best score, then commits those parameters for
 * the rest of the run.
 */
public class AutoTuner {

    public record ChosenSample(
            LatinHypercube.Sample sample,
            ScoreSnapshot score
    ) {}

    private final double warmupFraction;
    private final int iterations;

    public AutoTuner(int iterations, double warmupFraction) {
        this.iterations = Math.max(1, iterations);
        this.warmupFraction = Math.min(0.5, Math.max(0.05, warmupFraction));
    }

    /**
     * @param initial      starting solution
     * @param evaluate     runs N iterations and returns the final score
     * @return chosen sample
     */
    public ChosenSample tune(WorkingSolution initial,
                             BiFunction<WorkingSolution, LatinHypercube.Sample, ScoreSnapshot> evaluate) {
        LatinHypercube lh = new LatinHypercube(5, 42L);
        List<LatinHypercube.Sample> samples = lh.sample();
        LatinHypercube.Sample bestSample = samples.get(0);
        ScoreSnapshot bestScore = null;
        for (LatinHypercube.Sample s : samples) {
            WorkingSolution copy = clone(initial);
            ScoreSnapshot score = evaluate.apply(copy, s);
            if (bestScore == null
                    || score.getHardViolations() < bestScore.getHardViolations()
                    || (score.getHardViolations() == bestScore.getHardViolations()
                            && score.getCoverage() > bestScore.getCoverage())) {
                bestScore = score;
                bestSample = s;
            }
        }
        return new ChosenSample(bestSample, bestScore);
    }

    private static WorkingSolution clone(WorkingSolution source) {
        WorkingSolution copy = WorkingSolution.fromProblem(source.getConfig(), source.getDescriptor());
        for (var a : source.getAssignments()) {
            if (a.staffId > 0) copy.assign(a.slotId, a.staffId);
        }
        return copy;
    }
}