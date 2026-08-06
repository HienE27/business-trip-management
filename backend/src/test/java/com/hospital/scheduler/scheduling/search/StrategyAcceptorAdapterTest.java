package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.strategy.HillClimbingAcceptance;
import com.hospital.scheduler.scheduling.strategy.SimulatedAnnealingAcceptance;
import com.hospital.scheduler.scheduling.strategy.StrategyConfig;
import com.hospital.scheduler.scheduling.strategy.StrategyFactory;
import com.hospital.scheduler.scheduling.strategy.StrategyProperties;
import com.hospital.scheduler.scheduling.strategy.TabuAcceptance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR-12-01: acceptance strategy adapter wiring tests.
 *
 * <p>Verifies the bridge between the v11 strategy API
 * ({@code scheduling.strategy}) and the v10 search-loop API
 * ({@code scheduling.search}).
 */
class StrategyAcceptorAdapterTest {

    // ── Adapter ───────────────────────────────────────────────────────────────

    @Test
    void hillClimbing_acceptsOnlyImprovingMoves() {
        MoveAcceptor acceptor = new StrategyAcceptorAdapter(new HillClimbingAcceptance());
        acceptor.initialize(100);

        ScoreDelta neutral = ScoreDelta.zero();
        assertTrue(acceptor.accept(neutral, 0, true),
                "improving moves are accepted by aspiration");
        assertFalse(acceptor.accept(neutral, 0, false),
                "sideways moves are rejected under hill-climbing");
    }

    @Test
    void hillClimbing_isTabuAlwaysFalse() {
        MoveAcceptor acceptor = new StrategyAcceptorAdapter(new HillClimbingAcceptance());
        assertFalse(acceptor.isTabu(null, 0),
                "HillClimbing never marks moves tabu");
        assertDoesNotThrow(() -> acceptor.rememberApplied(null, 0));
    }

    @Test
    void simulatedAnnealing_acceptsImprovingAndSideways() {
        MoveAcceptor acceptor = new StrategyAcceptorAdapter(
                new SimulatedAnnealingAcceptance(1000.0, 0.99, 1.0));
        acceptor.initialize(100);

        assertTrue(acceptor.accept(neutralDelta(), 0, true));
        for (int i = 0; i < 50; i++) {
            acceptor.accept(neutralDelta(), i, false);
        }
    }

    @Test
    void tabuAcceptance_isTabuAndRememberAppliedRoundTrip() {
        TabuAcceptance tabu = new TabuAcceptance(5, 10);
        MoveAcceptor acceptor = new StrategyAcceptorAdapter(tabu);
        acceptor.initialize(100);

        Move move = stubMove(1);
        assertFalse(acceptor.isTabu(move, 0));
        acceptor.rememberApplied(move, 0);
        assertTrue(acceptor.isTabu(move, 5),
                "move should be tabu within tenure window");
        assertFalse(acceptor.isTabu(move, 1000),
                "tenure expired → no longer tabu");
    }

    @Test
    void nonTabuStrategy_isTabuAndRememberAppliedAreSafeNoOps() {
        MoveAcceptor acceptor = new StrategyAcceptorAdapter(new HillClimbingAcceptance());
        Move move = stubMove(1);
        assertFalse(acceptor.isTabu(move, 0));
        assertDoesNotThrow(() -> acceptor.rememberApplied(move, 0));
    }

    @Test
    void initializeAndResetForwardToStrategy() {
        TabuAcceptance tabu = new TabuAcceptance(5, 10);
        MoveAcceptor acceptor = new StrategyAcceptorAdapter(tabu);

        acceptor.initialize(100);
        Move move = stubMove(1);
        acceptor.rememberApplied(move, 0);
        assertTrue(acceptor.isTabu(move, 3));

        acceptor.reset();
        assertFalse(acceptor.isTabu(move, 3),
                "reset must clear tabu state via the strategy");
    }

    @Test
    void rejectsNullDelegate() {
        assertThrows(IllegalArgumentException.class,
                () -> new StrategyAcceptorAdapter(null));
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    @Test
    void factory_buildsAcceptorFromProperties() {
        StrategyProperties props = new StrategyProperties();
        props.setStrategy("HILL_CLIMBING");

        MoveAcceptor acceptor = StrategyAcceptorFactory.build(props, 100);
        assertNotNull(acceptor);
        assertTrue(acceptor.accept(neutralDelta(), 0, true));
        assertFalse(acceptor.accept(neutralDelta(), 0, false));
    }

    @Test
    void factory_buildsTabuAcceptorFromProperties() {
        StrategyProperties props = new StrategyProperties();
        props.setStrategy("TABU");
        props.setTabuTenureMin(5);
        props.setTabuTenureMax(10);

        MoveAcceptor acceptor = StrategyAcceptorFactory.build(props, 100);
        Move move = stubMove(1);
        acceptor.rememberApplied(move, 0);
        assertTrue(acceptor.isTabu(move, 3));
    }

    @Test
    void factory_buildsAcceptorFromExplicitConfig() {
        StrategyConfig config = StrategyConfig.simulatedAnnealing();
        MoveAcceptor acceptor = StrategyAcceptorFactory.build(config, 100);
        assertNotNull(acceptor);
        assertTrue(acceptor.accept(neutralDelta(), 0, true));
    }

    @Test
    void factory_rejectsNullArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> StrategyAcceptorFactory.build((StrategyProperties) null, 100));
        assertThrows(IllegalArgumentException.class,
                () -> StrategyAcceptorFactory.build((StrategyConfig) null, 100));
    }

    @Test
    void factory_vnsComposesNeighborhoods() {
        StrategyConfig vns = StrategyConfig.vns(java.util.List.of(
                StrategyConfig.hillClimbing(),
                StrategyConfig.tabu()));
        MoveAcceptor acceptor = StrategyAcceptorFactory.build(vns, 100);
        assertNotNull(acceptor);
        assertTrue(acceptor.accept(neutralDelta(), 0, true));
    }

    @Test
    void factory_directBuildMatchesAdapter() {
        StrategyConfig config = StrategyConfig.tabu();
        MoveAcceptor viaFactory = StrategyAcceptorFactory.build(config, 100);
        MoveAcceptor viaAdapter = new StrategyAcceptorAdapter(StrategyFactory.build(config));
        viaAdapter.initialize(100);

        Move move = stubMove(1);
        assertEquals(viaAdapter.isTabu(move, 0), viaFactory.isTabu(move, 0));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ScoreDelta neutralDelta() {
        return ScoreDelta.zero();
    }

    private static Move stubMove(int slotIdx) {
        return new Move() {
            @Override
            public com.hospital.scheduler.scheduling.move.Move.MoveType type() {
                return Move.MoveType.SWAP;
            }
            @Override
            public void doMove(WorkingSolution s) { /* no-op */ }
            @Override
            public void undo(WorkingSolution s) { /* no-op */ }
            @Override
            public int[] affectedSlotIndices() { return new int[]{slotIdx}; }
            @Override
            public int[] affectedStaffIndices() { return new int[]{1}; }
        };
    }
}