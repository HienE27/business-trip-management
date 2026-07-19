package com.hospital.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.repository.AlgorithmConfigAuditRepository;
import com.hospital.scheduler.repository.AlgorithmConfigKeyValue;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regression test for {@link AlgorithmConfigService#getAutoGenConfig()}.
 *
 * <p>Locks down the documented behavior: the method always returns a present
 * {@code Optional}. The previous javadoc claimed it would return
 * {@code Optional.empty()} when auto-gen is disabled, but the implementation
 * has never honored that contract — it always returns a present Optional and
 * exposes the disabled state via {@link AutoGenConfig#enabled()}.
 *
 * <p>If a future change tries to make the method honor the old javadoc
 * (return empty on disabled), these tests will fail and force the change to
 * be reviewed against the 12 production callers that depend on the current
 * contract.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AlgorithmConfigService.getAutoGenConfig - contract regression")
class AlgorithmConfigServiceAutoGenConfigTest {

    @Mock private AlgorithmConfigRepository configRepository;
    @Mock private AlgorithmConfigAuditRepository auditRepository;

    private AlgorithmConfigService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new AlgorithmConfigService(configRepository, auditRepository, objectMapper);
    }

    @Test
    @DisplayName("empty cache → present Optional with enabled=true default")
    void emptyCache_returnsPresentDefaultEnabled() {
        when(configRepository.findAllAsKeyValuePairs()).thenReturn(List.of());

        Optional<AutoGenConfig> result = service.getAutoGenConfig();

        assertThat(result).isPresent();
        assertThat(result.get().enabled()).isTrue();
    }

    @Test
    @DisplayName("AUTO_GEN_ENABLED=false → present Optional with enabled=false (NOT empty)")
    void disabledFlag_returnsPresentWithEnabledFalse() {
        when(configRepository.findAllAsKeyValuePairs()).thenReturn(List.of(
                new AlgorithmConfigKeyValue("auto_gen_enabled", "false")
        ));

        Optional<AutoGenConfig> result = service.getAutoGenConfig();

        // Contract lock: even when auto-gen is disabled, the Optional is present.
        // The disabled state is communicated through AutoGenConfig#enabled(),
        // not through Optional#isEmpty(). Changing this would break 12 callers.
        assertThat(result).isPresent();
        assertThat(result.get().enabled()).isFalse();
    }

    @Test
    @DisplayName("configured values propagate through the present Optional")
    void configuredValues_propagate() {
        when(configRepository.findAllAsKeyValuePairs()).thenReturn(List.of(
                new AlgorithmConfigKeyValue("auto_gen_enabled", "true"),
                new AlgorithmConfigKeyValue("auto_gen_l01_min_per_day", "3"),
                new AlgorithmConfigKeyValue("auto_gen_holiday_mode", "PARTIAL")
        ));

        Optional<AutoGenConfig> result = service.getAutoGenConfig();

        assertThat(result).isPresent();
        assertThat(result.get().enabled()).isTrue();
        assertThat(result.get().l01MinPerDay()).isEqualTo(3);
        assertThat(result.get().holidayMode()).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("Optional is never empty regardless of cache state")
    void neverReturnsEmpty_regardlessOfCacheContents() {
        // Sanity check across several realistic cache shapes.
        checkPresent(List.of());
        checkPresent(List.of(new AlgorithmConfigKeyValue("auto_gen_enabled", "false")));
        checkPresent(List.of(new AlgorithmConfigKeyValue("auto_gen_enabled", "true")));
        checkPresent(List.of(new AlgorithmConfigKeyValue("auto_gen_enabled", "")));
        checkPresent(List.of(new AlgorithmConfigKeyValue("some_unrelated_key", "x")));
    }

    private void checkPresent(List<AlgorithmConfigKeyValue> cacheRows) {
        when(configRepository.findAllAsKeyValuePairs()).thenReturn(cacheRows);

        Optional<AutoGenConfig> result = service.getAutoGenConfig();

        assertThat(result)
                .as("cacheRows=%s", cacheRows.stream().map(AlgorithmConfigKeyValue::getParamKey).toList())
                .isPresent();
    }
}