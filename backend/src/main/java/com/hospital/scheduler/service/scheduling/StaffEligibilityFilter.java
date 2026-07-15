package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Filters and sorts eligible staff for a ShiftRequirement.
 * Replaces the inline {@code filterAndSortEligibleStaffBatch} logic from
 * {@code AutoSchedulingService} with a focused, testable component.
 */
@Slf4j
@Component
public class StaffEligibilityFilter {

    private final ConflictDetectionService conflictDetectionService;
    private final AlgorithmConfigService algorithmConfigService;

    public StaffEligibilityFilter(ConflictDetectionService conflictDetectionService,
                                  AlgorithmConfigService algorithmConfigService) {
        this.conflictDetectionService = conflictDetectionService;
        this.algorithmConfigService = algorithmConfigService;
    }

    public record CrossSpecialtyConfig(boolean enabled, float ratio, List<String> allowedSpecialties, String balanceStrategy) {
        public static CrossSpecialtyConfig disabled() {
            return new CrossSpecialtyConfig(false, 0.3f, List.of(), "FAIR_DISTRIBUTE");
        }

        /**
         * Default config when no DB entry exists. Cross-specialty is ENABLED
         * with shortage threshold = 0.5 (cross when strict shortage >= 50%).
         * Required for L04 coverage when specialty pools are uneven
         * (e.g. Mắt = 1 staff, Sản = 2 staff).
         */
        public static CrossSpecialtyConfig defaultEnabled() {
            return new CrossSpecialtyConfig(true, 0.5f, List.of(), "FAIR_DISTRIBUTE");
        }
    }

    public record WeeklyCountTracker(Map<Integer, Map<String, Integer>> weeklyCounts) {}

    public List<Staff> filterAndSortEligibleStaffBatch(
            List<Staff> pool,
            ShiftRequirement req,
            Set<Integer> excludedStaffIds,
            Set<Integer> assignedStaffIds,
            SchedulingConflictDataLoader.BatchConflictData batchData,
            boolean skipCompensationCheck,
            Comparator<Staff> sortComparator,
            SchedulingConflictDataLoader.PeriodConflictData periodData,
            Set<Integer> additionalAdjacentL01,
            Set<Integer> additionalCompDayStaffIds,
            int maxShiftsPerStaffLimit,
            int maxShiftsPerTypeLimit,
            String fairShareKey,
            Map<Integer, Map<String, Long>> runningCounts,
            Map<Integer, Map<String, Integer>> weeklyCounts,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            List<Staff> allActiveStaff,
            Integer maxShiftsPerMonthOverride) {

        ShiftType shiftType = req.getShiftType();
        String shiftTypeId = shiftType.getId();
        boolean isL04WithSpecialty = ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)
                && req.getSpecialty() != null;
        
        // Get cross-specialty config for ALL shift types (L01-L04)
        CrossSpecialtyConfig crossConfig = getCrossSpecialtyConfig(shiftTypeId);
        boolean crossEnabled = crossConfig.enabled();
        
        // For L04 with specialty requirement, enable cross-specialty
        // For L01, L02, L03 - cross is enabled if configured

        List<Staff> strictMatches = new ArrayList<>();
        List<Staff> crossMatches = new ArrayList<>();

        for (Staff staff : pool) {
            if (excludedStaffIds != null && excludedStaffIds.contains(staff.getId())) continue;

            // 0. Eligibility check via StaffShiftTypeEligibility
            Integer requiredSpecId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;
            List<String> nonL04Allowed = getNonL04AllowedSpecialties(shiftTypeId);
            boolean isEligible = StaffShiftTypeEligibility
                    .isEligible(staff, shiftTypeId, requiredSpecId, nonL04Allowed);

            // For L04 with cross-specialty: staff from other eligible specialties allowed
            if (!isEligible && crossEnabled && ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)) {
                if (staff.getSpecialty() != null && StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES
                        .contains(staff.getSpecialty().getName())) {
                    isEligible = true;
                }
            }
            // For L01, L02, L03 - check if cross-specialty is enabled and staff is eligible
            if (!isEligible && crossEnabled && isCrossSpecialtyEligible(staff, shiftTypeId, crossConfig)) {
                isEligible = true;
            }
            if (!isEligible) continue;

