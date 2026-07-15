package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
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
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L01_MIN_PER_WEEK, String.valueOf(config.l01MinPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L01 tối thiểu mỗi người mỗi tuần.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L02_MIN_PER_WEEK, String.valueOf(config.l02MinPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L02 tối thiểu mỗi người mỗi tuần.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L03_MIN_PER_WEEK, String.valueOf(config.l03MinPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L03 tối thiểu mỗi người mỗi tuần.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_MIN_PER_WEEK, String.valueOf(config.l04MinPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L04 tối thiểu mỗi người mỗi tuần.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L01_MAX_PER_WEEK, String.valueOf(config.l01MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L01 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L02_MAX_PER_WEEK, String.valueOf(config.l02MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L02 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L03_MAX_PER_WEEK, String.valueOf(config.l03MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L03 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_MAX_PER_WEEK, String.valueOf(config.l04MaxPerWeek()), AlgorithmConfig.ValueType.NUMBER, "Số ca L04 tối đa mỗi người mỗi tuần. 0 = không giới hạn.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_HOLIDAY_MODE, config.holidayMode(), AlgorithmConfig.ValueType.STRING,
                "Xử lý ngày lễ: SKIP = bỏ qua, PARTIAL = giảm cường độ.");
        String removedCsv = config.removedShiftTypes() == null
                ? ""
                : String.join(",", config.removedShiftTypes());
        crud.upsert("AUTO_GEN_REMOVED_SHIFT_TYPES", removedCsv, AlgorithmConfig.ValueType.STRING,
                "Danh sách mã loại lịch (L01..L04) bị bỏ qua khi tự động tạo yêu cầu. Phân tách bằng dấu phẩy. Rỗng = không bỏ.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_CROSS_SPECIALTY, String.valueOf(config.l04CrossSpecialty()), AlgorithmConfig.ValueType.BOOLEAN,
                "Cho phép gán nhân sự từ chuyên khoa khác vào L04 khi chuyên khoa gốc thiếu nhân sự.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_CROSS_SPECIALTY_RATIO, String.valueOf(config.l04CrossSpecialtyRatio()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng shortage L04 (0.0-1.0) để kích hoạt cross-specialty. Ví dụ: 0.5 = chỉ dùng cross khi strict thiếu ≥ 50%. 0.0 = không bao giờ. 1.0 = dùng cross khi thiếu bất kỳ.");
        String allowedSpecs = config.l04AllowedSpecialties() == null || config.l04AllowedSpecialties().isEmpty()
                ? "" : String.join(",", config.l04AllowedSpecialties());
        crud.upsert("AUTO_GEN_L04_ALLOWED_SPECIALTIES", allowedSpecs, AlgorithmConfig.ValueType.STRING,
                "Danh sách chuyên khoa được gán L04. Rỗng = tất cả chuyên khoa. Ví dụ: Ngoại,Nội,Sản");
        String l01Csv = config.l01AllowedSpecialties() == null ? "" : String.join(",", config.l01AllowedSpecialties());
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L01_ALLOWED_SPECIALTIES, l01Csv, AlgorithmConfig.ValueType.STRING,
                "Danh sách chuyên khoa được gán L01 (trực 24/24). Rỗng = mặc định Ngoại,Nội. Ví dụ: Ngoại,Nội,Sản,Nhi,Mắt,Răng");
        String l02Csv = config.l02AllowedSpecialties() == null ? "" : String.join(",", config.l02AllowedSpecialties());
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L02_ALLOWED_SPECIALTIES, l02Csv, AlgorithmConfig.ValueType.STRING,
                "Danh sách chuyên khoa được gán L02 (thông tầm). Rỗng = mặc định Ngoại,Nội.");
        String l03Csv = config.l03AllowedSpecialties() == null ? "" : String.join(",", config.l03AllowedSpecialties());
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L03_ALLOWED_SPECIALTIES, l03Csv, AlgorithmConfig.ValueType.STRING,
                "Danh sách chuyên khoa được gán L03 (phòng khám dịch vụ). Rỗng = mặc định Ngoại,Nội.");
        
        // L01 cross-specialty
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L01_CROSS_SPECIALTY, String.valueOf(config.l01CrossSpecialty()), AlgorithmConfig.ValueType.BOOLEAN,
                "Cho phép gán nhân sự từ chuyên khoa khác vào L01 (trực 24/24) khi chuyên khoa gốc thiếu.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L01_CROSS_SPECIALTY_RATIO, String.valueOf(config.l01CrossSpecialtyRatio()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng shortage L01 (0.0-1.0) để kích hoạt cross-specialty.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L01_BALANCE_STRATEGY, config.l01BalanceStrategy() != null ? config.l01BalanceStrategy() : "FAIR_DISTRIBUTE", AlgorithmConfig.ValueType.STRING,
                "Chiến lược cân bằng cross-specialty L01: STRICT_MATCH_ONLY, FAIR_DISTRIBUTE, WEIGHTED_FAIR.");
        
        // L02 cross-specialty
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L02_CROSS_SPECIALTY, String.valueOf(config.l02CrossSpecialty()), AlgorithmConfig.ValueType.BOOLEAN,
                "Cho phép gán nhân sự từ chuyên khoa khác vào L02 (thông tầm) khi chuyên khoa gốc thiếu.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L02_CROSS_SPECIALTY_RATIO, String.valueOf(config.l02CrossSpecialtyRatio()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng shortage L02 (0.0-1.0) để kích hoạt cross-specialty.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L02_BALANCE_STRATEGY, config.l02BalanceStrategy() != null ? config.l02BalanceStrategy() : "FAIR_DISTRIBUTE", AlgorithmConfig.ValueType.STRING,
                "Chiến lược cân bằng cross-specialty L02: STRICT_MATCH_ONLY, FAIR_DISTRIBUTE, WEIGHTED_FAIR.");
        
        // L03 cross-specialty
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L03_CROSS_SPECIALTY, String.valueOf(config.l03CrossSpecialty()), AlgorithmConfig.ValueType.BOOLEAN,
                "Cho phép gán nhân sự từ chuyên khoa khác vào L03 (phòng khám dịch vụ) khi chuyên khoa gốc thiếu.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L03_CROSS_SPECIALTY_RATIO, String.valueOf(config.l03CrossSpecialtyRatio()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng shortage L03 (0.0-1.0) để kích hoạt cross-specialty.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L03_BALANCE_STRATEGY, config.l03BalanceStrategy() != null ? config.l03BalanceStrategy() : "FAIRDISTRIBUTE", AlgorithmConfig.ValueType.STRING,
                "Chiến lược cân bằng cross-specialty L03: STRICT_MATCH_ONLY, FAIR_DISTRIBUTE, WEIGHTED_FAIR.");
        
        // L04 cross-specialty
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_CROSS_SPECIALTY, String.valueOf(config.l04CrossSpecialty()), AlgorithmConfig.ValueType.BOOLEAN,
                "Cho phép gán nhân sự từ chuyên khoa khác vào L04 khi chuyên khoa gốc thiếu nhân sự.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_CROSS_SPECIALTY_RATIO, String.valueOf(config.l04CrossSpecialtyRatio()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng shortage L04 (0.0-1.0) để kích hoạt cross-specialty. Ví dụ: 0.5 = chỉ dùng cross khi strict thiếu ≥ 50%. 0.0 = không bao giờ. 1.0 = dùng cross khi thiếu bất kỳ.");
        crud.upsert(AlgorithmConfigService.AUTO_GEN_L04_BALANCE_STRATEGY, config.l04BalanceStrategy() != null ? config.l04BalanceStrategy() : "FAIR_DISTRIBUTE", AlgorithmConfig.ValueType.STRING,
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
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L01_MIN_PER_WEEK, 1, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L02_MIN_PER_WEEK, 2, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L03_MIN_PER_WEEK, 1, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L04_MIN_PER_WEEK, 1, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L01_MAX_PER_WEEK, 0, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L02_MAX_PER_WEEK, 0, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L03_MAX_PER_WEEK, 0, cache),
                crud.getIntValue(AlgorithmConfigService.AUTO_GEN_L04_MAX_PER_WEEK, 0, cache),
                crud.getStringValue(AlgorithmConfigService.AUTO_GEN_HOLIDAY_MODE, "SKIP", cache),
                crud.getStringListValue("AUTO_GEN_REMOVED_SHIFT_TYPES", cache),
                // L01 cross-specialty
                crud.getBooleanValue(AlgorithmConfigService.AUTO_GEN_L01_CROSS_SPECIALTY, false, cache),
                crud.getFloatValue(AlgorithmConfigService.AUTO_GEN_L01_CROSS_SPECIALTY_RATIO, 0.5f, cache),
                crud.getStringListValue(AlgorithmConfigService.AUTO_GEN_L01_ALLOWED_SPECIALTIES, cache),
                crud.getStringValue(AlgorithmConfigService.AUTO_GEN_L01_BALANCE_STRATEGY, "FAIR_DISTRIBUTE", cache),
                // L02 cross-specialty
                crud.getBooleanValue(AlgorithmConfigService.AUTO_GEN_L02_CROSS_SPECIALTY, false, cache),
                crud.getFloatValue(AlgorithmConfigService.AUTO_GEN_L02_CROSS_SPECIALTY_RATIO, 0.5f, cache),
                crud.getStringListValue(AlgorithmConfigService.AUTO_GEN_L02_ALLOWED_SPECIALTIES, cache),
                crud.getStringValue(AlgorithmConfigService.AUTO_GEN_L02_BALANCE_STRATEGY, "FAIR_DISTRIBUTE", cache),
                // L03 cross-specialty
                crud.getBooleanValue(AlgorithmConfigService.AUTO_GEN_L03_CROSS_SPECIALTY, false, cache),
                crud.getFloatValue(AlgorithmConfigService.AUTO_GEN_L03_CROSS_SPECIALTY_RATIO, 0.5f, cache),
                crud.getStringListValue(AlgorithmConfigService.AUTO_GEN_L03_ALLOWED_SPECIALTIES, cache),
                crud.getStringValue(AlgorithmConfigService.AUTO_GEN_L03_BALANCE_STRATEGY, "FAIR_DISTRIBUTE", cache),
                // L04 cross-specialty
                crud.getBooleanValue(AlgorithmConfigService.AUTO_GEN_L04_CROSS_SPECIALTY, false, cache),
                crud.getFloatValue(AlgorithmConfigService.AUTO_GEN_L04_CROSS_SPECIALTY_RATIO, 0.5f, cache),
                crud.getStringListValue("AUTO_GEN_L04_ALLOWED_SPECIALTIES", cache),
                crud.getStringValue(AlgorithmConfigService.AUTO_GEN_L04_BALANCE_STRATEGY, "FAIR_DISTRIBUTE", cache)
        );
    }
}