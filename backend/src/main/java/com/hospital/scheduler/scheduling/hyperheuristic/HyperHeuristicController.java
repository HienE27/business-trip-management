package com.hospital.scheduler.scheduling.hyperheuristic;

import com.hospital.scheduler.scheduling.move.Move;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-move-type weight tracker used by the hyper-heuristic controller.
 *
 * <p>Tracks acceptance rate and average score delta for each
 * {@link com.hospital.scheduler.scheduling.move.Move.MoveType}. After every
 * {@code blockSize} attempts the controller consults the stats and adjusts
 * weights:
 *
 * <ul>
 *   <li>Acceptance rate > 60% AND positive avg delta → weight += 1</li>
 *   <li>Acceptance rate < 20% AND negative avg delta → weight −= 1</li>
 * </ul>
 *
 * <p>Weights are clamped to {@code [1, 100]}.
 */
public class HyperHeuristicController {

    private final int blockSize;
    private final Map<Move.MoveType, Double> weights = new HashMap<>();
    private final Map<Move.MoveType, long[]> stats = new HashMap<>();
    private int sinceLastAdjust = 0;

    public HyperHeuristicController(int blockSize) {
        this.blockSize = Math.max(1, blockSize);
    }

    /** Get current sampling weight for {@code type}. */
    public double weight(Move.MoveType type) {
        return weights.getOrDefault(type, 1.0);
    }

    /** Snapshot of all weights in registration order. */
    public Map<Move.MoveType, Double> weightsSnapshot() {
        Map<Move.MoveType, Double> copy = new LinkedHashMap<>();
        for (var e : weights.entrySet()) copy.put(e.getKey(), e.getValue());
        return copy;
    }

    /** Record a move attempt. */
    public synchronized void record(Move.MoveType type, double delta, boolean accepted) {
        weights.putIfAbsent(type, 1.0);
        long[] s = stats.computeIfAbsent(type, k -> new long[3]);
        s[0]++; // total
        if (accepted) s[1]++; // accepted
        s[2] += (long) (delta * 1_000_000); // score delta, fixed-point
        sinceLastAdjust++;
        if (sinceLastAdjust >= blockSize) {
            adjust();
            sinceLastAdjust = 0;
        }
    }

    private void adjust() {
        for (var entry : stats.entrySet()) {
            Move.MoveType type = entry.getKey();
            long[] s = entry.getValue();
            if (s[0] == 0) continue;
            double acceptRate = (double) s[1] / s[0];
            double avgDelta = (s[2] / 1_000_000.0) / s[0];
            double w = weights.getOrDefault(type, 1.0);
            if (acceptRate > 0.6 && avgDelta > 0) {
                w = Math.min(100.0, w + 1.0);
            } else if (acceptRate < 0.2 && avgDelta < 0) {
                w = Math.max(1.0, w - 1.0);
            }
            weights.put(type, w);
            // reset stats after adjustment
            s[0] = 0;
            s[1] = 0;
            s[2] = 0;
        }
    }

    public synchronized void reset() {
        weights.clear();
        stats.clear();
        sinceLastAdjust = 0;
    }

    public Move.MoveType sample(java.util.Random rng) {
        Objects.requireNonNull(rng, "rng");
        double total = 0;
        for (double w : weights.values()) total += w;
        if (total <= 0) {
            // Fall back to round-robin when all weights are zero
            int idx = (int) (System.nanoTime() % Math.max(1, weights.size()));
            int i = 0;
            for (var key : weights.keySet()) {
                if (i == idx) return key;
                i++;
            }
            return weights.keySet().iterator().next();
        }
        double r = rng.nextDouble() * total;
        double cum = 0;
        for (var entry : weights.entrySet()) {
            cum += entry.getValue();
            if (r <= cum) return entry.getKey();
        }
        return weights.keySet().iterator().next();
    }
}