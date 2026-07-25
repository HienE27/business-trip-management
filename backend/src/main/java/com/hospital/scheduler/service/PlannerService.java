package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.algorithm.PlanningReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Auto Configuration Planner — phân tích dữ liệu đầu vào, tính các giới hạn
 * lý thuyết (ceiling), đánh giá tính khả thi của 3 kiểu fairness, đề xuất
 * thuật toán và tham số phù hợp.
 *
 * <p>Đây là tầng Planner đứng trước Scheduler, không thay đổi scheduler behavior.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlannerService {

    private final AlgorithmConfigService algorithmConfigService;

    /**
     * Entry point: tạo PlanningReport từ dữ liệu đầu vào.
     */
    public PlanningReport plan(
            int periodDays,
            int periodWeeks,
            Map<String, Integer> eligibleStaff,
            Map<String, Integer> targetPerStaff,
            boolean expandNonL04Eligibility,
            List<String> expandedSpecialties,
            int maxShiftsPerStaff,
            AutoGenConfig currentConfig
    ) {
        int totalStaff = Math.max(1, eligibleStaff.values().stream().mapToInt(Integer::intValue).max().orElse(1));
        int l01Elig = Math.max(1, eligibleStaff.getOrDefault("L01", 1));
        int l02Elig = Math.max(1, eligibleStaff.getOrDefault("L02", 1));
        int l03Elig = Math.max(1, eligibleStaff.getOrDefault("L03", 1));
        int l04Elig = Math.max(1, eligibleStaff.getOrDefault("L04", 1));

        // Effective L04 eligible (cross-specialty adjustment)
        int numSpecialties = Math.max(1, expandedSpecialties != null ? expandedSpecialties.size() : 1);
        boolean csEnabled = currentConfig != null && currentConfig.l04CrossSpecialty();
        int effectiveL04Elig = csEnabled
                ? l04Elig
                : Math.max(1, Math.min(l04Elig, (int) Math.ceil((double) totalStaff / numSpecialties)));

        // Targets
        int l01Target = targetPerStaff.getOrDefault("L01", 2);
        int l02Target = targetPerStaff.getOrDefault("L02", 2);
        int l03Target = targetPerStaff.getOrDefault("L03", 2);
        int l04Target = targetPerStaff.getOrDefault("L04", 5);

        // Total demand
        int totalDemand = l01Target * l01Elig + l02Target * l02Elig
                + l03Target * l03Elig + l04Target * effectiveL04Elig;

        // ── 1. Capacity Analysis ──
        int capacityPerPerson = Math.max(1, maxShiftsPerStaff > 0 ? maxShiftsPerStaff : periodDays);
        int maxCapacity = totalStaff * capacityPerPerson;
        double coverageCeiling = totalDemand > 0
                ? Math.min(100.0, (double) maxCapacity / totalDemand * 100.0)
                : 100.0;
        PlanningReport.CapacityAnalysis capacity = new PlanningReport.CapacityAnalysis(
                totalStaff, periodDays, totalDemand, maxCapacity, coverageCeiling
        );

        // ── 2. Constraint Analysis ──
        // Estimate leave density (no real leave data here — heuristic based on period length)
        double leaveDensity = Math.min(0.15, totalStaff > 0 ? 5.0 / totalStaff : 0.05);
        // L01 adjacency impact: L01 target density × window effect
        double l01DemandPerDay = (double) (l01Target * l01Elig) / periodDays;
        double l01Density = l01DemandPerDay / Math.max(1, l01Elig);
        double l01AdjacencyImpact = Math.min(1.0, l01Density * 2.0);
        // Weekly cap tightness: actual weekly demand vs max possible
        double weeklyDemand = (double) totalDemand / Math.max(1, periodWeeks);
        double weeklyCapacity = totalStaff * 7.0; // max shifts per staff per week (7 days)
        double weeklyCapTightness = weeklyCapacity > 0 ? weeklyDemand / weeklyCapacity : 0;

        double overallFeasibility = 100.0
                - Math.min(40, leaveDensity * 100 * 2.0)
                - Math.min(30, l01AdjacencyImpact * 30)
                - Math.min(30, Math.max(0, weeklyCapTightness - 0.5) * 30);
        overallFeasibility = Math.max(10, Math.min(100, overallFeasibility));

        String constraintRisk;
        if (overallFeasibility >= 80) constraintRisk = "LOW";
        else if (overallFeasibility >= 55) constraintRisk = "MEDIUM";
        else constraintRisk = "HIGH";

        PlanningReport.ConstraintAnalysis constraint = new PlanningReport.ConstraintAnalysis(
                leaveDensity, l01AdjacencyImpact, weeklyCapTightness, overallFeasibility, constraintRisk
        );

        // ── 3. Fairness Analysis (3 types) ──
        List<PlanningReport.FairnessAnalysis> fairnessOptions = new ArrayList<>();

        // 3a. Intra-type fairness
        int minPoolSize = Math.min(Math.min(l01Elig, l02Elig), l03Elig);
        double intraFeasibility = Math.min(99.0, 50.0 + minPoolSize * 5.0);
        double intraExpected = Math.min(99.0, 70.0 + minPoolSize * 3.0);
        int intraStars = intraFeasibility >= 90 ? 5 : intraFeasibility >= 75 ? 4 : intraFeasibility >= 55 ? 3 : 2;
        fairnessOptions.add(new PlanningReport.FairnessAnalysis(
                "INTRA_TYPE", "Intra-type fairness",
                intraFeasibility, intraExpected, 0.0, "LOW",
                "Chia đều từng loại ca (L01/L02/L03/L04) trong nhóm đủ điều kiện. Phù hợp với mọi demand.",
                intraStars
        ));

        // 3b. Inter-type balance
        double l01Ratio = l01Elig > 0 ? (double) l01Target / l01Elig : 0;
        double l02Ratio = l02Elig > 0 ? (double) l02Target / l02Elig : 0;
        double l03Ratio = l03Elig > 0 ? (double) l03Target / l03Elig : 0;
        double maxRatio = Math.max(Math.max(l01Ratio, l02Ratio), l03Ratio);
        double minRatio = Math.min(Math.min(l01Ratio, l02Ratio), l03Ratio);
        double ratioSimilarity = maxRatio > 0 && minRatio > 0 ? minRatio / maxRatio : 0;

        double interFeasibility = 99.0 * ratioSimilarity;
        // Coverage impact: when forcing inter-type balance, coverage may drop
        double interCoverageImpact = (1.0 - ratioSimilarity) * 20.0;
        double interExpected = Math.min(99.0, 50.0 + ratioSimilarity * 40.0);
        String interRisk = interCoverageImpact > 15 ? "MEDIUM" : interCoverageImpact > 8 ? "LOW" : "LOW";
        int interStars = interFeasibility >= 80 ? 4 : interFeasibility >= 55 ? 3 : interFeasibility >= 30 ? 2 : 1;
        fairnessOptions.add(new PlanningReport.FairnessAnalysis(
                "INTER_TYPE", "Inter-type balance",
                interFeasibility, interExpected, -interCoverageImpact, interRisk,
                "Cân bằng L01/L02/L03 trên cùng nhân sự. " + (ratioSimilarity > 0.6
                        ? "Demand các loại ca tương đối đồng đều → khả thi."
                        : "Demand lệch → có thể giảm coverage."),
                interStars
        ));

        // 3c. Cross-specialty balance (L04)
        int l04SpecCount = numSpecialties;
        double csFeasibility;
        String csDesc;
        if (csEnabled) {
            csFeasibility = Math.min(95.0, 50.0 + l04SpecCount * 5.0 + effectiveL04Elig * 2.0);
            csDesc = "Cho phép L04 chéo chuyên khoa. "
                    + (l04SpecCount >= 5 ? "Nhiều chuyên khoa → phân bổ linh hoạt." : "Ít chuyên khoa → cần cross-support.");
        } else {
            csFeasibility = Math.min(90.0, 30.0 + effectiveL04Elig * 4.0);
            csDesc = "L04 chỉ theo đúng chuyên khoa. "
                    + (effectiveL04Elig >= 5 ? "Đủ nhân sự mỗi khoa." : "Một số khoa có thể thiếu người.");
        }
        double csCoverageGain = csEnabled ? Math.min(15.0, (l04SpecCount > 1 ? 5.0 : 0.0) + (1.0 - effectiveL04Elig / (double) l04Elig) * 20) : 5.0;
        int csStars = csFeasibility >= 80 ? 4 : csFeasibility >= 55 ? 3 : csFeasibility >= 30 ? 2 : 1;
        fairnessOptions.add(new PlanningReport.FairnessAnalysis(
                "CROSS_SPECIALTY", "Cross-specialty balance",
                csFeasibility, Math.min(95.0, 50.0 + csFeasibility * 0.4), csCoverageGain,
                "LOW",
                csDesc,
                csStars
        ));

        // ── 4. Algorithm Recommendation ──
        String algorithm;
        String algoRationale;
        List<String> alternatives = new ArrayList<>();

        double coverageGap = totalDemand > 0 ? (1.0 - (double) maxCapacity / totalDemand) * 100 : 0;
        double fairnessNeed = Math.max(intraExpected, Math.max(interExpected, csFeasibility));

        if (overallFeasibility < 60) {
            algorithm = "CP_SAT";
            algoRationale = "Constraint density cao (" + (int) overallFeasibility + "% feasible) → cần CP-SAT (OR-Tools) để tìm lời giải khả thi.";
            alternatives.add("BEAM_SEARCH");
        } else if (coverageGap > 20) {
            algorithm = "BEAM_SEARCH";
            algoRationale = "Coverage gap " + String.format("%.0f", coverageGap) + "% → cần Beam Search tìm kiếm toàn cục.";
            alternatives.add("SIMULATED_ANNEALING");
            alternatives.add("ENHANCED_GREEDY");
        } else if (fairnessNeed > 90) {
            algorithm = "SIMULATED_ANNEALING";
            algoRationale = "Yêu cầu fairness cao (" + String.format("%.0f", fairnessNeed) + "%) → Simulated Annealing cho hội tụ toàn cục.";
            alternatives.add("BEAM_SEARCH");
        } else if (totalStaff < 50) {
            algorithm = "ENHANCED_GREEDY";
            algoRationale = "Quy mô nhỏ (" + totalStaff + " staff) → Enhanced Greedy đủ nhanh và tốt.";
            alternatives.add("BEAM_SEARCH");
        } else {
            algorithm = "BEAM_SEARCH";
            algoRationale = "Cân bằng giữa tốc độ và chất lượng cho " + totalStaff + " nhân sự.";
            alternatives.add("ENHANCED_GREEDY");
            alternatives.add("SIMULATED_ANNEALING");
        }

        PlanningReport.AlgorithmRecommendation algoRec = new PlanningReport.AlgorithmRecommendation(
                algorithm, algoRationale, alternatives
        );

        // ── 5. Parameter Recommendation ──
        int recBeamWidth = totalStaff <= 30 ? 20 : totalStaff <= 100 ? 50 : 80;
        int recRebalance = fairnessNeed > 85 ? 50 : fairnessNeed > 70 ? 30 : 15;
        double recWeekendWeight = overallFeasibility > 85 ? 1.5 : 2.5;
        double recCovWeight = coverageGap > 15 ? 0.45 : 0.40;
        double recFairWeight = fairnessNeed > 85 ? 0.40 : 0.35;
        double recConWeight = overallFeasibility < 70 ? 0.30 : 0.25;
        // Normalize weights to sum to 1.0
        double wSum = recCovWeight + recFairWeight + recConWeight;
        recCovWeight /= wSum;
        recFairWeight /= wSum;
        recConWeight /= wSum;

        // Arrangement mode: pick best fairness type
        String arrangementMode = "INTRA_TYPE";
        if (interFeasibility >= 65 && intraFeasibility >= 60) {
            arrangementMode = "WITH_INTER_BALANCE";
        }

        // Build param relevance map per algorithm
        java.util.Map<String, Boolean> relevance = new java.util.LinkedHashMap<>();
        boolean isBeamSearch = "BEAM_SEARCH".equals(algorithm);
        boolean isEnhancedGreedy = "ENHANCED_GREEDY".equals(algorithm);
        boolean isSimAnneal = "SIMULATED_ANNEALING".equals(algorithm);
        boolean isCpSat = "CP_SAT".equals(algorithm);
        boolean isGreedy = "GREEDY".equals(algorithm); // fallback
        relevance.put("beamWidth", isBeamSearch || isSimAnneal);
        relevance.put("weekendWeight", isEnhancedGreedy || isGreedy);
        relevance.put("arrangementMode", isEnhancedGreedy || isBeamSearch || isSimAnneal);
        relevance.put("rebalanceRounds", !isCpSat && !isGreedy);
        relevance.put("scorerWeights", true); // all algorithms use the scorer
        relevance.put("maxShiftsPerStaff", true); // universal cap

        PlanningReport.ParameterRecommendation params = new PlanningReport.ParameterRecommendation(
                recBeamWidth, recRebalance, recWeekendWeight,
                Math.round(recCovWeight * 100.0) / 100.0,
                Math.round(recFairWeight * 100.0) / 100.0,
                Math.round(recConWeight * 100.0) / 100.0,
                Math.max(totalDemand / Math.max(1, totalStaff), Math.min(60, (int) Math.ceil((double) totalDemand / totalStaff))),
                arrangementMode,
                relevance
        );

        // ── 6. Expected Result ──
        double expCoverage = Math.min(coverageCeiling, 95.0);
        double expConstraint = Math.min(overallFeasibility, 98.0);
        double expFairness = Math.min(95.0, intraExpected * (arrangementMode.equals("WITH_INTER_BALANCE") ? 0.95 : 1.0));
        double expQuality = 0.40 * expCoverage + 0.35 * expFairness + 0.25 * expConstraint;

        PlanningReport.ExpectedResult expected = new PlanningReport.ExpectedResult(
                Math.round(expCoverage * 10.0) / 10.0,
                Math.round(expConstraint * 10.0) / 10.0,
                Math.round(expFairness * 10.0) / 10.0,
                Math.round(expQuality * 10.0) / 10.0
        );

        // ── 7. Warnings ──
        List<String> warningsList = new ArrayList<>();
        if (coverageCeiling < 80) {
            warningsList.add("Coverage tối đa chỉ " + String.format("%.0f", coverageCeiling) + "% — demand vượt capacity. Cần giảm target hoặc tăng maxShiftsPerStaff.");
        }
        if (overallFeasibility < 60) {
            warningsList.add("Ràng buộc chặt (" + String.format("%.0f", overallFeasibility) + "% feasible) — khó tìm lịch hợp lệ.");
        }
        if (interFeasibility < 40) {
            warningsList.add("Inter-type balance khả thi thấp (" + String.format("%.0f", interFeasibility) + "%) — demand lệch giữa các loại ca.");
        }
        if (effectiveL04Elig < l04Elig && l04Elig > 1) {
            warningsList.add("L04 chỉ " + effectiveL04Elig + "/" + l04Elig + " người/khoa — " + (csEnabled ? "cross-specialty đang bù." : "cân nhắc bật cross-specialty."));
        }

        return new PlanningReport(
                capacity, constraint, fairnessOptions, algoRec, params, expected, warningsList
        );
    }
}
