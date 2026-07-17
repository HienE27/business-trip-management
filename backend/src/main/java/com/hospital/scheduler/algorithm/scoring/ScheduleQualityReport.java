package com.hospital.scheduler.algorithm.scoring;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Báo cáo chất lượng lịch sau khi chạy thuật toán.
 *
 * <p>Cấu trúc score (0.0 – 100.0):
 * <pre>
 *   totalScore = {@value #DEFAULT_COVERAGE_WEIGHT} * coverageScore
 *              + {@value #DEFAULT_FAIRNESS_WEIGHT} * fairnessScore
 *              + {@value #DEFAULT_CONSTRAINT_WEIGHT} * constraintScore
 * </pre>
 *
 * <p>Weights có thể điều chỉnh qua config nhưng mặc định ưu tiên:
 * Coverage > Fairness > Constraint-compliance (constraint chỉ là penalty, không bao giờ bị bỏ qua).
 *
 * <p>Default values are declared as public constants in
 * {@link ScheduleQualityScorer} to prevent drift.
 */
@Getter
@Builder
public class ScheduleQualityReport {

    // ─────────────────────────────────────────────
    // Re-exported defaults — kept here so callers can read them
    // without a compile-time dependency on ScheduleQualityScorer.
    // ─────────────────────────────────────────────

    /** {@value com.hospital.scheduler.algorithm.scoring.ScheduleQualityScorer#DEFAULT_COVERAGE_WEIGHT} */
    public static final double DEFAULT_COVERAGE_WEIGHT =
            ScheduleQualityScorer.DEFAULT_COVERAGE_WEIGHT;

    /** {@value com.hospital.scheduler.algorithm.scoring.ScheduleQualityScorer#DEFAULT_FAIRNESS_WEIGHT} */
    public static final double DEFAULT_FAIRNESS_WEIGHT =
            ScheduleQualityScorer.DEFAULT_FAIRNESS_WEIGHT;

    /** {@value com.hospital.scheduler.algorithm.scoring.ScheduleQualityScorer#DEFAULT_CONSTRAINT_WEIGHT} */
    public static final double DEFAULT_CONSTRAINT_WEIGHT =
            ScheduleQualityScorer.DEFAULT_CONSTRAINT_WEIGHT;

    // ─────────────────────────────────────────────
    // 1. TỔNG QUAN
    // ─────────────────────────────────────────────

    /** Score tổng hợp [0-100]. Đạt yêu cầu khi >= 80.0. */
    private final double totalScore;

    /** Đánh giá dạng chữ: EXCELLENT / GOOD / ACCEPTABLE / POOR */
    private final String grade;

    /** true khi totalScore >= threshold (mặc định 80.0). */
    private final boolean passed;

    // ─────────────────────────────────────────────
    // 2. COVERAGE SCORE  (weight 0.40)
    // ─────────────────────────────────────────────

    /**
     * Tỉ lệ phủ tổng thể [0-100].
     * Coverage = assignedSlots / requiredSlots * 100
     */
    private final double coverageScore;

    /** Tổng slot yêu cầu (Σ requiredStaffCount). */
    private final int totalRequired;

    /** Tổng slot đã phân công. */
    private final int totalAssigned;

    /** Số slot còn thiếu = totalRequired - totalAssigned. */
    private final int totalShortfall;

    /**
     * Coverage theo từng loại ca [L01, L02, L03, L04].
     * key = shiftTypeId, value = CoverageDetail
     */
    private final Map<String, CoverageDetail> coverageByType;

    // ─────────────────────────────────────────────
    // 3. FAIRNESS SCORE  (weight 0.35)
    // ─────────────────────────────────────────────

    /**
     * Eligible Group Fairness [0-100] — KPI chính đánh giá mức độ công bằng
     * trong các nhóm eligibility có thể phân công (pool_size ≥ 2).
     * Không bị ảnh hưởng bởi các nhóm trivial (1 người) như Mắt.
     * Giữ tên fairnessScore để tương thích ngược.
     */
    private final double fairnessScore;

    /**
     * Global Fairness [0-100] — chỉ số vận hành, phản ánh thực trạng toàn viện.
     * Chịu ảnh hưởng của mất cân bằng cấu trúc nhân sự (vd: Mắt 1 BS / 35 ca).
     * Chỉ để tham khảo, không dùng đánh giá thuật toán.
     */
    private final double globalFairnessScore;

    /**
     * Structural Load: danh sách chuyên khoa bị quá tải cấu trúc.
     * Ví dụ: "Mắt: 31 L04 / 1 BS → structural overload"
     * Metric này không phản ánh chất lượng thuật toán,
     * mà báo cho quản lý về vấn đề nhân sự.
     */
    private final List<String> structuralLoadWarnings;

    /**
     * Fairness theo từng loại ca.
     * key = shiftTypeId | "L04:specialtyId"
     */
    private final Map<String, FairnessDetail> fairnessByType;

    /**
     * Phân bổ số ca theo từng nhân sự (tổng tất cả loại).
     * key = staffId, value = số ca
     */
    private final Map<Integer, Integer> totalShiftsByStaff;

    /**
     * Phân bổ theo nhân sự + loại ca.
     * key = staffId, value = map(shiftTypeId → count)
     */
    private final Map<Integer, Map<String, Integer>> shiftsByStaffAndType;

    /** Độ lệch tối đa giữa nhân sự có nhiều ca nhất và ít ca nhất. */
    private final int maxDeviationTotal;

    /** Nhân sự có nhiều ca nhất (staffId). */
    private final Integer maxLoadStaffId;

    /** Nhân sự có ít ca nhất (staffId, không tính người nghỉ phép). */
    private final Integer minLoadStaffId;

    // ─────────────────────────────────────────────
    // 4. CONSTRAINT SCORE  (weight 0.25)
    // ─────────────────────────────────────────────

    /**
     * Score tuân thủ ràng buộc [0-100].
     * constraintScore = max(0, 100 - violationCount * penaltyPerViolation)
     */
    private final double constraintScore;

    /** Tổng số vi phạm nghiêm trọng (hard constraints). */
    private final int hardViolationCount;

    /** Tổng số vi phạm mềm (soft constraints). */
    private final int softViolationCount;

    /** Chi tiết từng vi phạm. */
    private final List<ConstraintViolation> violations;

    // ─────────────────────────────────────────────
    // 5. META
    // ─────────────────────────────────────────────

    /** Tên thuật toán đã chạy. */
    private final String algorithmUsed;

    /** Thời gian chạy (ms). */
    private final long executionTimeMs;

    /** Số vòng tối ưu hóa đã thực hiện (Local Search). */
    private final int optimizationRounds;

    // ─────────────────────────────────────────────
    // NESTED: CoverageDetail
    // ─────────────────────────────────────────────

    @Getter
    @Builder
    public static class CoverageDetail {
        private final String shiftTypeId;
        private final String specialtyName;   // null nếu không có specialty
        private final int required;
        private final int assigned;
        private final int shortfall;
        /** [0-100] */
        private final double coveragePct;
    }

    // ─────────────────────────────────────────────
    // NESTED: FairnessDetail
    // ─────────────────────────────────────────────

    @Getter
    @Builder
    public static class FairnessDetail {
        private final String shiftTypeId;
        private final String specialtyName;
        /** Số nhân sự trong pool của nhóm này (dùng để phát hiện quá tải cấu trúc). */
        private final int poolSize;
        /** Số ca trung bình mỗi nhân sự. */
        private final double mean;
        /** Độ lệch chuẩn. */
        private final double stdDev;
        /**
         * Coefficient of Variation = stdDev / mean.
         * CV < 0.10 → rất tốt
         * CV < 0.20 → chấp nhận được
         * CV >= 0.20 → kém
         */
        private final double cv;
        /** [0-100]: max(0, 100 - cv*100) */
        private final double fairnessPct;
        /** Max số ca (nhân sự nhiều nhất). */
        private final int maxCount;
        /** Min số ca (nhân sự ít nhất). */
        private final int minCount;
        /** Độ lệch tuyệt đối max - min. */
        private final int maxDeviation;
    }

    // ─────────────────────────────────────────────
    // NESTED: ConstraintViolation
    // ─────────────────────────────────────────────

    @Getter
    @Builder
    public static class ConstraintViolation {
        /** BR-01, BR-02, BR-03, ... */
        private final String ruleCode;
        /** HARD hoặc SOFT. */
        private final String severity;
        private final Integer staffId;
        private final String staffName;
        private final String workDate;
        private final String description;
    }

    // ─────────────────────────────────────────────
    // HELPER METHODS
    // ─────────────────────────────────────────────

    /**
     * Tóm tắt dạng text cho log.
     */
    public String summary() {
        return String.format(
            "[%s] totalScore=%.1f (coverage=%.1f, global=%.1f, eligible=%.1f, constraint=%.1f) " +
            "assigned=%d/%d violations=%d%s",
            grade, totalScore,
            coverageScore, globalFairnessScore, fairnessScore, constraintScore,
            totalAssigned, totalRequired,
            hardViolationCount + softViolationCount,
            structuralLoadWarnings != null && !structuralLoadWarnings.isEmpty()
                ? " | " + String.join("; ", structuralLoadWarnings)
                : ""
        );
    }

    /** Tính grade từ totalScore. */
    public static String gradeOf(double score) {
        if (score >= 95) return "EXCELLENT";
        if (score >= 80) return "GOOD";
        if (score >= 65) return "ACCEPTABLE";
        return "POOR";
    }
}
