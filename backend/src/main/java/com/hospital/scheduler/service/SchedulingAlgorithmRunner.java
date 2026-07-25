package com.hospital.scheduler.service;

import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.repository.LeaveRequestRepository;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.util.CompensationDateCalculator;
import com.hospital.scheduler.util.DateUtils;
import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Plain class (NOT a Spring bean) that holds the scheduling algorithm methods
 * extracted from AutoSchedulingService. Instantiated via @PostConstruct in
 * AutoSchedulingService.
 */
@Slf4j
public class SchedulingAlgorithmRunner {

    private final AutoSchedulingService autoSchedulingService;
    private final ScheduleRepository scheduleRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final CompensationDateCalculator compensationDateCalculator;

    public SchedulingAlgorithmRunner(AutoSchedulingService autoSchedulingService,
                                     ScheduleRepository scheduleRepository,
                                     LeaveRequestRepository leaveRequestRepository,
                                     CompensationDateCalculator compensationDateCalculator) {
        this.autoSchedulingService = autoSchedulingService;
        this.scheduleRepository = scheduleRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.compensationDateCalculator = compensationDateCalculator;
    }

    // ==================== INNER RECORDS ====================

    private record RebalanceMove(Schedule schedule, Staff toStaff) {}

    static record CrossSpecialtyConfig(boolean enabled, float ratio, List<String> allowedSpecialties) {}

