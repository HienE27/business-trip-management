package com.hospital.scheduler.scheduling.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SchedulingConfig.
 */
class SchedulingConfigTest {

    private SchedulingConfig config;

    @BeforeEach
    void setUp() {
        config = new SchedulingConfig();
    }

    @Test
    void testDefaultSearchConfig() {
        assertEquals(50, config.getSearch().getCandidateListSize());
        assertEquals(10, config.getSearch().getNeighborhoodSize());
        assertEquals(5, config.getSearch().getTabuTenureMin());
        assertEquals(10, config.getSearch().getTabuTenureMax());
        assertEquals(500, config.getSearch().getMaxIterations());
        assertEquals(50, config.getSearch().getMaxNoImprove());
        assertEquals(60, config.getSearch().getTimeLimitSeconds());
    }

    @Test
    void testDefaultFairnessConfig() {
        assertEquals(0.10, config.getFairness().getCvTarget());
        assertEquals(0.50, config.getFairness().getCvWorst());
        assertEquals(0.40, config.getFairness().getCoverageWeight());
        assertEquals(0.35, config.getFairness().getFairnessWeight());
        assertEquals(0.25, config.getFairness().getConstraintWeight());
    }

    @Test
    void testWeightsSumToOne() {
        double sum = config.getFairness().getCoverageWeight()
                + config.getFairness().getFairnessWeight()
                + config.getFairness().getConstraintWeight();
        assertEquals(1.0, sum, 0.001);
    }

    @Test
    void testSetters() {
        config.getSearch().setCandidateListSize(100);
        config.getSearch().setTimeLimitSeconds(120);
        config.getFairness().setCvTarget(0.15);

        assertEquals(100, config.getSearch().getCandidateListSize());
        assertEquals(120, config.getSearch().getTimeLimitSeconds());
        assertEquals(0.15, config.getFairness().getCvTarget());
    }
}
