package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.algorithm.AutoGenConstants;
import com.hospital.scheduler.entity.AlgorithmConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Owns AutoGenConfig reads and writes. Extracted from
 * AlgorithmConfigService in SERVICE_AUDIT.md P5.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AutoGenConfigService {

    private final AlgorithmConfigCrudService crud;

    @Transactional(readOnly = true)
    public Optional<AutoGenConfig> getAutoGenConfig() {
        Map<String, String> cache = crud.loadConfigCache();
        String enabledRaw = cache.get(AlgorithmConfigService.AUTO_GEN_ENABLED);
        boolean enabled = enabledRaw == null || Boolean.parseBoolean(enabledRaw);
        return Optional.of(buildAutoGenConfig(enabled, cache));
    }

    /**
     * Save (upsert) the AutoGen configuration. Every field of
     * {@link AutoGenConfig} is persisted as a separate row in
     * {@code algorithm_config} via the shared {@link AlgorithmConfigCrudService#upsert}.
     */
    public void saveAutoGenConfig(AutoGenConfig config) {
        crud.upsert(AlgorithmConfigService.AUTO_GEN_ENABLED, String.valueOf(config.enabled()), AlgorithmConfig.ValueType.BOOLEAN,
                "Tự động tạo yêu cầu nhân sự khi mở kỳ lịch mới.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L01_MIN_PER_DAY, String.valueOf(config.l01MinPerDay()), AlgorithmConfig.ValueType.NUMBER, "Mục tiêu nhân sự L01 mỗi ngày; thuật toán cố gắng đạt nhưng không phá ràng buộc cứng.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L02_MIN_PER_DAY, String.valueOf(config.l02MinPerDay()), AlgorithmConfig.ValueType.NUMBER, "Mục tiêu nhân sự L02 mỗi ngày; thuật toán cố gắng đạt nhưng không phá ràng buộc cứng.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L03_MIN_PER_DAY, String.valueOf(config.l03MinPerDay()), AlgorithmConfig.ValueType.NUMBER, "Mục tiêu nhân sự L03 mỗi ngày; thuật toán cố gắng đạt nhưng không phá ràng buộc cứng.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_MIN_PER_DAY, String.valueOf(config.l04MinPerDay()), AlgorithmConfig.ValueType.NUMBER, "Mục tiêu nhân sự L04 mỗi ngày/chuyên khoa; thuật toán cố gắng đạt nhưng không phá ràng buộc cứng.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L01_MAX_PER_DAY, String.valueOf(config.l01MaxPerDay()), AlgorithmConfig.ValueType.NUMBER, "Trần khuyến nghị L01 mỗi ngày khi sinh mục tiêu. 0 = không đặt trần.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L02_MAX_PER_DAY, String.valueOf(config.l02MaxPerDay()), AlgorithmConfig.ValueType.NUMBER, "Trần khuyến nghị L02 mỗi ngày khi sinh mục tiêu. 0 = không đặt trần.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L03_MAX_PER_DAY, String.valueOf(config.l03MaxPerDay()), AlgorithmConfig.ValueType.NUMBER, "Trần khuyến nghị L03 mỗi ngày khi sinh mục tiêu. 0 = không đặt trần.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_MAX_PER_DAY, String.valueOf(config.l04MaxPerDay()), AlgorithmConfig.ValueType.NUMBER, "Trần khuyến nghị L04 mỗi ngày/chuyên khoa khi sinh mục tiêu. 0 = không đặt trần.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L01_MAX_PER_WEEK, String.valueOf(config.l01MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Trần tối đa số ca L01 mỗi nhân sự trong 1 tuần. 0 = không giới hạn.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L02_MAX_PER_WEEK, String.valueOf(config.l02MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Trần tối đa số ca L02 mỗi nhân sự trong 1 tuần. 0 = không giới hạn.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L03_MAX_PER_WEEK, String.valueOf(config.l03MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Trần tối đa số ca L03 mỗi nhân sự trong 1 tuần. 0 = không giới hạn.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_MAX_PER_WEEK, String.valueOf(config.l04MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Trần tối đa số ca L04 mỗi nhân sự trong 1 tuần. 0 = không giới hạn.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_HOLIDAY_MODE, config.holidayMode(), AlgorithmConfig.ValueType.STRING,
                "Xử lý ngày lễ: SKIP = bỏ qua, PARTIAL = giảm cường độ.");
        String removedCsv = config.removedShiftTypes() == null
                ? ""
                : String.join(",", config.removedShiftTypes());
        crud.upsert(AlgorithmConfigService.AUTO_GEN_REMOVED_SHIFT_TYPES, removedCsv, AlgorithmConfig.ValueType.STRING,
                "Danh sách mã loại lịch (L01..L04) bị bỏ qua khi tự động tạo yêu cầu. Phân tách bằng dấu phẩy. Rỗng = không bỏ.");

        // L04 cross-specialty
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_CROSS_SPECIALTY, String.valueOf(config.l04CrossSpecialty()), AlgorithmConfig.ValueType.BOOLEAN,
                "Cho phép gán nhân sự từ chuyên khoa khác vào L04 (PK Chuyên gia) khi chuyên khoa gốc thiếu nhân sự.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_CROSS_SPECIALTY_RATIO, String.valueOf(config.l04CrossSpecialtyRatio()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng shortage L04 (0.0-1.0) để kích hoạt cross-specialty.");
        String allowedSpecs = config.l04AllowedSpecialties() == null || config.l04AllowedSpecialties().isEmpty()
                ? "" : String.join(",", config.l04AllowedSpecialties());
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_ALLOWED_SPECIALTIES, allowedSpecs, AlgorithmConfig.ValueType.STRING,
                "Danh sách chuyên khoa được gán L04. Rỗng = tất cả 6 khoa.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_BALANCE_STRATEGY,
                config.l04BalanceStrategy() != null ? config.l04BalanceStrategy() : AutoGenConstants.BALANCE_STRATEGY_FAIR_DISTRIBUTE,
                AlgorithmConfig.ValueType.STRING,
                "Chiến lược cân bằng cross-specialty L04: STRICT_MATCH_ONLY, FAIR_DISTRIBUTE, WEIGHTED_FAIR.");
    }

    private AutoGenConfig buildAutoGenConfig(boolean enabled, Map<String, String> cache) {
        return new AutoGenConfig(
                enabled,
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L01_MIN_PER_DAY, 1, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L02_MIN_PER_DAY, 1, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L03_MIN_PER_DAY, 1, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L04_MIN_PER_DAY, 1, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L01_MAX_PER_DAY, 0, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L02_MAX_PER_DAY, 0, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L03_MAX_PER_DAY, 0, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L04_MAX_PER_DAY, 0, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L01_MAX_PER_WEEK, 0, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L02_MAX_PER_WEEK, 0, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L03_MAX_PER_WEEK, 0, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L04_MAX_PER_WEEK, 0, cache),
                crud.getStringValue(AlgorithmConfigService.AUTO_GEN_HOLIDAY_MODE, AutoGenConstants.HOLIDAY_MODE_SKIP, cache),
                crud.getStringListValue(AlgorithmConfigService.AUTO_GEN_REMOVED_SHIFT_TYPES, cache),
                // L01/L02/L03: không có specialty config — dùng StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES
                // L04: có specialty config
                crud.getBooleanValue(AlgorithmConfigService.AUTO_GEN_L04_CROSS_SPECIALTY, false, cache),
                crud.getFloatValue(AlgorithmConfigService.AUTO_GEN_L04_CROSS_SPECIALTY_RATIO, 0.5f, cache),
                crud.getStringListValue(AlgorithmConfigService.AUTO_GEN_L04_ALLOWED_SPECIALTIES, cache),
                crud.getStringValue(AlgorithmConfigService.AUTO_GEN_L04_BALANCE_STRATEGY, AutoGenConstants.BALANCE_STRATEGY_FAIR_DISTRIBUTE, cache)
        );
    }
}