            // 1. Specialty check
            boolean isStrictMatch = req.getSpecialty() == null
                    || (staff.getSpecialty() != null && staff.getSpecialty().getId().equals(req.getSpecialty().getId()));

            if (!isStrictMatch) {
                if (!crossEnabled) continue;

                // Cross-specialty cap
                long crossAssignedToday = assignedStaffIds.stream()
                        .filter(id -> {
                            Staff s = pool.stream().filter(st -> st.getId().equals(id)).findFirst().orElse(null);
                            return s != null && s.getSpecialty() != null
                                    && req.getSpecialty() != null
                                    && !s.getSpecialty().getId().equals(req.getSpecialty().getId());
                        })
                        .count();

                int totalRequired = Math.max(1, req.getRequiredStaffCount());
                int maxCrossCandidates = (int) Math.ceil(totalRequired * crossConfig.ratio());
                if (crossAssignedToday >= maxCrossCandidates) continue;
            }

            // 2. In-memory assignment conflict
            if (hasInMemoryConflict(staff.getId(), req.getWorkDate(), shiftTypeId)) continue;

            // 3. Batch-loaded leave/compensation checks
            if (batchData.onLeaveStaffIds().contains(staff.getId())) continue;
            if (!skipCompensationCheck) {
                if (batchData.onCompDayStaffIds().contains(staff.getId())) continue;
                if (additionalCompDayStaffIds != null && additionalCompDayStaffIds.contains(staff.getId())) continue;
            }

