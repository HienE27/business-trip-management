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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

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
    public static final String MIN_STAFF_PER_SHIFT = "min_staff_per_shift";
    public static final String MAX_STAFF_PER_SHIFT = "max_staff_per_shift";
    public static final String MIN_SHIFTS_PER_STAFF = "min_shifts_per_staff";
    public static final String MAX_SHIFTS_PER_STAFF = "max_shifts_per_staff";

    public List<AlgorithmConfigDTO> getAllConfigs() {
        // OPTIMIZATION: use JOIN FETCH to avoid N+1 on updatedBy lazy loading
        return configRepository.findAllWithUpdatedBy().stream()
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
        return java.util.Optional.of(new AutoGenConfig(
                enabled,
                getIntValue(AUTO_GEN_L01_PER_DAY, 1),
                getIntValue(AUTO_GEN_L02_PER_DAY, 2),
                getIntValue(AUTO_GEN_L03_PER_DAY, 2),
                getIntValue(AUTO_GEN_L04_PER_DAY, 2),
                getIntValue(AUTO_GEN_L01_PER_WEEK, 1),
                getIntValue(AUTO_GEN_L02_PER_WEEK, 3),
                getIntValue(AUTO_GEN_L03_PER_WEEK, 2),
                getIntValue(AUTO_GEN_L04_PER_WEEK, 1),
                getStringValue(AUTO_GEN_HOLIDAY_MODE, "SKIP")
        ));
    }

    /**
     * Save auto-generation configuration.
     */
    @Transactional
    public void saveAutoGenConfig(AutoGenConfig config) {
        upsert(AUTO_GEN_ENABLED, String.valueOf(config.enabled()), AlgorithmConfig.ValueType.BOOLEAN,
                "Tự động tạo yêu cầu nhân sự khi mở kỳ lịch mới. Bật ON để hệ thống tự đề xuất lịch cho từng người.");
        upsert(AUTO_GEN_L01_PER_DAY, String.valueOf(config.l01RequiredPerDay()), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối thiểu cần xếp cho ca L01 (Lịch trực 24/24) mỗi ngày. Tăng nếu tỷ lệ phủ L01 chưa đạt.");
        upsert(AUTO_GEN_L02_PER_DAY, String.valueOf(config.l02RequiredPerDay()), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối thiểu cần xếp cho ca L02 (Lịch thông tầm) mỗi ngày. Điều chỉnh theo nhu cầu khám thường.");
        upsert(AUTO_GEN_L03_PER_DAY, String.valueOf(config.l03RequiredPerDay()), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối thiểu cần xếp cho ca L03 (Phòng khám dịch vụ) mỗi ngày.");
        upsert(AUTO_GEN_L04_PER_DAY, String.valueOf(config.l04RequiredPerDay()), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối thiểu cần xếp cho ca L04 (Phòng khám chuyên gia) mỗi ngày.");
        upsert(AUTO_GEN_L01_PER_WEEK, String.valueOf(config.minL01PerWeek()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L01 tối thiểu mỗi người trong 1 tuần. Giúp đảm bảo công bằng phân bổ trực đêm cho nhân sự.");
        upsert(AUTO_GEN_L02_PER_WEEK, String.valueOf(config.minL02PerWeek()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L02 tối thiểu mỗi người trong 1 tuần. Đảm bảo mỗi người có đủ ca ngày theo quy định.");
        upsert(AUTO_GEN_L03_PER_WEEK, String.valueOf(config.minL03PerWeek()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L03 tối thiểu mỗi người trong 1 tuần.");
        upsert(AUTO_GEN_L04_PER_WEEK, String.valueOf(config.minL04PerWeek()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L04 tối thiểu mỗi người trong 1 tuần.");
        upsert(AUTO_GEN_HOLIDAY_MODE, config.holidayMode(), AlgorithmConfig.ValueType.STRING,
                "Xử lý khi gặp ngày lễ: SKIP = bỏ qua ngày lễ (không xếp lịch), PARTIAL = vẫn xếp lịch nhưng giảm cường độ.");
    }

    /**
     * Sync descriptions for all well-known algorithm config parameters
     * using the canonical strings defined in code.
     * Unknown params are kept intact.
     */
    @Transactional
    public Map<String, String> syncDescriptions() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        upsert(AUTO_GEN_ENABLED, getStringValue(AUTO_GEN_ENABLED, "true"), AlgorithmConfig.ValueType.BOOLEAN,
                "Tự động tạo yêu cầu nhân sự khi mở kỳ lịch mới. Bật ON để hệ thống tự đề xuất lịch cho từng người.");
        map.put(AUTO_GEN_ENABLED, "OK");
        upsert(AUTO_GEN_L01_PER_DAY, getStringValue(AUTO_GEN_L01_PER_DAY, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối thiểu cần xếp cho ca L01 (Lịch trực 24/24) mỗi ngày. Tăng nếu tỷ lệ phủ L01 chưa đạt.");
        map.put(AUTO_GEN_L01_PER_DAY, "OK");
        upsert(AUTO_GEN_L02_PER_DAY, getStringValue(AUTO_GEN_L02_PER_DAY, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối thiểu cần xếp cho ca L02 (Lịch thông tầm) mỗi ngày. Điều chỉnh theo nhu cầu khám thường.");
        map.put(AUTO_GEN_L02_PER_DAY, "OK");
        upsert(AUTO_GEN_L03_PER_DAY, getStringValue(AUTO_GEN_L03_PER_DAY, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối thiểu cần xếp cho ca L03 (Phòng khám dịch vụ) mỗi ngày.");
        map.put(AUTO_GEN_L03_PER_DAY, "OK");
        upsert(AUTO_GEN_L04_PER_DAY, getStringValue(AUTO_GEN_L04_PER_DAY, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối thiểu cần xếp cho ca L04 (Phòng khám chuyên gia) mỗi ngày.");
        map.put(AUTO_GEN_L04_PER_DAY, "OK");
        upsert(AUTO_GEN_L01_PER_WEEK, getStringValue(AUTO_GEN_L01_PER_WEEK, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L01 tối thiểu mỗi người trong 1 tuần. Giúp đảm bảo công bằng phân bổ trực đêm cho nhân sự.");
        map.put(AUTO_GEN_L01_PER_WEEK, "OK");
        upsert(AUTO_GEN_L02_PER_WEEK, getStringValue(AUTO_GEN_L02_PER_WEEK, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L02 tối thiểu mỗi người trong 1 tuần. Đảm bảo mỗi người có đủ ca ngày theo quy định.");
        map.put(AUTO_GEN_L02_PER_WEEK, "OK");
        upsert(AUTO_GEN_L03_PER_WEEK, getStringValue(AUTO_GEN_L03_PER_WEEK, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L03 tối thiểu mỗi người trong 1 tuần.");
        map.put(AUTO_GEN_L03_PER_WEEK, "OK");
        upsert(AUTO_GEN_L04_PER_WEEK, getStringValue(AUTO_GEN_L04_PER_WEEK, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L04 tối thiểu mỗi người trong 1 tuần.");
        map.put(AUTO_GEN_L04_PER_WEEK, "OK");
        upsert(AUTO_GEN_HOLIDAY_MODE, getStringValue(AUTO_GEN_HOLIDAY_MODE, "SKIP"), AlgorithmConfig.ValueType.STRING,
                "Xử lý khi gặp ngày lễ: SKIP = bỏ qua ngày lễ (không xếp lịch), PARTIAL = vẫn xếp lịch nhưng giảm cường độ.");
        map.put(AUTO_GEN_HOLIDAY_MODE, "OK");
        upsert(MAX_ITERATIONS, getStringValue(MAX_ITERATIONS, "1000"), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp tối đa cho thuật toán backtracking. Tăng lên nếu thuật toán chưa hết thời gian mà vẫn chưa tìm được lời giải tốt; giảm xuống nếu chạy quá lâu.");
        map.put(MAX_ITERATIONS, "OK");
        upsert(WEEKEND_WEIGHT, getStringValue(WEEKEND_WEIGHT, "2"), AlgorithmConfig.ValueType.NUMBER,
                "Hệ số phạt khi xếp lịch cho người vào thứ 7 / chủ nhật. Giá trị càng cao → thuật toán càng tránh xếp ca cuối tuần. Đặt 1 để tắt ưu tiên.");
        map.put(WEEKEND_WEIGHT, "OK");
        upsert(OVERNIGHT_RECOVERY_HOURS, getStringValue(OVERNIGHT_RECOVERY_HOURS, "24"), AlgorithmConfig.ValueType.NUMBER,
                "Khoảng cách nghỉ bắt buộc giữa hai ca trực 24/24 liên tiếp của cùng một người. Thường đặt 24h để đảm bảo nghỉ ngơi đủ.");
        map.put(OVERNIGHT_RECOVERY_HOURS, "OK");
        upsert(GREEDY_COVERAGE_THRESHOLD, getStringValue(GREEDY_COVERAGE_THRESHOLD, "0.85"), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng phủ lịch tối thiểu (0.0–1.0). Khi tỷ lệ lịch đã phủ đạt mức này, thuật toán greedy sẽ dừng sớm. Giảm → chạy nhanh hơn; tăng → phủ kỹ hơn.");
        map.put(GREEDY_COVERAGE_THRESHOLD, "OK");
        upsert(BALANCE_SCORE_MIN, getStringValue(BALANCE_SCORE_MIN, "0.75"), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng điểm cân bằng tải tối thiểu (0.0–1.0). Cao → phân bổ ca trực công bằng hơn nhưng có thể khó đạt; thấp → dễ đáp ứng nhưng có thể thiên lệch.");
        map.put(BALANCE_SCORE_MIN, "OK");
        upsert(AUTO_COMPENSATION_ENABLED, getStringValue(AUTO_COMPENSATION_ENABLED, "true"), AlgorithmConfig.ValueType.BOOLEAN,
                "Tự động tạo ngày nghỉ bù sau mỗi ca trực 24/24 theo quy tắc bù ca đã quy định. Tắt OFF nếu muốn quản lý nghỉ bù thủ công.");
        map.put(AUTO_COMPENSATION_ENABLED, "OK");
        upsert(BACKTRACK_TIME_LIMIT_SECONDS, getStringValue(BACKTRACK_TIME_LIMIT_SECONDS, "60"), AlgorithmConfig.ValueType.NUMBER,
                "Thời gian tối đa cho phép thuật toán backtracking chạy (giây). Hết thời gian → dừng và trả kết quả tốt nhất đã tìm được.");
        map.put(BACKTRACK_TIME_LIMIT_SECONDS, "OK");
        return map;
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
                .minStaffPerShift(getIntValue(MIN_STAFF_PER_SHIFT, 1))
                .maxStaffPerShift(getIntValue(MAX_STAFF_PER_SHIFT, 0))
                .minShiftsPerStaff(getIntValue(MIN_SHIFTS_PER_STAFF, 0))
                .maxShiftsPerStaff(getIntValue(MAX_SHIFTS_PER_STAFF, 0))
                .build();
    }

    /**
     * Save algorithm runtime configuration.
     */
    @Transactional
    public void saveRuntimeConfig(AlgorithmRuntimeConfig config) {
        upsert(MAX_ITERATIONS, String.valueOf(config.getMaxIterations()), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp tối đa cho thuật toán backtracking. Tăng lên nếu thuật toán chưa hết thời gian mà vẫn chưa tìm được lời giải tốt; giảm xuống nếu chạy quá lâu.");
        upsert(WEEKEND_WEIGHT, String.valueOf(config.getWeekendWeight()), AlgorithmConfig.ValueType.NUMBER,
                "Hệ số phạt khi xếp lịch cho người vào thứ 7 / chủ nhật. Giá trị càng cao → thuật toán càng tránh xếp ca cuối tuần. Đặt 1 để tắt ưu tiên.");
        upsert(OVERNIGHT_RECOVERY_HOURS, String.valueOf(config.getOvernightRecoveryHours()), AlgorithmConfig.ValueType.NUMBER,
                "Khoảng cách nghỉ bắt buộc giữa hai ca trực 24/24 liên tiếp của cùng một người. Thường đặt 24h để đảm bảo nghỉ ngơi đủ.");
        upsert(GREEDY_COVERAGE_THRESHOLD, String.valueOf(config.getGreedyCoverageThreshold()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng phủ lịch tối thiểu (0.0–1.0). Khi tỷ lệ lịch đã phủ đạt mức này, thuật toán greedy sẽ dừng sớm. Giảm → chạy nhanh hơn; tăng → phủ kỹ hơn.");
        upsert(BALANCE_SCORE_MIN, String.valueOf(config.getBalanceScoreMin()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng điểm cân bằng tải tối thiểu (0.0–1.0). Cao → phân bổ ca trực công bằng hơn nhưng có thể khó đạt; thấp → dễ đáp ứng nhưng có thể thiên lệch.");
        upsert(AUTO_COMPENSATION_ENABLED, String.valueOf(config.isAutoCompensationEnabled()), AlgorithmConfig.ValueType.BOOLEAN,
                "Tự động tạo ngày nghỉ bù sau mỗi ca trực 24/24 theo quy tắc bù ca đã quy định. Tắt OFF nếu muốn quản lý nghỉ bù thủ công.");
        upsert(BACKTRACK_TIME_LIMIT_SECONDS, String.valueOf(config.getBacktrackTimeLimitSeconds()), AlgorithmConfig.ValueType.NUMBER,
                "Thời gian tối đa cho phép thuật toán backtracking chạy (giây). Hết thời gian → dừng và trả kết quả tốt nhất đã tìm được.");
        upsert(MIN_STAFF_PER_SHIFT, String.valueOf(config.getMinStaffPerShift()), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối thiểu mỗi ca. Đặt 0 để bỏ qua giới hạn này. Nếu không đủ nhân sự đạt ngưỡng, thuật toán sẽ cảnh báo nhưng vẫn xếp.");
        upsert(MAX_STAFF_PER_SHIFT, String.valueOf(config.getMaxStaffPerShift()), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối đa mỗi ca. Đặt 0 để không giới hạn. Giới hạn này chỉ áp dụng khi yêu cầu ca có requiredStaffCount > maxStaffPerShift.");
        upsert(MIN_SHIFTS_PER_STAFF, String.valueOf(config.getMinShiftsPerStaff()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca trực tối thiểu mỗi nhân sự trong kỳ. Đặt 0 để bỏ qua. Giúp đảm bảo mỗi người đều có ít nhất N ca trong kỳ.");
        upsert(MAX_SHIFTS_PER_STAFF, String.valueOf(config.getMaxShiftsPerStaff()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca trực tối đa mỗi nhân sự trong kỳ. Đặt 0 để dùng maxShiftsPerMonth của nhân sự. Giới hạn này ngược lại với min — ngăn không ai bị quá tải.");
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
    @lombok.Builder(access = lombok.AccessLevel.PRIVATE)
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
        private int minStaffPerShift;
        private int maxStaffPerShift;
        private int minShiftsPerStaff;
        private int maxShiftsPerStaff;
    }
}
