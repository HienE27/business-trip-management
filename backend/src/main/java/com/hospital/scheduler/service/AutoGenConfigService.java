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
                crud.getStringListValue(AlgorithmConfigService.AUTO_GEN_REMOVED_SHIFT_TYPES, cache)
        );
    }
}