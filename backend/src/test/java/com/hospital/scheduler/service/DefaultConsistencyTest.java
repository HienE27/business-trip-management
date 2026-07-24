package com.hospital.scheduler.service;

import com.hospital.scheduler.repository.AlgorithmConfigAuditRepository;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Verifies that all default-value code paths produce the same
 * <strong>canonical defaults</strong> per roadmap §10 Commit M:
 *
 * <pre>
 *   balanceScoreMin   = 0.70
 *   maxShiftsPerStaff = 12
 *   maxStaffPerShift  = 0
 *   maxShiftsPerDay   = 0
 *   overnightRecoveryHours = 24
 *   weekendWeight     = 2.0
 *   greedyCoverageThreshold = 0.85
 *   autoCompensationEnabled = true
 *   autoAdjustConfig  = true
 *   beamWidth         = 5
 * </pre>
 *
 * <p>When a new field is added to {@code AlgorithmRuntimeConfig} a corresponding
 * assertion should be added here, making default-drift immediately visible.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Canonical default consistency — no drift across code paths")
class DefaultConsistencyTest {

    @Mock private AlgorithmConfigRepository configRepository;
    @Mock private AlgorithmConfigAuditRepository auditRepository;

    @InjectMocks private AlgorithmConfigService configService;

    @BeforeEach
    void setUp() {
        // Empty DB cache → all getRuntimeConfig() calls fall back to code defaults
        lenient().when(configRepository.findAllAsKeyValuePairs()).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("getRuntimeConfig defaults match canonical values")
    void runtimeConfigDefaults() {
        var cfg = configService.getRuntimeConfig();

        assertThat(cfg.getBalanceScoreMin())
                .as("balanceScoreMin canonical default")
                .isEqualByComparingTo(BigDecimal.valueOf(0.70));

        assertThat(cfg.getMaxShiftsPerStaff())
                .as("maxShiftsPerStaff canonical default")
                .isEqualTo(12);

        assertThat(cfg.getMaxStaffPerShift())
                .as("maxStaffPerShift canonical default")
                .isEqualTo(0);

        assertThat(cfg.getMaxShiftsPerDay())
                .as("maxShiftsPerDay canonical default")
                .isEqualTo(0);

        assertThat(cfg.getOvernightRecoveryHours())
                .as("overnightRecoveryHours canonical default")
                .isEqualTo(24);

        assertThat(cfg.getWeekendWeight())
                .as("weekendWeight canonical default")
                .isEqualByComparingTo(BigDecimal.valueOf(2.0));

        assertThat(cfg.getGreedyCoverageThreshold())
                .as("greedyCoverageThreshold canonical default")
                .isEqualByComparingTo(BigDecimal.valueOf(0.85));

        assertThat(cfg.isAutoCompensationEnabled())
                .as("autoCompensationEnabled canonical default")
                .isTrue();

        assertThat(cfg.isAutoAdjustConfig())
                .as("autoAdjustConfig canonical default")
                .isTrue();

        assertThat(cfg.getBeamWidth())
                .as("beamWidth canonical default")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("getRuntimeConfig defaults for scorer weights match ScheduleQualityScorer originals")
    void scorerWeightDefaults() {
        var cfg = configService.getRuntimeConfig();

        assertThat(cfg.getCoverageWeight())
                .as("scorer_coverage_weight default")
                .isEqualByComparingTo(BigDecimal.valueOf(0.40));

        assertThat(cfg.getFairnessWeight())
                .as("scorer_fairness_weight default")
                .isEqualByComparingTo(BigDecimal.valueOf(0.35));

        assertThat(cfg.getConstraintWeight())
                .as("scorer_constraint_weight default")
                .isEqualByComparingTo(BigDecimal.valueOf(0.25));

        assertThat(cfg.getPassThreshold())
                .as("scorer_pass_threshold default")
                .isEqualTo(80.0);

        assertThat(cfg.getHardViolationPenalty())
                .as("scorer_hard_violation_penalty default")
                .isEqualTo(25.0);

        assertThat(cfg.getSoftViolationPenalty())
                .as("scorer_soft_violation_penalty default")
                .isEqualTo(5.0);

        assertThat(cfg.getTargetCv())
                .as("scorer_target_cv default")
                .isEqualTo(0.10);

        assertThat(cfg.getWorstCv())
                .as("scorer_worst_cv default")
                .isEqualTo(0.50);
    }

    @Test
    @DisplayName("getRuntimeConfig defaults for rebalance rounds match hard-coded originals")
    void rebalanceRoundDefaults() {
        var cfg = configService.getRuntimeConfig();

        assertThat(cfg.getRebalanceRoundsTotal())
                .as("rebalance_rounds_total default")
                .isEqualTo(80);

        assertThat(cfg.getRebalanceRoundsPerType())
                .as("rebalance_rounds_per_type default")
                .isEqualTo(30);

        assertThat(cfg.getRebalanceRoundsEg())
                .as("rebalance_rounds_eg default")
                .isEqualTo(40);

	        assertThat(cfg.getRebalanceRoundsPostSave())
	                .as("rebalance_rounds_post_save default")
	                .isEqualTo(100);
	    }

	    @Test
	    @DisplayName("AlgorithmRuntimeConfig builder() uses @Builder.Default for beamWidth=5")
	    void beamWidthBuilderDefault() {
	        var cfg = AlgorithmConfigService.AlgorithmRuntimeConfig.builder().build();
	        assertThat(cfg.getBeamWidth())
	                .as("beamWidth from bare builder (regression: missing @Builder.Default)")
	                .isEqualTo(5);
	    }
}
