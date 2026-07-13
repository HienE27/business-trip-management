package com.hospital.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.repository.AlgorithmConfigAuditRepository;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Regression: PUT /auto-gen-config previously hit SELECT-then-INSERT race → 409 Conflict.
 * Fix: service must use {@link AlgorithmConfigRepository#upsertConfig} (native MySQL upsert)
 * instead of findByParamKey + save(). These tests pin the call contract so the race fix
 * cannot regress silently.
 */
@ExtendWith(MockitoExtension.class)
class AutoGenConfigSaveTest {

    @Mock private AlgorithmConfigRepository configRepository;
    @Mock private AlgorithmConfigAuditRepository auditRepository;

    @InjectMocks private AlgorithmConfigService configService;

    private static AutoGenConfig sampleConfig() {
        return new AutoGenConfig(
                true,
                1, 1, 1, 1, 2, 2, 2, 2,
                1, 1, 1, 1, 5, 5, 5, 5,
                "SKIP",
                List.of(),
                false,
                0.3f,
                List.of("Ngoại", "Nội"),
                List.of("Ngoại", "Nội"),
                List.of("Ngoại", "Nội"),
                List.of("Ngoại", "Nội")
        );
    }

    @Test
    void saveAutoGenConfig_invokesUpsertConfigForEachParam() {
        configService.saveAutoGenConfig(sampleConfig());

        // 25 keys written: 22 constants in service + 3 dynamic (REMOVED_SHIFT_TYPES, L04_ALLOWED_SPECIALTIES, L01/L02/L03 specialtys)
        // Tightly bounded — if the upsert helper changes, this count fails loudly.
        verify(configRepository, times(25)).upsertConfig(
                any(), any(), any(), any());
    }

    @Test
    void saveAutoGenConfig_passesStringValueTypeForHolidayAndCsvKeys() {
        configService.saveAutoGenConfig(sampleConfig());

        verify(configRepository).upsertConfig(
                eq(AlgorithmConfigService.AUTO_GEN_HOLIDAY_MODE),
                eq("SKIP"),
                eq("STRING"),
                anyString());
        verify(configRepository).upsertConfig(
                eq(AlgorithmConfigService.AUTO_GEN_L01_ALLOWED_SPECIALTIES),
                eq("Ngoại,Nội"),
                eq("STRING"),
                anyString());
    }
}
