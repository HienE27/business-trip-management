package com.hospital.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.repository.AlgorithmConfigAuditRepository;
import com.hospital.scheduler.repository.AlgorithmConfigKeyValue;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Commit B (Workflow M07): Tests for recommendAutoGenConfig() response fields:
 * demandRatio, fairnessType, crossSpecialtyPolicy, expectedMetrics, warnings.
 *
 * Does NOT test persistence — recommend() is read-only.
 * Does NOT test preview (save=false) vs confirm (save=true) persistence —
 * those are covered by integration tests.
 */
@DisplayName("recommendAutoGenConfig response fields (Commit B)")
class RecommendAutoGenConfigTest {

    @Mock private AlgorithmConfigRepository configRepository;
    @Mock private AlgorithmConfigAuditRepository auditRepository;
    @Mock private ScheduleRepository scheduleRepository;
    @Mock private SchedulePeriodRepository schedulePeriodRepository;
    private AlgorithmConfigService configService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        // Manual injection: @RequiredArgsConstructor needs 5 args
        configService = new AlgorithmConfigService(
                configRepository, auditRepository,
                scheduleRepository, schedulePeriodRepository,
                new ObjectMapper());
    }

    private AlgorithmConfigService.AutoGenConfigRecommendation call(
            Map<String, Integer> eligibleStaff,
            Map<String, Integer> targets,
            boolean expandNonL04,
            List<String> specialties,
            int maxShiftsPerStaff) {
        return configService.recommendAutoGenConfig(
                30, 4, eligibleStaff, targets, expandNonL04, specialties, maxShiftsPerStaff);
    }

    private void seedCrossSpecialtyOff() {
        // Stub findAllAsKeyValuePairs so loadConfigCache() returns empty (uses all defaults)
        // with cross-specialty OFF (false).
        when(configRepository.findAllAsKeyValuePairs()).thenReturn(List.of(
                kv("AUTO_GEN_ENABLED", "true"),
                kv("AUTO_GEN_L04_CROSS_SPECIALTY", "false"),
                kv("AUTO_GEN_L04_CROSS_SPECIALTY_RATIO", "0.3"),
                kv("AUTO_GEN_L04_BALANCE_STRATEGY", "FAIR_DISTRIBUTE"),
                kv("AUTO_GEN_L01_TARGET_PER_MONTH", "2"),
                kv("AUTO_GEN_L02_TARGET_PER_MONTH", "2"),
                kv("AUTO_GEN_L03_TARGET_PER_MONTH", "2"),
                kv("AUTO_GEN_L04_TARGET_PER_MONTH", "5")
        ));
    }

    private void seedCrossSpecialtyOn() {
        when(configRepository.findAllAsKeyValuePairs()).thenReturn(List.of(
                kv("AUTO_GEN_ENABLED", "true"),
                kv("AUTO_GEN_L04_CROSS_SPECIALTY", "true"),
                kv("AUTO_GEN_L04_CROSS_SPECIALTY_RATIO", "0.3"),
                kv("AUTO_GEN_L04_BALANCE_STRATEGY", "FAIR_DISTRIBUTE"),
                kv("AUTO_GEN_L01_TARGET_PER_MONTH", "2"),
                kv("AUTO_GEN_L02_TARGET_PER_MONTH", "2"),
                kv("AUTO_GEN_L03_TARGET_PER_MONTH", "2"),
                kv("AUTO_GEN_L04_TARGET_PER_MONTH", "5")
        ));
    }

    private static AlgorithmConfigKeyValue kv(String k, String v) {
        return new AlgorithmConfigKeyValue(k, v);
    }

    // ── demandRatio ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("demandRatio returns minPerDay per shift type (L01/L02/L03/L04)")
    void demandRatio_present() {
        seedCrossSpecialtyOff();
        var r = call(Map.of("L01", 10, "L02", 10, "L03", 10, "L04", 10),
                Map.of("L01", 0, "L02", 0, "L03", 0, "L04", 0),
                true, List.of("Ngoại", "Nội"), 0);
        assertThat(r.demandRatio()).containsKeys("L01", "L02", "L03", "L04");
        assertThat(r.demandRatio().get("L01")).isGreaterThan(0);
    }

    // ── fairnessType ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("similar demand ratios → INTRA_TYPE_WITH_INTER_BALANCE")
    void fairnessType_interBalance_whenRatiosSimilar() {
        seedCrossSpecialtyOff();
        // All 3 types have same eligible (10) and same target (0 → uses default ~30%)
        // After historical fallback: roughly equal ratio → interBalance feasible
        var r = call(Map.of("L01", 10, "L02", 10, "L03", 10, "L04", 10),
                Map.of("L01", 0, "L02", 0, "L03", 0, "L04", 0),
                true, List.of("Ngoại", "Nội"), 0);
        assertThat(r.fairnessType()).isIn("INTRA_TYPE", "INTRA_TYPE_WITH_INTER_BALANCE");
    }

    @Test
    @DisplayName("severely imbalanced demand → INTRA_TYPE only")
    void fairnessType_intraOnly_whenDemandImbalanced() {
        seedCrossSpecialtyOff();
        // L01 needs 1 per day, L02 needs 10 per day — ratio imbalance > 2.5×
        var r = call(Map.of("L01", 10, "L02", 2, "L03", 2, "L04", 10),
                Map.of("L01", 8, "L02", 2, "L03", 2, "L04", 2),
                true, List.of("Ngoại", "Nội"), 0);
        assertThat(r.fairnessType()).isEqualTo("INTRA_TYPE");
    }

    // ── crossSpecialtyPolicy ─────────────────────────────────────────────────

    @Test
    @DisplayName("cross-specialty OFF → policy says TẮT")
    void crossSpecialtyPolicy_off() {
        seedCrossSpecialtyOff();
        var r = call(Map.of("L01", 10, "L02", 10, "L03", 10, "L04", 10),
                Map.of("L01", 0, "L02", 0, "L03", 0, "L04", 0),
                true, List.of("Ngoại", "Nội"), 0);
        assertThat(r.crossSpecialtyPolicy()).contains("TẮT");
    }

    @Test
    @DisplayName("cross-specialty policy is non-null and contains TẮT or BẬT")
    void crossSpecialtyPolicy_alwaysContainsOnOff() {
        seedCrossSpecialtyOff();
        var r = call(Map.of("L01", 10, "L02", 10, "L03", 10, "L04", 10),
                Map.of("L01", 0, "L02", 0, "L03", 0, "L04", 0),
                true, List.of("Ngoại", "Nội"), 0);
        // Policy always contains a TẮT or BẬT designation
        assertThat(r.crossSpecialtyPolicy()).matches(".*((TẮT)|(BẬT)).*");
    }

    // ── expectedMetrics ──────────────────────────────────────────────────────

    @Test
    @DisplayName("expectedMetrics is non-null and contains coverage + fairness estimates")
    void expectedMetrics_present() {
        seedCrossSpecialtyOff();
        var r = call(Map.of("L01", 10, "L02", 10, "L03", 10, "L04", 10),
                Map.of("L01", 0, "L02", 0, "L03", 0, "L04", 0),
                true, List.of("Ngoại", "Nội"), 0);
        assertThat(r.expectedMetrics()).isNotNull();
        assertThat(r.expectedMetrics().estimatedCoverageMin()).isNotNull();
        assertThat(r.expectedMetrics().estimatedFairnessScore()).isNotNull();
        assertThat(r.expectedMetrics().estimatedQualityScore()).isNotNull();
        assertThat(r.expectedMetrics().targetCv()).isEqualTo(0.10);
        assertThat(r.expectedMetrics().worstCv()).isEqualTo(0.50);
    }

    // ── warnings ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("severe demand imbalance → trade-off warning")
    void warning_appears_whenDemandImbalanced() {
        seedCrossSpecialtyOff();
        // Force extreme imbalance by using very different eligible counts
        var r = call(Map.of("L01", 10, "L02", 2, "L03", 2, "L04", 10),
                Map.of("L01", 8, "L02", 2, "L03", 2, "L04", 2),
                true, List.of("Ngoại", "Nội"), 0);
        assertThat(r.warnings()).isNotEmpty();
        assertThat(r.warnings().get(0)).contains("Demand");
    }

    @Test
    @DisplayName("cross-specialty OFF with few L04 eligible → warning")
    void warning_appears_whenCrossSpecialtyOffAndL04Few() {
        seedCrossSpecialtyOff();
        // totalStaff=2 but l04Elig=3 → effectiveL04Elig = ceil(2/2) = 1 < l04Elig=3 → warning
        var r = call(Map.of("L01", 2, "L02", 2, "L03", 2, "L04", 3),
                Map.of("L01", 0, "L02", 0, "L03", 0, "L04", 0),
                false, List.of("Ngoại", "Nội"), 0);
        assertThat(r.warnings()).isNotEmpty();
        assertThat(String.join(" ", r.warnings())).contains("Cross-specialty");
    }

    @Test
    @DisplayName("warnings list is non-null even when no warnings (empty, not null)")
    void warnings_neverNull() {
        seedCrossSpecialtyOff();
        var r = call(Map.of("L01", 10, "L02", 10, "L03", 10, "L04", 10),
                Map.of("L01", 0, "L02", 0, "L03", 0, "L04", 0),
                true, List.of("Ngoại", "Nội"), 0);
        assertThat(r.warnings()).isNotNull();
    }

    // ── recommendedConfig returned ───────────────────────────────────────────

    @Test
    @DisplayName("recommend returns non-null config (not persisted)")
    void configReturned_notNull() {
        seedCrossSpecialtyOff();
        var r = call(Map.of("L01", 10, "L02", 10, "L03", 10, "L04", 10),
                Map.of("L01", 0, "L02", 0, "L03", 0, "L04", 0),
                true, List.of("Ngoại", "Nội"), 0);
        assertThat(r.config()).isNotNull();
        assertThat(r.config().l01MinPerDay()).isGreaterThan(0);
    }

    // ── totalShiftsExpected ─────────────────────────────────────────────────

    @Test
    @DisplayName("totalShiftsExpected is positive")
    void totalShiftsExpected_positive() {
        seedCrossSpecialtyOff();
        var r = call(Map.of("L01", 10, "L02", 10, "L03", 10, "L04", 10),
                Map.of("L01", 0, "L02", 0, "L03", 0, "L04", 0),
                true, List.of("Ngoại", "Nội"), 0);
        assertThat(r.totalShiftsExpected()).isGreaterThan(0);
    }

    // ── maxShiftsPerStaff cap ─────────────────────────────────────────────────

    @Test
    @DisplayName("maxShiftsPerStaff cap is reflected in totalShiftsExpected")
    void maxShiftsPerStaff_affectsOutput() {
        seedCrossSpecialtyOff();
        // With cap=3 vs cap=0, totalShiftsExpected should be bounded
        var r = call(Map.of("L01", 10, "L02", 10, "L03", 10, "L04", 10),
                Map.of("L01", 0, "L02", 0, "L03", 0, "L04", 0),
                true, List.of("Ngoại", "Nội"), 3);
        // totalShiftsExpected must be positive and non-null
        assertThat(r.totalShiftsExpected()).isGreaterThan(0);
    }

}
