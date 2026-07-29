package com.hospital.scheduler.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.dto.request.AlgoConfigRequest;
import com.hospital.scheduler.dto.request.SaveAlgorithmTemplateRequest;
import com.hospital.scheduler.dto.response.AlgorithmConfigDTO;
import com.hospital.scheduler.dto.response.AlgorithmConfigResponse;
import com.hospital.scheduler.entity.AlgorithmConfig;
import com.hospital.scheduler.entity.AlgorithmConfigAudit;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.AlgorithmConfigAuditRepository;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns pure CRUD + audit + template persistence for {@link AlgorithmConfig}.
 * Extracted from {@link AlgorithmConfigService} in SERVICE_AUDIT.md P5 so the
 * read/write paths stop mixing with AutoGen + Runtime + Recommendation
 * domain logic.
 *
 * <p>Also owns the config-cache helper {@link #loadConfigCache()} (used by the
 * AutoGen / Runtime services) and the typed lookup helpers {@link #getIntValue}
 * / {@link #getStringValue} / etc. — they were previously private inside the
 * facade and had to be copy-pasted into every new place that read configs.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AlgorithmConfigCrudService {

    private final AlgorithmConfigRepository configRepository;
    private final AlgorithmConfigAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AlgorithmConfigDTO> getAllConfigs() {
        return configRepository.findAllWithUpdatedBy().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AlgorithmConfigDTO> getConfigsPage(Pageable pageable) {
        return configRepository.findAll(
                        PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                Sort.by(Sort.Direction.ASC, "paramKey")))
                .map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public AlgorithmConfigDTO getConfigByParamKey(String paramKey) {
        AlgorithmConfig config = configRepository.findByParamKey(paramKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy cấu hình với paramKey: " + paramKey));
        return toDTO(config);
    }

    // ── Write ────────────────────────────────────────────────────────────────

    public AlgorithmConfigDTO createConfig(AlgoConfigRequest request) {
        if (configRepository.findByParamKey(request.getParamKey()).isPresent()) {
            throw new BadRequestException(
                    "Cấu hình với paramKey '" + request.getParamKey() + "' đã tồn tại");
        }
        AlgorithmConfig saved = configRepository.save(AlgorithmConfig.builder()
                .paramKey(request.getParamKey())
                .paramValue(request.getParamValue())
                .valueType(request.getValueType())
                .description(request.getDescription())
                .build());
        recordAudit(request.getParamKey(), null, request.getParamValue(),
                AlgorithmConfigAudit.Action.CREATE);
        return toDTO(saved);
    }

    public AlgorithmConfigDTO updateConfig(String paramKey, AlgoConfigRequest request) {
        AlgorithmConfig config = configRepository.findByParamKey(paramKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy cấu hình với paramKey: " + paramKey));
        String oldValue = config.getParamValue();
        if (request.getParamValue() != null) {
            config.setParamValue(request.getParamValue());
        }
        if (request.getValueType() != null) {
            config.setValueType(request.getValueType());
        }
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }
        AlgorithmConfig saved = configRepository.save(config);
        if (request.getParamValue() != null && !Objects.equals(oldValue, request.getParamValue())) {
            recordAudit(paramKey, oldValue, request.getParamValue(),
                    AlgorithmConfigAudit.Action.UPDATE);
        }
        return toDTO(saved);
    }

    public void deleteConfig(String paramKey) {
        if (!configRepository.existsById(paramKey)) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy cấu hình với paramKey: " + paramKey);
        }
        String oldValue = configRepository.findByParamKey(paramKey)
                .map(AlgorithmConfig::getParamValue).orElse(null);
        configRepository.deleteById(paramKey);
        recordAudit(paramKey, oldValue, null, AlgorithmConfigAudit.Action.DELETE);
    }

    // ── Cache + typed lookups (shared with AutoGen / Runtime) ────────────────

    /**
     * Load every config row once and return it as a key/value map.
     * Used by bulk-read entry points (getAutoGenConfig, getRuntimeConfig)
     * to eliminate the N+1 query pattern that caused the algorithm-config
     * page to take 5+ seconds to load.
     */
    public Map<String, String> loadConfigCache() {
        Map<String, String> cache = new HashMap<>();
        for (com.hospital.scheduler.repository.AlgorithmConfigKeyValue kv
                : configRepository.findAllAsKeyValuePairs()) {
            // Normalize paramKey to lowercase so lookup by Java constants
            // (e.g. AUTO_GEN_REMOVED_SHIFT_TYPES = "auto_gen_removed_shift_types")
            // matches regardless of how the row was originally inserted.
            // MySQL's default ci collation lets findByParamKey match mixed-case
            // writes, so this cache normalization is the only line of defense
            // against future rows being saved with the wrong case.
            cache.put(kv.getParamKey().toLowerCase(), kv.getParamValue());
        }
        return cache;
    }

    public String lookupRaw(String paramKey) {
        return configRepository.findByParamKey(paramKey)
                .map(AlgorithmConfig::getParamValue)
                .orElse(null);
    }

    public int getIntValue(String paramKey, int defaultValue, Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey.toLowerCase()) : lookupRaw(paramKey);
        if (raw == null) return defaultValue;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public String getStringValue(String paramKey, String defaultValue, Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey.toLowerCase()) : lookupRaw(paramKey);
        return raw != null ? raw : defaultValue;
    }

    public List<String> getStringListValue(String paramKey, Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey.toLowerCase()) : lookupRaw(paramKey);
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
    }

    public boolean getBooleanValue(String paramKey, boolean defaultValue, Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey.toLowerCase()) : lookupRaw(paramKey);
        if (raw == null) return defaultValue;
        return Boolean.parseBoolean(raw);
    }

    public float getFloatValue(String paramKey, float defaultValue, Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey.toLowerCase()) : lookupRaw(paramKey);
        if (raw == null) return defaultValue;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public java.math.BigDecimal getBigDecimalValue(String paramKey, double defaultValue,
                                                    Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey.toLowerCase()) : lookupRaw(paramKey);
        if (raw == null) return java.math.BigDecimal.valueOf(defaultValue);
        try {
            return new java.math.BigDecimal(raw);
        } catch (NumberFormatException e) {
            return java.math.BigDecimal.valueOf(defaultValue);
        }
    }

    // ── Upsert (shared by sync, AutoGen save, Runtime save) ──────────────────

    public void upsert(String paramKey, String value, AlgorithmConfig.ValueType valueType,
                       String description) {
        AlgorithmConfig config = configRepository.findByParamKey(paramKey)
                .orElse(AlgorithmConfig.builder().paramKey(paramKey).build());
        config.setParamValue(value);
        config.setValueType(valueType);
        config.setDescription(description);
        configRepository.save(config);
    }

    /**
     * Bulk upsert from ConfigMapper — preserves existing valueType and description.
     * Used by ConfigService to persist full ConfigDomain.
     * BUGFIX (V25): was overwriting every row to ValueType.STRING + "" description,
     * destroying the legacy UI's ability to render correct controls (toggle/slider/text).
     */
    public void upsertAll(Map<String, String> paramKeyToValue) {
        for (Map.Entry<String, String> e : paramKeyToValue.entrySet()) {
            AlgorithmConfig existing = configRepository.findByParamKey(e.getKey()).orElse(null);
            AlgorithmConfig.ValueType vt = existing != null ? existing.getValueType() : AlgorithmConfig.ValueType.STRING;
            String desc = existing != null ? existing.getDescription() : "";
            upsert(e.getKey(), e.getValue(), vt, desc);
        }
    }

    // ── Templates ────────────────────────────────────────────────────────────

    public AlgorithmConfigResponse saveAsTemplate(SaveAlgorithmTemplateRequest request) {
        String paramsJson = null;
        if (request.getParams() != null && !request.getParams().isEmpty()) {
            try {
                paramsJson = objectMapper.writeValueAsString(request.getParams());
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Lỗi khi serialize params: " + e.getMessage());
            }
        }

        // Use template name as prefix for paramKey to ensure uniqueness
        String paramKey = "TEMPLATE_" + request.getName().toUpperCase().replaceAll("\\s+", "_");

        AlgorithmConfig config = configRepository.save(AlgorithmConfig.builder()
                .paramKey(paramKey)
                .paramValue(paramsJson)
                .valueType(AlgorithmConfig.ValueType.JSON)
                .description(request.getDescription() + " [Template: " + request.getAlgorithmType() + "]")
                .build());

        return AlgorithmConfigResponse.builder()
                .name(request.getName())
                .description(request.getDescription())
                .algorithmType(request.getAlgorithmType())
                .params(request.getParams())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    // ── Audit helpers ────────────────────────────────────────────────────────

    /**
     * Ghi audit entry. Không throw nếu user lookup fail để tránh chặn flow chính.
     */
    private void recordAudit(String paramKey, String oldValue, String newValue,
                             AlgorithmConfigAudit.Action action) {
        try {
            String username = currentUsername();
            auditRepository.save(AlgorithmConfigAudit.builder()
                    .paramKey(paramKey)
                    .oldValue(oldValue)
                    .newValue(newValue == null ? "" : newValue)
                    .action(action)
                    .changedByUsername(username)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record audit for {}: {}", paramKey, e.getMessage());
        }
    }

    private String currentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null ? auth.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public AlgorithmConfigDTO toDTO(AlgorithmConfig c) {
        return AlgorithmConfigDTO.builder()
                .paramKey(c.getParamKey())
                .paramValue(c.getParamValue())
                .valueType(c.getValueType().name())
                .description(c.getDescription())
                .updatedBy(c.getUpdatedBy() != null ? c.getUpdatedBy().getFullName() : null)
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}