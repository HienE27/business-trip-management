package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes balance score from a generated schedule list using per-type CV
 * (coefficient of variation). L04 is computed per-specialty for fairness.
 */
@Slf4j
@Component
public class BalanceScoreCalculator {

    public BigDecimal calculateBalanceScore(List<Schedule> schedules, int totalStaff) {
        if (schedules.isEmpty()) return BigDecimal.ZERO;

        Map<Integer, Long> staffScheduleCount = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

        if (staffScheduleCount.size() <= 1) {
            log.debug("Balance score 0: only {} staff assigned", staffScheduleCount.size());
            return BigDecimal.valueOf(0);
        }

        // Filter active staff by L01/L02/L03 eligibility so KTV/Dược staff
        // (L04-only) don't artificially inflate variance.
        Set<Integer> lxxEligibleStaffIds = staffScheduleCount.keySet().stream()
                .filter(id -> {
                    Schedule s0 = schedules.stream().filter(s -> s.getStaff().getId().equals(id)).findFirst().orElse(null);
                    if (s0 == null) return false;
                    return StaffShiftTypeEligibility.isEligible(s0.getStaff(), ConflictDetectionService.SHIFT_TYPE_L01, null);
                })
                .collect(Collectors.toSet());

        List<String> shiftTypes = List.of(
                ConflictDetectionService.SHIFT_TYPE_L01,
                ConflictDetectionService.SHIFT_TYPE_L02,
                ConflictDetectionService.SHIFT_TYPE_L03,
                ConflictDetectionService.SHIFT_TYPE_L04);

        double totalWeightedCv = 0.0;
        int typesWithDemand = 0;

        for (String typeId : shiftTypes) {
            // L04 is specialty-bound; compute per-specialty
            if (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeId)) {
                Map<Integer, List<Schedule>> bySpecialty = schedules.stream()
                        .filter(s -> typeId.equals(s.getShiftType().getId()))
                        .collect(Collectors.groupingBy(s -> {
                            if (s.getRequirement() != null && s.getRequirement().getSpecialty() != null) {
                                return s.getRequirement().getSpecialty().getId();
                            }
                            return -1;
                        }));

                if (bySpecialty.isEmpty()) continue;
                typesWithDemand++;

                double totalWeightedCvL04 = 0.0;
                int totalEligibleL04Staff = 0;

                for (Map.Entry<Integer, List<Schedule>> entry : bySpecialty.entrySet()) {
                    List<Schedule> specSchedules = entry.getValue();
                    Set<Integer> specStaffIds = specSchedules.stream()
                            .map(s -> s.getStaff().getId())
                            .collect(Collectors.toSet());

                    int specPool = specStaffIds.size();
                    if (specPool == 0) continue;

                    Map<Integer, Long> specPerStaff = specSchedules.stream()
                            .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

                    long totalSpec = specPerStaff.values().stream().mapToLong(Long::longValue).sum();
                    double avgSpec = (double) totalSpec / specPool;

                    double sumSqSpec = specPerStaff.values().stream()
                            .mapToDouble(Long::doubleValue)
                            .map(c -> (c - avgSpec) * (c - avgSpec))
                            .sum();
                    sumSqSpec += (specPool - specPerStaff.size()) * avgSpec * avgSpec;

                    double stdDevSpec = Math.sqrt(sumSqSpec / specPool);
                    double cvSpec = avgSpec > 0 ? (stdDevSpec / avgSpec) * 100 : 0.0;

                    totalWeightedCvL04 += cvSpec * specPool;
                    totalEligibleL04Staff += specPool;
                }

                double avgCvL04 = totalEligibleL04Staff > 0 ? totalWeightedCvL04 / totalEligibleL04Staff : 0.0;
                totalWeightedCv += avgCvL04;
                continue;
            }

            int effectiveTotalStaff = Math.max(lxxEligibleStaffIds.size(), 1);
            Map<Integer, Long> perTypeCount = schedules.stream()
                    .filter(s -> typeId.equals(s.getShiftType().getId()))
                    .filter(s -> lxxEligibleStaffIds.contains(s.getStaff().getId()))
                    .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

            if (perTypeCount.isEmpty()) continue;
            typesWithDemand++;

            int staffWithType = perTypeCount.size();
            long totalType = perTypeCount.values().stream().mapToLong(Long::longValue).sum();
            double avgType = (double) totalType / effectiveTotalStaff;

            if (avgType <= 0) continue;

            double sumSq = perTypeCount.values().stream()
                    .mapToDouble(Long::doubleValue)
                    .map(c -> (c - avgType) * (c - avgType))
                    .sum();
            sumSq += (effectiveTotalStaff - staffWithType) * avgType * avgType;

            double stdDevType = Math.sqrt(sumSq / effectiveTotalStaff);
            double cvType = (stdDevType / avgType) * 100;

            totalWeightedCv += cvType;
        }

        double avgCv = typesWithDemand > 0 ? totalWeightedCv / typesWithDemand : 0;
        double score = Math.max(0, 100 - avgCv);

        if (avgCv > 30) {
            log.warn("Balance WARNING: avg per-type CV={}% > 30%", String.format("%.2f", avgCv));
        }

        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }
}
