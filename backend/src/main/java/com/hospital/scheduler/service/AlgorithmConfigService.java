package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.algorithm.AutoGenConstants;
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
    private final ObjectMapper objectMapper;
    // PR-002A: delegate-only injection. AutoGenConfigService owns the
    // authoritative get/save implementation; this service keeps the
    // public API stable for the 12 production callers that still
    // depend on it. The PR-002C decision is intentionally deferred —
    // if the facade stays thin and cheap, removal is not required.
    private final AutoGenConfigService autoGenConfigService;

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
    // L04 cross-specialty (chỉ L04 có specialty config; L01/L02/L03 không cần)
    public static final String AUTO_GEN_L04_CROSS_SPECIALTY = "auto_gen_l04_cross_specialty";
    public static final String AUTO_GEN_L04_CROSS_SPECIALTY_RATIO = "auto_gen_l04_cross_specialty_ratio";
    public static final String AUTO_GEN_L04_ALLOWED_SPECIALTIES = "auto_gen_l04_allowed_specialties";
    public static final String AUTO_GEN_L04_BALANCE_STRATEGY = "auto_gen_l04_balance_strategy";
    // PR-002A: kept uppercase to match existing DB rows seeded by the legacy
    // upsert path. Migrating this row to lowercase is intentionally deferred —
    // it would change behavior outside the scope of the delegation refactor.
    public static final String AUTO_GEN_REMOVED_SHIFT_TYPES = "AUTO_GEN_REMOVED_SHIFT_TYPES";

    // Algorithm runtime config param keys
    public static final String WEEKEND_WEIGHT = "weekend_weight";
    public static final String OVERNIGHT_RECOVERY_HOURS = "overnight_recovery_hours";
    public static final String GREEDY_COVERAGE_THRESHOLD = "greedy_coverage_threshold";
    public static final String BALANCE_SCORE_MIN = "balance_score_min";
    public static final String MIN_STAFF_PER_SHIFT = "min_staff_per_shift";
    public static final String MAX_STAFF_PER_SHIFT = "max_staff_per_shift";
    public static final String MIN_SHIFTS_PER_STAFF = "min_shifts_per_staff";
    public static final String MAX_SHIFTS_PER_STAFF = "max_shifts_per_staff";

    // Scheduling metaheuristic param keys (config-ui visibility fix 2026-07-24)
    public static final String SCHEDULING_MAX_ITERATIONS = "scheduling_max_iterations";
    public static final String SCHEDULING_MAX_NO_IMPROVE = "scheduling_max_no_improve";
    public static final String SCHEDULING_DIVERSIFY_AFTER = "scheduling_diversify_after";
    public static final String SCHEDULING_ACCEPTANCE_STRATEGY = "scheduling_acceptance_strategy";
    public static final String SCHEDULING_CANDIDATE_LIST_SIZE = "scheduling_candidate_list_size";
    public static final String SCHEDULING_LA_MEMORY_SIZE = "scheduling_la_memory_size";
    public static final String SCHEDULING_GD_INITIAL_LEVEL = "scheduling_gd_initial_level";
    public static final String SCHEDULING_GD_DECAY_RATE = "scheduling_gd_decay_rate";
    public static final String SCHEDULING_GD_MIN_LEVEL = "scheduling_gd_min_level";
    public static final String BALANCE_SCORE_WORST = "balance_score_worst";

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
     * Returns the auto-generation configuration.
     *
     * <p>The {@code Optional} itself is always present. When
     * {@code AUTO_GEN_ENABLED} is missing from the DB the returned config
     * defaults to {@code enabled=true} so auto-scheduling works out-of-the-box;
     * when the flag is explicitly set to {@code false} the returned config
     * carries {@code enabled=false}. Consumers must inspect
     * {@link AutoGenConfig#enabled()} to decide whether to proceed.
     *
     * <p><b>Do not</b> branch on {@code Optional#isEmpty()} to detect a
     * disabled state — that pattern was misleadingly documented in an older
     * version of this method and has never been the actual contract.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<AutoGenConfig> getAutoGenConfig() {
        // PR-002A: delegate. The authoritative implementation lives in
        // AutoGenConfigService#getAutoGenConfig (uses the shared
        // AlgorithmConfigCrudService for bulk-load + cache parsing).
        // The contract documented above is preserved — this method still
        // returns a present Optional whose cfg.enabled() reflects the
        // persisted flag. Internal callers (getRuntimeConfig,
        // recommendAutoGenConfig) keep using this delegation.
        return autoGenConfigService.getAutoGenConfig();
    }

    /**
     * Save auto-generation configuration.
     */
    @Transactional
    public void saveAutoGenConfig(AutoGenConfig config) {
        // PR-002A: delegate to AutoGenConfigService#saveAutoGenConfig.
        // All field-level upserts (paramKey, value, type, description)
        // are owned by AutoGenConfigService to keep the persistence layer
        // in one place. This thin wrapper preserves the public API for
        // the 2 production callers (DataSeeder, AutoSchedulingController).
        autoGenConfigService.saveAutoGenConfig(config);
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
                upsert(AUTO_GEN_HOLIDAY_MODE, getStringValue(AUTO_GEN_HOLIDAY_MODE, AutoGenConstants.HOLIDAY_MODE_SKIP), AlgorithmConfig.ValueType.STRING,
                "Xử lý khi gặp ngày lễ: SKIP = bỏ qua ngày lễ (không xếp lịch), PARTIAL = vẫn xếp lịch nhưng giảm cường độ.");
        map.put(AUTO_GEN_HOLIDAY_MODE, "OK");
        upsert(WEEKEND_WEIGHT, getStringValue(WEEKEND_WEIGHT, "2"), AlgorithmConfig.ValueType.NUMBER,
                "Hệ số phạt khi xếp lịch cho người vào thứ 7 / chủ nhật. Giá trị càng cao → thuật toán càng tránh xếp ca cuối tuần. Đặt 1 để tắt ưu tiên.");
        map.put(WEEKEND_WEIGHT, "OK");
        upsert(OVERNIGHT_RECOVERY_HOURS, getStringValue(OVERNIGHT_RECOVERY_HOURS, "24"), AlgorithmConfig.ValueType.NUMBER,
                "[RESERVED v1.1] Giờ hồi phục sau trực đêm. Hiện tại không dùng — quy tắc nghỉ bù và back-to-back đã được xử lý.");
        map.put(OVERNIGHT_RECOVERY_HOURS, "OK");
        upsert(GREEDY_COVERAGE_THRESHOLD, getStringValue(GREEDY_COVERAGE_THRESHOLD, "0.85"), AlgorithmConfig.ValueType.NUMBER,
                "[v1.0] Chỉ dùng để giám sát/logging. Scheduler luôn gán 100% slot khi có thể. Không ảnh hưởng đến kết quả.");
        map.put(GREEDY_COVERAGE_THRESHOLD, "OK");
        upsert(BALANCE_SCORE_MIN, getStringValue(BALANCE_SCORE_MIN, "0.75"), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng điểm cân bằng tải tối thiểu (0.0–1.0). Cao → phân bổ ca trực công bằng hơn nhưng có thể khó đạt; thấp → dễ đáp ứng nhưng có thể thiên lệch.");
        map.put(BALANCE_SCORE_MIN, "OK");
        upsert(MIN_STAFF_PER_SHIFT, getStringValue(MIN_STAFF_PER_SHIFT, "1"), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng theo dõi số nhân sự tối thiểu mỗi ca; dùng cho đánh giá/chất lượng, không ép thuật toán phá ràng buộc cứng.");
        map.put(MIN_STAFF_PER_SHIFT, "OK");
        upsert(MAX_STAFF_PER_SHIFT, getStringValue(MAX_STAFF_PER_SHIFT, "5"), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối đa cho mỗi ca trực. Giới hạn tránh quá tải một ca.");
        map.put(MAX_STAFF_PER_SHIFT, "OK");
        upsert(MIN_SHIFTS_PER_STAFF, getStringValue(MIN_SHIFTS_PER_STAFF, "0"), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng theo dõi số ca tối thiểu mỗi nhân sự trong kỳ; dùng để đánh giá cân bằng, không ép tạo ca giả.");
        map.put(MIN_SHIFTS_PER_STAFF, "OK");
        upsert(MAX_SHIFTS_PER_STAFF, getStringValue(MAX_SHIFTS_PER_STAFF, "35"), AlgorithmConfig.ValueType.NUMBER,
                "Số ca tối đa mỗi nhân sự trong kỳ lịch. Spec M07-F01 yêu cầu phân bổ đều không giới hạn cố định, nhưng đặt trần hợp lý để bảo vệ nhân sự khỏi bị quá tải. Default 35 (≈1 ca/ngày + buffer cho L04 đa chuyên khoa).");
        map.put(MAX_SHIFTS_PER_STAFF, "OK");
        // ─── Scheduling metaheuristic params (config-ui visibility fix 2026-07-24) ───
        upsert(SCHEDULING_MAX_ITERATIONS, getStringValue(SCHEDULING_MAX_ITERATIONS, "500"), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp tối đa của thuật toán tối ưu (Tabu Search / Late Acceptance / Great Deluge). Tăng lên nếu thuật toán chưa hết thời gian mà vẫn chưa tìm được lời giải tốt; giảm xuống nếu chạy quá lâu.");
        map.put(SCHEDULING_MAX_ITERATIONS, "OK");
        upsert(SCHEDULING_MAX_NO_IMPROVE, getStringValue(SCHEDULING_MAX_NO_IMPROVE, "50"), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp liên tiếp không cải thiện trước khi dừng sớm (diversification). Tăng lên → thuật toán kiên trì tìm lời giải tốt hơn; giảm xuống → dừng sớm khi bão hòa.");
        map.put(SCHEDULING_MAX_NO_IMPROVE, "OK");
        upsert(SCHEDULING_DIVERSIFY_AFTER, getStringValue(SCHEDULING_DIVERSIFY_AFTER, "20"), AlgorithmConfig.ValueType.NUMBER,
                "Số vòng lặp không cải thiện liên tiếp kích hoạt diversification (reset/perturb lời giải hiện tại). Giúp tránh local optimum.");
        map.put(SCHEDULING_DIVERSIFY_AFTER, "OK");
        upsert(SCHEDULING_ACCEPTANCE_STRATEGY, getStringValue(SCHEDULING_ACCEPTANCE_STRATEGY, "TABU"), AlgorithmConfig.ValueType.STRING,
                "Chiến lược chấp nhận nghiệm xấu hơn: TABU (Tabu Search), SA (Simulated Annealing), LA (Late Acceptance), GD (Great Deluge).");
        map.put(SCHEDULING_ACCEPTANCE_STRATEGY, "OK");
        upsert(SCHEDULING_CANDIDATE_LIST_SIZE, getStringValue(SCHEDULING_CANDIDATE_LIST_SIZE, "50"), AlgorithmConfig.ValueType.NUMBER,
                "Số ứng viên lân cận được sinh ra mỗi vòng lặp. Tăng → khám phá rộng hơn, chậm hơn; giảm → hẹp hơn, nhanh hơn.");
        map.put(SCHEDULING_CANDIDATE_LIST_SIZE, "OK");
        upsert(SCHEDULING_LA_MEMORY_SIZE, getStringValue(SCHEDULING_LA_MEMORY_SIZE, "10"), AlgorithmConfig.ValueType.NUMBER,
                "Độ dài bộ nhớ của Late Acceptance (số vòng lặp trước được so sánh). Lớn hơn → ổn định hơn; nhỏ hơn → phản ứng nhanh hơn.");
        map.put(SCHEDULING_LA_MEMORY_SIZE, "OK");
        upsert(SCHEDULING_GD_INITIAL_LEVEL, getStringValue(SCHEDULING_GD_INITIAL_LEVEL, "1000.0"), AlgorithmConfig.ValueType.NUMBER,
                "Mực nước ban đầu của Great Deluge (ngưỡng chấp nhận lời giải xấu). Cao → chấp nhận thoáng hơn; thấp → khắt khe hơn.");
        map.put(SCHEDULING_GD_INITIAL_LEVEL, "OK");
        upsert(SCHEDULING_GD_DECAY_RATE, getStringValue(SCHEDULING_GD_DECAY_RATE, "0.999"), AlgorithmConfig.ValueType.NUMBER,
                "Tốc độ giảm mực nước Great Deluge mỗi vòng lặp. Gần 1.0 → giảm chậm, tìm kiếm kỹ; nhỏ hơn → giảm nhanh, hội tụ sớm.");
        map.put(SCHEDULING_GD_DECAY_RATE, "OK");
        upsert(SCHEDULING_GD_MIN_LEVEL, getStringValue(SCHEDULING_GD_MIN_LEVEL, "0.0"), AlgorithmConfig.ValueType.NUMBER,
                "Mực nước tối thiểu của Great Deluge — khi đạt ngưỡng, dừng thuật toán.");
        map.put(SCHEDULING_GD_MIN_LEVEL, "OK");
        upsert(BALANCE_SCORE_WORST, getStringValue(BALANCE_SCORE_WORST, "0.50"), AlgorithmConfig.ValueType.NUMBER,
                "Hệ số CV (Coefficient of Variation) tải ca trực cho phép ở mức tệ nhất. Thấp → yêu cầu cân bằng chặt; cao → chấp nhận chênh lệch nhiều hơn.");
        map.put(BALANCE_SCORE_WORST, "OK");
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
                .minStaffPerShift(getIntValue(MIN_STAFF_PER_SHIFT, 1, cache))
                .maxStaffPerShift(getIntValue(MAX_STAFF_PER_SHIFT, 0, cache))
                .minShiftsPerStaff(getIntValue(MIN_SHIFTS_PER_STAFF, 0, cache))
                .maxShiftsPerStaff(getIntValue(MAX_SHIFTS_PER_STAFF, 99, cache))
                // Per-type weekly max from AutoGenConfig
                .l01MaxPerWeek(autoGenConfig.map(AutoGenConfig::l01MaxPerWeek).orElse(0))
                .l02MaxPerWeek(autoGenConfig.map(AutoGenConfig::l02MaxPerWeek).orElse(0))
                .l03MaxPerWeek(autoGenConfig.map(AutoGenConfig::l03MaxPerWeek).orElse(0))
                .l04MaxPerWeek(autoGenConfig.map(AutoGenConfig::l04MaxPerWeek).orElse(0))
                .build();
    }

    /**
     * Save algorithm runtime configuration.
     *
     * <p>PR-MAX-WEEK: {@code l01MaxPerWeek..l04MaxPerWeek} deliberately live
     * here even though they are also part of {@link AutoGenConfig}.  The
     * runtime editor bundles them with the rest of {@code RuntimeConfig} on
     * save, but historically this method skipped them.  When a user modified
     * the per-type weekly maximums in the UI and hit Save, the values were
     * persisted successfully (HTTP 200, toast "Đã lưu cấu hình thuật toán")
     * yet vanished on refresh — because {@link #getRuntimeConfig} reads those
     * four fields from {@link AutoGenConfig} (which only writes via
     * {@code updateAutoGenConfig}), and a partial failure of the second PUT
     * left the {@code auto_gen_l*_max_per_week} rows at their defaults.
     * Persisting here makes the runtime endpoint self-sufficient: a single
     * PUT refreshes everything the UI bound to that endpoint.  The
     * auto-gen-config endpoint still upserts the same rows; concurrent
     * writes are safe because {@link #upsert(String, String, AlgorithmConfig.ValueType, String)}
     * is idempotent on the same key.
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
                "Ngưỡng điểm cân bằng tải tối thiểu (0.0–1.0). Cao → phân bổ ca trực công bằng hơn nhưng có thể khó đạt; thấp → dễ đáp ứng nhưng có thể thiên lệch.");
        upsert(MIN_STAFF_PER_SHIFT, String.valueOf(config.getMinStaffPerShift()), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối thiểu mỗi ca. Đặt 0 để bỏ qua giới hạn này. Nếu không đủ nhân sự đạt ngưỡng, thuật toán sẽ cảnh báo nhưng vẫn xếp.");
        upsert(MAX_STAFF_PER_SHIFT, String.valueOf(config.getMaxStaffPerShift()), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối đa mỗi ca. Đặt 0 để không giới hạn. Giới hạn này chỉ áp dụng khi yêu cầu ca có requiredStaffCount > maxStaffPerShift.");
        upsert(MIN_SHIFTS_PER_STAFF, String.valueOf(config.getMinShiftsPerStaff()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca trực tối thiểu mỗi nhân sự trong kỳ. Đặt 0 để bỏ qua. Giúp đảm bảo mỗi người đều có ít nhất N ca trong kỳ.");
        upsert(MAX_SHIFTS_PER_STAFF, String.valueOf(config.getMaxShiftsPerStaff()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca trực tối đa mỗi nhân sự trong kỳ. Đặt 0 để dùng maxShiftsPerMonth của nhân sự. Giới hạn này ngược lại với min — ngăn không ai bị quá tải.");
        // PR-MAX-WEEK: see class javadoc.  Per-type weekly maxes are
        // shared between RuntimeConfig and AutoGenConfig — see the comment
        // on AUTO_GEN_L01_MAX_PER_WEEK constants.  We persist them on
        // both endpoints to make the runtime-config save a single
        // self-sufficient round trip.
        upsert(AUTO_GEN_L01_MAX_PER_WEEK, String.valueOf(config.getL01MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L01 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
        upsert(AUTO_GEN_L02_MAX_PER_WEEK, String.valueOf(config.getL02MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L02 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
        upsert(AUTO_GEN_L03_MAX_PER_WEEK, String.valueOf(config.getL03MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L03 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
        upsert(AUTO_GEN_L04_MAX_PER_WEEK, String.valueOf(config.getL04MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca L04 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
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
            java.util.List<String> expandedSpecialties) {

        // Snapshot existing config để giữ enabled, holidayMode, cross-specialty, allowed lists
        AutoGenConfig current = getAutoGenConfig().orElseThrow();

        int l01Target = Math.max(0, targetPerStaff.getOrDefault("L01", 0));
        int l02Target = Math.max(0, targetPerStaff.getOrDefault("L02", 0));
        int l03Target = Math.max(0, targetPerStaff.getOrDefault("L03", 0));
        int l04Target = Math.max(0, targetPerStaff.getOrDefault("L04", 0));

        int l01Elig = Math.max(1, eligibleStaff.getOrDefault("L01", 1));
        int l02Elig = Math.max(1, eligibleStaff.getOrDefault("L02", 1));
        int l03Elig = Math.max(1, eligibleStaff.getOrDefault("L03", 1));
        int l04Elig = Math.max(1, eligibleStaff.getOrDefault("L04", 1));

        int days = Math.max(1, periodDays);
        int weeks = Math.max(1, periodWeeks);

        int l01MinPerDay = Math.max(1, (int) Math.ceil((double) (l01Target * l01Elig) / days));
        int l02MinPerDay = Math.max(1, (int) Math.ceil((double) (l02Target * l02Elig) / days));
        int l03MinPerDay = Math.max(1, (int) Math.ceil((double) (l03Target * l03Elig) / days));
        int l04MinPerDay = Math.max(1, (int) Math.ceil((double) (l04Target * l04Elig) / days));

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

        int totalExpected = (l01Target * l01Elig) + (l02Target * l02Elig)
                + (l03Target * l03Elig) + (l04Target * l04Elig);

        // L01/L02/L03: không có specialty config — dùng StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES
        // Chỉ L04 có specialty config
        AutoGenConfig recommended = new AutoGenConfig(
                current.enabled(),
                l01MinPerDay, l02MinPerDay, l03MinPerDay, l04MinPerDay,
                l01MaxPerDay, l02MaxPerDay, l03MaxPerDay, l04MaxPerDay,
                l01MinPerWeek, l02MinPerWeek, l03MinPerWeek, l04MinPerWeek,
                l01MaxPerWeek, l02MaxPerWeek, l03MaxPerWeek, l04MaxPerWeek,
                current.holidayMode(),
                current.removedShiftTypes() != null ? current.removedShiftTypes() : java.util.List.of(),
                // L04 only
                current.l04CrossSpecialty(),
                current.l04CrossSpecialtyRatio(),
                current.l04AllowedSpecialties() != null ? current.l04AllowedSpecialties() : java.util.List.of(),
                current.l04BalanceStrategy() != null ? current.l04BalanceStrategy() : AutoGenConstants.BALANCE_STRATEGY_FAIR_DISTRIBUTE
        );

        String rationale = String.format(
                "Đề xuất cho kỳ %d ngày/%d tuần với tổng ca dự kiến = %d. " +
                "L01/L02/L03: %d/%d/%d ca/người × %d/%d/%d người eligible (tất cả 6 khoa). " +
                "L04: %d ca/người × %d người eligible. " +
                "Eligible pool: %s",
                days, weeks, totalExpected,
                l01Target, l02Target, l03Target, l01Elig, l02Elig, l03Elig,
                l04Target, l04Elig,
                expandNonL04Eligibility
                    ? "Mở rộng cho tất cả specialties để đạt mục tiêu."
                    : "StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES (Ngoại, Nội, Sản, Nhi, Mắt, Răng)."
        );

        return new AutoGenConfigRecommendation(recommended, totalExpected, rationale);
    }

    /**
     * Kết quả recommend bao gồm config + metadata
     */
    public record AutoGenConfigRecommendation(
            AutoGenConfig config,
            int totalShiftsExpected,
            String rationale
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
        private int maxStaffPerShift;
        private int maxShiftsPerStaff;
        // Per-shift-type weekly max (from AutoGenConfig)
        private int l01MaxPerWeek;
        private int l02MaxPerWeek;
        private int l03MaxPerWeek;
        private int l04MaxPerWeek;

        /**
         * @deprecated Not used in scheduler v1.0. Kept only for backward compatibility.
         *
         * <p>TODO(v1.1):
         * <ul>
         *   <li>Remove from {@link AlgorithmRuntimeConfig} record</li>
         *   <li>Remove constants from {@link AlgorithmConfigService}</li>
         *   <li>Remove from {@link com.hospital.scheduler.scheduling.config.ConfigMapper}</li>
         *   <li>Remove from {@link com.hospital.scheduler.scheduling.config.ConfigMetadataRegistry}</li>
         *   <li>Remove DB rows: {@code min_staff_per_shift}</li>
         * </ul>
         *
         * @see <a href="https://github.com/tmHieu20-02/business-trip-management/issues">GitHub Issues</a>
         */
        @Deprecated
        private int minStaffPerShift;

        /**
         * @deprecated Not used in scheduler v1.0. Kept only for backward compatibility.
         *
         * <p>TODO(v1.1):
         * <ul>
         *   <li>Remove from {@link AlgorithmRuntimeConfig} record</li>
         *   <li>Remove constants from {@link AlgorithmConfigService}</li>
         *   <li>Remove from {@link com.hospital.scheduler.scheduling.config.ConfigMapper}</li>
         *   <li>Remove from {@link com.hospital.scheduler.scheduling.config.ConfigMetadataRegistry}</li>
         *   <li>Remove DB rows: {@code min_shifts_per_staff}</li>
         * </ul>
         *
         * @see <a href="https://github.com/tmHieu20-02/business-trip-management/issues">GitHub Issues</a>
         */
        @Deprecated
        private int minShiftsPerStaff;
    }
}