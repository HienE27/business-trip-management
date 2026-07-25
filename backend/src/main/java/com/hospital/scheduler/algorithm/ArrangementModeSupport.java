package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.service.AlgorithmConfigService.AlgorithmRuntimeConfig;

import java.util.Map;

/**
 * Soft inter-type balance helpers for arrangementMode.
 * Contract: docs/ARRANGEMENT_MODE_CONTRACT.md
 */
public final class ArrangementModeSupport {

    public static final double DEFAULT_INTER_WEIGHT = 5.0;
    /** Scale for 0–1 objective scores (SA/HC/Beam). */
    public static final double OBJECTIVE_INTER_SCALE = 0.02;

    private ArrangementModeSupport() {}

    public static boolean interEnabled(AlgorithmRuntimeConfig cfg) {
        return cfg != null && "WITH_INTER_BALANCE".equals(cfg.getArrangementMode());
    }

    public static boolean interEnabled(String arrangementMode) {
        return "WITH_INTER_BALANCE".equals(arrangementMode);
    }

    /**
     * Soft penalty = weight × (max(L01,L02,L03) − min(L01,L02,L03)).
     * Missing keys count as 0.
     */
    public static double interTypePenalty(Map<String, Integer> typeCounts, double weight) {
        if (weight <= 0 || typeCounts == null || typeCounts.isEmpty()) return 0;
        int l01 = typeCounts.getOrDefault("L01", 0);
        int l02 = typeCounts.getOrDefault("L02", 0);
        int l03 = typeCounts.getOrDefault("L03", 0);
        int span = Math.max(Math.max(l01, l02), l03) - Math.min(Math.min(l01, l02), l03);
        return weight * span;
    }

    public static double interTypePenalty(Map<String, Integer> typeCounts) {
        return interTypePenalty(typeCounts, DEFAULT_INTER_WEIGHT);
    }

    /**
     * Mean per-staff L01–L03 span across a full schedule (for objective scores).
     */
    public static double meanInterSpan(Map<Integer, Map<String, Integer>> typeCountByStaff) {
        if (typeCountByStaff == null || typeCountByStaff.isEmpty()) return 0;
        double sum = 0;
        int n = 0;
        for (Map<String, Integer> counts : typeCountByStaff.values()) {
            int l01 = counts.getOrDefault("L01", 0);
            int l02 = counts.getOrDefault("L02", 0);
            int l03 = counts.getOrDefault("L03", 0);
            sum += Math.max(Math.max(l01, l02), l03) - Math.min(Math.min(l01, l02), l03);
            n++;
        }
        return n > 0 ? sum / n : 0;
    }

    /** Build staffId → {L01,L02,L03 counts} from schedules (L04 ignored). */
    public static Map<Integer, Map<String, Integer>> typeCountsFromSchedules(
            Iterable<? extends com.hospital.scheduler.entity.Schedule> schedules) {
        Map<Integer, Map<String, Integer>> out = new java.util.HashMap<>();
        if (schedules == null) return out;
        for (var s : schedules) {
            if (s.getStaff() == null || s.getShiftType() == null) continue;
            String tid = s.getShiftType().getId();
            if (!"L01".equals(tid) && !"L02".equals(tid) && !"L03".equals(tid)) continue;
            out.computeIfAbsent(s.getStaff().getId(), k -> new java.util.HashMap<>())
                    .merge(tid, 1, Integer::sum);
        }
        return out;
    }
}
