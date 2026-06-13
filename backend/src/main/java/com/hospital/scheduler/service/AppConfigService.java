package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.AlgorithmConfig;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppConfigService {

    public static final String KEY_EMAIL_ENABLED = "email.enabled";
    public static final String KEY_EMAIL_CONFLICT_ENABLED = "email.conflict.notification.enabled";

    private final AlgorithmConfigRepository algorithmConfigRepository;

    public boolean isEmailEnabled() {
        return getBoolean(KEY_EMAIL_ENABLED, false);
    }

    public boolean isConflictEmailEnabled() {
        return getBoolean(KEY_EMAIL_CONFLICT_ENABLED, false);
    }

    @Transactional
    public void setEmailEnabled(boolean enabled) {
        saveConfig(KEY_EMAIL_ENABLED, String.valueOf(enabled), AlgorithmConfig.ValueType.BOOLEAN,
                "Bật/tắt gửi email thông báo");
    }

    @Transactional
    public void setConflictEmailEnabled(boolean enabled) {
        saveConfig(KEY_EMAIL_CONFLICT_ENABLED, String.valueOf(enabled), AlgorithmConfig.ValueType.BOOLEAN,
                "Bật/tắt email thông báo xung đột lịch");
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        return algorithmConfigRepository.findById(key)
                .map(c -> "true".equalsIgnoreCase(c.getParamValue()))
                .orElse(defaultValue);
    }

    private void saveConfig(String key, String value, AlgorithmConfig.ValueType valueType, String description) {
        AlgorithmConfig config = algorithmConfigRepository.findById(key)
                .orElse(AlgorithmConfig.builder()
                        .paramKey(key)
                        .description(description)
                        .build());
        config.setParamValue(value);
        config.setValueType(valueType);
        algorithmConfigRepository.save(config);
    }
}