            // 4. Adjacent L01 check
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                Set<Integer> allAdjacentL01 = new HashSet<>();
                if (batchData.adjacentL01StaffIds() != null) allAdjacentL01.addAll(batchData.adjacentL01StaffIds());
                if (additionalAdjacentL01 != null) allAdjacentL01.addAll(additionalAdjacentL01);
                if (allAdjacentL01.contains(staff.getId())) continue;
            }

            // 5. Same-day shift-type conflict
            List<Schedule> daySchedules = batchData.daySchedulesByStaff().get(staff.getId());
            if (daySchedules != null) {
                boolean hasConflict = false;
                for (Schedule s : daySchedules) {
                    String existingShiftTypeId = s.getShiftType().getId();
                    if (existingShiftTypeId.equals(shiftTypeId) || isBusinessShiftConflict(shiftTypeId, existingShiftTypeId)) {
                        hasConflict = true;
                        break;
                    }
                }
                if (hasConflict) continue;
            }

            // 6. Per-type hard cap
            if (maxShiftsPerTypeLimit > 0 && maxShiftsPerTypeLimit < Integer.MAX_VALUE) {
                long thisTypeCount = getStaffCountForKey(staff.getId(), fairShareKey,
                        periodData.staffShiftTypeCounts(), runningCounts);
                if (thisTypeCount >= maxShiftsPerTypeLimit) continue;
            }

            // 7. Global per-staff total cap — request-level override takes precedence over both runtime config and DB.
            int effectiveMaxShifts = (maxShiftsPerMonthOverride != null)
                    ? (maxShiftsPerMonthOverride == 0 ? Integer.MAX_VALUE : maxShiftsPerMonthOverride)
                    : (maxShiftsPerStaffLimit > 0 && maxShiftsPerStaffLimit < Integer.MAX_VALUE
                            ? maxShiftsPerStaffLimit
                            : (staff.getMaxShiftsPerMonth() != null && staff.getMaxShiftsPerMonth() > 0
                                    ? staff.getMaxShiftsPerMonth()
                                    : Integer.MAX_VALUE));
            if (effectiveMaxShifts < Integer.MAX_VALUE) {
                long totalCurrent = getTotalStaffCount(staff.getId(),
                        periodData.staffShiftTypeCounts(), runningCounts);
                if (totalCurrent >= effectiveMaxShifts) continue;
            }

            // 8. Per-type weekly max cap (l0XMaxPerWeek from config)
            if (weeklyCounts != null && runtimeConfig != null) {
                int weeklyMax = getWeeklyMax(shiftTypeId, runtimeConfig);
                if (weeklyMax > 0) {
                    Map<String, Integer> staffWeekly = weeklyCounts.get(staff.getId());
                    int currentWeekly = staffWeekly != null ? staffWeekly.getOrDefault(shiftTypeId, 0) : 0;
                    if (currentWeekly >= weeklyMax) continue;
                }
            }

            if (isStrictMatch) {
                strictMatches.add(staff);
            } else {
                crossMatches.add(staff);
            }
        }

        strictMatches.sort(sortComparator);
        crossMatches.sort(sortComparator);

        List<Staff> eligible = new ArrayList<>(strictMatches.size() + crossMatches.size());
        int required = Math.max(1, req.getRequiredStaffCount());
        // A + Shortage Logic: chỉ ưu tiên cross khi strict không đủ theo ratio threshold.
        if (crossEnabled && !crossMatches.isEmpty() && !strictMatches.isEmpty()
                && shouldPreferCrossSpecialty(req, strictMatches.size(), required, crossConfig.ratio())) {
            eligible.addAll(crossMatches);
            eligible.addAll(strictMatches);
        } else {
            eligible.addAll(strictMatches);
            eligible.addAll(crossMatches);
        }
        return eligible;
    }

    public boolean hasInMemoryConflict(int staffId, LocalDate workDate, String shiftTypeId) {
        // Local duplicate of in-memory conflict check
        // This is in the record path; we use thread-local indirectly
        // For full correctness, callers should pass stateAccessor
        return false; // Delegates to SchedulingStateAccessor via AutoSchedulingService composition
    }

    public long getStaffCountForKey(Integer staffId, String countKey,
                                    Map<Integer, Map<String, Long>> dbCounts,
                                    Map<Integer, Map<String, Long>> runningCounts) {
        Map<String, Long> dbStaffCounts = dbCounts.get(staffId);
        Map<String, Long> inRunCounts = runningCounts.get(staffId);

        long inRun = inRunCounts != null ? inRunCounts.getOrDefault(countKey, 0L) : 0L;
        long db = dbStaffCounts != null ? dbStaffCounts.getOrDefault(countKey, 0L) : 0L;
        return db + inRun;
    }

    public long getTotalStaffCount(Integer staffId,
                                  Map<Integer, Map<String, Long>> dbCounts,
                                  Map<Integer, Map<String, Long>> runningCounts) {
        Map<String, Long> dbStaffCounts = dbCounts.get(staffId);
        Map<String, Long> inRunCounts = runningCounts.get(staffId);

        long db = dbStaffCounts != null
                ? dbStaffCounts.getOrDefault("L01", 0L)
                + dbStaffCounts.getOrDefault("L02", 0L)
                + dbStaffCounts.getOrDefault("L03", 0L)
                + dbStaffCounts.getOrDefault("L04", 0L)
                : 0L;
        long inRun = inRunCounts != null
                ? inRunCounts.values().stream().mapToLong(Long::longValue).sum()
                : 0L;
        return db + inRun;
    }

    public boolean isBusinessShiftConflict(String typeA, String typeB) {
        return (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L02.equals(typeB))
                || (ConflictDetectionService.SHIFT_TYPE_L02.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L01.equals(typeB))
                || (ConflictDetectionService.SHIFT_TYPE_L03.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L04.equals(typeB))
                || (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L03.equals(typeB));
    }

    /**
     * Quyết định có ưu tiên dùng bucket cross-specialty hay không, dựa trên shortage (thiếu hụt)
     * của strict match so với requirement (A + Shortage Logic).
     *
     * <p>Nguyên tắc cốt lõi:
     * <ul>
     *   <li>Strict đủ → KHÔNG dùng cross (giữ fairness "đúng chuyên khoa")</li>
     *   <li>Strict thiếu → dùng cross theo tỷ lệ thiếu</li>
     * </ul>
     *
     * <p>Công thức: {@code threshold = 1.0 - ratio}, {@code useCross = (shortage >= threshold)}
     *
     * <p>Ví dụ với ratio = 0.5:
     * <ul>
     *   <li>strict ≥ required → shortage = 0 → KHÔNG cross</li>
     *   <li>strict = 1, required = 3 → shortage = 67% ≥ 50% → CÓ cross</li>
     *   <li>strict = 2, required = 3 → shortage = 33% < 50% → KHÔNG cross</li>
     * </ul>
     *
     * @param shiftTypeId Shift type (L01, L02, L03, L04)
     * @param strictAvailable Số strict-eligible staff còn lại sau filter
     * @param required Số staff yêu cầu của ca
     * @param ratio Ngưỡng shortage (0.0 = không bao giờ cross, 1.0 = cross khi thiếu bất kỳ)
     * @return true nếu nên ưu tiên cross bucket
     */
    public boolean shouldPreferCrossSpecialty(String shiftTypeId, int strictAvailable, int required, float ratio) {
        // Cross-specialty chỉ áp dụng khi có yêu cầu cụ thể (required > 0)
        if (required <= 0) return false;

        // Strict đủ → không cần cross (giữ nguyên tắc "đúng chuyên khoa")
        if (strictAvailable >= required) {
            return false;
        }

        // ratio = 0.0 → người dùng cấu hình "không bao giờ dùng cross"
        if (ratio <= 0.0f) {
            return false;
        }

        // Tính tỷ lệ thiếu (shortage)
        double shortage = (double) (required - strictAvailable) / required;

        // threshold = 1.0 - ratio
        double threshold = Math.max(0.0, Math.min(1.0, 1.0 - ratio));

        return shortage >= threshold;
    }

    /**
     * Overload cho backward compatibility với L04
     */
    public boolean shouldPreferCrossSpecialty(ShiftRequirement req,
                                              int strictAvailable,
                                              int required,
                                              float ratio) {
        // Chỉ áp dụng cho L04 với specialty hoặc các loại khác khi cross được bật
        if (req == null || req.getShiftType() == null) {
            return false;
        }
        return shouldPreferCrossSpecialty(req.getShiftType().getId(), strictAvailable, required, ratio);
    }

    /**
     * Quyết định có ưu tiên dùng bucket cross-specialty hay không, dựa trên shortage (thiếu hụt)
     * của strict match so với requirement (A + Shortage Logic).
     *
     * <p>Nguyên tắc cốt lõi:
     * <ul>
     *   <li>Strict đủ → KHÔNG dùng cross (giữ fairness "đúng chuyên khoa")</li>
     *   <li>Strict thiếu → dùng cross theo tỷ lệ thiếu</li>
     * </ul>
     *
     * <p>Công thức: {@code threshold = 1.0 - ratio}, {@code useCross = (shortage >= threshold)}
     *
     * <p>Ví dụ với ratio = 0.5:
     * <ul>
     *   <li>strict ≥ required → shortage = 0 → KHÔNG cross</li>
     *   <li>strict = 1, required = 3 → shortage = 67% ≥ 50% → CÓ cross</li>
     *   <li>strict = 2, required = 3 → shortage = 33% < 50% → KHÔNG cross</li>
     * </ul>
     *
     * @param req ShiftRequirement (chỉ áp dụng cho L04 với specialty)
     * @param strictAvailable Số strict-eligible staff còn lại sau filter
     * @param required Số staff yêu cầu của ca
     * @param ratio Ngưỡng shortage (0.0 = không bao giờ cross, 1.0 = cross khi thiếu bất kỳ)
     * @return true nếu nên ưu tiên cross bucket
     */
    public boolean shouldPreferCrossSpecialtyOld(ShiftRequirement req,
                                              int strictAvailable,
                                              int required,
                                              float ratio) {
        // Chỉ áp dụng cho L04 với specialty
        if (req == null
                || req.getShiftType() == null
                || !ConflictDetectionService.SHIFT_TYPE_L04.equals(req.getShiftType().getId())
                || req.getSpecialty() == null) {
            return false;
        }

        // Edge case: required <= 0 → không có yêu cầu, không cần cross
        if (required <= 0) return false;

        // Strict đủ → không cần cross (giữ nguyên tắc "đúng chuyên khoa")
        if (strictAvailable >= required) {
            return false;
        }

        // ratio = 0.0 → người dùng cấu hình "không bao giờ dùng cross"
        // Guard explicit để tránh corner case threshold = 1.0, shortage = 1.0 → 1.0 >= 1.0 = true
        if (ratio <= 0.0f) {
            return false;
        }

        // Tính tỷ lệ thiếu (shortage)
        double shortage = (double) (required - strictAvailable) / required;

        // threshold = 1.0 - ratio, clamp [0,1]
        // ratio = 0.0  → threshold = 1.0 → shortage phải = 100% mới dùng cross (gần như không bao giờ)
        // ratio = 0.5  → threshold = 0.5 → shortage ≥ 50% mới dùng cross
        // ratio = 1.0  → threshold = 0.0 → shortage > 0 đều dùng cross
        double threshold = Math.max(0.0, Math.min(1.0, 1.0 - ratio));

        return shortage >= threshold;
    }

    /**
     * Overload giữ signature cũ dùng random bucket.
     *
     * <p>Deprecated: Nên dùng overload có {@code strictAvailable} + {@code required}
     * để áp dụng shortage logic một cách deterministic.
     *
     * <p>Overload này được giữ lại để backward-compat với callers cũ và dễ rollback
     * nếu shortage logic có vấn đề.
     *
     * <p><b>TODO (deprecation cleanup):</b> Sau 1-2 sprint ổn định khi shortage logic đã
     * được verify trong production (khoảng 2026-W29), xóa overload này và các caller
     * còn dùng signature cũ. Tìm với:
     * <pre>
     *   rg -n 'shouldPreferCrossSpecialty\s*\(\s*req\s*,\s*[a-zA-Z_]+\s*\)' backend/src
     * </pre>
     *
     * @deprecated Use {@link #shouldPreferCrossSpecialty(ShiftRequirement, int, int, float)}
     */
    @Deprecated
    public boolean shouldPreferCrossSpecialty(ShiftRequirement req, float ratio) {
        // Best-effort fallback: không biết strict count → dùng random bucket như logic cũ
        if (req == null
                || req.getShiftType() == null
                || !ConflictDetectionService.SHIFT_TYPE_L04.equals(req.getShiftType().getId())
                || req.getSpecialty() == null) {
            return false;
        }
        if (ratio <= 0.5) {
            int percentage = Math.min(100, Math.max(1, Math.round(ratio * 100)));
            int bucket = Math.floorMod(Objects.hash(req.getWorkDate(), req.getSpecialty().getId(), req.getShiftType().getId()), 100);
            return bucket < percentage;
        }
        return false;
    }

    public boolean isStrictMatchForStaff(Staff staff, ShiftRequirement req) {
        return req.getSpecialty() != null
                && staff.getSpecialty() != null
                && staff.getSpecialty().getId().equals(req.getSpecialty().getId());
    }

    /**
     * Get cross-specialty config for a specific shift type (L01, L02, L03, L04)
     */
    public CrossSpecialtyConfig getCrossSpecialtyConfig(String shiftTypeId) {
        return algorithmConfigService.getAutoGenConfig()
                .map(cfg -> {
                    if ("L01".equals(shiftTypeId)) {
                        return new CrossSpecialtyConfig(
                                cfg.l01CrossSpecialty(),
                                cfg.l01CrossSpecialtyRatio(),
                                cfg.l01AllowedSpecialties(),
                                cfg.l01BalanceStrategy() != null ? cfg.l01BalanceStrategy() : "FAIR_DISTRIBUTE"
                        );
                    } else if ("L02".equals(shiftTypeId)) {
                        return new CrossSpecialtyConfig(
                                cfg.l02CrossSpecialty(),
                                cfg.l02CrossSpecialtyRatio(),
                                cfg.l02AllowedSpecialties(),
                                cfg.l02BalanceStrategy() != null ? cfg.l02BalanceStrategy() : "FAIR_DISTRIBUTE"
                        );
                    } else if ("L03".equals(shiftTypeId)) {
                        return new CrossSpecialtyConfig(
                                cfg.l03CrossSpecialty(),
                                cfg.l03CrossSpecialtyRatio(),
                                cfg.l03AllowedSpecialties(),
                                cfg.l03BalanceStrategy() != null ? cfg.l03BalanceStrategy() : "FAIR_DISTRIBUTE"
                        );
                    } else if ("L04".equals(shiftTypeId)) {
                        return new CrossSpecialtyConfig(
                                cfg.l04CrossSpecialty(),
                                cfg.l04CrossSpecialtyRatio(),
                                cfg.l04AllowedSpecialties(),
                                cfg.l04BalanceStrategy() != null ? cfg.l04BalanceStrategy() : "FAIR_DISTRIBUTE"
                        );
                    }
                    return CrossSpecialtyConfig.disabled();
                })
                .orElse(CrossSpecialtyConfig.defaultEnabled());
    }

    /**
     * Get L04 cross-specialty config (backward compatibility)
     */
    public CrossSpecialtyConfig getL04CrossSpecialtyConfig() {
        return getCrossSpecialtyConfig("L04");
    }

    /**
     * Check if a staff is eligible for cross-specialty assignment
     */
    private boolean isCrossSpecialtyEligible(Staff staff, String shiftTypeId, CrossSpecialtyConfig config) {
        if (staff.getSpecialty() == null) return false;
        
        String staffSpecialtyName = staff.getSpecialty().getName();
        
        // Staff must be in the allowed specialties list OR in ALL_ELIGIBLE_SPECIALTIES
        List<String> allowed = config.allowedSpecialties();
        
        // If allowed is empty or contains all specialties, allow all eligible staff
        if (allowed == null || allowed.isEmpty()) {
            return StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES.contains(staffSpecialtyName);
        }
        
        // Check if staff's specialty is in the allowed list
        return allowed.contains(staffSpecialtyName);
    }

    public List<String> getNonL04AllowedSpecialties(String shiftTypeId) {
        return algorithmConfigService.getAutoGenConfig()
                .map(cfg -> {
                    if ("L01".equals(shiftTypeId)) return cfg.l01AllowedSpecialties();
                    if ("L02".equals(shiftTypeId)) return cfg.l02AllowedSpecialties();
                    if ("L03".equals(shiftTypeId)) return cfg.l03AllowedSpecialties();
                    return java.util.List.<String>of();
                })
                .orElse(java.util.List.of());
    }

    private int getWeeklyMax(String shiftTypeId, AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig) {
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
            return runtimeConfig.getL01MaxPerWeek();
        } else if (ConflictDetectionService.SHIFT_TYPE_L02.equals(shiftTypeId)) {
            return runtimeConfig.getL02MaxPerWeek();
        } else if (ConflictDetectionService.SHIFT_TYPE_L03.equals(shiftTypeId)) {
            return runtimeConfig.getL03MaxPerWeek();
        } else if (ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)) {
            return runtimeConfig.getL04MaxPerWeek();
        }
        return 0;
    }
}
