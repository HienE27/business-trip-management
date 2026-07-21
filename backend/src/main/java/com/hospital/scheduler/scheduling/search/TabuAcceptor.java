package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.move.Move.MoveType;
import com.hospital.scheduler.scheduling.score.ScoreDelta;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Tabu-list acceptor with iteration-based tenure.
 *
 * <p>A move is "tabu" if it was applied within the last
 * {@code random(tabuTenureMin, tabuTenureMax)} iterations. Tabu moves can
 * still be accepted via aspiration (improving on best-so-far).
 */
public class TabuAcceptor implements MoveAcceptor {

    private final SchedulingConfig config;
    private final Random random = new Random(123);

    /** move-key → iteration when the move was last applied (becomes tabu until iteration+tenure) */
    private final Map<String, Integer> tabuUntil = new HashMap<>();

    public TabuAcceptor(SchedulingConfig config) {
        this.config = config;
    }

    @Override
    public boolean accept(ScoreDelta delta, int iteration, boolean improving) {
        // Aspiration: improving moves are always accepted
        if (improving) return true;
        // Otherwise rely on the caller's per-move check; the search loop calls
        // isTabu(move, iteration) before invoking us.
        return true;
    }

    /**
     * Register a move as applied — adds it to the tabu list with a random tenure.
     */
    public void rememberApplied(Move move, int iteration) {
        int min = config.getSearch().getTabuTenureMin();
        int max = config.getSearch().getTabuTenureMax();
        if (max < min) max = min;
        int tenure = min + random.nextInt(max - min + 1);
        String key = moveKey(move);
        tabuUntil.put(key, iteration + tenure);
    }

    /** True if {@code move} is currently tabu at {@code iteration}. */
    public boolean isTabu(Move move, int iteration) {
        String key = moveKey(move);
        Integer until = tabuUntil.get(key);
        if (until == null) return false;
        return iteration < until;
    }

    private static String moveKey(Move move) {
        // Use affected slots as the key — different staff ids aren't tabu,
        // only the same slot reassignment is.
        StringBuilder sb = new StringBuilder();
        sb.append(move.type()).append(':');
        for (int s : move.affectedSlotIndices()) sb.append(s).append(',');
        return sb.toString();
    }
}