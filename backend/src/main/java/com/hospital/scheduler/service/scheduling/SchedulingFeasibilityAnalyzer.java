package com.hospital.scheduler.service.scheduling;

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
 * <p>Cung cấp báo cáo chi tiết về:
 * <ul>
 *   <li>Tỷ lệ eligible/required cho mỗi ngày và loại lịch</li>
 *   <li>Cảnh báo khi requirement không khả thi</li>
 *   <li>Gợi ý cấu hình cross-specialty</li>
 *   <li>Thống kê staff availability</li>
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
    private final AlgorithmConfigService algorithmConfigService;

    /**
     * Kết quả phân tích tính khả thi
     */
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

    /**
     * Phân tích từng ngày
     */
    public record DayAnalysis(
            LocalDate date,
            Map<String, ShiftTypeAnalysis> shiftTypes
    ) {}

    /**
     * Phân tích từng loại lịch trong ngày
     */
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

    /**
     * Tóm tắt availability theo loại lịch
     */
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
     * Phân tích tính khả thi cho một kỳ lịch
     */
    public FeasibilityReport analyzeFeasibility(Integer periodId) {
        // 1. Load data
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

        // 3. Load leaves and compensations
        Set<LocalDate> holidayDates = Collections.emptySet(); // TODO: load from holiday table
        List<LeaveRequest> leaves = leaveRequestRepository
                .findApprovedInRange(startDate, endDate);
        List<CompensationDay> compensations = compensationDayRepository
                .findInRange(startDate, endDate);

        Map<LocalDate, Set<Integer>> leavesByDate = new HashMap<>();
        for (LeaveRequest leave : leaves) {
            for (LocalDate d = leave.getStartDate(); !d.isAfter(leave.getEndDate()); d = d.plusDays(1)) {
                leavesByDate.computeIfAbsent(d, k -> new HashSet<>()).add(leave.getStaff().getId());
            }
        }

        Set<Integer> compDayStaffIds = compensations.stream()
                .map(c -> c.getStaff().getId())
                .collect(Collectors.toSet());

        // 4. Group requirements by date and shift type
        Map<LocalDate, Map<String, List<ShiftRequirement>>> reqsByDateAndType = requirements.stream()
                .collect(Collectors.groupingBy(
                        ShiftRequirement::getWorkDate,
                        Collectors.groupingBy(r -> r.getShiftType().getId())
                ));

        // 5. Analyze each day
        List<DayAnalysis> dailyAnalysis = new ArrayList<>();
        int understaffedDays = 0;
        int feasibleDays = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Map<String, ShiftTypeAnalysis> shiftTypeAnalysis = new HashMap<>();
            
            Map<String, List<ShiftRequirement>> dayReqs = reqsByDateAndType.getOrDefault(date, Collections.emptyMap());
            
            Set<Integer> staffOnLeave = leavesByDate.getOrDefault(date, Collections.emptySet());
            Set<Integer> availableStaffIds = allActiveStaff.stream()
                    .filter(s -> !staffOnLeave.contains(s.getId()) && !compDayStaffIds.contains(s.getId()))
                    .map(Staff::getId)
                    .collect(Collectors.toSet());

            for (Map.Entry<String, List<ShiftRequirement>> entry : dayReqs.entrySet()) {
                String shiftTypeId = entry.getKey();
                List<ShiftRequirement> dayReqList = entry.getValue();
                
                int totalRequired = dayReqList.stream()
                        .mapToInt(ShiftRequirement::getRequiredStaffCount)
                        .sum();

                // Count eligible staff (simplified - actual eligibility depends on more factors)
                int eligible = countEligibleStaff(shiftTypeId, dayReqList, availableStaffIds, allActiveStaff);
                int active = availableStaffIds.size();
                int onLeave = (int) staffOnLeave.stream()
                        .filter(id -> allActiveStaff.stream().anyMatch(s -> s.getId().equals(id)))
                        .count();

                double coverage = totalRequired > 0 ? (double) eligible / totalRequired * 100 : 100;
                boolean isUnderstaffed = eligible < totalRequired;
                
                String issue = null;
                if (isUnderstaffed) {
                    understaffedDays++;
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
                        active,
                        onLeave,
                        compDayStaffIds.size(),
                        coverage,
                        isUnderstaffed,
                        issue
                ));
            }

            if (!shiftTypeAnalysis.isEmpty()) {
                dailyAnalysis.add(new DayAnalysis(date, shiftTypeAnalysis));
                if (!shiftTypeAnalysis.values().stream().anyMatch(ShiftTypeAnalysis::isUnderstaffed)) {
                    feasibleDays++;
                }
            }
        }

        // 6. Calculate availability by shift type
        Map<String, StaffAvailabilitySummary> availabilityByShiftType = calculateAvailabilitySummary(
                requirements, allActiveStaff, dailyAnalysis);

        // 7. Generate warnings and recommendations
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        double coverageRate = dailyAnalysis.isEmpty() ? 0 : 
                (double) feasibleDays / dailyAnalysis.size() * 100;

        if (coverageRate < 50) {
            warnings.add(String.format("[CANH-BAO] Chỉ %.0f%% ngày có đủ nhân sự - hệ thống sẽ xếp thiếu nhiều", coverageRate));
        } else if (coverageRate < 80) {
            warnings.add(String.format("[CANH-BAO] %.0f%% ngày có đủ nhân sự - một số ca sẽ thiếu", coverageRate));
        }

        // Check for specific issues
        for (DayAnalysis day : dailyAnalysis) {
            for (ShiftTypeAnalysis sta : day.shiftTypes().values()) {
                if (sta.isUnderstaffed() && sta.eligibleStaff() == 0) {
                    recommendations.add(String.format(
                            "[NGAY] Ngày %s [%s]: Bật cross-specialty hoặc thêm nhân sự",
                            day.date(), sta.shiftTypeId()));
                }
            }
        }

        // Add cross-specialty recommendations (use plain-text tags instead of
        // emoji to avoid font-rendering issues; the frontend maps the tag to a
        // Material Symbol icon).
        recommendations.add("[GOI-Y] Bật cross-specialty trong Cấu hình thuật toán để tăng pool nhân sự eligible");
        recommendations.add("[GOI-Y] Giảm required count nếu không đủ nhân sự thực tế");

        return FeasibilityReport.builder()
                .feasible(coverageRate >= 80)
                .totalDays(dailyAnalysis.size())
                .feasibleDays(feasibleDays)
                .understaffedDays(understaffedDays)
                .coverageRate(coverageRate)
                .dailyAnalysis(dailyAnalysis)
                .availabilityByShiftType(availabilityByShiftType)
                .warnings(warnings)
                .recommendations(recommendations)
                .build();
    }

    /**
     * Đếm số staff eligible cho một loại lịch cụ thể
     */
    private int countEligibleStaff(
            String shiftTypeId,
            List<ShiftRequirement> requirements,
            Set<Integer> availableStaffIds,
            List<Staff> allActiveStaff) {

        // Get specialty requirements
        Set<Integer> requiredSpecialtyIds = requirements.stream()
                .filter(r -> r.getSpecialty() != null)
                .map(r -> r.getSpecialty().getId())
                .collect(Collectors.toSet());

        // Simplified eligibility check
        return (int) allActiveStaff.stream()
                .filter(s -> availableStaffIds.contains(s.getId()))
                .filter(s -> {
                    // Check eligibility based on shift type
                    return isStaffEligibleForShiftType(s, shiftTypeId);
                })
                .count();
    }

    /**
     * Kiểm tra staff có eligible cho shift type không
     */
    private boolean isStaffEligibleForShiftType(Staff staff, String shiftTypeId) {
        // L01: Cross-specialty - all specialties allowed
        // L02: Cross-specialty - all specialties allowed
        // L03: Cross-specialty - all specialties allowed
        // L04: Only matching specialty (or cross-specialty if enabled)
        
        // For simplicity, assume all active staff are eligible for L01-L03
        // L04 requires specialty match (simplified)
        if ("L04".equals(shiftTypeId)) {
            // In real implementation, check specialty match
            return true;
        }
        return true;
    }

    /**
     * Tính toán summary availability theo shift type
     */
    private Map<String, StaffAvailabilitySummary> calculateAvailabilitySummary(
            List<ShiftRequirement> requirements,
            List<Staff> allActiveStaff,
            List<DayAnalysis> dailyAnalysis) {

        Map<String, List<Integer>> dailyEligibleByType = new HashMap<>();
        
        for (DayAnalysis day : dailyAnalysis) {
            for (ShiftTypeAnalysis sta : day.shiftTypes().values()) {
                dailyEligibleByType
                        .computeIfAbsent(sta.shiftTypeId(), k -> new ArrayList<>())
                        .add(sta.eligibleStaff());
            }
        }

        Map<String, StaffAvailabilitySummary> summary = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : dailyEligibleByType.entrySet()) {
            String shiftTypeId = entry.getKey();
            List<Integer> counts = entry.getValue();
            
            int totalActive = allActiveStaff.size();
            int avgEligible = counts.isEmpty() ? 0 : 
                    (int) counts.stream().mapToInt(Integer::intValue).average().orElse(0);
            int minEligible = counts.isEmpty() ? 0 : 
                    counts.stream().mapToInt(Integer::intValue).min().orElse(0);
            int maxEligible = counts.isEmpty() ? 0 : 
                    counts.stream().mapToInt(Integer::intValue).max().orElse(0);
            double utilization = totalActive > 0 ? (double) avgEligible / totalActive * 100 : 0;

            summary.put(shiftTypeId, new StaffAvailabilitySummary(
                    shiftTypeId,
                    totalActive,
                    totalActive, // Simplified
                    avgEligible,
                    minEligible,
                    maxEligible,
                    utilization
            ));
        }

        return summary;
    }

    /**
     * Kiểm tra nhanh xem kỳ lịch có khả thi không
     */
    public boolean isPeriodFeasible(Integer periodId) {
        FeasibilityReport report = analyzeFeasibility(periodId);
        return report.feasible();
    }

    /**
     * Lấy danh sách ngày bị understaffed
     */
    public List<LocalDate> getUnderstaffedDates(Integer periodId) {
        FeasibilityReport report = analyzeFeasibility(periodId);
        return report.dailyAnalysis().stream()
                .filter(day -> day.shiftTypes().values().stream().anyMatch(ShiftTypeAnalysis::isUnderstaffed))
                .map(DayAnalysis::date)
                .collect(Collectors.toList());
    }
}
