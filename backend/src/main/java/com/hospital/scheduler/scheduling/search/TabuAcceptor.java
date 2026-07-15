package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ObjectiveScore;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tabu search acceptor with iteration-based tenure.
 * 
 * <p>Features:
 * <ul>
 *   <li>Iteration-based tabu tenure (not timestamp)</li>
 *   <li>Adaptive tenure adjustment</li>
 *   <li>Aspiration criteria</li>
 *   <li>Random diversification</li>
 * </ul>
 */
@Slf4j
public class TabuAcceptor implements MoveAcceptor {

    private final SchedulingConfig config;
    private final IterationBasedTabuSet tabuSet;
    private final Random random = new Random();

    // State
    private int currentIteration = 0;
    private int consecutiveImprovements = 0;
    private int consecutiveStagnations = 0;
    private int currentTenure;

    public TabuAcceptor(SchedulingConfig config) {
        this.config = config;
        int min = config.getSearch().getTabuTenureMin();
        int max = config.getSearch().getTabuTenureMax();
        this.tabuSet = new IterationBasedTabuSet(min, max);
        this.currentTenure = (min + max) / 2;
    }

    @Override
    public AcceptResult shouldAccept(Move move, ObjectiveScore current, 
                                    ObjectiveScore proposed, ObjectiveScore best) {
        
        currentIteration++;
        String key = move.moveKey();

        // Check tabu status
        if (tabuSet.isTabu(key, currentIteration)) {
            // Aspiration: if this is better than best, accept anyway
            if (isAspirationBetter(proposed, best)) {
                tabuSet.add(key, currentIteration);
                consecutiveStagnations = 0;
                return AcceptResult.accept("Aspiration: better than best");
            }
            return AcceptResult.reject("Tabu: " + key);
        }

        // Lexicographic comparison
        int cmp = proposed.compareTo(current);

        if (cmp < 0) {
            // Improvement
            tabuSet.add(key, currentIteration);
            consecutiveImprovements++;
            consecutiveStagnations = 0;
            adaptTenure(true);
            return AcceptResult.accept("Improvement");
        }

        // Random diversification
        if (random.nextDouble() < config.getSearch().getDiversificationProbability()) {
            tabuSet.add(key, currentIteration);
            return AcceptResult.accept("Random diversification");
        }

        // No improvement
        consecutiveStagnations++;
        consecutiveImprovements = 0;
        adaptTenure(false);

        return AcceptResult.reject("No improvement");
    }

    private boolean isAspirationBetter(ObjectiveScore proposed, ObjectiveScore best) {
        if (best == null) return true;

        // Feasible and better
        if (proposed.hardViolations() < best.hardViolations()) {
            return true;
        }

        // Same hard violations but better in other aspects
        if (proposed.hardViolations() == best.hardViolations()) {
            if (proposed.coverage() > best.coverage() + 0.1) {
                return true;
            }
        }

        return false;
    }

    private void adaptTenure(boolean improvement) {
        int min = config.getSearch().getTabuTenureMin();
        int max = config.getSearch().getTabuTenureMax();

        if (improvement) {
            if (consecutiveImprovements > 5) {
                // Decrease tenure to intensify
                currentTenure = Math.max(min, currentTenure - 1);
                consecutiveImprovements = 0;
            }
        } else {
            if (consecutiveStagnations > 10) {
                // Increase tenure to diversify
                currentTenure = Math.min(max, currentTenure + 1);
                consecutiveStagnations = 0;
            }
        }
    }

    public int getCurrentIteration() {
        return currentIteration;
    }

    public int getTabuSize() {
        return tabuSet.size();
    }

    public void reset() {
        currentIteration = 0;
        consecutiveImprovements = 0;
        consecutiveStagnations = 0;
        tabuSet.clear();
    }

    /**
     * Iteration-based tabu set.
     */
    public static class IterationBasedTabuSet {
        private final Map<String, Integer> tabuTable = new ConcurrentHashMap<>();
        private final int minTenure;
        private final int maxTenure;
        private final Random random = new Random();

        public IterationBasedTabuSet(int minTenure, int maxTenure) {
            this.minTenure = minTenure;
            this.maxTenure = maxTenure;
        }

        /**
         * Add move to tabu list with iteration-based expiry.
         */
        public void add(String moveKey, int currentIteration) {
            int tenure = minTenure + random.nextInt(maxTenure - minTenure + 1);
            tabuTable.put(moveKey, currentIteration + tenure);
        }

        /**
         * Check if move is tabu.
         */
        public boolean isTabu(String moveKey, int currentIteration) {
            Integer expiry = tabuTable.get(moveKey);
            if (expiry == null) return false;

            if (currentIteration >= expiry) {
                tabuTable.remove(moveKey);
                return false;
            }
            return true;
        }

        /**
         * Clear all tabu entries.
         */
        public void clear() {
            tabuTable.clear();
        }

        /**
         * Get number of tabu moves.
         */
        public int size() {
            return tabuTable.size();
        }
    }
}
