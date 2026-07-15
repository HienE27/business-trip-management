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
     *   <li>{@code minPerWeek = ⌈target / periodWeeks⌋} — weekly fair-share</li>
     *   <li>{@code maxPerWeek = ⌈(target / periodWeeks) × 1.5⌉} — buffer 50%</li>
     *   <li>{@code maxPerDay = ⌈maxPerWeek × 1.2⌉} — daily peak buffer</li>
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

        List<String> l01Spec = expandNonL04Eligibility
                ? (expandedSpecialties != null && !expandedSpecialties.isEmpty()
                    ? expandedSpecialties
                    : List.of("Bác sĩ", "Điều dưỡng", "Kỹ thuật viên", "Dược sĩ",
                        "Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng"))
                : (current.l01AllowedSpecialties() != null && !current.l01AllowedSpecialties().isEmpty()
                    ? current.l01AllowedSpecialties()
                    : List.of("Ngoại", "Nội"));
        List<String> l02Spec = expandNonL04Eligibility
                ? l01Spec
                : (current.l02AllowedSpecialties() != null && !current.l02AllowedSpecialties().isEmpty()
                    ? current.l02AllowedSpecialties()
                    : List.of("Ngoại", "Nội"));
        List<String> l03Spec = expandNonL04Eligibility
                ? l01Spec
                : (current.l03AllowedSpecialties() != null && !current.l03AllowedSpecialties().isEmpty()
                    ? current.l03AllowedSpecialties()
                    : List.of("Ngoại", "Nội"));

        int totalExpected = (l01Target * l01Elig) + (l02Target * l02Elig)
                + (l03Target * l03Elig) + (l04Target * l04Elig);

        AutoGenConfig recommended = new AutoGenConfig(
                current.enabled(),
                l01MinPerDay, l02MinPerDay, l03MinPerDay, l04MinPerDay,
                l01MaxPerDay, l02MaxPerDay, l03MaxPerDay, l04MaxPerDay,
                l01MinPerWeek, l02MinPerWeek, l03MinPerWeek, l04MinPerWeek,
                l01MaxPerWeek, l02MaxPerWeek, l03MaxPerWeek, l04MaxPerWeek,
                current.holidayMode(),
                current.removedShiftTypes() != null ? current.removedShiftTypes() : List.of(),
                // L01
                current.l01CrossSpecialty(),
                current.l01CrossSpecialtyRatio(),
                l01Spec,
                "FAIR_DISTRIBUTE",
                // L02
                current.l02CrossSpecialty(),
                current.l02CrossSpecialtyRatio(),
                l02Spec,
                "FAIR_DISTRIBUTE",
                // L03
                current.l03CrossSpecialty(),
                current.l03CrossSpecialtyRatio(),
                l03Spec,
                "FAIR_DISTRIBUTE",
                // L04
                current.l04CrossSpecialty(),
                current.l04CrossSpecialtyRatio(),
                current.l04AllowedSpecialties() != null ? current.l04AllowedSpecialties() : List.of(),
                "FAIR_DISTRIBUTE"
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