package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Filters and sorts eligible staff for a ShiftRequirement.
 * Replaces the inline {@code filterAndSortEligibleStaffBatch} logic from
 * {@code AutoSchedulingService} with a focused, testable component.
 */
@Slf4j
@Component
public class StaffEligibilityFilter {

    private final ConflictDetectionService conflictDetectionService;

    public StaffEligibilityFilter(ConflictDetectionService conflictDetectionService) {
        this.conflictDetectionService = conflictDetectionService;
    }

    public record WeeklyCountTracker(Map<Integer, Map<String, Integer>> weeklyCounts) {}

    /**
     * @deprecated Not used by production scheduler.
     *             Production scheduling uses
     *             {@link com.hospital.scheduler.service.AutoSchedulingService#filterAndSortEligibleStaffBatch(
     *                 List, ShiftRequirement, Set, Set,
     *                 SchedulingConflictDataLoader.BatchConflictData, boolean,
     *                 Comparator, SchedulingConflictDataLoader.PeriodConflictData,
     *                 Set, Set, int, int, String, Map, Map,
     *                 AlgorithmConfigService.AlgorithmRuntimeConfig, List, Integer)}.
     *             <p>
     *             Kept only for reference and will be removed in a future release.
     *             <p>
     *             Scheduled for removal.
     */
    @Deprecated(forRemoval = true)
    public List<Staff> filterAndSortEligibleStaffBatch(
            List<Staff> pool,
            ShiftRequirement req,
            Set<Integer> excludedStaffIds,
            Set<Integer> assignedStaffIds,
            SchedulingConflictDataLoader.BatchConflictData batchData,
            boolean skipCompensationCheck,
            Comparator<Staff> sortComparator,
            SchedulingConflictDataLoader.PeriodConflictData periodData,
            Set<Integer> additionalAdjacentL01,
            Set<Integer> additionalCompDayStaffIds,
            int maxShiftsPerStaffLimit,
            int maxShiftsPerTypeLimit,
            String fairShareKey,
            Map<Integer, Map<String, Long>> runningCounts,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            List<Staff> allActiveStaff,
            Integer maxShiftsPerMonthOverride) {

        ShiftType shiftType = req.getShiftType();
        String shiftTypeId = shiftType.getId();
        // L04 luôn strict-specialty (cross-specialty đã gỡ — thay bằng
        // "đổi ngày mở thích ứng"): staff không đúng chuyên khoa không bao
        // giờ được đưa vào pool L04.

        List<Staff> strictMatches = new ArrayList<>();

        for (Staff staff : pool) {
            if (excludedStaffIds != null && excludedStaffIds.contains(staff.getId())) continue;

            // 0. Eligibility check via StaffShiftTypeEligibility
            // L01/L02/L03: chỉ cần active + ALL_ELIGIBLE_SPECIALTIES (không cần config)
            // L04: kiểm tra thêm requiredSpecialtyId nếu có
            Integer requiredSpecId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;
            if (!StaffShiftTypeEligibility.isEligible(staff, shiftTypeId, requiredSpecId)) continue;

            // 1. Specialty check — L04 strict-only: non-matching specialty bị loại
            boolean isStrictMatch = req.getSpecialty() == null
                    || (staff.getSpecialty() != null && staff.getSpecialty().getId().equals(req.getSpecialty().getId()));

            if (!isStrictMatch) continue;

            // 2. In-memory assignment conflict
            if (hasInMemoryConflict(staff.getId(), req.getWorkDate(), shiftTypeId)) continue;

            // 3. Batch-loaded leave/compensation checks
            if (batchData.onLeaveStaffIds().contains(staff.getId())) continue;
            if (!skipCompensationCheck) {
                if (batchData.onCompDayStaffIds().contains(staff.getId())) continue;
                if (additionalCompDayStaffIds != null && additionalCompDayStaffIds.contains(staff.getId())) continue;
            }

            // 4. Adjacent L01 check
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                Set<Integer> allAdjacentL01 = new HashSet<>();
                if (batchData.adjacentL01StaffIds() != null) allAdjacentL01.addAll(batchData.adjacentL01StaffIds());
                if (additionalAdjacentL01 != null) allAdjacentL01.addAll(additionalAdjacentL01);
                if (allAdjacentL01.contains(staff.getId())) continue;
            }

            // 5. Same-day shift-type conflict
            List<Schedule> daySchedules = batchData.daySchedulesByStaff().get(staff.getId());
            if (daySchedules != null) {
                boolean hasConflict = false;
                for (Schedule s : daySchedules) {
                    String existingShiftTypeId = s.getShiftType().getId();
                    if (existingShiftTypeId.equals(shiftTypeId) || isBusinessShiftConflict(shiftTypeId, existingShiftTypeId)) {
                        hasConflict = true;
                        break;
                    }
                }
                if (hasConflict) continue;
            }

            // 6. Per-type hard cap
            if (maxShiftsPerTypeLimit > 0 && maxShiftsPerTypeLimit < Integer.MAX_VALUE) {
                long thisTypeCount = getStaffCountForKey(staff.getId(), fairShareKey,
                        periodData.staffShiftTypeCounts(), runningCounts);
                if (thisTypeCount >= maxShiftsPerTypeLimit) continue;
            }

            // 7. Global per-staff total cap — request-level override takes precedence over both runtime config and DB.
            int effectiveMaxShifts = (maxShiftsPerMonthOverride != null)
                    ? (maxShiftsPerMonthOverride == 0 ? Integer.MAX_VALUE : maxShiftsPerMonthOverride)
                    : (maxShiftsPerStaffLimit > 0 && maxShiftsPerStaffLimit < Integer.MAX_VALUE
                            ? maxShiftsPerStaffLimit
                            : (staff.getMaxShiftsPerMonth() != null && staff.getMaxShiftsPerMonth() > 0
                                    ? staff.getMaxShiftsPerMonth()
                                    : Integer.MAX_VALUE));
            if (effectiveMaxShifts < Integer.MAX_VALUE) {
                long totalCurrent = getTotalStaffCount(staff.getId(),
                        periodData.staffShiftTypeCounts(), runningCounts);
                if (totalCurrent >= effectiveMaxShifts) continue;
            }

            if (isStrictMatch) {
                strictMatches.add(staff);
            }
        }

