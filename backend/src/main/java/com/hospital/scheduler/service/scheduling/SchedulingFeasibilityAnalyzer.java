package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service kiểm tra tính khả thi của kỳ lịch trước khi chạy auto-scheduling.
 *
 * <p>Báo cáo chi tiết:
 * <ul>
 *   <li>Tỷ lệ eligible/required cho mỗi ngày và loại lịch</li>
 *   <li>Cảnh báo khi requirement không khả thi</li>
 *   <li>Gợi ý cấu hình cross-specialty</li>
 *   <li>Thống kê staff availability</li>
 * </ul>
 *
 * <p>Eligibility được tính đúng theo nghiệp vụ:
 * <ul>
 *   <li>L01/L02/L03: tất cả staff active ∈ eligible specialties</li>
 *   <li>L04: staff active ∈ eligible specialties + khớp requiredSpecialty
 *       HOẶC trong danh sách cross-specialty</li>
 *   <li>Loại trừ: staff đang nghỉ phép, đang nghỉ bù, holiday</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulingFeasibilityAnalyzer {

    private final StaffRepository staffRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final HolidayRepository holidayRepository;
    private final AlgorithmConfigService algorithmConfigService;

    /** Kết quả phân tích tính khả thi */
    @Builder
    public record FeasibilityReport(
            boolean feasible,
            int totalDays,
            int feasibleDays,
            int understaffedDays,
            double coverageRate,
            List<DayAnalysis> dailyAnalysis,
            Map<String, StaffAvailabilitySummary> availabilityByShiftType,
            List<String> warnings,
            List<String> recommendations
    ) {}

    /** Phân tích từng ngày */
    public record DayAnalysis(
            LocalDate date,
            Map<String, ShiftTypeAnalysis> shiftTypes
    ) {}

    /** Phân tích từng loại lịch trong ngày */
    public record ShiftTypeAnalysis(
            String shiftTypeId,
            int required,
            int eligibleStaff,
            int activeStaff,
            int onLeave,
            int onCompensation,
            double coverageRate,
            boolean isUnderstaffed,
            String issue
    ) {}

    /** Tóm tắt availability theo loại lịch */
    public record StaffAvailabilitySummary(
            String shiftTypeId,
            int totalActiveStaff,
            int eligibleStaff,
            double averageDailyEligible,
            int minDailyEligible,
            int maxDailyEligible,
            double utilizationRate
    ) {}

    /**
     * Phân tích tính khả thi cho một kỳ lịch.
     *
     * <p>Thuật toán:
     * <ol>
     *   <li>Load requirements, staff, leaves, compensations, holidays</li>
     *   <li>Với mỗi ngày trong kỳ:
     *     <ul>
     *       <li>Tính staff đang nghỉ (leaves + compensations + holidays)</li>
     *       <li>Với mỗi shift-type trong ngày, đếm eligible staff theo
     *           {@link StaffShiftTypeEligibility} (L04 specialty matching + cross-specialty)</li>
     *       <li>So sánh eligible vs required → xác định understaffed</li>
     *     </ul>
     *   <li>Tổng hợp thành summary per shift-type</li>
     *   <li>Sinh warnings + recommendations</li>
     * </ol>
     */
    public FeasibilityReport analyzeFeasibility(Integer periodId) {
        // 1. Load requirements and staff
        List<ShiftRequirement> requirements = requirementRepository.findByPeriodId(periodId);
        List<Staff> allActiveStaff = staffRepository.findByIsActiveTrue();

        if (requirements.isEmpty() || allActiveStaff.isEmpty()) {
            return FeasibilityReport.builder()
                    .feasible(false)
                    .totalDays(0)
                    .feasibleDays(0)
                    .understaffedDays(0)
                    .coverageRate(0)
                    .dailyAnalysis(Collections.emptyList())
                    .availabilityByShiftType(Collections.emptyMap())
                    .warnings(List.of("Không có yêu cầu hoặc nhân sự active"))
                    .recommendations(List.of("Thêm yêu cầu nhân sự hoặc kích hoạt nhân sự"))
                    .build();
        }

        // 2. Get date range
        LocalDate startDate = requirements.stream()
                .map(ShiftRequirement::getWorkDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
        LocalDate endDate = requirements.stream()
                .map(ShiftRequirement::getWorkDate)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        // 3. Load leaves, compensations, holidays
        List<LeaveRequest> leaves = leaveRequestRepository.findApprovedInRange(startDate, endDate);
        List<CompensationDay> compensations = compensationDayRepository.findInRange(startDate, endDate);
        List<Holiday> holidays = holidayRepository.findActiveHolidaysBetween(startDate, endDate);

        // 3a. Map: date → staff IDs on leave
        Map<LocalDate, Set<Integer>> leavesByDate = new HashMap<>();
        for (LeaveRequest leave : leaves) {
            for (LocalDate d = leave.getStartDate(); !d.isAfter(leave.getEndDate()); d = d.plusDays(1)) {
                leavesByDate.computeIfAbsent(d, k -> new HashSet<>()).add(leave.getStaff().getId());
            }
        }

        // 3b. Map: date → staff IDs on compensation
        Map<LocalDate, Set<Integer>> compDaysByDate = new HashMap<>();
        for (CompensationDay cd : compensations) {
            compDaysByDate.computeIfAbsent(cd.getCompensationDate(), k -> new HashSet<>())
                    .add(cd.getStaff().getId());
        }

        // 3c. Set of holiday dates
        Set<LocalDate> holidayDates = holidays.stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        // 3d. Build L04 cross-specialty config from DB
        List<String> l04AllowedSpecialties = List.of();
        boolean l04CrossSpecialty = false;
        try {
            var cfgOpt = algorithmConfigService.getAutoGenConfig();
            if (cfgOpt.isPresent()) {
                var cfg = cfgOpt.get();
                l04CrossSpecialty = cfg.l04CrossSpecialty();
                l04AllowedSpecialties = cfg.l04AllowedSpecialties() != null
                        ? cfg.l04AllowedSpecialties()
                        : List.of();
            }
        } catch (Exception e) {
            log.warn("Could not load L04 cross-specialty config, using defaults: {}", e.getMessage());
        }

        // 4. Group requirements by date and shift type
        Map<LocalDate, Map<String, List<ShiftRequirement>>> reqsByDateAndType = requirements.stream()
                .collect(Collectors.groupingBy(
                        ShiftRequirement::getWorkDate,
                        Collectors.groupingBy(r -> r.getShiftType().getId())
                ));

        // 5. Analyze each day
        List<DayAnalysis> dailyAnalysis = new ArrayList<>();
        int understaffedDayCount = 0;
        int feasibleDays = 0;

        // Accumulators for per-shift-type statistics across all days
        Map<String, List<Integer>> dailyEligibleByType = new HashMap<>();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Map<String, ShiftTypeAnalysis> shiftTypeAnalysis = new HashMap<>();

            // Skip holidays (no scheduling needed)
            if (holidayDates.contains(date)) {
                dailyAnalysis.add(new DayAnalysis(date, Collections.emptyMap()));
                continue;
            }

            Map<String, List<ShiftRequirement>> dayReqs = reqsByDateAndType.getOrDefault(date, Collections.emptyMap());

            Set<Integer> staffOnLeave = leavesByDate.getOrDefault(date, Collections.emptySet());
            Set<Integer> staffOnComp = compDaysByDate.getOrDefault(date, Collections.emptySet());

            // Pool of staff available (not on leave, not on compensation)
            List<Staff> pool = allActiveStaff.stream()
                    .filter(s -> !staffOnLeave.contains(s.getId()))
                    .filter(s -> !staffOnComp.contains(s.getId()))
                    .collect(Collectors.toList());

            int activeStaff = pool.size();
            int onLeave = staffOnLeave.size();
            int onComp = staffOnComp.size();

            for (Map.Entry<String, List<ShiftRequirement>> entry : dayReqs.entrySet()) {
                String shiftTypeId = entry.getKey();
                List<ShiftRequirement> dayReqList = entry.getValue();

                int totalRequired = dayReqList.stream()
                        .mapToInt(ShiftRequirement::getRequiredStaffCount)
                        .sum();

                // Count eligible staff using StaffShiftTypeEligibility (mirrors actual scheduler logic)
                int eligible = countEligibleStaff(shiftTypeId, dayReqList, pool,
                        l04CrossSpecialty, l04AllowedSpecialties);

                // Accumulate for summary
                dailyEligibleByType.computeIfAbsent(shiftTypeId, k -> new ArrayList<>()).add(eligible);

                double coverage = totalRequired > 0 ? (double) eligible / totalRequired * 100 : 100;
                boolean isUnderstaffed = eligible < totalRequired;

                String issue = null;
                if (isUnderstaffed) {
                    if (eligible == 0) {
                        issue = "Không có nhân sự eligible";
                    } else {
                        issue = String.format("Thiếu %d nhân sự (%d/%d)",
                                totalRequired - eligible, eligible, totalRequired);
                    }
                }

                shiftTypeAnalysis.put(shiftTypeId, new ShiftTypeAnalysis(
                        shiftTypeId,
                        totalRequired,
                        eligible,
                        activeStaff,
                        onLeave,
                        onComp,
                        coverage,
                        isUnderstaffed,
                        issue
                ));
            }

            if (!shiftTypeAnalysis.isEmpty()) {
                dailyAnalysis.add(new DayAnalysis(date, shiftTypeAnalysis));
                boolean anyUnderstaffed = shiftTypeAnalysis.values().stream()
                        .anyMatch(ShiftTypeAnalysis::isUnderstaffed);
                if (anyUnderstaffed) {
                    understaffedDayCount++;
                } else {
                    feasibleDays++;
                }
            } else {
                dailyAnalysis.add(new DayAnalysis(date, Collections.emptyMap()));
            }
        }

        // 6. Calculate availability summary per shift type
        Map<String, StaffAvailabilitySummary> availabilityByShiftType = calculateAvailabilitySummary(
                allActiveStaff, dailyEligibleByType);

        // 7. Generate warnings and recommendations
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        int totalDays = (int) (endDate.toEpochDay() - startDate.toEpochDay()) + 1;
        double coverageRate = totalDays > 0 ? (double) feasibleDays / totalDays * 100 : 0;

        if (coverageRate < 50) {
            warnings.add(String.format("[CANH-BAO] Chỉ %.0f%% ngày có đủ nhân sự - hệ thống sẽ xếp thiếu nhiều", coverageRate));
        } else if (coverageRate < 80) {
            warnings.add(String.format("[CANH-BAO] %.0f%% ngày có đủ nhân sự - một số ca sẽ thiếu", coverageRate));
        }

        // Detect "no buffer" risk: eligible == required on every day of a shift type.
        // This means any single absence will cause understaffing — a hidden risk
        // even when coverageRate = 100%.
        Map<String, Long> noBufferDaysByType = new HashMap<>();
        Map<String, Long> totalDaysByType = new HashMap<>();
        for (DayAnalysis day : dailyAnalysis) {
            for (ShiftTypeAnalysis sta : day.shiftTypes().values()) {
                totalDaysByType.merge(sta.shiftTypeId(), 1L, Long::sum);
                if (sta.required() > 0 && sta.eligibleStaff() == sta.required()) {
                    noBufferDaysByType.merge(sta.shiftTypeId(), 1L, Long::sum);
                }
            }
        }
        for (String shiftTypeId : totalDaysByType.keySet()) {
            long total = totalDaysByType.get(shiftTypeId);
            long noBuffer = noBufferDaysByType.getOrDefault(shiftTypeId, 0L);
            if (noBuffer == total) {
                // Every single day has eligible == required — zero buffer
                warnings.add(String.format(
                        "[CANH-BAO] [%s] vừa đủ nhân sự mỗi ngày nhưng KHÔNG có dự phòng — nếu 1 người nghỉ, ca đó sẽ thiếu",
                        SHIFT_TYPE_LABELS.getOrDefault(shiftTypeId, shiftTypeId)));
            } else if (noBuffer > 0 && noBuffer == total - 1) {
                // Almost every day has no buffer — high risk
                warnings.add(String.format(
                        "[CANH-BAO] [%s] có %d/%d ngày vừa đủ (không có dự phòng) — cần chú ý ngày nghỉ phép",
                        SHIFT_TYPE_LABELS.getOrDefault(shiftTypeId, shiftTypeId), noBuffer, total));
            }
        }

        // Specific day/shift-type recommendations
        Map<String, Integer> shortageByType = new HashMap<>();
        for (DayAnalysis day : dailyAnalysis) {
            for (ShiftTypeAnalysis sta : day.shiftTypes().values()) {
                if (sta.isUnderstaffed() && sta.eligibleStaff() == 0) {
                    recommendations.add(String.format(
                            "[NGAY] %s [%s]: Không có nhân sự eligible - bật cross-specialty hoặc thêm nhân sự",
                            day.date(), SHIFT_TYPE_LABELS.getOrDefault(sta.shiftTypeId(), sta.shiftTypeId())));
                } else if (sta.isUnderstaffed()) {
                    shortageByType.merge(sta.shiftTypeId(), sta.required() - sta.eligibleStaff(), Integer::sum);
                }
            }
        }

        // Recommend enabling cross-specialty if L04 has shortages
        String l04Label = SHIFT_TYPE_LABELS.getOrDefault("L04", "PK Chuyên gia");
        if (shortageByType.containsKey("L04") && !l04CrossSpecialty) {
            recommendations.add(String.format(
                    "[GOI-Y] %s thiếu %d ca — bật cross-specialty trong Cấu hình thuật toán để tăng pool nhân sự",
                    l04Label, shortageByType.get("L04")));
        }

        boolean hasNoBufferWarning = noBufferDaysByType.values().stream().anyMatch(v -> v > 0);
        if (shortageByType.isEmpty() && coverageRate >= 80) {
            if (hasNoBufferWarning) {
                recommendations.add("[GOI-Y] Kỳ lịch đủ nhân sự nhưng thiếu buffer dự phòng — cân nhắc thêm nhân sự hoặc giảm required");
            } else {
                recommendations.add("[GOI-Y] Kỳ lịch khả thi với cấu hình hiện tại");
            }
        } else {
            recommendations.add("[GOI-Y] Giảm required count nếu không đủ nhân sự thực tế");
        }

        return FeasibilityReport.builder()
                .feasible(coverageRate >= 80)
                .totalDays(totalDays)
                .feasibleDays(feasibleDays)
                .understaffedDays(understaffedDayCount)
                .coverageRate(coverageRate)
                .dailyAnalysis(dailyAnalysis)
                .availabilityByShiftType(availabilityByShiftType)
                .warnings(warnings)
                .recommendations(recommendations)
                .build();
    }

    /**
     * Đếm số staff eligible cho một shift-type trong ngày.
     *
     * <p>Sử dụng {@link StaffShiftTypeEligibility} — cùng logic với
     * {@link AutoSchedulingService}, đảm bảo feasibility report đồng nhất
     * với kết quả thực tế của scheduler.
     *
     * <p>Với L04, kiểm tra:
     * <ul>
     *   <li>Staff active + thuộc eligible specialties</li>
     *   <li>Staff khớp requiredSpecialtyId HOẶC thuộc l04AllowedSpecialties (nếu cross-specialty bật)</li>
     * </ul>
     */
    private int countEligibleStaff(
            String shiftTypeId,
            List<ShiftRequirement> requirements,
            List<Staff> pool,
            boolean l04CrossSpecialty,
            List<String> l04AllowedSpecialties) {

        switch (shiftTypeId) {
            case "L01":
            case "L02":
            case "L03":
                // L01/L02/L03: tất cả active staff ∈ eligible specialties
                return (int) pool.stream()
                        .filter(s -> StaffShiftTypeEligibility.isEligibleForAnyShift(s))
                        .count();

            case "L04": {
                // L04: staff phải khớp requiredSpecialty HOẶC trong allowed list (cross-specialty)
                Set<Integer> requiredSpecIds = requirements.stream()
                        .filter(r -> r.getSpecialty() != null)
                        .map(r -> r.getSpecialty().getId())
                        .collect(Collectors.toSet());

                return (int) pool.stream()
                        .filter(s -> StaffShiftTypeEligibility.isEligibleForAnyShift(s))
                        .filter(s -> {
                            // Staff phải khớp MỘT trong required specialties
                            if (requiredSpecIds.isEmpty()) return true; // no specialty constraint
                            Integer staffSpecId = s.getSpecialty() != null ? s.getSpecialty().getId() : null;
                            if (staffSpecId != null && requiredSpecIds.contains(staffSpecId)) return true;

                            // HOẶC trong cross-specialty allowlist
                            if (l04CrossSpecialty && l04AllowedSpecialties != null && !l04AllowedSpecialties.isEmpty()) {
                                String staffSpecName = s.getSpecialty() != null ? s.getSpecialty().getName() : null;
                                if (staffSpecName != null && l04AllowedSpecialties.contains(staffSpecName)) {
                                    return true;
                                }
                            }
                            return false;
                        })
                        .count();
            }

            default:
                return 0;
        }
    }

    /**
     * Tính toán summary availability per shift type.
     */
    private Map<String, StaffAvailabilitySummary> calculateAvailabilitySummary(
            List<Staff> allActiveStaff,
            Map<String, List<Integer>> dailyEligibleByType) {

        int totalActive = allActiveStaff.size();
        Map<String, StaffAvailabilitySummary> summary = new HashMap<>();

        for (Map.Entry<String, List<Integer>> entry : dailyEligibleByType.entrySet()) {
            String shiftTypeId = entry.getKey();
            List<Integer> counts = entry.getValue();

            if (counts.isEmpty()) continue;

            double avgEligible = counts.stream().mapToInt(Integer::intValue).average().orElse(0);
            int minEligible = counts.stream().mapToInt(Integer::intValue).min().orElse(0);
            int maxEligible = counts.stream().mapToInt(Integer::intValue).max().orElse(0);
            double utilization = totalActive > 0 ? avgEligible / totalActive * 100 : 0;

            // eligibleStaff in summary = count of distinct staff that ever appear as eligible
            // across all days (unique staff pool for this shift type)
            summary.put(shiftTypeId, new StaffAvailabilitySummary(
                    shiftTypeId,
                    totalActive,
                    (int) avgEligible,   // simplified: avg daily eligible
                    avgEligible,
                    minEligible,
                    maxEligible,
                    utilization
            ));
        }

        return summary;
    }

    /** Kiểm tra nhanh xem kỳ lịch có khả thi không */
    public boolean isPeriodFeasible(Integer periodId) {
        return analyzeFeasibility(periodId).feasible();
    }

    /** Lấy danh sách ngày bị understaffed */
    public List<LocalDate> getUnderstaffedDates(Integer periodId) {
        return analyzeFeasibility(periodId).dailyAnalysis().stream()
                .filter(day -> day.shiftTypes().values().stream().anyMatch(ShiftTypeAnalysis::isUnderstaffed))
                .map(DayAnalysis::date)
                .collect(Collectors.toList());
    }

    private static final Map<String, String> SHIFT_TYPE_LABELS = Map.of(
            "L01", "Trực 24/24",
            "L02", "Thông tầm",
            "L03", "PK Dịch vụ",
            "L04", "PK Chuyên gia"
    );
}
