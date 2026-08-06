package com.hospital.scheduler.scheduling.diversifier;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link NoImproveRestartStrategy}. */
class NoImproveRestartStrategyTest {

    @Test
    void returnsContinueWhenBelowThreshold() {
        NoImproveRestartStrategy s = new NoImproveRestartStrategy(50, 3);
        assertEquals(DiversifierSignal.CONTINUE,
                s.decide(null, null, 30, 30));
    }

    @Test
    void returnsRestartWhenAboveThreshold() {
        NoImproveRestartStrategy s = new NoImproveRestartStrategy(50, 3);
        assertEquals(DiversifierSignal.RESTART,
                s.decide(null, null, 51, 51));
    }

    @Test
    void returnsContinueWhenMaxRestartsExhausted() {
        NoImproveRestartStrategy s = new NoImproveRestartStrategy(10, 1);
        assertEquals(DiversifierSignal.RESTART, s.decide(null, null, 11, 11));
        assertEquals(DiversifierSignal.CONTINUE, s.decide(null, null, 12, 12));
        assertEquals(DiversifierSignal.CONTINUE, s.decide(null, null, 100, 100));
    }

    @Test
    void shakingStrengthTriggersShakeSignal() {
        NoImproveRestartStrategy s = new NoImproveRestartStrategy(10, 3, 5);
        assertEquals(DiversifierSignal.SHAKE, s.decide(null, null, 11, 11));
    }

    @Test
    void resetClearsRestartCount() {
        NoImproveRestartStrategy s = new NoImproveRestartStrategy(5, 1);
        assertEquals(DiversifierSignal.RESTART, s.decide(null, null, 6, 6));
        s.reset();
        assertEquals(DiversifierSignal.RESTART, s.decide(null, null, 6, 6));
    }

    @Test
    void gettersReturnConfiguredValues() {
        NoImproveRestartStrategy s = new NoImproveRestartStrategy(25, 4, 7);
        assertEquals(25, s.getNoImproveThreshold());
        assertEquals(4, s.getMaxRestarts());
        assertEquals(7, s.getShakingStrength());
    }
}
