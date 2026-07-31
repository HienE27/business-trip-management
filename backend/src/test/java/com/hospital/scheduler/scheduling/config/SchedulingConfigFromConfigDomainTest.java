package com.hospital.scheduler.scheduling.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BUGFIX (M08-DBCONFIG-V10): verifies DB-driven {@link SchedulingConfig}
 * construction — UI edits to {@code scheduling_*} params must reach the
 * v10 search layer via {@link SchedulingConfig#from(ConfigDomain)}.
 */
class SchedulingConfigFromConfigDomainTest {

    @Test
    void from_ConfigDomain_mapsAllSharedFields() {
        ConfigDomain cfg = ConfigDefaults.withDefaults().builder().from(ConfigDefaults.withDefaults())
                .maxIterations(1200)
                .candidateListSize(80)
                .neighborhoodSize(25)
                .tabuTenureMin(3)
                .tabuTenureMax(9)
                .maxNoImproveIterations(100)
                .timeLimitSeconds(30)
                .cvTarget(0.05)
                .cvWorst(0.40)
                .relativeImprovementThreshold(0.01)
                .diversifyAfterIterations(5)
                .build();

        SchedulingConfig sc = SchedulingConfig.from(cfg);

        assertEquals(1200, sc.getSearch().getMaxIterations());
        assertEquals(80, sc.getSearch().getCandidateListSize());
        assertEquals(25, sc.getSearch().getNeighborhoodSize());
        assertEquals(3, sc.getSearch().getTabuTenureMin());
        assertEquals(9, sc.getSearch().getTabuTenureMax());
        assertEquals(100, sc.getSearch().getMaxNoImprove());
        assertEquals(30, sc.getSearch().getTimeLimitSeconds());
        assertEquals(0.05, sc.getFairness().getCvTarget());
        assertEquals(0.40, sc.getFairness().getCvWorst());
        assertEquals(0.01, sc.getTermination().getRelativeImprovementThreshold());
        assertEquals(5, sc.getTermination().getDiversifyAfter());
    }

    @Test
    void from_ZeroFields_keepsJavaDefaults() {
        ConfigDomain cfg = ConfigDomain.builder().build(); // all 0 / false

        SchedulingConfig sc = SchedulingConfig.from(cfg);

        assertEquals(500, sc.getSearch().getMaxIterations());
        assertEquals(50, sc.getSearch().getCandidateListSize());
        assertEquals(10, sc.getSearch().getNeighborhoodSize());
        assertEquals(60, sc.getSearch().getTimeLimitSeconds());
        assertEquals(0.10, sc.getFairness().getCvTarget());
        assertEquals(0.001, sc.getTermination().getRelativeImprovementThreshold());
    }
}