    // ==================== GREEDY ALGORITHM ====================
    public List<Schedule> runGreedy(SchedulePeriod period, List<ShiftRequirement> requirements,
                                     List<Staff> activeStaff, boolean save,
                                     AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
                                     Set<Integer> excludedStaffIds) {
        List<Schedule> createdSchedules = new ArrayList<>();
        Map<LocalDate, List<ShiftRequirement>> requirementsByDate = autoSchedulingService.groupRequirementsByDate(requirements);

        // OPTIMIZATION 1: Load all conflict data for entire period in ONE pass (instead of per-day)
        // OPTIMIZATION 2: Load all shift type counts in ONE query (instead of Nx4 queries)
        AutoSchedulingService.PeriodConflictData periodData = autoSchedulingService.loadPeriodConflictData(period, requirements, activeStaff,
                runtimeConfig != null ? runtimeConfig.getL01AdjacentDayWindow() : 1);

        // FAIRNESS: Pre-compute fair share per shift type = ceil(totalDemand[type] / eligiblePool)
        // L04 uses per-specialty pool (spec M05); L01/L02/L03 use full staffPool.
        int staffPool = Math.max(1, activeStaff.size());
        Map<String, Integer> fairSharePerType = autoSchedulingService.computeFairSharePerTypeWithStaff(requirements, staffPool, activeStaff);

        // greedy_coverage_threshold: track coverage as we go; process ALL requirements (no early return)
        // The threshold is tracked for logging purposes only - we always fill every requirement
        int totalRequired = requirements.stream()
                .mapToInt(com.hospital.scheduler.entity.ShiftRequirement::getRequiredStaffCount)
                .sum();
        final int coverageTarget = (int) Math.ceil(totalRequired * runtimeConfig.getGreedyCoverageThreshold().doubleValue());

        // Track L01 assignments by date for adjacent-day back-to-back checking
        Map<LocalDate, Set<Integer>> l01AssignmentsByDate = new HashMap<>();

        // Track compensation days created during this run to prevent assignments on those days
        // When L01 is created on day N, staff cannot work any shift on their compensation day
        Map<LocalDate, Set<Integer>> compensationDaysByDate = new HashMap<>();

        // Track assignments created during this run so fairness decisions see current preview load.
        // Keys are plain shift type (L01/L02/L03) or L04 per-specialty (L04:<specialtyId>).
        Map<Integer, Map<String, Long>> greedyRunningCounts = new HashMap<>();

        // Track per-type weekly counts for enforcing l0XMaxPerWeek (per-type weekly cap from config).
        // Key: staffId, Value: Map<shiftTypeId, weeklyCount>
        Map<Integer, Map<String, Integer>> greedyWeeklyCounts = new HashMap<>();
        // Track which ISO week we're currently in to reset counts when week changes
        LocalDate currentDate = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();
        int currentWeekNumber = currentDate.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        int currentWeekYear = currentDate.get(java.time.temporal.WeekFields.ISO.weekBasedYear());
        // FAIRNESS: Fair-greedy rotation index per shift type so each staff rotates through shift types evenly.
        // Without this, the same staff keep being picked for L01 until they hit maxShiftsPerStaff, leaving others with 0 L01.
        final Map<String, Map<Integer, Integer>> shiftTypeRotationIndex = new HashMap<>();
        while (!currentDate.isAfter(periodEnd)) {
            // Check if we've moved to a new week — reset weekly counts for l0XMaxPerWeek enforcement
            int newWeekNumber = currentDate.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            int newWeekYear = currentDate.get(java.time.temporal.WeekFields.ISO.weekBasedYear());
            if (newWeekNumber != currentWeekNumber || newWeekYear != currentWeekYear) {
                // New week: clear all weekly counts so enforcement starts fresh
                greedyWeeklyCounts.clear();
                currentWeekNumber = newWeekNumber;
                currentWeekYear = newWeekYear;
            }

            List<ShiftRequirement> todayReqs = autoSchedulingService.sortRequirementsByPriority(
                    requirementsByDate.getOrDefault(currentDate, Collections.emptyList()));

            AutoSchedulingService.BatchConflictData todayConflicts = periodData.byDate().get(currentDate);

            // Merge DB adjacent L01 with batch-assigned L01 from this greedy run
            // window = ceil(overnightRecoveryHours/24) — L01 cấm L01 trong ±l01Window ngày
            int l01Window = runtimeConfig != null ? runtimeConfig.getL01AdjacentDayWindow() : 1;
            Set<Integer> adjacentL01FromPrev = new HashSet<>();
            for (int dt = 1; dt <= l01Window; dt++) {
                Set<Integer> fromBatch = l01AssignmentsByDate.get(currentDate.minusDays(dt));
                if (fromBatch != null) adjacentL01FromPrev.addAll(fromBatch);
            }
            if (todayConflicts != null && todayConflicts.adjacentL01StaffIds() != null) {
                adjacentL01FromPrev.addAll(todayConflicts.adjacentL01StaffIds());
            }

            // Get compensation days for today (created from earlier days in this run)
            Set<Integer> todayCompDayStaffIds = compensationDaysByDate.getOrDefault(currentDate, Collections.emptySet());

            Set<Integer> assignedStaffIds = new HashSet<>();
            for (ShiftRequirement req : todayReqs) {
                // NOTE: L01 can appear multiple times in todayReqs (separate ShiftRequirement entries).
                // We do NOT skip subsequent L01 requirements here — the filterAndSortEligibleStaffBatch
                // will handle it by checking assignedStaffIds (same staff can't be assigned to 2 L01 slots)
                // and the L01-specific conflicts (adjacent days, compensation days) are checked in the filter.

                final LocalDate workDate = currentDate;
                final String shiftTypeId = req.getShiftType().getId();
                final boolean isWeekend = currentDate.getDayOfWeek() == DayOfWeek.SATURDAY
                        || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY;

                // Calculate running stats for balance scoring
                int totalAssigned = createdSchedules.size();
                int staffWithWork = periodData.staffShiftTypeCounts().size();
                double avgPerStaff = staffWithWork > 0 ? (double) totalAssigned / staffWithWork : 0;

                // FAIRNESS: Fair-greedy rotation index per shift type so each staff rotates through shift types evenly.
                // Without this, the same staff keep being picked for L01 until they hit maxShiftsPerStaff, leaving others with 0 L01.
                final Map<Integer, Integer> rotationForType = shiftTypeRotationIndex.computeIfAbsent(
                        shiftTypeId, k -> new HashMap<>());

                // Per-type cap derived from actual demand: ceil(demand[type] / staffPool).
                // For L04 with a specialty, use per-specialty key "L04:specialtyId" for accurate fairness.
                final String fairShareKey = (ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId) && req.getSpecialty() != null)
                        ? "L04:" + req.getSpecialty().getId()
                        : shiftTypeId;
                final int fairShare = fairSharePerType.getOrDefault(fairShareKey, fairSharePerType.getOrDefault(shiftTypeId, 1));
                // Hard cap: ALL types get a large buffer to ensure EVEN distribution.
                // Key insight: we want EVERY staff to get at least 1 of each type before caps apply.
                // So cap should be generous enough to allow rotation through all staff.
                // For L04 with cross-specialty, calculate buffer proportionally to crossConfig.ratio().
                // E.g., ratio=0.3 -> buffer = fairShare * 0.5 = 50% more slots.
                int capBuffer = 1;
                if (ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId) && req.getSpecialty() != null) {
                    var crossConfig = autoSchedulingService.getL04CrossSpecialtyConfig();
                    if (crossConfig.enabled()) {
                        // Cross-specialty enabled: allow more assignments to fill from other specialties
                        capBuffer = Math.max(1, (int) Math.ceil(fairShare * 0.5));
                    }
                }
                // For non-L04 or L04 without cross-specialty: generous buffer = fairShare itself
                // This ensures we can assign to all eligible staff before hitting the cap
                if (capBuffer == 1) {
                    capBuffer = Math.max(1, (int) Math.ceil(fairShare * 0.5));
                }
                final int shiftTypeSpecificMax = fairShare + capBuffer;
                // Soft cap for comparator: deprioritize (don't block) staff who exceed this many for THIS type.
                // Set to fairShare+1 so staff who already did their fair share are deprioritized but still
                // considered as a last resort if understaffed.
                final int softCapPerType = fairShare + (capBuffer / 2);

                // Fairness comparator — guarantees even per-type AND overall distribution.
                // For L04, comparator uses per-specialty count key to prevent cross-specialty interference.
                final String capturedFairShareKey = fairShareKey;
                final Map<Integer, Map<String, Long>> capturedRunningCounts = greedyRunningCounts;
                final double targetAvgPerStaff = avgPerStaff;
                Comparator<Staff> fairnessComparator = Comparator
                        // Tier 1: SWAP PRIORITY — honour pending swap requests
                        .comparingDouble((Staff s) -> autoSchedulingService.swapPriorityStaffIds.get().contains(s.getId()) ? 0.0 : 1.0)
                        // Tier 2: MINIMUM GUARANTEE per type — staff with 0 of this type get TOP priority.
                        // This ensures EVERY staff gets at least 1 shift of each type before caps apply.
                        // Only deprioritize if staff already has >= softCapPerType AND softCapPerType >= 1.
                        .thenComparingInt((Staff s) -> {
                            long typeCount = autoSchedulingService.getStaffCountForKey(s.getId(), capturedFairShareKey,
                                    periodData.staffShiftTypeCounts(), capturedRunningCounts);
                            if (typeCount == 0) {
                                return 0; // TOP priority: staff needs at least 1 of this type
                            }
                            // Soft cap: deprioritize if already at soft cap
                            return typeCount >= softCapPerType ? 1 : 0;
                        })
                        // Tier 3: Fewest of THIS shift type/specialty — primary per-type fairness signal
                        .thenComparingLong((Staff s) -> {
                            return autoSchedulingService.getStaffCountForKey(s.getId(), capturedFairShareKey,
                                    periodData.staffShiftTypeCounts(), capturedRunningCounts);
                        })
                        // Tier 4 (stronger): Penalty for staff whose total shifts exceed the running average.
                        // Squared term amplifies imbalance so the algorithm aggressively prefers under-loaded staff.
                        .thenComparingDouble(s -> {
                            Map<String, Long> counts = periodData.staffShiftTypeCounts().get(s.getId());
                            long totalShifts = counts != null
                                    ? counts.getOrDefault("L01", 0L) + counts.getOrDefault("L02", 0L)
                                            + counts.getOrDefault("L03", 0L) + counts.getOrDefault("L04", 0L)
                                    : 0L;
                            double dev = totalShifts - targetAvgPerStaff;
                            return dev * dev; // squared deviation: prioritize staff most below average
                        })
                        // Tier 5: Fewest total shifts — overall balance tiebreak
                        .thenComparingLong(s -> {
                            Map<String, Long> counts = periodData.staffShiftTypeCounts().get(s.getId());
                            if (counts == null) return 0L;
                            return counts.getOrDefault("L01", 0L) + counts.getOrDefault("L02", 0L)
                                    + counts.getOrDefault("L03", 0L) + counts.getOrDefault("L04", 0L);
                        })
                        // Tier 6: Weekend penalty — penalize staff with many shifts on weekends
                        .thenComparingDouble(s -> {
                            if (!isWeekend) return 0.0;
                            Map<String, Long> counts = periodData.staffShiftTypeCounts().get(s.getId());
                            long totalShifts = counts != null
                                    ? counts.getOrDefault("L01", 0L) + counts.getOrDefault("L02", 0L)
                                            + counts.getOrDefault("L03", 0L) + counts.getOrDefault("L04", 0L)
                                    : 0L;
                            return totalShifts * runtimeConfig.getWeekendWeight().doubleValue();
                        });

                List<Staff> eligibleStaff = autoSchedulingService.filterAndSortEligibleStaffBatch(
                        activeStaff, req, excludedStaffIds, assignedStaffIds, todayConflicts, !save,
                        fairnessComparator, periodData, adjacentL01FromPrev, todayCompDayStaffIds,
                        runtimeConfig.getMaxShiftsPerStaff() > 0 ? runtimeConfig.getMaxShiftsPerStaff() : Integer.MAX_VALUE,
                        shiftTypeSpecificMax, fairShareKey, greedyRunningCounts, greedyWeeklyCounts, runtimeConfig, activeStaff);

                // DEBUG: Log L04 fairShare info
                if (log.isDebugEnabled() && ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)) {
                    String specialtyId = req.getSpecialty() != null ? String.valueOf(req.getSpecialty().getId()) : "null";
                    log.debug("L04 DEBUG: date={} specialty={} required={} eligible={} fairShare={} cap={}",
                        workDate, specialtyId, req.getRequiredStaffCount(), eligibleStaff.size(),
                        fairShare, shiftTypeSpecificMax);
                }

