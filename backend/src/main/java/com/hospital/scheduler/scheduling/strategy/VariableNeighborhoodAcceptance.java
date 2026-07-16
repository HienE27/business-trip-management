package com.hospital.scheduler.scheduling.strategy;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import java.util.List;
import java.util.function.Function;

/**
 * Variable Neighborhood Search. Chains several inner acceptors and switches
 * between them on a rotation schedule. The outer search loop is expected to
 * call {@link #rotate()} to advance the active neighborhood.
 */
public class VariableNeighborhoodAcceptance implements MoveAcceptanceStrategy {

    private final List<MoveAcceptanceStrategy> neighborhoods;
    private int cursor = 0;

    public VariableNeighborhoodAcceptance(List<MoveAcceptanceStrategy> neighborhoods) {
        this.neighborhoods = List.copyOf(neighborhoods);
    }

    @Override
    public AcceptanceStrategy kind() { return AcceptanceStrategy.VARIABLE_NEIGHBORHOOD_SEARCH; }

    @Override
    public void initialize(int estimatedIterations) {
        neighborhoods.forEach(n -> n.initialize(estimatedIterations));
    }

    public void rotate() {
        if (neighborhoods.isEmpty()) return;
        cursor = (cursor + 1) % neighborhoods.size();
    }

    public MoveAcceptanceStrategy current() {
        return neighborhoods.isEmpty() ? null : neighborhoods.get(cursor);
    }

    @Override
    public boolean evaluate(Move move, ScoreDelta delta, boolean improving) {
        MoveAcceptanceStrategy current = current();
        if (current == null) return improving;
        return current.evaluate(move, delta, improving);
    }

    @Override
    public void reset() {
        cursor = 0;
        neighborhoods.forEach(MoveAcceptanceStrategy::reset);
    }
}
