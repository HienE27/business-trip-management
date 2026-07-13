package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure-function helpers used by the Greedy algorithm. Extracted from
 * {@code AutoSchedulingService} during the M07 refactor so the algorithm
 * body itself can move to {@link GreedyAssignmentEngine} without pulling in
 * heavy orchestrator dependencies.
 *
 * <p>All methods in this class are deterministic and stateless — they only
 * read from their input arguments.
 */
@Slf4j
public final class GreedyAssignmentEngine {

    private GreedyAssignmentEngine() {}

    /**
     * Default ratio used when the algorithm config is absent.
     */
    public static final float DEFAULT_L04_CROSS_RATIO = 0.3f;

    /**
     * Group requirements by work date.
     */
    public static Map<LocalDate, List<ShiftRequirement>> groupRequirementsByDate(List<ShiftRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) return Collections.emptyMap();
        return requirements.stream().collect(Collectors.groupingBy(ShiftRequirement::getWorkDate));
    }

    /**
     * Sort requirements L01 → L02 → L03 → L04 so the algorithm reserves
     * compensation days before assigning non-overnight shifts.
     */
    public static List<ShiftRequirement> sortRequirementsByPriority(List<ShiftRequirement> reqs) {
        if (reqs == null || reqs.isEmpty()) return reqs;
        return reqs.stream()
                .sorted(Comparator.comparingInt((ShiftRequirement r) -> {
                    String id = r.getShiftType().getId();
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(id)) return 0;
                    if (ConflictDetectionService.SHIFT_TYPE_L02.equals(id)) return 1;
                    if (ConflictDetectionService.SHIFT_TYPE_L03.equals(id)) return 2;
                    if (ConflictDetectionService.SHIFT_TYPE_L04.equals(id)) return 3;
                    return 4;
                }))
                .collect(Collectors.toList());
    }

    /**
     * Compute per-shift-type fair-share = ceil(totalDemand[type] / pool).
     * L04 uses per-specialty keys like "L04:5" so fairness can be enforced
     * independently within each specialty (M05).
     */
    public static Map<String, Integer> computeFairSharePerTypeWithStaff(
            List<ShiftRequirement> requirements, int staffPool, List<Staff> activeStaff) {
        Map<String, Integer> result = new HashMap<>();
        if (requirements == null || requirements.isEmpty()) {
            return Map.of(
                    ConflictDetectionService.SHIFT_TYPE_L01, 1,
                    ConflictDetectionService.SHIFT_TYPE_L02, 1,
                    ConflictDetectionService.SHIFT_TYPE_L03, 1,
                    ConflictDetectionService.SHIFT_TYPE_L04, 1
            );
        }
        List<Staff> safeActiveStaff = (activeStaff == null || activeStaff.isEmpty()) ? List.of() : activeStaff;
        int safePool = Math.max(1, safeActiveStaff.isEmpty() ? staffPool : safeActiveStaff.size());

        for (String typeId : List.of(
                ConflictDetectionService.SHIFT_TYPE_L01,
                ConflictDetectionService.SHIFT_TYPE_L02,
                ConflictDetectionService.SHIFT_TYPE_L03,
                ConflictDetectionService.SHIFT_TYPE_L04)) {

            int totalDemand = requirements.stream()
                    .filter(r -> typeId.equals(r.getShiftType().getId()))
                    .mapToInt(ShiftRequirement::getRequiredStaffCount)
                    .sum();

            int fairShare = totalDemand > 0
                    ? Math.min(totalDemand, Math.max(1, (int) Math.ceil((double) totalDemand / safePool)))
                    : 1;
            result.put(typeId, fairShare);

            if (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeId)) {
                Set<Integer> l04SpecIds = requirements.stream()
                        .filter(r -> typeId.equals(r.getShiftType().getId()) && r.getSpecialty() != null)
                        .map(r -> r.getSpecialty().getId())
                        .collect(Collectors.toSet());
                for (Integer specId : l04SpecIds) {
                    int specDemand = requirements.stream()
                            .filter(r -> typeId.equals(r.getShiftType().getId())
                                    && r.getSpecialty() != null
                                    && specId.equals(r.getSpecialty().getId()))
                            .mapToInt(ShiftRequirement::getRequiredStaffCount)
                            .sum();
                    long specPool = safeActiveStaff.stream()
                            .filter(s -> s.getSpecialty() != null && specId.equals(s.getSpecialty().getId()))
                            .count();
                    int specFairShare = specDemand > 0
                            ? Math.min(specDemand, Math.max(1, (int) Math.ceil((double) specDemand / Math.max(1, specPool))))
                            : 1;
                    result.put("L04:" + specId, specFairShare);
                }
            }
        }
        return result;
    }

    /**
     * Per-type shift count, merging DB counts with in-memory counts.
     * L04 with specialty uses "L04:specialtyId" key + DB-level L04 baseline.
     */
    public static long getStaffCountForKey(Integer staffId, String countKey,
                                           Map<Integer, Map<String, Long>> dbCounts,
                                           Map<Integer, Map<String, Long>> runningCounts) {
        Map<String, Long> dbStaffCounts = dbCounts.get(staffId);
        Map<String, Long> inRunCounts = runningCounts.get(staffId);

        long inRun = inRunCounts != null ? inRunCounts.getOrDefault(countKey, 0L) : 0L;
        if (countKey.startsWith("L04:")) {
            long db = dbStaffCounts != null ? dbStaffCounts.getOrDefault("L04", 0L) : 0L;
            return db + inRun;
        }
        long db = dbStaffCounts != null ? dbStaffCounts.getOrDefault(countKey, 0L) : 0L;
        return db + inRun;
    }

    /**
     * Sum of all shift counts for a staff (across L01-L04) from DB + in-memory.
     */
    public static long getTotalStaffCount(Integer staffId,
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
}
