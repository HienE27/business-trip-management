package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Owns the AI recommendation flow for {@link AutoGenConfig} — computes an
 * optimal per-type / per-week / per-day config from user-supplied targets
 * and eligible-staff counts. Extracted from {@link AlgorithmConfigService}
 * in P5-completion.
 *
 * <p>Reads the current config via {@link AutoGenConfigService} so the
 * recommendation starts from the persisted state (rather than defaults).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AlgorithmConfigRecommendationService {

    private final AutoGenConfigService autoGenConfigService;

    /**
     * Compute a recommended {@link AutoGenConfig} from targets.
     *
     * <p>Formula:
     * <ul>
     *   <li>{@code minPerDay = ⌈(target × eligible) / periodDays⌋} — coverage</li>
     *   <li>{@code maxPerDay = ⌈minPerDay × 1.2⌉} — daily peak buffer</li>
     * </ul>
     */
    public AutoGenConfigRecommendation recommendAutoGenConfig(
            int periodDays,
            int periodWeeks,
            Map<String, Integer> eligibleStaff,
            Map<String, Integer> targetPerStaff,
            boolean expandNonL04Eligibility,
            List<String> expandedSpecialties) {

        AutoGenConfig current = autoGenConfigService.getAutoGenConfig().orElseThrow();

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

        int l01MaxPerDay = Math.max(l01MinPerDay, (int) Math.ceil(l01MinPerDay * 1.2));
        int l02MaxPerDay = Math.max(l02MinPerDay, (int) Math.ceil(l02MinPerDay * 1.2));
        int l03MaxPerDay = Math.max(l03MinPerDay, (int) Math.ceil(l03MinPerDay * 1.2));
        int l04MaxPerDay = Math.max(l04MinPerDay, (int) Math.ceil(l04MinPerDay * 1.2));

        int totalExpected = (l01Target * l01Elig) + (l02Target * l02Elig)
                + (l03Target * l03Elig) + (l04Target * l04Elig);
        // Chỉ L04 có specialty config
        AutoGenConfig recommended = new AutoGenConfig(
                current.enabled(),
                l01MinPerDay, l02MinPerDay, l03MinPerDay, l04MinPerDay,
                l01MaxPerDay, l02MaxPerDay, l03MaxPerDay, l04MaxPerDay,
                0, 0, 0, 0,  // max/week (unused by algorithm, kept for future enforcement)
                current.holidayMode(),
                current.removedShiftTypes() != null ? current.removedShiftTypes() : List.of(),
                // L04 only
                current.l04CrossSpecialty(),
                current.l04CrossSpecialtyRatio(),
                current.l04AllowedSpecialties() != null ? current.l04AllowedSpecialties() : List.of(),
                current.l04BalanceStrategy() != null ? current.l04BalanceStrategy() : "FAIR_DISTRIBUTE"
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
     * Recommendation result: config + metadata.
     */
    public record AutoGenConfigRecommendation(
            AutoGenConfig config,
            int totalShiftsExpected,
            String rationale
    ) {}
}