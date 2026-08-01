package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Balance Analytics Service — decomposes the scheduler's balance score into
 * per-pool (shiftType, specialty) contributions and generates actionable
 * recommendations.
 *
 * <p>Purpose: when {@code balanceScore} looks low (e.g. 55.02), Manager/QA need
 * to know <b>which pool</b> caused the drop. Without this breakdown the single
 * number is opaque.
 *
 * <p>Algorithm mirrors {@code ScheduleQualityScorer.computeFairness()} so the
 * numbers reported here stay consistent with the score returned by the
 * auto-schedule endpoint.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BalanceAnalyticsService {

    private final ScheduleRepository scheduleRepository;
    private final StaffRepository staffRepository;

    /** CV threshold (matches ScheduleQualityScorer default targetCv). */
    private static final double TARGET_CV = 0.10;
    private static final double WORST_CV = 0.50;

    /**
     * Build the full balance analytics breakdown for a period.
     *
     * @param periodId schedule period id
     * @return analytics payload
     */
    public Map<String, Object> buildBreakdown(Integer periodId) {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);
        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();

        if (activeStaff.isEmpty() || schedules.isEmpty()) {
            return Map.of(
                    "periodId", periodId,
                    "overall", Map.of("score", 0.0, "cv", 0.0, "totalSchedules", schedules.size()),
                    "pools", List.of(),
                    "message", "No schedules or active staff in period"
            );
        }

        // 1. Eligibility pools (same source as ScheduleQualityScorer)
        Set<Integer> nonL04Eligible = StaffShiftTypeEligibility.eligibleStaffIdsForNonL04(activeStaff);
        Map<Integer, Set<Integer>> l04BySpec = StaffShiftTypeEligibility.getL04EligibilityBySpecialty(activeStaff);

        // 2. Group schedules by (shiftType, [specialty])
        Map<String, Map<Integer, Integer>> countsByTypeAndStaff = new HashMap<>();
        Map<String, String> typeKeySpecialtyName = new HashMap<>(); // for response

        for (Schedule s : schedules) {
            String typeId = s.getShiftType().getId();
            String specialtyKey = "";
            String specialtyName = null;
            if (s.getRequirement() != null && s.getRequirement().getSpecialty() != null) {
                specialtyKey = ":" + s.getRequirement().getSpecialty().getId();
                specialtyName = s.getRequirement().getSpecialty().getName();
            }
            String typeKey = typeId + specialtyKey;
            typeKeySpecialtyName.putIfAbsent(typeKey, specialtyName);

            int staffId = s.getStaff().getId();
            countsByTypeAndStaff
                    .computeIfAbsent(typeKey, k -> new HashMap<>())
                    .merge(staffId, 1, Integer::sum);
        }

        // 3. Build pool rows with CV/fairness/contribution
        List<Map<String, Object>> pools = new ArrayList<>();
        double totalCvWeighted = 0.0;
        int weightSum = 0;

        for (Map.Entry<String, Map<Integer, Integer>> entry : countsByTypeAndStaff.entrySet()) {
            String typeKey = entry.getKey();
            Map<Integer, Integer> perStaff = entry.getValue();
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

            // Variance includes zero-count staff in pool (correct fairness measure).
            // Note: perStaff may contain staff OUTSIDE pool (cross-specialty fallback),
            // so we only iterate pool for variance — matches ScheduleQualityScorer semantics.
            double sumSq = 0.0;
            int maxCount = 0, minCount = Integer.MAX_VALUE;
            int maxStaffId = -1, minStaffId = -1;
            for (Integer sid : pool) {
                int c = perStaff.getOrDefault(sid, 0);
                double diff = c - mean;
                sumSq += diff * diff;
                if (c > maxCount) { maxCount = c; maxStaffId = sid; }
                if (c < minCount) { minCount = c; minStaffId = sid; }
            }
            if (minCount == Integer.MAX_VALUE) minCount = 0;

            // Also report true max/min over perStaff dataset.
            int actualMaxCount = perStaff.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int actualMinCount = perStaff.values().stream().mapToInt(Integer::intValue).min().orElse(0);

            double variance = sumSq / poolSize;
            double stdDev = Math.sqrt(variance);
            double cv = mean > 0 ? stdDev / mean : 0.0;

            // piecewise linear (matches ScheduleQualityScorer)
            double fairnessPct;
            if (cv <= TARGET_CV) fairnessPct = 100.0;
            else if (cv >= WORST_CV) fairnessPct = 0.0;
            else fairnessPct = 100.0 * (1.0 - (cv - TARGET_CV) / (WORST_CV - TARGET_CV));

            int weight = Math.max(1, (int) totalForType);
            double contribution = cv * weight;
            totalCvWeighted += contribution;
            weightSum += weight;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("typeKey", typeKey);
            row.put("shiftTypeId", typeId);
            row.put("specialtyName", typeKeySpecialtyName.get(typeKey));
            row.put("poolSize", poolSize);
            row.put("actualStaffCount", perStaff.size());
            row.put("totalAssignments", totalForType);
            row.put("mean", round(mean));
            row.put("stdDev", round(stdDev));
            row.put("cv", round(cv));
            row.put("fairnessPct", round(fairnessPct));
            row.put("maxCount", maxCount);
            row.put("minCount", minCount);
            row.put("actualMaxCount", actualMaxCount);
            row.put("actualMinCount", actualMinCount);
            row.put("maxDeviation", maxCount - minCount);
            row.put("maxStaffId", maxStaffId > 0 ? maxStaffId : null);
            row.put("minStaffId", minStaffId > 0 ? minStaffId : null);
            row.put("weight", weight);
            row.put("contributionToOverall", round(contribution));
            pools.add(row);
        }

        double overallCv = weightSum > 0 ? totalCvWeighted / weightSum : 0.0;
        double overallScore;
        if (overallCv <= TARGET_CV) overallScore = 100.0;
        else if (overallCv >= WORST_CV) overallScore = 0.0;
        else overallScore = 100.0 * (1.0 - (overallCv - TARGET_CV) / (WORST_CV - TARGET_CV));

        // 4. Top 5 best / worst pools by fairnessPct
        List<Map<String, Object>> sortedByFairness = new ArrayList<>(pools);
        sortedByFairness.sort((a, b) -> Double.compare(
                ((Number) b.get("fairnessPct")).doubleValue(),
                ((Number) a.get("fairnessPct")).doubleValue()));
        List<Map<String, Object>> top5Best = sortedByFairness.stream().limit(5).toList();
        List<Map<String, Object>> top5Worst = sortedByFairness.stream()
                .skip(Math.max(0, sortedByFairness.size() - 5))
                .toList();

        // 5. Most overloaded / underloaded staff (by totalShifts)
        Map<Integer, Integer> totalShiftsByStaff = new HashMap<>();
        for (Schedule s : schedules) {
            totalShiftsByStaff.merge(s.getStaff().getId(), 1, Integer::sum);
        }
        double avgShifts = totalShiftsByStaff.values().stream()
                .mapToInt(Integer::intValue).average().orElse(0);
        double maxShifts = totalShiftsByStaff.values().stream()
                .mapToInt(Integer::intValue).max().orElse(0);

        List<Map<String, Object>> overloaded = new ArrayList<>();
        List<Map<String, Object>> underloaded = new ArrayList<>();
        Map<Integer, String> staffNames = new HashMap<>();
        for (Staff st : activeStaff) staffNames.put(st.getId(), st.getFullName());

        for (Map.Entry<Integer, Integer> e : totalShiftsByStaff.entrySet()) {
            int sid = e.getKey();
            int count = e.getValue();
            double deviation = avgShifts > 0 ? ((count - avgShifts) * 100.0 / avgShifts) : 0;
            Map<String, Object> row = Map.of(
                    "staffId", sid,
                    "staffName", staffNames.getOrDefault(sid, "Unknown"),
                    "totalShifts", count,
                    "deviationPct", round(deviation));
            if (count == maxShifts) overloaded.add(row);
            if (count <= avgShifts * 0.85) underloaded.add(row);
        }
        overloaded.sort((a, b) -> Integer.compare(
                ((Number) b.get("totalShifts")).intValue(),
                ((Number) a.get("totalShifts")).intValue()));
        underloaded.sort((a, b) -> Integer.compare(
                ((Number) a.get("totalShifts")).intValue(),
                ((Number) b.get("totalShifts")).intValue()));

        // 6. Recommendations
        List<Map<String, Object>> recommendations = generateRecommendations(pools, activeStaff,
                overloaded, underloaded, l04BySpec);

        Map<String, Object> overall = new LinkedHashMap<>();
        overall.put("score", round(overallScore));
        overall.put("cv", round(overallCv));
        overall.put("targetCv", TARGET_CV);
        overall.put("worstCv", WORST_CV);
        overall.put("totalSchedules", schedules.size());
        overall.put("totalActiveStaff", activeStaff.size());
        overall.put("poolCount", pools.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodId", periodId);
        result.put("overall", overall);
        result.put("pools", pools);
        result.put("top5BestPools", top5Best);
        result.put("top5WorstPools", top5Worst);
        result.put("mostOverloadedStaff", overloaded.stream().limit(5).toList());
        result.put("mostUnderloadedStaff", underloaded.stream().limit(5).toList());
        result.put("recommendations", recommendations);
        return result;
    }

    private List<Map<String, Object>> generateRecommendations(
            List<Map<String, Object>> pools,
            List<Staff> activeStaff,
            List<Map<String, Object>> overloaded,
            List<Map<String, Object>> underloaded,
            Map<Integer, Set<Integer>> l04BySpec) {

        List<Map<String, Object>> recs = new ArrayList<>();

        // Rec 1: Pools with small poolSize + high CV
        for (Map<String, Object> p : pools) {
            int poolSize = ((Number) p.get("poolSize")).intValue();
            double cv = ((Number) p.get("cv")).doubleValue();
            if (poolSize <= 3 && cv > 0.20) {
                String specName = (String) p.get("specialtyName");
                String typeId = (String) p.get("shiftTypeId");
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("severity", cv > 0.40 ? "high" : "medium");
                rec.put("pool", String.format("%s%s", typeId,
                        specName != null ? " - " + specName : ""));
                rec.put("issue", String.format("Pool size chỉ %d staff, CV=%.0f%% → fairness kém",
                        poolSize, cv * 100));
                rec.put("suggestions", List.of(
                        "Tăng cross-specialty ratio cho " + typeId,
                        "Bổ sung thêm nhân sự cho " + (specName != null ? specName : "loại ca này"),
                        "Giảm requirement " + typeId + " để giảm tải"));
                recs.add(rec);
            }
        }

        // Rec 2: Overloaded staff with >15% deviation
        for (Map<String, Object> o : overloaded) {
            double dev = ((Number) o.get("deviationPct")).doubleValue();
            if (dev > 15) {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("severity", "medium");
                rec.put("pool", "Staff: " + o.get("staffName"));
                rec.put("issue", String.format("Quá tải +%.0f%% so với trung bình", dev));
                rec.put("suggestions", List.of(
                        "Giảm ca cho " + o.get("staffName"),
                        "Phân bổ lại ca cho staff đang dưới tải"));
                recs.add(rec);
            }
        }

        // Rec 3: Underloaded staff with <-15% deviation
        for (Map<String, Object> u : underloaded) {
            double dev = ((Number) u.get("deviationPct")).doubleValue();
            if (dev < -15) {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("severity", "low");
                rec.put("pool", "Staff: " + u.get("staffName"));
                rec.put("issue", String.format("Dưới tải %.0f%% so với trung bình", dev));
                rec.put("suggestions", List.of(
                        "Tăng ca cho " + u.get("staffName"),
                        "Phân bổ lại ca từ staff quá tải"));
                recs.add(rec);
            }
        }

        // Rec 4: Pools with high CV due to fallback (cross-specialty)
        long crossSpecPools = pools.stream()
                .filter(p -> {
                    String tk = (String) p.get("typeKey");
                    return tk.startsWith("L04:") &&
                            ((Number) p.get("cv")).doubleValue() > 0.30;
                })
                .count();
        if (crossSpecPools > 0) {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("severity", "info");
            rec.put("pool", "L04 (cross-specialty)");
            rec.put("issue", crossSpecPools + " L04-specialty pool có CV cao do fallback từ chuyên khoa khác");
            rec.put("suggestions", List.of(
                    "Kiểm tra requirement L04 có chính xác theo specialty không",
                    "Bổ sung staff đúng chuyên khoa để giảm fallback"));
            recs.add(rec);
        }

        return recs;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
