package com.hospital.scheduler.scheduling.strategy;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Tabu search. Move applied within {@code tenure} iterations is forbidden
 * unless it improves on best-so-far (aspiration).
 */
public class TabuAcceptance implements MoveAcceptanceStrategy {

    private final int minTenure;
    private final int maxTenure;
    private final Random random = new Random();
    private final Map<String, Integer> tabuUntil = new HashMap<>();

    public TabuAcceptance(int minTenure, int maxTenure) {
        this.minTenure = Math.max(1, minTenure);
        this.maxTenure = Math.max(this.minTenure, maxTenure);
    }

    @Override
    public AcceptanceStrategy kind() { return AcceptanceStrategy.TABU; }

    @Override
    public void initialize(int estimatedIterations) {
        tabuUntil.clear();
    }

    @Override
    public boolean evaluate(Move move, ScoreDelta delta, boolean improving) {
        if (improving) return true;
        // For now, the caller invokes rememberApplied separately to populate
        // the tabu list. The strategy here only returns whether the move is
        // NOT tabu; the search loop is expected to check tabu status itself.
        return true;
    }

    public boolean isTabu(Move move, int iteration) {
        String key = key(move);
        Integer until = tabuUntil.get(key);
        if (until == null) return false;
        return iteration < until;
    }

    public void rememberApplied(Move move, int iteration) {
        int tenure = minTenure + random.nextInt(maxTenure - minTenure + 1);
        tabuUntil.put(key(move), iteration + tenure);
    }

    private static String key(Move move) {
        StringBuilder sb = new StringBuilder();
        sb.append(move.type()).append(':');
        for (int s : move.affectedSlotIndices()) sb.append(s).append(',');
        return sb.toString();
    }

    @Override
    public void reset() { tabuUntil.clear(); }
}