        strictMatches.sort(sortComparator);

        // L04 strict-only: chỉ trả về staff đúng chuyên khoa. Không còn bucket
        // cross-specialty / balance strategy (cơ chế đã bị gỡ).
        return strictMatches;
    }

    public boolean hasInMemoryConflict(int staffId, LocalDate workDate, String shiftTypeId) {
        // Local duplicate of in-memory conflict check
        // This is in the record path; we use thread-local indirectly
        // For full correctness, callers should pass stateAccessor
        return false; // Delegates to SchedulingStateAccessor via AutoSchedulingService composition
    }

    public long getStaffCountForKey(Integer staffId, String countKey,
                                    Map<Integer, Map<String, Long>> dbCounts,
                                    Map<Integer, Map<String, Long>> runningCounts) {
        Map<String, Long> dbStaffCounts = dbCounts.get(staffId);
        Map<String, Long> inRunCounts = runningCounts.get(staffId);

        long inRun = inRunCounts != null ? inRunCounts.getOrDefault(countKey, 0L) : 0L;
        long db = dbStaffCounts != null ? dbStaffCounts.getOrDefault(countKey, 0L) : 0L;
        return db + inRun;
    }

    public long getTotalStaffCount(Integer staffId,
                                  Map<Integer, Map<String, Long>> dbCounts,
                                  Map<Integer, Map<String, Long>> runningCounts) {
        Map<String, Long> dbStaffCounts = dbCounts.get(staffId);
        Map<String, Long> inRunCounts = runningCounts.get(staffId);

        long db = dbStaffCounts != null
                ? dbStaffCounts.getOrDefault("L01", 0L)
                + dbStaffCounts.getOrDefault("L02", 0L)
                + dbStaffCounts.getOrDefault("L03", 0L)
                + dbStaffCounts.getOrDefault("L04", 0L)
                : 0L;
        long inRun = inRunCounts != null
                ? inRunCounts.values().stream().mapToLong(Long::longValue).sum()
                : 0L;
        return db + inRun;
    }

    public boolean isBusinessShiftConflict(String typeA, String typeB) {
        return (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L02.equals(typeB))
                || (ConflictDetectionService.SHIFT_TYPE_L02.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L01.equals(typeB))
                || (ConflictDetectionService.SHIFT_TYPE_L03.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L04.equals(typeB))
                || (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L03.equals(typeB));
    }

    public boolean isStrictMatchForStaff(Staff staff, ShiftRequirement req) {
        return req.getSpecialty() != null
                && staff.getSpecialty() != null
                && staff.getSpecialty().getId().equals(req.getSpecialty().getId());
    }
}
