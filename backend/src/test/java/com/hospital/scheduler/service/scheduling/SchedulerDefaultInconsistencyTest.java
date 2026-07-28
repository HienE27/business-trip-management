package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.repository.AlgorithmConfigAuditRepository;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import com.hospital.scheduler.service.AlgorithmConfigCrudService;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.AutoGenConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H1 verification test — Default inconsistency between scheduler paths.
 *
 * <p>When the algorithm_config table is EMPTY (no rows), there are two code paths
 * that read L04 cross-specialty config:
 *
 * <ol>
 *   <li>{@link AutoGenConfigService#getAutoGenConfig()} → builds {@link AutoGenConfig}
 *       with {@code getBooleanValue(AUTO_GEN_L04_CROSS_SPECIALTY, false, cache)}.
 *       When cache is empty → raw=null → returns {@code false} (getBooleanValue ignores
 *       defaultValue and returns false on null).
 *   <li>{@link StaffEligibilityFilter#getCrossSpecialtyConfig(String)} →
 *       when {@code algorithmConfigService.getAutoGenConfig()} returns empty
 *       → falls back to {@link StaffEligibilityFilter.CrossSpecialtyConfig#defaultEnabled()}.
 * </ol>
 *
 * <p>If these two defaults disagree, a first-time user who has never saved any config
 * will see scheduler behave differently from what the defaults imply.
 *
 * @see <a href="https://github.com/...">H1 - High-risk hypothesis</a>
 */
@DisplayName("H1 — Scheduler / UI default inconsistency")
class SchedulerDefaultInconsistencyTest {

    // ─── Path 1: AutoGenConfigService (used by scheduler via getAutoGenConfig) ──

    @Nested
    @DisplayName("Path 1: AutoGenConfigService")
    class Path1_AutoGenConfigService {

	    @Test
	    @DisplayName("empty cache → l04CrossSpecialty enabled = false (aligned with UI default)")
	    void emptyCache_l04CrossSpecialtyEnabled() {
	        AlgorithmConfigCrudService crud = new MockCrud(Map.of());
	        AutoGenConfigService service = new AutoGenConfigService(crud);

	        var config = service.getAutoGenConfig();

	        assertThat(config).isPresent();
	        assertThat(config.get().l04CrossSpecialty())
	                .as("After H1 fix: empty DB → cross OFF by default, aligned with UI (ConfigMapper default=false)")
	                .isFalse();
	    }

        @Test
        @DisplayName("empty cache → l04CrossSpecialtyRatio = 0.5f")
        void emptyCache_l04CrossSpecialtyRatioDefault() {
            AlgorithmConfigCrudService crud = new MockCrud(Map.of());
            AutoGenConfigService service = new AutoGenConfigService(crud);

            var config = service.getAutoGenConfig();

            assertThat(config).isPresent();
            assertThat(config.get().l04CrossSpecialtyRatio()).isEqualTo(0.5f);
        }

        @Test
        @DisplayName("empty cache → l04AllowedSpecialties = empty (means all)")
        void emptyCache_l04AllowedSpecialtiesAll() {
            AlgorithmConfigCrudService crud = new MockCrud(Map.of());
            AutoGenConfigService service = new AutoGenConfigService(crud);

            var config = service.getAutoGenConfig();

            assertThat(config).isPresent();
            assertThat(config.get().l04AllowedSpecialties()).isEmpty();
        }
    }

    // ─── Path 2: StaffEligibilityFilter fallback ────────────────────────────

    @Nested
    @DisplayName("Path 2: StaffEligibilityFilter (scheduler runtime eligibility)")
    class Path2_StaffEligibilityFilter {

	    @Test
	    @DisplayName("getCrossSpecialtyConfig(L04) → enabled = false when config missing")
	    void missingConfig_returnsDefaultEnabled() {
	        var cfg = StaffEligibilityFilter.CrossSpecialtyConfig.defaultEnabled();

	        assertThat(cfg.enabled()).isFalse();
	        assertThat(cfg.ratio()).isEqualTo(0.5f);
	        assertThat(cfg.allowedSpecialties()).isEmpty();
	        assertThat(cfg.balanceStrategy()).isEqualTo("FAIR_DISTRIBUTE");
	    }
    }

    // ─── THE INCONSISTENCY TEST ─────────────────────────────────────────────

    @Nested
    @DisplayName("H1 — Inconsistency between two paths")
    class H1_Inconsistency {

	    @Test
	    @DisplayName("After H1 fix: AutoGenConfigService=false matches StaffEligibilityFilter.defaultEnabled=false → CONSISTENT")
	    void l04CrossSpecialtyEnabled_consistent_afterFix() {
	        AlgorithmConfigCrudService crud = new MockCrud(Map.of());
	        AutoGenConfigService service = new AutoGenConfigService(crud);
	        var autoGen = service.getAutoGenConfig();

	        var filterDefault = StaffEligibilityFilter.CrossSpecialtyConfig.defaultEnabled();

	        assertThat(autoGen).isPresent();
	        // After H1 fix: both paths return false when DB is empty (aligned with UI)
	        assertThat(autoGen.get().l04CrossSpecialty())
	                .as("AutoGenConfigService now aligns with StaffEligibilityFilter.defaultEnabled() — both false")
	                .isEqualTo(filterDefault.enabled());
        }

        @Test
        @DisplayName("l04CrossSpecialtyRatio: both paths return 0.5f → CONSISTENT")
        void l04CrossSpecialtyRatio_consistent() {
            AlgorithmConfigCrudService crud = new MockCrud(Map.of());
            AutoGenConfigService service = new AutoGenConfigService(crud);
            var autoGen = service.getAutoGenConfig();
            var filterDefault = StaffEligibilityFilter.CrossSpecialtyConfig.defaultEnabled();

            assertThat(autoGen).isPresent();
            assertThat(autoGen.get().l04CrossSpecialtyRatio())
                    .isEqualTo(filterDefault.ratio());
        }

        @Test
        @DisplayName("l04AllowedSpecialties: both paths return empty → CONSISTENT")
        void l04AllowedSpecialties_consistent() {
            AlgorithmConfigCrudService crud = new MockCrud(Map.of());
            AutoGenConfigService service = new AutoGenConfigService(crud);
            var autoGen = service.getAutoGenConfig();
            var filterDefault = StaffEligibilityFilter.CrossSpecialtyConfig.defaultEnabled();

            assertThat(autoGen).isPresent();
            assertThat(autoGen.get().l04AllowedSpecialties())
                    .isEqualTo(filterDefault.allowedSpecialties());
        }

        @Test
        @DisplayName("After saving config with enabled=false → AutoGenConfigService returns false")
        void savedDisabledConfig_isHonored() {
            Map<String, String> savedConfig = Map.of(
                    "auto_gen_l04_cross_specialty", "false",
                    "auto_gen_l04_cross_specialty_ratio", "0.3",
                    "auto_gen_l04_allowed_specialties", "Ngoại,Nội"
            );
            AlgorithmConfigCrudService crud = new MockCrud(savedConfig);
            AutoGenConfigService service = new AutoGenConfigService(crud);
            var config = service.getAutoGenConfig();

            assertThat(config).isPresent();
            assertThat(config.get().l04CrossSpecialty()).isFalse();
            assertThat(config.get().l04CrossSpecialtyRatio()).isEqualTo(0.3f);
        }

	    @Test
	    @DisplayName("After saving config with enabled=true → AutoGenConfigService returns true")
	    void savedEnabledConfig_bothPathsAgree() {
	        Map<String, String> savedConfig = Map.of(
	                "auto_gen_l04_cross_specialty", "true",
	                "auto_gen_l04_cross_specialty_ratio", "0.7"
	        );
	        AlgorithmConfigCrudService crud = new MockCrud(savedConfig);
	        AutoGenConfigService service = new AutoGenConfigService(crud);
	        var config = service.getAutoGenConfig();

	        assertThat(config).isPresent();
	        // Saved value takes precedence over default
	        assertThat(config.get().l04CrossSpecialty()).isTrue();
	        assertThat(config.get().l04CrossSpecialtyRatio()).isEqualTo(0.7f);
	    }
    }

    // ─── Mock AlgorithmConfigCrudService ───────────────────────────────────

    /**
     * Minimal in-memory mock of {@link AlgorithmConfigCrudService}
     * that overrides only the read methods used in this test.
     * Does NOT extend AlgorithmConfigCrudService to avoid constructor issues —
     * instead implements the interface-like behavior by passing mock data directly.
     */
    private static class MockCrud extends AlgorithmConfigCrudService {
        private final Map<String, String> data;

        MockCrud(Map<String, String> data) {
            super(mockRepo(), mockAuditRepo(), new ObjectMapper());
            this.data = data;
        }

        private static AlgorithmConfigRepository mockRepo() {
            return null; // overridden methods don't need it
        }

        private static AlgorithmConfigAuditRepository mockAuditRepo() {
            return null;
        }

        @Override
        public Map<String, String> loadConfigCache() {
            return new java.util.HashMap<>(data);
        }

        @Override
        public int getIntValue(String key, int defaultVal, Map<String, String> cache) {
            String v = cache.get(key);
            if (v == null || v.isBlank()) return defaultVal;
            return Integer.parseInt(v);
        }

        @Override
        public String getStringValue(String key, String defaultVal, Map<String, String> cache) {
            return cache.getOrDefault(key, defaultVal);
        }

        @Override
        public List<String> getStringListValue(String key, Map<String, String> cache) {
            String v = cache.get(key);
            if (v == null || v.isBlank()) return List.of();
            return java.util.Arrays.stream(v.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        @Override
        public boolean getBooleanValue(String key, boolean defaultVal, Map<String, String> cache) {
            String v = cache.get(key);
            if (v == null || v.isBlank()) return defaultVal;
            return "true".equalsIgnoreCase(v) || "1".equals(v);
        }

        @Override
        public float getFloatValue(String key, float defaultVal, Map<String, String> cache) {
            String v = cache.get(key);
            if (v == null || v.isBlank()) return defaultVal;
            return Float.parseFloat(v);
        }

        @Override
        public void upsert(String key, String value,
                           com.hospital.scheduler.entity.AlgorithmConfig.ValueType type,
                           String description) {
            // no-op for read tests
        }

        @Override
        public void upsertAll(Map<String, String> paramKeyToValue) {
            // no-op for read tests
        }
    }
}
