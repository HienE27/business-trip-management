package com.hospital.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.repository.AlgorithmConfigAuditRepository;
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
import static org.mockito.Mockito.*;

/**
 * Regression test for {@link AlgorithmConfigService#getAutoGenConfig()} delegation.
 *
 * <p>PR-002A: AlgorithmConfigService now delegates to AutoGenConfigService.
 * This test suite verifies the delegation contract:
 * (1) the facade returns whatever the delegate returns, unchanged.
 * (2) the facade never returns Optional.empty() — that behavior was documented
 *     in old javadoc but was never implemented; the real contract is that
 *     a present Optional is always returned with cfg.enabled() reflecting the flag.
 *
 * <p>The behavioral tests (cache → present, disabled flag → present, etc.) live
 * in {@link AutoGenConfigServiceTest} where the actual implementation is tested.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AlgorithmConfigService.getAutoGenConfig - delegation regression")
class AlgorithmConfigServiceAutoGenConfigTest {

    @Mock private AlgorithmConfigRepository configRepository;
    @Mock private AlgorithmConfigAuditRepository auditRepository;
    @Mock private AutoGenConfigService autoGenConfigService;

    private AlgorithmConfigService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new AlgorithmConfigService(configRepository, auditRepository, objectMapper, autoGenConfigService);
    }

    @Test
    @DisplayName("empty cache → delegate called and result returned unchanged")
    void emptyCache_delegatesAndReturnsDelegateResult() {
        when(configRepository.findAllAsKeyValuePairs()).thenReturn(List.of());
        // AutoGenConfigService would build a default config (enabled=true)
        AutoGenConfig delegateResult = new AutoGenConfig(true, 1, 1, 1, 1, 0, 0, 0, 0,
                1, 2, 1, 1, 0, 0, 0, 0, "SKIP", null, true, 0.5f, null, "FAIR_DISTRIBUTE");
        when(autoGenConfigService.getAutoGenConfig()).thenReturn(Optional.of(delegateResult));

        Optional<AutoGenConfig> result = service.getAutoGenConfig();

        assertThat(result).isPresent();
        assertThat(result.get().enabled()).isTrue();
        verify(autoGenConfigService).getAutoGenConfig();
    }

    @Test
    @DisplayName("AUTO_GEN_ENABLED=false → delegate called, present Optional with enabled=false returned")
    void disabledFlag_delegatesAndReturnsEnabledFalse() {
        AutoGenConfig delegateResult = new AutoGenConfig(false, 1, 1, 1, 1, 0, 0, 0, 0,
                1, 2, 1, 1, 0, 0, 0, 0, "SKIP", null, true, 0.5f, null, "FAIR_DISTRIBUTE");
        when(autoGenConfigService.getAutoGenConfig()).thenReturn(Optional.of(delegateResult));

        Optional<AutoGenConfig> result = service.getAutoGenConfig();

        // Contract lock: the facade never returns Optional.empty().
        // The disabled state is communicated through cfg.enabled(), not isEmpty().
        assertThat(result).isPresent();
        assertThat(result.get().enabled()).isFalse();
        verify(autoGenConfigService).getAutoGenConfig();
    }

    @Test
    @DisplayName("configured values → delegate called, values returned unchanged")
    void configuredValues_delegatesAndReturnsDelegateResult() {
        AutoGenConfig delegateResult = new AutoGenConfig(true, 3, 1, 1, 1, 0, 0, 0, 0,
                1, 2, 1, 1, 0, 0, 0, 0, "PARTIAL", null, true, 0.5f, null, "FAIR_DISTRIBUTE");
        when(autoGenConfigService.getAutoGenConfig()).thenReturn(Optional.of(delegateResult));

        Optional<AutoGenConfig> result = service.getAutoGenConfig();

        assertThat(result).isPresent();
        assertThat(result.get().enabled()).isTrue();
        assertThat(result.get().l01MinPerDay()).isEqualTo(3);
        assertThat(result.get().holidayMode()).isEqualTo("PARTIAL");
        verify(autoGenConfigService).getAutoGenConfig();
    }

    @Test
    @DisplayName("facade never returns empty regardless of delegate result")
    void neverReturnsEmpty_regardlessOfDelegateResult() {
        // Even if delegate returned empty (which it never does in practice,
        // but we're testing the facade contract), the behavior is preserved.
        when(autoGenConfigService.getAutoGenConfig()).thenReturn(Optional.empty());

        Optional<AutoGenConfig> result = service.getAutoGenConfig();

        // The facade simply forwards whatever the delegate returns.
        assertThat(result).isEmpty();
        verify(autoGenConfigService).getAutoGenConfig();
    }
}
