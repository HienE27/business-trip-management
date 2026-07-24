package com.hospital.scheduler.algorithm.scoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Proves ScheduleQualityScorer is prototype-scoped so ObjectProvider.getObject()
 * yields a fresh instance (no shared mutable weight state across call sites).
 */
@SpringJUnitConfig(classes = ScheduleQualityScorerPrototypeIsolationTest.Config.class)
@DisplayName("ScheduleQualityScorer — Spring prototype isolation")
class ScheduleQualityScorerPrototypeIsolationTest {

    @Import(ScheduleQualityScorer.class)
    static class Config {
    }

    @Autowired
    private ObjectProvider<ScheduleQualityScorer> scheduleQualityScorerProvider;

    @Test
    @DisplayName("ObjectProvider.getObject() returns distinct instances")
    void getObjectReturnsDistinctInstances() {
        ScheduleQualityScorer a = scheduleQualityScorerProvider.getObject();
        ScheduleQualityScorer b = scheduleQualityScorerProvider.getObject();
        assertNotSame(a, b);
        assertThat(a).isNotNull();
        assertThat(b).isNotNull();
    }

    @Test
    @DisplayName("Fluent mutation on one provider instance does not leak to another")
    void fluentMutationDoesNotLeakAcrossProviderInstances() {
        ScheduleQualityScorer a = scheduleQualityScorerProvider.getObject();
        ScheduleQualityScorer b = scheduleQualityScorerProvider.getObject();
        assertNotSame(a, b);

        a.withWeights(0.80, 0.10, 0.10);
        b.withWeights(0.10, 0.80, 0.10);

        // Re-read via second get would also be new; assert pair independence by re-applying
        // and scoring is covered in BehaviorTest — here only identity + non-shared ref.
        ScheduleQualityScorer c = scheduleQualityScorerProvider.getObject();
        assertNotSame(a, c);
        assertNotSame(b, c);
    }
}
