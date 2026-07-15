package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.AlgorithmConfig;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppConfigService Tests - Cấu hình app boolean flags (email.enabled, ...)")
class AppConfigServiceTest {

    @Mock
    private AlgorithmConfigRepository algorithmConfigRepository;

    @InjectMocks
    private AppConfigService service;

    private AlgorithmConfig buildConfig(String key, String value) {
        return AlgorithmConfig.builder()
                .paramKey(key)
                .paramValue(value)
                .valueType(AlgorithmConfig.ValueType.BOOLEAN)
                .build();
    }

    @Test
    @DisplayName("isEmailEnabled - key chưa tồn tại -> default false")
    void isEmailEnabled_defaultFalse() {
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_ENABLED))
                .thenReturn(Optional.empty());

        assertThat(service.isEmailEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEmailEnabled - value='true' -> true")
    void isEmailEnabled_true() {
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_ENABLED))
                .thenReturn(Optional.of(buildConfig(AppConfigService.KEY_EMAIL_ENABLED, "true")));

        assertThat(service.isEmailEnabled()).isTrue();
    }

    @Test
    @DisplayName("isEmailEnabled - value='TRUE' (uppercase) -> true (case-insensitive)")
    void isEmailEnabled_caseInsensitive() {
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_ENABLED))
                .thenReturn(Optional.of(buildConfig(AppConfigService.KEY_EMAIL_ENABLED, "TRUE")));

        assertThat(service.isEmailEnabled()).isTrue();
    }

    @Test
    @DisplayName("isEmailEnabled - value='false' -> false")
    void isEmailEnabled_false() {
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_ENABLED))
                .thenReturn(Optional.of(buildConfig(AppConfigService.KEY_EMAIL_ENABLED, "false")));

        assertThat(service.isEmailEnabled()).isFalse();
    }

    @Test
    @DisplayName("isEmailEnabled - value='yes' (không phải 'true') -> false")
    void isEmailEnabled_onlyTrueMatches() {
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_ENABLED))
                .thenReturn(Optional.of(buildConfig(AppConfigService.KEY_EMAIL_ENABLED, "yes")));

        assertThat(service.isEmailEnabled()).isFalse();
    }

    @Test
    @DisplayName("isConflictEmailEnabled - key chưa tồn tại -> default false")
    void isConflictEmailEnabled_defaultFalse() {
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_CONFLICT_ENABLED))
                .thenReturn(Optional.empty());

        assertThat(service.isConflictEmailEnabled()).isFalse();
    }

    @Test
    @DisplayName("isConflictEmailEnabled - value='true' -> true")
    void isConflictEmailEnabled_true() {
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_CONFLICT_ENABLED))
                .thenReturn(Optional.of(buildConfig(AppConfigService.KEY_EMAIL_CONFLICT_ENABLED, "true")));

        assertThat(service.isConflictEmailEnabled()).isTrue();
    }

    @Test
    @DisplayName("setEmailEnabled - key chưa tồn tại -> tạo mới với description đúng")
    void setEmailEnabled_insert() {
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_ENABLED))
                .thenReturn(Optional.empty());
        when(algorithmConfigRepository.save(any(AlgorithmConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.setEmailEnabled(true);

        ArgumentCaptor<AlgorithmConfig> captor = ArgumentCaptor.forClass(AlgorithmConfig.class);
        verify(algorithmConfigRepository).save(captor.capture());
        AlgorithmConfig saved = captor.getValue();
        assertThat(saved.getParamKey()).isEqualTo(AppConfigService.KEY_EMAIL_ENABLED);
        assertThat(saved.getParamValue()).isEqualTo("true");
        assertThat(saved.getValueType()).isEqualTo(AlgorithmConfig.ValueType.BOOLEAN);
        assertThat(saved.getDescription()).contains("email");
    }

    @Test
    @DisplayName("setEmailEnabled - key đã tồn tại -> update value (giữ description cũ)")
    void setEmailEnabled_update() {
        AlgorithmConfig existing = AlgorithmConfig.builder()
                .paramKey(AppConfigService.KEY_EMAIL_ENABLED)
                .paramValue("false")
                .valueType(AlgorithmConfig.ValueType.BOOLEAN)
                .description("Old description")
                .build();
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_ENABLED))
                .thenReturn(Optional.of(existing));
        when(algorithmConfigRepository.save(any(AlgorithmConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.setEmailEnabled(true);

        ArgumentCaptor<AlgorithmConfig> captor = ArgumentCaptor.forClass(AlgorithmConfig.class);
        verify(algorithmConfigRepository).save(captor.capture());
        AlgorithmConfig saved = captor.getValue();
        assertThat(saved.getParamValue()).isEqualTo("true");
        assertThat(saved.getDescription()).isEqualTo("Old description");
    }

    @Test
    @DisplayName("setEmailEnabled(false) -> save 'false'")
    void setEmailEnabled_false() {
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_ENABLED))
                .thenReturn(Optional.empty());
        when(algorithmConfigRepository.save(any(AlgorithmConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.setEmailEnabled(false);

        ArgumentCaptor<AlgorithmConfig> captor = ArgumentCaptor.forClass(AlgorithmConfig.class);
        verify(algorithmConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getParamValue()).isEqualTo("false");
    }

    @Test
    @DisplayName("setConflictEmailEnabled - key chưa tồn tại -> tạo mới với value 'true'")
    void setConflictEmailEnabled_insert() {
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_CONFLICT_ENABLED))
                .thenReturn(Optional.empty());
        when(algorithmConfigRepository.save(any(AlgorithmConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.setConflictEmailEnabled(true);

        ArgumentCaptor<AlgorithmConfig> captor = ArgumentCaptor.forClass(AlgorithmConfig.class);
        verify(algorithmConfigRepository).save(captor.capture());
        AlgorithmConfig saved = captor.getValue();
        assertThat(saved.getParamKey()).isEqualTo(AppConfigService.KEY_EMAIL_CONFLICT_ENABLED);
        assertThat(saved.getParamValue()).isEqualTo("true");
        assertThat(saved.getValueType()).isEqualTo(AlgorithmConfig.ValueType.BOOLEAN);
        assertThat(saved.getDescription()).contains("xung đột lịch");
    }

    @Test
    @DisplayName("setConflictEmailEnabled - key tồn tại -> update")
    void setConflictEmailEnabled_update() {
        AlgorithmConfig existing = AlgorithmConfig.builder()
                .paramKey(AppConfigService.KEY_EMAIL_CONFLICT_ENABLED)
                .paramValue("true")
                .valueType(AlgorithmConfig.ValueType.BOOLEAN)
                .description("old")
                .build();
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_CONFLICT_ENABLED))
                .thenReturn(Optional.of(existing));
        when(algorithmConfigRepository.save(any(AlgorithmConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.setConflictEmailEnabled(false);

        ArgumentCaptor<AlgorithmConfig> captor = ArgumentCaptor.forClass(AlgorithmConfig.class);
        verify(algorithmConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getParamValue()).isEqualTo("false");
    }

    @Test
    @DisplayName("Round-trip: setEmailEnabled(true) -> isEmailEnabled() == true")
    void roundTrip_setThenGet() {
        AlgorithmConfig stored = buildConfig(AppConfigService.KEY_EMAIL_ENABLED, "true");
        when(algorithmConfigRepository.findById(AppConfigService.KEY_EMAIL_ENABLED))
                .thenReturn(Optional.of(stored));

        assertThat(service.isEmailEnabled()).isTrue();
    }
}