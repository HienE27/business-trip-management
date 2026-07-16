package com.hospital.scheduler.scheduling.alns;

import com.hospital.scheduler.scheduling.score.ScoreDirector;
import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import java.util.List;
import java.util.Random;

/**
 * Adaptive Large Neighborhood Search engine.
 *
 * <p>Per segment:
 * <ol>
 *   <li>Pick a destroy operator weighted by {@code weights}</li>
 *   <li>Destroy {@code removeFraction * slots} assignments</li>
 *   <li>Pick a repair operator weighted by {@code weights}</li>
 *   <li>Repair</li>
 *   <li>Accept with a Simulated Annealing criterion</li>
 *   <li>Bump weights of operators that produced the new best or improved solution</li>
 * </ol>
 *
 * <p>Operates on a freshly cloned working solution each iteration so the
 * caller can decide whether to commit the candidate. The ALNS engine only
 * reports its {@link Result} back.
 */
public class AlnsEngine {

    public record Result(
            WorkingSolution bestSolution,
            ScoreSnapshot bestScore,
            double[] destroyWeights,
            double[] repairWeights
    ) {}

    private final List<DestroyOperator> destroys;
    private final List<RepairOperator> repairs;
    private final double removeFraction;
    private final int segmentSize;
    private final double initialTemperature;
    private final double cooling;
    private final Random rng = new Random();

    private double[] destroyWeights;
    private double[] repairWeights;
    private double temperature;

    public AlnsEngine(List<DestroyOperator> destroys,
                      List<RepairOperator> repairs,
                      double removeFraction,
                      int segmentSize,
                      double initialTemperature,
                      double cooling) {
        this.destroys = destroys;
        this.repairs = repairs;
        this.removeFraction = Math.min(0.5, Math.max(0.05, removeFraction));
        this.segmentSize = Math.max(1, segmentSize);
        this.initialTemperature = initialTemperature;
        this.cooling = Math.min(0.9999, Math.max(0.9, cooling));
        this.destroyWeights = new double[destroys.size()];
        this.repairWeights = new double[repairs.size()];
        for (int i = 0; i < destroyWeights.length; i++) destroyWeights[i] = 1.0;
        for (int i = 0; i < repairWeights.length; i++) repairWeights[i] = 1.0;
        this.temperature = initialTemperature;
    }

    public Result run(WorkingSolution initial, ScoreDirector scoreDirector) {
        scoreDirector.recomputeFull(initial);
        ScoreSnapshot bestScore = scoreDirector.getCurrent().toImmutable();
        WorkingSolution best = clone(initial);

        int totalSlots = initial.getAssignments().size();
        int removeCount = Math.max(1, (int) (totalSlots * removeFraction));
        for (int iter = 0; iter < segmentSize * 4; iter++) {
            int dIdx = weightedPick(destroyWeights);
            int rIdx = weightedPick(repairWeights);
            DestroyOperator d = destroys.get(dIdx);
            RepairOperator r = repairs.get(rIdx);

            WorkingSolution candidate = clone(initial);
            int removed = d.destroy(candidate, removeCount);
            int inserted = r.repair(candidate, removed);
            scoreDirector.recomputeFull(candidate);

            ScoreSnapshot candidateScore = scoreDirector.getCurrent().toImmutable();
            double delta = scoreDelta(bestScore, candidateScore);
            boolean accept = delta < 0 || rng.nextDouble() < Math.exp(-delta / Math.max(1e-6, temperature));
            if (accept) {
                if (delta < 0) {
                    bestScore = candidateScore;
                    best = candidate;
                    destroyWeights[dIdx] += 1.0;
                    repairWeights[rIdx] += 1.0;
                } else {
                    destroyWeights[dIdx] += 0.5;
                    repairWeights[rIdx] += 0.5;
                }
            } else {
                destroyWeights[dIdx] = Math.max(0.1, destroyWeights[dIdx] - 0.1);
                repairWeights[rIdx] = Math.max(0.1, repairWeights[rIdx] - 0.1);
            }
            temperature *= cooling;
        }
        return new Result(best, bestScore, destroyWeights.clone(), repairWeights.clone());
    }

    public double[] getDestroyWeights() { return destroyWeights.clone(); }
    public double[] getRepairWeights() { return repairWeights.clone(); }

    private static double scoreDelta(ScoreSnapshot a, ScoreSnapshot b) {
        return (a.getHardViolations() - b.getHardViolations()) * 100.0
                + (a.getCoverage() - b.getCoverage());
    }

    private int weightedPick(double[] weights) {
        double total = 0;
        for (double w : weights) total += w;
        if (total <= 0) return rng.nextInt(weights.length);
        double r = rng.nextDouble() * total;
        double cum = 0;
        for (int i = 0; i < weights.length; i++) {
            cum += weights[i];
            if (r <= cum) return i;
        }
        return weights.length - 1;
    }

    private static WorkingSolution clone(WorkingSolution source) {
        WorkingSolution copy = WorkingSolution.fromProblem(source.getConfig(), source.getDescriptor());
        for (var a : source.getAssignments()) {
            if (a.staffId > 0) {
                copy.assign(a.slotId, a.staffId);
            }
        }
        return copy;
    }
}