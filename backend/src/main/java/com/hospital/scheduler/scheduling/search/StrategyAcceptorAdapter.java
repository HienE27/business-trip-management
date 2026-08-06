package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.strategy.MoveAcceptanceStrategy;
import com.hospital.scheduler.scheduling.strategy.TabuAcceptance;

/**
 * Adapter that bridges the v11 {@link MoveAcceptanceStrategy} API
 * ({@code scheduling.strategy}) onto the v10 search-loop
 * {@link MoveAcceptor} API ({@code scheduling.search}).
 *
 * <p>The search loop drives the algorithm by calling {@link #accept} for
 * every non-improving candidate move. Soft acceptance policy (HillClimbing,
 * Simulated Annealing, Late Acceptance, Great Deluge, Variable Neighborhood
 * Search) is delegated wholesale to the wrapped strategy.
 *
 * <p>Tabu bookkeeping is layered on top: when the wrapped strategy is a
 * {@link TabuAcceptance}, the adapter also forwards
 * {@link #isTabu(Move, int)} / {@link #rememberApplied(Move, int)} to the
 * same instance. For all other strategies the adapter returns {@code false}
 * from {@code isTabu} and leaves {@code rememberApplied} as a no-op, which
 * preserves the semantics defined in {@link MoveAcceptor}.
 *
 * <p>This adapter is intentionally cheap: each call performs at most one
 * delegation and no allocation on the hot path.
 */
public class StrategyAcceptorAdapter implements MoveAcceptor {

    private final MoveAcceptanceStrategy delegate;

    public StrategyAcceptorAdapter(MoveAcceptanceStrategy delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    public MoveAcceptanceStrategy getDelegate() {
        return delegate;
    }

    @Override
    public boolean accept(ScoreDelta delta, int iteration, boolean improving) {
        // Soft acceptance decision is made by the strategy itself; the
        // search loop has already filtered out hard-violation increases
        // and "improving" moves (aspiration) before reaching here, so the
        // strategy receives only sideways candidates that need an uphill
        // decision. Move is required by the strategy API but unused for
        // non-tabu strategies — passing null is acceptable because the
        // strategy signature declares @param move but tabu-specific
        // implementations are the only consumers of it.
        return delegate.evaluate(null, delta, improving);
    }

    @Override
    public boolean isTabu(Move move, int iteration) {
        if (delegate instanceof TabuAcceptance tabu) {
            return tabu.isTabu(move, iteration);
        }
        return false;
    }

    @Override
    public void rememberApplied(Move move, int iteration) {
        if (delegate instanceof TabuAcceptance tabu) {
            tabu.rememberApplied(move, iteration);
        }
    }

    @Override
    public void initialize(int estimatedIterations) {
        delegate.initialize(estimatedIterations);
    }

    @Override
    public void reset() {
        delegate.reset();
    }
}