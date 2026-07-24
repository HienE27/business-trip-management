package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.service.SchedulingAlgorithmRunner.CrossSpecialtyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link AutoSchedulingService#getL04CrossSpecialtyConfig()} correctly
 * resolves the 3-branch {@code l04BalanceStrategy} into the appropriate
 * {@link CrossSpecialtyConfig#enabled()} and {@link CrossSpecialtyConfig#ratio()}.
 *
 * <p>Three strategies:
 * <ul>
 *   <li>{@code STRICT_MATCH_ONLY} → enabled=false, ratio=0.0</li>
 *   <li>{@code FAIR_DISTRIBUTE} → enabled=true, ratio=1.0</li>
 *   <li>{@code WEIGHTED_FAIR} → enabled=true, ratio=user-specified (e.g. 0.3)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutoSchedulingService.getL04CrossSpecialtyConfig — 3-branch strategy")
class L04BalanceStrategyTest {

    @Mock private AlgorithmConfigService algorithmConfigService;
    @InjectMocks private AutoSchedulingService service;

    /** Shared AutoGenConfig builder — only strategy/ratio/allowed vary. */
    private AutoGenConfig baseConfig(String strategy, float ratio, List<String> allowed) {
        return new AutoGenConfig(
                true,
                2, 2, 2, 2,
                2, 2, 2, 2,
                2, 2, 2, 2,
                8, 8, 8, 8,
                "SKIP", List.of(),
                false,       // l04CrossSpecialty (legacy — ignored by getL04CrossSpecialtyConfig)
                ratio,
                allowed,
                List.of("Ngoại", "Nội"),
                List.of("Ngoại", "Nội"),
                List.of("Ngoại", "Nội"),
                5, 5, 5, 5,
                strategy    // <-- the field under test
        );
    }

    @Test
    @DisplayName("STRICT_MATCH_ONLY → disabled, zero ratio")
    void strictMatchOnly() {
        when(algorithmConfigService.getAutoGenConfig())
                .thenReturn(Optional.of(baseConfig("STRICT_MATCH_ONLY", 0.5f, List.of())));

        CrossSpecialtyConfig cfg = service.getL04CrossSpecialtyConfig();

        assertThat(cfg.enabled()).isFalse();
        assertThat(cfg.ratio()).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("FAIR_DISTRIBUTE → enabled, ratio=1.0 (full cross)")
    void fairDistribute() {
        when(algorithmConfigService.getAutoGenConfig())
                .thenReturn(Optional.of(baseConfig("FAIR_DISTRIBUTE", 0.5f, List.of())));

        CrossSpecialtyConfig cfg = service.getL04CrossSpecialtyConfig();

        assertThat(cfg.enabled()).isTrue();
        assertThat(cfg.ratio()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("WEIGHTED_FAIR with ratio=0.3 → enabled, ratio=0.3")
    void weightedFair() {
        when(algorithmConfigService.getAutoGenConfig())
                .thenReturn(Optional.of(baseConfig("WEIGHTED_FAIR", 0.3f, List.of("Ngoại", "Nội"))));

        CrossSpecialtyConfig cfg = service.getL04CrossSpecialtyConfig();

        assertThat(cfg.enabled()).isTrue();
        assertThat(cfg.ratio()).isEqualTo(0.3f);
        assertThat(cfg.allowedSpecialties()).containsExactly("Ngoại", "Nội");
    }

    @Test
    @DisplayName("null strategy → treated as STRICT_MATCH_ONLY (disabled)")
    void nullStrategy() {
        when(algorithmConfigService.getAutoGenConfig())
                .thenReturn(Optional.of(baseConfig(null, 0.4f, List.of())));

        CrossSpecialtyConfig cfg = service.getL04CrossSpecialtyConfig();

        assertThat(cfg.enabled()).isFalse();
        assertThat(cfg.ratio()).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("unknown strategy → treated as STRICT_MATCH_ONLY")
    void unknownStrategy() {
        when(algorithmConfigService.getAutoGenConfig())
                .thenReturn(Optional.of(baseConfig("LEGACY_MODE", 0.8f, List.of())));

        CrossSpecialtyConfig cfg = service.getL04CrossSpecialtyConfig();

        assertThat(cfg.enabled()).isFalse();
        assertThat(cfg.ratio()).isEqualTo(0.0f);
    }

    @Test
    @DisplayName("empty Optional → defaults to disabled, zero ratio")
    void emptyConfig() {
        when(algorithmConfigService.getAutoGenConfig()).thenReturn(Optional.empty());

        CrossSpecialtyConfig cfg = service.getL04CrossSpecialtyConfig();

        assertThat(cfg.enabled()).isFalse();
        assertThat(cfg.ratio()).isEqualTo(0.0f);
        assertThat(cfg.allowedSpecialties()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "STRICT_MATCH_ONLY, 0.0, 0.0",
        "FAIR_DISTRIBUTE,   1.0, 1.0",
        "WEIGHTED_FAIR,     0.3, 0.3",
        "WEIGHTED_FAIR,     0.7, 0.7",
    })
    @DisplayName("parameterized: strategy, inputRatio, expectedRatio")
    void parameterized(String strategy, float inputRatio, float expectedRatio) {
        when(algorithmConfigService.getAutoGenConfig())
                .thenReturn(Optional.of(baseConfig(strategy, inputRatio, List.of())));

        CrossSpecialtyConfig cfg = service.getL04CrossSpecialtyConfig();

        boolean expectedEnabled = "FAIR_DISTRIBUTE".equals(strategy) || "WEIGHTED_FAIR".equals(strategy);
        assertThat(cfg.enabled()).isEqualTo(expectedEnabled);
        assertThat(cfg.ratio()).isEqualTo(expectedRatio);
    }
}
