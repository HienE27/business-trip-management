package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import com.hospital.scheduler.dto.request.NotificationDTO;
import com.hospital.scheduler.dto.response.AlgorithmMetricsDTO;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.exception.BadRequestException;
import com.hospital.scheduler.exception.ConflictException;
import com.hospital.scheduler.exception.ResourceNotFoundException;
import com.hospital.scheduler.repository.*;
import com.hospital.scheduler.util.CompensationDateCalculator;
import com.hospital.scheduler.util.DateUtils;
import com.hospital.scheduler.util.ScheduleKeyUtils;
import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.algorithm.CSPScheduler;
import com.hospital.scheduler.algorithm.ScheduleChange;
import com.hospital.scheduler.algorithm.SchedulingResult;
import com.hospital.scheduler.algorithm.ShiftRequirementInfo;
import com.hospital.scheduler.algorithm.scoring.ScheduleQualityScorer;
import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import com.hospital.scheduler.service.scheduling.CspAssignmentEngine;
import com.hospital.scheduler.service.scheduling.GreedyAssignmentEngine;
import com.hospital.scheduler.service.scheduling.PostAssignmentOptimizer;
import com.hospital.scheduler.service.scheduling.SimulatedAnnealingOptimizer;
import com.hospital.scheduler.service.scheduling.RequirementPreparationService;
import com.hospital.scheduler.service.scheduling.ReplacementSuggestionService;
import com.hospital.scheduler.service.scheduling.SchedulePersistenceService;
import com.hospital.scheduler.service.scheduling.SchedulingConflictDataLoader;
import com.hospital.scheduler.service.scheduling.SchedulingLockService;
import com.hospital.scheduler.service.scheduling.SchedulingMetricsService;
import com.hospital.scheduler.service.scheduling.SchedulingStateAccessor;
import com.hospital.scheduler.service.scheduling.StaffEligibilityFilter;
import com.hospital.scheduler.service.scheduling.UnassignedDaysReportBuilder;
import com.hospital.scheduler.service.scheduling.WorkloadChartBuilder;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AutoSchedulingService {

    // Wrapper to return both schedules and the fairness score for downstream
    // metrics. Kept as a record because callers in the CSP path also need
    // to know whether the plan was a partial timeout result so the Greedy
    // fallback can take over.
    private record SchedulingResultWithFairness(List<Schedule> schedules, BigDecimal fairnessScore, boolean cspPartial) {
        SchedulingResultWithFairness(List<Schedule> schedules, BigDecimal fairnessScore) {
            this(schedules, fairnessScore, false);
        }
    }

    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository periodRepository;
    private final StaffRepository staffRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ScheduleExchangeRepository scheduleExchangeRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final AuditHistoryService auditHistoryService;
    private final CompensationDateCalculator compensationDateCalculator;
    private final NotificationService notificationService;
    private final AlgorithmConfigService algorithmConfigService;
    private final HolidayRepository holidayRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final CSPScheduler cspScheduler;
    private final com.hospital.scheduler.scheduling.LocalSearchScheduler localSearchScheduler;
    private final EntityManager entityManager;
    private final ScheduleConflictRepository scheduleConflictRepository;
    private final AlgorithmProgressTracker progressTracker;
    private final ScheduleQualityScorer scheduleQualityScorer;

    // ─── Refactored sub-services (M07 refactor) ──────────────────────────────
    private final SchedulingLockService lockService;
    private final SchedulingStateAccessor stateAccessor;
    private final SchedulingMetricsService metricsService;
    private final UnassignedDaysReportBuilder unassignedDaysReportBuilder;
    private final ReplacementSuggestionService replacementSuggestionService;
    private final WorkloadChartBuilder workloadChartBuilder;
    private final SchedulingConflictDataLoader conflictDataLoader;
    private final StaffEligibilityFilter staffEligibilityFilter;
    private final PostAssignmentOptimizer postAssignmentOptimizer;
    private final SimulatedAnnealingOptimizer simulatedAnnealingOptimizer;
    private final SchedulePersistenceService schedulePersistenceService;

    /**
     * Single source of truth for ShiftRequirement generation. Replaces the
     * formerly-duplicated {@code generateRequirementsFromConfig},
     * {@code persistRequirementsIfTransient}, {@code syncExistingRequirementsWithConfig}
     * and helper methods that used to live on this class. See Issue #12 (B2).
     */
    private final RequirementPreparationService requirementPreparationService;

    // ─── In-memory scheduling state — backed by SchedulingStateAccessor. Concurrent
    // requests each see their own copy because SchedulingStateAccessor holds the
    // ThreadLocals internally. Callers in this facade go through the accessor helpers
    // below so we have one source of truth for the cache invalidation lifecycle
    // (reset() at run start, cleanup() in finally).

    private Map<String, Set<String>> inMemoryAssignments() {
        return stateAccessor.getInMemoryAssignments();
    }

    private Set<String> inMemoryCompensationShiftDates() {
        return stateAccessor.getInMemoryCompensationShiftDates();
    }

    private Set<String> allCompensationShiftDates() {
        return stateAccessor.getAllCompensationShiftDates();
    }

    private Set<Integer> swapPriorityStaffIds() {
        return stateAccessor.getSwapPriorityStaffIds();
    }

    // ─── Reset/Cleanup delegated to SchedulingStateAccessor — used by top-level
    // entry points so the worker thread is left clean between requests. ──────────

    private void resetSchedulingState() {
        stateAccessor.reset();
    }

    private void clearSchedulingState() {
        stateAccessor.cleanup();
    }

    // Per-period execution locks live in {@link SchedulingLockService} (single source
    // of truth). Concurrent autoSchedule / previewSchedule calls on the same period
    // are serialized there so their delete-and-regenerate operations cannot interleave.

    // Pre-loaded period-level conflict data (rebuilt each scheduling run)
    private record BatchConflictData(
            Set<Integer> onLeaveStaffIds,
            Set<Integer> onCompDayStaffIds,
            Map<Integer, List<Schedule>> daySchedulesByStaff,
            Set<Integer> adjacentL01StaffIds
    ) {}

    // Period-level data pre-loaded once per scheduling run
    private record PeriodConflictData(
            Map<LocalDate, BatchConflictData> byDate,
            Map<Integer, Map<String, Long>> staffShiftTypeCounts,
            Map<Integer, Staff> staffMap
    ) {}

    public AutoScheduleResponse previewSchedule(AutoScheduleRequestDTO request) {
        // BUGFIX (was M07 #3): Same per-period lock as autoSchedule — a preview run
        // also deletes-and-regenerates schedule rows, so it must not race with
        // a concurrent autoSchedule or another preview on the same period.
        // BUGFIX: force-release stale lock if owning thread is dead (prevents
        // "period is being scheduled by another request" after a failed cancel).
        lockService.forceReleaseStaleLock(request.getPeriodId());
        java.util.concurrent.Semaphore periodSem = acquirePeriodLock(request.getPeriodId());
        boolean acquired = false;
        try {
            acquired = periodSem.tryAcquire();
            if (!acquired) {
                throw new BadRequestException(
                        "Kỳ lịch " + request.getPeriodId() + " đang được xếp tự động bởi một yêu cầu khác. "
                                + "Vui lòng đợi yêu cầu trước hoàn tất rồi thử lại.");
            }
            lockService.registerRunningThread(request.getPeriodId());
            inMemoryAssignments().clear();
            inMemoryCompensationShiftDates().clear();
            allCompensationShiftDates().clear();
            swapPriorityStaffIds().clear();
            try {
                return runScheduling(request, false);
            } finally {
                clearSchedulingState();
            }
        } finally {
            if (acquired) {
                lockService.unregisterRunningThread(request.getPeriodId());
                periodSem.release();
            }
        }
    }

    public AutoScheduleResponse autoSchedule(AutoScheduleRequestDTO request) {
        // BUGFIX (was M07 #3): Acquire a per-period execution lock so two concurrent
        // autoSchedule requests on the same period cannot interleave their
        // delete-and-regenerate operations and produce duplicate or lost schedules.
        // The V9 migration dropped the schedule UNIQUE constraint, so the only
        // remaining defence is this lock. If the period is already locked, return 409
        // so the client can retry once the first run completes.
        // BUGFIX: force-release stale lock if owning thread is dead (prevents
        // "period is being scheduled by another request" after a failed cancel).
        lockService.forceReleaseStaleLock(request.getPeriodId());
        java.util.concurrent.Semaphore periodSem = acquirePeriodLock(request.getPeriodId());
        boolean acquired = false;
        try {
            acquired = periodSem.tryAcquire();
            if (!acquired) {
                throw new BadRequestException(
                        "Kỳ lịch " + request.getPeriodId() + " đang được xếp tự động bởi một yêu cầu khác. "
                                + "Vui lòng đợi yêu cầu trước hoàn tất rồi thử lại.");
            }
            lockService.registerRunningThread(request.getPeriodId());
            inMemoryAssignments().clear();
            inMemoryCompensationShiftDates().clear();
            allCompensationShiftDates().clear();
            swapPriorityStaffIds().clear();
            try {
                return runScheduling(request, true);
            } finally {
                clearSchedulingState();
            }
        } finally {
            if (acquired) {
                lockService.unregisterRunningThread(request.getPeriodId());
                periodSem.release();
            }
        }
    }

    public AutoScheduleResponse applyPreviewSchedule(com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO request) {
        // BUGFIX (was M07 #4): the apply path reads from the in-memory cache
        // (allCompensationShiftDates, inMemoryAssignments, etc.) but never
        // cleared it in a finally block. When Tomcat reuses a worker thread
        // for a different request, the leftover snapshot could be observed by
        // any code that touches these ThreadLocals. Initialize fresh values
        // here and remove them on exit so the worker thread is left clean.
        inMemoryAssignments().clear();
        inMemoryCompensationShiftDates().clear();
        allCompensationShiftDates().clear();
        swapPriorityStaffIds().clear();
        try {
            return applyPreviewScheduleInternal(request);
        } finally {
            clearSchedulingState();
        }
    }

    private AutoScheduleResponse applyPreviewScheduleInternal(com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO request) {
        SchedulePeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể áp dụng bản nháp khi kỳ lịch ở trạng thái DRAFT");
        }

        // Xóa tất cả lịch cũ của period trước khi áp dụng bản preview mới
        List<Schedule> oldSchedules = scheduleRepository.findByPeriodId(period.getId());
        if (!oldSchedules.isEmpty()) {
            log.info("Deleting {} old schedules for period {} before applying preview", oldSchedules.size(), period.getId());
            List<Integer> scheduleIds = oldSchedules.stream().map(Schedule::getId).toList();
            scheduleConflictRepository.deleteByScheduleIds(scheduleIds);
            compensationDayRepository.deleteAllByPeriodId(period.getId());
            entityManager.flush();
            scheduleRepository.deleteAllByPeriodId(period.getId());
            entityManager.flush();
        }

        List<Schedule> savedSchedules = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        // Track staff→date→shifts assigned so far in this apply loop, so the conflict
        // check catches L01↔L02 / L03↔L04 collisions between sibling preview items.
        // This replaces the trust in the in-memory assignments used during preview,
        // which is no longer available at apply time.
        Map<String, Set<String>> inApplyLoop = new HashMap<>();
        
        // CRITICAL: Load existing compensation days into in-memory cache to prevent duplicates
        // when re-running the algorithm after old schedules were deleted
        Set<String> existingCompDays = new HashSet<>();
        for (CompensationDay cd : compensationDayRepository.findByPeriodId(period.getId())) {
            String compKey = cd.getStaff().getId() + "_" + cd.getCompensationDate().toString();
            existingCompDays.add(compKey);
            allCompensationShiftDates().add(compKey);
        }
        log.info("Loaded {} existing compensation days for period {} into memory cache",
                existingCompDays.size(), period.getId());

        // Index period requirements once so the per-item resolver runs in O(1) and we
        // can detect L04-style multi-specialty collisions deterministically instead of
        // leaning on findFirst() (M07 #8 was exactly that bug).
        List<ShiftRequirement> periodRequirementsAll = requirementRepository.findByPeriodId(period.getId());
        Map<Integer, ShiftRequirement> byId = new HashMap<>();
        for (ShiftRequirement r : periodRequirementsAll) {
            if (r.getId() != null) {
                byId.put(r.getId(), r);
            }
        }
        // Map key = "yyyy-MM-dd|shiftTypeId" → matching requirements (1+ for L04 multi-specialty)
        Map<String, List<ShiftRequirement>> byDateShift = new HashMap<>();
        for (ShiftRequirement r : periodRequirementsAll) {
            String key = r.getWorkDate().toString() + "|" + r.getShiftType().getId();
            byDateShift.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        Set<String> removedScheduleKeys = request.getRemovedSchedules() == null
                ? Set.of()
                : request.getRemovedSchedules().stream()
                        .map(item -> scheduleKey(item.getStaffId(), item.getWorkDate(), item.getShiftTypeId()))
                        .collect(Collectors.toSet());

        for (var item : request.getSchedules()) {
            if (removedScheduleKeys.contains(scheduleKey(item.getStaffId(), item.getWorkDate(), item.getShiftTypeId()))) {
                log.info("Skipping removed preview item: staffId={}, workDate={}, shiftType={}",
                        item.getStaffId(), item.getWorkDate(), item.getShiftTypeId());
                continue;
            }

            Staff staff = staffRepository.findById(item.getStaffId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân sự với ID: " + item.getStaffId()));
            ShiftRequirement requirement;
            if (item.getRequirementId() != null) {
                // BUGFIX (was M07 #8): prefer the explicit id from the preview
                // payload so multi-specialty L04 requirements resolve deterministically.
                requirement = byId.get(item.getRequirementId());
                if (requirement == null) {
                    throw new BadRequestException("requirementId không hợp lệ: " + item.getRequirementId());
                }
                // Sanity-check: caller claims this requirement belongs to the same
                // (workDate, shiftType) — refuse otherwise.
                String reqDate = requirement.getWorkDate().toString();
                String reqShift = requirement.getShiftType().getId();
                if (!reqDate.equals(item.getWorkDate()) || !reqShift.equals(item.getShiftTypeId())) {
                    throw new BadRequestException(
                            "requirementId " + item.getRequirementId()
                                    + " không khớp (workDate=" + reqDate + ", shiftTypeId=" + reqShift
                                    + ") so với (workDate=" + item.getWorkDate()
                                    + ", shiftTypeId=" + item.getShiftTypeId() + ")");
                }
            } else {
                // Backwards-compatible fallback: look up by (workDate, shiftTypeId) but
                // fail loudly if multiple match so callers learn to populate requirementId.
                String key = item.getWorkDate() + "|" + item.getShiftTypeId();
                List<ShiftRequirement> candidates = byDateShift.getOrDefault(key, List.of());
                if (candidates.isEmpty()) {
                    requirement = null;
                } else if (candidates.size() == 1) {
                    requirement = candidates.get(0);
                } else {
                    throw new BadRequestException(
                            "Có nhiều requirement cho (workDate=" + item.getWorkDate()
                                    + ", shiftTypeId=" + item.getShiftTypeId()
                                    + ") — client phải gửi requirementId để chọn đúng (ID ứng viên: "
                                    + candidates.stream().map(r -> String.valueOf(r.getId())).collect(Collectors.joining(", "))
                                    + ").");
                }
            }
            ShiftType shiftType = requirement != null ? requirement.getShiftType() : null;
            if (shiftType == null) {
                throw new BadRequestException("Không tìm thấy ca trực phù hợp cho ngày " + item.getWorkDate());
            }

            LocalDate workDate = LocalDate.parse(item.getWorkDate());

            // Check if workDate is a holiday
            if (holidayRepository.existsByHolidayDateAndIsActiveTrue(workDate)) {
                throw new ConflictException("Không thể phân công vào ngày lễ: " + workDate);
            }

            // Pre-check the in-loop assignments so we never save a sibling conflict
            // (e.g. L01 then L02 in the same preview for the same staff+date).
            if (hasInLoopConflict(inApplyLoop, staff.getId(), workDate, shiftType.getId())) {
                throw new ConflictException("Xung đột trong bản nháp: nhân sự " + staff.getId()
                        + " đã được phân công ca xung khắc ngày " + workDate);
            }

            // Re-validate against persisted state. The auto-scheduling algorithm may have
            // produced a preview minutes ago — DB state can have changed since then
            // (other managers added schedules, leave requests were approved, etc.).
            // CRITICAL: do NOT skip shift-type conflict (L01↔L02, L03↔L04). Per SPEC M02-F07
            // these collisions must block the save. The sibling check hasInLoopConflict() above
            // only catches collisions WITHIN this apply batch; hasAnyConflict() must also catch
            // collisions with schedules already in the DB (e.g. a manager-edited preview inserted
            // an L02 on a date that already has an L01 from a manual edit).
            //
            // Skip schedule if conflict detected instead of throwing to allow partial save.
            if (conflictDetectionService.hasAnyConflict(
                    staff.getId(), workDate, shiftType.getId(), null, false, false)) {
                log.warn("Conflict when applying schedule, skipping: staffId={}, workDate={}, shiftType={}, periodId={}",
                        staff.getId(), workDate, shiftType.getId(), period.getId());
                continue;
            }

            Schedule schedule = Schedule.builder()
                    .period(period)
                    .staff(staff)
                    .shiftType(shiftType)
                    .workDate(workDate)
                    .requirement(requirement)
                    .hasConflict(false)
                    .build();

            // Check if schedule already exists to avoid duplicate key error
            boolean exists = scheduleRepository.existsByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                    period.getId(), staff.getId(), shiftType.getId(), workDate);
            if (exists) {
                log.warn("Schedule already exists, skipping: staffId={}, workDate={}, shiftType={}",
                        staff.getId(), workDate, shiftType.getId());
                continue;
            }

            Schedule saved = scheduleRepository.save(schedule);
            inApplyLoop.computeIfAbsent(staff.getId() + "_" + workDate, k -> new HashSet<>())
                    .add(shiftType.getId());
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftType.getId())) {
                createCompensationDayForAuto(saved);
                // Force JPA flush so the next iteration's conflict-detection reads (which run
                // inside the same @Transactional method) can see the compensation day we just
                // inserted. Without this, detectCompensationConflict() in hasAnyConflict() may
                // return false for a sibling schedule that lands on the new comp day, and the
                // save slips through — only to surface as a conflict on the next monthly-schedule
                // reload. Explicit flush guarantees same-transaction visibility.
                entityManager.flush();
            }
            auditHistoryService.logAction("schedule", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, null);
            savedSchedules.add(saved);
        }

        List<AutoScheduleResponse.ScheduleSummary> summaries = savedSchedules.stream()
                .map(s -> AutoScheduleResponse.ScheduleSummary.builder()
                        .scheduleId(s.getId())
                        .staffId(s.getStaff().getId())
                        .staffName(s.getStaff().getFullName())
                        .workDate(s.getWorkDate().toString())
                        .shiftTypeId(s.getShiftType().getId())
                        .shiftTypeName(s.getShiftType().getName())
                        .staffSpecialtyName(getStaffSpecialtyName(s))
                        .requiredSpecialtyName(getRequiredSpecialtyName(s))
                        .crossSpecialty(isCrossSpecialtyAssignment(s))
                        .requirementId(s.getRequirement() != null ? s.getRequirement().getId() : null)
                        .build())
                .toList();

        // Notify each staff about their auto-assigned shifts (one notification per staff, not per schedule)
        var staffScheduleMap = savedSchedules.stream()
                .collect(java.util.stream.Collectors.groupingBy(s -> s.getStaff().getId()));
        for (var entry : staffScheduleMap.entrySet()) {
            Integer staffId = entry.getKey();
            List<Schedule> staffSchedules = entry.getValue();
            String dutyList = staffSchedules.stream()
                    .map(s -> s.getWorkDate().toString() + " (" + s.getShiftType().getName() + ")")
                    .collect(Collectors.joining("; "));
            notificationService.createNotification(staffId, new NotificationDTO(
                    "Bạn được phân công ca trực tự động",
                    "Bạn vừa được phân công " + staffSchedules.size() + " ca trực tự động trong kỳ lịch.\nDanh sách: " + dutyList));
        }

        // Load requirements for coverage calculation (B7: coverage = saved vs needed)
        List<ShiftRequirement> periodRequirements = requirementRepository.findByPeriodId(period.getId());
        int totalRequiredStaffNeeded = periodRequirements.stream()
                .mapToInt(ShiftRequirement::getRequiredStaffCount)
                .sum();

        BigDecimal coverageRate = totalRequiredStaffNeeded > 0
                ? BigDecimal.valueOf(Math.min(1.0, (double) savedSchedules.size() / totalRequiredStaffNeeded) * 100)
                : BigDecimal.ZERO;

        // Build unassigned days report (B7: danh sách ngày chưa phân công đầy đủ)
        List<Map<String, Object>> unassignedDays = unassignedDaysReportBuilder.buildUnassignedDays(periodRequirements, savedSchedules);

        int distinctStaffAssigned = (int) savedSchedules.stream()
                .map(s -> s.getStaff().getId())
                .distinct()
                .count();
        int staffCount = distinctStaffAssigned > 0 ? distinctStaffAssigned : 1;
        BigDecimal balanceScore = calculateBalanceScore(savedSchedules, staffCount);

        long executionTime = System.currentTimeMillis() - startTime;

        // Save metrics so History tab shows this execution. The conflictCount is
        // determined AFTER re-checking the persisted state so the recorded value
        // matches what the monthly-schedule page will display. Pre-fix (was M07 #9):
        // we recorded conflictCount=0 here and re-checked later, but never updated
        // the row — leaving every history entry pinned to 0 conflicts.
        int appliedConflictCount = 0;
        try {
            appliedConflictCount = conflictDetectionService.checkPeriodConflicts(period.getId()).getTotalConflicts();
        } catch (Exception e) {
            log.error("Failed to check period conflicts after apply for period {}: {}",
                    period.getId(), e.getMessage(), e);
        }
        try {
            saveMetrics(period, request.getAlgorithmType(), (int) executionTime, coverageRate,
                    balanceScore, appliedConflictCount, savedSchedules.size());
            log.info("Metrics saved for period {} with algorithm {}: coverage={}%, balance={}, conflicts={}",
                    period.getId(), request.getAlgorithmType(), coverageRate, balanceScore, appliedConflictCount);
        } catch (Exception e) {
            log.error("Failed to save metrics for period {}: {}", period.getId(), e.getMessage(), e);
        }

        // Build shift type breakdown
        Map<String, AutoScheduleResponse.ShiftTypeBreakdown> byShiftType = buildByShiftTypeBreakdown(savedSchedules, periodRequirements);

        return AutoScheduleResponse.builder()
                .success(true)
                .message(appliedConflictCount == 0
                        ? "Đã áp dụng bản nháp đã chỉnh sửa"
                        : "Đã áp dụng bản nháp đã chỉnh sửa — phát hiện " + appliedConflictCount + " xung đột cần xử lý")
                .periodId(period.getId())
                .algorithmType(request.getAlgorithmType())
                .executionTimeMs((int) executionTime)
                .coverageRate(coverageRate.setScale(2, RoundingMode.HALF_UP))
                .balanceScore(balanceScore.setScale(2, RoundingMode.HALF_UP))
                .conflictCount(appliedConflictCount)
                .totalSchedulesCreated(savedSchedules.size())
                .schedules(summaries)
                .unassignedDays(unassignedDays)
                .executedAt(LocalDateTime.now())
                .byShiftType(byShiftType)
                .build();
    }

    private AutoScheduleResponse runScheduling(AutoScheduleRequestDTO request, boolean save) {
        long startTime = System.currentTimeMillis();

        // lastFairnessScore is set by the algorithm dispatch when running CSP/GA variants.
        // GA fairness score is on a 0-100 scale and used by downstream metrics.
        List<ShiftRequirement> requirements;
        
        SchedulePeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xếp lịch tự động khi kỳ lịch ở trạng thái DRAFT");
        }

        // Decide whether to clear existing schedules BEFORE generating new ones.
        // - Preview (save=false): NEVER delete — preview is non-destructive, just runs
        //   the algorithm in-memory and returns the proposed plan. Existing schedules
        //   are still loaded below as context for the algorithm.
        // - Apply (save=true): only delete when overwriteExisting=true. Otherwise throw
        //   BadRequestException to protect manual schedules from being silently lost.
        List<Schedule> existingSchedulesForPeriod = scheduleRepository.findByPeriodId(period.getId());
        boolean overwrite = Boolean.TRUE.equals(request.getOverwriteExisting());

        if (save && overwrite) {
            if (!existingSchedulesForPeriod.isEmpty()) {
                // CRITICAL: Delete in correct FK order: schedule_conflict -> compensation_day -> schedule
                List<Integer> scheduleIds = existingSchedulesForPeriod.stream().map(Schedule::getId).toList();
                scheduleConflictRepository.deleteByScheduleIds(scheduleIds);
                compensationDayRepository.deleteAllByPeriodId(period.getId());
                entityManager.flush();
                scheduleRepository.deleteAllByPeriodId(period.getId());
                entityManager.flush();
                log.info("Cleared {} existing schedules and compensation days for period {} (overwriteExisting=true)",
                        existingSchedulesForPeriod.size(), period.getId());
            }
        } else if (save && !overwrite && !existingSchedulesForPeriod.isEmpty()) {
            throw new BadRequestException(
                    "Kỳ lịch " + period.getId() + " đã có " + existingSchedulesForPeriod.size()
                            + " lịch hiện tại (bao gồm có thể cả lịch phân công thủ công). "
                            + "Đặt overwriteExisting=true nếu muốn xóa hết lịch cũ và xếp lại từ đầu, "
                            + "hoặc dùng endpoint /preview để xem trước trước khi áp dụng.");
        } else if (!save) {
            log.info("Preview mode: keeping {} existing schedules for period {} (preview is non-destructive)",
                    existingSchedulesForPeriod.size(), period.getId());
        }

        // CRITICAL: Clear in-memory cache after deleting old data to prevent stale entries
        allCompensationShiftDates().clear();
        log.info("Cleared in-memory compensation day cache for period {}", period.getId());

        // Load runtime config from DB (or use defaults if not set)
        AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig = algorithmConfigService.getRuntimeConfig();
        log.info("Using runtime config: weekendWeight={}, overnightRecoveryHours={}, greedyThreshold={}, balanceMin={}, maxShiftsPerStaff={}",
                runtimeConfig.getWeekendWeight(),
                runtimeConfig.getOvernightRecoveryHours(), runtimeConfig.getGreedyCoverageThreshold(),
                runtimeConfig.getBalanceScoreMin(), runtimeConfig.getMaxShiftsPerStaff());

        // Load approved swap requests for priority assignment
        // Staff in PENDING/APPROVED swap requests get priority for their preferred schedules
        List<ScheduleExchange> pendingSwaps = scheduleExchangeRepository
                .findByPeriodIdAndStatus(period.getId(),
                        com.hospital.scheduler.entity.ScheduleExchange.ExchangeStatus.PENDING);
        if (pendingSwaps == null) pendingSwaps = Collections.emptyList();
        log.info("Loaded {} pending swap requests for period {}", pendingSwaps.size(), period.getId());

        // If a swap request exists for a schedule, prioritize the swap partner (target)
        // When target's schedule is assigned, the requester should be considered for their preferred shift
        Set<Integer> swapPrioritySet = swapPriorityStaffIds();
        swapPrioritySet.clear();
        for (ScheduleExchange swap : pendingSwaps) {
            // The target (người đổi cùng) should get priority over their preferred schedule
            // The requester (người yêu cầu) should be freed up (by getting the target's schedule)
            if (swap.getTarget() != null) {
                swapPrioritySet.add(swap.getTarget().getId());
            }
        }

        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        if (request.getExcludedStaffIds() != null && !request.getExcludedStaffIds().isEmpty()) {
            Set<Integer> excluded = new HashSet<>(request.getExcludedStaffIds());
            activeStaff = activeStaff.stream()
                    .filter(s -> !excluded.contains(s.getId()))
                    .collect(Collectors.toList());
        }

        // Always generate requirements from algorithm config (no manual requirements page)
        var autoGenConfig = algorithmConfigService.getAutoGenConfig();
        if (autoGenConfig.isEmpty() || !autoGenConfig.get().enabled()) {
            throw new BadRequestException(
                    "Cấu hình auto-gen chưa được bật. Vui lòng bật auto_generate_requirements trong cấu hình thuật toán.");
        }
        // Derive removedShiftTypes once so the same set is used to (a) filter the
        // requirement list before dispatch and (b) filter the response schedules.
        // Without this, stale shift_requirement rows persisted before the user
        // toggled a type off would still feed the algorithm and produce L02/etc.
        // assignments even though the UI shows the toggle as enabled.
        Set<String> removedShiftTypes = autoGenConfig.get().removedShiftTypes() == null
                ? java.util.Set.of()
                : autoGenConfig.get().removedShiftTypes().stream()
                        .filter(java.util.Objects::nonNull)
                        .map(String::toUpperCase)
                        .collect(Collectors.toSet());
        if (!removedShiftTypes.isEmpty()) {
            log.info("Filtering out removed shift types from dispatch: {}", removedShiftTypes);
        }
        // CRITICAL: Re-sync existing requirements with current config so changes to min/max per day
        // take effect on the next preview run. Without this, the scheduler would re-use stale
        // requiredCount values persisted by a previous run with older config.
        // B2 (Issue #12): delegate requirement generation + persistence to
        // RequirementPreparationService — single source of truth. The previously
        // duplicated branches (save vs preview) both end up at the same helper
        // which already handles "use existing requirements; fall back to in-memory
        // generation when DB is empty". The caller's save flag controls whether
        // sync + persist happen or the result stays transient.
        requirements = requirementPreparationService.prepareRequirements(period, save, activeStaff);
        log.info("Prepared {} requirements (save={}) for period {}", requirements.size(), save, period.getId());

        // Defensive filter: drop any requirement whose shift type is in removedShiftTypes.
        // RequirementPreparationService already filters when generating fresh, but the DB may
        // still hold stale rows from a prior run with a different config. Without this filter
        // the algorithm would still see (and assign) L02 rows even though the user toggled L02
        // off in the Configuration Calculator.
        if (!removedShiftTypes.isEmpty()) {
            int before = requirements.size();
            requirements = requirements.stream()
                    .filter(r -> r.getShiftType() == null
                            || !removedShiftTypes.contains(r.getShiftType().getId().toUpperCase()))
                    .collect(Collectors.toList());
            if (requirements.size() != before) {
                log.info("Dropped {} stale requirements for removed shift types {} (period {})",
                        before - requirements.size(), removedShiftTypes, period.getId());
            }
        }

        // Pre-load existing compensation days from the same period so greedy doesn't assign L01 on a day
        // that is already someone's compensation day (confirmed day off — cannot assign L01)
        // Skip in clean preview mode (skipExisting=true) so the algorithm generates from scratch
        if (!(Boolean.TRUE.equals(request.getSkipExisting()) && !save)) {
            List<CompensationDay> existingCompDays = compensationDayRepository.findByPeriodId(period.getId());
            for (CompensationDay cd : existingCompDays) {
                allCompensationShiftDates().add(cd.getStaff().getId() + "_" + cd.getCompensationDate().toString());
            }
        } else {
            log.info("Clean preview: skip loading existing compensation days for period {}", period.getId());
        }

        // CRITICAL: Pre-load existing schedules from the same period into memory
        // This ensures the algorithm sees all already-assigned shifts and avoids conflicts
        // that would fail at apply-preview time (e.g., back-to-back L01 checks)
        // Skip in clean preview mode (skipExisting=true) so the algorithm sees a blank slate
        List<Schedule> existingSchedules = scheduleRepository.findByPeriodId(period.getId());
        if (!(Boolean.TRUE.equals(request.getSkipExisting()) && !save)) {
            for (Schedule existing : existingSchedules) {
                String key = existing.getStaff().getId() + "_" + existing.getWorkDate();
                inMemoryAssignments().computeIfAbsent(key, k -> new HashSet<>()).add(existing.getShiftType().getId());
                // Also track compensation dates from existing L01 shifts
                if (ConflictDetectionService.SHIFT_TYPE_L01.equals(existing.getShiftType().getId())) {
                    LocalDate compDate = compensationDateCalculator.calculate(existing.getWorkDate());
                    if (compDate != null) {
                        allCompensationShiftDates().add(existing.getStaff().getId() + "_" + compDate.toString());
                    }
                }
            }
        } else {
            log.info("Clean preview: skip loading {} existing schedules into in-memory state",
                    existingSchedules.size());
        }

        // P2-8: Enforce L01→L02→L03→L04 processing order per spec
        // L01 must be assigned first to reserve compensation days and avoid L01↔L02 conflicts
        // Safety: filter out any requirements with null shiftType before sorting (defensive against data issues)
        requirements.sort(Comparator.comparingInt((ShiftRequirement r) -> {
            if (r.getShiftType() == null) return 4;
            String id = r.getShiftType().getId();
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(id)) return 0;
            if (ConflictDetectionService.SHIFT_TYPE_L02.equals(id)) return 1;
            if (ConflictDetectionService.SHIFT_TYPE_L03.equals(id)) return 2;
            if (ConflictDetectionService.SHIFT_TYPE_L04.equals(id)) return 3;
            return 4;
        }));

        if (activeStaff.isEmpty()) {
            throw new BadRequestException("Không có nhân sự nào đang hoạt động");
        }

        // Dispatch to the right algorithm; Fair Greedy variant → runFairGreedy,
        // CSP variant → runCsp (with fallback to Greedy on empty/partial), default → runGreedy.
        // Returns the initial createdSchedules + the GA fairness score if any.
        AlgorithmDispatchResult initialDispatch = dispatchAlgorithm(period, requirements, activeStaff, save, runtimeConfig, request);
        List<Schedule> createdSchedules = initialDispatch.schedules();
        BigDecimal lastFairnessScore = initialDispatch.fairnessScore();
        String algorithmType = initialDispatch.algorithmType();

        int greedyStaffCount = (int) createdSchedules.stream().map(s -> s.getStaff().getId()).distinct().count();
        BigDecimal greedyBalanceScore = calculateBalanceScore(createdSchedules, greedyStaffCount > 0 ? greedyStaffCount : 1);

        // balance_score_min: if balance is below threshold, try alternatives and pick the best
        // This applies to ALL algorithms, not just GREEDY
        BigDecimal bestScore = greedyBalanceScore;
        List<Schedule> bestSchedules = createdSchedules;

        // BUGFIX (V25): BalanceScoreCalculator returns 0-100 scale, but
        // balanceScoreMin is stored as 0-1 (e.g. 0.70). Multiply by 100 so
        // the comparison is correct: 41.44 < 70 → FAIR_GREEDY fallback fires.
        BigDecimal threshold = runtimeConfig.getBalanceScoreMin().multiply(java.math.BigDecimal.valueOf(100));

        if (greedyBalanceScore.compareTo(threshold) < 0 && !activeStaff.isEmpty()) {
            // Preview path: skip Fair Greedy fallback so the user gets a fast response.
            // The Fair Greedy fallback is a heavy second pass that adds minutes on
            // a 1-month period with 23 staff — preview is about showing the user the
            // CSP plan quickly, not about finding the global optimum.
            if (!save) {
                log.info("{} balance score {} < threshold {} (preview) — skipping Fair Greedy fallback",
                        algorithmType, greedyBalanceScore, runtimeConfig.getBalanceScoreMin());
            } else {
                // Try Fair Greedy as a fallback. BUGFIX (was M07 #1): previously
                // this branch called runFairGreedy(..., /*save=*/false, ...) as a
                // probe, then swapped `bestSchedules` to the in-memory probe result
                // when its balance score beat Greedy's. That left the persisted
                // Greedy schedules in the DB while the response advertised the
                // un-persisted Fair Greedy plan — response and DB diverged on
                // every fallback. Now we run Fair Greedy with save=true directly
                // when we're in a save path; the persisted schedule set and the
                // schedule set reported to the caller always agree.
                log.info("{} balance score {} < threshold {}, running Fair Greedy (save=true) as fallback",
                        algorithmType, greedyBalanceScore, runtimeConfig.getBalanceScoreMin());
	                List<Schedule> fairGreedySchedules = runFairGreedy(period, requirements, activeStaff, /*save=*/true, runtimeConfig,
	                        request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null,
	                        request.getMaxShiftsPerMonthOverride(), false);
                int fgStaffCount = (int) fairGreedySchedules.stream().map(s -> s.getStaff().getId()).distinct().count();
                BigDecimal fgBalanceScore = calculateBalanceScore(fairGreedySchedules, fgStaffCount > 0 ? fgStaffCount : 1);
                log.info("Fair Greedy fallback: balanceScore={} ({} had {})", fgBalanceScore, algorithmType, greedyBalanceScore);
                if (fgBalanceScore.compareTo(bestScore) > 0) {
                    log.info("Using Fair Greedy result (better balance score, {} schedules persisted)",
                            fairGreedySchedules.size());
                    bestScore = fgBalanceScore;
                    bestSchedules = fairGreedySchedules;
                } else {
                    // FG didn't actually beat Greedy on balance — roll back FG's
                    // persistence so DB doesn't carry both plans. Easiest path:
                    // delete the rows we just inserted and keep the Greedy set.
                    log.info("Fair Greedy did not improve over {} — rolling back its persisted schedules", algorithmType);
                    final List<Schedule> greedySnapshot = createdSchedules;
                    rollBackSchedulesByPredicate(fairGreedySchedules, sched -> !greedySnapshot.contains(sched));
                }
            }
        }

        // Use the best result
        createdSchedules = bestSchedules;

        // CRITICAL: existing schedules count toward coverage in preview mode.
        // The algorithm only generates NEW assignments (filling gaps) — coverage
        // must reflect total = existing + newly generated, not just the delta.
        // This prevents misleading 0% coverage when existing schedules already
        // satisfy most requirements.
        //
        // Bug fix: skipExisting flag lets the user preview a "clean run" — what
        // the algorithm would generate from scratch, ignoring stale existing data
        // that may have been saved from earlier runs with different config.
        if (!save) {
            if (Boolean.TRUE.equals(request.getSkipExisting())) {
                log.info("Preview CLEAN RUN (skipExisting=true): {} new schedules generated",
                        createdSchedules.size());
            } else {
                for (Schedule existing : existingSchedulesForPeriod) {
                    if (!createdSchedules.contains(existing)) {
                        createdSchedules.add(existing);
                    }
                }
                log.info("Preview coverage: {} existing + {} new = {} total",
                        existingSchedulesForPeriod.size(),
                        createdSchedules.size() - existingSchedulesForPeriod.size(),
                        createdSchedules.size());
            }
        }

        // Phase 3: Local Search fairness rebalance.
        // Keep L01 fixed because it creates compensation-day side effects; safely rebalance L02/L03/L04 only.
        // Preview path: skip the 200-iteration rebalance — it adds ~10s on a 1-month period
        // for marginal fairness gain that the user can't see anyway in preview mode.
        if (!createdSchedules.isEmpty() && save) {
            int optimizedMoves = optimizeFairnessBySafeReassignment(createdSchedules, activeStaff, requirements, 200);
            if (optimizedMoves > 0) {
                log.info("Local Search fairness optimization applied {} safe reassignment moves", optimizedMoves);
            }
        }

        // Phase 3c (ENHANCED): Simulated Annealing refinement.
        // Optional post-processing that improves fairness + fatigue by randomly
        // mutating shift types (L02/L03/L04 only — L01 kept fixed) and accepting
        // better configurations via SA acceptance probability.
        // Preview path: skip (expensive, ~5s on a 1-month period).
        if (!createdSchedules.isEmpty() && save && "CSP_MRV_FC".equals(algorithmType)) {
            try {
                List<ShiftType> allShiftTypes = shiftTypeRepository.findAll();
                List<LeaveRequest> leaveReqs = leaveRequestRepository.findApprovedInRange(
                        period.getStartDate(), period.getEndDate());
                Map<Integer, Set<LocalDate>> leaveIndex = new HashMap<>();
                for (LeaveRequest lr : leaveReqs) {
                    LocalDate d = lr.getStartDate();
                    while (!d.isAfter(lr.getEndDate())) {
                        leaveIndex.computeIfAbsent(lr.getStaff().getId(), k -> new HashSet<>()).add(d);
                        d = d.plusDays(1);
                    }
                }
                Set<String> compDaySet = new HashSet<>(allCompensationShiftDates());

                var changes = simulatedAnnealingOptimizer.optimize(
                        createdSchedules, activeStaff, requirements, allShiftTypes,
                        leaveIndex, compDaySet);

                if (!changes.isEmpty()) {
                    for (var change : changes) {
                        change.schedule().setShiftType(change.newShiftType());
                        if (change.schedule().getId() != null) {
                            scheduleRepository.save(change.schedule());
                            auditHistoryService.logAction("schedule", change.schedule().getId(),
                                    AuditHistory.ActionType.UPDATE, null, change.schedule(), null);
                        }
                    }
                    log.info("SA optimization applied {} changes to schedule", changes.size());
                }
            } catch (Exception e) {
                log.warn("SA optimization failed (non-fatal): {}", e.getMessage());
            }
        }

        // Phase 3b: HARD GUARANTEE - ensure EVERY active staff has at least 1 shift.
        // This is critical for fairness: no staff should be left with 0 assignments.
        // Only assign to eligible staff for each shift type, using soft constraints.
        // Preview path: skip — preview is about showing the user a quick plan, not
        // guaranteeing every staff gets a slot in the preview snapshot.
        if (!activeStaff.isEmpty() && !createdSchedules.isEmpty() && save) {
            Set<Integer> staffWithAnyShift = createdSchedules.stream()
                    .map(s -> s.getStaff().getId())
                    .collect(Collectors.toSet());
            List<Staff> staffWithoutShifts = activeStaff.stream()
                    .filter(s -> !staffWithAnyShift.contains(s.getId()))
                    .collect(Collectors.toList());

            if (!staffWithoutShifts.isEmpty()) {
                log.warn("HARD GUARANTEE: {} staff have 0 assignments, attempting to fix", staffWithoutShifts.size());
                int guaranteed = guaranteeMinimumShifts(createdSchedules, staffWithoutShifts, requirements, activeStaff);
                log.info("HARD GUARANTEE: fixed {} staff with minimum shifts", guaranteed);
            }
        }

        // Notify staff on successful Greedy / Fair-Greedy / CSP save paths.
        if (save && !createdSchedules.isEmpty()) {
            var staffMap = createdSchedules.stream()
                    .collect(java.util.stream.Collectors.groupingBy(s -> s.getStaff().getId()));
            for (var entry : staffMap.entrySet()) {
                List<Schedule> staffSchedules = entry.getValue();
                String dutyList = staffSchedules.stream()
                        .map(s -> s.getWorkDate() + " (" + s.getShiftType().getName() + ")")
                        .collect(Collectors.joining("; "));
                notificationService.createNotification(entry.getKey(), new NotificationDTO(
                        "Bạn được phân công ca trực tự động",
                        "Bạn vừa được phân công " + staffSchedules.size() + " ca trực tự động trong kỳ lịch.\nDanh sách: " + dutyList));
            }
        }
        List<String> warnings = buildWarnings(requirements, createdSchedules);
        List<Map<String, Object>> unassignedDays = unassignedDaysReportBuilder.buildUnassignedDays(requirements, createdSchedules);

        long executionTime = System.currentTimeMillis() - startTime;

        // ── ScheduleQualityScorer: compute comprehensive quality report ──────────
        // Load comp days and approved leaves for full constraint scanning
        List<com.hospital.scheduler.entity.CompensationDay> compDaysForScoring =
                compensationDayRepository.findByPeriodId(period.getId());
        List<com.hospital.scheduler.entity.LeaveRequest> approvedLeaves =
                leaveRequestRepository.findByPeriodIdAndStatus(
                        period.getId(), com.hospital.scheduler.entity.LeaveRequest.LeaveStatus.APPROVED);

        com.hospital.scheduler.algorithm.AutoGenConfig autoGenCfgForScoring =
            algorithmConfigService.getAutoGenConfig().orElse(null);
        com.hospital.scheduler.algorithm.scoring.ScheduleQualityScorer.ScoringMeta scoringMeta =
                com.hospital.scheduler.algorithm.scoring.ScheduleQualityScorer.ScoringMeta
                        .of(algorithmType, executionTime);
        com.hospital.scheduler.algorithm.scoring.ScheduleQualityReport qualityReport =
                scheduleQualityScorer.score(
                        createdSchedules, requirements, activeStaff,
                        compDaysForScoring, approvedLeaves, scoringMeta, autoGenCfgForScoring);
        log.info("Quality report: {}", qualityReport.summary());

        // ── Derive legacy metrics from quality report (backward compat) ──────────
        int totalRequiredStaffSlots = qualityReport.getTotalRequired();
        int totalAssignedStaffSlots = qualityReport.getTotalAssigned();
        BigDecimal coverageRate = BigDecimal.valueOf(qualityReport.getCoverageScore())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal balanceScore = BigDecimal.valueOf(qualityReport.getFairnessScore())
                .setScale(2, RoundingMode.HALF_UP);

        int distinctStaffAssigned = (int) createdSchedules.stream()
                .map(s -> s.getStaff().getId()).distinct().count();

        // ── Conflict count ────────────────────────────────────────────────────────
        // In preview mode: use in-memory count (schedules not yet persisted).
        // In save mode: run full DB-backed conflict detection.
        int actualConflictCount;
        if (save) {
            try {
                actualConflictCount = conflictDetectionService.checkPeriodConflicts(period.getId()).getTotalConflicts();
            } catch (Exception e) {
                log.warn("Conflict detection failed: {}. Falling back to quality-report violation count.", e.getMessage());
                actualConflictCount = qualityReport.getHardViolationCount();
            }
        } else {
            actualConflictCount = qualityReport.getHardViolationCount();
            log.info("Preview mode: hard violation count={}, soft warning count={}",
                    actualConflictCount, qualityReport.getSoftViolationCount());
        }

        if (save) {
            saveMetrics(period, algorithmType, (int) executionTime, coverageRate, balanceScore,
                    actualConflictCount, createdSchedules.size());
            createCompensationDaysForL01InPeriod(period.getId());
        }

        // ── Build schedule summaries (deduplicated) ───────────────────────────────
        // Filter out any shift type the user has marked as removed so the response
        // never surfaces L02/etc. assignments even when stale state (existing in-memory
        // assignments, fall-through assignments from balance-score fallback, etc.) would
        // otherwise include them. The shift_requirement list was already filtered above,
        // but this guards the response shape itself so the UI cannot accidentally display
        // a removed type.
        Set<String> seen = new java.util.LinkedHashSet<>();
        List<AutoScheduleResponse.ScheduleSummary> scheduleSummaries = createdSchedules.stream()
                .filter(s -> s.getShiftType() == null
                        || !removedShiftTypes.contains(s.getShiftType().getId().toUpperCase()))
                .filter(s -> {
                    String key = s.getStaff().getId() + "_" + s.getWorkDate() + "_" + s.getShiftType().getId();
                    return seen.add(key);
                })
                .map(s -> AutoScheduleResponse.ScheduleSummary.builder()
                        .scheduleId(s.getId())
                        .staffId(s.getStaff().getId())
                        .staffName(s.getStaff().getFullName())
                        .workDate(s.getWorkDate().toString())
                        .shiftTypeId(s.getShiftType().getId())
                        .shiftTypeName(s.getShiftType().getName())
                        .staffSpecialtyName(getStaffSpecialtyName(s))
                        .requiredSpecialtyName(getRequiredSpecialtyName(s))
                        .crossSpecialty(isCrossSpecialtyAssignment(s))
                        .requirementId(s.getRequirement() != null ? s.getRequirement().getId() : null)
                        .build())
                .collect(Collectors.toList());

        String actionType = save ? "Xếp lịch tự động thành công" : "Xem trước lịch";
        String qualityGrade = qualityReport.getGrade();
        String scoreMsg = String.format(" [%s %.1f/100]", qualityGrade, qualityReport.getTotalScore());

        var responseBuilder = AutoScheduleResponse.builder()
                .success(true)
                .message(warnings.isEmpty()
                        ? actionType + scoreMsg
                        : actionType + " với " + warnings.size() + " cảnh báo" + scoreMsg)
                .periodId(period.getId())
                .algorithmType(algorithmType)
                .executionTimeMs((int) executionTime)
                .coverageRate(coverageRate)
                .balanceScore(balanceScore)
                .conflictCount(actualConflictCount)
                .totalSchedulesCreated(scheduleSummaries.size())
                .schedules(scheduleSummaries)
                .unassignedDays(unassignedDays)
                .qualityReport(qualityReport)
                .executedAt(LocalDateTime.now());

        if (request.getExcludedStaffIds() != null) {
            responseBuilder.excludedStaffIds(request.getExcludedStaffIds());
        }

        // Deduplicate before computing breakdown — createdSchedules may contain
        // duplicates from preview-mode merging (existing + newly generated).
        java.util.Set<String> seenKeys = new java.util.HashSet<>();
        List<Schedule> dedupedForBreakdown = createdSchedules.stream()
                .filter(s -> seenKeys.add(s.getStaff().getId() + "_" + s.getWorkDate() + "_" + s.getShiftType().getId()))
                .toList();
        Map<String, AutoScheduleResponse.ShiftTypeBreakdown> byShiftType =
                buildByShiftTypeBreakdown(dedupedForBreakdown, requirements);
        responseBuilder.byShiftType(byShiftType);

        return responseBuilder.build();
    }

    /**
     * Result of the initial algorithm dispatch: which algorithm ran, the schedules
     * it produced (possibly after CSP→Greedy fallback / top-up merge), and the
     * GA fairness score if the algorithm emitted one.
     */
    private record AlgorithmDispatchResult(
            String algorithmType,
            List<Schedule> schedules,
            BigDecimal fairnessScore) {}

    /**
     * Whitelist-supported algorithm types — reject unknown values with HTTP 400
     * instead of silently substituting Greedy. Without this guard, callers can
     * request "BACKTRACKING" or "GENETIC" and the run would still be persisted as
     * that algorithm in metrics — masking the fact that no such algorithm ran.
     */
    private static final Set<String> SUPPORTED_ALGORITHMS = Set.of(
            "GREEDY",
            "ROUND_ROBIN",
            "FAIR_ROUND_ROBIN",
            "FAIR",
            "FAIR_GREEDY",
            "CSP_MRV_FC",
            "CSP",
            "V10_LOCAL_SEARCH",
            "V10");

    private AlgorithmDispatchResult dispatchAlgorithm(SchedulePeriod period,
                                                     List<ShiftRequirement> requirements,
                                                     List<Staff> activeStaff,
                                                     boolean save,
                                                     AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
                                                     AutoScheduleRequestDTO request) {
        String algorithmType = request.getAlgorithmType() != null
                ? request.getAlgorithmType().toUpperCase()
                : "CSP_MRV_FC";

        if (!SUPPORTED_ALGORITHMS.contains(algorithmType)) {
            throw new BadRequestException("algorithmType '" + request.getAlgorithmType()
                    + "' không được hỗ trợ. Các giá trị hợp lệ: " + SUPPORTED_ALGORITHMS);
        }

        Set<Integer> excluded = request.getExcludedStaffIds() != null
                ? new HashSet<>(request.getExcludedStaffIds())
                : null;

        Integer maxShiftsPerMonthOverride = request.getMaxShiftsPerMonthOverride();
        if (log.isInfoEnabled() && maxShiftsPerMonthOverride != null) {
            log.info("Auto-schedule run with maxShiftsPerMonthOverride={} (original staff caps untouched in DB)",
                    maxShiftsPerMonthOverride);
        }

	        if ("ROUND_ROBIN".equals(algorithmType)
	                || "FAIR_ROUND_ROBIN".equals(algorithmType)
	                || "FAIR".equals(algorithmType)
	                || "FAIR_GREEDY".equals(algorithmType)) {
	            List<Schedule> schedules = runFairGreedy(period, requirements, activeStaff, save, runtimeConfig, excluded, maxShiftsPerMonthOverride,
	                    Boolean.TRUE.equals(request.getSkipExisting()));
	            return new AlgorithmDispatchResult(algorithmType, schedules, null);
	        }

        if ("CSP_MRV_FC".equals(algorithmType) || "CSP".equals(algorithmType)) {
            // Run CSP-MRV-FC (Constraint Satisfaction with MRV + Forward Checking).
            // The CSP scheduler is the recommended default per spec: it propagates
            // arc-consistency (AC-3) before search and uses learned nogoods, so it
            // can produce a feasible solution for over-constrained periods where
            // Greedy / Round-Robin fail.
            log.info("Running CSP-MRV-FC for period {}", period.getId());
            SchedulingResultWithFairness cspResult = runCsp(period, requirements, activeStaff, save, excluded,
                    Boolean.TRUE.equals(request.getSkipExisting()));
            List<Schedule> cspSchedules = cspResult.schedules();
            log.info("CSP-MRV-FC completed with {} schedules (partial={})", cspSchedules.size(), cspResult.cspPartial());

            if (cspSchedules.isEmpty()) {
                // Fall back to Greedy so the UI never shows "0% coverage" when a
                // feasible plan exists via a different algorithm. CSP-MRV-FC can
                // fail on over-constrained periods (e.g. period 5 with very few
                // Mắt/Răng staff) even though FAIR_GREEDY finds 300+ schedules,
                // and the production UX must keep showing the user a usable plan.
                log.warn("CSP-MRV-FC returned 0 schedules / partial for period {} — falling back to Greedy. Check CspSearchEngine logs for INCONSISTENT result.", period.getId());
                List<Schedule> greedyFallback = runGreedy(period, requirements, activeStaff, save, runtimeConfig, excluded, maxShiftsPerMonthOverride, false);
                log.info("Greedy fallback result: {} schedules", greedyFallback.size());
                return new AlgorithmDispatchResult(algorithmType, greedyFallback, null);
            }

            if (cspResult.cspPartial()) {
                // CSP produced a partial plan under timeout. BUGFIX (was M07 #2):
                // the previous version ran Greedy with save=true here, persisting
                // Greedy's full plan to the DB. Then filterSchedulesExcluding()
                // trimmed the in-memory list to keep only the slots CSP hadn't
                // already covered — but the DB already had the full Greedy plan
                // including duplicate rows. DB and response diverged on every
                // CSP-partial path (response showed merged, DB had CSP + full Greedy).
                //
                // New flow: run Greedy with save=false to obtain the plan in-memory,
                // compute the top-up set (slots CSP didn't cover), persist only those
                // top-up slots via a dedicated REQUIRES_NEW pass, and merge the
                // persisted top-up back into createdSchedules. DB and response now
                // agree on exactly CSP rows + top-up rows.
                log.info("CSP produced partial plan ({} schedules) — running Greedy save=false for top-up planning",
                        cspSchedules.size());
                List<Schedule> greedyPlan = runGreedy(period, requirements, activeStaff, /*save=*/false, runtimeConfig, excluded, maxShiftsPerMonthOverride, false);
	                List<Schedule> greedyTopUp = filterSchedulesExcluding(greedyPlan, cspSchedules);
                log.info("Greedy top-up identified {} new slots (of {} planned) — persisting now",
                        greedyTopUp.size(), greedyPlan.size());
                List<Schedule> persistedTopUp = persistGreedyTopUpOnly(period, greedyTopUp, save, activeStaff);
                List<Schedule> merged = mergeSchedules(cspSchedules, persistedTopUp);
                log.info("CSP+top-up merged total = {} (DB and response now match)", merged.size());
                return new AlgorithmDispatchResult(algorithmType, merged, cspResult.fairnessScore());
            }

            return new AlgorithmDispatchResult(algorithmType, cspSchedules, cspResult.fairnessScore());
        }

        if ("V10_LOCAL_SEARCH".equals(algorithmType) || "V10".equals(algorithmType)) {
            // v10 LocalSearch: incremental statistics + tabu acceptor + sampled
            // neighborhood. Reuses runCspWithResult to rehydrate assignments into
            // JPA entities so save=true persists the same way as CSP/Greedy.
            SchedulingResultWithFairness v10Result = runV10LocalSearch(
                    period, requirements, activeStaff, save, excluded);
            return new AlgorithmDispatchResult(algorithmType, v10Result.schedules(), v10Result.fairnessScore());
        }

        // Explicit Greedy branch (was previously the implicit default for unknown values —
        // now unreachable thanks to the whitelist check above).
        List<Schedule> schedules = runGreedy(period, requirements, activeStaff, save, runtimeConfig, excluded, maxShiftsPerMonthOverride,
                Boolean.TRUE.equals(request.getSkipExisting()));
        return new AlgorithmDispatchResult(algorithmType, schedules, null);
    }

    // ==================== GREEDY ALGORITHM ====================
	    private List<Schedule> runGreedy(SchedulePeriod period, List<ShiftRequirement> requirements,
	                                     List<Staff> activeStaff, boolean save,
	                                     AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
	                                     Set<Integer> excludedStaffIds,
	                                     Integer maxShiftsPerMonthOverride,
	                                     boolean skipExisting) {
	        List<Schedule> createdSchedules = new ArrayList<>();
	        Map<LocalDate, List<ShiftRequirement>> requirementsByDate =
	                GreedyAssignmentEngine.groupRequirementsByDate(requirements);
	
	        // OPTIMIZATION 1: Load all conflict data for entire period in ONE pass (instead of per-day)
	        // OPTIMIZATION 2: Load all shift type counts in ONE query (instead of N×4 queries)
	        PeriodConflictData periodData = loadPeriodConflictData(period, requirements, activeStaff, skipExisting && !save);

        // FAIRNESS: Pre-compute fair share per shift type = ceil(totalDemand[type] / eligiblePool)
        // L04 uses per-specialty pool (spec M05); L01/L02/L03 use full staffPool.
        int staffPool = Math.max(1, activeStaff.size());
        Map<String, Integer> fairSharePerType = GreedyAssignmentEngine.computeFairSharePerTypeWithStaff(
                requirements, staffPool, activeStaff);

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

        // ENHANCED GREEDY: track last work date per staff for fatigue-aware selection.
        // Updated after each successful assignment; used by the fatigue tier in the
        // fairnessComparator to prefer staff who have had at least 1 rest day.
        Map<Integer, LocalDate> staffLastWorkDate = new HashMap<>();
        LocalDate currentDate = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();
        // FAIRNESS: Fair-greedy rotation index per shift type so each staff rotates through shift types evenly.
        // Without this, the same staff keep being picked for L01 until they hit maxShiftsPerStaff, leaving others with 0 L01.
        final Map<String, Map<Integer, Integer>> shiftTypeRotationIndex = new HashMap<>();
        while (!currentDate.isAfter(periodEnd)) {

            List<ShiftRequirement> todayReqs = GreedyAssignmentEngine.sortRequirementsByPriority(
                    requirementsByDate.getOrDefault(currentDate, Collections.emptyList()));

            BatchConflictData todayConflicts = periodData.byDate().get(currentDate);


            // Merge DB adjacent L01 with batch-assigned L01 from this greedy run
            // IMPORTANT: Include BOTH N-1 AND N-2 for back-to-back checking
            // N-1: catches immediate consecutive days
            // N-2: catches cases where staff had L01 on N-2, then day N-1 was their compensation day (which is blocked),
            //       but day N they might have been assigned L01 again (if compensation day wasn't enforced)
            Set<Integer> adjacentL01FromPrev = new HashSet<>();
            Set<Integer> fromBatch1 = l01AssignmentsByDate.get(currentDate.minusDays(1));
            if (fromBatch1 != null) adjacentL01FromPrev.addAll(fromBatch1);
            Set<Integer> fromBatch2 = l01AssignmentsByDate.get(currentDate.minusDays(2));
            if (fromBatch2 != null) adjacentL01FromPrev.addAll(fromBatch2);
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

                // FAIRNESS: Fair-greedy rotation index per shift type. Tracks the LAST iteration
                // a staff was picked (not cumulative count). When all staff share the same
                // iteration value, Java's stable sort falls back to input list order — the
                // first N staff keep getting picked because they're at the front of the list.
                // Storing last-picked ensures staff who were never picked (value 0) stay
                // preferred until everyone has been picked at least once, producing true
                // round-robin rotation.
                final Map<Integer, Integer> lastPickedForType = shiftTypeRotationIndex.computeIfAbsent(
                        shiftTypeId, k -> new HashMap<>());
                final int[] typeIterationHolder = new int[]{0}; // current iteration counter

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
                // E.g., ratio=0.3 → buffer = fairShare * 0.5 = 50% more slots.
                int capBuffer = 1;
                if (ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId) && req.getSpecialty() != null) {
                    var crossConfig = getL04CrossSpecialtyConfig();
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
                        .comparingDouble((Staff s) -> swapPriorityStaffIds().contains(s.getId()) ? 0.0 : 1.0)
                        // Tier 2: MINIMUM GUARANTEE per type — staff with 0 of this type get TOP priority.
                        // This ensures EVERY staff gets at least 1 shift of each type before caps apply.
                        // Only deprioritize if staff already has >= softCapPerType AND softCapPerType >= 1.
                        .thenComparingInt((Staff s) -> {
                            long typeCount = getStaffCountForKey(s.getId(), capturedFairShareKey,
                                    periodData.staffShiftTypeCounts(), capturedRunningCounts);
                            if (typeCount == 0) {
                                return 0; // TOP priority: staff needs at least 1 of this type
                            }
                            // Soft cap: deprioritize if already at soft cap
                            return typeCount >= softCapPerType ? 1 : 0;
                        })
                        // Tier 3: Fewest of THIS shift type/specialty — primary per-type fairness signal
                        .thenComparingLong((Staff s) -> {
                            return getStaffCountForKey(s.getId(), capturedFairShareKey,
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
                        })
                        // Tier 7: Rotation tiebreaker — prefer staff picked the LONGEST ago (smallest last-pick value).
                        // Using lastPickedForType (not cumulative count) means never-picked staff (value 0)
                        // stay top priority until everyone has been picked once. This produces true round-robin
                        // and fixes the 7-vs-16 imbalance where first N staff in input list dominate.
                        .thenComparingInt(s -> lastPickedForType.getOrDefault(s.getId(), 0))
                        // Tier 8 (ENHANCED GREEDY): Fatigue awareness — prefer staff with rest before today.
                        // Penalty = daysSinceLastWork * 100 + (consecutive ? 1000 : 0).
                        // Staff never assigned get priority (lastWorkDate == null → score 0).
                        // This is a soft preference only; if no rested staff available, tired staff still picked.
                        .thenComparingLong((Staff s) -> {
                            LocalDate last = staffLastWorkDate.get(s.getId());
                            if (last == null) return 0L; // never worked → best candidate
                            long gap = ChronoUnit.DAYS.between(last, workDate);
                            if (gap >= 1) return gap * 100L; // had rest → prefer proportionally
                            return 1000L + gap * 100L; // consecutive → penalise
                        });

                List<Staff> eligibleStaff = filterAndSortEligibleStaffBatch(
                        activeStaff, req, excludedStaffIds, assignedStaffIds, todayConflicts, !save,
                        fairnessComparator, periodData, adjacentL01FromPrev, todayCompDayStaffIds,
                        runtimeConfig.getMaxShiftsPerStaff() > 0 ? runtimeConfig.getMaxShiftsPerStaff() : Integer.MAX_VALUE,
                        shiftTypeSpecificMax, fairShareKey, greedyRunningCounts, activeStaff, maxShiftsPerMonthOverride);

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
                    eligibleStaff = filterAndSortEligibleStaffBatch(
                            activeStaff, req, excludedStaffIds, assignedStaffIds, todayConflicts, !save,
                            fairnessComparator, periodData, adjacentL01FromPrev, todayCompDayStaffIds,
                            Integer.MAX_VALUE,
                            fairShare * 5, fairShareKey, greedyRunningCounts, activeStaff, maxShiftsPerMonthOverride);
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
                    Schedule saved = buildAndSaveSchedule(period, staff, req, workDate, save, createdSchedules, skipExisting);
                    if (saved == null) continue;
                    // DEBUG: verify adjacentL01 blocking worked for L01 assignments
                    if (log.isInfoEnabled() && ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        log.info("Greedy L01 SAVED: staff={} date={} (adjPrev={} blocked)", staff.getId(), workDate, adjacentL01FromPrev.size());
                    }
                    trackAssignment(staff, workDate, req.getShiftType().getId());
                    // ENHANCED GREEDY: update last work date for fatigue-aware comparator
                    staffLastWorkDate.put(staff.getId(), workDate);
                    assignedStaffIds.add(staff.getId());
                    assignedCount++;
                    // Update rotation index for this shift type so next time a different staff gets priority.
                    // Store the current iteration (not cumulative count) — Tier 7 uses last-picked to cycle.
                    lastPickedForType.put(staff.getId(), ++typeIterationHolder[0]);

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
                        // Pick the best option among valid compensation days (flexible for Fri/Sat duty)
                        LocalDate compDate = pickBestCompensationDay(workDate, period.getStartDate(), period.getEndDate(), compensationDaysByDate);
                        if (compDate != null) {
                            compensationDaysByDate.computeIfAbsent(compDate, k -> new HashSet<>()).add(staff.getId());
                            // Also track in global conflict caches (replaces what trackAssignment did)
                            String compKey = staff.getId() + "_" + compDate;
                            inMemoryCompensationShiftDates().add(compKey);
                            allCompensationShiftDates().add(compKey);
                        }
                        if (save) {
                            log.debug("Creating compensation day for greedy L01: staff={}, date={} -> comp={}",
                                    staff.getId(), workDate, compDate);
                            if (compDate != null) {
                                schedulePersistenceService.createCompensationDayForAuto(
                                        compensationDayRepository, saved, compDate);
                            } else {
                                createCompensationDayForAuto(saved);
                            }
                        }
                    }
                    String runningCountKey = (ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId) && req.getSpecialty() != null)
                            ? "L04:" + req.getSpecialty().getId()
                            : shiftTypeId;
                    greedyRunningCounts
                            .computeIfAbsent(staff.getId(), k -> new HashMap<>())
                            .merge(runningCountKey, 1L, Long::sum);

                }
            }
            currentDate = currentDate.plusDays(1);
        }
        return createdSchedules;
    }

    // ==================== FAIR GREEDY ALGORITHM (formerly "Round Robin") ====================
    // Despite the old "Round Robin" name, this algorithm is structurally a fair variant of Greedy:
    // it uses a per-shift-type rotation index (fgShiftTypeRotationIndex below) plus a demand-based
    // fair-share cap, not a cyclic permutation. The dispatch table in runScheduling accepts the
    // aliases "ROUND_ROBIN", "FAIR_ROUND_ROBIN", "FAIR", and "FAIR_GREEDY" for back-compat.
    // ==================== V10 LOCAL SEARCH ALGORITHM ====================

    /**
     * v10 entry point: delegates to {@link com.hospital.scheduler.scheduling.LocalSearchScheduler}.
     * Reuses {@link #runCspWithResult} to rehydrate assignments into JPA {@link Schedule}
     * entities so the rest of the pipeline (audit, balance-score fallback, conflict save)
     * can treat v10 the same as CSP.
     */
    private SchedulingResultWithFairness runV10LocalSearch(
            SchedulePeriod period,
            List<ShiftRequirement> requirements,
            List<Staff> activeStaff,
            boolean save,
            Set<Integer> excludedStaffIds) {
        log.info("Running v10-LocalSearch for period {}", period.getId());

        // ── Subtract existing schedules from requirements (preview mode) ──────
        // V10 must not re-assign slots already covered by existing schedules.
        // Load existing schedules and reduce each requirement's requiredCount.
        List<Schedule> existingForPeriod = scheduleRepository.findByPeriodId(period.getId());
        // Index: key = "workDate|shiftTypeId|specialtyId" → count of existing
        java.util.Map<String, Integer> existingCoverage = new java.util.HashMap<>();
        for (Schedule s : existingForPeriod) {
            String specId = s.getRequirement() != null && s.getRequirement().getSpecialty() != null
                    ? String.valueOf(s.getRequirement().getSpecialty().getId())
                    : null;
            String key = s.getWorkDate() + "|" + s.getShiftType().getId() + "|" + (specId != null ? specId : "");
            existingCoverage.merge(key, 1, Integer::sum);
        }

        List<ShiftRequirementInfo> algoReqs = toRequirementInfos(requirements);
        // Adjust: reduce requiredCount by existing coverage (capped at 0)
        List<ShiftRequirementInfo> adjustedReqs = new java.util.ArrayList<>();
        for (ShiftRequirementInfo r : algoReqs) {
            String specId = r.specialtyId() != null ? String.valueOf(r.specialtyId()) : "";
            String key = r.workDate() + "|" + r.shiftTypeId() + "|" + specId;
            int existing = existingCoverage.getOrDefault(key, 0);
            int adjusted = Math.max(0, r.requiredCount() - existing);
            if (adjusted > 0) {
                adjustedReqs.add(new ShiftRequirementInfo(r.shiftTypeId(), r.workDate(), adjusted, r.specialtyId()));
            }
        }
        log.info("V10 requirements adjusted: {} original → {} after subtracting {} existing schedules",
                algoReqs.size(), adjustedReqs.size(), existingForPeriod.size());

        // Load existing compensation days for the period (same as runCsp)
        Set<String> existingCompDays = new HashSet<>();
        for (CompensationDay cd : compensationDayRepository.findByPeriodId(period.getId())) {
            String compKey = cd.getStaff().getId() + "_" + cd.getCompensationDate().toString();
            existingCompDays.add(compKey);
            allCompensationShiftDates().add(compKey);
        }

        List<LeaveRequest> v10LeaveRequests = leaveRequestRepository.findApprovedInRange(
                period.getStartDate(), period.getEndDate());

        SchedulingResult result = localSearchScheduler.solve(
                activeStaff,
                period.getStartDate(),
                period.getEndDate(),
                adjustedReqs,
                existingCompDays,
                v10LeaveRequests,
                excludedStaffIds != null ? excludedStaffIds : new HashSet<>());

        // Rehydrate Map-based assignments → JPA entities, then persist if save=true.
        SchedulingResultWithFairness rehydrated = runCspWithResult(result, period, requirements);

        // ── Filter out assignments that conflict with existing schedules ─────
        // V10 doesn't know about existing schedules; after rehydration, remove
        // any schedule that creates a same-day shift conflict (L01↔L02, L03↔L04)
        // with an existing schedule from the DB.
        if (!existingForPeriod.isEmpty()) {
            // Index existing schedules: staffId_date → set of shift types
            java.util.Map<String, java.util.Set<String>> existingByStaffDate = new java.util.HashMap<>();
            for (Schedule s : existingForPeriod) {
                String key = s.getStaff().getId() + "|" + s.getWorkDate();
                existingByStaffDate.computeIfAbsent(key, k -> new java.util.HashSet<>())
                        .add(s.getShiftType().getId());
            }
            List<Schedule> filtered = new java.util.ArrayList<>();
            for (Schedule s : rehydrated.schedules()) {
                String key = s.getStaff().getId() + "|" + s.getWorkDate();
                java.util.Set<String> existingTypes = existingByStaffDate.get(key);
                if (existingTypes != null && !existingTypes.isEmpty()) {
                    boolean conflict = false;
                    for (String existingType : existingTypes) {
                        if (("L01".equals(s.getShiftType().getId()) && "L02".equals(existingType))
                                || ("L02".equals(s.getShiftType().getId()) && "L01".equals(existingType))
                                || ("L03".equals(s.getShiftType().getId()) && "L04".equals(existingType))
                                || ("L04".equals(s.getShiftType().getId()) && "L03".equals(existingType))) {
                            conflict = true;
                            break;
                        }
                    }
                    if (conflict) continue; // skip conflicting assignment
                }
                filtered.add(s);
            }
            if (filtered.size() < rehydrated.schedules().size()) {
                log.info("V10 filtered out {} conflicting assignments with existing schedules",
                        rehydrated.schedules().size() - filtered.size());
                rehydrated = new SchedulingResultWithFairness(filtered, rehydrated.fairnessScore());
            }
        }
        if (save) {
            for (Schedule s : rehydrated.schedules()) {
                Schedule saved = scheduleRepository.save(s);
                if (saved != null) {
                    auditHistoryService.logAction(
                            "schedule", saved.getId(),
                            AuditHistory.ActionType.INSERT, null, saved, null);
                }
            }
        }
        log.info("v10-LocalSearch completed: {} schedules (valid={}, partial={})",
                rehydrated.schedules().size(), result.isValid(), result.isPartial());
        return rehydrated;
    }

	    private List<Schedule> runFairGreedy(SchedulePeriod period, List<ShiftRequirement> requirements,
	                                          List<Staff> activeStaff, boolean save,
	                                          AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
	                                          Set<Integer> excludedStaffIds,
	                                          Integer maxShiftsPerMonthOverride,
	                                          boolean skipExisting) {
	        List<Schedule> createdSchedules = new ArrayList<>();
	        // Per-type rotation index — same structure as Greedy's shiftTypeRotationIndex so that
	        // Fair Greedy also rotates each staff through each shift type independently.
	        final Map<String, Map<Integer, Integer>> fgShiftTypeRotationIndex = new HashMap<>();
	
	        Map<LocalDate, List<ShiftRequirement>> requirementsByDate =
	                GreedyAssignmentEngine.groupRequirementsByDate(requirements);
	
	        // FAIRNESS: Compute fair-share cap per shift type from actual demand.
	        // L04 uses per-specialty pool (spec M05 — chuyên gia phải đúng chuyên khoa).
	        // L01/L02/L03 use full staff pool.
	        final int fgStaffPool = Math.max(1, activeStaff.size());
	        final Map<String, Integer> fgFairSharePerType = GreedyAssignmentEngine.computeFairSharePerTypeWithStaff(
	                requirements, fgStaffPool, activeStaff);
	        log.info("FG fairSharePerType: L01={} L02={} L03={} L04={}",
	                fgFairSharePerType.get(ConflictDetectionService.SHIFT_TYPE_L01),
	                fgFairSharePerType.get(ConflictDetectionService.SHIFT_TYPE_L02),
	                fgFairSharePerType.get(ConflictDetectionService.SHIFT_TYPE_L03),
	                fgFairSharePerType.get(ConflictDetectionService.SHIFT_TYPE_L04));
	
	        // OPTIMIZATION: Load ALL conflict data in ONE query (same as Greedy)
	        PeriodConflictData periodData = loadPeriodConflictData(period, requirements, activeStaff, skipExisting && !save);

        // Track L01 assignments by date for adjacent-day back-to-back checking (same as Greedy)
        Map<LocalDate, Set<Integer>> l01AssignmentsByDate = new HashMap<>();

        // Track compensation days created during this run (same as Greedy)
        // When L01 is created on day N, staff cannot work any shift on their compensation day
        Map<LocalDate, Set<Integer>> compensationDaysByDate = new HashMap<>();

        // Track assignments created during this run so fairness decisions see current in-memory load.
        // Keys are plain shift type (L01/L02/L03) or L04 per-specialty (L04:<specialtyId>).
        Map<Integer, Map<String, Long>> fgRunningCounts = new HashMap<>();

        LocalDate currentDate = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();
        while (!currentDate.isAfter(periodEnd)) {

            List<ShiftRequirement> todayReqs = GreedyAssignmentEngine.sortRequirementsByPriority(
                    requirementsByDate.getOrDefault(currentDate, Collections.emptyList()));

            BatchConflictData todayConflicts = periodData.byDate().get(currentDate);


            // Merge DB adjacent L01 with batch-assigned L01 from this run (same as Greedy)
            Set<Integer> adjacentL01FromPrev = new HashSet<>();
            Set<Integer> fromBatch1 = l01AssignmentsByDate.get(currentDate.minusDays(1));
            if (fromBatch1 != null) adjacentL01FromPrev.addAll(fromBatch1);
            Set<Integer> fromBatch2 = l01AssignmentsByDate.get(currentDate.minusDays(2));
            if (fromBatch2 != null) adjacentL01FromPrev.addAll(fromBatch2);
            if (todayConflicts != null && todayConflicts.adjacentL01StaffIds() != null) {
                adjacentL01FromPrev.addAll(todayConflicts.adjacentL01StaffIds());
            }

            // Get compensation days for today (created from earlier days in this run)
            Set<Integer> todayCompDayStaffIds = compensationDaysByDate.getOrDefault(currentDate, Collections.emptySet());

            Set<Integer> assignedStaffIds = new HashSet<>();
            final int fgGlobalMaxRR = runtimeConfig.getMaxShiftsPerStaff() > 0 ? runtimeConfig.getMaxShiftsPerStaff() : Integer.MAX_VALUE;
            for (ShiftRequirement req : todayReqs) {
                // NOTE: L01 can appear multiple times in todayReqs (separate ShiftRequirement entries).
                // We do NOT skip subsequent L01 requirements here — the filterAndSortEligibleStaffBatch
                // will handle it by checking assignedStaffIds and L01-specific conflicts.

                final LocalDate workDate = currentDate;
                final String shiftTypeId = req.getShiftType().getId();
                final boolean isWeekend = currentDate.getDayOfWeek() == DayOfWeek.SATURDAY
                        || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY;

                // FAIRNESS: Use demand-based fair-share cap per shift type (replaces hardcoded multipliers).
                // Ensures every staff gets ~equal share of each shift type.
                // L04: use per-specialty key (e.g. "L04:5") so staff are capped per-specialty, not globally.
                // L01/L02/L03: +1 buffer for coverage.
                final String fgFairShareKey = (ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)
                        && req.getSpecialty() != null)
                        ? shiftTypeId + ":" + req.getSpecialty().getId()
                        : shiftTypeId;
                final int fgFairShare = fgFairSharePerType.getOrDefault(fgFairShareKey, fgFairSharePerType.getOrDefault(shiftTypeId, 20));
                // Hard cap is intentionally tight: fairShare + 1 is enough to absorb remainder
                // while preserving max deviation <= 1 whenever constraints allow it.
                final int fgShiftTypeMax = fgFairShare + 1;
                final int fgSoftMax = fgFairShare;

                // Per-type rotation index: stores LAST iteration staff was picked (not cumulative count).
                // Cumulative counts tie across staff after they've each been picked N times, then
                // Java stable sort falls back to input order — the first N staff keep getting picked.
                // Last-picked semantics produce true round-robin: never-picked staff stay preferred
                // until everyone has been picked at least once.
                final Map<Integer, Integer> fgLastPickedForType = fgShiftTypeRotationIndex.computeIfAbsent(
                        fgFairShareKey, k -> new HashMap<>());
                final int[] fgTypeIterationHolder = new int[]{0}; // current iteration counter
                final String fgCapturedKey = fgFairShareKey;
                final Map<Integer, Map<String, Long>> capturedFgCounts = fgRunningCounts;
                Comparator<Staff> fairnessComparator = Comparator
                        // Tier 1: SWAP PRIORITY
                        .comparingDouble((Staff s) -> swapPriorityStaffIds().contains(s.getId()) ? 0.0 : 1.0)
                        // Tier 2: SOFT CAP — per-specialty for L04, per-type for others
                        .thenComparingInt((Staff s) -> {
                            long typeCount = getStaffCountForKey(s.getId(), fgCapturedKey,
                                    periodData.staffShiftTypeCounts(), capturedFgCounts);
                            return typeCount >= fgSoftMax ? 1 : 0;
                        })
                        // Tier 3: Fewest of THIS shift type (per-specialty for L04)
                        .thenComparingLong((Staff s) -> {
                            return getStaffCountForKey(s.getId(), fgCapturedKey,
                                    periodData.staffShiftTypeCounts(), capturedFgCounts);
                        })
                        // Tier 4: Per-type rotation index — tiebreak within same per-type count
                        .thenComparingInt(s -> fgLastPickedForType.getOrDefault(s.getId(), 0))
                        // Tier 5: Total shifts — overall balance tiebreak using current in-run load
                        .thenComparingLong(s -> getTotalStaffCount(
                                s.getId(), periodData.staffShiftTypeCounts(), capturedFgCounts))
                        // Tier 6: Weekend penalty
                        .thenComparingDouble(s -> {
                            if (!isWeekend) return 0.0;
                            long totalShifts = getTotalStaffCount(
                                    s.getId(), periodData.staffShiftTypeCounts(), capturedFgCounts);
                            return totalShifts * runtimeConfig.getWeekendWeight().doubleValue();
                        });

                List<Staff> eligibleStaff = filterAndSortEligibleStaffBatch(
                        activeStaff, req, excludedStaffIds, assignedStaffIds, todayConflicts, false,
                        fairnessComparator, periodData, adjacentL01FromPrev, todayCompDayStaffIds,
                        fgGlobalMaxRR, fgShiftTypeMax, fgFairShareKey, fgRunningCounts, activeStaff, maxShiftsPerMonthOverride);

                // Fallback: if no staff eligible due to fair-share cap, relax cap.
                // For L04 with cross-specialty, calculate fallback proportionally to crossConfig.ratio().
                if (eligibleStaff.isEmpty() && req.getRequiredStaffCount() > 0) {
                    int fallbackCap;
                    if (ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)) {
                        var crossConfig = getL04CrossSpecialtyConfig();
                        if (crossConfig.enabled()) {
                            fallbackCap = Math.max(fgFairShare * 2,
                                    (int) Math.ceil(fgFairShare * (1 + crossConfig.ratio() * 2)));
                        } else {
                            fallbackCap = fgFairShare * 5;
                        }
                    } else {
                        fallbackCap = fgFairShare * 2;
                    }
                    eligibleStaff = filterAndSortEligibleStaffBatch(
                            activeStaff, req, excludedStaffIds, assignedStaffIds, todayConflicts, false,
                            fairnessComparator, periodData, adjacentL01FromPrev, todayCompDayStaffIds,
                            Integer.MAX_VALUE, fallbackCap, fgFairShareKey, fgRunningCounts, activeStaff, maxShiftsPerMonthOverride);
                    if (!eligibleStaff.isEmpty()) {
                        log.debug("FG fallback cap: date={} type={} relaxed to {}",
                                currentDate, shiftTypeId, fallbackCap);
                    }
                }

                int toAssign = Math.min(req.getRequiredStaffCount(), eligibleStaff.size());
                int assignedCount = 0;
                int staffIndex = 0;
                while (assignedCount < toAssign && staffIndex < eligibleStaff.size()) {
                    Staff staff = eligibleStaff.get(staffIndex);
                    staffIndex++;
                    Schedule saved = buildAndSaveSchedule(period, staff, req, workDate, save, createdSchedules, skipExisting);
	                    if (saved == null) continue;
                    trackAssignment(staff, workDate, req.getShiftType().getId());
                    assignedStaffIds.add(staff.getId());
                    // Per-type rotation: store LAST iteration this staff was picked (not cumulative count).
                    fgShiftTypeRotationIndex.computeIfAbsent(shiftTypeId, k -> new HashMap<>())
                            .put(staff.getId(), ++fgTypeIterationHolder[0]);
                    assignedCount++;

                    // FIX: Track L01 assignment for adjacent-day checking (same as Greedy)
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        // Track for adjacent-day back-to-back check
                        l01AssignmentsByDate.computeIfAbsent(workDate, k -> new HashSet<>()).add(staff.getId());
                        // FIX: Track compensation day — staff cannot work any shift on their compensation day
                        // Pick the best option among valid compensation days (flexible for Fri/Sat duty)
                        LocalDate compDate = pickBestCompensationDay(workDate, period.getStartDate(), period.getEndDate(), compensationDaysByDate);
                        if (compDate != null) {
                            compensationDaysByDate.computeIfAbsent(compDate, k -> new HashSet<>()).add(staff.getId());
                            // Also track in global conflict caches (replaces what trackAssignment did)
                            String compKey = staff.getId() + "_" + compDate;
                            inMemoryCompensationShiftDates().add(compKey);
                            allCompensationShiftDates().add(compKey);
                        }
                        if (save) {
                            log.debug("Creating compensation day for fair-greedy L01: staff={}, date={} -> comp={}",
                                    staff.getId(), workDate, compDate);
                            if (compDate != null) {
                                schedulePersistenceService.createCompensationDayForAuto(
                                        compensationDayRepository, saved, compDate);
                            } else {
                                createCompensationDayForAuto(saved);
                            }
                        }
                    }
                    // Update in-memory counts for the current run so the next assignment sees fresh load.
                    // L04 is tracked per specialty; L01/L02/L03 use plain type keys.
                    fgRunningCounts
                            .computeIfAbsent(staff.getId(), k -> new HashMap<>())
                            .merge(fgFairShareKey, 1L, Long::sum);

                }
            }
            currentDate = currentDate.plusDays(1);
        }
        return createdSchedules;
    }

    // ==================== CSP-MRV-FC ALGORITHM ====================

    /**
     * Run CSP-MRV-FC (Constraint Satisfaction with MRV + Forward Checking).
     *
     * <p>Pipeline:
     * <ol>
     *   <li>Build {@code ProblemData} via {@link com.hospital.scheduler.algorithm.CspDataBuilder}
     *       which also runs initial AC-3 arc-consistency.</li>
     *   <li>{@link com.hospital.scheduler.algorithm.CspSearchEngine} performs
     *       backtracking search with MRV variable ordering, forward-checking
     *       propagation, and nogood learning from conflicts.</li>
     *   <li>{@link com.hospital.scheduler.algorithm.CspResultBuilder} shapes the
     *       raw assignment into the domain {@link SchedulingResult}.</li>
     * </ol>
     *
     * <p>The result's {@code assignments} map uses key format
     * {@code "staffId|workDate"} (pipe-separated) and value = shift type id
     * (e.g. {@code L01}, {@code L02}, …). We translate that into JPA
     * {@link Schedule} entities, including the L01 compensation-day derivation
     * so the saved plan stays consistent with the compensation rules.
     */
	    private SchedulingResultWithFairness runCsp(
	            SchedulePeriod period,
	            List<ShiftRequirement> requirements,
	            List<Staff> activeStaff,
	            boolean save,
	            Set<Integer> excludedStaffIds,
	            boolean skipExisting) {
	
        try {
            // Translate DB requirements -> algorithm DTO
            List<ShiftRequirementInfo> cspRequirements = toRequirementInfos(requirements);
	
	            // Existing compensation days (across the period, to avoid
	            // creating overlapping days when we map back to Schedule).
	            // Skip in clean preview mode (skipExisting=true) so CSP sees blank slate
	            Set<String> existingCompDays = new HashSet<>();
	            if (!skipExisting || save) {
	                for (CompensationDay cd : compensationDayRepository.findByPeriodId(period.getId())) {
	                    // Use underscore separator for consistency with GA and in-memory cache
	                    String compKey = cd.getStaff().getId() + "_" + cd.getCompensationDate().toString();
	                    existingCompDays.add(compKey);
	                    // CRITICAL: Also add to in-memory cache to prevent duplicate compensation day creation
	                    allCompensationShiftDates().add(compKey);
	                }
	            }

            // Approved leave requests in the window — CSP encodes them as
            // hard domain-pruning constraints in CspDataBuilder.
            List<LeaveRequest> leaveRequests = leaveRequestRepository.findApprovedInRange(
                    period.getStartDate(), period.getEndDate());

            // Run CSP. Thread the L04 allowed-specialties from AutoGenConfig so
            // the CSP's domain pruning uses the same definition as
            // StaffShiftTypeEligibility / ScheduleQualityScorer — otherwise the
            // search and the scoring would silently disagree on who is eligible
            // for L04 (and the earlier hardcoded "Bác sĩ / Điều dưỡng" check in
            // CspDataBuilder would invalidate every staff member).
            List<String> l04Allowed = algorithmConfigService.getAutoGenConfig()
                    .map(cfg -> cfg.l04AllowedSpecialties() != null ? cfg.l04AllowedSpecialties() : List.<String>of())
                    .orElse(List.of());
            // Preview path uses an 8s wall-clock cap so the UI returns fast even
            // when the full search would need 30s. The apply path keeps the
            // default 30s budget — see CSPScheduler#solve vs solveForPreview.
            boolean l04CrossSpecialty = algorithmConfigService.getAutoGenConfig()
                    .map(cfg -> cfg.l04CrossSpecialty())
                    .orElse(false);
            // Read maxShiftsPerStaff from runtime config (same source as Greedy)
            // instead of the entity field maxShiftsPerMonth (which defaults to 5
            // in seed data and makes CSP over-constrained).
            int maxShiftsPerStaffCsp = algorithmConfigService.getRuntimeConfig().getMaxShiftsPerStaff();
            SchedulingResult cspResult = save
                    ? cspScheduler.solve(
                            activeStaff,
                            period.getStartDate(),
                            period.getEndDate(),
                            cspRequirements,
                            existingCompDays,
                            leaveRequests,
                            excludedStaffIds,
                            l04Allowed,
                            l04CrossSpecialty,
                            maxShiftsPerStaffCsp)
                    : cspScheduler.solveForPreview(
                            activeStaff,
                            period.getStartDate(),
                            period.getEndDate(),
                            cspRequirements,
                            existingCompDays,
                            leaveRequests,
                            excludedStaffIds,
                            l04Allowed,
                            l04CrossSpecialty,
                            maxShiftsPerStaffCsp);

            if (cspResult == null || !cspResult.isValid()) {
                log.warn("CSP-MRV-FC returned no feasible solution for period {}: {}",
                        period.getId(), cspResult == null ? "null result" : cspResult.getErrors());
                return new SchedulingResultWithFairness(new ArrayList<>(), BigDecimal.ZERO);
            }
            if (cspResult.isPartial()) {
                // CSP returned a partial plan under timeout pressure (e.g. the
                // 23-staff Period 5 workload with 6 specialties). Signal the
                // fallback path downstream so Greedy can top up coverage
                // instead of presenting only the partial set to the user.
                // The partial set itself is preserved (not discarded) so the
                // orchestrator can merge it with the Greedy top-up. Pure
                // discard was the previous behaviour and it lost all the
                // fairness work CSP had already done within its 30s budget.
                log.info("CSP-MRV-FC returned a partial plan for period {} ({} assignments) — Greedy will top up missing slots",
                        period.getId(), cspResult.getScheduleCount());
                return new SchedulingResultWithFairness(CspAssignmentEngine.cspPartialToSchedules(cspResult, period, requirements, activeStaff, compensationDateCalculator),
                        cspResult.getFairnessScore() != null ? cspResult.getFairnessScore() : BigDecimal.ZERO,
                        true);
            }

            // Convert domain assignments -> Schedule entities
            List<Schedule> createdSchedules = new ArrayList<>();
            Set<String> allCompensationDays = new HashSet<>(existingCompDays);

            // Use compensation days from CSP result (already computed with flexible
            // best-option logic via ProblemData.compDayIdx). Convert pipe → underscore.
            if (cspResult.getCompensationDays() != null) {
                for (String cd : cspResult.getCompensationDays()) {
                    String[] parts = cd.split("\\|");
                    if (parts.length >= 2) {
                        allCompensationDays.add(parts[0] + "_" + parts[1]);
                    }
                }
            }

            // Second pass: persist
            // Requirements are pre-persisted in runScheduling() so the FK on
            // schedule.requirement_id resolves to a managed entity.
            for (Map.Entry<String, String> entry : cspResult.getAssignments().entrySet()) {
                String[] parts = entry.getKey().split("\\|");
                if (parts.length < 2) continue;
                Integer staffId = Integer.parseInt(parts[0]);
                LocalDate workDate = LocalDate.parse(parts[1]);
                String shiftTypeId = entry.getValue();

                if (allCompensationDays.contains(staffId + "_" + workDate)) {
                    log.debug("Skipping CSP assignment {} - staff is on a compensation day", entry.getKey());
                    continue;
                }

                Staff staff = activeStaff.stream()
                        .filter(s -> s.getId().equals(staffId))
                        .findFirst().orElse(null);
                if (staff == null) continue;

                ShiftRequirement req = requirements.stream()
                        .filter(r -> r.getWorkDate().equals(workDate)
                                && r.getShiftType().getId().equals(shiftTypeId))
                        .findFirst().orElse(null);
                if (req == null) continue;

                Schedule schedule = Schedule.builder()
                        .period(period)
                        .staff(staff)
                        .shiftType(req.getShiftType())
                        .workDate(workDate)
                        .requirement(req)
                        .hasConflict(false)
                        .build();

                if (save) {
                    boolean exists = scheduleRepository.existsByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                            period.getId(), staff.getId(), shiftTypeId, workDate);
                    if (!exists) {
                        Schedule saved = scheduleRepository.save(schedule);
                        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                            // Use the compensation date that CSP actually chose (flexible for Fri/Sat)
                            String lookupKey = staffId + "|" + workDate;
                            LocalDate cspCompDate = cspResult.getL01CompensationDateMap() != null
                                    ? cspResult.getL01CompensationDateMap().get(lookupKey)
                                    : null;
                            if (cspCompDate != null) {
                                schedulePersistenceService.createCompensationDayForAuto(
                                        compensationDayRepository, saved, cspCompDate);
                            } else {
                                createCompensationDayForAuto(saved);
                            }
                        }
                        auditHistoryService.logAction("schedule", saved.getId(),
                                AuditHistory.ActionType.INSERT, null, saved, null);
                        createdSchedules.add(saved);
                    }
                } else {
                    createdSchedules.add(schedule);
                }
            }

            // Post-process: drop any persisted schedule that lands on a comp day
            // from this very run (defensive — second pass already skipped most,
            // but L01 cross-day pairs in same run may still slip through).
            // Only valid when we actually persisted: with save=false the entity
            // manager was not touched and the requirement entities stay attached,
            // but the flush/clear cycle below would detach them mid-loop and
            // every following iteration would rebuild a transient Schedule from
            // a detached ShiftRequirement → TransientPropertyValueException.
            if (save && !createdSchedules.isEmpty()) {
                entityManager.flush();
                entityManager.clear();
                List<Schedule> compDayViolations = createdSchedules.stream()
                        .filter(s -> allCompensationDays.contains(
                                s.getStaff().getId() + "_" + s.getWorkDate().toString()))
                        .toList();
                if (!compDayViolations.isEmpty()) {
                    log.warn("CSP produced {} comp-day violations, removing them", compDayViolations.size());
                    for (Schedule violation : compDayViolations) {
                        scheduleRepository.delete(violation);
                        createdSchedules.remove(violation);
                    }
                    entityManager.flush();
                }
            }

            BigDecimal fairnessScore = cspResult.getFairnessScore() != null
                    ? cspResult.getFairnessScore()
                    : BigDecimal.ZERO;
            log.info("CSP-MRV-FC produced {} schedules with fairness={} ({}ms)",
                    createdSchedules.size(), fairnessScore, cspResult.getExecutionTimeMs());
            return new SchedulingResultWithFairness(createdSchedules, fairnessScore);

        } catch (Exception e) {
            // Explicit, loud error so we never silently fall through to Greedy
            // and hide a real bug in the CSP pipeline.
            log.error("CSP-MRV-FC failed for period {}: {}", period.getId(), e.getMessage(), e);
            return new SchedulingResultWithFairness(new ArrayList<>(), BigDecimal.ZERO);
        }
    }


    /**
     * Convert the CSP partial assignment map into transient {@link Schedule}
     * entities that the orchestrator can merge with the Greedy top-up.
     * Mirrors the conversion block inside {@link #runCsp} but skips the
     * persistence path — the orchestrator decides whether to persist
     * based on its own {@code save} flag.
     *
     * Defensive: drops any CSP assignment that lands on a compensation day
     * generated by the partial plan itself (same logic as the full-result
     * conversion, kept here so the merged plan stays consistent with the
     * compensation rules).
     */
    // DELEGATED to CspAssignmentEngine.cspPartialToSchedules (M07 refactor)
    private List<Schedule> cspPartialToSchedules(
            SchedulingResult cspResult,
            SchedulePeriod period,
            List<ShiftRequirement> requirements,
            List<Staff> activeStaff) {
        return CspAssignmentEngine.cspPartialToSchedules(cspResult, period, requirements, activeStaff, compensationDateCalculator);
    }

    // DELEGATED to CspAssignmentEngine.filterSchedulesExcluding (M07 refactor)
    private List<Schedule> filterSchedulesExcluding(List<Schedule> candidates, List<Schedule> kept) {
        return CspAssignmentEngine.filterSchedulesExcluding(candidates, kept);
    }

    // DELEGATED to CspAssignmentEngine.mergeSchedules (M07 refactor)
    private List<Schedule> mergeSchedules(List<Schedule> cspPartial, List<Schedule> topUp) {
        return CspAssignmentEngine.mergeSchedules(cspPartial, topUp);
    }

    /**
     * Persist only the Greedy top-up slots (those not already covered by CSP's
     * partial plan). Returns the schedules actually written to the DB. When
     * {@code save=false} (preview mode) this is a no-op and returns the input
     * list untouched — preview wants in-memory data only, no DB writes.
     *
     * <p>BUGFIX (was M07 #2) helper. Called from the CSP-partial branch to
     * keep DB rows aligned with the response schedules list.
     */
    private List<Schedule> persistGreedyTopUpOnly(SchedulePeriod period,
                                                  List<Schedule> topUp,
                                                  boolean save,
                                                  List<Staff> activeStaff) {
        if (topUp == null || topUp.isEmpty()) return List.of();
        if (!save) {
            log.debug("persistGreedyTopUpOnly: preview mode — {} schedules kept in-memory only", topUp.size());
            return topUp;
        }

        List<Schedule> persisted = new ArrayList<>(topUp.size());
        int failed = 0;
        for (Schedule s : topUp) {
            try {
                // Re-validate against current DB state (CSP/greedy in-memory data
                // may be slightly stale by the time we persist, especially after
                // the multi-second CSP run). Skip duplicates and conflicts that
                // surfaced since the in-memory plan was built.
                if (scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                        period.getId(),
                        s.getStaff().getId(),
                        s.getShiftType().getId(),
                        s.getWorkDate()).isPresent()) {
                    failed++;
                    log.debug("persistGreedyTopUpOnly: skip duplicate staff={} date={} shift={}",
                            s.getStaff().getId(), s.getWorkDate(), s.getShiftType().getId());
                    continue;
                }
                // BUGFIX (A1): Greedy top-up plan is generated by runGreedy(save=false)
                // which does NOT consult ConflictDetectionService — it only enforces
                // duplicates + fair-share + specialty. Without this guard, the top-up
                // can persist a staff onto a date that already has an L01 (when adding
                // L02/L03) or already has L03 (when adding L04), violating the
                // "L01 vs L02 cùng ngày" and "L03 vs L04 cùng ngày" business rules
                // (CRITICAL constraint #1 and #2 from PROJECT_CONTEXT.md). 146 such
                // conflicts surfaced on period 4 production apply; we now reject
                // business-conflict top-up slots at persist time and let coverage
                // report reflect the true fillable plan.
                if (conflictDetectionService.hasAnyConflict(
                        s.getStaff().getId(),
                        s.getWorkDate(),
                        s.getShiftType().getId(),
                        /*excludeScheduleId*/ null)) {
                    failed++;
                    log.warn("persistGreedyTopUpOnly: skip BUSINESS-SHIFT-CONFLICT staff={} date={} shift={} (would violate L01↔L02 / L03↔L04 rule)",
                            s.getStaff().getId(), s.getWorkDate(), s.getShiftType().getId());
                    continue;
                }
                // Strip the unsaved entity state and re-insert via the canonical
                // build path so compensation-day and audit side-effects run.
                s.setId(null);
                s.setPeriod(period);
                Schedule saved = scheduleRepository.save(s);
                persisted.add(saved);

                if (Boolean.TRUE.equals(s.getShiftType().getIsOvernight())) {
                    // Inline compensation-day creation for the L01 top-up slot.
                    // Reusing createCompensationDaysForL01InPeriod() at this
                    // granularity would re-scan the entire period, which is
                    // wasteful for a single schedule. The INSERT IGNORE pattern
                    // mirrors the canonical path and safely handles duplicate
                    // compensation dates from concurrent runs.
                    try {
                        LocalDate compDate = compensationDateCalculator.calculate(s.getWorkDate());
                        compensationDayRepository.insertIgnoreCompensationDay(
                                s.getStaff().getId(),
                                period.getId(),
                                s.getId(),
                                s.getWorkDate(),
                                compDate,
                                "Ngày nghỉ bù tự động từ CSP-partial Greedy top-up (shift_id=" + s.getId() + ")"
                        );
                    } catch (Exception compEx) {
                        log.warn("Top-up compensation day creation failed for shift id={}: {}",
                                s.getId(), compEx.getMessage());
                    }
                }
            } catch (Exception ex) {
                failed++;
                log.warn("persistGreedyTopUpOnly: failed to save staff={} date={} shift={}: {}",
                        s.getStaff().getId(), s.getWorkDate(), s.getShiftType().getId(), ex.getMessage());
            }
        }
        if (failed > 0) {
            log.warn("persistGreedyTopUpOnly: {} of {} top-up slots skipped due to duplicates/conflicts",
                    failed, topUp.size());
        }
        return persisted;
    }

    // ==================== M07-F06: Báo cáo ngày chưa phân công ====================
    // DELEGATED to UnassignedDaysReportBuilder (M07 refactor)
    public Map<String, Object> getUnassignedDaysReport(Integer periodId) {
        SchedulePeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + periodId));
        List<ShiftRequirement> requirements = requirementRepository.findByPeriodId(periodId);
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);
        return unassignedDaysReportBuilder.buildReport(period, requirements, schedules);
    }

    // ==================== M07-F08: Đề xuất người thay thế ====================
    // DELEGATED to ReplacementSuggestionService (M07 refactor)
    public Map<String, Object> suggestReplacements(Integer scheduleId) {
        return replacementSuggestionService.suggestReplacements(scheduleId, null);
    }

    /**
     * Suggest replacement staff for a given schedule, optionally excluding a list of
     * staff IDs (e.g. managers who want to re-run the suggestion with the previously
     * suggested-but-rejected staff filtered out).
     */
    public Map<String, Object> suggestReplacements(Integer scheduleId, Set<Integer> excludedStaffIds) {
        return replacementSuggestionService.suggestReplacements(scheduleId, excludedStaffIds);
    }

    // (Original implementation moved to ReplacementSuggestionService)

    // ==================== M07-F09: Data biểu đồ cân bằng tải ====================
    // DELEGATED to WorkloadChartBuilder (M07 refactor)
    public Map<String, Object> getWorkloadChartData(Integer periodId) {
        return workloadChartBuilder.getWorkloadChartData(periodId);
    }

    public Map<String, Object> getWorkloadChartData(Integer periodId, String shiftTypeId) {
        return workloadChartBuilder.getWorkloadChartData(periodId, shiftTypeId);
    }

    // (Original implementation moved to WorkloadChartBuilder)

    // ==================== LOCAL SEARCH FAIRNESS OPTIMIZER ====================

    private int optimizeFairnessBySafeReassignment(List<Schedule> schedules,
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
     *
     * <p>DELEGATED to PostAssignmentOptimizer (M07 refactor). The body is preserved
     * here temporarily for cross-layer data coupling that requires in-memory mutation
     * of the schedules list — once the orchestrator adopts PostAssignmentOptimizer
     * end-to-end this wrapper can be removed.
     */
    private int guaranteeMinimumShifts(List<Schedule> schedules,
                                       List<Staff> staffWithoutShifts,
                                       List<ShiftRequirement> requirements,
                                       List<Staff> activeStaff) {
        return postAssignmentOptimizer.guaranteeMinimumShifts(schedules, staffWithoutShifts, requirements, activeStaff, stateAccessor);
    }

    private Map<String, Map<Integer, Long>> buildSafeRebalanceCounts(List<Schedule> schedules, List<Staff> activeStaff) {
        // ... preserved for local-search delegation compatibility — see PostAssignmentOptimizer
        return postAssignmentOptimizer.buildSafeRebalanceCountsCompat(schedules, activeStaff);
    }

    private RebalanceMove findBestSafeRebalanceMove(List<Schedule> schedules,
                                                    List<Staff> activeStaff,
                                                    Map<Integer, Staff> staffById,
                                                    Map<String, Map<Integer, Long>> counts) {
        // BUGFIX: the compat shim returns null when no eligible donor/receiver pair is
        // available (e.g. all over-staffed days have blocked L01 chains). Previously
        // RebalanceMove.wrap(...) dereferenced null.schedule() and threw NPE here.
        // Returning null makes the optimizer exit early via the existing null-check
        // in optimizeFairnessBySafeReassignment.
        com.hospital.scheduler.service.scheduling.PostAssignmentOptimizer.RebalanceMove inner =
                postAssignmentOptimizer.findBestSafeRebalanceMoveCompat(schedules, activeStaff, staffById, counts, stateAccessor);
        return RebalanceMove.wrap(inner);
    }

    private boolean isSafeLocalSearchReassignment(Schedule schedule, Staff candidate, List<Schedule> schedules) {
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
        if (allCompensationShiftDates().contains(compKey) || inMemoryCompensationShiftDates().contains(compKey)) {
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

    private Set<Integer> eligiblePoolForRebalanceKey(String key, List<Staff> activeStaff) {
        if (key.startsWith(ConflictDetectionService.SHIFT_TYPE_L04 + ":")) {
            Integer specialtyId = Integer.parseInt(key.substring(key.indexOf(':') + 1));
            return activeStaff.stream()
                    .filter(s -> s.getSpecialty() != null && specialtyId.equals(s.getSpecialty().getId()))
                    .map(Staff::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        // L02/L03: chỉ Bác sĩ / Điều dưỡng (KTV/Dược sĩ không eligible).
        String shiftTypeId = key.startsWith("L0") ? key.substring(0, 3) : key;
        Integer requiredSpecId = null; // L02/L03 không yêu cầu specialty cụ thể
        return activeStaff.stream()
                .filter(s -> com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility
                        .isEligible(s, shiftTypeId, requiredSpecId))
                .map(Staff::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String rebalanceKey(Schedule schedule) {
        String typeId = schedule.getShiftType().getId();
        if (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeId)
                && schedule.getRequirement() != null
                && schedule.getRequirement().getSpecialty() != null) {
            return typeId + ":" + schedule.getRequirement().getSpecialty().getId();
        }
        return typeId;
    }

    // DELEGATED to StaffEligibilityFilter.isBusinessShiftConflict (M07 refactor)
    private boolean isBusinessShiftConflict(String typeA, String typeB) {
        return staffEligibilityFilter.isBusinessShiftConflict(typeA, typeB);
    }

    private record RebalanceMove(Schedule schedule, Staff toStaff) {
        static com.hospital.scheduler.service.scheduling.PostAssignmentOptimizer.RebalanceMove unwrap(RebalanceMove m) {
            return new com.hospital.scheduler.service.scheduling.PostAssignmentOptimizer.RebalanceMove(m.schedule(), m.toStaff());
        }
        static RebalanceMove wrap(com.hospital.scheduler.service.scheduling.PostAssignmentOptimizer.RebalanceMove m) {
            // BUGFIX: findBestSafeRebalanceMoveCompat returns null when no safe move is available
            // (no eligible donor/receiver pair on over-staffed days). Passing null into the record
            // ctor previously triggered NPE on the first .schedule() access inside the rebalance
            // loop. Returning a sentinel null here lets the caller distinguish "nothing to move"
            // from "move pending" and exit cleanly. The caller (findBestSafeRebalanceMove)
            // is updated to return null in that case.
            if (m == null) return null;
            return new RebalanceMove(m.schedule(), m.toStaff());
        }
    }

    // ==================== HELPER METHODS ====================
    private List<String> buildWarnings(List<ShiftRequirement> requirements, List<Schedule> schedules) {
        List<String> warnings = new ArrayList<>();

        Map<String, Long> assignedCount = schedules.stream()
                .filter(s -> s != null && s.getWorkDate() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getWorkDate() + "_" + s.getShiftType().getId(),
                        Collectors.counting()));

        for (ShiftRequirement req : requirements) {
            String key = req.getWorkDate() + "_" + req.getShiftType().getId();
            long assigned = assignedCount.getOrDefault(key, 0L);
            if (assigned < req.getRequiredStaffCount()) {
                warnings.add(String.format("Ngày %s (%s), ca %s: thiếu %d nhân sự (có %d). %s",
                        req.getWorkDate(), DateUtils.getDayOfWeekVietnamese(req.getWorkDate().getDayOfWeek()),
                        req.getShiftType().getName(),
                        req.getRequiredStaffCount() - assigned, assigned,
                        buildUnassignedReason(req, assigned)));
            }
        }

        return warnings;
    }

    private String getStaffSpecialtyName(Schedule schedule) {
        return schedule.getStaff() != null && schedule.getStaff().getSpecialty() != null
                ? schedule.getStaff().getSpecialty().getName()
                : null;
    }

    private String getRequiredSpecialtyName(Schedule schedule) {
        return schedule.getRequirement() != null && schedule.getRequirement().getSpecialty() != null
                ? schedule.getRequirement().getSpecialty().getName()
                : null;
    }

    private boolean isCrossSpecialtyAssignment(Schedule schedule) {
        if (schedule.getRequirement() == null || schedule.getRequirement().getSpecialty() == null) {
            return false;
        }
        if (!ConflictDetectionService.SHIFT_TYPE_L04.equals(schedule.getShiftType().getId())) {
            return false;
        }
        Integer requiredSpecialtyId = schedule.getRequirement().getSpecialty().getId();
        Integer staffSpecialtyId = schedule.getStaff() != null && schedule.getStaff().getSpecialty() != null
                ? schedule.getStaff().getSpecialty().getId()
                : null;
        return !Objects.equals(requiredSpecialtyId, staffSpecialtyId);
    }

    // DELEGATED to UnassignedDaysReportBuilder (M07 refactor)
    private String buildUnassignedReason(ShiftRequirement req, long assigned) {
        // Reason logic is encapsulated in the report builder.
        // For backward compatibility during incremental migration we still expose the helper.
        if (assigned == 0) {
            if (ConflictDetectionService.SHIFT_TYPE_L04.equals(req.getShiftType().getId()) && req.getSpecialty() != null) {
                return "Không còn nhân sự hợp lệ cho chuyên khoa " + req.getSpecialty().getName()
                        + " sau khi áp dụng nghỉ phép, nghỉ bù và xung đột.";
            }
            return "Không còn nhân sự hợp lệ sau khi áp dụng nghỉ phép, nghỉ bù và xung đột ca.";
        }
        return "Mục tiêu phân bổ từ cấu hình cao hơn số nhân sự hợp lệ còn lại; phần thiếu cần quản lý xử lý thủ công.";
    }

    // DELEGATED to UnassignedDaysReportBuilder (M07 refactor)
    private String buildUnassignedReasonCode(ShiftRequirement req, long assigned) {
        if (assigned == 0 && ConflictDetectionService.SHIFT_TYPE_L04.equals(req.getShiftType().getId())
                && req.getSpecialty() != null) {
            return "NO_SPECIALTY_STAFF";
        }
        if (assigned == 0) {
            return "NO_ELIGIBLE_STAFF";
        }
        return "PARTIAL_COVERAGE";
    }

    // DELEGATED to UnassignedDaysReportBuilder (M07 refactor)
    private String buildUnassignedSeverity(int required, int assigned) {
        if (assigned <= 0) return "critical";
        double missingRatio = (double) (required - assigned) / Math.max(1, required);
        return missingRatio >= 0.5 ? "warning" : "info";
    }

    /**
     * Get L04 cross-specialty config from algorithmConfigService.
     * Returns a simple record with enabled, ratio, and allowedSpecialties.
     */
    private static record CrossSpecialtyConfig(boolean enabled, float ratio, List<String> allowedSpecialties) {}
    public CrossSpecialtyConfig getL04CrossSpecialtyConfig() {
        return algorithmConfigService.getAutoGenConfig()
                .map(cfg -> new CrossSpecialtyConfig(cfg.l04CrossSpecialty(), cfg.l04CrossSpecialtyRatio(), cfg.l04AllowedSpecialties()))
                .orElse(new CrossSpecialtyConfig(true, 0.5f, List.of())); // Default: enabled, ratio 0.5, all specialties
    }

    private Staff selectStaffByWorkload(List<Staff> availableStaff, Integer periodId, String shiftTypeId) {
        Staff selected = null;
        long minCount = Long.MAX_VALUE;

        for (Staff staff : availableStaff) {
            long count = scheduleRepository.countByStaffIdAndPeriodId(staff.getId(), periodId);
            if (count < minCount) {
                minCount = count;
                selected = staff;
            }
        }

        return selected;
    }

    private boolean hasInMemoryConflict(Integer staffId, LocalDate workDate, String shiftTypeId) {
        String key = staffId + "_" + workDate;
        Set<String> existingShifts = inMemoryAssignments().get(key);
        if (existingShifts != null) {
            for (String existingId : existingShifts) {
                if (existingId.equals(shiftTypeId)) {
                    return true;
                }
                if (isBusinessShiftConflict(shiftTypeId, existingId)) {
                    return true;
                }
            }
        }

        // BACK-TO-BACK CHECK for L01 (trực 24/24 liên tiếp)
        // L01 occupies 7h30 ngày N → 7h30 ngày N+1,
        // so if assigned L01 on day N-1 or day N+1, it's a conflict
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
            Map<String, Set<String>> allAssignments = inMemoryAssignments();
            // Check previous day
            LocalDate prevDay = workDate.minusDays(1);
            String prevKey = staffId + "_" + prevDay;
            Set<String> prevShifts = allAssignments.get(prevKey);
            if (prevShifts != null && prevShifts.contains(ConflictDetectionService.SHIFT_TYPE_L01)) {
                return true;
            }
            // Check next day
            LocalDate nextDay = workDate.plusDays(1);
            String nextKey = staffId + "_" + nextDay;
            Set<String> nextShifts = allAssignments.get(nextKey);
            if (nextShifts != null && nextShifts.contains(ConflictDetectionService.SHIFT_TYPE_L01)) {
                return true;
            }
        }


        // Check if this date is a compensation day for the staff
        // L02/L03/L04 cannot be assigned on a day that is a compensation day for this staff
        if (!ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
            String compKey = staffId + "_" + workDate.toString();
            if (inMemoryCompensationShiftDates().contains(compKey)) {
                return true;
            }
        }

        // L01 cannot be assigned on a day that is already a compensation day for this staff
        // (from L01 in a previous published period)
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
            String compKey = staffId + "_" + workDate.toString();
            if (allCompensationShiftDates().contains(compKey)) {
                return true;
            }
        }

        return false;
    }

    private String scheduleKey(Integer staffId, String workDate, String shiftTypeId) {
        return staffId + "_" + workDate + "_" + shiftTypeId;
    }

    /**
     * Same conflict rules as {@link #hasInMemoryConflict(Integer, LocalDate, String)} but
     * reads from a caller-provided map. Used by {@link #applyPreviewSchedule} to detect
     * collisions between sibling preview items since the ThreadLocal assignments from
     * {@link #runScheduling} are no longer in scope.
     */
    private boolean hasInLoopConflict(Map<String, Set<String>> inApplyLoop, Integer staffId,
                                      LocalDate workDate, String shiftTypeId) {
        String key = staffId + "_" + workDate;
        Set<String> existingShifts = inApplyLoop.get(key);
        if (existingShifts != null) {
            for (String existingId : existingShifts) {
                if (existingId.equals(shiftTypeId)) {
                    continue;
                }
                boolean newIsOvernight = ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId);
                boolean existingIsOvernight = ConflictDetectionService.SHIFT_TYPE_L01.equals(existingId);
                if (newIsOvernight != existingIsOvernight) {
                    return true;
                }
                if (!newIsOvernight) {
                    boolean aL03 = ConflictDetectionService.SHIFT_TYPE_L03.equals(shiftTypeId);
                    boolean bL04 = ConflictDetectionService.SHIFT_TYPE_L04.equals(existingId);
                    boolean aL04 = ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId);
                    boolean bL03 = ConflictDetectionService.SHIFT_TYPE_L03.equals(existingId);
                    if ((aL03 && bL04) || (aL04 && bL03)) {
                        return true;
                    }
                }
            }
        }

        // BACK-TO-BACK CHECK for L01: if assigning L01, check if L01 already exists on N-1, N-2, or N+1
        // N-2 is needed because L01 on N-2 means N-1 is compensation day (blocked),
        // but if N-1 compensation wasn't enforced, N-2 staff could have another L01 on N
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
            // Check N-1
            LocalDate prevDay = workDate.minusDays(1);
            String prevKey = staffId + "_" + prevDay;
            Set<String> prevShifts = inApplyLoop.get(prevKey);
            if (prevShifts != null && prevShifts.contains(ConflictDetectionService.SHIFT_TYPE_L01)) {
                return true;
            }
            // Check N-2
            LocalDate prev2Day = workDate.minusDays(2);
            String prev2Key = staffId + "_" + prev2Day;
            Set<String> prev2Shifts = inApplyLoop.get(prev2Key);
            if (prev2Shifts != null && prev2Shifts.contains(ConflictDetectionService.SHIFT_TYPE_L01)) {
                return true;
            }
            // Check N+1
            LocalDate nextDay = workDate.plusDays(1);
            String nextKey = staffId + "_" + nextDay;
            Set<String> nextShifts = inApplyLoop.get(nextKey);
            if (nextShifts != null && nextShifts.contains(ConflictDetectionService.SHIFT_TYPE_L01)) {
                return true;
            }
        }

        return false;
    }

    private void trackAssignment(Staff staff, LocalDate workDate, String shiftTypeId) {
        String key = staff.getId() + "_" + workDate;
        inMemoryAssignments().computeIfAbsent(key, k -> new HashSet<>()).add(shiftTypeId);
        // NOTE: compensation day tracking for L01 is handled by the caller
        // (runGreedy/runFairGreedy) using pickBestCompensationDay() for flexibility.
        // Do NOT track compensation days here — use the caller's compDate instead.
    }

    /**
     * Count the number of conflicting schedules in the in-memory assignment produced by the
     * algorithm run. Used in preview mode to surface the same conflict count the monthly-schedule
     * page will see after Apply — so the manager does not click Apply with hidden violations.
     *
     * A schedule counts as conflicting if its staff+date+shiftType combination violates any of:
     *   - L01 and L02 (or L03 and L04) on the same day for the same staff (shift-type conflict)
     *   - L01 assigned to a staff whose in-run compensation day falls on this date
     *   - Back-to-back L01 (staff already assigned L01 on day N-1 or N+1 within this run)
     *
     * Leave and max-shift checks are skipped here because the in-run algorithm already filtered
     * by them; they would mostly produce false positives (e.g. leaves that aren't in the period).
     */
    private int countInMemoryConflicts(List<Schedule> createdSchedules) {
        if (createdSchedules == null || createdSchedules.isEmpty()) {
            return 0;
        }
        Map<String, Set<String>> assignments = inMemoryAssignments();
        Set<String> compDayKeys = inMemoryCompensationShiftDates();
        int conflicts = 0;
        for (Schedule s : createdSchedules) {
            String key = s.getStaff().getId() + "_" + s.getWorkDate();
            Set<String> shifts = assignments.get(key);
            if (shifts == null || shifts.size() <= 1) {
                // First or only assignment on this date — algorithm's own filter already covered it.
                // Still check compensation day for L01 to catch back-to-back when comp-day maps here.
                if (ConflictDetectionService.SHIFT_TYPE_L01.equals(s.getShiftType().getId())) {
                    String compKey = s.getStaff().getId() + "_" + s.getWorkDate();
                    if (compDayKeys.contains(compKey)) {
                        conflicts++;
                    }
                }
                continue;
            }
            // Multiple shifts on same date for same staff → at least one pair violates the
            // shift-type rules. Count as a single conflict per (staff, date) bucket.
            boolean hasViolation = false;
            List<String> shiftList = new ArrayList<>(shifts);
            for (int i = 0; i < shiftList.size() && !hasViolation; i++) {
                for (int j = i + 1; j < shiftList.size() && !hasViolation; j++) {
                    String a = shiftList.get(i);
                    String b = shiftList.get(j);
                    boolean aL01 = ConflictDetectionService.SHIFT_TYPE_L01.equals(a);
                    boolean bL01 = ConflictDetectionService.SHIFT_TYPE_L01.equals(b);
                    if (aL01 != bL01) {
                        hasViolation = true;
                        break;
                    }
                    boolean aL03 = ConflictDetectionService.SHIFT_TYPE_L03.equals(a);
                    boolean bL03 = ConflictDetectionService.SHIFT_TYPE_L03.equals(b);
                    boolean aL04 = ConflictDetectionService.SHIFT_TYPE_L04.equals(a);
                    boolean bL04 = ConflictDetectionService.SHIFT_TYPE_L04.equals(b);
                    if ((aL03 && bL04) || (aL04 && bL03)) {
                        hasViolation = true;
                    }
                }
            }
            if (hasViolation) {
                conflicts++;
            }
        }
        // Also count any (staff, compensation-date) where the algorithm assigned an L02/L03/L04
        // onto a comp day it generated earlier in the same run. The run already prevented this
        // in the filter, but we surface a defensive count in case of future regressions.
        for (Schedule s : createdSchedules) {
            String shiftTypeId = s.getShiftType().getId();
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) continue;
            String compKey = s.getStaff().getId() + "_" + s.getWorkDate();
            if (compDayKeys.contains(compKey)) {
                conflicts++;
            }
        }
        return conflicts;
    }

    /**
     * Create a compensation day for a saved L01 schedule.
     * Delegates to {@link SchedulePersistenceService} which owns the duplicate-prevention
     * logic (in-memory cache + DB pre-check + INSERT IGNORE).
     */
    private void createCompensationDayForAuto(Schedule schedule) {
        schedulePersistenceService.createCompensationDayForAuto(compensationDayRepository, schedule);
    }

    /**
     * Create compensation days for all L01 schedules in a period.
     * CRITICAL: Each L01 shift requires ONE compensation day (24h recovery rule).
     * Each schedule -> 1 compensation day mapping.
     */
    public void createCompensationDaysForL01InPeriod(Integer periodId) {
        log.info("Creating compensation days for all L01 schedules in period {}", periodId);
        
        List<Schedule> l01Schedules = scheduleRepository.findByPeriodIdAndShiftTypeId(periodId, "L01");
        log.info("Found {} L01 schedules in period {}", l01Schedules.size(), periodId);
        
        int created = 0;
        int skipped = 0;
        int errors = 0;
        
        for (Schedule schedule : l01Schedules) {
            try {
                LocalDate shiftDate = schedule.getWorkDate();
                LocalDate compensationDate = compensationDateCalculator.calculate(shiftDate);
                
                // Use INSERT IGNORE to avoid duplicate key errors - this is the proper fix
                int inserted = compensationDayRepository.insertIgnoreCompensationDay(
                        schedule.getStaff().getId(),
                        schedule.getPeriod().getId(),
                        schedule.getId(),
                        shiftDate,
                        compensationDate,
                        "Ngày nghỉ bù tự động từ ca L01 (shift_id=" + schedule.getId() + ")"
                );
                if (inserted > 0) {
                    created++;
                } else {
                    skipped++;
                }
                
            } catch (Exception e) {
                log.warn("Error creating compensation day for schedule {}: {}", schedule.getId(), e.getMessage());
                errors++;
            }
        }
        
        log.info("Compensation day creation complete: created={}, skipped={}, errors={}", created, skipped, errors);
    }

    private BigDecimal calculateBalanceScore(List<Schedule> schedules, int totalStaff) {
        if (schedules.isEmpty()) return BigDecimal.ZERO;

        // Per-staff weighted volume: L01 weight=2 (24h + comp day), L02/L03/L04 weight=1
        Map<Integer, Double> weightedVolume = new java.util.HashMap<>();
        for (Schedule s : schedules) {
            double w = com.hospital.scheduler.algorithm.scoring.ShiftTypeWeights.of(s.getShiftType().getId());
            weightedVolume.merge(s.getStaff().getId(), w, Double::sum);
        }

        if (weightedVolume.size() <= 1) {
            log.debug("Balance score 0: only {} staff assigned", weightedVolume.size());
            return BigDecimal.valueOf(0);
        }

        // Pool size = total active staff (including zero-load staff)
        int poolSize = Math.max(totalStaff, weightedVolume.size());
        double totalVolume = weightedVolume.values().stream().mapToDouble(Double::doubleValue).sum();
        double mean = totalVolume / poolSize;

        // Variance: include zero-load staff
        double sumSq = 0.0;
        for (Map.Entry<Integer, Double> e : weightedVolume.entrySet()) {
            double diff = e.getValue() - mean;
            sumSq += diff * diff;
        }
        int zeroCount = poolSize - weightedVolume.size();
        if (zeroCount > 0) {
            sumSq += zeroCount * mean * mean;
        }

        double variance = sumSq / poolSize;
        double stdDev = Math.sqrt(variance);
        double cv = mean > 0 ? (stdDev / mean) * 100 : 0.0;

        double score = Math.max(0, 100 - cv);

        if (cv > 30) {
            log.warn("Balance WARNING: weighted-volume CV={}% > 30%", String.format("%.2f", cv));
        }

        log.info("Balance score: weightedVolumeCv={}% totalVolume={} poolSize={} score={}",
                String.format("%.2f", cv), String.format("%.0f", totalVolume), poolSize, String.format("%.2f", score));

        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Delete any persisted schedule from {@code candidate} that passes the
     * {@code shouldDelete} predicate AND is not present in {@code keep}.
     *
     * <p>BUGFIX (was M07 #1) helper: when Fair Greedy ran as a fallback probe
     * (save=true) but its balance score didn't actually beat Greedy, we need
     * to undo FG's persisted rows so DB only carries the chosen plan. The
     * predicate filter prevents deleting schedules Greedy already created —
     * those rows are owned by the Greedy run and stay.
     */
    private void rollBackSchedulesByPredicate(List<Schedule> candidate, java.util.function.Predicate<Schedule> shouldDelete) {
        if (candidate == null || candidate.isEmpty()) return;
        int deleted = 0;
        for (Schedule s : candidate) {
            if (!shouldDelete.test(s)) continue;
            Integer id = s.getId();
            if (id == null) continue; // not yet persisted
            try {
                scheduleRepository.deleteById(id);
                deleted++;
            } catch (Exception ex) {
                log.warn("rollback schedule id={} failed: {}", id, ex.getMessage());
            }
        }
        if (deleted > 0) {
            log.info("Fair Greedy rollback: removed {} persisted schedules", deleted);
        }
    }

    // DELEGATED to SchedulingMetricsService (M07 refactor)
    private void saveMetrics(SchedulePeriod period, String algorithmType, int executionTime,
                             BigDecimal coverageRate, BigDecimal balanceScore, int conflictCount, int totalSchedulesCreated) {
        metricsService.saveMetrics(period, algorithmType, executionTime, coverageRate, balanceScore, conflictCount, totalSchedulesCreated);
    }

    public List<AlgorithmMetricsDTO> getMetricsByPeriod(Integer periodId) {
        return metricsService.getMetricsByPeriod(periodId);
    }

    public List<AlgorithmMetricsDTO> getAllMetrics() {
        return metricsService.getAllMetrics();
    }

    /**
     * Server-paginated variant of getAllMetrics / getMetricsByPeriod,
     * used by the auto-scheduling history page's &lt;Pagination&gt; widget.
     */
    public Page<AlgorithmMetricsDTO> getMetricsPage(Integer periodId, Pageable pageable) {
        return metricsService.getMetricsPage(periodId, pageable);
    }

    /**
     * Build shift type breakdown for detailed statistics per schedule type (L01/L02/L03/L04).
     */
    private Map<String, AutoScheduleResponse.ShiftTypeBreakdown> buildByShiftTypeBreakdown(
            List<Schedule> schedules, List<ShiftRequirement> requirements) {
        
        Map<String, Map<String, Object>> typeStats = new LinkedHashMap<>();
        Set<String> shiftTypeIds = new HashSet<>();
        for (ShiftRequirement req : requirements) {
            String id = req.getShiftType().getId();
            shiftTypeIds.add(id);
            typeStats.computeIfAbsent(id, k -> {
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("shiftTypeId", id);
                stats.put("shiftTypeName", req.getShiftType().getName());
                stats.put("totalRequired", 0);
                stats.put("totalAssigned", 0);
                stats.put("unassignedDates", new ArrayList<String>());
                return stats;
            });
            Map<String, Object> stats = typeStats.get(id);
            stats.put("totalRequired", (int) stats.get("totalRequired") + req.getRequiredStaffCount());
        }
        
        // Count assigned per shift type
        Map<String, Long> assignedPerType = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getShiftType().getId(), Collectors.counting()));
        
        // Track unassigned dates per shift type
        Map<String, Set<String>> unassignedDatesPerType = new HashMap<>();
        Map<String, Map<String, Long>> assignedCountByTypeAndDate = schedules.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getShiftType().getId(),
                        Collectors.groupingBy(s -> s.getWorkDate().toString(), Collectors.counting())));
        
        for (ShiftRequirement req : requirements) {
            String id = req.getShiftType().getId();
            String dateStr = req.getWorkDate().toString();
            long assigned = assignedCountByTypeAndDate
                    .getOrDefault(id, Collections.emptyMap())
                    .getOrDefault(dateStr, 0L);
            if (assigned < req.getRequiredStaffCount()) {
                unassignedDatesPerType
                        .computeIfAbsent(id, k -> new HashSet<>())
                        .add(dateStr);
            }
        }
        
        // Build final breakdown
        Map<String, AutoScheduleResponse.ShiftTypeBreakdown> result = new LinkedHashMap<>();
        for (String shiftTypeId : shiftTypeIds) {
            Map<String, Object> stats = typeStats.get(shiftTypeId);
            int totalRequired = (int) stats.get("totalRequired");
            int totalAssigned = assignedPerType.getOrDefault(shiftTypeId, 0L).intValue();
            double coverageRate = totalRequired > 0 
                    ? Math.min(100.0, (double) totalAssigned / totalRequired * 100) 
                    : 0.0;
            
            List<String> unassignedDates = new ArrayList<>(unassignedDatesPerType.getOrDefault(shiftTypeId, Collections.emptySet()));
            Collections.sort(unassignedDates);
            
            Set<Integer> distinctStaff = schedules.stream()
                    .filter(s -> s.getShiftType().getId().equals(shiftTypeId))
                    .map(s -> s.getStaff().getId())
                    .collect(Collectors.toSet());
            
            result.put(shiftTypeId, AutoScheduleResponse.ShiftTypeBreakdown.builder()
                    .shiftTypeId(shiftTypeId)
                    .shiftTypeName((String) stats.get("shiftTypeName"))
                    .totalAssigned(totalAssigned)
                    .totalRequired(totalRequired)
                    .coverageRate(Math.round(coverageRate * 100.0) / 100.0)
                    .unassignedDates(unassignedDates)
                    .distinctStaffAssigned(distinctStaff.size())
                    .build());
        }
        
        return result;
    }

    // metricsToDTO() moved to SchedulingMetricsService.metricsToDTO (M07 refactor)

    // sortRequirementsByPriority() moved to GreedyAssignmentEngine (M07 refactor)

    // groupRequirementsByDate() moved to GreedyAssignmentEngine (M07 refactor)

    // DELEGATED to GreedyAssignmentEngine.computeFairSharePerTypeWithStaff (M07 refactor)
    private Map<String, Integer> computeFairSharePerType(List<ShiftRequirement> requirements, int staffPool) {
        return GreedyAssignmentEngine.computeFairSharePerTypeWithStaff(requirements, staffPool, null);
    }

    // DELEGATED to GreedyAssignmentEngine.computeFairSharePerTypeWithStaff (M07 refactor)
    private Map<String, Integer> computeFairSharePerTypeWithStaff(
            List<ShiftRequirement> requirements, int staffPool, List<Staff> activeStaff) {
        Map<String, Integer> result = GreedyAssignmentEngine.computeFairSharePerTypeWithStaff(requirements, staffPool, activeStaff);
        log.info("fairSharePerType: L01={} L02={} L03={} L04={} (staffPool={})",
                result.get(ConflictDetectionService.SHIFT_TYPE_L01),
                result.get(ConflictDetectionService.SHIFT_TYPE_L02),
                result.get(ConflictDetectionService.SHIFT_TYPE_L03),
                result.get(ConflictDetectionService.SHIFT_TYPE_L04),
                staffPool);
        return result;
    }

    // filterAndSortEligibleStaff (pre-batch) was removed in M07 refactor — git history.
	    private Schedule buildAndSaveSchedule(SchedulePeriod period, Staff staff, ShiftRequirement req,
	                                         LocalDate workDate, boolean save, List<Schedule> list,
	                                         boolean skipExisting) {
	        // BUGFIX (A1, second attempt): eligibility filters in runGreedy exclude staff that
	        // already have a CONFLICTING shift on this date (per filterAndSortEligibleStaffBatch),
	        // but the per-shift eligibility list is computed BEFORE all four shift types finish
	        // processing for the day. The Greedy loop iterates (date, shiftType) pairs and saves
	        // directly via this builder, so a later shift-type pass can collide with a shift
	        // persisted by an earlier pass within the same date (e.g. L01 saved during L01 pass
	        // vs L02/L03 saved during their own pass — they share the same in-memory ThreadLocal
	        // `inMemoryAssignments` set, but only IF the eligibility filter consults it; for some
	        // edge cases such as cross-pass L01↔L02/L03 the batch comparator skips the check).
	        // Persisting at the DB layer causes 146 BUSINESS_SHIFT_TYPE conflicts to surface in
	        // conflictCount after apply (period 4 baseline). Hard guard here re-checks against the
	        // DB at the moment of save and returns null so the caller skips this slot silently
	        // (the schedule is still counted as "attempted" but not persisted, which is preferable
	        // to corrupting the period with conflicting shifts).
	        // In clean preview mode (skipExisting && !save), skip the DB conflict check because
	        // the DB still has 1128 existing schedules from previous runs that would falsely
	        // block every new assignment.
	        if (!(skipExisting && !save) && conflictDetectionService.hasAnyConflict(
	                staff.getId(),
	                workDate,
	                req.getShiftType().getId(),
	                /*excludeScheduleId*/ null)) {
	            log.warn("buildAndSaveSchedule: skip BUSINESS-SHIFT-CONFLICT staff={} date={} shift={} (would violate L01↔L02 / L03↔L04 rule)",
	                    staff.getId(), workDate, req.getShiftType().getId());
	            return null;
	        }
        Schedule schedule = Schedule.builder()
                .period(period)
                .staff(staff)
                .shiftType(req.getShiftType())
                .workDate(workDate)
                .requirement(req)
                .hasConflict(false)
                .build();
        if (save) {
            Schedule saved = scheduleRepository.save(schedule);
            if (saved != null) {
                auditHistoryService.logAction("schedule", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, null);
                list.add(saved);
                return saved;
            }
            return null;
        } else {
            schedule.setId(null);
            list.add(schedule);
            return schedule;
        }
    }


    // ==================== BATCH CONFLICT DATA LOADING (avoids N+1) ====================

    /**
     * Load all conflict data for the entire scheduling period in ONE pass.
     * - All leave requests for the period
     * - All compensation days for the period
     * - All existing schedules for the period (grouped by date + staff)
     * - All adjacent L01 staff IDs (prev/next day of each date)
     *
     * This replaces:
     * - P × 2 queries for adjacent days (prev/next)
     * - P × 1 query for schedules per day
     * - P × 1 query for leave per day
     * - P × 1 query for compensation per day
     * → down to 4 queries total regardless of period length
     */
	    private PeriodConflictData loadPeriodConflictData(SchedulePeriod period, List<ShiftRequirement> requirements, List<Staff> activeStaff, boolean skipExisting) {
	        LocalDate periodStart = period.getStartDate();
	        LocalDate periodEnd = period.getEndDate();
        boolean cleanPreview = skipExisting;

        // 3. Load all schedules for the period with details (single query)
	        // Skip in clean preview mode so algorithm doesn't see existing coverage
	        Map<Integer, List<Schedule>> allSchedulesByStaff = new HashMap<>();
	        if (!cleanPreview) {
	            for (Schedule s : scheduleRepository.findByPeriodId(period.getId())) {
	                allSchedulesByStaff.computeIfAbsent(s.getStaff().getId(), k -> new ArrayList<>()).add(s);
	            }
	        }
	
	        // 4. Pre-compute for each date: who is on leave, who is on comp day, who has schedules today
	        // Collect all unique dates from requirements + existing schedules
	        Set<LocalDate> allDates = new HashSet<>();
	        for (ShiftRequirement req : requirements) {
	            allDates.add(req.getWorkDate());
	        }
	        if (!cleanPreview) {
	            for (List<Schedule> staffSchedules : allSchedulesByStaff.values()) {
	                for (Schedule s : staffSchedules) {
	                    allDates.add(s.getWorkDate());
	                }
	            }
	        }
	
        // 5. Build date range for adjacent L01 check (+2 so compensation days on day N+2 are blocked)
        LocalDate adjStart = periodStart.minusDays(1);
        LocalDate adjEnd = periodEnd.plusDays(2);

        // 7. Build per-date BatchConflictData
	        // OPTIMIZATION: batch-load all leaves/compensations once, then filter in-memory (eliminates N+1)
	        Map<LocalDate, Set<Integer>> leavesByDate = new HashMap<>();
	        for (LeaveRequest lr : leaveRequestRepository.findApprovedInRange(periodStart, periodEnd)) {
	            // A leave request covers a date range [startDate, endDate]
	            LocalDate start = lr.getStartDate();
	            LocalDate end = lr.getEndDate();
	            LocalDate cursor = start.isBefore(periodStart) ? periodStart : start;
	            LocalDate endLimit = end.isAfter(periodEnd) ? periodEnd : end;
	            while (!cursor.isAfter(endLimit)) {
	                leavesByDate.computeIfAbsent(cursor, k -> new HashSet<>()).add(lr.getStaff().getId());
	                cursor = cursor.plusDays(1);
	            }
	        }
	
	        Map<LocalDate, Set<Integer>> compDaysByDate = new HashMap<>();
	        if (!cleanPreview) {
	            // FIX: Expand range by ±1 day to catch compensation days that fall on the boundary day
	            // before or after the current period. Example: L01 on Friday (prev period) generates
	            // compensation on Tuesday (start of new period) — Tuesday must be blocked.
	            for (CompensationDay cd : compensationDayRepository.findInRange(periodStart.minusDays(1), periodEnd.plusDays(1))) {
	                compDaysByDate.computeIfAbsent(cd.getCompensationDate(), k -> new HashSet<>()).add(cd.getStaff().getId());
	                // Also add to in-memory cache (HashSet handles duplicates)
	                String compKey = cd.getStaff().getId() + "_" + cd.getCompensationDate().toString();
	                allCompensationShiftDates().add(compKey);
	            }
	        }
	
	        // Build prev/next L01 lookup per date
	        Map<LocalDate, Set<Integer>> adjacentL01ByDate = new HashMap<>();
	        if (!cleanPreview) {
	            for (Schedule s : scheduleRepository.findL01SchedulesInRange(adjStart, adjEnd)) {
	                LocalDate adj = s.getWorkDate();
	                adjacentL01ByDate.computeIfAbsent(adj.minusDays(1), k -> new HashSet<>()).add(s.getStaff().getId());
		                adjacentL01ByDate.computeIfAbsent(adj.plusDays(1), k -> new HashSet<>()).add(s.getStaff().getId());
		            }
		        }

        Map<LocalDate, BatchConflictData> byDate = new HashMap<>();
        for (LocalDate date : allDates) {
            Set<Integer> onLeave = leavesByDate.getOrDefault(date, Collections.emptySet());
            Set<Integer> onComp = compDaysByDate.getOrDefault(date, Collections.emptySet());

            Map<Integer, List<Schedule>> daySchedulesByStaff = new HashMap<>();
            for (Map.Entry<Integer, List<Schedule>> entry : allSchedulesByStaff.entrySet()) {
                for (Schedule s : entry.getValue()) {
                    if (s.getWorkDate().equals(date)) {
                        daySchedulesByStaff.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(s);
                    }
                }
            }

            Set<Integer> adjacentL01 = adjacentL01ByDate.getOrDefault(date, Collections.emptySet());
            byDate.put(date, new BatchConflictData(onLeave, onComp, daySchedulesByStaff, adjacentL01));
        }

        // 8. Build shift type counts from all schedules.
        // For L04, also track per-specialty count using key "L04:<specialtyId>" so that
        // the Greedy/RR comparators and hard-cap logic can enforce fair distribution
        // within each specialty independently (M05: L04 is specialty-bound).
        Map<Integer, Map<String, Long>> staffShiftTypeCounts = new HashMap<>();
        for (Map.Entry<Integer, List<Schedule>> entry : allSchedulesByStaff.entrySet()) {
            Map<String, Long> counts = new HashMap<>();
            counts.put("L01", 0L);
            counts.put("L02", 0L);
            counts.put("L03", 0L);
            counts.put("L04", 0L);
            for (Schedule s : entry.getValue()) {
                counts.merge(s.getShiftType().getId(), 1L, Long::sum);
                // Per-specialty L04 tracking: key = "L04:<specialtyId>"
                if (ConflictDetectionService.SHIFT_TYPE_L04.equals(s.getShiftType().getId())
                        && s.getRequirement() != null
                        && s.getRequirement().getSpecialty() != null) {
                    String specKey = "L04:" + s.getRequirement().getSpecialty().getId();
                    counts.merge(specKey, 1L, Long::sum);
                }
            }
            staffShiftTypeCounts.put(entry.getKey(), counts);
        }

        // 9. Build staff map for maxShiftsPerMonth lookup
        Map<Integer, Staff> staffMap = new HashMap<>();
        for (Staff s : activeStaff) {
            staffMap.put(s.getId(), s);
        }

        return new PeriodConflictData(byDate, staffShiftTypeCounts, staffMap);
    }

    /**
     * Load all conflict-check data for a single date in one shot.
     * Replaces the O(N) per-staff queries that were causing the hang.
     */
    private BatchConflictData loadBatchConflictData(LocalDate date) {
        LocalDate prevDay = date.minusDays(1);
        LocalDate nextDay = date.plusDays(1);

        Set<Integer> onLeave = new HashSet<>();
        for (LeaveRequest lr : leaveRequestRepository.findApprovedByDate(date)) {
            onLeave.add(lr.getStaff().getId());
        }

        Set<Integer> onComp = new HashSet<>();
        for (CompensationDay cd : compensationDayRepository.findByDate(date)) {
            onComp.add(cd.getStaff().getId());
        }

        Map<Integer, List<Schedule>> daySchedules = new HashMap<>();
        for (Schedule s : scheduleRepository.findByWorkDateWithDetails(date)) {
            daySchedules.computeIfAbsent(s.getStaff().getId(), k -> new ArrayList<>()).add(s);
        }

        Set<Integer> adjacentL01 = new HashSet<>();
        for (Schedule s : scheduleRepository.findByWorkDateWithDetails(prevDay)) {
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(s.getShiftType().getId())) adjacentL01.add(s.getStaff().getId());
        }
        for (Schedule s : scheduleRepository.findByWorkDateWithDetails(nextDay)) {
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(s.getShiftType().getId())) adjacentL01.add(s.getStaff().getId());
        }

        return new BatchConflictData(onLeave, onComp, daySchedules, adjacentL01);
    }

    /**
     * Batch-aware version of filterAndSortEligibleStaff that uses pre-loaded conflict data
     * instead of making per-staff DB queries for leave/compensation/shift-type conflicts.
     *
     * @param fairShareKey the staffShiftTypeCounts key to use for per-type cap enforcement.
     *                     For L04 with a specialty, this is "L04:specialtyId"; for all
     *                     other shifts it is the plain shiftTypeId (e.g. "L01").
     */
    private List<Staff> filterAndSortEligibleStaffBatch(
            List<Staff> pool,
            ShiftRequirement req,
            Set<Integer> excludedStaffIds,
            Set<Integer> assignedStaffIds,
            BatchConflictData batchData,
            boolean skipCompensationCheck,
            Comparator<Staff> sortComparator,
            PeriodConflictData periodData,
            Set<Integer> additionalAdjacentL01,
            Set<Integer> additionalCompDayStaffIds,
            int maxShiftsPerStaffLimit,
            int maxShiftsPerTypeLimit,
            String fairShareKey,
            Map<Integer, Map<String, Long>> l04PerSpecialtyCounts,
            List<Staff> allActiveStaff,
            Integer maxShiftsPerMonthOverride) {

        ShiftType shiftType = req.getShiftType();
        String shiftTypeId = shiftType.getId();
        boolean isL04WithSpecialty = ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)
                && req.getSpecialty() != null;

        // Get cross-specialty config
        var crossConfig = getL04CrossSpecialtyConfig();
        boolean crossEnabled = crossConfig.enabled() && isL04WithSpecialty;

        // OPTIMIZATION: Build staff ID → Staff lookup once for O(1) access
        Map<Integer, Staff> staffById = new HashMap<>(pool.size() * 4 / 3 + 1);
        for (Staff s : pool) {
            staffById.put(s.getId(), s);
        }

        // OPTIMIZATION: Precompute cross-specialty assigned count once per call
        // (assignedStaffIds does not change during this call)
        int totalRequired = Math.max(1, req.getRequiredStaffCount());
        int maxCrossCandidates = 0;
        long precomputedCrossAssignedToday = 0;
        if (crossEnabled && req.getSpecialty() != null) {
            Integer requiredSpecId = req.getSpecialty().getId();
            precomputedCrossAssignedToday = assignedStaffIds.stream()
                    .filter(id -> {
                        Staff s = staffById.get(id);
                        return s != null && s.getSpecialty() != null
                                && !s.getSpecialty().getId().equals(requiredSpecId);
                    })
                    .count();
            maxCrossCandidates = (int) Math.ceil(totalRequired * crossConfig.ratio());
        }

        // OPTIMIZATION: Pre-merge adjacent L01 sets once (shared across all staff)
        Set<Integer> mergedAdjacentL01;
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
            mergedAdjacentL01 = new HashSet<>();
            if (batchData.adjacentL01StaffIds() != null) mergedAdjacentL01.addAll(batchData.adjacentL01StaffIds());
            if (additionalAdjacentL01 != null) mergedAdjacentL01.addAll(additionalAdjacentL01);
        } else {
            mergedAdjacentL01 = Collections.emptySet();
        }

        List<Staff> strictMatches = new ArrayList<>();
        List<Staff> crossMatches = new ArrayList<>();

        for (Staff staff : pool) {
            if (excludedStaffIds != null && excludedStaffIds.contains(staff.getId())) continue;

            // 0. ELIGIBILITY CHECK: staff phải thuộc ALL_ELIGIBLE_SPECIALTIES (6 khoa).
            Integer requiredSpecId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;
            if (!StaffShiftTypeEligibility.isEligible(staff, shiftTypeId, requiredSpecId)) {
                if (crossEnabled && ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)) {
                    if (staff.getSpecialty() == null
                            || !StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES
                                    .contains(staff.getSpecialty().getName())) {
                        continue;
                    }
                    // Eligible via cross-specialty — proceed below
                } else {
                    continue;
                }
            }

            // 1. Check specialty FIRST (hard requirement for non-L04 or if cross-specialty disabled)
            boolean isStrictMatch = req.getSpecialty() == null
                    || (staff.getSpecialty() != null && staff.getSpecialty().getId().equals(req.getSpecialty().getId()));

            if (!isStrictMatch) {
                if (!crossEnabled) continue;
                // OPTIMIZATION: Use precomputed cross-specialty count (cheap O(1))
                if (precomputedCrossAssignedToday >= maxCrossCandidates) continue;
            }

            // 2. In-memory assignment conflict (from this scheduling run)
            if (hasInMemoryConflict(staff.getId(), req.getWorkDate(), shiftTypeId)) {
                continue;
            }

            // 3. Use batch-loaded data instead of per-staff queries (leave, comp day, adjacents)
            if (batchData.onLeaveStaffIds().contains(staff.getId())) continue;

            if (!skipCompensationCheck) {
                if (batchData.onCompDayStaffIds().contains(staff.getId())) continue;
                if (additionalCompDayStaffIds != null && additionalCompDayStaffIds.contains(staff.getId())) continue;
            }

            // Adjacent day restriction only applies to L01 (set pre-built once above)
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)
                    && mergedAdjacentL01.contains(staff.getId())) continue;

            // Same-day shift-type conflict: only duplicate same-type and documented business pairs are blocked.
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

            // 4. Per-type hard cap enforcement.
            if (maxShiftsPerTypeLimit > 0 && maxShiftsPerTypeLimit < Integer.MAX_VALUE) {
                long thisTypeCount = getStaffCountForKey(staff.getId(), fairShareKey,
                        periodData.staffShiftTypeCounts(), l04PerSpecialtyCounts);
                if (thisTypeCount >= maxShiftsPerTypeLimit) continue;
            }
            // 4b. Global per-staff total cap.
            int overrideCap = maxShiftsPerMonthOverride != null ? maxShiftsPerMonthOverride : -1;
            int effectiveMaxShifts = (overrideCap >= 0)
                    ? (overrideCap == 0 ? Integer.MAX_VALUE : overrideCap)
                    : (maxShiftsPerStaffLimit > 0 && maxShiftsPerStaffLimit < Integer.MAX_VALUE
                            ? maxShiftsPerStaffLimit
                            : (staff.getMaxShiftsPerMonth() != null && staff.getMaxShiftsPerMonth() > 0
                                    ? staff.getMaxShiftsPerMonth()
                                    : Integer.MAX_VALUE));
            if (effectiveMaxShifts < Integer.MAX_VALUE) {
                long totalCurrent = getTotalStaffCount(staff.getId(),
                        periodData.staffShiftTypeCounts(), l04PerSpecialtyCounts);
                if (totalCurrent >= effectiveMaxShifts) continue;
            }

            if (isStrictMatch) {
                strictMatches.add(staff);
            } else {
                crossMatches.add(staff);
            }
        }

        strictMatches.sort(sortComparator);
        crossMatches.sort(sortComparator);

        List<Staff> eligible = new ArrayList<>(strictMatches.size() + crossMatches.size());
        if (crossEnabled && !crossMatches.isEmpty() && !strictMatches.isEmpty()
                && staffEligibilityFilter.shouldPreferCrossSpecialty(
                        req, strictMatches.size(), totalRequired, crossConfig.ratio())) {
            eligible.addAll(crossMatches);
            eligible.addAll(strictMatches);
        } else {
            eligible.addAll(strictMatches);
            eligible.addAll(crossMatches);
        }
        return eligible;
    }

    /**
     * Get staff shift type count, merging DB counts with in-memory counts from the current scheduling run.
     * For L04 with specialty, uses "L04:specialtyId" as the running key and adds the DB-level L04 baseline.
     */
    // DELEGATED to StaffEligibilityFilter.getStaffCountForKey (M07 refactor)
    private long getStaffCountForKey(Integer staffId, String countKey,
            Map<Integer, Map<String, Long>> dbCounts,
            Map<Integer, Map<String, Long>> runningCounts) {
        return staffEligibilityFilter.getStaffCountForKey(staffId, countKey, dbCounts, runningCounts);
    }

    // DELEGATED to StaffEligibilityFilter.getTotalStaffCount (M07 refactor)
    private long getTotalStaffCount(Integer staffId,
            Map<Integer, Map<String, Long>> dbCounts,
            Map<Integer, Map<String, Long>> runningCounts) {
        return staffEligibilityFilter.getTotalStaffCount(staffId, dbCounts, runningCounts);
    }

    // DELEGATED to StaffEligibilityFilter.isStrictMatchForStaff (M07 refactor)
    private boolean isStrictMatchForStaff(Staff staff, ShiftRequirement req) {
        return staffEligibilityFilter.isStrictMatchForStaff(staff, req);
    }

    // Requirement generation + persistence moved to
    // {@link com.hospital.scheduler.service.scheduling.RequirementPreparationService}
    // — see Issue #12 (B2) — to eliminate duplication with AutoSchedulingService.

    // ==================== INCREMENTAL RE-SCHEDULE ====================

    /**
     * Re-solve a period using CSP's incremental path when the delta supports it,
     * otherwise fall back to a full CSP solve. This is the entry point for the
     * /auto-schedule/reschedule endpoint — wired to {@link com.hospital.scheduler.algorithm.CspIncrementalResolver}.
     *
     * <p>Why both branches: {@link com.hospital.scheduler.algorithm.CSPScheduler#canReSolveIncrementally}
     * returns false when {@link com.hospital.scheduler.algorithm.ScheduleChange#requiresFullReSolve()}
     * is true (e.g. staff list changed, or no prior result to diff from). In that case we
     * run a normal full solve so the caller's contract — "give me a fresh feasible plan for
     * this period" — still holds.
     *
     * <p>The returned result carries assignments keyed by "staffId|workDate" — the same
     * shape as {@link #runCsp}, so persistence can mirror it identically.
     */
    public AutoScheduleResponse reschedulePeriodIncremental(Integer periodId, ScheduleChange changes, boolean save) {
        inMemoryAssignments().clear();
        inMemoryCompensationShiftDates().clear();
        allCompensationShiftDates().clear();
        swapPriorityStaffIds().clear();
        try {
            SchedulePeriod period = periodRepository.findById(periodId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + periodId));

            List<ShiftRequirement> requirements = requirementRepository.findByPeriodId(periodId);
            List<Staff> activeStaff = staffRepository.findByIsActiveTrue();

            // Load previous result from DB schedules so the incremental resolver can diff.
            Map<String, String> previousAssignments = new HashMap<>();
            for (Schedule s : scheduleRepository.findByPeriodId(periodId)) {
                previousAssignments.put(s.getStaff().getId() + "_" + s.getWorkDate().toString(), s.getShiftType().getId());
            }
            SchedulingResult previous = previousAssignments.isEmpty() ? null
                    : SchedulingResult.builder().assignments(previousAssignments).valid(true).build();

            List<LeaveRequest> leaveRequests = leaveRequestRepository.findApprovedInRange(
                    period.getStartDate(), period.getEndDate());

            Set<Integer> excludedStaffIds = null; // reschedule respects the period's existing exclusions implicitly

            SchedulingResultWithFairness result;
            boolean usedIncremental = false;
            if (previous != null && cspScheduler.canReSolveIncrementally(changes)) {
                log.info("Reschedule period {} via CSP incremental path ({} changes)",
                        periodId, countChanges(changes));
                result = runCspWithResult(cspScheduler.reSolve(previous, changes, activeStaff, CspAssignmentEngine.toRequirementInfos(requirements), leaveRequests), period, requirements);
                usedIncremental = true;
            } else {
                log.info("Reschedule period {} via full CSP solve (incremental not applicable: previous={}, canReSolve={})",
                        periodId, previous != null,
                        previous != null && cspScheduler.canReSolveIncrementally(changes));
                result = runCsp(period, requirements, activeStaff, false, excludedStaffIds, false);
            }
            log.info("Reschedule period {} done (incremental={}): {} assignments",
                    periodId, usedIncremental, result.schedules().size());

            // If save=true, wipe current schedules and persist the fresh assignment plan.
            List<Schedule> persisted = result.schedules();
            if (save && !persisted.isEmpty()) {
                List<Integer> scheduleIds = scheduleRepository.findByPeriodId(periodId).stream()
                        .map(Schedule::getId).toList();
                if (!scheduleIds.isEmpty()) {
                    scheduleConflictRepository.deleteByScheduleIds(scheduleIds);
                }
                compensationDayRepository.deleteAllByPeriodId(periodId);
                entityManager.flush();
                scheduleRepository.deleteAllByPeriodId(periodId);
                entityManager.flush();
                persisted = scheduleRepository.saveAll(persisted);
                entityManager.flush();
                createCompensationDaysForL01InPeriod(periodId);
                log.info("Reschedule persisted {} schedules for period {}", persisted.size(), periodId);
            }

            Map<LocalDate, List<ShiftRequirement>> reqsByDate = requirements.stream()
                    .collect(Collectors.groupingBy(ShiftRequirement::getWorkDate));
            int totalRequiredSlots = reqsByDate.values().stream().mapToInt(List::size).sum() * 4;
            int coverage = totalRequiredSlots == 0 ? 100
                    : Math.min(100, persisted.size() * 100 / Math.max(1, totalRequiredSlots));
            BigDecimal balanceScore = calculateBalanceScore(persisted,
                    (int) persisted.stream().map(s -> s.getStaff().getId()).distinct().count());

            Set<String> seen = new LinkedHashSet<>();
            List<AutoScheduleResponse.ScheduleSummary> summaries = persisted.stream()
                    .filter(s -> seen.add(s.getStaff().getId() + "_" + s.getWorkDate() + "_" + s.getShiftType().getId()))
                    .map(s -> AutoScheduleResponse.ScheduleSummary.builder()
                            .scheduleId(s.getId())
                            .staffId(s.getStaff().getId())
                            .staffName(s.getStaff().getFullName())
                            .workDate(s.getWorkDate().toString())
                            .shiftTypeId(s.getShiftType().getId())
                            .shiftTypeName(s.getShiftType().getName())
                            .requirementId(s.getRequirement() != null ? s.getRequirement().getId() : null)
                            .build())
                    .toList();

            return AutoScheduleResponse.builder()
                    .success(true)
                    .message(usedIncremental
                            ? "Đã tái xếp lịch bằng CSP incremental"
                            : "Đã tái xếp lịch bằng CSP full solve (delta quá lớn)")
                    .periodId(periodId)
                    .algorithmType("CSP_MRV_FC")
                    .coverageRate(BigDecimal.valueOf(coverage))
                    .balanceScore(balanceScore)
                    .totalSchedulesCreated(persisted.size())
                    .schedules(summaries)
                    .executedAt(LocalDateTime.now())
                    .build();
        } finally {
            clearSchedulingState();
        }
    }

    /**
     * Re-hydrate a raw {@link SchedulingResult} from the incremental path into Schedule entities.
     * The incremental resolver returns assignments keyed by "staffId_workDate" — we map them back
     * to Schedule entities so the existing persistence pipeline can be reused unchanged.
     *
     * <p>BUGFIX (BUG-UI-001): When the CSP/Local Search solver produces an assignment, the
     * rehydrated {@link Schedule} entity MUST carry the originating {@link ShiftRequirement}
     * reference, otherwise downstream code (preview response, apply-preview request mapping)
     * receives {@code requirementId=null} and BUG-UI-001 strikes for L04 multi-specialty
     * slots ("Có nhiều requirement cho (workDate, shiftTypeId)").</p>
     */
    private SchedulingResultWithFairness runCspWithResult(SchedulingResult result, SchedulePeriod period,
                                                           List<ShiftRequirement> requirements) {
        if (result == null || !result.isValid() || result.getAssignments() == null || result.getAssignments().isEmpty()) {
            // BUGFIX (M07-PREVIEW-UOE): returning List.of() here is an immutable
            // empty list. The preview path in runScheduling() then tries
            // `createdSchedules.add(existing)` to fold in pre-existing schedules
            // for coverage display — that throws UnsupportedOperationException
            // every time V10 returns an empty result (e.g. period 1 with the
            // current V10 weights, valid=false). Return a mutable empty list so
            // both the save and preview callers can safely append.
            return new SchedulingResultWithFairness(new ArrayList<>(), BigDecimal.ZERO);
        }
        Map<Integer, Staff> staffById = new HashMap<>();
        for (Staff s : staffRepository.findByIsActiveTrue()) {
            staffById.put(s.getId(), s);
        }
        // Build (workDate|shiftTypeId) → list of requirements. When multiple
        // requirements match (L04 multi-specialty), the picker below prefers the
        // one whose specialty matches the assigned staff's specialty — falling
        // back to the lowest id. This matters because V10 may have legitimately
        // assigned staff X (specialty S) to a Ngoại L04 slot on 2026-06-16,
        // and we need to attach the Ngoại requirement (not the Sản or Nội one
        // that happens to share the date) so the cross-L04 KPI is not falsely
        // inflated by a rehydration artifact.
        Map<String, List<ShiftRequirement>> reqsByDateShift = new HashMap<>();
        if (requirements != null) {
            for (ShiftRequirement r : requirements) {
                if (r == null || r.getShiftType() == null) continue;
                String key = r.getWorkDate() + "|" + r.getShiftType().getId();
                reqsByDateShift.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }
        }
        List<Schedule> rehydrated = new ArrayList<>();
        for (Map.Entry<String, String> e : result.getAssignments().entrySet()) {
            String[] parts = e.getKey().split("_");
            // Support both key formats:
            //   CSP:  "staffId_date"           → parts=[staffId, date]
            //   V10:  "staffId_date_shiftType"  → parts=[staffId, date, shiftType]
            if (parts.length < 2) continue;
            try {
                Integer staffId = Integer.parseInt(parts[0]);
                LocalDate workDate = LocalDate.parse(parts[1]);
                // For V10 keys, the embedded shiftType in parts[2] must match the value
                String keyShiftType = parts.length >= 3 ? parts[2] : null;
                String valueShiftType = e.getValue();
                // Sanity: if both are present they must agree
                if (keyShiftType != null && !keyShiftType.equals(valueShiftType)) {
                    log.warn("Assignment key/value shiftType mismatch: key={}, value={}", e.getKey(), valueShiftType);
                    continue;
                }
                ShiftType shiftType = shiftTypeRepository.findById(valueShiftType).orElse(null);
                if (shiftType == null) continue;
                Staff staff = staffById.get(staffId);
                if (staff == null) continue;
                List<ShiftRequirement> candidates = reqsByDateShift.get(workDate + "|" + shiftType.getId());
                if (candidates == null || candidates.isEmpty()) continue;
                // Prefer the requirement whose specialty matches the staff's
                // specialty; otherwise fall back to the lowest-id requirement
                // (preserves prior deterministic behavior for non-L04).
                ShiftRequirement req = null;
                if (staff.getSpecialty() != null) {
                    Integer staffSpecId = staff.getSpecialty().getId();
                    for (ShiftRequirement r : candidates) {
                        if (r.getSpecialty() != null
                                && staffSpecId.equals(r.getSpecialty().getId())) {
                            req = r;
                            break;
                        }
                    }
                }
                if (req == null) {
                    req = candidates.stream()
                            .min(Comparator.comparing(ShiftRequirement::getId))
                            .orElse(null);
                }
                if (req == null) continue;
                rehydrated.add(Schedule.builder()
                        .period(period)
                        .staff(staff)
                        .workDate(workDate)
                        .shiftType(shiftType)
                        .requirement(req)
                        .hasConflict(false)
                        .build());
            } catch (Exception parseErr) {
                log.warn("Skipping malformed assignment key during incremental rehydrate: {}", e.getKey());
            }
        }
        return new SchedulingResultWithFairness(rehydrated,
                result.getFairnessScore() != null ? result.getFairnessScore() : BigDecimal.ZERO);
    }

    static List<ShiftRequirementInfo> toRequirementInfos(List<ShiftRequirement> requirements) {
        if (requirements == null) return List.of();
        return requirements.stream()
                .filter(r -> r != null && r.getShiftType() != null)
                .map(r -> new ShiftRequirementInfo(
                        r.getShiftType().getId(),
                        r.getWorkDate(),
                        r.getRequiredStaffCount(),
                        r.getSpecialty() != null ? r.getSpecialty().getId() : null))
                .toList();
    }

    private int countChanges(ScheduleChange changes) {
        if (changes == null) return 0;
        return changes.getAdded().size() + changes.getRemoved().size()
                + changes.getModified().size() + changes.getAddedLeaves().size()
                + changes.getRemovedLeaves().size();
    }

    /**
     * BUGFIX (was M07 #3): Acquire (or lazily create) a non-fair lock for the
     * given period. Concurrent autoSchedule/previewSchedule calls on the same
     * period coordinate via {@link java.util.concurrent.locks.Lock#tryLock()};
     * the loser gets a 400 BadRequestException so the client can retry.
     *
     * <p>The map is unbounded on purpose — period IDs are small bounded integers
     * from a separate table, and locks are JVM-scoped which matches the
     * request-scoped transaction boundary.
     */
    // DELEGATED to SchedulingLockService (M07 refactor)
    private java.util.concurrent.Semaphore acquirePeriodLock(Integer periodId) {
        return lockService.acquirePeriodLock(periodId);
    }

    /**
     * Pick the best compensation day from all valid options.
     * For Fri/Sat duty there are multiple valid days (Tue/Wed/Thu of next week);
     * this method picks the one with the fewest other staff already booked for
     * compensation on that day, spreading load evenly across the week.
     */
    private LocalDate pickBestCompensationDay(LocalDate workDate,
                                              LocalDate periodStart,
                                              LocalDate periodEnd,
                                              Map<LocalDate, Set<Integer>> compensationDaysByDate) {
        Set<LocalDate> options = compensationDateCalculator.calculateAll(workDate);
        if (options == null || options.isEmpty()) return null;

        LocalDate best = null;
        int minLoad = Integer.MAX_VALUE;
        for (LocalDate opt : options) {
            if (opt.isBefore(periodStart) || opt.isAfter(periodEnd)) continue;
            int load = compensationDaysByDate.getOrDefault(opt, Collections.emptySet()).size();
            if (load < minLoad) {
                minLoad = load;
                best = opt;
            }
        }
        return best;
    }
}
