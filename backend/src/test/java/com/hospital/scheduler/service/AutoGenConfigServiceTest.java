package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
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
 * Unit tests for {@link AutoGenConfigService} — extracted in SERVICE_AUDIT.md P5.
 *
 * <p>Verifies the cache-backed read path returns sensible defaults when
 * rows are missing and preserves existing values when they are present.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AutoGenConfigService - AutoGenConfig reads (P5)")
class AutoGenConfigServiceTest {

    @Mock private AlgorithmConfigRepository configRepository;
    @Mock private AlgorithmConfigCrudService crud;

    private AutoGenConfigService service;

    @BeforeEach
    void setUp() {
        service = new AutoGenConfigService(crud);
        when(crud.loadConfigCache()).thenReturn(new java.util.HashMap<>());
        when(crud.getIntValue(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(crud.getStringValue(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(crud.getStringListValue(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(crud.getBooleanValue(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(1));
        when(crud.getFloatValue(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    @DisplayName("getAutoGenConfig - empty cache → enabled=true by default")
    void emptyCache_returnsDefaultEnabled() {
        Optional<AutoGenConfig> result = service.getAutoGenConfig();

        assertThat(result).isPresent();
        assertThat(result.get().enabled()).isTrue();
    }

    @Test
    @DisplayName("getAutoGenConfig - AUTO_GEN_ENABLED=false → disabled")
    void disabledFlag_propagates() {
        java.util.Map<String, String> cache = new java.util.HashMap<>();
        cache.put("auto_gen_enabled", "false");
        when(crud.loadConfigCache()).thenReturn(cache);

        Optional<AutoGenConfig> result = service.getAutoGenConfig();

        assertThat(result.get().enabled()).isFalse();
    }

    @Test
    @DisplayName("getAutoGenConfig - configured values are preserved")
    void configuredValues_preserved() {
        java.util.Map<String, String> cache = new java.util.HashMap<>();
        cache.put("auto_gen_l01_min_per_day", "3");
        cache.put("auto_gen_holiday_mode", "PARTIAL");
        when(crud.loadConfigCache()).thenReturn(cache);
        when(crud.getIntValue(org.mockito.ArgumentMatchers.eq("auto_gen_l01_min_per_day"),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(3);
        when(crud.getStringValue(org.mockito.ArgumentMatchers.eq("auto_gen_holiday_mode"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn("PARTIAL");

        Optional<AutoGenConfig> result = service.getAutoGenConfig();

        assertThat(result.get().l01MinPerDay()).isEqualTo(3);
        assertThat(result.get().holidayMode()).isEqualTo("PARTIAL");
    }
}