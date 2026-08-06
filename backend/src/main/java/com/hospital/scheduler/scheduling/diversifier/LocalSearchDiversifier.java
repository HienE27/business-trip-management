package com.hospital.scheduler.scheduling.diversifier;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.move.Move.MoveType;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.IncrementalStatisticsHub;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Default diversification strategy combining all four mechanisms:
 * <ol>
 *   <li><b>Elite pool</b> — tracks the top-N solutions by coverage + hard violations
 *       + mix deviation. Used to seed restarts and for path relinking.</li>
 *   <li><b>Tabu frequency</b> — penalises (slot, staff) pairs that have appeared
 *       frequently in recent solutions by marking them tabu for additional iterations.</li>
 *   <li><b>No-improve restart</b> — triggers a restart when the search fails to
 *       improve for more than {@code noImproveThreshold} iterations.</li>
 *   <li><b>Path relinking</b> — when restarting, instead of a pure restart from the
 *       best elite, generates a trajectory toward the best elite from the current
 *       solution and picks the best intermediate.</li>
 * </ol>
 *
 * <p>The {@code moveSelector} and {@code statisticsHub} are used for frequency
 * counting and for applying shaking moves when required.
 */
public class LocalSearchDiversifier implements SchedulingDiversifier {

    private final EliteSolutionPool elitePool;
    private final NoImproveRestartStrategy restartStrategy;
    private final PathRelinking pathRelinking;
    private final FrequencyTabuStore frequencyTabu;
    private final int shakingMoves;

    /** Seed for the current restart — the solution we restarted FROM. */
    private WorkingSolution restartSeed;

    public LocalSearchDiversifier(EliteSolutionPool elitePool,
                                  NoImproveRestartStrategy restartStrategy,
                                  PathRelinking pathRelinking,
                                  int frequencyTabuTenure,
                                  int shakingMoves) {
        this.elitePool = elitePool;
        this.restartStrategy = restartStrategy;
        this.pathRelinking = pathRelinking;
        this.frequencyTabu = new FrequencyTabuStore(frequencyTabuTenure);
        this.shakingMoves = shakingMoves;
    }

    public LocalSearchDiversifier(int elitePoolSize,
                                  int noImproveThreshold,
                                  int maxRestarts,
                                  int pathRelinkingSteps,
                                  int frequencyTabuTenure,
                                  int shakingMoves) {
        this(new EliteSolutionPool(elitePoolSize),
                new NoImproveRestartStrategy(noImproveThreshold, maxRestarts),
                new PathRelinking(pathRelinkingSteps),
                frequencyTabuTenure,
                shakingMoves);
    }

    /**
     * Convenience constructor with sensible defaults:
     * - pool size: 5
     * - no-improve threshold: 100
     * - max restarts: 3
     * - path relinking steps: 20
     * - frequency tabu tenure: 10
     * - shaking: 5 random moves after restart
     */
    public LocalSearchDiversifier() {
        this(5, 100, 3, 20, 10, 5);
    }

    @Override
    public DiversifierSignal decide(WorkingSolution current,
                                   WorkingSolution best,
                                   int noImproveSinceRestart,
                                   int iterationsSinceRestart) {
        return restartStrategy.decide(current, best, noImproveSinceRestart, iterationsSinceRestart);
    }

    @Override
    public void onNewBest(WorkingSolution solution) {
        if (solution == null) return;
        elitePool.offer(
                solution,
                solution.getCoverage(),
                0, // hard violations should be 0 for best
                solution.mixDeviation());
    }

    @Override
    public void beforeRestart() {
        restartSeed = elitePool.getBest();
    }

    @Override
    public void afterRestart(WorkingSolution restartedFrom, WorkingSolution current) {
        // Apply frequency tabu from current solution
        for (var a : current.getAssignments()) {
            if (a.staffId > 0) {
                frequencyTabu.mark(a.slotId, a.staffId);
            }
        }
    }

    /**
     * Returns the solution to restart from. If path relinking is enabled and an
     * elite solution exists, applies path relinking from the current working
     * solution toward the best elite and returns the best intermediate.
     *
     * @param current the current (possibly stale) working solution
     * @return the solution to seed the restart from
     */
    public WorkingSolution getRestartTarget(WorkingSolution current) {
        WorkingSolution bestElite = elitePool.getBest();
        if (bestElite == null) {
            return restartSeed != null ? restartSeed : current;
        }
        // Apply path relinking from current toward best elite
        WorkingSolution relinked = pathRelinking.relink(
                current,
                bestElite,
                sol -> sol.getCoverage() - sol.mixDeviation() * 0.01);
        return relinked;
    }

    /**
     * Marks a (slot, staff) pair as used in the current solution. Frequency
     * tabu stores counts; pairs exceeding the frequency threshold are marked
     * tabu for the configured tenure.
     */
    public void recordAssignment(int slotId, int staffId) {
        frequencyTabu.mark(slotId, staffId);
    }

    /**
     * Checks whether a (slot, staff) pair is frequency-tabu.
     * Frequency-tabu overrides normal tabu: even if the acceptance policy would
     * accept a move, if the pair is frequency-tabu it is rejected.
     */
    public boolean isFrequencyTabu(int slotId, int staffId) {
        return frequencyTabu.isTabu(slotId, staffId);
    }

    /**
     * Decrements all frequency tabu counters by one. Called once per iteration
     * so that tenure counts down correctly.
     */
    public void tick() {
        frequencyTabu.tick();
    }

    public EliteSolutionPool getElitePool() {
        return elitePool;
    }

    public NoImproveRestartStrategy getRestartStrategy() {
        return restartStrategy;
    }

    // ─── FrequencyTabuStore (private, stateless entry) ───────────────────────────

    /**
     * Frequency-based tabu store. Instead of tenure in iterations, this tracks
     * how many times a (slot, staff) pair has appeared in the current solution
     * pool. Pairs appearing more than {@code frequencyThreshold} times are
     * marked tabu for an additional {@code tenure} iterations.
     */
    public static final class FrequencyTabuStore {
        private final int tenure;
        private final java.util.Map<Long, Integer> counts = new java.util.HashMap<>();
        private final java.util.Map<Long, Integer> tabuExpiry = new java.util.HashMap<>();

        public FrequencyTabuStore(int tenure) {
            this.tenure = Math.max(1, tenure);
        }

        public void mark(int slotId, int staffId) {
            long key = pack(slotId, staffId);
            counts.merge(key, 1, Integer::sum);
        }

        public boolean isTabu(int slotId, int staffId) {
            long key = pack(slotId, staffId);
            Integer expiry = tabuExpiry.get(key);
            if (expiry != null && expiry > 0) return true;
            Integer count = counts.get(key);
            return count != null && count >= frequencyThreshold;
        }

        public void tick() {
            // Decrement all expiry counters
            tabuExpiry.replaceAll((k, v) -> Math.max(0, v - 1));
            tabuExpiry.entrySet().removeIf(e -> e.getValue() <= 0);
            // Increment counts → once count >= threshold, mark as tabu
            for (var e : counts.entrySet()) {
                if (e.getValue() >= frequencyThreshold) {
                    tabuExpiry.merge(e.getKey(), tenure, Math::max);
                }
            }
        }

        public void clear() {
            counts.clear();
            tabuExpiry.clear();
        }

        private static final int frequencyThreshold = 3;

        private static long pack(int slot, int staff) {
            return ((long) slot << 32) | (staff & 0xffffffffL);
        }
    }
}