package com.hospital.scheduler.scheduling.score;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScoreDelta.
 */
class ScoreDeltaTest {

    @Test
    void testZeroDelta() {
        ScoreDelta delta = ScoreDelta.ZERO;
        
        assertEquals(0, delta.hardDelta());
        assertEquals(0, delta.coverageDelta(), 0.001);
        assertEquals(0, delta.cvDelta(), 0.001);
        assertEquals(0, delta.softDelta());
    }

    @Test
    void testAdd() {
        ScoreDelta a = ScoreDelta.builder()
                .hardDelta(1)
                .softDelta(2)
                .cvDelta(0.1)
                .build();
        
        ScoreDelta b = ScoreDelta.builder()
                .hardDelta(3)
                .softDelta(4)
                .cvDelta(0.2)
                .build();
        
        ScoreDelta result = a.add(b);
        
        assertEquals(4, result.hardDelta());
        assertEquals(6, result.softDelta());
        assertEquals(0.3, result.cvDelta(), 0.001);
    }

    @Test
    void testNegate() {
        ScoreDelta delta = ScoreDelta.builder()
                .hardDelta(5)
                .softDelta(3)
                .cvDelta(0.5)
                .build();
        
        ScoreDelta negated = delta.negate();
        
        assertEquals(-5, negated.hardDelta());
        assertEquals(-3, negated.softDelta());
        assertEquals(-0.5, negated.cvDelta(), 0.001);
    }

    @Test
    void testScale() {
        ScoreDelta delta = ScoreDelta.builder()
                .hardDelta(4)
                .softDelta(2)
                .cvDelta(0.4)
                .build();
        
        ScoreDelta scaled = delta.scale(2.0);
        
        assertEquals(8, scaled.hardDelta());
        assertEquals(4, scaled.softDelta());
        assertEquals(0.8, scaled.cvDelta(), 0.001);
    }

    @Test
    void testBuilder() {
        ScoreDelta delta = ScoreDelta.builder()
                .hardDelta(1)
                .softDelta(2)
                .coverageDelta(50.0)
                .cvDelta(0.1)
                .cvWeekendDelta(0.05)
                .gapDelta(3)
                .giniDelta(0.02)
                .build();
        
        assertEquals(1, delta.hardDelta());
        assertEquals(2, delta.softDelta());
        assertEquals(50.0, delta.coverageDelta(), 0.001);
        assertEquals(0.1, delta.cvDelta(), 0.001);
        assertEquals(0.05, delta.cvWeekendDelta(), 0.001);
        assertEquals(3, delta.gapDelta());
        assertEquals(0.02, delta.giniDelta(), 0.001);
    }

    @Test
    void testEquals() {
        ScoreDelta a = ScoreDelta.builder().hardDelta(1).softDelta(2).build();
        ScoreDelta b = ScoreDelta.builder().hardDelta(1).softDelta(2).build();
        ScoreDelta c = ScoreDelta.builder().hardDelta(1).softDelta(3).build();
        
        assertEquals(a, b);
        assertNotEquals(a, c);
    }
}