                // FALLBACK: If hard per-type cap blocks all candidates (demand > capacity),
                // relax to fairShare*2 so coverage stays at 100% while still distributing load.
                if (eligibleStaff.isEmpty()) {
                    eligibleStaff = autoSchedulingService.filterAndSortEligibleStaffBatch(
                            activeStaff, req, excludedStaffIds, assignedStaffIds, todayConflicts, !save,
                            fairnessComparator, periodData, adjacentL01FromPrev, todayCompDayStaffIds,
                            Integer.MAX_VALUE,
                            fairShare * 5, fairShareKey, greedyRunningCounts, greedyWeeklyCounts, runtimeConfig, activeStaff);
                    if (!eligibleStaff.isEmpty()) {
                        log.debug("Greedy fallback cap: date={} type={} relaxed to fairShare*5={}",
                                workDate, shiftTypeId, fairShare * 5);
                    }
                }

                // maxStaffPerShift: cap assignments at the limit, but still try to meet requiredStaffCount
                int effectiveMax = runtimeConfig.getMaxStaffPerShift() > 0
                        ? Math.min(runtimeConfig.getMaxStaffPerShift(), req.getRequiredStaffCount())
                        : req.getRequiredStaffCount();
                int toAssign = Math.min(effectiveMax, eligibleStaff.size());
                if (log.isInfoEnabled()) {
                    log.info("=== GREEDY PROCESSING === date={} type={} required={} eligible={} toAssign={}",
                        workDate, shiftTypeId, req.getRequiredStaffCount(), eligibleStaff.size(), toAssign);
                    log.info("  fairShare={} capBuffer={} shiftTypeSpecificMax={} softCapPerType={}",
                        fairShare, capBuffer, shiftTypeSpecificMax, softCapPerType);
                }

