package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.AlgorithmConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Owns the runtime algorithm configuration (cross-type parameters like
 * weekend weight, greedy coverage threshold, max shifts per staff). Extracted
 * from {@link AlgorithmConfigService} in P5-completion.
 *
 * <p>Reads share the bulk cache with {@link AutoGenConfigService} via
 * {@link AlgorithmConfigCrudService#loadConfigCache()}, and writes go through
 * {@link AlgorithmConfigCrudService#upsert} so the cache and the DB stay in
 * sync.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RuntimeConfigService {

    private final AlgorithmConfigCrudService crud;
    private final AutoGenConfigService autoGenConfigService;

    /**
     * Get runtime config. The per-type weekly max fields are sourced from
     * {@link AutoGenConfigService} so the runtime view always reflects the
     * persisted AutoGen settings.
     */
    @Transactional(readOnly = true)
    public AlgorithmConfigService.AlgorithmRuntimeConfig getRuntimeConfig() {
        var autoGenConfig = autoGenConfigService.getAutoGenConfig();
        Map<String, String> cache = crud.loadConfigCache();
        return AlgorithmConfigService.AlgorithmRuntimeConfig.builder()
                .weekendWeight(crud.getBigDecimalValue(AlgorithmConfigService.WEEKEND_WEIGHT, 2.0, cache))
                .overnightRecoveryHours(crud.getIntValue(AlgorithmConfigService.OVERNIGHT_RECOVERY_HOURS, 24, cache))
                .greedyCoverageThreshold(crud.getBigDecimalValue(AlgorithmConfigService.GREEDY_COVERAGE_THRESHOLD, 0.85, cache))
                .balanceScoreMin(crud.getBigDecimalValue(AlgorithmConfigService.BALANCE_SCORE_MIN, 0.70, cache))
                .minStaffPerShift(crud.getIntValue(AlgorithmConfigService.MIN_STAFF_PER_SHIFT, 1, cache))
                .maxStaffPerShift(crud.getIntValue(AlgorithmConfigService.MAX_STAFF_PER_SHIFT, 0, cache))
                .minShiftsPerStaff(crud.getIntValue(AlgorithmConfigService.MIN_SHIFTS_PER_STAFF, 0, cache))
                .maxShiftsPerStaff(crud.getIntValue(AlgorithmConfigService.MAX_SHIFTS_PER_STAFF, 99, cache))
                .build();
    }

    /**
     * Save runtime config. All fields are persisted via the shared
     * {@link AlgorithmConfigCrudService#upsert} helper.
     */
    public void saveRuntimeConfig(AlgorithmConfigService.AlgorithmRuntimeConfig config) {
        upsert(AlgorithmConfigService.WEEKEND_WEIGHT, String.valueOf(config.getWeekendWeight()), AlgorithmConfig.ValueType.NUMBER,
                "Hệ số phạt khi xếp lịch cho người vào thứ 7 / chủ nhật. Giá trị càng cao → thuật toán càng tránh xếp ca cuối tuần. Đặt 1 để tắt ưu tiên.");
        upsert(AlgorithmConfigService.OVERNIGHT_RECOVERY_HOURS, String.valueOf(config.getOvernightRecoveryHours()), AlgorithmConfig.ValueType.NUMBER,
                "Khoảng cách nghỉ bắt buộc giữa hai ca trực 24/24 liên tiếp của cùng một người. Thường đặt 24h để đảm bảo nghỉ ngơi đủ.");
        upsert(AlgorithmConfigService.GREEDY_COVERAGE_THRESHOLD, String.valueOf(config.getGreedyCoverageThreshold()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng phủ lịch tối thiểu (0.0–1.0). Khi tỷ lệ lịch đã phủ đạt mức này, thuật toán greedy sẽ dừng sớm. Giảm → chạy nhanh hơn; tăng → phủ kỹ hơn.");
        upsert(AlgorithmConfigService.BALANCE_SCORE_MIN, String.valueOf(config.getBalanceScoreMin()), AlgorithmConfig.ValueType.NUMBER,
                "Ngưỡng điểm cân bằng tải tối thiểu (0.0–1.0). Cao → phân bổ ca trực công bằng hơn nhưng có thể khó đạt; thấp → dễ đáp ứng nhưng có thể thiên lệch.");
        upsert(AlgorithmConfigService.MIN_STAFF_PER_SHIFT, String.valueOf(config.getMinStaffPerShift()), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối thiểu mỗi ca. Đặt 0 để bỏ qua giới hạn này. Nếu không đủ nhân sự đạt ngưỡng, thuật toán sẽ cảnh báo nhưng vẫn xếp.");
        upsert(AlgorithmConfigService.MAX_STAFF_PER_SHIFT, String.valueOf(config.getMaxStaffPerShift()), AlgorithmConfig.ValueType.NUMBER,
                "Số nhân sự tối đa mỗi ca. Đặt 0 để không giới hạn. Giới hạn này chỉ áp dụng khi yêu cầu ca có requiredStaffCount > maxStaffPerShift.");
        upsert(AlgorithmConfigService.MIN_SHIFTS_PER_STAFF, String.valueOf(config.getMinShiftsPerStaff()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca trực tối thiểu mỗi nhân sự trong kỳ. Đặt 0 để bỏ qua. Giúp đảm bảo mỗi người đều có ít nhất N ca trong kỳ.");
        upsert(AlgorithmConfigService.MAX_SHIFTS_PER_STAFF, String.valueOf(config.getMaxShiftsPerStaff()), AlgorithmConfig.ValueType.NUMBER,
                "Số ca trực tối đa mỗi nhân sự trong kỳ. Đặt 0 để dùng maxShiftsPerMonth của nhân sự. Giới hạn này ngược lại với min — ngăn không ai bị quá tải.");
    }

    private void upsert(String key, String value, AlgorithmConfig.ValueType type, String desc) {
        crud.upsert(key, value, type, desc);
    }

    /**
     * Helper to call BigDecimal conversion from a Double field on the config.
     */
    @SuppressWarnings("unused")
    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}