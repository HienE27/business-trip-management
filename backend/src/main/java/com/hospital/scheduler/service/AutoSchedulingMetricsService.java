package com.hospital.scheduler.service;

import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.dto.response.AlgorithmMetricsDTO;
import com.hospital.scheduler.entity.AlgorithmMetrics;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.repository.AlgorithmMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for calculating and managing auto-scheduling metrics
 * including balance scores, algorithm execution metrics, and historical data.
 * Extracted from the monolithic AutoSchedulingService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSchedulingMetricsService {

    private final AlgorithmMetricsRepository metricsRepository;

    /**
     * Calculate balance score from a list of schedules.
     * Based on per-type coefficient of variation (CV) across staff.
     */
    public BigDecimal calculateBalanceScore(List<Schedule> schedules, int totalStaff) {
        if (schedules.isEmpty()) return BigDecimal.ZERO;

        Map<Integer, Long> staffScheduleCount = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

        if (staffScheduleCount.size() <= 1) {
            log.debug("Balance score 0: only {} staff assigned", staffScheduleCount.size());
            return BigDecimal.valueOf(0);
        }

        // CRITICAL FIX: filter active staff by L01/L02/L03 eligibility (Bac si + Dieu duong) so that
        // KTV/Duoc/Rang (only eligible for L04) are NOT included in the denominator for L01/L02/L03.
        // Otherwise CV is artificially inflated because those staff always have 0 L01/L02/L03.
        Set<Integer> lxxEligibleStaffIds = staffScheduleCount.keySet().stream()
                .filter(id -> {
                    Schedule s0 = schedules.stream().filter(s -> s.getStaff().getId().equals(id)).findFirst().orElse(null);
                    if (s0 == null) return false;
                    return StaffShiftTypeEligibility
                            .isEligible(s0.getStaff(), ConflictDetectionService.SHIFT_TYPE_L01, null);
                })
                .collect(Collectors.toSet());

        // Per-type CV: tinh rieng tung loai L01/L02/L03/L04 theo spec M07-F02 (phan bo deu tung loai).
        // CRITICAL FIX for L04: M05 spec requires L04 to be specialty-bound, so CV must be
        // computed per-specialty, not globally. All eligible staff from each specialty are
        // included in that specialty's CV (staff with 0 L04 get counted as 0 -- that's fair).
        List<String> shiftTypes = List.of(
                ConflictDetectionService.SHIFT_TYPE_L01,
                ConflictDetectionService.SHIFT_TYPE_L02,
                ConflictDetectionService.SHIFT_TYPE_L03,
                ConflictDetectionService.SHIFT_TYPE_L04);

        double totalWeightedCv = 0.0;
        int typesWithDemand = 0;

        for (String typeId : shiftTypes) {
            // For L04: compute per-specialty CV, then weighted-average across specialties.
            // Staff with no L04 demand in their specialty are NOT penalized.
            if (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeId)) {
                // Group L04 schedules by specialty
                Map<Integer, List<Schedule>> bySpecialty = schedules.stream()
                        .filter(s -> typeId.equals(s.getShiftType().getId()))
                        .collect(Collectors.groupingBy(s -> {
                            if (s.getRequirement() != null && s.getRequirement().getSpecialty() != null) {
                                return s.getRequirement().getSpecialty().getId();
                            }
                            return -1; // Unknown specialty
                        }));

                if (bySpecialty.isEmpty()) continue;
                typesWithDemand++;

                double totalWeightedCvL04 = 0.0;
                int totalEligibleL04Staff = 0;

                for (Map.Entry<Integer, List<Schedule>> entry : bySpecialty.entrySet()) {
                    int specialtyId = entry.getKey();
                    List<Schedule> specSchedules = entry.getValue();

                    // Count eligible staff in this specialty from the schedule data
                    Set<Integer> specStaffIds = specSchedules.stream()
                            .map(s -> s.getStaff().getId())
                            .collect(Collectors.toSet());

                    int specPool = specStaffIds.size();
                    if (specPool == 0) continue;

                    // Per-staff count within this specialty
                    Map<Integer, Long> specPerStaff = specSchedules.stream()
                            .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

                    long totalSpec = specPerStaff.values().stream().mapToLong(Long::longValue).sum();
                    double avgSpec = (double) totalSpec / specPool;

                    double sumSqSpec = specPerStaff.values().stream()
                            .mapToDouble(Long::doubleValue)
                            .map(c -> (c - avgSpec) * (c - avgSpec))
                            .sum();
                    // All staff in the pool had 0 initially -- add zero-variance contribution for them
                    sumSqSpec += (specPool - specPerStaff.size()) * avgSpec * avgSpec;

                    double stdDevSpec = Math.sqrt(sumSqSpec / specPool);
                    double cvSpec = avgSpec > 0 ? (stdDevSpec / avgSpec) * 100 : 0.0;

                    totalWeightedCvL04 += cvSpec * specPool;
                    totalEligibleL04Staff += specPool;
                }

                double avgCvL04 = totalEligibleL04Staff > 0 ? totalWeightedCvL04 / totalEligibleL04Staff : 0.0;

                log.info("Balance per-type L04 (per-specialty weighted): cvAvg={}% specialties={} totalEligible={}",
                        String.format("%.2f", avgCvL04), bySpecialty.size(), totalEligibleL04Staff);

                totalWeightedCv += avgCvL04;
                continue;
            }

            // L01/L02/L03: use eligibility-filtered pool size so KTV/Duoc (only L04 eligible) don't
            // inflate the variance with their guaranteed-zero count for these types.
            int effectiveTotalStaff = Math.max(lxxEligibleStaffIds.size(), 1);
            Map<Integer, Long> perTypeCount = schedules.stream()
                    .filter(s -> typeId.equals(s.getShiftType().getId()))
                    .filter(s -> lxxEligibleStaffIds.contains(s.getStaff().getId()))
                    .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

            if (perTypeCount.isEmpty()) continue;
            typesWithDemand++;

            // Pad voi 0 cho staff chua duoc phan cong loai nay
            int staffWithType = perTypeCount.size();
            long totalType = perTypeCount.values().stream().mapToLong(Long::longValue).sum();
            double avgType = (double) totalType / effectiveTotalStaff;

            if (avgType <= 0) continue;

            // Tinh variance co tinh ca staff eligible = 0 (quan trong: phat hien don ca).
            // Variance dem tu eligible staff count = 0, KHONG phai toan bo totalStaff.
            double sumSq = perTypeCount.values().stream()
                    .mapToDouble(Long::doubleValue)
                    .map(c -> (c - avgType) * (c - avgType))
                    .sum();
            sumSq += (effectiveTotalStaff - staffWithType) * avgType * avgType;

            double stdDevType = Math.sqrt(sumSq / effectiveTotalStaff);
            double cvType = (stdDevType / avgType) * 100;

            log.info("Balance per-type {}: total={} avg={} stdDev={} cv={}%",
                    typeId, totalType,
                    String.format("%.2f", avgType),
                    String.format("%.2f", stdDevType),
                    String.format("%.2f", cvType));

            totalWeightedCv += cvType;
        }

        double avgCv = typesWithDemand > 0 ? totalWeightedCv / typesWithDemand : 0;
        double score = Math.max(0, 100 - avgCv);

        // Canh bao neu phan bo lech lon -- spec M02-F05, M07-F09
        if (avgCv > 30) {
            log.warn("Balance WARNING: avg per-type CV={}% > 30% -- phan bo lech lon giua cac nhan su",
                    String.format("%.2f", avgCv));
        }

        log.info("Balance score: avgPerTypeCv={}% typesWithDemand={} totalStaff={} score={}",
                String.format("%.2f", avgCv), typesWithDemand, totalStaff, String.format("%.2f", score));

        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Save algorithm execution metrics to the database.
     */
    public void saveMetrics(SchedulePeriod period, String algorithmType, int executionTime,
                            BigDecimal coverageRate, BigDecimal balanceScore, int conflictCount, int totalSchedulesCreated) {
        AlgorithmMetrics metrics = AlgorithmMetrics.builder()
                .period(period)
                .algorithmType(algorithmType)
                .executionTimeMs(executionTime)
                .coverageRate(coverageRate)
                .balanceScore(balanceScore)
                .conflictCount(conflictCount)
                .totalSchedulesCreated(totalSchedulesCreated)
                .build();

        metricsRepository.save(metrics);
    }

    /**
     * Get all algorithm metrics for a specific period.
     */
    public List<AlgorithmMetricsDTO> getMetricsByPeriod(Integer periodId) {
        return metricsRepository.findByPeriodId(periodId).stream()
                .map(this::metricsToDTO)
                .toList();
    }

    /**
     * Get all algorithm metrics across all periods.
     */
    public List<AlgorithmMetricsDTO> getAllMetrics() {
        return metricsRepository.findAll().stream()
                .map(this::metricsToDTO)
                .toList();
    }

    /**
     * Server-paginated variant of getAllMetrics / getMetricsByPeriod,
     * used by the auto-scheduling history page's Pagination widget.
     */
    public Page<AlgorithmMetricsDTO> getMetricsPage(Integer periodId, Pageable pageable) {
        Page<AlgorithmMetrics> page = (periodId == null)
                ? metricsRepository.findAll(pageable)
                : metricsRepository.findByPeriodId(periodId, pageable);
        return page.map(this::metricsToDTO);
    }

    /**
     * Convert AlgorithmMetrics entity to DTO.
     */
    public AlgorithmMetricsDTO metricsToDTO(AlgorithmMetrics m) {
        return AlgorithmMetricsDTO.builder()
                .id(m.getId())
                .algorithmType(m.getAlgorithmType())
                .executionTimeMs(m.getExecutionTimeMs())
                .coverageRate(m.getCoverageRate())
                .balanceScore(m.getBalanceScore())
                .conflictCount(m.getConflictCount())
                .totalSchedulesCreated(m.getTotalSchedulesCreated())
                .periodId(m.getPeriod() != null ? m.getPeriod().getId() : null)
                .periodName(m.getPeriod() != null ? m.getPeriod().getPeriodName() : null)
                .createdAt(m.getCreatedAt())
                .build();
    }
}
