package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.dto.request.AlgoConfigRequest;
import com.hospital.scheduler.dto.request.SaveAlgorithmTemplateRequest;
import com.hospital.scheduler.dto.response.AlgorithmConfigDTO;
import com.hospital.scheduler.dto.response.AlgorithmConfigResponse;
import com.hospital.scheduler.entity.AlgorithmConfig;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AlgorithmConfigService {

    private final AlgorithmConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    // Auto-generate config param keys
    public static final String AUTO_GEN_ENABLED = "auto_gen_enabled";
    public static final String AUTO_GEN_L01_PER_DAY = "auto_gen_l01_per_day";
    public static final String AUTO_GEN_L02_PER_DAY = "auto_gen_l02_per_day";
    public static final String AUTO_GEN_L03_PER_DAY = "auto_gen_l03_per_day";
    public static final String AUTO_GEN_L04_PER_DAY = "auto_gen_l04_per_day";
    public static final String AUTO_GEN_L01_PER_WEEK = "auto_gen_l01_per_week";
    public static final String AUTO_GEN_L02_PER_WEEK = "auto_gen_l02_per_week";
    public static final String AUTO_GEN_L03_PER_WEEK = "auto_gen_l03_per_week";
    public static final String AUTO_GEN_L04_PER_WEEK = "auto_gen_l04_per_week";
    public static final String AUTO_GEN_HOLIDAY_MODE = "auto_gen_holiday_mode";

    // Algorithm runtime config param keys
    public static final String MAX_ITERATIONS = "max_iterations";
    public static final String WEEKEND_WEIGHT = "weekend_weight";
    public static final String OVERNIGHT_RECOVERY_HOURS = "overnight_recovery_hours";
    public static final String GREEDY_COVERAGE_THRESHOLD = "greedy_coverage_threshold";
    public static final String BALANCE_SCORE_MIN = "balance_score_min";
    public static final String AUTO_COMPENSATION_ENABLED = "auto_compensation_enabled";
    public static final String BACKTRACK_TIME_LIMIT_SECONDS = "backtrack_time_limit_seconds";

    public List<AlgorithmConfigDTO> getAllConfigs() {
        return configRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public AlgorithmConfigDTO getConfigByParamKey(String paramKey) {
        AlgorithmConfig config = configRepository.findByParamKey(paramKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy cấu hình với paramKey: " + paramKey));
        return toDTO(config);
    }

    @Transactional
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
        return toDTO(saved);
    }

    @Transactional
    public AlgorithmConfigDTO updateConfig(String paramKey, AlgoConfigRequest request) {
        AlgorithmConfig config = configRepository.findByParamKey(paramKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy cấu hình với paramKey: " + paramKey));
        if (request.getParamValue() != null) {
            config.setParamValue(request.getParamValue());
        }
        if (request.getValueType() != null) {
            config.setValueType(request.getValueType());
        }
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }
        return toDTO(configRepository.save(config));
    }

    @Transactional
    public void deleteConfig(String paramKey) {
        if (!configRepository.existsById(paramKey)) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy cấu hình với paramKey: " + paramKey);
        }
        configRepository.deleteById(paramKey);
    }

    /**
     * Save algorithm configuration as a reusable template.
     * Stores the template configuration as a JSON entry in the algorithm_config table.
     */
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

    private AlgorithmConfigDTO toDTO(AlgorithmConfig c) {
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

    /**
     * Get auto-generation configuration.
     * Returns Optional.empty() if auto-gen is disabled.
     */
    public java.util.Optional<AutoGenConfig> getAutoGenConfig() {
        var enabledOpt = configRepository.findByParamKey(AUTO_GEN_ENABLED);
        boolean enabled = enabledOpt.isPresent()
                ? Boolean.parseBoolean(enabledOpt.get().getParamValue())
                : true;  // Default to true so auto-scheduling works out-of-the-box
        // Always return a config with defaults — even if AUTO_GEN_ENABLED is missing from DB,
        // fall back to defaults so auto-scheduling works out-of-the-box without manual config setup.
        return java.util.Optional.of(AutoGenConfig.builder()
                .enabled(enabled)
                .l01RequiredPerDay(getIntValue(AUTO_GEN_L01_PER_DAY, 1))  // 1 người L01/ngày
                .l02RequiredPerDay(getIntValue(AUTO_GEN_L02_PER_DAY, 2))
                .l03RequiredPerDay(getIntValue(AUTO_GEN_L03_PER_DAY, 2))
                .l04RequiredPerDay(getIntValue(AUTO_GEN_L04_PER_DAY, 2))
                .minL01PerWeek(getIntValue(AUTO_GEN_L01_PER_WEEK, 1))
                .minL02PerWeek(getIntValue(AUTO_GEN_L02_PER_WEEK, 3))
                .minL03PerWeek(getIntValue(AUTO_GEN_L03_PER_WEEK, 2))
                .minL04PerWeek(getIntValue(AUTO_GEN_L04_PER_WEEK, 1))
                .holidayMode(getStringValue(AUTO_GEN_HOLIDAY_MODE, "SKIP"))
                .build());
    }

    /**
     * Save auto-generation configuration.
     */
    @Transactional
    public void saveAutoGenConfig(AutoGenConfig config) {
        upsert(AUTO_GEN_ENABLED, String.valueOf(config.enabled()), AlgorithmConfig.ValueType.BOOLEAN, "Bật/tắt tự động tạo yêu cầu");
        upsert(AUTO_GEN_L01_PER_DAY, String.valueOf(config.l01RequiredPerDay()), AlgorithmConfig.ValueType.NUMBER, "Số người cần cho L01 mỗi ngày");
        upsert(AUTO_GEN_L02_PER_DAY, String.valueOf(config.l02RequiredPerDay()), AlgorithmConfig.ValueType.NUMBER, "Số người cần cho L02 mỗi ngày");
        upsert(AUTO_GEN_L03_PER_DAY, String.valueOf(config.l03RequiredPerDay()), AlgorithmConfig.ValueType.NUMBER, "Số người cần cho L03 mỗi ngày");
        upsert(AUTO_GEN_L04_PER_DAY, String.valueOf(config.l04RequiredPerDay()), AlgorithmConfig.ValueType.NUMBER, "Số người cần cho L04 mỗi ngày");
        upsert(AUTO_GEN_L01_PER_WEEK, String.valueOf(config.minL01PerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số L01 tối thiểu mỗi tuần");
        upsert(AUTO_GEN_L02_PER_WEEK, String.valueOf(config.minL02PerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số L02 tối thiểu mỗi tuần");
        upsert(AUTO_GEN_L03_PER_WEEK, String.valueOf(config.minL03PerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số L03 tối thiểu mỗi tuần");
        upsert(AUTO_GEN_L04_PER_WEEK, String.valueOf(config.minL04PerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số L04 tối thiểu mỗi tuần");
        upsert(AUTO_GEN_HOLIDAY_MODE, config.holidayMode(), AlgorithmConfig.ValueType.STRING, "Chế độ ngày lễ: SKIP hoặc PARTIAL");
    }

    private void upsert(String paramKey, String value, AlgorithmConfig.ValueType valueType, String description) {
        AlgorithmConfig config = configRepository.findByParamKey(paramKey)
                .orElse(AlgorithmConfig.builder().paramKey(paramKey).build());
        config.setParamValue(value);
        config.setValueType(valueType);
        config.setDescription(description);
        configRepository.save(config);
    }

    private int getIntValue(String paramKey, int defaultValue) {
        return configRepository.findByParamKey(paramKey)
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getParamValue());
                    } catch (NumberFormatException e) {
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    private String getStringValue(String paramKey, String defaultValue) {
        return configRepository.findByParamKey(paramKey)
                .map(AlgorithmConfig::getParamValue)
                .orElse(defaultValue);
    }

    /**
     * Get algorithm runtime configuration.
     * Returns an object with all runtime parameters or defaults if not set.
     */
    public AlgorithmRuntimeConfig getRuntimeConfig() {
        return AlgorithmRuntimeConfig.builder()
                .maxIterations(getIntValue(MAX_ITERATIONS, 1000))
                .weekendWeight(getBigDecimalValue(WEEKEND_WEIGHT, 2.0))
                .overnightRecoveryHours(getIntValue(OVERNIGHT_RECOVERY_HOURS, 24))
                .greedyCoverageThreshold(getBigDecimalValue(GREEDY_COVERAGE_THRESHOLD, 0.85))
                .balanceScoreMin(getBigDecimalValue(BALANCE_SCORE_MIN, 0.70))
                .autoCompensationEnabled(getBooleanValue(AUTO_COMPENSATION_ENABLED, true))
                .backtrackTimeLimitSeconds(getIntValue(BACKTRACK_TIME_LIMIT_SECONDS, 60))
                .build();
    }

    /**
     * Save algorithm runtime configuration.
     */
    @Transactional
    public void saveRuntimeConfig(AlgorithmRuntimeConfig config) {
        upsert(MAX_ITERATIONS, String.valueOf(config.getMaxIterations()), AlgorithmConfig.ValueType.NUMBER, "Số vòng lặp tối đa cho thuật toán backtracking");
        upsert(WEEKEND_WEIGHT, String.valueOf(config.getWeekendWeight()), AlgorithmConfig.ValueType.NUMBER, "Trọng số cuối tuần (càng cao càng tránh xếp cuối tuần)");
        upsert(OVERNIGHT_RECOVERY_HOURS, String.valueOf(config.getOvernightRecoveryHours()), AlgorithmConfig.ValueType.NUMBER, "Số giờ nghỉ bắt buộc sau trực 24/24");
        upsert(GREEDY_COVERAGE_THRESHOLD, String.valueOf(config.getGreedyCoverageThreshold()), AlgorithmConfig.ValueType.NUMBER, "Ngưỡng phủ lịch tối thiểu (0.0-1.0)");
        upsert(BALANCE_SCORE_MIN, String.valueOf(config.getBalanceScoreMin()), AlgorithmConfig.ValueType.NUMBER, "Ngưỡng cân bằng tải tối thiểu (0.0-1.0)");
        upsert(AUTO_COMPENSATION_ENABLED, String.valueOf(config.isAutoCompensationEnabled()), AlgorithmConfig.ValueType.BOOLEAN, "Tự động tạo ngày nghỉ bù sau trực 24/24");
        upsert(BACKTRACK_TIME_LIMIT_SECONDS, String.valueOf(config.getBacktrackTimeLimitSeconds()), AlgorithmConfig.ValueType.NUMBER, "Giới hạn thời gian chạy backtracking (giây)");
    }

    private boolean getBooleanValue(String paramKey, boolean defaultValue) {
        return configRepository.findByParamKey(paramKey)
                .map(c -> Boolean.parseBoolean(c.getParamValue()))
                .orElse(defaultValue);
    }

    private java.math.BigDecimal getBigDecimalValue(String paramKey, double defaultValue) {
        return configRepository.findByParamKey(paramKey)
                .map(c -> {
                    try {
                        return new java.math.BigDecimal(c.getParamValue());
                    } catch (NumberFormatException e) {
                        return java.math.BigDecimal.valueOf(defaultValue);
                    }
                })
                .orElse(java.math.BigDecimal.valueOf(defaultValue));
    }

    /**
     * Runtime configuration record for algorithm execution.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlgorithmRuntimeConfig {
        private int maxIterations;
        private java.math.BigDecimal weekendWeight;
        private int overnightRecoveryHours;
        private java.math.BigDecimal greedyCoverageThreshold;
        private java.math.BigDecimal balanceScoreMin;
        private boolean autoCompensationEnabled;
        private int backtrackTimeLimitSeconds;
    }
}