                if (log.isInfoEnabled() && eligibleStaff.size() < req.getRequiredStaffCount()) {
                    log.warn("UNDERSTAFFED: date={} req={} eligible={} required={} assigned={}",
                        workDate, shiftTypeId, eligibleStaff.size(), req.getRequiredStaffCount(), toAssign);
                }
                if (log.isInfoEnabled() && ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId()) && eligibleStaff.size() < req.getRequiredStaffCount()) {
                    log.warn("Greedy L01 UNDERSTAFFED: date={} required={} eligible={} (adjPrev={} may be blocking too many)",
                        workDate, req.getRequiredStaffCount(), eligibleStaff.size(), adjacentL01FromPrev.size());
                }
                if (log.isDebugEnabled()) {
                    log.debug("runGreedy date={} req={} eligible={} toAssign={} assignedSoFar={}",
                        workDate, req.getShiftType().getId(), eligibleStaff.size(), toAssign, assignedStaffIds.size());
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        log.debug("  L01 assignment: date={} adjacentL01FromPrev={}", workDate, adjacentL01FromPrev);
                    }
                } else if (log.isInfoEnabled() && ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                    log.info("Greedy L01: date={} eligible={} toAssign={} adjPrev={}",
                        workDate, eligibleStaff.size(), toAssign, adjacentL01FromPrev.size());
                }
                int assignedCount = 0;
                int staffIndex = 0;
                while (assignedCount < toAssign && staffIndex < eligibleStaff.size()) {
                    Staff staff = eligibleStaff.get(staffIndex);
                    staffIndex++;
                    Schedule saved = autoSchedulingService.buildAndSaveSchedule(period, staff, req, workDate, save, createdSchedules);
                    if (saved == null) continue;
                    // DEBUG: verify adjacentL01 blocking worked for L01 assignments
                    if (log.isInfoEnabled() && ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        log.info("Greedy L01 SAVED: staff={} date={} (adjPrev={} blocked)", staff.getId(), workDate, adjacentL01FromPrev.size());
                    }
                    autoSchedulingService.trackAssignment(staff, workDate, req.getShiftType().getId());
                    // Update weekly count for this shift type (for l0XMaxPerWeek enforcement)
                    greedyWeeklyCounts.computeIfAbsent(staff.getId(), k -> new HashMap<>())
                            .merge(shiftTypeId, 1, Integer::sum);
                    assignedStaffIds.add(staff.getId());
                    assignedCount++;
                    // Update rotation index for this shift type so next time a different staff gets priority
                    rotationForType.merge(staff.getId(), 1, Integer::sum);

                    // greedy_coverage_threshold: log when target coverage is reached (but keep processing all requirements)
                    if (createdSchedules.size() >= coverageTarget) {
                        log.info("Greedy coverage threshold reached: {}/{} = {}% (target: {}%) - continuing to fill remaining requirements",
                                createdSchedules.size(), totalRequired,
                                String.format("%.2f", (double) createdSchedules.size() / totalRequired * 100),
                                String.format("%.0f", runtimeConfig.getGreedyCoverageThreshold().doubleValue() * 100));
                    }

                    // Track L01 assignment for adjacent-day back-to-back checking
                    // (The l01AssignedToday flag was REMOVED — L01 re-entry is allowed when requiredStaffCount > 1)
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        // Track for adjacent-day back-to-back check
                        l01AssignmentsByDate.computeIfAbsent(workDate, k -> new HashSet<>()).add(staff.getId());
                        // Track compensation day - staff cannot work any shift on their compensation day
                        LocalDate compDate = compensationDateCalculator.calculate(workDate);
                        if (compDate != null && !compDate.isBefore(period.getStartDate()) && !compDate.isAfter(period.getEndDate())) {
                            compensationDaysByDate.computeIfAbsent(compDate, k -> new HashSet<>()).add(staff.getId());
                        }
                    }
                    String runningCountKey = (ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId) && req.getSpecialty() != null)
                            ? "L04:" + req.getSpecialty().getId()
                            : shiftTypeId;
                    greedyRunningCounts
                            .computeIfAbsent(staff.getId(), k -> new HashMap<>())
                            .merge(runningCountKey, 1L, Long::sum);

                    if (save && ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        log.debug("Creating compensation day for auto-scheduled L01: staff={}, date={}", staff.getId(), workDate);
                        autoSchedulingService.createCompensationDayForAuto(saved);
                    }
                }
            }
            currentDate = currentDate.plusDays(1);
        }
        return createdSchedules;
    }


    // ==================== LOCAL SEARCH FAIRNESS OPTIMIZER ====================
    public int optimizeFairnessBySafeReassignment(List<Schedule> schedules,
                                                   List<Staff> activeStaff,
                                                   List<ShiftRequirement> requirements,
                                                   int maxRounds) {
        if (schedules == null || schedules.isEmpty() || activeStaff == null || activeStaff.isEmpty()) {
            return 0;
        }

        Map<Integer, Staff> staffById = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s, (a, b) -> a));
        int moves = 0;

        for (int round = 0; round < maxRounds; round++) {
            Map<String, Map<Integer, Long>> counts = buildSafeRebalanceCounts(schedules, activeStaff);
            RebalanceMove move = findBestSafeRebalanceMove(schedules, activeStaff, staffById, counts);
            if (move == null) {
                break;
            }

            move.schedule().setStaff(move.toStaff());
            if (move.schedule().getId() != null) {
                scheduleRepository.save(move.schedule());
            }
            moves++;
        }

        return moves;
    }

    /**
     * HARD GUARANTEE: Ensure every active staff gets at least 1 shift.
     * This addresses the fairness issue where some staff get 0 assignments.
     * Strategy: Find days with unfilled requirements and assign unassigned staff there.
     */
    public int guaranteeMinimumShifts(List<Schedule> schedules,
                                       List<Staff> staffWithoutShifts,
                                       List<ShiftRequirement> requirements,
                                       List<Staff> activeStaff) {
        if (staffWithoutShifts == null || staffWithoutShifts.isEmpty()) {
            return 0;
        }

        int fixed = 0;
        Map<Integer, Staff> staffMap = activeStaff.stream()
                .collect(Collectors.toMap(Staff::getId, s -> s, (a, b) -> a));

        // Build current assignments map: date -> set of assigned staff
        Map<LocalDate, Set<Integer>> assignedByDate = new HashMap<>();
        Map<LocalDate, Map<String, Set<Integer>>> assignedByDateAndType = new HashMap<>();
        for (Schedule s : schedules) {
            LocalDate date = s.getWorkDate();
            assignedByDate.computeIfAbsent(date, k -> new HashSet<>()).add(s.getStaff().getId());
            assignedByDateAndType.computeIfAbsent(date, k -> new HashMap<>())
                    .computeIfAbsent(s.getShiftType().getId(), k -> new HashSet<>())
                    .add(s.getStaff().getId());
        }

        // Find unfilled requirements by date and type
        Map<LocalDate, Map<String, Integer>> unfilledByDateAndType = new HashMap<>();
        for (ShiftRequirement req : requirements) {
            LocalDate date = req.getWorkDate();
            String typeId = req.getShiftType().getId();
            Set<Integer> assigned = assignedByDateAndType.getOrDefault(date, Map.of())
                    .getOrDefault(typeId, Set.of());
            int needed = Math.max(0, req.getRequiredStaffCount() - assigned.size());
            if (needed > 0) {
                unfilledByDateAndType.computeIfAbsent(date, k -> new HashMap<>())
                        .put(typeId, needed);
            }
        }

        // For each staff without shifts, find any eligible unfilled slot
        for (Staff staff : staffWithoutShifts) {
            boolean assigned = false;

            // Try each unfilled date/type
            for (Map.Entry<LocalDate, Map<String, Integer>> dateEntry : unfilledByDateAndType.entrySet()) {
                if (assigned) break;

                LocalDate date = dateEntry.getKey();
                for (Map.Entry<String, Integer> typeEntry : dateEntry.getValue().entrySet()) {
                    if (assigned) break;
                    if (typeEntry.getValue() <= 0) continue;

                    String typeId = typeEntry.getKey();

                    // Check if staff is eligible for this type
                    Integer specId = requirements.stream()
                            .filter(r -> r.getWorkDate().equals(date) && r.getShiftType().getId().equals(typeId))
                            .findFirst()
                            .map(r -> r.getSpecialty() != null ? r.getSpecialty().getId() : null)
                            .orElse(null);

                    if (!StaffShiftTypeEligibility.isEligible(staff, typeId, specId)) {
                        continue;
                    }

                    // Check for conflicts
                    // 1. Already has shift that day
                    if (assignedByDate.getOrDefault(date, Set.of()).contains(staff.getId())) {
                        continue;
                    }
                    // 2. Business conflict (L01 vs L02, L03 vs L04)
                    boolean hasConflict = false;
                    Set<String> existingTypes = assignedByDateAndType.getOrDefault(date, Map.of()).keySet();
                    for (String existingType : existingTypes) {
                        if (isBusinessShiftConflict(typeId, existingType)) {
                            hasConflict = true;
                            break;
                        }
                    }
                    if (hasConflict) continue;

                    // Check L01 adjacent constraint (no back-to-back L01 within l01Window days)
                    // guaranteeMinimumShifts là safety-net → dùng window mặc định 1 (tương thích ngược)
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeId)) {
                        boolean hasAdjL01 = false;
                        for (int dt = 1; dt <= 1 && !hasAdjL01; dt++) {
                            LocalDate adjDate = date.minusDays(dt);
                            if (assignedByDate.getOrDefault(adjDate, Set.of()).contains(staff.getId())) {
                                hasAdjL01 = checkAdjacentL01(schedules, staff.getId(), adjDate);
                            }
                            if (!hasAdjL01) {
                                adjDate = date.plusDays(dt);
                                if (assignedByDate.getOrDefault(adjDate, Set.of()).contains(staff.getId())) {
                                    hasAdjL01 = checkAdjacentL01(schedules, staff.getId(), adjDate);
                                }
                            }
                        }
                        if (hasAdjL01) continue;
                    }

                    // ASSIGN THIS STAFF
                    ShiftRequirement req = requirements.stream()
                            .filter(r -> r.getWorkDate().equals(date) && r.getShiftType().getId().equals(typeId))
                            .findFirst()
                            .orElse(null);

                    if (req != null) {
                        Schedule newSchedule = buildNewSchedule(staff, req, date);
                        schedules.add(newSchedule);

                        // Update tracking maps
                        assignedByDate.computeIfAbsent(date, k -> new HashSet<>()).add(staff.getId());
                        assignedByDateAndType.computeIfAbsent(date, k -> new HashMap<>())
                                .computeIfAbsent(typeId, k -> new HashSet<>())
                                .add(staff.getId());
                        typeEntry.setValue(typeEntry.getValue() - 1);

                        log.info("HARD GUARANTEE: Assigned staff {} to {} on {} (type={})",
                                staff.getId(), typeId, date, typeId);
                        fixed++;
                        assigned = true;
                    }
                }
            }
        }

        return fixed;
    }

    public Schedule buildNewSchedule(Staff staff, ShiftRequirement req, LocalDate workDate) {
        Schedule schedule = Schedule.builder()
                .staff(staff)
                .shiftType(req.getShiftType())
                .workDate(workDate)
                .period(req.getPeriod())
                .requirement(req)
                .hasConflict(false)
                .isPreview(false)
                .build();
        return schedule;
    }

    public Map<String, Map<Integer, Long>> buildSafeRebalanceCounts(List<Schedule> schedules, List<Staff> activeStaff) {
        Map<String, Map<Integer, Long>> counts = new LinkedHashMap<>();
        for (Schedule schedule : schedules) {
            String key = rebalanceKey(schedule);
            counts.computeIfAbsent(key, k -> new HashMap<>())
                    .merge(schedule.getStaff().getId(), 1L, Long::sum);
        }

        for (String key : new ArrayList<>(counts.keySet())) {
            Set<Integer> pool = eligiblePoolForRebalanceKey(key, activeStaff);
            for (Integer staffId : pool) {
                counts.get(key).putIfAbsent(staffId, 0L);
            }
        }
        return counts;
    }

    public RebalanceMove findBestSafeRebalanceMove(List<Schedule> schedules,
                                                    List<Staff> activeStaff,
                                                    Map<Integer, Staff> staffById,
                                                    Map<String, Map<Integer, Long>> counts) {
        RebalanceMove best = null;
        long bestGap = 1;

        for (Map.Entry<String, Map<Integer, Long>> entry : counts.entrySet()) {
            String key = entry.getKey();
            Map<Integer, Long> perStaff = entry.getValue();
            if (perStaff.isEmpty()) continue;

            Integer overloadedStaffId = perStaff.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            Integer underloadedStaffId = perStaff.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (overloadedStaffId == null || underloadedStaffId == null || overloadedStaffId.equals(underloadedStaffId)) {
                continue;
            }

            long gap = perStaff.getOrDefault(overloadedStaffId, 0L) - perStaff.getOrDefault(underloadedStaffId, 0L);
            if (gap <= bestGap) {
                continue;
            }

            Staff toStaff = staffById.get(underloadedStaffId);
            if (toStaff == null) {
                continue;
            }

            Optional<Schedule> movable = schedules.stream()
                    .filter(s -> overloadedStaffId.equals(s.getStaff().getId()))
                    .filter(s -> key.equals(rebalanceKey(s)))
                    .filter(s -> isSafeLocalSearchReassignment(s, toStaff, schedules))
                    .findFirst();

            if (movable.isPresent()) {
                best = new RebalanceMove(movable.get(), toStaff);
                bestGap = gap;
            }
        }

        return best;
    }

    public boolean isSafeLocalSearchReassignment(Schedule schedule, Staff candidate, List<Schedule> schedules) {
        String typeId = schedule.getShiftType().getId();
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeId)) {
            return false;
        }
        if (candidate == null || candidate.getId().equals(schedule.getStaff().getId())) {
            return false;
        }
        if (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeId)
                && schedule.getRequirement() != null
                && schedule.getRequirement().getSpecialty() != null
                && !isStrictMatchForStaff(candidate, schedule.getRequirement())) {
            return false;
        }

        LocalDate workDate = schedule.getWorkDate();
        String compKey = candidate.getId() + "_" + workDate;
        if (autoSchedulingService.compensationDayAutoService.isInCache(compKey)
                || autoSchedulingService.inMemoryCompensationShiftDates.get().contains(compKey)) {
            return false;
        }

        boolean hasApprovedLeave = leaveRequestRepository
                .findByStaffIdAndDateRange(candidate.getId(), workDate, workDate)
                .stream()
                .anyMatch(lr -> lr.getStatus() == LeaveRequest.LeaveStatus.APPROVED);
        if (hasApprovedLeave) {
            return false;
        }

        for (Schedule existing : schedules) {
            if (existing == schedule) continue;
            if (!candidate.getId().equals(existing.getStaff().getId())) continue;

            String existingType = existing.getShiftType().getId();
            if (workDate.equals(existing.getWorkDate())) {
                if (existingType.equals(typeId)) return false;
                if (isBusinessShiftConflict(typeId, existingType)) return false;
                if (ConflictDetectionService.SHIFT_TYPE_L01.equals(existingType)) return false;
            }

            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(existingType)
                    && existing.getWorkDate().equals(workDate.minusDays(1))) {
                return false;
            }
        }

        return true;
    }

    public Set<Integer> eligiblePoolForRebalanceKey(String key, List<Staff> activeStaff) {
        if (key.startsWith(ConflictDetectionService.SHIFT_TYPE_L04 + ":")) {
            Integer specialtyId = Integer.parseInt(key.substring(key.indexOf(':') + 1));
            return activeStaff.stream()
                    .filter(s -> s.getSpecialty() != null && specialtyId.equals(s.getSpecialty().getId()))
                    .map(Staff::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        // L02/L03: chi Bac si / Dieu duong (KTV/Duoc si khong eligible).
        String shiftTypeId = key.startsWith("L0") ? key.substring(0, 3) : key;
        Integer requiredSpecId = null; // L02/L03 khong yeu cau specialty cu the
        return activeStaff.stream()
                .filter(s -> com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility
                        .isEligible(s, shiftTypeId, requiredSpecId))
                .map(Staff::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String rebalanceKey(Schedule schedule) {
        String typeId = schedule.getShiftType().getId();
        if (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeId)
                && schedule.getRequirement() != null
                && schedule.getRequirement().getSpecialty() != null) {
            return typeId + ":" + schedule.getRequirement().getSpecialty().getId();
        }
        return typeId;
    }

    public boolean isBusinessShiftConflict(String typeA, String typeB) {
        return com.hospital.scheduler.algorithm.ScheduleConflictUtils.isBusinessConflict(typeA, typeB);
    }

    public List<ShiftRequirement> sortRequirementsByPriority(List<ShiftRequirement> requirements) {
        return requirements.stream()
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

    public Map<LocalDate, List<ShiftRequirement>> groupRequirementsByDate(List<ShiftRequirement> requirements) {
        return requirements.stream().collect(Collectors.groupingBy(ShiftRequirement::getWorkDate));
    }

    public Map<String, Integer> computeFairSharePerType(List<ShiftRequirement> requirements, int staffPool) {
        return computeFairSharePerTypeWithStaff(requirements, staffPool, null);
    }

    public Map<String, Integer> computeFairSharePerTypeWithStaff(
            List<ShiftRequirement> requirements, int staffPool, List<Staff> activeStaff) {
        if (requirements == null || requirements.isEmpty()) {
            return Map.of(
                ConflictDetectionService.SHIFT_TYPE_L01, 1,
                ConflictDetectionService.SHIFT_TYPE_L02, 1,
                ConflictDetectionService.SHIFT_TYPE_L03, 1,
                ConflictDetectionService.SHIFT_TYPE_L04, 1
            );
        }
        Map<String, Integer> result = new HashMap<>();
        // Guard against null/empty activeStaff for L04 cross-specialty logic
        List<Staff> safeActiveStaff = (activeStaff == null || activeStaff.isEmpty()) ? List.of() : activeStaff;

        for (String typeId : List.of(
                ConflictDetectionService.SHIFT_TYPE_L01,
                ConflictDetectionService.SHIFT_TYPE_L02,
                ConflictDetectionService.SHIFT_TYPE_L03,
                ConflictDetectionService.SHIFT_TYPE_L04)) {

            int totalDemand = requirements.stream()
                    .filter(r -> typeId.equals(r.getShiftType().getId()))
                    .mapToInt(ShiftRequirement::getRequiredStaffCount)
                    .sum();

            int effectivePool;
            if (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeId) && !safeActiveStaff.isEmpty()) {
                // L04 voi cross-specialty: dung toan bo staff pool de tang coverage
                // Khi cross-specialty bat, staff tu specialty khac co the duoc gan, nen pool phai rong hon
                var crossConfig = autoSchedulingService.getL04CrossSpecialtyConfig();
                boolean crossEnabled = crossConfig.enabled();

                Set<Integer> l04SpecialtyIds = requirements.stream()
                        .filter(r -> typeId.equals(r.getShiftType().getId()) && r.getSpecialty() != null)
                        .map(r -> r.getSpecialty().getId())
                        .collect(Collectors.toSet());

                if (!l04SpecialtyIds.isEmpty()) {
                    // Count eligible L04 staff — use ALL_ELIGIBLE_SPECIALTIES (Ngoai, Noi, San, Nhi, Mat, Rang)
                    // so that staff from extended specialties can fill L04 slots when their
                    // own specialty pool is exhausted (cross-specialty = true by default for L04).
                    int totalEligibleL04Staff = (int) safeActiveStaff.stream()
                            .filter(s -> s.getSpecialty() != null
                                    && StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES.contains(s.getSpecialty().getName()))
                            .count();

                    if (crossEnabled) {
                        // Cross-specialty BAT: dung toan bo eligible staff (Bac si/Dieu duong) lam pool
                        // Staff tu specialty khac co the duoc gan, nen pool rong hon
                        effectivePool = Math.max(1, totalEligibleL04Staff);
                        log.info("L04 cross-specialty ENABLED: using eligible staff pool (size={}, total={})",
                                totalEligibleL04Staff, safeActiveStaff.size());
                    } else {
                        // Cross-specialty TAT: chi dung staff cung specialty
                        long eligibleL04Count = safeActiveStaff.stream()
                                .filter(s -> s.getSpecialty() != null && l04SpecialtyIds.contains(s.getSpecialty().getId()))
                                .count();
                        effectivePool = Math.max(1, (int) eligibleL04Count);
                    }

                    // Per-specialty fairShare: tinh theo specialty pool de dam bao cong bang
                    for (Integer specId : l04SpecialtyIds) {
                        int specDemand = requirements.stream()
                                .filter(r -> typeId.equals(r.getShiftType().getId())
                                        && r.getSpecialty() != null
                                        && specId.equals(r.getSpecialty().getId()))
                                .mapToInt(ShiftRequirement::getRequiredStaffCount)
                                .sum();

                        if (crossEnabled) {
                            // Cross-specialty enabled: use eligible staff pool for fair-share calculation
                            // Staff from other specialties can fill in, so use the full eligible pool
                            int specFairShare = specDemand > 0
                                    ? Math.min(specDemand,
                                            (int) Math.ceil((double) specDemand / totalEligibleL04Staff * 1.2)) : 1;
                            result.put("L04:" + specId, specFairShare);
                            log.info("L04 cross-specialty fairShare for specialty {}: demand={}, eligiblePool={}, fairShare={}",
                                    specId, specDemand, totalEligibleL04Staff, specFairShare);
                        } else {
                            // Cross-specialty tat: dung specialty pool
                            long specPool = safeActiveStaff.stream()
                                    .filter(s -> s.getSpecialty() != null && specId.equals(s.getSpecialty().getId()))
                                    .count();
                            int specEffectivePool = Math.max(1, (int) specPool);
                            int specFairShare = specDemand > 0
                                    ? (int) Math.ceil((double) specDemand / specEffectivePool) : 1;
                            result.put("L04:" + specId, specFairShare);
                        }
                    }
                } else {
                    effectivePool = staffPool;
                }
            } else {
                effectivePool = staffPool;
            }

            // ceil(demand / pool) — khong cong buffer cung de tranh lech
            int fairShare = totalDemand > 0 ? (int) Math.ceil((double) totalDemand / effectivePool) : 1;
            result.put(typeId, fairShare);
        }

        log.info("fairSharePerType: L01={} L02={} L03={} L04={} (staffPool={})",
                result.get(ConflictDetectionService.SHIFT_TYPE_L01),
                result.get(ConflictDetectionService.SHIFT_TYPE_L02),
                result.get(ConflictDetectionService.SHIFT_TYPE_L03),
                result.get(ConflictDetectionService.SHIFT_TYPE_L04),
                staffPool);
        // Log per-specialty fairShare for L04
        result.entrySet().stream()
                .filter(e -> e.getKey().startsWith("L04:"))
                .forEach(e -> log.info("  fairShare {}: {}", e.getKey(), e.getValue()));

        // DEBUG: Log requirements breakdown by type
        Map<String, Long> reqCountByType = requirements.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> r.getShiftType().getId() + (r.getSpecialty() != null ? ":" + r.getSpecialty().getId() : ""),
                        java.util.stream.Collectors.counting()));
        reqCountByType.forEach((k, v) -> log.info("  REQUIREMENT: type={} count={}", k, v));

        return result;
    }

    public boolean isStrictMatchForStaff(Staff staff, ShiftRequirement req) {
        return req.getSpecialty() != null
                && staff.getSpecialty() != null
                && staff.getSpecialty().getId().equals(req.getSpecialty().getId());
    }

    /** Check if a specific staff has an L01 schedule on a specific date. */
    private boolean checkAdjacentL01(List<Schedule> schedules, int staffId, LocalDate date) {
        for (Schedule s : schedules) {
            if (s.getStaff().getId().equals(staffId)
                    && s.getWorkDate().equals(date)
                    && ConflictDetectionService.SHIFT_TYPE_L01.equals(s.getShiftType().getId())) {
                return true;
            }
        }
        return false;
    }
}
