package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
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
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.repository.AlgorithmConfigAuditRepository;
import com.hospital.scheduler.repository.AlgorithmConfigRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AlgorithmConfigService {

    private final AlgorithmConfigRepository configRepository;
    private final AlgorithmConfigAuditRepository auditRepository;
    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final ObjectMapper objectMapper;

    // Auto-generate config param keys
    public static final String AUTO_GEN_ENABLED = "auto_gen_enabled";
    public static final String AUTO_GEN_L01_MIN_PER_DAY = "auto_gen_l01_min_per_day";
    public static final String AUTO_GEN_L02_MIN_PER_DAY = "auto_gen_l02_min_per_day";
    public static final String AUTO_GEN_L03_MIN_PER_DAY = "auto_gen_l03_min_per_day";
    public static final String AUTO_GEN_L04_MIN_PER_DAY = "auto_gen_l04_min_per_day";
    public static final String AUTO_GEN_L01_MAX_PER_DAY = "auto_gen_l01_max_per_day";
    public static final String AUTO_GEN_L02_MAX_PER_DAY = "auto_gen_l02_max_per_day";
    public static final String AUTO_GEN_L03_MAX_PER_DAY = "auto_gen_l03_max_per_day";
    public static final String AUTO_GEN_L04_MAX_PER_DAY = "auto_gen_l04_max_per_day";
    public static final String AUTO_GEN_L01_MIN_PER_WEEK = "auto_gen_l01_min_per_week";
    public static final String AUTO_GEN_L02_MIN_PER_WEEK = "auto_gen_l02_min_per_week";
    public static final String AUTO_GEN_L03_MIN_PER_WEEK = "auto_gen_l03_min_per_week";
    public static final String AUTO_GEN_L04_MIN_PER_WEEK = "auto_gen_l04_min_per_week";
    public static final String AUTO_GEN_L01_MAX_PER_WEEK = "auto_gen_l01_max_per_week";
    public static final String AUTO_GEN_L02_MAX_PER_WEEK = "auto_gen_l02_max_per_week";
    public static final String AUTO_GEN_L03_MAX_PER_WEEK = "auto_gen_l03_max_per_week";
    public static final String AUTO_GEN_L04_MAX_PER_WEEK = "auto_gen_l04_max_per_week";
    public static final String AUTO_GEN_HOLIDAY_MODE = "auto_gen_holiday_mode";
    public static final String AUTO_GEN_L04_CROSS_SPECIALTY = "auto_gen_l04_cross_specialty";
    public static final String AUTO_GEN_L04_CROSS_SPECIALTY_RATIO = "auto_gen_l04_cross_specialty_ratio";
    public static final String AUTO_GEN_L04_BALANCE_STRATEGY = "auto_gen_l04_balance_strategy";
    public static final String AUTO_GEN_L01_ALLOWED_SPECIALTIES = "auto_gen_l01_allowed_specialties";
    public static final String AUTO_GEN_L02_ALLOWED_SPECIALTIES = "auto_gen_l02_allowed_specialties";
    public static final String AUTO_GEN_L03_ALLOWED_SPECIALTIES = "auto_gen_l03_allowed_specialties";
    // Target ca/người/tháng — input editable cho recommendAutoGenConfig.
    // Persist vào DB để UI refresh không reset default.
    public static final String AUTO_GEN_L01_TARGET_PER_MONTH = "auto_gen_l01_target_per_month";
    public static final String AUTO_GEN_L02_TARGET_PER_MONTH = "auto_gen_l02_target_per_month";
    public static final String AUTO_GEN_L03_TARGET_PER_MONTH = "auto_gen_l03_target_per_month";
    public static final String AUTO_GEN_L04_TARGET_PER_MONTH = "auto_gen_l04_target_per_month";

    // Algorithm runtime config param keys
    public static final String ARRANGEMENT_MODE = "arrangement_mode";
    public static final String WEEKEND_WEIGHT = "weekend_weight";
    public static final String OVERNIGHT_RECOVERY_HOURS = "overnight_recovery_hours";
    public static final String GREEDY_COVERAGE_THRESHOLD = "greedy_coverage_threshold";
    public static final String BALANCE_SCORE_MIN = "balance_score_min";
    public static final String AUTO_COMPENSATION_ENABLED = "auto_compensation_enabled";
    public static final String MAX_STAFF_PER_SHIFT = "max_staff_per_shift";
    public static final String MAX_SHIFTS_PER_STAFF = "max_shifts_per_staff";
    public static final String MAX_SHIFTS_PER_DAY = "max_shifts_per_day";
    public static final String BEAM_WIDTH = "beam_width";
    public static final String AUTO_ADJUST_CONFIG = "auto_adjust_config";

    // ScheduleQualityScorer weight keys
    public static final String SCORER_COVERAGE_WEIGHT = "scorer_coverage_weight";
    public static final String SCORER_FAIRNESS_WEIGHT = "scorer_fairness_weight";
    public static final String SCORER_CONSTRAINT_WEIGHT = "scorer_constraint_weight";
    public static final String SCORER_PASS_THRESHOLD = "scorer_pass_threshold";
    public static final String SCORER_HARD_VIOLATION_PENALTY = "scorer_hard_violation_penalty";
    public static final String SCORER_SOFT_VIOLATION_PENALTY = "scorer_soft_violation_penalty";
    public static final String SCORER_TARGET_CV = "scorer_target_cv";
    public static final String SCORER_WORST_CV = "scorer_worst_cv";

    // Rebalance round keys
    public static final String REBALANCE_ROUNDS_TOTAL = "rebalance_rounds_total";
    public static final String REBALANCE_ROUNDS_PER_TYPE = "rebalance_rounds_per_type";
    public static final String REBALANCE_ROUNDS_EG = "rebalance_rounds_eg";
    public static final String REBALANCE_ROUNDS_POST_SAVE = "rebalance_rounds_post_save";

    public List<AlgorithmConfigDTO> getAllConfigs() {
        // OPTIMIZATION: use JOIN FETCH to avoid N+1 on updatedBy lazy loading
        return configRepository.findAllWithUpdatedBy().stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Paginated variant of {@link #getAllConfigs()}. Uses {@link org.springframework.data.jpa.repository.JpaSpecificationExecutor}
     * pattern via {@link com.hospital.scheduler.repository.AlgorithmConfigRepository} — for now
     * we rely on the built-in {@code findAll(Pageable)} plus a separate fetch-by-id step
     * to populate the {@code updatedBy} relationship without N+1 lazy loads.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AlgorithmConfigDTO> getConfigsPage(
            org.springframework.data.domain.Pageable pageable) {
        return configRepository.findAll(
                org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(),
                        pageable.getPageSize(),
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.ASC, "paramKey")))
                .map(this::toDTO);
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
        recordAudit(request.getParamKey(), null, request.getParamValue(),
                AlgorithmConfigAudit.Action.CREATE);
        return toDTO(saved);
    }

    @Transactional
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
        if (request.getParamValue() != null && !java.util.Objects.equals(oldValue, request.getParamValue())) {
            recordAudit(paramKey, oldValue, request.getParamValue(),
                    AlgorithmConfigAudit.Action.UPDATE);
        }
        return toDTO(saved);
    }

    @Transactional
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
            // best-effort, never fail main op
            org.slf4j.LoggerFactory.getLogger(AlgorithmConfigService.class)
                    .warn("Failed to record audit for {}: {}", paramKey, e.getMessage());
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
        // Bulk-load once, then read every key from the in-memory map — replaces
        // the 25 separate findByParamKey SELECTs that were making the
        // algorithm-config page take 5+ seconds to load.
        java.util.Map<String, String> cache = loadConfigCache();
        String enabledRaw = cache.get(AUTO_GEN_ENABLED);
        // Default to true so auto-scheduling works out-of-the-box.
        boolean enabled = enabledRaw == null || Boolean.parseBoolean(enabledRaw);
        // Always return a config with defaults — even if AUTO_GEN_ENABLED is missing from DB,
        // fall back to defaults so auto-scheduling works out-of-the-box without manual config setup.
        return java.util.Optional.of(new AutoGenConfig(
                enabled,
                getIntValue(AUTO_GEN_L01_MIN_PER_DAY, 1, cache),
                getIntValue(AUTO_GEN_L02_MIN_PER_DAY, 1, cache),
                getIntValue(AUTO_GEN_L03_MIN_PER_DAY, 1, cache),
                getIntValue(AUTO_GEN_L04_MIN_PER_DAY, 1, cache),
                getIntValue(AUTO_GEN_L01_MAX_PER_DAY, 0, cache),
                getIntValue(AUTO_GEN_L02_MAX_PER_DAY, 0, cache),
                getIntValue(AUTO_GEN_L03_MAX_PER_DAY, 0, cache),
                getIntValue(AUTO_GEN_L04_MAX_PER_DAY, 0, cache),
                getIntValue(AUTO_GEN_L01_MIN_PER_WEEK, 1, cache),
                getIntValue(AUTO_GEN_L02_MIN_PER_WEEK, 2, cache),
                getIntValue(AUTO_GEN_L03_MIN_PER_WEEK, 1, cache),
                getIntValue(AUTO_GEN_L04_MIN_PER_WEEK, 1, cache),
                getIntValue(AUTO_GEN_L01_MAX_PER_WEEK, 0, cache),
                getIntValue(AUTO_GEN_L02_MAX_PER_WEEK, 0, cache),
                getIntValue(AUTO_GEN_L03_MAX_PER_WEEK, 0, cache),
                getIntValue(AUTO_GEN_L04_MAX_PER_WEEK, 0, cache),
                getStringValue(AUTO_GEN_HOLIDAY_MODE, "SKIP", cache),
                getStringListValue("AUTO_GEN_REMOVED_SHIFT_TYPES", cache),
                getBooleanValue(AUTO_GEN_L04_CROSS_SPECIALTY, false, cache),
                getFloatValue(AUTO_GEN_L04_CROSS_SPECIALTY_RATIO, 0.3f, cache),
                getStringListValue("AUTO_GEN_L04_ALLOWED_SPECIALTIES", cache), // null/empty = all specialties
                // L01/L02/L03: null/empty → fallback to CORE_ELIGIBLE_SPECIALTIES (Ngoại, Nội) trong StaffShiftTypeEligibility
                getStringListValue(AUTO_GEN_L01_ALLOWED_SPECIALTIES, cache),
                getStringListValue(AUTO_GEN_L02_ALLOWED_SPECIALTIES, cache),
                getStringListValue(AUTO_GEN_L03_ALLOWED_SPECIALTIES, cache),
                // Target ca/người/tháng — default L01-L03=2, L04=5 (hợp lý cho bệnh viện ~900 NS)
                getIntValue(AUTO_GEN_L01_TARGET_PER_MONTH, 2, cache),
                getIntValue(AUTO_GEN_L02_TARGET_PER_MONTH, 2, cache),
                getIntValue(AUTO_GEN_L03_TARGET_PER_MONTH, 2, cache),
                getIntValue(AUTO_GEN_L04_TARGET_PER_MONTH, 5, cache),
                // Chiến lược cân bằng L04 cross-specialty — frontend default "FAIR_DISTRIBUTE".
                getStringValue(AUTO_GEN_L04_BALANCE_STRATEGY, "FAIR_DISTRIBUTE", cache)
        ));
    }

    /**
     * Save auto-generation configuration.
     */
    @Transactional
    public void saveAutoGenConfig(AutoGenConfig config) {
        upsert(AUTO_GEN_ENABLED, String.valueOf(config.enabled()), AlgorithmConfig.ValueType.BOOLEAN,
                "Tự động tạo yêu cầu nhân sự khi mở kỳ lịch mới.");
        upsert(AUTO_GEN_L01_MIN_PER_DAY, String.valueOf(config.l01MinPerDay()), AlgorithmConfig.ValueType.NUMBER, "Mục tiêu nhân sự L01 mỗi ngày; thuật toán cố gắng đạt nhưng không phá ràng buộc cứng.");
        upsert(AUTO_GEN_L02_MIN_PER_DAY, String.valueOf(config.l02MinPerDay()), AlgorithmConfig.ValueType.NUMBER, "Mục tiêu nhân sự L02 mỗi ngày; thuật toán cố gắng đạt nhưng không phá ràng buộc cứng.");
        upsert(AUTO_GEN_L03_MIN_PER_DAY, String.valueOf(config.l03MinPerDay()), AlgorithmConfig.ValueType.NUMBER, "Mục tiêu nhân sự L03 mỗi ngày; thuật toán cố gắng đạt nhưng không phá ràng buộc cứng.");
        upsert(AUTO_GEN_L04_MIN_PER_DAY, String.valueOf(config.l04MinPerDay()), AlgorithmConfig.ValueType.NUMBER, "Mục tiêu nhân sự L04 mỗi ngày/chuyên khoa; thuật toán cố gắng đạt nhưng không phá ràng buộc cứng.");
        upsert(AUTO_GEN_L01_MAX_PER_DAY, String.valueOf(config.l01MaxPerDay()), AlgorithmConfig.ValueType.NUMBER, "Trần khuyến nghị L01 mỗi ngày khi sinh mục tiêu. 0 = không đặt trần.");
        upsert(AUTO_GEN_L02_MAX_PER_DAY, String.valueOf(config.l02MaxPerDay()), AlgorithmConfig.ValueType.NUMBER, "Trần khuyến nghị L02 mỗi ngày khi sinh mục tiêu. 0 = không đặt trần.");
        upsert(AUTO_GEN_L03_MAX_PER_DAY, String.valueOf(config.l03MaxPerDay()), AlgorithmConfig.ValueType.NUMBER, "Trần khuyến nghị L03 mỗi ngày khi sinh mục tiêu. 0 = không đặt trần.");
        upsert(AUTO_GEN_L04_MAX_PER_DAY, String.valueOf(config.l04MaxPerDay()), AlgorithmConfig.ValueType.NUMBER, "Trần khuyến nghị L04 mỗi ngày/chuyên khoa khi sinh mục tiêu. 0 = không đặt trần.");
        upsert(AUTO_GEN_L01_MIN_PER_WEEK, String.valueOf(config.l01MinPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L01 tối thiểu mỗi người mỗi tuần.");
        upsert(AUTO_GEN_L02_MIN_PER_WEEK, String.valueOf(config.l02MinPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L02 tối thiểu mỗi người mỗi tuần.");
        upsert(AUTO_GEN_L03_MIN_PER_WEEK, String.valueOf(config.l03MinPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L03 tối thiểu mỗi người mỗi tuần.");
        upsert(AUTO_GEN_L04_MIN_PER_WEEK, String.valueOf(config.l04MinPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L04 tối thiểu mỗi người mỗi tuần.");
        upsert(AUTO_GEN_L01_MAX_PER_WEEK, String.valueOf(config.l01MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L01 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
        upsert(AUTO_GEN_L02_MAX_PER_WEEK, String.valueOf(config.l02MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L02 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
        upsert(AUTO_GEN_L03_MAX_PER_WEEK, String.valueOf(config.l03MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L03 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
        upsert(AUTO_GEN_L04_MAX_PER_WEEK, String.valueOf(config.l04MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L04 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
        upsert(AUTO_GEN_HOLIDAY_MODE, config.holidayMode(), AlgorithmConfig.ValueType.STRING,
                "Xử lý ngày lễ: SKIP = bỏ qua, PARTIAL = giảm cường độ.");
        String removedCsv = config.removedShiftTypes() == null
                ? ""
                : String.join(",", config.removedShiftTypes());
        upsert("AUTO_GEN_REMOVED_SHIFT_TYPES", removedCsv, AlgorithmConfig.ValueType.STRING,
                "Danh sách mã loại lịch (L01..L04) bị bỏ qua khi tự động tạo yêu cầu. Phân tách bằng dấu phẩy. Rỗng = không bỏ.");
        upsert(AUTO_GEN_L04_CROSS_SPECIALTY, String.valueOf(config.l04CrossSpecialty()), AlgorithmConfig.ValueType.BOOLEAN,
                "Cho phép gán nhân sự từ chuyên khoa khác vào L04 khi chuyên khoa gốc thiếu nhân sự.");
        upsert(AUTO_GEN_L04_CROSS_SPECIALTY_RATIO, String.valueOf(config.l04CrossSpecialtyRatio()), AlgorithmConfig.ValueType.NUMBER,
                "Tỷ lệ tối đa nhân sự cross-specialty cho L04 (0.0-1.0). Ví dụ: 0.3 = tối đa 30% nhân sự được gán từ chuyên khoa khác.");
        // Lưu danh sách specialties được phép gán L04 (comma-separated)
        String allowedSpecs = config.l04AllowedSpecialties() == null || config.l04AllowedSpecialties().isEmpty()
                ? "" : String.join(",", config.l04AllowedSpecialties());
        upsert("AUTO_GEN_L04_ALLOWED_SPECIALTIES", allowedSpecs, AlgorithmConfig.ValueType.STRING,
                "Danh sách chuyên khoa được gán L04. Rỗng = tất cả chuyên khoa. Ví dụ: Ngoại,Nội,Sản");
        // L01/L02/L03 allowed specialties (CSV). Rỗng → dùng default CORE = Ngoại,Nội.
        String l01Csv = config.l01AllowedSpecialties() == null ? "" : String.join(",", config.l01AllowedSpecialties());
        upsert(AUTO_GEN_L01_ALLOWED_SPECIALTIES, l01Csv, AlgorithmConfig.ValueType.STRING,
                "Danh sách chuyên khoa được gán L01 (trực 24/24). Rỗng = mặc định Ngoại,Nội. Ví dụ: Ngoại,Nội,Sản,Nhi,Mắt,Răng");
        String l02Csv = config.l02AllowedSpecialties() == null ? "" : String.join(",", config.l02AllowedSpecialties());
        upsert(AUTO_GEN_L02_ALLOWED_SPECIALTIES, l02Csv, AlgorithmConfig.ValueType.STRING,
                "Danh sách chuyên khoa được gán L02 (thông tầm). Rỗng = mặc định Ngoại,Nội.");
        String l03Csv = config.l03AllowedSpecialties() == null ? "" : String.join(",", config.l03AllowedSpecialties());
        upsert(AUTO_GEN_L03_ALLOWED_SPECIALTIES, l03Csv, AlgorithmConfig.ValueType.STRING,
                "Danh sách chuyên khoa được gán L03 (phòng khám dịch vụ). Rỗng = mặc định Ngoại,Nội.");
        // Target ca/người/tháng — input editable cho recommend.
        upsert(AUTO_GEN_L01_TARGET_PER_MONTH, String.valueOf(config.l01TargetPerMonth()),
                AlgorithmConfig.ValueType.NUMBER, "Mục tiêu ca L01 mỗi nhân sự mỗi tháng (input cho đề xuất cấu hình tự động).");
        upsert(AUTO_GEN_L02_TARGET_PER_MONTH, String.valueOf(config.l02TargetPerMonth()),
                AlgorithmConfig.ValueType.NUMBER, "Mục tiêu ca L02 mỗi nhân sự mỗi tháng (input cho đề xuất cấu hình tự động).");
        upsert(AUTO_GEN_L03_TARGET_PER_MONTH, String.valueOf(config.l03TargetPerMonth()),
                AlgorithmConfig.ValueType.NUMBER, "Mục tiêu ca L03 mỗi nhân sự mỗi tháng (input cho đề xuất cấu hình tự động).");
        upsert(AUTO_GEN_L04_TARGET_PER_MONTH, String.valueOf(config.l04TargetPerMonth()),
                AlgorithmConfig.ValueType.NUMBER, "Mục tiêu ca L04 mỗi nhân sự mỗi tháng (input cho đề xuất cấu hình tự động).");
        upsert(AUTO_GEN_L04_BALANCE_STRATEGY,
                config.l04BalanceStrategy() != null ? config.l04BalanceStrategy() : "FAIR_DISTRIBUTE",
                AlgorithmConfig.ValueType.STRING,
                "Chiến lược cân bằng L04 cross-specialty: STRICT_MATCH_ONLY | FAIR_DISTRIBUTE | WEIGHTED_FAIR.");
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
        upsert(AUTO_GEN_L01_MIN_PER_DAY, getStringValue(AUTO_GEN_L01_MIN_PER_DAY, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Mục tiêu nhân sự L01 (Lịch trực 24/24) mỗi ngày. Thuật toán cố gắng đạt nhưng không phá ràng buộc cứng.");
        map.put(AUTO_GEN_L01_MIN_PER_DAY, "OK");
        upsert(AUTO_GEN_L02_MIN_PER_DAY, getStringValue(AUTO_GEN_L02_MIN_PER_DAY, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Mục tiêu nhân sự L02 (Lịch thông tầm) mỗi ngày. Điều chỉnh theo nhu cầu, phần thiếu sẽ hiển thị trong preview.");
        map.put(AUTO_GEN_L02_MIN_PER_DAY, "OK");
        upsert(AUTO_GEN_L03_MIN_PER_DAY, getStringValue(AUTO_GEN_L03_MIN_PER_DAY, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Mục tiêu nhân sự L03 (Phòng khám dịch vụ) mỗi ngày. Đây là mục tiêu mềm của thuật toán.");
        map.put(AUTO_GEN_L03_MIN_PER_DAY, "OK");
        upsert(AUTO_GEN_L04_MIN_PER_DAY, getStringValue(AUTO_GEN_L04_MIN_PER_DAY, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Mục tiêu nhân sự L04 (Phòng khám chuyên gia) mỗi ngày/chuyên khoa. Đây là mục tiêu mềm của thuật toán.");
        map.put(AUTO_GEN_L04_MIN_PER_DAY, "OK");
        upsert(AUTO_GEN_L01_MIN_PER_WEEK, getStringValue(AUTO_GEN_L01_MIN_PER_WEEK, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L01 tối thiểu mỗi người trong 1 tuần. Giúp đảm bảo công bằng phân bổ trực đêm cho nhân sự.");
        map.put(AUTO_GEN_L01_MIN_PER_WEEK, "OK");
        upsert(AUTO_GEN_L02_MIN_PER_WEEK, getStringValue(AUTO_GEN_L02_MIN_PER_WEEK, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L02 tối thiểu mỗi người trong 1 tuần. Đảm bảo mỗi người có đủ ca ngày theo quy định.");
        map.put(AUTO_GEN_L02_MIN_PER_WEEK, "OK");
        upsert(AUTO_GEN_L03_MIN_PER_WEEK, getStringValue(AUTO_GEN_L03_MIN_PER_WEEK, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L03 tối thiểu mỗi người trong 1 tuần.");
        map.put(AUTO_GEN_L03_MIN_PER_WEEK, "OK");
        upsert(AUTO_GEN_L04_MIN_PER_WEEK, getStringValue(AUTO_GEN_L04_MIN_PER_WEEK, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L04 tối thiểu mỗi người trong 1 tuần.");
        map.put(AUTO_GEN_L04_MIN_PER_WEEK, "OK");
        upsert(AUTO_GEN_L01_MAX_PER_DAY, getStringValue(AUTO_GEN_L01_MAX_PER_DAY, "0"), AlgorithmConfig.ValueType.NUMBER,
                "Trần khuyến nghị L01 mỗi ngày khi sinh mục tiêu. 0 = không đặt trần.");
        map.put(AUTO_GEN_L01_MAX_PER_DAY, "OK");
        upsert(AUTO_GEN_L02_MAX_PER_DAY, getStringValue(AUTO_GEN_L02_MAX_PER_DAY, "0"), AlgorithmConfig.ValueType.NUMBER,
                "Trần khuyến nghị L02 mỗi ngày khi sinh mục tiêu. 0 = không đặt trần.");
        map.put(AUTO_GEN_L02_MAX_PER_DAY, "OK");
        upsert(AUTO_GEN_L03_MAX_PER_DAY, getStringValue(AUTO_GEN_L03_MAX_PER_DAY, "0"), AlgorithmConfig.ValueType.NUMBER,
                "Trần khuyến nghị L03 mỗi ngày khi sinh mục tiêu. 0 = không đặt trần.");
        map.put(AUTO_GEN_L03_MAX_PER_DAY, "OK");
        upsert(AUTO_GEN_L04_MAX_PER_DAY, getStringValue(AUTO_GEN_L04_MAX_PER_DAY, "0"), AlgorithmConfig.ValueType.NUMBER,
                "Trần khuyến nghị L04 mỗi ngày/chuyên khoa khi sinh mục tiêu. 0 = không đặt trần.");
        map.put(AUTO_GEN_L04_MAX_PER_DAY, "OK");
        upsert(AUTO_GEN_L01_MAX_PER_WEEK, getStringValue(AUTO_GEN_L01_MAX_PER_WEEK, "0"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L01 tối đa mỗi người trong 1 tuần. 0 = không giới hạn.");
        map.put(AUTO_GEN_L01_MAX_PER_WEEK, "OK");
        upsert(AUTO_GEN_L02_MAX_PER_WEEK, getStringValue(AUTO_GEN_L02_MAX_PER_WEEK, "0"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L02 tối đa mỗi người trong 1 tuần. 0 = không giới hạn.");
        map.put(AUTO_GEN_L02_MAX_PER_WEEK, "OK");
        upsert(AUTO_GEN_L03_MAX_PER_WEEK, getStringValue(AUTO_GEN_L03_MAX_PER_WEEK, "0"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L03 tối đa mỗi người trong 1 tuần. 0 = không giới hạn.");
        map.put(AUTO_GEN_L03_MAX_PER_WEEK, "OK");
        upsert(AUTO_GEN_L04_MAX_PER_WEEK, getStringValue(AUTO_GEN_L04_MAX_PER_WEEK, "0"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L04 tối đa mỗi người trong 1 tuần. 0 = không giới hạn.");
        map.put(AUTO_GEN_L04_MAX_PER_WEEK, "OK");
        upsert(AUTO_GEN_L04_BALANCE_STRATEGY, getStringValue(AUTO_GEN_L04_BALANCE_STRATEGY, "FAIR_DISTRIBUTE"), AlgorithmConfig.ValueType.STRING,
                "Chiến lược cân bằng L04 cross-specialty: STRICT_MATCH_ONLY | FAIR_DISTRIBUTE | WEIGHTED_FAIR.");
        map.put(AUTO_GEN_L04_BALANCE_STRATEGY, "OK");
        upsert(AUTO_GEN_HOLIDAY_MODE, getStringValue(AUTO_GEN_HOLIDAY_MODE, "SKIP"), AlgorithmConfig.ValueType.STRING,
                "Xử lý khi gặp ngày lễ: SKIP = bỏ qua ngày lễ (không xếp lịch), PARTIAL = vẫn xếp lịch nhưng giảm cường độ.");
        map.put(AUTO_GEN_HOLIDAY_MODE, "OK");
        upsert(WEEKEND_WEIGHT, getStringValue(WEEKEND_WEIGHT, "2"), AlgorithmConfig.ValueType.NUMBER,
                "Hệ số phạt khi xếp lịch cho người vào thứ 7 / chủ nhật. Giá trị càng cao → thuật toán càng tránh xếp ca cuối tuần. Đặt 1 để tắt ưu tiên.");
        map.put(WEEKEND_WEIGHT, "OK");
        upsert(OVERNIGHT_RECOVERY_HOURS, getStringValue(OVERNIGHT_RECOVERY_HOURS, "24"), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng nghỉ ngơi tham chiếu cho L01. Ràng buộc thực tế vẫn theo ngày nghỉ bù và kiểm tra back-to-back.");
        map.put(OVERNIGHT_RECOVERY_HOURS, "OK");
        upsert(GREEDY_COVERAGE_THRESHOLD, getStringValue(GREEDY_COVERAGE_THRESHOLD, "0.85"), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng phủ lịch tối thiểu (0.0–1.0). Khi tỷ lệ lịch đã phủ đạt mức này, thuật toán greedy sẽ dừng sớm. Giảm → chạy nhanh hơn; tăng → phủ kỹ hơn.");
        map.put(GREEDY_COVERAGE_THRESHOLD, "OK");
        upsert(BALANCE_SCORE_MIN, getStringValue(BALANCE_SCORE_MIN, "0.70"), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng fairness tối thiểu (0.0–1.0, mặc định 0.70 = 70%). So với balanceScore sau khi xếp lịch; dưới ngưỡng → cảnh báo soft, không từ chối kết quả.");
        map.put(BALANCE_SCORE_MIN, "OK");
        upsert(AUTO_COMPENSATION_ENABLED, getStringValue(AUTO_COMPENSATION_ENABLED, "true"), AlgorithmConfig.ValueType.BOOLEAN,
                "Tự động tạo ngày nghỉ bù sau mỗi ca trực 24/24 theo quy tắc bù ca đã quy định. Tắt OFF nếu muốn quản lý nghỉ bù thủ công.");
        map.put(AUTO_COMPENSATION_ENABLED, "OK");
        upsert(MAX_STAFF_PER_SHIFT, getStringValue(MAX_STAFF_PER_SHIFT, "0"), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối đa cho mỗi ca trực. Giới hạn tránh quá tải một ca. 0 = không giới hạn.");
        map.put(MAX_STAFF_PER_SHIFT, "OK");
        upsert(MAX_SHIFTS_PER_STAFF, getStringValue(MAX_SHIFTS_PER_STAFF, "12"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca tối đa mỗi nhân sự trong kỳ lịch. Đặt 0 để dùng maxShiftsPerMonth của nhân sự. Mặc định 12.");
        map.put(MAX_SHIFTS_PER_STAFF, "OK");
        upsert(MAX_SHIFTS_PER_DAY, getStringValue(MAX_SHIFTS_PER_DAY, "0"), AlgorithmConfig.ValueType.NUMBER,
		                "Số ca tối đa mỗi nhân sự trong 1 ngày. 0 = không giới hạn, thuật toán tự quyết định dựa trên ràng buộc conflict (L01+L02, L03+L04).");
		        map.put(MAX_SHIFTS_PER_DAY, "OK");
		        upsert(BEAM_WIDTH, getStringValue(BEAM_WIDTH, "5"), AlgorithmConfig.ValueType.NUMBER,
		                "Độ rộng Beam Search (mặc định 5). Giá trị càng cao → tìm kiếm rộng hơn, quality tốt hơn nhưng chậm hơn. Với SA scheduler, dùng để tính số vòng lặp (beamWidth × 100).");
		        map.put(BEAM_WIDTH, "OK");
        // ScheduleQualityScorer runtime weights (Commit I)
        upsert(SCORER_COVERAGE_WEIGHT, getStringValue(SCORER_COVERAGE_WEIGHT, "0.40"), AlgorithmConfig.ValueType.NUMBER,
                "Trọng số coverage cho ScheduleQualityScorer (0.0–1.0). Mặc định 0.40. Càng cao càng ưu tiên lấp đầy ca trực.");
        map.put(SCORER_COVERAGE_WEIGHT, "OK");
        upsert(SCORER_FAIRNESS_WEIGHT, getStringValue(SCORER_FAIRNESS_WEIGHT, "0.35"), AlgorithmConfig.ValueType.NUMBER,
                "Trọng số fairness cho ScheduleQualityScorer (0.0–1.0). Mặc định 0.35. Càng cao càng ưu tiên phân bổ công bằng.");
        map.put(SCORER_FAIRNESS_WEIGHT, "OK");
        upsert(SCORER_CONSTRAINT_WEIGHT, getStringValue(SCORER_CONSTRAINT_WEIGHT, "0.25"), AlgorithmConfig.ValueType.NUMBER,
                "Trọng số constraint cho ScheduleQualityScorer (0.0–1.0). Mặc định 0.25. Càng cao càng ưu tiên kỷ luật ràng buộc.");
        map.put(SCORER_CONSTRAINT_WEIGHT, "OK");
        upsert(SCORER_PASS_THRESHOLD, getStringValue(SCORER_PASS_THRESHOLD, "80.0"), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng điểm đạt yêu cầu (0-100). Mặc định 80.0. Lịch có tổng điểm ≥ ngưỡng này được coi là passed.");
        map.put(SCORER_PASS_THRESHOLD, "OK");
        upsert(SCORER_HARD_VIOLATION_PENALTY, getStringValue(SCORER_HARD_VIOLATION_PENALTY, "25.0"), AlgorithmConfig.ValueType.NUMBER,
                "Phạt điểm cho mỗi vi phạm HARD (BR-01 đến BR-05). Mặc định 25.0.");
        map.put(SCORER_HARD_VIOLATION_PENALTY, "OK");
        upsert(SCORER_SOFT_VIOLATION_PENALTY, getStringValue(SCORER_SOFT_VIOLATION_PENALTY, "5.0"), AlgorithmConfig.ValueType.NUMBER,
                "Phạt điểm cho mỗi vi phạm SOFT (BR-06, BR-07). Mặc định 5.0.");
        map.put(SCORER_SOFT_VIOLATION_PENALTY, "OK");
        upsert(SCORER_TARGET_CV, getStringValue(SCORER_TARGET_CV, "0.10"), AlgorithmConfig.ValueType.NUMBER,
                "CV mục tiêu cho fairness. CV ≤ targetCv → 100 điểm fairness. Mặc định 0.10.");
        map.put(SCORER_TARGET_CV, "OK");
        upsert(SCORER_WORST_CV, getStringValue(SCORER_WORST_CV, "0.50"), AlgorithmConfig.ValueType.NUMBER,
                "CV vượt ngưỡng này → 0 điểm fairness. Mặc định 0.50.");
        map.put(SCORER_WORST_CV, "OK");
        // Rebalance round keys (Commit C)
        upsert(REBALANCE_ROUNDS_TOTAL, getStringValue(REBALANCE_ROUNDS_TOTAL, "80"), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp rebalance tổng (RRHC totalCountRebalance, SA fairnessRebalance). Đặt 0 để tắt. Mặc định 80.");
        map.put(REBALANCE_ROUNDS_TOTAL, "OK");
        upsert(REBALANCE_ROUNDS_PER_TYPE, getStringValue(REBALANCE_ROUNDS_PER_TYPE, "30"), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp rebalance per-type (RRHC perTypeRebalance, Beam perTypeRebalance). Đặt 0 để tắt. Mặc định 30.");
        map.put(REBALANCE_ROUNDS_PER_TYPE, "OK");
        upsert(REBALANCE_ROUNDS_EG, getStringValue(REBALANCE_ROUNDS_EG, "40"), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp rebalance EG perTypeMoveRebalance. Đặt 0 để tắt. Mặc định 40.");
        map.put(REBALANCE_ROUNDS_EG, "OK");
	        upsert(REBALANCE_ROUNDS_POST_SAVE, getStringValue(REBALANCE_ROUNDS_POST_SAVE, "100"), AlgorithmConfig.ValueType.NUMBER,
	                "Số vòng lặp post-process rebalance khi lưu (AutoSchedulingService.optimizeFairnessBySafeReassignment). Đặt 0 để tắt. Mặc định 100.");
	        map.put(REBALANCE_ROUNDS_POST_SAVE, "OK");
	        upsert(AUTO_ADJUST_CONFIG, getStringValue(AUTO_ADJUST_CONFIG, "true"), AlgorithmConfig.ValueType.BOOLEAN,
	                "Tự động điều chỉnh cấu hình (giảm L04) nếu tổng yêu cầu vượt năng lực nhân sự. Tắt nếu muốn dùng config thủ công.");
	        map.put(AUTO_ADJUST_CONFIG, "OK");
		        return map;
    }

    private void upsert(String paramKey, String value, AlgorithmConfig.ValueType valueType, String description) {
        // Native upsert — avoids SELECT-then-INSERT race that caused 409 on concurrent PUTs.
        configRepository.upsertConfig(paramKey, value, valueType.name(), description);
    }

    /**
     * Public helper for auto-scheduling to override auto-gen config fields
     * when the DB contains unrealistic values.
     */
    public void updateAutoGenField(String paramKey, String value) {
        upsert(paramKey, value, AlgorithmConfig.ValueType.NUMBER,
                "Auto-adjusted by scheduler based on staff count");
    }

    /**
     * Read a raw config value from DB, returning the given default if not found.
     */
    public String getConfigValue(String paramKey, String defaultValue) {
        return configRepository.findById(paramKey)
                .map(AlgorithmConfig::getParamValue)
                .orElse(defaultValue);
    }

    private int getIntValue(String paramKey, int defaultValue) {
        return getIntValue(paramKey, defaultValue, null);
    }

    /**
     * Lookup variant. When {@code cache} is non-null, the param value is read
     * from the preloaded key/value map (the result of a single bulk SELECT)
     * instead of issuing a separate SELECT for this key — this is the
     * fix for the N+1 query pattern that was making the algorithm-config
     * page render take 5+ seconds.
     */
    private int getIntValue(String paramKey, int defaultValue, java.util.Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey) : lookupRaw(paramKey);
        if (raw == null) return defaultValue;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getStringValue(String paramKey, String defaultValue) {
        return getStringValue(paramKey, defaultValue, null);
    }

    private String getStringValue(String paramKey, String defaultValue, java.util.Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey) : lookupRaw(paramKey);
        return raw != null ? raw : defaultValue;
    }

    private java.util.List<String> getStringListValue(String paramKey) {
        return getStringListValue(paramKey, null);
    }

    private java.util.List<String> getStringListValue(String paramKey, java.util.Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey) : lookupRaw(paramKey);
        if (raw == null || raw.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
    }

    /**
     * Single-row SELECT kept around for callers that do NOT preload the cache
     * (e.g. one-off lookups during save/upsert, audit queries).
     */
    private String lookupRaw(String paramKey) {
        return configRepository.findByParamKey(paramKey)
                .map(AlgorithmConfig::getParamValue)
                .orElse(null);
    }

    /**
     * Load every config row once and return it as a key/value map.
     * Used by bulk-read entry points (getAutoGenConfig, getRuntimeConfig)
     * to eliminate the N+1 query pattern that caused the algorithm-config
     * page to take 5+ seconds to load.
     */
    private java.util.Map<String, String> loadConfigCache() {
        java.util.Map<String, String> cache = new java.util.HashMap<>();
        for (com.hospital.scheduler.repository.AlgorithmConfigKeyValue kv : configRepository.findAllAsKeyValuePairs()) {
            cache.put(kv.getParamKey(), kv.getParamValue());
        }
        return cache;
    }

    /**
     * Get algorithm runtime configuration.
     * Returns an object with all runtime parameters or defaults if not set.
     */
    public AlgorithmRuntimeConfig getRuntimeConfig() {
        // Load AutoGenConfig to get per-type weekly max values. Both
        // getAutoGenConfig() and the lookup calls below share the same bulk
        // SELECT internally — total cost is 1 row-fetch per call instead of
        // 30+ SELECTs.
        var autoGenConfig = getAutoGenConfig();
        java.util.Map<String, String> cache = loadConfigCache();
        return AlgorithmRuntimeConfig.builder()
                .weekendWeight(getBigDecimalValue(WEEKEND_WEIGHT, 2.0, cache))
                .overnightRecoveryHours(getIntValue(OVERNIGHT_RECOVERY_HOURS, 24, cache))
                .greedyCoverageThreshold(getBigDecimalValue(GREEDY_COVERAGE_THRESHOLD, 0.85, cache))
                .balanceScoreMin(getBigDecimalValue(BALANCE_SCORE_MIN, 0.70, cache))
                .autoCompensationEnabled(getBooleanValue(AUTO_COMPENSATION_ENABLED, true, cache))
                .maxStaffPerShift(getIntValue(MAX_STAFF_PER_SHIFT, 0, cache))
                .maxShiftsPerDay(getIntValue(MAX_SHIFTS_PER_DAY, 0, cache))
                .maxShiftsPerStaff(getIntValue(MAX_SHIFTS_PER_STAFF, 12, cache))
                // Per-type weekly max from AutoGenConfig
                .l01MaxPerWeek(autoGenConfig.map(AutoGenConfig::l01MaxPerWeek).orElse(0))
                .l02MaxPerWeek(autoGenConfig.map(AutoGenConfig::l02MaxPerWeek).orElse(0))
                .l03MaxPerWeek(autoGenConfig.map(AutoGenConfig::l03MaxPerWeek).orElse(0))
                .l04MaxPerWeek(autoGenConfig.map(AutoGenConfig::l04MaxPerWeek).orElse(0))
                .autoAdjustConfig(getBooleanValue(AUTO_ADJUST_CONFIG, true, cache))
                .arrangementMode(cache.getOrDefault(ARRANGEMENT_MODE, "INTRA_TYPE"))
                .beamWidth(getIntValue(BEAM_WIDTH, 5, cache))
                .coverageWeight(getBigDecimalValue(SCORER_COVERAGE_WEIGHT, 0.40, cache))
                .fairnessWeight(getBigDecimalValue(SCORER_FAIRNESS_WEIGHT, 0.35, cache))
                .constraintWeight(getBigDecimalValue(SCORER_CONSTRAINT_WEIGHT, 0.25, cache))
                .passThreshold(getDoubleValue(SCORER_PASS_THRESHOLD, 80.0, cache))
                .hardViolationPenalty(getDoubleValue(SCORER_HARD_VIOLATION_PENALTY, 25.0, cache))
                .softViolationPenalty(getDoubleValue(SCORER_SOFT_VIOLATION_PENALTY, 5.0, cache))
                .targetCv(getDoubleValue(SCORER_TARGET_CV, 0.10, cache))
                .worstCv(getDoubleValue(SCORER_WORST_CV, 0.50, cache))
                .rebalanceRoundsTotal(getIntValue(REBALANCE_ROUNDS_TOTAL, 80, cache))
                .rebalanceRoundsPerType(getIntValue(REBALANCE_ROUNDS_PER_TYPE, 30, cache))
                .rebalanceRoundsEg(getIntValue(REBALANCE_ROUNDS_EG, 40, cache))
                .rebalanceRoundsPostSave(getIntValue(REBALANCE_ROUNDS_POST_SAVE, 100, cache))
                .build();
    }

    /**
     * Save algorithm runtime configuration.
     */
    @Transactional
    public void saveRuntimeConfig(AlgorithmRuntimeConfig config) {
        upsert(WEEKEND_WEIGHT, String.valueOf(config.getWeekendWeight()), AlgorithmConfig.ValueType.NUMBER,
                "Hệ số phạt khi xếp lịch cho người vào thứ 7 / chủ nhật. Giá trị càng cao → thuật toán càng tránh xếp ca cuối tuần. Đặt 1 để tắt ưu tiên.");
        upsert(OVERNIGHT_RECOVERY_HOURS, String.valueOf(config.getOvernightRecoveryHours()), AlgorithmConfig.ValueType.NUMBER,
                "Khoảng cách nghỉ bắt buộc giữa hai ca trực 24/24 liên tiếp của cùng một người. Thường đặt 24h để đảm bảo nghỉ ngơi đủ.");
        upsert(GREEDY_COVERAGE_THRESHOLD, String.valueOf(config.getGreedyCoverageThreshold()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng phủ lịch tối thiểu (0.0–1.0). Khi tỷ lệ lịch đã phủ đạt mức này, thuật toán greedy sẽ dừng sớm. Giảm → chạy nhanh hơn; tăng → phủ kỹ hơn.");
        upsert(BALANCE_SCORE_MIN, String.valueOf(config.getBalanceScoreMin()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng fairness tối thiểu (0.0–1.0, mặc định 0.70 = 70%). So với balanceScore sau khi xếp lịch; dưới ngưỡng → cảnh báo soft, không từ chối kết quả.");
        upsert(AUTO_COMPENSATION_ENABLED, String.valueOf(config.isAutoCompensationEnabled()), AlgorithmConfig.ValueType.BOOLEAN,
                "Tự động tạo ngày nghỉ bù sau mỗi ca trực 24/24 theo quy tắc bù ca đã quy định. Tắt OFF nếu muốn quản lý nghỉ bù thủ công.");
        upsert(MAX_STAFF_PER_SHIFT, String.valueOf(config.getMaxStaffPerShift()), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối đa mỗi ca. Đặt 0 để không giới hạn. Giới hạn này chỉ áp dụng khi yêu cầu ca có requiredStaffCount > maxStaffPerShift.");
        upsert(MAX_SHIFTS_PER_STAFF, String.valueOf(config.getMaxShiftsPerStaff()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca trực tối đa mỗi nhân sự trong kỳ. Đặt 0 để dùng maxShiftsPerMonth của nhân sự. Giới hạn này ngược lại với min — ngăn không ai bị quá tải.");
        upsert(MAX_SHIFTS_PER_DAY, String.valueOf(config.getMaxShiftsPerDay()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca tối đa mỗi nhân sự trong 1 ngày. 0 = không giới hạn, thuật toán tự quyết định.");
        upsert(BEAM_WIDTH, String.valueOf(config.getBeamWidth()), AlgorithmConfig.ValueType.NUMBER,
                "Độ rộng Beam Search (mặc định 5). Giá trị càng cao → tìm kiếm rộng hơn, quality tốt hơn nhưng chậm hơn. Với SA scheduler, dùng để tính số vòng lặp (beamWidth × 100).");
        upsert(AUTO_ADJUST_CONFIG, String.valueOf(config.isAutoAdjustConfig()), AlgorithmConfig.ValueType.BOOLEAN,
                "Tự động điều chỉnh cấu hình (giảm L04) nếu tổng yêu cầu vượt năng lực nhân sự. Tắt nếu muốn dùng config thủ công.");
        upsert(ARRANGEMENT_MODE, config.getArrangementMode(), AlgorithmConfig.ValueType.STRING,
                "Chế độ sắp xếp: INTRA_TYPE (công bằng trong từng loại ca) hoặc WITH_INTER_BALANCE (cân bằng giữa các loại ca trên cùng nhân sự).");
        upsert(SCORER_COVERAGE_WEIGHT, String.valueOf(config.getCoverageWeight()), AlgorithmConfig.ValueType.NUMBER,
                "Trọng số coverage cho ScheduleQualityScorer (0.0–1.0). Mặc định 0.40. Càng cao càng ưu tiên lấp đầy ca trực.");
        upsert(SCORER_FAIRNESS_WEIGHT, String.valueOf(config.getFairnessWeight()), AlgorithmConfig.ValueType.NUMBER,
                "Trọng số fairness cho ScheduleQualityScorer (0.0–1.0). Mặc định 0.35. Càng cao càng ưu tiên phân bổ công bằng.");
        upsert(SCORER_CONSTRAINT_WEIGHT, String.valueOf(config.getConstraintWeight()), AlgorithmConfig.ValueType.NUMBER,
                "Trọng số constraint cho ScheduleQualityScorer (0.0–1.0). Mặc định 0.25. Càng cao càng ưu tiên kỷ luật ràng buộc.");
        upsert(SCORER_PASS_THRESHOLD, String.valueOf(config.getPassThreshold()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng điểm đạt yêu cầu (0-100). Mặc định 80.0. Lịch có tổng điểm ≥ ngưỡng này được coi là passed.");
        upsert(SCORER_HARD_VIOLATION_PENALTY, String.valueOf(config.getHardViolationPenalty()), AlgorithmConfig.ValueType.NUMBER,
                "Phạt điểm cho mỗi vi phạm HARD (BR-01 đến BR-05). Mặc định 25.0.");
        upsert(SCORER_SOFT_VIOLATION_PENALTY, String.valueOf(config.getSoftViolationPenalty()), AlgorithmConfig.ValueType.NUMBER,
                "Phạt điểm cho mỗi vi phạm SOFT (BR-06, BR-07). Mặc định 5.0.");
        upsert(SCORER_TARGET_CV, String.valueOf(config.getTargetCv()), AlgorithmConfig.ValueType.NUMBER,
                "CV mục tiêu cho fairness. CV ≤ targetCv → 100 điểm fairness. Mặc định 0.10.");
        upsert(SCORER_WORST_CV, String.valueOf(config.getWorstCv()), AlgorithmConfig.ValueType.NUMBER,
                "CV vượt ngưỡng này → 0 điểm fairness. Mặc định 0.50.");
        upsert(REBALANCE_ROUNDS_TOTAL, String.valueOf(config.getRebalanceRoundsTotal()), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp rebalance tổng (RRHC totalCountRebalance, SA fairnessRebalance). Mặc định 80.");
        upsert(REBALANCE_ROUNDS_PER_TYPE, String.valueOf(config.getRebalanceRoundsPerType()), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp rebalance per-type (RRHC perTypeRebalance, Beam perTypeRebalance). Mặc định 30.");
        upsert(REBALANCE_ROUNDS_EG, String.valueOf(config.getRebalanceRoundsEg()), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp rebalance EG perTypeMoveRebalance (cũng dùng cho Beam totalCountRebalance). Mặc định 40.");
        upsert(REBALANCE_ROUNDS_POST_SAVE, String.valueOf(config.getRebalanceRoundsPostSave()), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp post-process rebalance khi lưu (AutoSchedulingService). Mặc định 100.");
    }

    private boolean getBooleanValue(String paramKey, boolean defaultValue) {
        return getBooleanValue(paramKey, defaultValue, null);
    }

    private boolean getBooleanValue(String paramKey, boolean defaultValue, java.util.Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey) : lookupRaw(paramKey);
        return raw != null ? Boolean.parseBoolean(raw) : defaultValue;
    }

    private float getFloatValue(String paramKey, float defaultValue) {
        return getFloatValue(paramKey, defaultValue, null);
    }

    private float getFloatValue(String paramKey, float defaultValue, java.util.Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey) : lookupRaw(paramKey);
        if (raw == null) return defaultValue;
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private java.math.BigDecimal getBigDecimalValue(String paramKey, double defaultValue) {
        return getBigDecimalValue(paramKey, defaultValue, null);
    }

    private java.math.BigDecimal getBigDecimalValue(String paramKey, double defaultValue, java.util.Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey) : lookupRaw(paramKey);
        if (raw == null) return java.math.BigDecimal.valueOf(defaultValue);
        try {
            return new java.math.BigDecimal(raw);
        } catch (NumberFormatException e) {
            return java.math.BigDecimal.valueOf(defaultValue);
        }
    }

    private double getDoubleValue(String paramKey, double defaultValue, java.util.Map<String, String> cache) {
        String raw = (cache != null) ? cache.get(paramKey) : lookupRaw(paramKey);
        if (raw == null) return defaultValue;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Đọc lịch sử từ kỳ PUBLISHED gần nhất để tính tỉ lệ phân bổ L01–L04.
     */
    private java.util.Map<String, Double> loadHistoricalShiftRatios() {
        try {
            List<SchedulePeriod> pastPeriods = schedulePeriodRepository
                    .findByStatusOrderByStartDateDesc(SchedulePeriod.PeriodStatus.PUBLISHED);
            if (pastPeriods.isEmpty()) return null;
            SchedulePeriod last = pastPeriods.get(0);
            List<Schedule> schedules = scheduleRepository.findByPeriodId(last.getId());
            if (schedules.isEmpty()) return null;
            java.util.Map<String, Long> counts = schedules.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            s -> s.getShiftType().getId(), java.util.stream.Collectors.counting()));
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            if (total == 0) return null;
            java.util.Map<String, Double> ratios = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, Long> e : counts.entrySet()) {
                ratios.put(e.getKey(), (double) e.getValue() / total);
            }
            log.info("Historical shift ratios from period {}: {}", last.getId(), ratios);
            return ratios;
        } catch (Exception e) {
            log.warn("Cannot load historical shift ratios, use fallback", e);
            return null;
        }
    }

    /**
     * Tính toán và trả về AutoGenConfig đề xuất dựa trên mục tiêu ca/người/tháng.
     *
     * <p>Công thức:
     * <ul>
     *   <li>{@code minPerDay = ⌈(target × eligible) / periodDays⌋} — đảm bảo coverage</li>
     *   <li>{@code minPerWeek = ⌈target / periodWeeks⌋} — phân bổ đều theo tuần</li>
     *   <li>{@code maxPerWeek = ⌈(target / periodWeeks) × 1.5⌉} — buffer 50%</li>
     *   <li>{@code maxPerDay = ⌈maxPerWeek × 1.2⌉} — buffer cho ngày cao điểm</li>
     * </ul>
     *
     * @param periodDays       Số ngày trong kỳ (VD: 30 cho tháng 9)
     * @param periodWeeks      Số tuần trong kỳ (mặc định 4)
     * @param eligibleStaff    Map shiftTypeId → số người đủ điều kiện
     * @param targetPerStaff   Map shiftTypeId → mục tiêu ca/người/tháng
     * @return Đề xuất AutoGenConfig kèm expected total shifts
     */
	    public AutoGenConfigRecommendation recommendAutoGenConfig(
	            int periodDays,
	            int periodWeeks,
	            java.util.Map<String, Integer> eligibleStaff,
	            java.util.Map<String, Integer> targetPerStaff,
	            boolean expandNonL04Eligibility,
	            java.util.List<String> expandedSpecialties,
	            int maxShiftsPerStaff,
	            String arrangementMode) {

	        // Snapshot existing config để giữ enabled, holidayMode, cross-specialty, allowed lists
	        AutoGenConfig current = getAutoGenConfig().orElseThrow();

	        int totalStaff = Math.max(1, eligibleStaff.values().stream().mapToInt(Integer::intValue).max().orElse(1));
	        int l04Elig = Math.max(1, eligibleStaff.getOrDefault("L04", 1));

	        // Điều chỉnh eligible pool cho L04 dựa trên cross-specialty và số chuyên khoa
	        int numSpecialties = Math.max(1, expandedSpecialties != null ? expandedSpecialties.size() : 1);
	        boolean csEnabled = current.l04CrossSpecialty();
	        int effectiveL04Elig = csEnabled
	                ? l04Elig
	                : Math.max(1, Math.min(l04Elig, (int) Math.ceil((double) totalStaff / numSpecialties)));

                // Dùng target từ lịch sử nếu có, fallback sang frontend hoặc % mặc định
                int capacityPerPerson = Math.max(1, maxShiftsPerStaff > 0 ? maxShiftsPerStaff : periodDays);
                java.util.Map<String, Double> histRatios = loadHistoricalShiftRatios();
                int l01Target, l02Target, l03Target, l04Target;
                // ƯU TIÊN target_per_month từ DB (user đã chỉnh trong UI). Chỉ fallback sang
                // histRatios/percent khi target = 0 (chưa set). Trước đây target frontend
                // truyền vào nhưng bị ignore → recommend bơm minPerDay lên 299.
                if (current.l01TargetPerMonth() > 0 || current.l02TargetPerMonth() > 0
                        || current.l03TargetPerMonth() > 0 || current.l04TargetPerMonth() > 0) {
                    l01Target = current.l01TargetPerMonth() > 0 ? current.l01TargetPerMonth() : 2;
                    l02Target = current.l02TargetPerMonth() > 0 ? current.l02TargetPerMonth() : 2;
                    l03Target = current.l03TargetPerMonth() > 0 ? current.l03TargetPerMonth() : 2;
                    l04Target = current.l04TargetPerMonth() > 0 ? current.l04TargetPerMonth() : 5;
                } else if (histRatios != null) {
                    l01Target = Math.max(1, (int) Math.round(capacityPerPerson * histRatios.getOrDefault("L01", 0.30)));
                    l02Target = Math.max(1, (int) Math.round(capacityPerPerson * histRatios.getOrDefault("L02", 0.25)));
                    l03Target = Math.max(1, (int) Math.round(capacityPerPerson * histRatios.getOrDefault("L03", 0.30)));
                    l04Target = Math.max(1, (int) Math.round(capacityPerPerson * histRatios.getOrDefault("L04", 0.15)));
                } else {
                    l01Target = Math.max(1, (int) Math.round(capacityPerPerson * 0.30));
                    l02Target = Math.max(1, (int) Math.round(capacityPerPerson * 0.25));
                    l03Target = Math.max(1, (int) Math.round(capacityPerPerson * 0.30));
                    l04Target = Math.max(1, (int) Math.round(capacityPerPerson * 0.15));
                }

	        int l01Elig = Math.max(1, eligibleStaff.getOrDefault("L01", 1));
	        int l02Elig = Math.max(1, eligibleStaff.getOrDefault("L02", 1));
	        int l03Elig = Math.max(1, eligibleStaff.getOrDefault("L03", 1));
	        int days = Math.max(1, periodDays);
	        int weeks = Math.max(1, periodWeeks);

	        int l01MinPerDay = Math.max(1, (int) Math.ceil((double) (l01Target * l01Elig) / days));
	        int l02MinPerDay = Math.max(1, (int) Math.ceil((double) (l02Target * l02Elig) / days));
	        int l03MinPerDay = Math.max(1, (int) Math.ceil((double) (l03Target * l03Elig) / days));
	        int l04MinPerDay = Math.max(1, (int) Math.ceil((double) (l04Target * effectiveL04Elig) / days));

        int l01MinPerWeek = Math.max(1, (int) Math.ceil((double) l01Target / weeks));
        int l02MinPerWeek = Math.max(1, (int) Math.ceil((double) l02Target / weeks));
        int l03MinPerWeek = Math.max(1, (int) Math.ceil((double) l03Target / weeks));
        int l04MinPerWeek = Math.max(1, (int) Math.ceil((double) l04Target / weeks));

        int l01MaxPerWeek = Math.max(l01MinPerWeek + 1, (int) Math.ceil(((double) l01Target / weeks) * 1.5));
        int l02MaxPerWeek = Math.max(l02MinPerWeek + 1, (int) Math.ceil(((double) l02Target / weeks) * 1.5));
        int l03MaxPerWeek = Math.max(l03MinPerWeek + 1, (int) Math.ceil(((double) l03Target / weeks) * 1.5));
        int l04MaxPerWeek = Math.max(l04MinPerWeek + 1, (int) Math.ceil(((double) l04Target / weeks) * 1.5));

        int l01MaxPerDay = Math.max(l01MinPerDay, (int) Math.ceil(l01MaxPerWeek * 1.2));
        int l02MaxPerDay = Math.max(l02MinPerDay, (int) Math.ceil(l02MaxPerWeek * 1.2));
        int l03MaxPerDay = Math.max(l03MinPerDay, (int) Math.ceil(l03MaxPerWeek * 1.2));
        int l04MaxPerDay = Math.max(l04MinPerDay, (int) Math.ceil(l04MaxPerWeek * 1.2));

        java.util.List<String> l01Spec = expandNonL04Eligibility
                ? (expandedSpecialties != null && !expandedSpecialties.isEmpty()
                    ? expandedSpecialties
                    : java.util.List.of("Bác sĩ", "Điều dưỡng", "Kỹ thuật viên", "Dược sĩ",
                        "Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng"))
                : (current.l01AllowedSpecialties() != null && !current.l01AllowedSpecialties().isEmpty()
                    ? current.l01AllowedSpecialties()
                    : java.util.List.of("Ngoại", "Nội"));
        java.util.List<String> l02Spec = expandNonL04Eligibility
                ? l01Spec
                : (current.l02AllowedSpecialties() != null && !current.l02AllowedSpecialties().isEmpty()
                    ? current.l02AllowedSpecialties()
                    : java.util.List.of("Ngoại", "Nội"));
        java.util.List<String> l03Spec = expandNonL04Eligibility
                ? l01Spec
                : (current.l03AllowedSpecialties() != null && !current.l03AllowedSpecialties().isEmpty()
                    ? current.l03AllowedSpecialties()
                    : java.util.List.of("Ngoại", "Nội"));

int totalExpected = (l01Target * l01Elig) + (l02Target * l02Elig)
                + (l03Target * l03Elig) + (l04Target * effectiveL04Elig);

	        // Nếu có maxShiftsPerStaff, kiểm tra năng lực thực tế và giới hạn min/ngày
	        if (maxShiftsPerStaff > 0) {
	            int staffCount = Math.max(1, eligibleStaff.values().stream().mapToInt(Integer::intValue).max().orElse(1));
	            int dailyCapacity = Math.max(1, staffCount * maxShiftsPerStaff / days);
	            int totalMinPerDay = l01MinPerDay + l02MinPerDay + l03MinPerDay + l04MinPerDay;
	            if (totalMinPerDay > dailyCapacity) {
	                double ratio = Math.max(0.25, (double) dailyCapacity / totalMinPerDay);
	                l01MinPerDay = Math.max(1, (int) (l01MinPerDay * ratio));
	                l02MinPerDay = Math.max(1, (int) (l02MinPerDay * ratio));
	                l03MinPerDay = Math.max(1, (int) (l03MinPerDay * ratio));
	                l04MinPerDay = Math.max(1, (int) (l04MinPerDay * ratio));
	                // Re-derive max từ min đã giới hạn
	                l01MaxPerDay = Math.max(l01MinPerDay, (int) Math.ceil(l01MaxPerWeek * 1.2));
	                l02MaxPerDay = Math.max(l02MinPerDay, (int) Math.ceil(l02MaxPerWeek * 1.2));
	                l03MaxPerDay = Math.max(l03MinPerDay, (int) Math.ceil(l03MaxPerWeek * 1.2));
	                l04MaxPerDay = Math.max(l04MinPerDay, (int) Math.ceil(l04MaxPerWeek * 1.2));
	            }
	        }

        AutoGenConfig recommended = new AutoGenConfig(
                current.enabled(),
                l01MinPerDay, l02MinPerDay, l03MinPerDay, l04MinPerDay,
                l01MaxPerDay, l02MaxPerDay, l03MaxPerDay, l04MaxPerDay,
                l01MinPerWeek, l02MinPerWeek, l03MinPerWeek, l04MinPerWeek,
                l01MaxPerWeek, l02MaxPerWeek, l03MaxPerWeek, l04MaxPerWeek,
                current.holidayMode(),
                current.removedShiftTypes() != null ? current.removedShiftTypes() : java.util.List.of(),
                current.l04CrossSpecialty(),
                current.l04CrossSpecialtyRatio(),
                current.l04AllowedSpecialties() != null ? current.l04AllowedSpecialties() : java.util.List.of(),
                l01Spec, l02Spec, l03Spec,
                // Preserve target_per_month từ config hiện tại — recommend không đổi target,
                // chỉ dùng target để tính minPerDay.
                current.l01TargetPerMonth(), current.l02TargetPerMonth(),
                current.l03TargetPerMonth(), current.l04TargetPerMonth(),
                current.l04BalanceStrategy()
        );

        String rationale = String.format(
                "Đề xuất cho kỳ %d ngày/%d tuần với tổng ca dự kiến = %d. " +
                "L01/L02/L03: %d/%d/%d ca/người × %d/%d/%d người eligible. " +
                "L04: %d ca/người × %d người eligible. " +
                "%s",
                days, weeks, totalExpected,
                l01Target, l02Target, l03Target, l01Elig, l02Elig, l03Elig,
                l04Target, l04Elig,
                expandNonL04Eligibility
                    ? "Mở rộng eligibility L01/L02/L03 cho tất cả specialties để đạt mục tiêu."
                    : "Giữ eligibility L01/L02/L03 cho Ngoại,Nội (8 người) — nếu không đủ, cân nhắc mở rộng."
        );

        // ── Commit B: compute demand ratio, fairness type, expected metrics, warnings ──
        java.util.Map<String, Integer> demandRatio = new java.util.LinkedHashMap<>();
        demandRatio.put("L01", l01MinPerDay);
        demandRatio.put("L02", l02MinPerDay);
        demandRatio.put("L03", l03MinPerDay);
        demandRatio.put("L04", l04MinPerDay);

        // Fairness type: base is INTRA_TYPE. INTER_TYPE_BALANCE only when demand ratios
        // across L01/L02/L03 are similar enough that a soft rebalance is feasible.
        // If user explicitly chose arrangementMode, respect it.
        double l01Ratio = (double) l01MinPerDay / Math.max(1, l01Elig);
        double l02Ratio = (double) l02MinPerDay / Math.max(1, l02Elig);
        double l03Ratio = (double) l03MinPerDay / Math.max(1, l03Elig);
        double maxRatio = Math.max(Math.max(l01Ratio, l02Ratio), l03Ratio);
        double minRatio = Math.min(Math.min(l01Ratio, l02Ratio), l03Ratio);
        String fairnessType;
        if ("WITH_INTER_BALANCE".equals(arrangementMode)) {
            fairnessType = "INTRA_TYPE_WITH_INTER_BALANCE";
        } else {
            boolean interBalanceFeasible = maxRatio > 0 && (maxRatio / minRatio) <= 2.5;
            fairnessType = interBalanceFeasible ? "INTRA_TYPE_WITH_INTER_BALANCE" : "INTRA_TYPE";
        }

        // Cross-specialty policy description
        String crossSpecialtyPolicy;
        if (csEnabled) {
            if (current.l04BalanceStrategy() != null) {
                crossSpecialtyPolicy = switch (current.l04BalanceStrategy()) {
                    case "STRICT_MATCH_ONLY" -> "TẮT — L04 chỉ theo đúng chuyên khoa";
                    case "FAIR_DISTRIBUTE" -> String.format("BẬT — cross-specialty ratio %.0f%%, phân bổ công bằng theo specialty",
                            current.l04CrossSpecialtyRatio() * 100);
                    case "WEIGHTED_FAIR" -> String.format("BẬT — cross-specialty ratio %.0f%%, chiến lược cân bằng theo trọng số",
                            current.l04CrossSpecialtyRatio() * 100);
                    default -> String.format("BẬT — cross-specialty ratio %.0f%%",
                            current.l04CrossSpecialtyRatio() * 100);
                };
            } else {
                crossSpecialtyPolicy = String.format("BẬT — cross-specialty ratio %.0f%%, chiến lược FAIR_DISTRIBUTE mặc định",
                        current.l04CrossSpecialtyRatio() * 100);
            }
        } else {
            crossSpecialtyPolicy = "TẮT — L04 chỉ theo đúng chuyên khoa";
        }

        // Expected metrics (estimates before running algorithm)
        double targetCv = 0.10;
        double worstCv = 0.50;
        // estFairness: how balanced the demand ratios are across L01/L02/L03 eligible groups.
        // 100 = perfectly proportional, decreases as imbalance grows.
        double estFairness = maxRatio > 0 && minRatio > 0
                ? 100.0 * Math.max(0, 1.0 - (maxRatio / minRatio - 1.0) / 2.0)
                : 75.0;  // conservative default
        // Estimated coverage: min achievable given minPerDay vs eligible capacity
        int totalMinPerDay = l01MinPerDay + l02MinPerDay + l03MinPerDay + l04MinPerDay;
        int totalEligible = l01Elig + l02Elig + l03Elig + effectiveL04Elig;
        double estCoverage = totalEligible > 0
                ? Math.min(100.0, 100.0 * totalMinPerDay / Math.max(1, totalEligible))
                : 80.0;
        double estQuality = 0.40 * estCoverage + 0.35 * estFairness + 0.25 * 85.0;  // 85 = constraint baseline

        var expectedMetrics = new com.hospital.scheduler.dto.response.AutoGenConfigRecommendResponse.ExpectedMetrics(
                estCoverage, estFairness, estQuality, targetCv, worstCv);

	        // Trade-off warnings
	        java.util.List<String> warnings = new java.util.ArrayList<>();
	        if (l01Ratio > 0 && l02Ratio > 0 && l03Ratio > 0) {
	            double imbalance = (maxRatio / minRatio - 1.0) * 100;
	            if (imbalance > 50) {
	                warnings.add(String.format("⚠️ Demand lệch: L01/L02/L03 ratio = %.0f%%/%.0f%%/%.0f%%/ca. " +
	                        "Inter-type balance chỉ là soft objective — không đảm bảo bằng nhau nếu demand gốc lệch.",
	                        l01Ratio * days, l02Ratio * days, l03Ratio * days));
	            }
	            if ("WITH_INTER_BALANCE".equals(arrangementMode) && imbalance > 50) {
	                warnings.add("⚠️ Inter-type balance được chọn theo yêu cầu nhưng demand lệch " + String.format("%.0f%%", imbalance) + " — coverage có thể giảm.");
	            }
	        }
        if (totalMinPerDay > (totalEligible > 0 ? totalEligible : 1) && maxShiftsPerStaff > 0) {
            warnings.add("⚠️ Tổng min/ngày (" + totalMinPerDay + ") có thể vượt năng lực. Đã áp dụng uniform scaling.");
        }
        if (!csEnabled && effectiveL04Elig < l04Elig) {
            warnings.add("⚠️ Cross-specialty TẮT — L04 chỉ có " + effectiveL04Elig + " người eligible, có thể thiếu.");
        }
        if (l04MinPerDay > effectiveL04Elig && effectiveL04Elig > 0) {
            warnings.add("⚠️ L04: " + l04MinPerDay + " ca/ngày cho " + effectiveL04Elig + " người — mỗi người cần ≥1 ca.");
        }

        return new AutoGenConfigRecommendation(
                recommended, totalExpected, rationale,
                demandRatio, fairnessType, crossSpecialtyPolicy, expectedMetrics, warnings);
    }

    /**
     * Commit B (Workflow M07): Kết quả recommend bao gồm config + metadata cho recommendation card.
     * Bao gồm: demand ratio, fairness type, cross-specialty policy, expected metrics, trade-off warnings.
     */
    public record AutoGenConfigRecommendation(
            AutoGenConfig config,
            int totalShiftsExpected,
            String rationale,
            /** minPerDay values per shift type — keys: L01/L02/L03/L04 */
            java.util.Map<String, Integer> demandRatio,
            /** Fairness type: INTRA_TYPE | INTRA_TYPE_WITH_INTER_BALANCE */
            String fairnessType,
            /** Human-readable cross-specialty policy description */
            String crossSpecialtyPolicy,
            /** Estimated metrics before running preview */
            com.hospital.scheduler.dto.response.AutoGenConfigRecommendResponse.ExpectedMetrics expectedMetrics,
            /** Trade-off and constraint warnings */
            java.util.List<String> warnings
    ) {}

    /**
     * Runtime configuration record for algorithm execution.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AlgorithmRuntimeConfig {
        private java.math.BigDecimal weekendWeight;
        private int overnightRecoveryHours;
        private java.math.BigDecimal greedyCoverageThreshold;
        private java.math.BigDecimal balanceScoreMin;
        private boolean autoCompensationEnabled;
        private int maxStaffPerShift;
        private int maxShiftsPerStaff;
        private int maxShiftsPerDay;
        // Per-shift-type weekly max (from AutoGenConfig)
        private int l01MaxPerWeek;
        private int l02MaxPerWeek;
        private int l03MaxPerWeek;
        private int l04MaxPerWeek;
        // Beam Search width (default 5, from DB)
        @lombok.Builder.Default
        private int beamWidth = 5;
        // Auto-adjust config before scheduler run
        @lombok.Builder.Default
        private boolean autoAdjustConfig = true;

        // Arrangement mode: INTRA_TYPE (default) or WITH_INTER_BALANCE
        @lombok.Builder.Default
        private String arrangementMode = "INTRA_TYPE";

        // ScheduleQualityScorer runtime weights
        @lombok.Builder.Default
        private java.math.BigDecimal coverageWeight = java.math.BigDecimal.valueOf(0.40);
        @lombok.Builder.Default
        private java.math.BigDecimal fairnessWeight = java.math.BigDecimal.valueOf(0.35);
        @lombok.Builder.Default
        private java.math.BigDecimal constraintWeight = java.math.BigDecimal.valueOf(0.25);

        // ScheduleQualityScorer runtime thresholds/penalties — mirror defaults
        // declared in ScheduleQualityScorer.java (single source of truth stays
        // there; these @Builder.Default values must match the scorer's hardcoded
        // fallbacks: passThreshold=80.0, hardViolationPenalty=25.0,
        // softViolationPenalty=5.0, targetCv=0.10, worstCv=0.50).
        @lombok.Builder.Default
        private double passThreshold = 80.0;
        @lombok.Builder.Default
        private double hardViolationPenalty = 25.0;
        @lombok.Builder.Default
        private double softViolationPenalty = 5.0;
        @lombok.Builder.Default
        private double targetCv = 0.10;
        @lombok.Builder.Default
        private double worstCv = 0.50;

        // Rebalance rounds (defaults match hard-coded values before Commit C)
        @lombok.Builder.Default
        private int rebalanceRoundsTotal = 80;
        @lombok.Builder.Default
        private int rebalanceRoundsPerType = 30;
        @lombok.Builder.Default
        private int rebalanceRoundsEg = 40;
        @lombok.Builder.Default
        private int rebalanceRoundsPostSave = 100;

        /**
         * L01 adjacent day window derived from overnightRecoveryHours.
         * Ceil(hours / 24) = số ngày cấm L01 trước/sau 1 L01 đã gán.
         * Default 24h → W=1 (trùng hành vi cũ ±1). 48h → W=2.
         */
        public int getL01AdjacentDayWindow() {
            return overnightRecoveryHours > 0
                    ? (int) Math.ceil(overnightRecoveryHours / 24.0)
                    : 1;
        }
    }
}
