package com.hospital.scheduler.algorithm.scoring;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.ConflictDetectionService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Engine tính điểm chất lượng cho một lịch đã sinh.
 *
 * <p><b>3 thành phần điểm (có thể điều chỉnh qua config):</b>
 * <pre>
 *   totalScore = w_cov * coverageScore
 *              + w_fair * fairnessScore
 *              + w_con * constraintScore
 * </pre>
 *
 * <p><b>Default weights:</b> Coverage=0.40, Fairness=0.35, Constraint=0.25
 * (Coverage được ưu tiên vì thiếu ca là hard fail; Constraint chỉ là penalty).
 *
 * <p><b>Fairness:</b> Tính CV (Coefficient of Variation) theo từng loại ca.
 * L01/L02/L03: tính toàn cục.
 * L04: tính theo từng chuyên khoa (per-spec M05).
 * CV &lt; 10% → rất tốt. CV &gt; 30% → xấu.
 *
 * <p><b>Constraint:</b> Quét BR-01 → BR-05 (5 rule cốt lõi từ spec).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleQualityScorer {

    // ─────────────────────────────────────────────────────────────
    // Configuration (overridable)
    // ─────────────────────────────────────────────────────────────

    /** Trọng số coverage. Mặc định 0.40. */
    private double coverageWeight = DEFAULT_COVERAGE_WEIGHT;

    /** Trọng số fairness. Mặc định 0.35. */
    private double fairnessWeight = DEFAULT_FAIRNESS_WEIGHT;

    /** Trọng số constraint. Mặc định 0.25. */
    private double constraintWeight = DEFAULT_CONSTRAINT_WEIGHT;

    /** Ngưỡng đạt yêu cầu. Mặc định 80.0. */
    private double passThreshold = 80.0;

    /** Penalty cho mỗi HARD violation. Mặc định 25.0. */
    private double hardViolationPenalty = 25.0;

    /** Penalty cho mỗi SOFT violation. Mặc định 5.0. */
    private double softViolationPenalty = 5.0;

    /** CV mục tiêu [%]. CV ≤ targetCV * 100 → 100 điểm fairness. */
    private double targetCv = 0.10;

    /** CV vượt ngưỡng này [%] → 0 điểm fairness. */
    private double worstCv = 0.50;

    // ─────────────────────────────────────────────────────────────
    // Defaults — single source of truth for all three weights.
    // References: ScheduleQualityReport javadoc and test helpers.
    // ─────────────────────────────────────────────────────────────

    /** Default coverage weight: 0.40 */
    public static final double DEFAULT_COVERAGE_WEIGHT = 0.40;

    /** Default fairness weight: 0.35 */
    public static final double DEFAULT_FAIRNESS_WEIGHT = 0.35;

    /** Default constraint weight: 0.25 */
    public static final double DEFAULT_CONSTRAINT_WEIGHT = 0.25;

    // ─────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────

    private static final List<String> SHIFT_TYPES =
            Arrays.asList("L01", "L02", "L03", "L04");

    // ─────────────────────────────────────────────────────────────
    // CONFIG SETTERS
    // ─────────────────────────────────────────────────────────────

    public ScheduleQualityScorer withWeights(double coverage, double fairness, double constraint) {
        double sum = coverage + fairness + constraint;
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalArgumentException(
                "Weights must sum to 1.0, got " + sum);
        }
        this.coverageWeight = coverage;
        this.fairnessWeight = fairness;
        this.constraintWeight = constraint;
        return this;
    }

    public ScheduleQualityScorer withPassThreshold(double threshold) {
        this.passThreshold = threshold;
        return this;
    }

    public ScheduleQualityScorer withViolationPenalties(double hardPenalty, double softPenalty) {
        this.hardViolationPenalty = hardPenalty;
        this.softViolationPenalty = softPenalty;
        return this;
    }

    public ScheduleQualityScorer withCvTargets(double target, double worst) {
        this.targetCv = target;
        this.worstCv = worst;
        return this;
    }

    // ─────────────────────────────────────────────────────────────
    // MAIN ENTRY POINT
    // ─────────────────────────────────────────────────────────────

    /**
     * Tính điểm chất lượng đầy đủ cho một tập schedules.
     *
     * @param schedules     Các schedule đã sinh (có thể bao gồm preview schedules).
     * @param requirements  Tất cả requirements của kỳ (để tính coverage).
     * @param activeStaff   Danh sách nhân sự đang hoạt động (để tính pool cho fairness).
     * @param compDays      Các compensation days trong kỳ (để check BR-04).
     * @param leaveRequests Các leave request APPROVED (để check BR-05).
     * @param meta          Metadata (algorithm, time, rounds).
     * @return ScheduleQualityReport hoàn chỉnh.
     */
    public ScheduleQualityReport score(
            List<Schedule> schedules,
            List<ShiftRequirement> requirements,
            List<Staff> activeStaff,
            List<CompensationDay> compDays,
            List<LeaveRequest> leaveRequests,
            ScoringMeta meta) {

        long t0 = System.currentTimeMillis();

        if (schedules == null) schedules = List.of();
        if (requirements == null) requirements = List.of();
        if (activeStaff == null) activeStaff = List.of();
        if (compDays == null) compDays = List.of();
        if (leaveRequests == null) leaveRequests = List.of();

        // 1. Coverage
        CoverageResult cov = computeCoverage(schedules, requirements);
        double coverageScore = cov.totalCoveragePct;

        // 2. Fairness
        FairnessResult fair = computeFairness(schedules, activeStaff);
        double globalFairnessScore = fair.overallFairnessPct; // toàn viện (tham khảo)
        double fairnessScore = fair.internalFairnessPct; // trong nhóm (KPI chính)

        // 3. Constraint
        ConstraintResult con = computeConstraints(
            schedules, activeStaff, compDays, leaveRequests);
        double constraintScore = con.score;

        // 4. Tổng hợp — dùng fairnessScore (algorithm fairness) cho total
        double total = coverageWeight * coverageScore
                     + fairnessWeight * fairnessScore
                     + constraintWeight * constraintScore;
        total = clamp(total, 0.0, 100.0);

        // 5. Tìm max/min load staff (cho M07-F09)
        int maxLoad = 0, minLoad = Integer.MAX_VALUE;
        Integer maxStaff = null, minStaff = null;
        for (var entry : fair.totalShiftsByStaff.entrySet()) {
            int c = entry.getValue();
            if (c > maxLoad) { maxLoad = c; maxStaff = entry.getKey(); }
            if (c < minLoad) { minLoad = c; minStaff = entry.getKey(); }
        }
        if (minLoad == Integer.MAX_VALUE) minLoad = 0;
        int maxDeviation = maxLoad - minLoad;

        String grade = ScheduleQualityReport.gradeOf(total);
        boolean passed = total >= passThreshold;

        long elapsed = System.currentTimeMillis() - t0;
        log.debug("ScheduleQualityScorer: total={} coverage={} global={} algo={} constraint={} took={}ms",
            total, coverageScore, globalFairnessScore, fairnessScore, constraintScore, elapsed);

        return ScheduleQualityReport.builder()
            .totalScore(round(total))
            .grade(grade)
            .passed(passed)
            .coverageScore(round(coverageScore))
            .totalRequired(cov.totalRequired)
            .totalAssigned(cov.totalAssigned)
            .totalShortfall(cov.totalShortfall)
            .coverageByType(cov.byType)
            .fairnessScore(round(fairnessScore))
            .globalFairnessScore(round(globalFairnessScore))
            .structuralLoadWarnings(fair.structuralLoadWarnings != null ? fair.structuralLoadWarnings : List.of())
            .fairnessByType(fair.byType)
            .totalShiftsByStaff(fair.totalShiftsByStaff)
            .shiftsByStaffAndType(fair.shiftsByStaffAndType)
            .maxDeviationTotal(maxDeviation)
            .maxLoadStaffId(maxStaff)
            .minLoadStaffId(minStaff)
            .constraintScore(round(constraintScore))
            .hardViolationCount(con.hardCount)
            .softViolationCount(con.softCount)
            .violations(con.violations)
            .algorithmUsed(meta != null ? meta.getAlgorithmUsed() : "unknown")
            .executionTimeMs(meta != null ? meta.getExecutionTimeMs() : elapsed)
            .optimizationRounds(meta != null ? meta.getOptimizationRounds() : 0)
            .build();
    }

    /**
     * Convenience overload: chỉ schedules + requirements + active staff.
     */
    public ScheduleQualityReport score(
            List<Schedule> schedules,
            List<ShiftRequirement> requirements,
            List<Staff> activeStaff,
            ScoringMeta meta) {
        return score(schedules, requirements, activeStaff, List.of(), List.of(), meta);
    }

    /**
     * Overload cho phép truyền {@link AutoGenConfig} để dùng config động
     * cho fairness eligibility (xem {@link #computeFairness(List, List, AutoGenConfig)}).
     * Nếu {@code autoGenConfig} là null → fallback về hardcoded CORE (Ngoại, Nội).
     */
    public ScheduleQualityReport score(
            List<Schedule> schedules,
            List<ShiftRequirement> requirements,
            List<Staff> activeStaff,
            List<CompensationDay> compDays,
            List<LeaveRequest> leaveRequests,
            ScoringMeta meta,
            AutoGenConfig autoGenConfig) {
        // Reuse main logic with a slight detour: we use a thread-local override
        // by wrapping into a helper that calls computeFairness with cfg.
        return scoreWithAutoGen(schedules, requirements, activeStaff, compDays, leaveRequests, meta, autoGenConfig);
    }

    private ScheduleQualityReport scoreWithAutoGen(
            List<Schedule> schedules,
            List<ShiftRequirement> requirements,
            List<Staff> activeStaff,
            List<CompensationDay> compDays,
            List<LeaveRequest> leaveRequests,
            ScoringMeta meta,
            AutoGenConfig cfg) {

        long t0 = System.currentTimeMillis();
        if (schedules == null) schedules = List.of();
        if (requirements == null) requirements = List.of();
        if (activeStaff == null) activeStaff = List.of();
        if (compDays == null) compDays = List.of();
        if (leaveRequests == null) leaveRequests = List.of();

        CoverageResult cov = computeCoverage(schedules, requirements);
        double coverageScore = cov.totalCoveragePct;

        // 2. Fairness (with AutoGen config for dynamic eligibility)
        FairnessResult fair = computeFairness(schedules, activeStaff, cfg);
        double globalFairnessScore = fair.overallFairnessPct;
        double fairnessScore = fair.internalFairnessPct;

        ConstraintResult con = computeConstraints(schedules, activeStaff, compDays, leaveRequests);
        double constraintScore = con.score;

        double total = coverageWeight * coverageScore
            + fairnessWeight * fairnessScore
            + constraintWeight * constraintScore;

        long elapsed = System.currentTimeMillis() - t0;
        return ScheduleQualityReport.builder()
            .totalScore(round(total))
            .grade(grade(total, passThreshold))
            .coverageScore(round(coverageScore))
            .fairnessScore(round(fairnessScore))
            .globalFairnessScore(round(globalFairnessScore))
            .structuralLoadWarnings(fair.structuralLoadWarnings != null ? fair.structuralLoadWarnings : List.of())
            .constraintScore(round(constraintScore))
            .totalRequired(cov.totalRequired)
            .totalAssigned(cov.totalAssigned)
            .totalShortfall(cov.totalShortfall)
            .coverageByType(cov.byType)
            .fairnessByType(fair.byType)
            .totalShiftsByStaff(fair.totalShiftsByStaff)
            .shiftsByStaffAndType(fair.shiftsByStaffAndType)
            .passed(total >= passThreshold)
            .hardViolationCount(con.hardCount)
            .softViolationCount(con.softCount)
            .violations(con.violations)
            .algorithmUsed(meta != null ? meta.getAlgorithmUsed() : "unknown")
            .executionTimeMs(meta != null ? meta.getExecutionTimeMs() : elapsed)
            .optimizationRounds(meta != null ? meta.getOptimizationRounds() : 0)
            .build();
    }

    private String grade(double total, double threshold) {
        if (total >= 90.0) return "A";
        if (total >= 80.0) return "B";
        if (total >= threshold) return "C";
        return "D";
    }

    // ─────────────────────────────────────────────────────────────
    // 1. COVERAGE
    // ─────────────────────────────────────────────────────────────

    @Getter
    @Builder
    private static class CoverageResult {
        double totalCoveragePct;
        int totalRequired;
        int totalAssigned;
        int totalShortfall;
        Map<String, ScheduleQualityReport.CoverageDetail> byType;
    }

    private CoverageResult computeCoverage(
            List<Schedule> schedules,
            List<ShiftRequirement> requirements) {

        // Build requirement key → requiredCount
        Map<String, Integer> requiredByReq = new HashMap<>();
        for (ShiftRequirement r : requirements) {
            String key = reqKey(r.getWorkDate(), r.getShiftType().getId(),
                                r.getSpecialty() != null ? r.getSpecialty().getId() : null);
            requiredByReq.merge(key, r.getRequiredStaffCount(), Integer::sum);
        }

        // Count assigned per requirement key
        Map<String, Integer> assignedByReq = new HashMap<>();
        for (Schedule s : schedules) {
            // Use composite key from schedule (workDate + shiftTypeId + specialty)
            // instead of requirement ID which may be null for transient requirements
            if (s.getStaff() == null || s.getShiftType() == null) continue;
            String key = reqKey(s.getWorkDate(), s.getShiftType().getId(),
                                s.getRequirement() != null && s.getRequirement().getSpecialty() != null
                                    ? s.getRequirement().getSpecialty().getId() : null);
            assignedByReq.merge(key, 1, Integer::sum);
        }

        // Compute per-type coverage
        Map<String, ScheduleQualityReport.CoverageDetail> byType = new LinkedHashMap<>();
        Map<String, int[]> perTypeStats = new LinkedHashMap<>();  // [required, assigned]

        int totalRequired = 0, totalAssigned = 0, totalShortfall = 0;

        for (var entry : requiredByReq.entrySet()) {
            String key = entry.getKey();
            int req = entry.getValue();
            int asn = assignedByReq.getOrDefault(key, 0);
            int shortfall = Math.max(0, req - asn);

            totalRequired += req;
            totalAssigned += asn;
            totalShortfall += shortfall;

            String typeId = key.split("\\|")[0];
            int[] stats = perTypeStats.computeIfAbsent(typeId, k -> new int[2]);
            stats[0] += req;
            stats[1] += asn;
        }

        for (var entry : perTypeStats.entrySet()) {
            String typeId = entry.getKey();
            int req = entry.getValue()[0];
            int asn = entry.getValue()[1];
            double pct = req > 0 ? (asn * 100.0 / req) : 100.0;

            byType.put(typeId, ScheduleQualityReport.CoverageDetail.builder()
                .shiftTypeId(typeId)
                .required(req)
                .assigned(asn)
                .shortfall(Math.max(0, req - asn))
                .coveragePct(round(pct))
                .build());
        }

        double overallPct = totalRequired > 0 ? (totalAssigned * 100.0 / totalRequired) : 100.0;

        return CoverageResult.builder()
            .totalCoveragePct(overallPct)
            .totalRequired(totalRequired)
            .totalAssigned(totalAssigned)
            .totalShortfall(totalShortfall)
            .byType(byType)
            .build();
    }

    // ─────────────────────────────────────────────────────────────
    // 2. FAIRNESS
    // ─────────────────────────────────────────────────────────────

    @Getter
    @Builder
    private static class FairnessResult {
        double overallFairnessPct;
        double internalFairnessPct; // per-specialty average (không bị ảnh hưởng bởi quy mô)
        List<String> structuralLoadWarnings; // chuyên khoa bị quá tải cấu trúc
        Map<String, ScheduleQualityReport.FairnessDetail> byType;
        Map<Integer, Integer> totalShiftsByStaff;
        Map<Integer, Map<String, Integer>> shiftsByStaffAndType;
    }

    private FairnessResult computeFairness(
            List<Schedule> schedules,
            List<Staff> activeStaff) {
        return computeFairness(schedules, activeStaff, null);
    }

    /**
     * Overload cho phép truyền {@link AutoGenConfig} để dùng config động cho
     * eligibility L01/L02/L03 (thay vì hardcoded CORE = Ngoại + Nội).
     * Nếu {@code cfg} là null → fallback về CORE defaults.
     */
    private FairnessResult computeFairness(
            List<Schedule> schedules,
            List<Staff> activeStaff,
            AutoGenConfig cfg) {

        if (activeStaff.isEmpty()) {
            return FairnessResult.builder()
                .overallFairnessPct(100.0)
                .byType(Map.of())
                .totalShiftsByStaff(Map.of())
                .shiftsByStaffAndType(Map.of())
                .build();
        }

        // Build per-shift-type eligibility map once so we compute fairness
        // only on staff who COULD have been assigned (Bác sĩ / Điều dưỡng for
        // L01/L02/L03, by-specialty for L04). This prevents Dược sĩ / KTV
        // (who have 0 ca by design) from inflating the CV.
        //
        // Nếu có AutoGenConfig → dùng danh sách allowed specialties động
        // (l01/l02/l03AllowedSpecialties). Nếu rỗng hoặc null → fallback CORE.
        final Set<Integer> nonL04Eligible;
        final Map<Integer, Set<Integer>> l04BySpec;
        if (cfg != null) {
            java.util.List<String> l01Specs = cfg.l01AllowedSpecialties() != null && !cfg.l01AllowedSpecialties().isEmpty()
                ? cfg.l01AllowedSpecialties() : java.util.List.of();
            java.util.List<String> l02Specs = cfg.l02AllowedSpecialties() != null && !cfg.l02AllowedSpecialties().isEmpty()
                ? cfg.l02AllowedSpecialties() : java.util.List.of();
            java.util.List<String> l03Specs = cfg.l03AllowedSpecialties() != null && !cfg.l03AllowedSpecialties().isEmpty()
                ? cfg.l03AllowedSpecialties() : java.util.List.of();

            // Gộp cả 3 danh sách (nếu có) → set union để fairness pool cho L01/L02/L03
            Set<String> unionSpecs = new HashSet<>();
            unionSpecs.addAll(l01Specs);
            unionSpecs.addAll(l02Specs);
            unionSpecs.addAll(l03Specs);

            nonL04Eligible = activeStaff.stream()
                .filter(s -> s != null && Boolean.TRUE.equals(s.getIsActive())
                    && s.getSpecialty() != null
                    && (unionSpecs.isEmpty()
                        ? StaffShiftTypeEligibility.CORE_ELIGIBLE_SPECIALTIES.contains(s.getSpecialty().getName())
                        : unionSpecs.contains(s.getSpecialty().getName())))
                .map(Staff::getId)
                .collect(Collectors.toSet());

            l04BySpec = StaffShiftTypeEligibility.getL04EligibilityBySpecialty(activeStaff, cfg.l04AllowedSpecialties());
        } else {
            nonL04Eligible =
                StaffShiftTypeEligibility.eligibleStaffIdsForNonL04(activeStaff);
            l04BySpec =
                StaffShiftTypeEligibility.getL04EligibilityBySpecialty(activeStaff);
        }

        // Group schedules by (shiftType, [specialty])
        // L01/L02/L03: always global (no specialty splitting)
        // L04: per-specialty (specialty-bound)
        Map<String, Map<Integer, Integer>> countsByTypeAndStaff = new HashMap<>();
        for (Schedule s : schedules) {
            String typeId = s.getShiftType().getId();
            String typeKey;
            if ("L04".equals(typeId)
                    && s.getRequirement() != null && s.getRequirement().getSpecialty() != null) {
                typeKey = typeId + ":" + s.getRequirement().getSpecialty().getId();
            } else {
                // L01/L02/L03: always global key (no specialty suffix)
                typeKey = typeId;
            }

            int staffId = s.getStaff().getId();
            countsByTypeAndStaff
                .computeIfAbsent(typeKey, k -> new HashMap<>())
                .merge(staffId, 1, Integer::sum);
        }

        Map<String, ScheduleQualityReport.FairnessDetail> fairnessByType = new LinkedHashMap<>();
        double totalCvWeighted = 0.0;
        int weightSum = 0;

        for (String typeKey : countsByTypeAndStaff.keySet()) {
            Map<Integer, Integer> perStaff = countsByTypeAndStaff.get(typeKey);

            // Determine pool: for L04 with specialty, only staff in that specialty.
            // For L01/L02/L03: only Bác sĩ / Điều dưỡng (other specialties cannot take these shifts).
            String typeId = typeKey.split(":")[0];
            Set<Integer> pool;
            if ("L04".equals(typeId) && typeKey.contains(":")) {
                Integer specId = Integer.parseInt(typeKey.split(":")[1]);
                pool = l04BySpec.getOrDefault(specId, Set.of());
            } else {
                pool = nonL04Eligible;
            }

            if (pool.isEmpty()) continue;

            int poolSize = pool.size();
            long totalForType = perStaff.values().stream().mapToInt(Integer::intValue).sum();
            double mean = totalForType * 1.0 / poolSize;

            // Variance includes zero-count staff (correct fairness measure)
            double sumSq = 0.0;
            int maxCount = 0, minCount = Integer.MAX_VALUE;
            for (Integer sid : pool) {
                int c = perStaff.getOrDefault(sid, 0);
                double diff = c - mean;
                sumSq += diff * diff;
                if (c > maxCount) maxCount = c;
                if (c < minCount) minCount = c;
            }
            if (minCount == Integer.MAX_VALUE) minCount = 0;

            double variance = sumSq / poolSize;
            double stdDev = Math.sqrt(variance);
            double cv = mean > 0 ? stdDev / mean : 0.0;

            // Fairness: use max-min deviation instead of CV for more intuitive scoring
            // CV penalizes low means which is misleading for fair distributions
            double maxMinDev = maxCount - minCount;
            double fairnessPct;
            if (maxCount == 0 || poolSize == 0) {
                fairnessPct = 100.0;
            } else {
                // Score = 100% when deviation ≤ 1, 0% when deviation ≥ 10
                double ratio = Math.min(1.0, maxMinDev / 10.0);
                fairnessPct = 100.0 * (1.0 - ratio);
            }

            String specialtyName = null;
            if (typeKey.contains(":")) {
                Integer specId = Integer.parseInt(typeKey.split(":")[1]);
                // Try to derive specialty name from any staff
                for (Schedule s : schedules) {
                    if (s.getRequirement() != null
                        && s.getRequirement().getSpecialty() != null
                        && specId.equals(s.getRequirement().getSpecialty().getId())) {
                        specialtyName = s.getRequirement().getSpecialty().getName();
                        break;
                    }
                }
            }

            fairnessByType.put(typeKey, ScheduleQualityReport.FairnessDetail.builder()
                .shiftTypeId(typeId)
                .specialtyName(specialtyName)
                .poolSize(poolSize)
                .mean(round(mean))
                .stdDev(round(stdDev))
                .cv(round(cv))
                .fairnessPct(round(fairnessPct))
                .maxCount(maxCount)
                .minCount(minCount)
                .maxDeviation(maxCount - minCount)
                .build());

            // Weight by total shifts in this type (more shifts → more impact)
            int weight = Math.max(1, (int) totalForType);
            totalCvWeighted += cv * weight;
            weightSum += weight;
        }

        double overallCv = weightSum > 0 ? totalCvWeighted / weightSum : 0.0;
        double overallFairness;
        if (overallCv <= targetCv) {
            overallFairness = 100.0;
        } else if (overallCv >= worstCv) {
            overallFairness = 0.0;
        } else {
            double ratio = (overallCv - targetCv) / (worstCv - targetCv);
            overallFairness = 100.0 * (1.0 - ratio);
        }

        // ── Algorithm Fairness: trung bình có trọng số fairness của các nhóm eligibility
        // Loại các nhóm chỉ có 1 người (eligible_size ≤ 1) vì không có quyết định phân bổ.
        // Dùng trọng số theo pool size (số người trong nhóm) để phản ánh đúng tầm ảnh hưởng.
        double internalFairness = 0;
        int internalWeightSum = 0;
        List<String> structuralWarnings = new ArrayList<>();
        for (var entry : fairnessByType.entrySet()) {
            String typeKey = entry.getKey();
            var detail = entry.getValue();
            int poolSize = detail.getPoolSize();
            // Bỏ qua nhóm eligible_size ≤ 1 (vd: Mắt 1 người — không có cạnh tranh)
            if (poolSize <= 1) {
                if (typeKey.contains(":") && detail.getMaxCount() >= 20) {
                    String specName = detail.getSpecialtyName() != null ? detail.getSpecialtyName() : typeKey;
                    structuralWarnings.add(specName + ": " + detail.getMaxCount() + " ca L04 / " + poolSize + " BS (excluded from algo fairness -- trivial group)");
                }
                continue;
            }
            internalFairness += detail.getFairnessPct() * poolSize;
            internalWeightSum += poolSize;
        }
        double internalFairnessPct = internalWeightSum > 0 ? internalFairness / internalWeightSum : 100.0;
	
        // Per-staff totals & per-(staff, type) breakdown
        Map<Integer, Integer> totalByStaff = new HashMap<>();
        Map<Integer, Map<String, Integer>> byStaffAndType = new HashMap<>();
        for (Schedule s : schedules) {
            int sid = s.getStaff().getId();
            totalByStaff.merge(sid, 1, Integer::sum);
            byStaffAndType
                .computeIfAbsent(sid, k -> new HashMap<>())
                .merge(s.getShiftType().getId(), 1, Integer::sum);
        }

        return FairnessResult.builder()
            .overallFairnessPct(overallFairness)
            .internalFairnessPct(internalFairnessPct)
            .structuralLoadWarnings(structuralWarnings)
            .byType(fairnessByType)
            .totalShiftsByStaff(totalByStaff)
            .shiftsByStaffAndType(byStaffAndType)
            .build();
    }

    // ─────────────────────────────────────────────────────────────
    // 3. CONSTRAINTS
    // ─────────────────────────────────────────────────────────────

    @Getter
    @Builder
    private static class ConstraintResult {
        double score;
        int hardCount;
        int softCount;
        List<ScheduleQualityReport.ConstraintViolation> violations;
    }

    /**
     * Quét tất cả vi phạm ràng buộc.
     *
     * <p>Rules (theo spec M07 §1.4 + §2):
     * <ul>
     *   <li><b>BR-01 (HARD):</b> L01 + L02 same day same staff → VIOLATION</li>
     *   <li><b>BR-02 (HARD):</b> L03 + L04 same day same staff → VIOLATION</li>
     *   <li><b>BR-03 (HARD):</b> Schedule assigned on staff's compensation day → VIOLATION</li>
     *   <li><b>BR-04 (HARD):</b> Adjacent L01 (2 L01 cách nhau &lt; 24h) → VIOLATION</li>
     *   <li><b>BR-05 (HARD):</b> Schedule assigned on APPROVED leave day → VIOLATION</li>
     *   <li><b>BR-06 (SOFT):</b> Staff vượt maxShiftsPerMonth → VIOLATION</li>
     *   <li><b>BR-07 (SOFT):</b> Same staff same shift-type same day (duplicate) → VIOLATION</li>
     * </ul>
     */
    private ConstraintResult computeConstraints(
            List<Schedule> schedules,
            List<Staff> activeStaff,
            List<CompensationDay> compDays,
            List<LeaveRequest> leaveRequests) {

        List<ScheduleQualityReport.ConstraintViolation> violations = new ArrayList<>();
        int hardCount = 0, softCount = 0;

        Map<Integer, Staff> staffMap = activeStaff.stream()
            .collect(Collectors.toMap(Staff::getId, s -> s));

        // Pre-index comp days
        Set<String> compDayKeys = new HashSet<>();
        for (CompensationDay cd : compDays) {
            compDayKeys.add(cd.getStaff().getId() + "|" + cd.getCompensationDate());
        }

        // Pre-index leaves
        Set<String> leaveKeys = new HashSet<>();
        for (LeaveRequest lr : leaveRequests) {
            if (lr.getStatus() != LeaveRequest.LeaveStatus.APPROVED) continue;
            // Iterate over date range
            LocalDate from = lr.getStartDate();
            LocalDate to = lr.getEndDate() != null ? lr.getEndDate() : from;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                leaveKeys.add(lr.getStaff().getId() + "|" + d);
            }
        }

        // Group schedules by (staff, date) for fast lookup
        Map<String, List<Schedule>> byStaffDate = new HashMap<>();
        Map<Integer, List<Schedule>> byStaff = new HashMap<>();
        for (Schedule s : schedules) {
            String key = s.getStaff().getId() + "|" + s.getWorkDate();
            byStaffDate.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            byStaff.computeIfAbsent(s.getStaff().getId(), k -> new ArrayList<>()).add(s);
        }

        // ── Scan per-schedule rules ──
        for (Schedule s : schedules) {
            int staffId = s.getStaff().getId();
            LocalDate date = s.getWorkDate();
            String typeId = s.getShiftType().getId();
            String staffKey = staffId + "|" + date;

            // BR-03: compensation day
            if (compDayKeys.contains(staffKey)) {
                violations.add(violation("BR-03", "HARD", staffId, staffMap, date,
                    "Lịch " + typeId + " được xếp vào ngày nghỉ bù"));
                hardCount++;
            }

            // BR-05: leave day
            if (leaveKeys.contains(staffKey)) {
                violations.add(violation("BR-05", "HARD", staffId, staffMap, date,
                    "Lịch " + typeId + " được xếp vào ngày nghỉ phép APPROVED"));
                hardCount++;
            }

            // BR-01, BR-02, BR-07: same-day same-staff conflicts
            List<Schedule> sameDaySchedules = byStaffDate.getOrDefault(staffKey, List.of());
            Set<String> seenTypes = new HashSet<>();
            for (Schedule other : sameDaySchedules) {
                if (other == s) continue;
                if (other.getId() != null && other.getId().equals(s.getId())) continue;
                String otherType = other.getShiftType().getId();
                if (otherType.equals(typeId)) {
                    // BR-07 duplicate
                    if (!seenTypes.contains(typeId)) {
                        seenTypes.add(typeId);
                        violations.add(violation("BR-07", "SOFT", staffId, staffMap, date,
                            "Trùng lịch " + typeId + " cùng ngày"));
                        softCount++;
                    }
                } else if (isShiftTypeConflict(typeId, otherType)) {
                    // BR-01 (L01↔L02) or BR-02 (L03↔L04)
                    String rule = ("L01".equals(typeId) || "L01".equals(otherType))
                        ? "BR-01" : "BR-02";
                    String desc = "Xung đột " + typeId + "↔" + otherType + " cùng ngày";
                    if (!violations.stream().anyMatch(v ->
                        v.getRuleCode().equals(rule)
                        && v.getStaffId().equals(staffId)
                        && v.getWorkDate().equals(date.toString()))) {
                        violations.add(violation(rule, "HARD", staffId, staffMap, date, desc));
                        hardCount++;
                    }
                }
            }

            // BR-04: adjacent L01 (cùng nhân sự, 2 ngày liên tiếp có L01)
            if ("L01".equals(typeId)) {
                for (int delta : new int[]{-1, 1}) {
                    LocalDate adj = date.plusDays(delta);
                    String adjKey = staffId + "|" + adj;
                    List<Schedule> adjList = byStaffDate.getOrDefault(adjKey, List.of());
                    if (adjList.stream().anyMatch(x -> "L01".equals(x.getShiftType().getId()))) {
                        violations.add(violation("BR-04", "HARD", staffId, staffMap, date,
                            "Trực 24/24 liên tiếp (" + date + " và " + adj + ")"));
                        hardCount++;
                        break;
                    }
                }
            }
        }

        // ── BR-06: max shifts per month ──
        for (var entry : byStaff.entrySet()) {
            int staffId = entry.getKey();
            int count = entry.getValue().size();
            Staff staff = staffMap.get(staffId);
            if (staff != null && staff.getMaxShiftsPerMonth() != null
                && count > staff.getMaxShiftsPerMonth()) {
                violations.add(ScheduleQualityReport.ConstraintViolation.builder()
                    .ruleCode("BR-06")
                    .severity("SOFT")
                    .staffId(staffId)
                    .staffName(staff.getFullName())
                    .workDate("MONTH")
                    .description("Vượt maxShiftsPerMonth: " + count + " > "
                                 + staff.getMaxShiftsPerMonth())
                    .build());
                softCount++;
            }
        }

        double penalty = hardCount * hardViolationPenalty
                       + softCount * softViolationPenalty;
        double score = clamp(100.0 - penalty, 0.0, 100.0);

        return ConstraintResult.builder()
            .score(score)
            .hardCount(hardCount)
            .softCount(softCount)
            .violations(violations)
            .build();
    }

    private static boolean isShiftTypeConflict(String t1, String t2) {
        // BR-01: L01 ↔ L02
        if (("L01".equals(t1) && "L02".equals(t2))
         || ("L02".equals(t1) && "L01".equals(t2))) return true;
        // BR-02: L03 ↔ L04
        if (("L03".equals(t1) && "L04".equals(t2))
         || ("L04".equals(t1) && "L03".equals(t2))) return true;
        return false;
    }

    private static ScheduleQualityReport.ConstraintViolation violation(
            String ruleCode, String severity, int staffId,
            Map<Integer, Staff> staffMap, LocalDate date, String description) {
        Staff s = staffMap.get(staffId);
        return ScheduleQualityReport.ConstraintViolation.builder()
            .ruleCode(ruleCode)
            .severity(severity)
            .staffId(staffId)
            .staffName(s != null ? s.getFullName() : "Staff#" + staffId)
            .workDate(date.toString())
            .description(description)
            .build();
    }

    // ─────────────────────────────────────────────────────────────
    // UTILITIES
    // ─────────────────────────────────────────────────────────────

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String reqKey(LocalDate date, String typeId, Integer specialtyId) {
        return typeId + "|" + date + "|" + (specialtyId != null ? specialtyId : "0");
    }

    // ─────────────────────────────────────────────────────────────
    // METADATA BUILDER
    // ─────────────────────────────────────────────────────────────

    @Getter
    @Builder
    public static class ScoringMeta {
        private final String algorithmUsed;
        private final long executionTimeMs;
        private final int optimizationRounds;

        public static ScoringMeta of(String algo, long ms) {
            return ScoringMeta.builder()
                .algorithmUsed(algo)
                .executionTimeMs(ms)
                .optimizationRounds(0)
                .build();
        }

        public static ScoringMeta of(String algo, long ms, int rounds) {
            return ScoringMeta.builder()
                .algorithmUsed(algo)
                .executionTimeMs(ms)
                .optimizationRounds(rounds)
                .build();
        }
    }
}