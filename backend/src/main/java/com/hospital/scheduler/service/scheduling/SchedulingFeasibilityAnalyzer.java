package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** Mức độ rủi ro buffer */
    public enum BufferRisk {
        NONE,       // eligible > required — an toàn
        LOW,        // eligible == required — bất kỳ ai nghỉ đều thiếu
        MEDIUM,     // N-1 ngày no-buffer
        HIGH        // mọi ngày no-buffer
    }

    /** Tóm tắt availability theo loại lịch */
    public record StaffAvailabilitySummary(
            String shiftTypeId,
            int totalActiveStaff,
            int eligibleStaff,
            double averageDailyEligible,
            int minDailyEligible,
            int maxDailyEligible,
            double utilizationRate,
            int bufferMin,            // = minDailyEligible - typicalRequired
            BufferRisk bufferRisk,    // mức độ rủi ro no-buffer
            int noBufferDays,         // số ngày eligible == required
            int totalDays,             // tổng số ngày có shift này
            List<StaffBackup> backups // nhân sự có thể thay thế nếu buffer = 0
    ) {}

    /** Nhân sự dự phòng có thể thay thế */
    public record StaffBackup(
            Integer staffId,
            String staffName,
            String specialtyName,
            int daysAvailable  // số ngày trong kỳ nhân sự này KHÔNG bị block
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
    @Transactional(readOnly = true)
    public FeasibilityReport analyzeFeasibility(Integer periodId) {
        // 1. Load requirements and staff
        List<ShiftRequirement> requirements = requirementRepository.findByPeriodId(periodId);
        // BUGFIX (lazy-specialty-init): use the variant that LEFT JOIN FETCHes the
        // Specialty so downstream filter logic can read staff.getSpecialty().getId()
        // / .getName() without tripping LazyInitializationException once the
        // transaction boundary is left.
        List<Staff> allActiveStaff = staffRepository.findByIsActiveTrueWithSpecialty();

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

        // 4. Group requirements by date and shift type (defensive: skip null shiftType)
        Map<LocalDate, Map<String, List<ShiftRequirement>>> reqsByDateAndType = requirements.stream()
                .filter(r -> r.getShiftType() != null)
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

        // Combined pool: all active staff (used for backup/staffing analysis)
        Set<Integer> allLeaveStaffIds = leavesByDate.values().stream()
                .flatMap(Set::stream).collect(Collectors.toSet());
        Set<Integer> allCompStaffIds = compDaysByDate.values().stream()
                .flatMap(Set::stream).collect(Collectors.toSet());
        List<Staff> combinedPool = allActiveStaff.stream()
                .filter(s -> !allLeaveStaffIds.contains(s.getId()))
                .filter(s -> !allCompStaffIds.contains(s.getId()))
                .toList();

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
                allActiveStaff, dailyEligibleByType,
                reqsByDateAndType, combinedPool,
                l04CrossSpecialty, l04AllowedSpecialties);

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
            String label = SHIFT_TYPE_LABELS.getOrDefault(shiftTypeId, shiftTypeId);
            if (noBuffer == total) {
                warnings.add(String.format(
                        "[CANH-BAO] [%s] vừa đủ nhân sự mỗi ngày nhưng KHÔNG có dự phòng — nếu 1 người nghỉ, ca đó sẽ thiếu",
                        label));
            } else if (noBuffer > 0 && noBuffer == total - 1) {
                warnings.add(String.format(
                        "[CANH-BAO] [%s] có %d/%d ngày vừa đủ (không có dự phòng) — cần chú ý ngày nghỉ phép",
                        label, noBuffer, total));
            }
        }

        // Actionable recommendations based on availability summary
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

        // Recommend enabling cross-specialty if L04 has shortages or no-buffer
        String l04Label = SHIFT_TYPE_LABELS.getOrDefault("L04", "PK Chuyên gia");
        if (shortageByType.containsKey("L04") && !l04CrossSpecialty) {
            recommendations.add(String.format(
                    "[GOI-Y] %s thiếu %d ca — bật cross-specialty trong Cấu hình thuật toán để tăng pool nhân sự",
                    l04Label, shortageByType.get("L04")));
        }
        // Also check buffer risk for L04
        var l04Summary = availabilityByShiftType.get("L04");
        if (l04Summary != null && l04Summary.bufferRisk() != BufferRisk.NONE && !l04CrossSpecialty) {
            recommendations.add(String.format(
                    "[GOI-Y] %s có bufferRisk=%s — bật cross-specialty để tăng dự phòng",
                    l04Label, l04Summary.bufferRisk()));
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

                            // Cross-specialty: empty allowed list = tất cả khoa được phép
                            if (l04CrossSpecialty) {
                                if (l04AllowedSpecialties == null || l04AllowedSpecialties.isEmpty()) {
                                    return true; // empty = all specialties
                                }
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
            Map<String, List<Integer>> dailyEligibleByType,
            Map<LocalDate, Map<String, List<ShiftRequirement>>> reqsByDateAndType,
            List<Staff> pool,
            boolean l04CrossSpecialty,
            List<String> l04AllowedSpecialties) {

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

            // Gather required counts per day to compute mode/median and no-buffer days
            List<Integer> requiredCounts = new ArrayList<>();
            List<Integer> eligibleCounts = counts;
            int noBufferDays = 0;
            int idx = 0;
            for (LocalDate date : reqsByDateAndType.keySet().stream().sorted().toList()) {
                Map<String, List<ShiftRequirement>> dayReqs = reqsByDateAndType.get(date);
                if (dayReqs == null || !dayReqs.containsKey(shiftTypeId)) continue;
                List<ShiftRequirement> dayReqList = dayReqs.get(shiftTypeId);
                int required = dayReqList.stream().mapToInt(ShiftRequirement::getRequiredStaffCount).sum();
                requiredCounts.add(required);
                if (idx < eligibleCounts.size()) {
                    if (eligibleCounts.get(idx) == required) noBufferDays++;
                }
                idx++;
            }
            int totalDaysForThisType = requiredCounts.size();
            int typicalRequired = computeModeOrMedian(requiredCounts);

            int bufferMin = minEligible - typicalRequired;

            // Determine buffer risk level
            BufferRisk risk;
            if (bufferMin > 0) {
                risk = BufferRisk.NONE;
            } else if (bufferMin == 0) {
                if (totalDaysForThisType > 0 && noBufferDays == totalDaysForThisType) {
                    risk = BufferRisk.HIGH;
                } else if (totalDaysForThisType > 0 && noBufferDays >= totalDaysForThisType - 1) {
                    risk = BufferRisk.MEDIUM;
                } else {
                    risk = BufferRisk.LOW;
                }
            } else {
                risk = BufferRisk.LOW;
            }

            // Find backup staff: eligible but not in main pool (on leave/comp)
            List<StaffBackup> backups = findBackupStaff(shiftTypeId, allActiveStaff, pool,
                    l04CrossSpecialty, l04AllowedSpecialties);

            summary.put(shiftTypeId, new StaffAvailabilitySummary(
                    shiftTypeId,
                    totalActive,
                    (int) avgEligible,
                    avgEligible,
                    minEligible,
                    maxEligible,
                    utilization,
                    bufferMin,
                    risk,
                    noBufferDays,
                    totalDaysForThisType,
                    backups
            ));
        }

        return summary;
    }

    /** Tính typical required = mode hoặc median của required counts */
    private int computeModeOrMedian(List<Integer> values) {
        if (values.isEmpty()) return 0;
        Map<Integer, Long> freq = values.stream()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));
        var maxEntry = freq.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
        if (maxEntry != null && maxEntry.getValue() > 1) {
            return maxEntry.getKey();
        }
        // else median
        List<Integer> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(mid - 1) + sorted.get(mid)) / 2 : sorted.get(mid);
    }

    /** Tìm nhân sự dự phòng — không trong pool chính nhưng có thể cross-cover */
    private List<StaffBackup> findBackupStaff(
            String shiftTypeId,
            List<Staff> allActiveStaff,
            List<Staff> pool,
            boolean l04CrossSpecialty,
            List<String> l04AllowedSpecialties) {

        Set<Integer> poolIds = pool.stream().map(Staff::getId).collect(Collectors.toSet());
        Set<Integer> crossEligibleIds = new HashSet<>();

	        if ("L04".equals(shiftTypeId) && l04CrossSpecialty) {
	            boolean allAllowed = l04AllowedSpecialties == null || l04AllowedSpecialties.isEmpty();
	            crossEligibleIds = allActiveStaff.stream()
	                    .filter(s -> poolIds.contains(s.getId()))
	                    .filter(s -> s.getSpecialty() != null && (allAllowed || l04AllowedSpecialties.contains(s.getSpecialty().getName())))
	                    .map(Staff::getId)
	                    .collect(Collectors.toSet());
	        }

	        // Backups = staff active but NOT in pool (on leave/comp)
	        // AND eligible via cross-specialty if L04
	        List<StaffBackup> backups = allActiveStaff.stream()
	                .filter(s -> !poolIds.contains(s.getId()))
	                .filter(s -> {
	                    if ("L04".equals(shiftTypeId)) {
	                        if (!l04CrossSpecialty) return false;
	                        if (s.getSpecialty() == null) return false;
	                        if (l04AllowedSpecialties == null || l04AllowedSpecialties.isEmpty()) return true; // all allowed
	                        return l04AllowedSpecialties.contains(s.getSpecialty().getName());
	                    }
	                    return true;
	                })
                .map(s -> new StaffBackup(
                        s.getId(),
                        s.getFullName(),
                        s.getSpecialty() != null ? s.getSpecialty().getName() : "—",
                        0))
                .toList();

        return backups;
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
