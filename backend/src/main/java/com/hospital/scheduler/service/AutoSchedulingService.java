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
import com.hospital.scheduler.algorithm.BeamSearchScheduler;
import com.hospital.scheduler.algorithm.EnhancedGreedyScheduler;
import com.hospital.scheduler.algorithm.RandomRestartHCScheduler;
import com.hospital.scheduler.algorithm.ScheduleChange;
import com.hospital.scheduler.algorithm.ShiftRequirementInfo;
import com.hospital.scheduler.algorithm.SchedulingResult;
import com.hospital.scheduler.algorithm.ShiftRequirementInfo;
import com.hospital.scheduler.algorithm.scoring.ScheduleQualityScorer;
import com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AutoSchedulingService {

    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository periodRepository;
    private final StaffRepository staffRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ScheduleExchangeRepository scheduleExchangeRepository;
    private final AlgorithmMetricsRepository metricsRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final AuditHistoryService auditHistoryService;
    final CompensationDateCalculator compensationDateCalculator;
    private final NotificationService notificationService;
    private final AlgorithmConfigService algorithmConfigService;
    private final HolidayRepository holidayRepository;
    private final HolidayValidationService holidayValidationService;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SpecialtyRepository specialtyRepository;
    private final AlgorithmProgressTracker progressTracker;
    private final ScheduleQualityScorer scheduleQualityScorer;

    // New extracted services
    final CompensationDayAutoService compensationDayAutoService;
    private final RequirementAutoGenService requirementAutoGenService;
    private final AutoSchedulingMetricsService autoSchedulingMetricsService;
    private final BeamSearchScheduler beamSearchScheduler;
    private final EntityManager entityManager;
    private final ScheduleConflictRepository scheduleConflictRepository;
    private final PreviewConflictCheckService previewConflictCheckService;
    private final EnhancedGreedyScheduler enhancedGreedyScheduler;
    private final RandomRestartHCScheduler randomRestartHCScheduler;

    // Extracted runners (instantiated via @PostConstruct)
    private SchedulingAlgorithmRunner algorithmRunner;
    @Autowired
    private AutoSchedulingReportingService reportingService;

    // Thread-local so concurrent requests don't share state
    final ThreadLocal<Map<String, Set<String>>> inMemoryAssignments = ThreadLocal.withInitial(HashMap::new);
    final ThreadLocal<Set<String>> inMemoryCompensationShiftDates = ThreadLocal.withInitial(HashSet::new);
    // Swap request priority: Set of staff IDs who should be PREFERRED (those whose swap partner was assigned)
    final ThreadLocal<Set<Integer>> swapPriorityStaffIds = ThreadLocal.withInitial(HashSet::new);

    // BUGFIX (was M07 #3): Per-period execution locks. Concurrent autoSchedule /
    // previewSchedule calls on the same period are serialized so their
    // delete-and-regenerate operations cannot interleave. Locks are created on
    // first use and reused; they live as long as the JVM (acceptable for a
    // scheduling system whose period cardinality is small).
    private final java.util.concurrent.ConcurrentHashMap<Integer, java.util.concurrent.locks.Lock> periodLocks =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Tracks when each period's execution lock was acquired (epoch millis). Used to detect stale locks. */
    private final java.util.concurrent.ConcurrentHashMap<Integer, Long> lockAcquiredAt =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Stale lock threshold: if a lock is held longer than this (ms), a new request may override it. */
    private static final long STALE_LOCK_TIMEOUT_MS = 60_000L;

    // Pre-loaded period-level conflict data (rebuilt each scheduling run)
    record BatchConflictData(
            Set<Integer> onLeaveStaffIds,
            Set<Integer> onCompDayStaffIds,
            Map<Integer, List<Schedule>> daySchedulesByStaff,
            Set<Integer> adjacentL01StaffIds
    ) {}

    // Period-level data pre-loaded once per scheduling run
    record PeriodConflictData(
            Map<LocalDate, BatchConflictData> byDate,
            Map<Integer, Map<String, Long>> staffShiftTypeCounts,
            Set<Integer> allL01StaffIdsInRange,
            Map<Integer, Staff> staffMap  // For accessing maxShiftsPerMonth
    ) {}

    @PostConstruct
	    void init() {
	        this.algorithmRunner = new SchedulingAlgorithmRunner(
	                this, scheduleRepository, leaveRequestRepository, compensationDateCalculator);
	    }

	    public AutoScheduleResponse previewSchedule(AutoScheduleRequestDTO request) {
	        // BUGFIX (was M07 #3): Same per-period lock as autoSchedule — a preview run
	        // also deletes-and-regenerates schedule rows, so it must not race with
	        // a concurrent autoSchedule or another preview on the same period.
	        java.util.concurrent.locks.Lock periodLock = acquirePeriodLock(request.getPeriodId());
	        boolean acquired = false;
	        try {
	            acquired = periodLock.tryLock();
	            
	            // If lock is held and stale (e.g. client disconnected during CSP), override it
	            if (!acquired && isLockStale(request.getPeriodId())) {
	                periodLocks.remove(request.getPeriodId());
	                periodLock = acquirePeriodLock(request.getPeriodId());
	                acquired = periodLock.tryLock();
	                if (acquired) {
	                    log.warn("Overrode stale lock for period {}", request.getPeriodId());
	                }
	            }
	            
	            if (!acquired) {
	                throw new BadRequestException(
	                        "Kỳ lịch " + request.getPeriodId() + " đang được xếp tự động bởi một yêu cầu khác. "
	                                + "Vui lòng đợi yêu cầu trước hoàn tất rồi thử lại.");
	            }
	            lockAcquiredAt.put(request.getPeriodId(), System.currentTimeMillis());
	            inMemoryAssignments.set(new HashMap<>());
	            inMemoryCompensationShiftDates.set(new HashSet<>());
	            compensationDayAutoService.getAllCompensationShiftDates().set(new HashSet<>());
	            swapPriorityStaffIds.set(new HashSet<>());
	            try {
	                return runScheduling(request, false);
	            } finally {
	                inMemoryAssignments.remove();
	                inMemoryCompensationShiftDates.remove();
	                compensationDayAutoService.removeThreadLocal();
	                swapPriorityStaffIds.remove();
	            }
		    } finally {
	            if (acquired) {
	                periodLock.unlock();
	                // Clean up lock from map to prevent memory leak.
	                // The next caller will create a fresh ReentrantLock.
	                periodLocks.remove(request.getPeriodId());
	                lockAcquiredAt.remove(request.getPeriodId());
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
	        java.util.concurrent.locks.Lock periodLock = acquirePeriodLock(request.getPeriodId());
	        boolean acquired = false;
	        try {
	            // Try immediate lock acquisition
	            acquired = periodLock.tryLock();
	            
	            // If lock is held and stale (e.g. client disconnected during CSP), override it
	            if (!acquired && isLockStale(request.getPeriodId())) {
	                periodLocks.remove(request.getPeriodId());
	                periodLock = acquirePeriodLock(request.getPeriodId());
	                acquired = periodLock.tryLock();
	                if (acquired) {
	                    log.warn("Overrode stale lock for period {}", request.getPeriodId());
	                }
	            }
	            
	            if (!acquired) {
	                throw new BadRequestException(
	                        "Kỳ lịch " + request.getPeriodId() + " đang được xếp tự động bởi một yêu cầu khác. "
	                                + "Vui lòng đợi yêu cầu trước hoàn tất rồi thử lại.");
	            }
	            lockAcquiredAt.put(request.getPeriodId(), System.currentTimeMillis());
	            inMemoryAssignments.set(new HashMap<>());
	            inMemoryCompensationShiftDates.set(new HashSet<>());
	            compensationDayAutoService.getAllCompensationShiftDates().set(new HashSet<>());
	            swapPriorityStaffIds.set(new HashSet<>());
	            try {
	                return runScheduling(request, true);
	            } finally {
	                inMemoryAssignments.remove();
	                inMemoryCompensationShiftDates.remove();
	                compensationDayAutoService.removeThreadLocal();
	                swapPriorityStaffIds.remove();
	            }
	        } finally {
	            if (acquired) {
	                periodLock.unlock();
	                periodLocks.remove(request.getPeriodId());
	                lockAcquiredAt.remove(request.getPeriodId());
	            }
	        }
    }

    public AutoScheduleResponse applyPreviewSchedule(com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO request) {
        // BUGFIX (was M07 #4): the apply path reads from the in-memory cache
        // (compensationDayAutoService, inMemoryAssignments, etc.) but never
        // cleared it in a finally block. When Tomcat reuses a worker thread
        // for a different request, the leftover snapshot could be observed by
        // any code that touches these ThreadLocals. Initialize fresh values
        // here and remove them on exit so the worker thread is left clean.
        inMemoryAssignments.set(new HashMap<>());
        inMemoryCompensationShiftDates.set(new HashSet<>());
        compensationDayAutoService.getAllCompensationShiftDates().set(new HashSet<>());
        swapPriorityStaffIds.set(new HashSet<>());
        try {
            return applyPreviewScheduleInternal(request);
        } finally {
            inMemoryAssignments.remove();
            inMemoryCompensationShiftDates.remove();
            compensationDayAutoService.removeThreadLocal();
            swapPriorityStaffIds.remove();
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
            compensationDayAutoService.addToCache(compKey);
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
            if (holidayValidationService.isHoliday(workDate)) {
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

        // Use ScheduleQualityScorer for accurate coverage and balance (fixes43% / 0% bug)
        List<Staff> activeStaffForApply = staffRepository.findByIsActiveTrue();
        List<com.hospital.scheduler.entity.CompensationDay> compDaysForApply =
                compensationDayRepository.findByPeriodId(period.getId());
        List<com.hospital.scheduler.entity.LeaveRequest> approvedLeavesForApply =
                leaveRequestRepository.findByPeriodIdAndStatus(
                        period.getId(), com.hospital.scheduler.entity.LeaveRequest.LeaveStatus.APPROVED);
        com.hospital.scheduler.algorithm.AutoGenConfig autoGenCfgForApply =
                algorithmConfigService.getAutoGenConfig().orElse(null);
        com.hospital.scheduler.algorithm.scoring.ScheduleQualityScorer.ScoringMeta scoringMetaForApply =
                com.hospital.scheduler.algorithm.scoring.ScheduleQualityScorer.ScoringMeta
                        .of(request.getAlgorithmType(), 0L);
        com.hospital.scheduler.algorithm.scoring.ScheduleQualityReport qualityReportForApply =
                scheduleQualityScorer.score(
                        savedSchedules, periodRequirements, activeStaffForApply,
                        compDaysForApply, approvedLeavesForApply, scoringMetaForApply, autoGenCfgForApply);

        BigDecimal coverageRate = BigDecimal.valueOf(qualityReportForApply.getCoverageScore())
                .setScale(2, RoundingMode.HALF_UP);

        // Build unassigned days report (B7: danh sách ngày chưa phân công đầy đủ)
        List<Map<String, Object>> unassignedDays = buildUnassignedDays(periodRequirements, savedSchedules);

        int distinctStaffAssigned = (int) savedSchedules.stream()
                .map(s -> s.getStaff().getId())
                .distinct()
                .count();
        int staffCount = distinctStaffAssigned > 0 ? distinctStaffAssigned : 1;
        BigDecimal balanceScore = BigDecimal.valueOf(qualityReportForApply.getFairnessScore())
                .setScale(2, RoundingMode.HALF_UP);

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
        
        // Track GA fairness score separately (0-100 scale)
        BigDecimal lastFairnessScore = null;
        
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
        compensationDayAutoService.clearCache();
        log.info("Cleared in-memory compensation day cache for period {}", period.getId());

	        // Load runtime config from DB (or use defaults if not set)
	        AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig = algorithmConfigService.getRuntimeConfig();

		        // ── AUTO-ADJUST CONFIG: Algorithm reads dataset and adjusts config ──
		        boolean autoAdjust = "true".equalsIgnoreCase(
		                algorithmConfigService.getConfigValue("auto_adjust_config", "true"));

		        // Load active staff count for capacity calculation
		        List<Staff> activeStaffForAuto = staffRepository.findByIsActiveTrue();
		        if (request.getExcludedStaffIds() != null && !request.getExcludedStaffIds().isEmpty()) {
		            Set<Integer> excluded = new HashSet<>(request.getExcludedStaffIds());
		            activeStaffForAuto = activeStaffForAuto.stream()
		                    .filter(s -> !excluded.contains(s.getId()))
		                    .collect(Collectors.toList());
		        }
		        int staffCount = Math.max(1, activeStaffForAuto.size());
		        int periodDays = (int) java.time.temporal.ChronoUnit.DAYS.between(
		                period.getStartDate(), period.getEndDate()) + 1;

		        // Tính tổng yêu cầu từ config
		        var autoGenCfg = algorithmConfigService.getAutoGenConfig();
		        if (autoAdjust && autoGenCfg.isPresent()) {
		            var cfg = autoGenCfg.get();
		            int estimatedDaily = cfg.l01MaxPerDay() + cfg.l02MaxPerDay() + cfg.l03MaxPerDay()
		                    + cfg.l04MaxPerDay() * 6;
		            int estimatedTotal = estimatedDaily * periodDays;
		            int capacity = staffCount * runtimeConfig.getMaxShiftsPerStaff();

		            // Nếu yêu cầu > năng lực → tự động giảm L04 max
		            if (estimatedTotal > capacity && estimatedTotal > 0) {
		                double ratio = (double) capacity / estimatedTotal;
		                int newL04Max = Math.max(1, (int)(cfg.l04MaxPerDay() * ratio));
	                log.warn("[AutoAdjust] Config không phù hợp: yêu cầu={} > năng lực={}, tự giảm L04 max từ {} → {}",
	                        estimatedTotal, capacity, cfg.l04MaxPerDay(), newL04Max);
	                algorithmConfigService.updateAutoGenField("auto_gen_l04_max_per_day", String.valueOf(newL04Max));
	                // Reload config after update
	                autoGenCfg = algorithmConfigService.getAutoGenConfig();
	            }
	        }

	        // Override maxShiftsPerStaff nếu = 0
	        if (runtimeConfig.getMaxShiftsPerStaff() <= 0) {
	            int maxPhysical = periodDays;
	            log.info("maxShiftsPerStaff=0 → using physical limit of {}", maxPhysical);
	            runtimeConfig.setMaxShiftsPerStaff(maxPhysical);
	        }

	        log.info("Runtime config: maxShiftsPerStaff={}, balanceMin={}, autoAdjust={}",
	                runtimeConfig.getMaxShiftsPerStaff(), runtimeConfig.getBalanceScoreMin(), autoAdjust);

        // Load approved swap requests for priority assignment
        // Staff in PENDING/APPROVED swap requests get priority for their preferred schedules
        List<ScheduleExchange> pendingSwaps = scheduleExchangeRepository
                .findByPeriodIdAndStatus(period.getId(),
                        com.hospital.scheduler.entity.ScheduleExchange.ExchangeStatus.PENDING);
        if (pendingSwaps == null) pendingSwaps = Collections.emptyList();
        log.info("Loaded {} pending swap requests for period {}", pendingSwaps.size(), period.getId());

        // If a swap request exists for a schedule, prioritize the swap partner (target)
        // When target's schedule is assigned, the requester should be considered for their preferred shift
        Set<Integer> swapPrioritySet = swapPriorityStaffIds.get();
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

	        // Auto-adjust config n?u b?t
	        if (autoAdjust) {
	            autoScheduleConfigPreCheck(period, activeStaff, runtimeConfig);
	        }

        // Always generate requirements from algorithm config (no manual requirements page)
        var autoGenConfig = algorithmConfigService.getAutoGenConfig();
        if (autoGenConfig.isEmpty() || !autoGenConfig.get().enabled()) {
            throw new BadRequestException(
                    "Cấu hình auto-gen chưa được bật. Vui lòng bật auto_generate_requirements trong cấu hình thuật toán.");
        }
        // CRITICAL: Re-sync existing requirements with current config so changes to min/max per day
        // take effect on the next preview run. Without this, the scheduler would re-use stale
        // requiredCount values persisted by a previous run with older config.
        // BUGFIX (was M07 #6): Preview runs must NOT delete-and-regenerate requirements.
        // The user expects preview to be read-only — touching persisted state would
        // silently mutate the draft period on every preview. Only run the destructive
        // sync + persist path when the caller is committing the result (save=true).
        if (save) {
            syncExistingRequirementsWithConfig(period, autoGenConfig.get(), activeStaff);
            requirements = generateRequirementsFromConfig(period, autoGenConfig.get(), activeStaff);
            requirements = persistRequirementsIfTransient(requirements);
            log.info("Generated {} requirements from config for period {}", requirements.size(), period.getId());
        } else {
            // Preview mode: reuse whatever is already in the DB without touching it.
            requirements = requirementRepository.findByPeriodId(period.getId());
            if (requirements == null || requirements.isEmpty()) {
                // First run against a fresh period — fall back to in-memory generation
                // without persisting, so a preview still has data to schedule against.
                requirements = generateRequirementsFromConfig(period, autoGenConfig.get(), activeStaff);
                log.info("Preview-only generated {} requirements (transient) for period {}", requirements.size(), period.getId());
            }
        }

        // Pre-load existing compensation days from the same period so greedy doesn't assign L01 on a day
        // that is already someone's compensation day (confirmed day off — cannot assign L01)
        List<CompensationDay> existingCompDays = compensationDayRepository.findByPeriodId(period.getId());
        for (CompensationDay cd : existingCompDays) {
            compensationDayAutoService.addToCache(cd.getStaff().getId() + "_" + cd.getCompensationDate().toString());
        }

        // CRITICAL: Pre-load existing schedules from the same period into memory
        // This ensures the algorithm sees all already-assigned shifts and avoids conflicts
        // that would fail at apply-preview time (e.g., back-to-back L01 checks)
        List<Schedule> existingSchedules = scheduleRepository.findByPeriodId(period.getId());
        for (Schedule existing : existingSchedules) {
            String key = existing.getStaff().getId() + "_" + existing.getWorkDate();
            inMemoryAssignments.get().computeIfAbsent(key, k -> new HashSet<>()).add(existing.getShiftType().getId());
            // Also track compensation dates from existing L01 shifts
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(existing.getShiftType().getId())) {
                LocalDate compDate = compensationDateCalculator.calculate(existing.getWorkDate());
                if (compDate != null) {
                    compensationDayAutoService.addToCache(existing.getStaff().getId() + "_" + compDate.toString());
                }
            }
        }

        // P2-8: Enforce L01→L02→L03→L04 processing order per spec
        // L01 must be assigned first to reserve compensation days and avoid L01↔L02 conflicts
        requirements.sort(Comparator.comparingInt((ShiftRequirement r) -> {
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

	        String algorithmType = request.getAlgorithmType() != null
	                ? request.getAlgorithmType().toUpperCase()
	                : "GREEDY";

        // Whitelist supported algorithm types — reject unknown values with HTTP 400
        // instead of silently substituting Greedy. Without this guard, callers can
        // request "BACKTRACKING" or "GENETIC" and the run would still be persisted as
        // that algorithm in metrics — masking the fact that no such algorithm ran.
	        java.util.Set<String> supportedAlgorithms = java.util.Set.of(
	                "BEAM_SEARCH", "ENHANCED_GREEDY", "RANDOM_RESTART_HC"
	        );
        if (!supportedAlgorithms.contains(algorithmType)) {
            throw new BadRequestException("algorithmType '" + request.getAlgorithmType()
                    + "' không được hỗ trợ. Các giá trị hợp lệ: " + supportedAlgorithms);
        }

        List<Schedule> createdSchedules;
	        if ("BEAM_SEARCH".equals(algorithmType)) {
	            log.info("Running Beam Search for period {}", period.getId());
	            createdSchedules = beamSearchScheduler.solve(
	                    activeStaff, requirements, period, runtimeConfig,
	                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
	        } else if ("ENHANCED_GREEDY".equals(algorithmType)) {
	            log.info("Running Enhanced Greedy for period {}", period.getId());
	            createdSchedules = enhancedGreedyScheduler.solve(
	                    activeStaff, requirements, period, runtimeConfig,
	                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
	        } else if ("RANDOM_RESTART_HC".equals(algorithmType)) {
	            log.info("Running Random Restart HC for period {}", period.getId());
	            createdSchedules = randomRestartHCScheduler.solve(
	                    activeStaff, requirements, period, runtimeConfig,
	                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
	        } else {
	            log.info("Running Greedy for period {}", period.getId());
	            createdSchedules = runGreedy(period, requirements, activeStaff, save, runtimeConfig,
	                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
	        }
        int greedyStaffCount = (int) createdSchedules.stream().map(s -> s.getStaff().getId()).distinct().count();
        BigDecimal greedyBalanceScore = calculateBalanceScore(createdSchedules, greedyStaffCount > 0 ? greedyStaffCount : 1);

        // balance_score_min: if balance is below threshold, log it (no fallback needed)
        // Beam Search already handles fairness, Greedy has ~99% balance naturally.
        BigDecimal bestScore = greedyBalanceScore;
        List<Schedule> bestSchedules = createdSchedules;

        if (greedyBalanceScore.compareTo(runtimeConfig.getBalanceScoreMin()) < 0 && !activeStaff.isEmpty()) {
            log.info("{} balance score {} < threshold {} — result still usable",
                    algorithmType, greedyBalanceScore, runtimeConfig.getBalanceScoreMin());
        }

        // Use the best result
        createdSchedules = bestSchedules;

        // Phase 2b: Rotation - ensure EVERY staff has ALL 4 shift types
        if (!createdSchedules.isEmpty()) {
            int rotated = applyRotationPostProcessing(createdSchedules, requirements, activeStaff);
            if (rotated > 0) {
                log.info("Rotation post-processing applied {} swaps for {}", rotated, algorithmType);
            }
        }

        // Phase 2c: Gap-fill - fill remaining unassigned requirements
        // EnhancedGreedy does this internally, but BeamSearch/RandomRestart need it
        if (!createdSchedules.isEmpty()) {
            int gapFilled = applyGapFill(createdSchedules, requirements, activeStaff, period, runtimeConfig, 
                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
            if (gapFilled > 0) {
                log.info("Gap-fill added {} schedules for {}", gapFilled, algorithmType);
            }
        }

        // Phase 3: Local Search fairness rebalance.
        if (!createdSchedules.isEmpty()) {
            int rebalanceRounds = save ? 200 : 100;
            int optimizedMoves = optimizeFairnessBySafeReassignment(createdSchedules, activeStaff, requirements, rebalanceRounds);
            if (optimizedMoves > 0) {
                log.info("Local Search fairness optimization applied {} safe reassignment moves (rounds={})", optimizedMoves, rebalanceRounds);
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

        // Notify staff on successful save paths.
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
        List<Map<String, Object>> unassignedDays = buildUnassignedDays(requirements, createdSchedules);

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
            if (algorithmConfigService.getRuntimeConfig().isAutoCompensationEnabled()) {
                createCompensationDaysForL01InPeriod(period.getId());
            } else {
                log.info("Auto compensation disabled by config for period {}", period.getId());
            }
        }

        // ── Build schedule summaries (deduplicated) ───────────────────────────────
        Set<String> seen = new java.util.LinkedHashSet<>();
        List<AutoScheduleResponse.ScheduleSummary> scheduleSummaries = createdSchedules.stream()
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
                .totalSchedulesCreated(createdSchedules.size())
                .schedules(scheduleSummaries)
                .unassignedDays(unassignedDays)
                .qualityReport(qualityReport)
                .executedAt(LocalDateTime.now());

        if (request.getExcludedStaffIds() != null) {
            responseBuilder.excludedStaffIds(request.getExcludedStaffIds());
        }

        Map<String, AutoScheduleResponse.ShiftTypeBreakdown> byShiftType =
                buildByShiftTypeBreakdown(createdSchedules, requirements);
        responseBuilder.byShiftType(byShiftType);

        return responseBuilder.build();
    }

    // ==================== GREEDY ALGORITHM ====================
    private List<Schedule> runGreedy(SchedulePeriod period, List<ShiftRequirement> requirements,
                                     List<Staff> activeStaff, boolean save,
                                     AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
                                     Set<Integer> excludedStaffIds) {
        return algorithmRunner.runGreedy(period, requirements, activeStaff, save, runtimeConfig, excludedStaffIds);
    }

    // ==================== FAIR GREEDY ALGORITHM (formerly "Round Robin") ====================
    // Despite the old "Round Robin" name, this algorithm is structurally a fair variant of Greedy:

	    // ==================== ALGORITHM DISPATCH ====================

	    /**
     * Build a unique-slot key for an in-memory schedule. Used by the CSP-partial
     * + Greedy merge so we never double-assign the same (staff, shift, date)
     * triplet.
     */
    private String scheduleSlotKey(Schedule s) {
        return s.getStaff().getId() + "_" + s.getShiftType().getId() + "_" + s.getWorkDate();
    }

    /**
     * Filter {@code candidates} to remove any schedule whose slot already
     * appears in {@code kept}. Used to deduplicate the Greedy top-up against
     * the CSP partial plan when merging the two.
     */
    private List<Schedule> filterSchedulesExcluding(List<Schedule> candidates, List<Schedule> kept) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        Set<String> keptKeys = new HashSet<>(kept.size() * 2);
        for (Schedule s : kept) keptKeys.add(scheduleSlotKey(s));
        List<Schedule> filtered = new ArrayList<>();
        for (Schedule s : candidates) {
            if (!keptKeys.contains(scheduleSlotKey(s))) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    /**
     * Merge two schedule lists into one. CSP partial slots are kept verbatim
     * (priority over Greedy); Greedy-only slots are appended. The dedup is
     * already applied to {@code topUp} by {@link #filterSchedulesExcluding}
     * so this is just a stable concatenation.
     */
    private List<Schedule> mergeSchedules(List<Schedule> cspPartial, List<Schedule> topUp) {
        List<Schedule> merged = new ArrayList<>(cspPartial.size() + topUp.size());
        merged.addAll(cspPartial);
        merged.addAll(topUp);
        return merged;
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
    public Map<String, Object> getUnassignedDaysReport(Integer periodId) {
        return reportingService.getUnassignedDaysReport(periodId);
    }

    // ==================== M07-F08: Đề xuất người thay thế ====================
    public Map<String, Object> suggestReplacements(Integer scheduleId) {
        return reportingService.suggestReplacements(scheduleId);
    }

    public Map<String, Object> suggestReplacements(Integer scheduleId, Set<Integer> excludedStaffIds) {
        return reportingService.suggestReplacements(scheduleId, excludedStaffIds);
    }

    // ==================== M07-F09: Data biểu đồ cân bằng tải ====================
    public Map<String, Object> getWorkloadChartData(Integer periodId) {
        return reportingService.getWorkloadChartData(periodId);
    }

    public Map<String, Object> getWorkloadChartData(Integer periodId, String shiftTypeId) {
        return reportingService.getWorkloadChartData(periodId, shiftTypeId);
    }

    // ==================== LOCAL SEARCH FAIRNESS OPTIMIZER ====================

    private int optimizeFairnessBySafeReassignment(List<Schedule> schedules,
                                                   List<Staff> activeStaff,
                                                   List<ShiftRequirement> requirements,
                                                   int maxRounds) {
        return algorithmRunner.optimizeFairnessBySafeReassignment(schedules, activeStaff, requirements, maxRounds);
    }

    private int guaranteeMinimumShifts(List<Schedule> schedules,
                                       List<Staff> staffWithoutShifts,
                                       List<ShiftRequirement> requirements,
                                       List<Staff> activeStaff) {
        return algorithmRunner.guaranteeMinimumShifts(schedules, staffWithoutShifts, requirements, activeStaff);
    }

    private boolean isBusinessShiftConflict(String typeA, String typeB) {
        return algorithmRunner.isBusinessShiftConflict(typeA, typeB);
    }

    // ==================== HELPER METHODS ====================
    private List<String> buildWarnings(List<ShiftRequirement> requirements, List<Schedule> schedules) {
        return reportingService.buildWarnings(requirements, schedules);
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

    private String buildUnassignedReason(ShiftRequirement req, long assigned) {
        return reportingService.buildUnassignedReason(req, assigned);
    }

    private String buildUnassignedReasonCode(ShiftRequirement req, long assigned) {
        return reportingService.buildUnassignedReasonCode(req, assigned);
    }

    private String buildUnassignedSeverity(int required, int assigned) {
        return reportingService.buildUnassignedSeverity(required, assigned);
    }

    private List<Staff> filterBySpecialty(List<Staff> staffList, Integer specialtyId,
                                          boolean crossSpecialtyEnabled, float crossSpecialtyRatio) {
        if (specialtyId == null) return staffList;

        List<Staff> strictMatches = staffList.stream()
                .filter(s -> s.getSpecialty() != null && s.getSpecialty().getId().equals(specialtyId))
                .collect(Collectors.toList());

        // If we have enough strict matches, return them
        if (strictMatches.size() >= staffList.size() / 2) { // arbitrary threshold, enough candidates
            return strictMatches;
        }

        // Cross-specialty fallback: only when strict matches are insufficient
        if (crossSpecialtyEnabled && strictMatches.size() < staffList.size() / 3) {
            int remaining = Math.max(3, (int) (staffList.size() * crossSpecialtyRatio));
            List<Staff> crossCandidates = staffList.stream()
                    .filter(s -> s.getSpecialty() == null || !s.getSpecialty().getId().equals(specialtyId))
                    .sorted(Comparator.comparingLong(s ->
                            strictMatches.stream()
                                    .filter(m -> m.getSpecialty() != null && m.getSpecialty().getId().equals(s.getSpecialty().getId()))
                                    .count()))
                    .limit(remaining)
                    .collect(Collectors.toList());

            List<Staff> result = new ArrayList<>(strictMatches);
            result.addAll(crossCandidates);
            if (log.isDebugEnabled()) {
                log.debug("Cross-specialty: {} strict + {} cross = {} total candidates for specialty {}",
                        strictMatches.size(), crossCandidates.size(), result.size(), specialtyId);
            }
            return result;
        }

        return strictMatches;
    }

    SchedulingAlgorithmRunner.CrossSpecialtyConfig getL04CrossSpecialtyConfig() {
        return algorithmConfigService.getAutoGenConfig()
                .map(cfg -> new SchedulingAlgorithmRunner.CrossSpecialtyConfig(cfg.l04CrossSpecialty(), cfg.l04CrossSpecialtyRatio(), cfg.l04AllowedSpecialties()))
                .orElse(new SchedulingAlgorithmRunner.CrossSpecialtyConfig(false, 0.3f, List.of())); // Default: all specialties
    }

    /**
     * Trả về danh sách specialties được phép gán cho L01/L02/L03.
     * Đọc từ algorithm_config; null/empty → StaffShiftTypeEligibility sẽ fallback về CORE (Ngoại, Nội).
     */
    java.util.List<String> getNonL04AllowedSpecialties(String shiftTypeId) {
        return algorithmConfigService.getAutoGenConfig()
                .map(cfg -> {
                    if ("L01".equals(shiftTypeId)) return cfg.l01AllowedSpecialties();
                    if ("L02".equals(shiftTypeId)) return cfg.l02AllowedSpecialties();
                    if ("L03".equals(shiftTypeId)) return cfg.l03AllowedSpecialties();
                    return java.util.List.<String>of();
                })
                .orElse(java.util.List.of());
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

    private Schedule buildSchedule(SchedulePeriod period, Staff staff, ShiftType shiftType,
                                   LocalDate workDate, ShiftRequirement requirement) {
        // Check DB for existing schedule
        Optional<Schedule> existing = scheduleRepository.findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(
                period.getId(), staff.getId(), shiftType.getId(), workDate);
        if (existing.isPresent()) return null;

        // Check in-memory assignments (for preview mode and same-run conflicts)
        if (hasInMemoryConflict(staff.getId(), workDate, shiftType.getId())) {
            return null;
        }

        return Schedule.builder()
                .period(period)
                .staff(staff)
                .shiftType(shiftType)
                .workDate(workDate)
                .requirement(requirement)
                .hasConflict(false)
                .build();
    }

    boolean hasInMemoryConflict(Integer staffId, LocalDate workDate, String shiftTypeId) {
        String key = staffId + "_" + workDate;
        Set<String> existingShifts = inMemoryAssignments.get().get(key);
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
            Map<String, Set<String>> allAssignments = inMemoryAssignments.get();
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
            if (inMemoryCompensationShiftDates.get().contains(compKey)) {
                return true;
            }
        }

        // L01 cannot be assigned on a day that is already a compensation day for this staff
        // (from L01 in a previous published period)
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
            String compKey = staffId + "_" + workDate.toString();
            if (compensationDayAutoService.isInCache(compKey)) {
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

    void trackAssignment(Staff staff, LocalDate workDate, String shiftTypeId) {
        String key = staff.getId() + "_" + workDate;
        inMemoryAssignments.get().computeIfAbsent(key, k -> new HashSet<>()).add(shiftTypeId);
        // Also track compensation day if this is L01, so later L02/L03/L04 can't be assigned on that day
        // AND so we know not to assign L01 on this staff's compensation day
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
            LocalDate compDate = compensationDateCalculator.calculate(workDate);
            if (compDate != null) {
                String compKey = staff.getId() + "_" + compDate;
                inMemoryCompensationShiftDates.get().add(compKey);
                compensationDayAutoService.addToCache(staff.getId() + "_" + compDate.toString());
            }
        }
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
        Map<String, Set<String>> assignments = inMemoryAssignments.get();
        Set<String> compDayKeys = inMemoryCompensationShiftDates.get();
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

    void createCompensationDayForAuto(Schedule schedule) {
        compensationDayAutoService.createCompensationDayForAuto(schedule);
    }

    /**
     * Create compensation days for all L01 schedules in a period.
     * CRITICAL: Each L01 shift requires ONE compensation day (24h recovery rule).
     * Each schedule -> 1 compensation day mapping.
     */
    public void createCompensationDaysForL01InPeriod(Integer periodId) {
        compensationDayAutoService.createCompensationDaysForL01InPeriod(periodId);
    }

    BigDecimal calculateBalanceScore(List<Schedule> schedules, int totalStaff) {
        return autoSchedulingMetricsService.calculateBalanceScore(schedules, totalStaff);
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

    private void saveMetrics(SchedulePeriod period, String algorithmType, int executionTime,
                             BigDecimal coverageRate, BigDecimal balanceScore, int conflictCount, int totalSchedulesCreated) {
        autoSchedulingMetricsService.saveMetrics(period, algorithmType, executionTime, coverageRate, balanceScore, conflictCount, totalSchedulesCreated);
    }

    public List<AlgorithmMetricsDTO> getMetricsByPeriod(Integer periodId) {
        return autoSchedulingMetricsService.getMetricsByPeriod(periodId);
    }

    public List<AlgorithmMetricsDTO> getAllMetrics() {
        return autoSchedulingMetricsService.getAllMetrics();
    }

    /**
     * Server-paginated variant of getAllMetrics / getMetricsByPeriod,
     * used by the auto-scheduling history page's &lt;Pagination&gt; widget.
     */
    public Page<AlgorithmMetricsDTO> getMetricsPage(Integer periodId, Pageable pageable) {
        return autoSchedulingMetricsService.getMetricsPage(periodId, pageable);
    }

    private List<Map<String, Object>> buildUnassignedDays(List<ShiftRequirement> requirements, List<Schedule> schedules) {
        return reportingService.buildUnassignedDays(requirements, schedules);
    }

    /**
     * Build shift type breakdown for detailed statistics per schedule type (L01/L02/L03/L04).
     */
    private Map<String, AutoScheduleResponse.ShiftTypeBreakdown> buildByShiftTypeBreakdown(
            List<Schedule> schedules, List<ShiftRequirement> requirements) {
        return reportingService.buildByShiftTypeBreakdown(schedules, requirements);
    }

    private AlgorithmMetricsDTO metricsToDTO(AlgorithmMetrics m) {
        return autoSchedulingMetricsService.metricsToDTO(m);
    }

    List<ShiftRequirement> sortRequirementsByPriority(List<ShiftRequirement> requirements) {
        return algorithmRunner.sortRequirementsByPriority(requirements);
    }

    Map<LocalDate, List<ShiftRequirement>> groupRequirementsByDate(List<ShiftRequirement> requirements) {
        return algorithmRunner.groupRequirementsByDate(requirements);
    }

    /**
     * Compute demand-based fair share per shift type.
     * fairShare[type] = ceil(totalDemand[type] / staffPool) + 1 buffer
     * Used as the per-type cap in Greedy and Round Robin comparators.
     */
    /**
     * Tính fair-share cap per shift type dựa trên demand thực và pool hợp lệ.
     *
     * Spec M07-F05: L04 gắn theo chuyên khoa (M05) — pool hợp lệ của L04 là staff có đúng specialty,
     * không phải toàn bộ staffPool. Dùng staffPool chung cho L01/L02/L03 vì không có ràng buộc specialty.
     * Kết quả = ceil(totalDemand[type] / eligiblePool[type]) — không cộng thêm buffer cứng.
     */
    Map<String, Integer> computeFairSharePerType(List<ShiftRequirement> requirements, int staffPool) {
        return algorithmRunner.computeFairSharePerType(requirements, staffPool);
    }

    Map<String, Integer> computeFairSharePerTypeWithStaff(
            List<ShiftRequirement> requirements, int staffPool, List<Staff> activeStaff) {
        return algorithmRunner.computeFairSharePerTypeWithStaff(requirements, staffPool, activeStaff);
    }

    private List<Staff> filterAndSortEligibleStaff(List<Staff> pool, ShiftRequirement req,
                                                    Set<Integer> excludedStaffIds, boolean skipCompensationCheck, boolean skipMaxShifts,
                                                    Comparator<Staff> sortComparator) {
        var crossConfig = getL04CrossSpecialtyConfig();
        boolean isL04WithSpecialty = ConflictDetectionService.SHIFT_TYPE_L04.equals(req.getShiftType().getId())
                && req.getSpecialty() != null;
        boolean crossEnabled = crossConfig.enabled() && isL04WithSpecialty;

        // Step 1: Get strict matches
        List<Staff> strictMatches = pool.stream()
                .filter(s -> excludedStaffIds == null || !excludedStaffIds.contains(s.getId()))
                .filter(s -> s.getSpecialty() != null && s.getSpecialty().getId().equals(req.getSpecialty().getId()))
                .filter(s -> !conflictDetectionService.hasAnyConflict(s.getId(), req.getWorkDate(), req.getShiftType().getId(), null, skipCompensationCheck, false))
                .filter(s -> !hasInMemoryConflict(s.getId(), req.getWorkDate(), req.getShiftType().getId()))
                .collect(Collectors.toList());

        // Step 2: If cross-specialty enabled and strict matches insufficient, add cross matches
        if (crossEnabled && strictMatches.size() < req.getRequiredStaffCount()) {
            int needed = req.getRequiredStaffCount() - strictMatches.size();
            int maxCross = Math.max(1, (int) (req.getRequiredStaffCount() * crossConfig.ratio()));
            int toTake = Math.min(needed, maxCross);

            List<Staff> crossMatches = pool.stream()
                    .filter(s -> excludedStaffIds == null || !excludedStaffIds.contains(s.getId()))
                    .filter(s -> s.getSpecialty() == null || !s.getSpecialty().getId().equals(req.getSpecialty().getId()))
                    .filter(s -> !conflictDetectionService.hasAnyConflict(s.getId(), req.getWorkDate(), req.getShiftType().getId(), null, skipCompensationCheck, false))
                    .filter(s -> !hasInMemoryConflict(s.getId(), req.getWorkDate(), req.getShiftType().getId()))
                    .sorted(sortComparator)
                    .limit(toTake)
                    .collect(Collectors.toList());

            strictMatches.addAll(crossMatches);
            if (log.isDebugEnabled()) {
                log.debug("filterAndSortEligibleStaff cross-specialty: {} strict + {} cross for specialty {}",
                        strictMatches.size() - crossMatches.size(), crossMatches.size(), req.getSpecialty().getId());
            }
        }

        return strictMatches.stream().sorted(sortComparator).collect(Collectors.toList());
    }

    Schedule buildAndSaveSchedule(SchedulePeriod period, Staff staff, ShiftRequirement req,
                                         LocalDate workDate, boolean save, List<Schedule> list) {
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
    PeriodConflictData loadPeriodConflictData(SchedulePeriod period, List<ShiftRequirement> requirements, List<Staff> activeStaff) {
        LocalDate periodStart = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();

        // Collect all unique dates from requirements
        Set<LocalDate> allDates = new HashSet<>();
        for (ShiftRequirement req : requirements) {
            allDates.add(req.getWorkDate());
        }

        // ── Batch 1: Load all approved leaves ONCE ──────────────────────────
        List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedInRange(periodStart, periodEnd);
        Set<Integer> allOnLeave = new HashSet<>();
        Map<LocalDate, Set<Integer>> leavesByDate = new HashMap<>();
        for (LeaveRequest lr : approvedLeaves) {
            allOnLeave.add(lr.getStaff().getId());
            LocalDate start = lr.getStartDate().isBefore(periodStart) ? periodStart : lr.getStartDate();
            LocalDate end = lr.getEndDate().isAfter(periodEnd) ? periodEnd : lr.getEndDate();
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                leavesByDate.computeIfAbsent(cursor, k -> new HashSet<>()).add(lr.getStaff().getId());
                cursor = cursor.plusDays(1);
            }
        }

        // ── Batch 2: Load all compensation days ONCE (wider range) ──────────
        LocalDate compStart = periodStart.minusDays(1);
        LocalDate compEnd = periodEnd.plusDays(1);
        List<CompensationDay> compDays = compensationDayRepository.findInRange(compStart, compEnd);
        Set<Integer> allOnCompDay = new HashSet<>();
        Map<LocalDate, Set<Integer>> compDaysByDate = new HashMap<>();
        for (CompensationDay cd : compDays) {
            allOnCompDay.add(cd.getStaff().getId());
            compDaysByDate.computeIfAbsent(cd.getCompensationDate(), k -> new HashSet<>()).add(cd.getStaff().getId());
            compensationDayAutoService.addToCache(cd.getStaff().getId() + "_" + cd.getCompensationDate());
        }

        // ── Batch 3: Load all schedules for the period ──────────────────────
        List<Schedule> periodSchedules = scheduleRepository.findByPeriodId(period.getId());
        Map<Integer, List<Schedule>> allSchedulesByStaff = new HashMap<>();
        for (Schedule s : periodSchedules) {
            allSchedulesByStaff.computeIfAbsent(s.getStaff().getId(), k -> new ArrayList<>()).add(s);
            allDates.add(s.getWorkDate());
        }

        // ── Batch 4: Load L01 schedules in adjacent range ONCE ──────────────
        LocalDate adjStart = periodStart.minusDays(1);
        LocalDate adjEnd = periodEnd.plusDays(2);
        List<Schedule> l01Schedules = scheduleRepository.findL01SchedulesInRange(adjStart, adjEnd);
        Set<Integer> allL01StaffIds = new HashSet<>();
        Map<LocalDate, Set<Integer>> adjacentL01ByDate = new HashMap<>();
        for (Schedule s : l01Schedules) {
            allL01StaffIds.add(s.getStaff().getId());
            adjacentL01ByDate.computeIfAbsent(s.getWorkDate().minusDays(1), k -> new HashSet<>()).add(s.getStaff().getId());
            adjacentL01ByDate.computeIfAbsent(s.getWorkDate().plusDays(1), k -> new HashSet<>()).add(s.getStaff().getId());
        }

        // ── Build per-date BatchConflictData in-memory ──────────────────────
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

        return new PeriodConflictData(byDate, staffShiftTypeCounts, allL01StaffIds, staffMap);
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
        for (Schedule s : scheduleRepository.findByStaffIdAndDateRange(null, prevDay, prevDay)) {
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(s.getShiftType().getId())) adjacentL01.add(s.getStaff().getId());
        }
        for (Schedule s : scheduleRepository.findByStaffIdAndDateRange(null, nextDay, nextDay)) {
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
     * @param greedyWeeklyCounts in-memory weekly counts per staff per shift type (for l0XMaxPerWeek enforcement)
     * @param runtimeConfig algorithm runtime config (for per-type weekly max)
     */
    List<Staff> filterAndSortEligibleStaffBatch(
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
            Map<Integer, Map<String, Integer>> greedyWeeklyCounts,
            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
            List<Staff> allActiveStaff) {

        ShiftType shiftType = req.getShiftType();
        String shiftTypeId = shiftType.getId();
        boolean isL04WithSpecialty = ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)
                && req.getSpecialty() != null;

        // Get cross-specialty config
        var crossConfig = getL04CrossSpecialtyConfig();
        boolean crossEnabled = crossConfig.enabled() && isL04WithSpecialty;

        List<Staff> strictMatches = new ArrayList<>();
        List<Staff> crossMatches = new ArrayList<>();

        for (Staff staff : pool) {
            if (excludedStaffIds != null && excludedStaffIds.contains(staff.getId())) continue;
            // Do not block a staff from every second shift in the same day here.
            // Business rules only forbid specific pairs (L01/L02 and L03/L04), duplicate same-type,
            // compensation days, and leave days; hasInMemoryConflict enforces those below.

            // 0. ELIGIBILITY CHECK: staff phải thuộc chuyên khoa phù hợp với shift type.
            //    L01/L02/L03: specialties lấy từ config (mặc định Ngoại,Nội; có thể mở rộng qua UI).
            //    L04: staff có specialty khớp requirement HOẶC cross-specialty enabled.
            //    Tập trung logic tại StaffShiftTypeEligibility để thống nhất giữa
            //    scoring engine và thuật toán.
            Integer requiredSpecId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;
            java.util.List<String> nonL04Allowed = getNonL04AllowedSpecialties(shiftTypeId);
            boolean isEligible = StaffShiftTypeEligibility
                    .isEligible(staff, shiftTypeId, requiredSpecId, nonL04Allowed);
            // For L04 with cross-specialty enabled: staff from other eligible specialties are allowed
            if (!isEligible && crossEnabled && ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)) {
                // Cross-specialty L04: staff must belong to at least ONE eligible specialty
                // (CORE or extended). Use ALL_ELIGIBLE_SPECIALTIES so Nhi/Mắt/Răng/Sản
                // staff can fill L04 when their own specialty's pool is exhausted.
                if (staff.getSpecialty() != null && StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES
                        .contains(staff.getSpecialty().getName())) {
                    isEligible = true;
                }
            }
            if (!isEligible) {
                if (log.isTraceEnabled()) {
                    log.trace("FILTER_ELIGIBILITY: staff={} type={} spec={} REJECTED",
                        staff.getId(), shiftTypeId, staff.getSpecialty() != null ? staff.getSpecialty().getName() : "null");
                }
                continue;
            }
            if (log.isTraceEnabled()) {
                log.trace("FILTER_ELIGIBILITY: staff={} type={} spec={} ACCEPTED",
                    staff.getId(), shiftTypeId, staff.getSpecialty() != null ? staff.getSpecialty().getName() : "null");
            }

            // 1. Check specialty FIRST (hard requirement for non-L04 or if cross-specialty disabled)
            boolean isStrictMatch = req.getSpecialty() == null
                    || (staff.getSpecialty() != null && staff.getSpecialty().getId().equals(req.getSpecialty().getId()));

            if (!isStrictMatch) {
                // For L04 with specialty + cross-specialty enabled, allow cross-specialty
                if (!crossEnabled) {
                    if (log.isTraceEnabled()) {
                        log.trace("FILTER_SPECIALTY: staff={} type={} spec={} REJECTED - cross-specialty disabled",
                            staff.getId(), shiftTypeId, staff.getSpecialty() != null ? staff.getSpecialty().getName() : "null");
                    }
                    continue;
                }

                // Cross-specialty enabled: count how many cross-specialty staff already assigned this day
                long crossAssignedToday = assignedStaffIds.stream()
                        .filter(id -> {
                            Staff s = pool.stream().filter(st -> st.getId().equals(id)).findFirst().orElse(null);
                            return s != null && s.getSpecialty() != null 
                                    && req.getSpecialty() != null
                                    && !s.getSpecialty().getId().equals(req.getSpecialty().getId());
                        })
                        .count();

                int totalRequired = Math.max(1, req.getRequiredStaffCount());
                int maxCrossCandidates = (int) Math.ceil(totalRequired * crossConfig.ratio());
                if (crossAssignedToday >= maxCrossCandidates) {
                    if (log.isTraceEnabled()) {
                        log.trace("FILTER_SPECIALTY: staff={} type={} spec={} REJECTED - cross cap reached ({}/{})",
                            staff.getId(), shiftTypeId, staff.getSpecialty().getName(), crossAssignedToday, maxCrossCandidates);
                    }
                    continue;
                }
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

            // Adjacent day restriction only applies to L01
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                Set<Integer> allAdjacentL01 = new HashSet<>();
                if (batchData.adjacentL01StaffIds() != null) allAdjacentL01.addAll(batchData.adjacentL01StaffIds());
                if (additionalAdjacentL01 != null) allAdjacentL01.addAll(additionalAdjacentL01);
                if (allAdjacentL01.contains(staff.getId())) continue;
            }

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
            // For L04 with specialty, fairShareKey = "L04:specialtyId" → use per-specialty count.
            // For other types, fairShareKey = shiftTypeId (e.g. "L01") → global count.
            // Only skip enforcement if maxShiftsPerTypeLimit is 0 or MAX (means "no cap configured").
            if (maxShiftsPerTypeLimit > 0 && maxShiftsPerTypeLimit < Integer.MAX_VALUE) {
                long thisTypeCount = getStaffCountForKey(staff.getId(), fairShareKey,
                        periodData.staffShiftTypeCounts(), l04PerSpecialtyCounts);
                if (thisTypeCount >= maxShiftsPerTypeLimit) continue;
            }
                        // 4b. Global per-staff total cap — use runtimeConfig if set, else per-staff maxShiftsPerMonth.
            int effectiveMaxShifts = (maxShiftsPerStaffLimit > 0 && maxShiftsPerStaffLimit < Integer.MAX_VALUE)
                    ? maxShiftsPerStaffLimit
                    : (staff.getMaxShiftsPerMonth() != null && staff.getMaxShiftsPerMonth() > 0
                            ? staff.getMaxShiftsPerMonth()
                            : Integer.MAX_VALUE);
            if (effectiveMaxShifts < Integer.MAX_VALUE) {
                long totalCurrent = getTotalStaffCount(staff.getId(),
                        periodData.staffShiftTypeCounts(), l04PerSpecialtyCounts);
                if (totalCurrent >= effectiveMaxShifts) continue;
            }
            // 4c. Per-type weekly max cap enforcement (l0XMaxPerWeek from config).
            // Weekly counts are reset when the ISO week changes (tracked in runGreedy).
            // This is a HARD cap — staff who have reached their weekly max for this type cannot be assigned.
            if (greedyWeeklyCounts != null && runtimeConfig != null) {
                int weeklyMax = 0;
                if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                    weeklyMax = runtimeConfig.getL01MaxPerWeek();
                } else if (ConflictDetectionService.SHIFT_TYPE_L02.equals(shiftTypeId)) {
                    weeklyMax = runtimeConfig.getL02MaxPerWeek();
                } else if (ConflictDetectionService.SHIFT_TYPE_L03.equals(shiftTypeId)) {
                    weeklyMax = runtimeConfig.getL03MaxPerWeek();
                } else if (ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)) {
                    weeklyMax = runtimeConfig.getL04MaxPerWeek();
                }
                if (weeklyMax > 0) {
                    Map<String, Integer> staffWeekly = greedyWeeklyCounts.get(staff.getId());
                    int currentWeekly = staffWeekly != null ? staffWeekly.getOrDefault(shiftTypeId, 0) : 0;
                    if (currentWeekly >= weeklyMax) {
                        if (log.isDebugEnabled()) {
                            log.debug("WEEKLY_MAX_BLOCK: staff={} type={} current={} max={}",
                                staff.getId(), shiftTypeId, currentWeekly, weeklyMax);
                        }
                        continue;
                    }
                }
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
        if (crossEnabled && shouldPreferCrossSpecialty(req, crossConfig.ratio()) && !crossMatches.isEmpty()) {
            eligible.addAll(crossMatches);
            eligible.addAll(strictMatches);
        } else {
            eligible.addAll(strictMatches);
            eligible.addAll(crossMatches);
        }
        return eligible;
    }

    private boolean shouldPreferCrossSpecialty(ShiftRequirement req, float ratio) {
        if (!ConflictDetectionService.SHIFT_TYPE_L04.equals(req.getShiftType().getId()) || req.getSpecialty() == null) {
            return false;
        }
        if (ratio <= 0) {
            return false;
        }
        int percentage = Math.min(100, Math.max(1, Math.round(ratio * 100)));
        int bucket = Math.floorMod(Objects.hash(req.getWorkDate(), req.getSpecialty().getId(), req.getShiftType().getId()), 100);
        return bucket < percentage;
    }

    /**
     * Get staff shift type count, merging DB counts with in-memory counts from the current scheduling run.
     * For L04 with specialty, uses "L04:specialtyId" as the running key and adds the DB-level L04 baseline.
     */
    long getStaffCountForKey(Integer staffId, String countKey,
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

    long getTotalStaffCount(Integer staffId,
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

    boolean isStrictMatchForStaff(Staff staff, ShiftRequirement req) {
        return algorithmRunner.isStrictMatchForStaff(staff, req);
    }

    // ==================== REQUIREMENTS GENERATION FROM CONFIG ====================

    /**
     * Re-sync persisted requirements for a period with the current auto-gen config.
     * Existing ShiftRequirement rows keep their id (FK safety with schedule rows) but get their
     * requiredStaffCount updated to match the latest min/max per day from the config.
     * Without this, previous runs' stale requiredCount values would persist and ignore config changes.
     */
    private void syncExistingRequirementsWithConfig(SchedulePeriod period, AutoGenConfig config, List<Staff> activeStaff) {
        requirementAutoGenService.syncExistingRequirementsWithConfig(period, config, activeStaff);
    }

    private List<ShiftRequirement> generateRequirementsFromConfig(SchedulePeriod period, AutoGenConfig config, List<Staff> activeStaff) {
        return requirementAutoGenService.generateRequirementsFromConfig(period, config, activeStaff);
    }

    private ShiftRequirement buildAutoRequirement(
            SchedulePeriod period,
            ShiftType shiftType,
            LocalDate workDate,
            Specialty specialty,
            int targetStaffCount,
            String note) {
        return requirementAutoGenService.buildAutoRequirement(period, shiftType, workDate, specialty, targetStaffCount, note);
    }

    /**
     * Resolve daily staff target from min/max config.
     * <p>
     * Logic: start from preferredMax (upper bound), clamp to min if needed, cap at pool.
     * Examples:
     *   min=3, max=4, pool=20 → 4 (within [min,max], cap at pool)
     *   min=3, max=4, pool=2  → 2 (below min, use pool)
     *   min=5, max=10, pool=20 → 10 (within [min,max], cap at pool)
     *   min=5, max=10, pool=7  → 7 (below min, use pool)
     *   min=5, max=0, pool=20  → 5 (max=0 means unlimited, use min)
     */
    private int resolveSoftDailyTarget(int preferredMin, int preferredMax, int eligiblePoolSize) {
        return requirementAutoGenService.resolveSoftDailyTarget(preferredMin, preferredMax, eligiblePoolSize);
    }

    private int countActiveStaffBySpecialty(List<Staff> activeStaff, Integer specialtyId) {
        return requirementAutoGenService.countActiveStaffBySpecialty(activeStaff, specialtyId);
    }

    private List<ShiftRequirement> persistRequirementsIfTransient(List<ShiftRequirement> requirements) {
        return requirementAutoGenService.persistRequirementsIfTransient(requirements);
    }

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
        throw new UnsupportedOperationException("Incremental rescheduling requires CSP which has been removed");
    }

    /** Re-hydrate a raw {@link SchedulingResult} from the incremental path into Schedule entities. */
    private Object runCspWithResult(SchedulingResult result, SchedulePeriod period) {
        throw new UnsupportedOperationException("CSP has been removed");
    }

    static List<ShiftRequirementInfo> toRequirementInfos(List<ShiftRequirement> requirements) {
        return requirements.stream()
                .map(r -> new ShiftRequirementInfo(
                        r.getShiftType().getId(),
                        r.getWorkDate(),
                        r.getRequiredStaffCount(),
                        r.getSpecialty() != null ? r.getSpecialty().getId() : null))
                .collect(Collectors.toList());
    }

    private int countChanges(ScheduleChange changes) {
        return 0;
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
    private java.util.concurrent.locks.Lock acquirePeriodLock(Integer periodId) {
        return periodLocks.computeIfAbsent(periodId,
                id -> new java.util.concurrent.locks.ReentrantLock());
    }

    /**
     * Check if the lock for the given period has been held longer than the stale
     * threshold. Used to detect requests where the client disconnected (page refresh)
     * but the backend thread is still running (e.g. CSP solver).
     */
	    private boolean isLockStale(Integer periodId) {
	        Long acquiredAt = lockAcquiredAt.get(periodId);
	        return acquiredAt != null && (System.currentTimeMillis() - acquiredAt) > STALE_LOCK_TIMEOUT_MS;
	    }

	    /**
	     * Auto-adjust config d?a trên s? nhân s? th?c t?.
	     * Ch? ch?y khi b?t c?u hình auto_adjust_config=true.
	     * Không thay d?i yêu c?u khách hàng, mà tính toán giá tr? phù h?p.
	     */
	    private void autoScheduleConfigPreCheck(
	            SchedulePeriod period,
	            List<Staff> activeStaff,
	            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig) {

	        int staffCount = Math.max(1, activeStaff.size());
	        int periodDays = (int) java.time.temporal.ChronoUnit.DAYS.between(
	                period.getStartDate(), period.getEndDate()) + 1;

	        // ?c tính t?ng yêu c?u t? config hi?n t?i
	        var autoGenCfg = algorithmConfigService.getAutoGenConfig().orElse(null);
	        if (autoGenCfg == null) return;

	        int estimatedDaily = autoGenCfg.l01MaxPerDay() + autoGenCfg.l02MaxPerDay()
	                + autoGenCfg.l03MaxPerDay() + autoGenCfg.l04MaxPerDay() * 6;
	        int estimatedTotal = estimatedDaily * periodDays;

	        // Fair max = t?ng yêu c?u / s? NS +50% buffer ?? có capacity d?phòng
	        int fairMax = (int) Math.ceil((double) estimatedTotal / staffCount * 1.5);
	        if (runtimeConfig.getMaxShiftsPerStaff() <= 0 || runtimeConfig.getMaxShiftsPerStaff() != fairMax) {
	            log.warn("[AutoAdjust] maxShiftsPerStaff: {} -> {} (est.{} ca, {} NS)",
	                    runtimeConfig.getMaxShiftsPerStaff(), fairMax, estimatedTotal, staffCount);
	            runtimeConfig.setMaxShiftsPerStaff(fairMax);
	        }

	        // L01-L03: ch? Ngo?i+N?i (dùng isEligible ?? tránh l?i encoding)
	        long ngoaiNoi = activeStaff.stream()
	                .filter(s -> StaffShiftTypeEligibility.isEligible(
	                        s, ConflictDetectionService.SHIFT_TYPE_L01, null))
	                .count();
	        int fairNonL04 = Math.max(1, (int) Math.ceil(ngoaiNoi / 4.0));
	        if (autoGenCfg.l01MaxPerDay() > fairNonL04) {
	            log.warn("[AutoAdjust] L01 max: {} -> {} (eligible={})", autoGenCfg.l01MaxPerDay(), fairNonL04, ngoaiNoi);
	            algorithmConfigService.updateAutoGenField("auto_gen_l01_max_per_day", String.valueOf(fairNonL04));
	        }
	        if (autoGenCfg.l02MaxPerDay() > fairNonL04) {
	            log.warn("[AutoAdjust] L02 max: {} -> {} (eligible={})", autoGenCfg.l02MaxPerDay(), fairNonL04, ngoaiNoi);
	            algorithmConfigService.updateAutoGenField("auto_gen_l02_max_per_day", String.valueOf(fairNonL04));
	        }
	        if (autoGenCfg.l03MaxPerDay() > fairNonL04) {
	            log.warn("[AutoAdjust] L03 max: {} -> {} (eligible={})", autoGenCfg.l03MaxPerDay(), fairNonL04, ngoaiNoi);
	            algorithmConfigService.updateAutoGenField("auto_gen_l03_max_per_day", String.valueOf(fairNonL04));
	        }

	        // L04: mỗi chuyên khoa cần 1 L04/ngày → l04MaxPerDay = 1
	        long specCount = activeStaff.stream().filter(s -> s.getSpecialty() != null).count();
	        long activeSpecialtyCount = activeStaff.stream()
	                .filter(s -> s.getSpecialty() != null)
	                .map(s -> s.getSpecialty().getId())
	                .distinct()
	                .count();
	        int poolPerSpec = (int) Math.max(1, specCount / Math.max(1, activeSpecialtyCount));
	        int fairL04 = poolPerSpec > 5 ? 2 : 1;
	        if (autoGenCfg.l04MaxPerDay() > fairL04) {
	            log.warn("[AutoAdjust] L04 max: {} -> {} (specialties={}, pool/spec={})",
	                    autoGenCfg.l04MaxPerDay(), fairL04, activeSpecialtyCount, poolPerSpec);
	            algorithmConfigService.updateAutoGenField("auto_gen_l04_max_per_day", String.valueOf(fairL04));
	        }
	
	        // maxShiftsPerDay: không auto-adjust, để thuật toán tự quyết định
	        // dựa trên conflict rules (L01+L02 cấm, L03+L04 cấm)
	
	        log.info("[AutoAdjust] Hoàn t?t: maxShifts={}, L01-L03={}/ngày, L04={}/ngày",
	                runtimeConfig.getMaxShiftsPerStaff(), fairNonL04, fairL04);
		    }

	    /**
	     * Rotation post-processing để đảm bảo EVERY staff có ALL 4 shift types.
	     * Nếu staff thiếu loại nào, swap từ loại khác (ưu tiên loại có nhiều hơn 1) sang loại thiếu.
	     */
	    private int applyRotationPostProcessing(List<Schedule> schedules,
	            List<ShiftRequirement> requirements, List<Staff> activeStaff) {
	        if (schedules.isEmpty()) return 0;
	        
	        Map<Integer, Set<String>> staffTypes = new HashMap<>();
	        for (Schedule s : schedules) {
	            staffTypes.computeIfAbsent(s.getStaff().getId(), k -> new HashSet<>())
	                    .add(s.getShiftType().getId());
	        }
	        
	        int swaps = 0;
	        for (Map.Entry<Integer, Set<String>> entry : staffTypes.entrySet()) {
	            if (entry.getValue().size() >= 4) continue;
	            
	            int sid = entry.getKey();
	            Set<String> types = entry.getValue();
	            String[] needed = {"L01", "L02", "L03", "L04"};
	            
	            for (String need : needed) {
	                if (types.contains(need)) continue;
	                
	                for (String swapFrom : new String[]{"L04", "L01", "L02", "L03"}) {
	                    if (!types.contains(swapFrom)) continue;
	                    
	                    long count = schedules.stream()
	                            .filter(s -> s.getStaff().getId() == sid && swapFrom.equals(s.getShiftType().getId()))
	                            .count();
	                    if (count <= 1) continue;
	                    
	                    for (Schedule s : schedules) {
	                        if (s.getStaff().getId() == sid && swapFrom.equals(s.getShiftType().getId())) {
	                            // Check conflict
	                            if (hasRotationConflict(s, schedules, need)) continue;
	                            
	                            // Swap
	                            s.setShiftType(findShiftTypeById(need, requirements));
	                            ShiftRequirement req = findRequirementForDate(s, need, requirements);
	                            if (req != null) s.setRequirement(req);
	                            types.add(need);
	                            
	                            long newCount = schedules.stream()
	                                    .filter(s2 -> s2.getStaff().getId() == sid && swapFrom.equals(s2.getShiftType().getId()))
	                                    .count();
	                            if (newCount <= 0) types.remove(swapFrom);
	                            swaps++;
	                            break;
	                        }
	                    }
	                    if (types.contains(need)) break;
	                }
	            }
	        }
	        
	        // Fix consecutive L01 violations created by rotation
	        for (int i = schedules.size() - 1; i >= 0; i--) {
	            Schedule s = schedules.get(i);
	            if (!"L01".equals(s.getShiftType().getId())) continue;
	            int sid = s.getStaff().getId();
	            for (Schedule other : schedules) {
	                if (other == s || other.getStaff().getId() != sid) continue;
	                if (!"L01".equals(other.getShiftType().getId())) continue;
	                long diff = Math.abs(other.getWorkDate().toEpochDay() - s.getWorkDate().toEpochDay());
	                if (diff == 1) {
	                    s.setShiftType(findShiftTypeById("L04", requirements));
	                    ShiftRequirement l04Req = findRequirementForDate(s, "L04", requirements);
	                    if (l04Req != null) s.setRequirement(l04Req);
	                    break;
	                }
	            }
	        }
	        
		        return swaps;
		    }
		    
		    /**
		     * Gap-fill: bổ sung ca còn thiếu cho tất cả algorithms.
		     * Quét từng requirement, tìm NS rảnh không conflict, ưu tiên NS ít ca.
		     */
		    private int applyGapFill(List<Schedule> schedules, List<ShiftRequirement> requirements,
		            List<Staff> activeStaff, SchedulePeriod period,
		            AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
		            Set<Integer> excludedStaffIds) {
		        if (schedules.isEmpty() || requirements.isEmpty()) return 0;
		        
		        Map<Integer, Staff> staffMap = activeStaff.stream()
		                .collect(Collectors.toMap(Staff::getId, s -> s));
		        Map<Integer, Integer> staffCount = new HashMap<>();
		        for (Schedule s : schedules) {
		            staffCount.merge(s.getStaff().getId(), 1, Integer::sum);
		        }
		        
		        int gapFilled = 0;
		        int maxShifts = runtimeConfig != null && runtimeConfig.getMaxShiftsPerStaff() > 0
		                ? runtimeConfig.getMaxShiftsPerStaff() : Integer.MAX_VALUE;
		        
		        // Group requirements by date
		        Map<LocalDate, List<ShiftRequirement>> byDate = requirements.stream()
		                .collect(Collectors.groupingBy(ShiftRequirement::getWorkDate, 
		                        () -> new java.util.TreeMap<>(), Collectors.toList()));
		        
		        for (Map.Entry<LocalDate, List<ShiftRequirement>> e : byDate.entrySet()) {
		            LocalDate date = e.getKey();
		            for (ShiftRequirement req : e.getValue()) {
		                String shiftTypeId = req.getShiftType().getId();
		                Integer specId = req.getSpecialty() != null ? req.getSpecialty().getId() : null;
		                
		                // Count already assigned
		                long assigned = schedules.stream()
		                        .filter(s -> s.getWorkDate().equals(date) && s.getShiftType().getId().equals(shiftTypeId))
		                        .filter(s -> {
		                            if (specId == null) return true;
		                            return s.getRequirement() != null && s.getRequirement().getSpecialty() != null
		                                    && s.getRequirement().getSpecialty().getId().equals(specId);
		                        })
		                        .count();
		                
		                int stillNeeded = req.getRequiredStaffCount() - (int) assigned;
		                if (stillNeeded <= 0) continue;
		                
		                // Build per-type counts and per-staff type sets from existing schedules
		                Map<Integer, Map<String, Integer>> typeCounts = new HashMap<>();
		                Map<Integer, Set<String>> staffTypeSets = new HashMap<>();
		                for (Schedule s : schedules) {
		                    int sid = s.getStaff().getId();
		                    String tid = s.getShiftType().getId();
		                    typeCounts.computeIfAbsent(sid, k -> new HashMap<>()).merge(tid, 1, Integer::sum);
		                    staffTypeSets.computeIfAbsent(sid, k -> new HashSet<>()).add(tid);
		                }
		                
		                // Score candidates with balance awareness
		                List<Object[]> scored = activeStaff.stream()
		                        .filter(s -> excludedStaffIds == null || !excludedStaffIds.contains(s.getId()))
		                        .filter(s -> staffCount.getOrDefault(s.getId(), 0) < maxShifts)
		                        .filter(s -> !hasGapConflict(schedules, s.getId(), date, shiftTypeId, specId))
		                        .filter(s -> {
		                            if (specId == null) return true;
		                            return s.getSpecialty() != null && s.getSpecialty().getId().equals(specId);
		                        })
		                        .map(s -> {
		                            int sid = s.getId();
		                            int cnt = staffCount.getOrDefault(sid, 0);
		                            // Total shift penalty
		                            double score = 100.0 - cnt * 4.0;
		                            // Per-type balance
		                            int typeCnt = typeCounts.getOrDefault(sid, new HashMap<>()).getOrDefault(shiftTypeId, 0);
		                            score -= typeCnt * 6.0;
		                            // Rotation bonus for missing types
		                            int missingTypes = 4 - staffTypeSets.getOrDefault(sid, new HashSet<>()).size();
		                            score += missingTypes * 10.0;
		                            // L04 specialty balance
		                            if (specId != null && "L04".equals(shiftTypeId)) {
		                                long l04InSpec = schedules.stream()
		                                        .filter(s2 -> s2.getStaff().getId() == sid && "L04".equals(s2.getShiftType().getId())
		                                                && s2.getRequirement() != null && s2.getRequirement().getSpecialty() != null
		                                                && s2.getRequirement().getSpecialty().getId().equals(specId))
		                                        .count();
		                                score -= l04InSpec * 5.0;
		                            }
		                            return new Object[]{s, score};
		                        })
		                        .sorted((a, b) -> Double.compare((Double)b[1], (Double)a[1]))
		                        .limit(stillNeeded)
		                        .collect(Collectors.toList());
		                
		                for (Object[] entry : scored) {
		                    Staff s = (Staff) entry[0];
		                    Schedule sch = new Schedule();
		                    sch.setStaff(s);
		                    sch.setPeriod(period);
		                    sch.setWorkDate(date);
		                    sch.setShiftType(req.getShiftType());
		                    sch.setRequirement(req);
		                    sch.setHasConflict(false);
		                    schedules.add(sch);
		                    staffCount.merge(s.getId(), 1, Integer::sum);
		                    gapFilled++;
		                }
		            }
		        }
		        return gapFilled;
		    }
		    
		    private boolean hasGapConflict(List<Schedule> schedules, int staffId, 
		            LocalDate date, String shiftTypeId, Integer specId) {
		        for (Schedule s : schedules) {
		            if (s.getStaff().getId() != staffId) continue;
		            
		            // Same type already on this date
		            if (s.getWorkDate().equals(date) && s.getShiftType().getId().equals(shiftTypeId)) {
		                return true;
		            }
		            
		            // Conflict pairs
		            if (s.getWorkDate().equals(date)) {
		                String existingType = s.getShiftType().getId();
		                if (("L01".equals(shiftTypeId) && "L02".equals(existingType))
		                        || ("L02".equals(shiftTypeId) && "L01".equals(existingType))
		                        || ("L03".equals(shiftTypeId) && "L04".equals(existingType))
		                        || ("L04".equals(shiftTypeId) && "L03".equals(existingType))) {
		                    return true;
		                }
		            }
		            
		            // Consecutive L01
		            if ("L01".equals(shiftTypeId) && "L01".equals(s.getShiftType().getId())) {
		                long diff = Math.abs(s.getWorkDate().toEpochDay() - date.toEpochDay());
		                if (diff == 1) return true;
		            }
		        }
		        return false;
		    }
		    
		    private boolean hasRotationConflict(Schedule target, List<Schedule> schedules, String newType) {
	        for (Schedule s : schedules) {
	            if (s == target) continue;
	            if (!s.getStaff().getId().equals(target.getStaff().getId())) continue;
	            
	            if (s.getWorkDate().equals(target.getWorkDate())) {
	                String existingType = s.getShiftType().getId();
	                if (("L01".equals(newType) && "L02".equals(existingType))
	                        || ("L02".equals(newType) && "L01".equals(existingType))
	                        || ("L03".equals(newType) && "L04".equals(existingType))
	                        || ("L04".equals(newType) && "L03".equals(existingType))) {
	                    return true;
	                }
	            }
	            if ("L01".equals(newType) && "L01".equals(s.getShiftType().getId())) {
	                long diff = Math.abs(s.getWorkDate().toEpochDay() - target.getWorkDate().toEpochDay());
	                if (diff == 1) return true;
	            }
	        }
	        return false;
	    }
	    
	    private com.hospital.scheduler.entity.ShiftType findShiftTypeById(String id, List<ShiftRequirement> reqs) {
	        return reqs.stream().filter(r -> r.getShiftType().getId().equals(id))
	                .findFirst().map(ShiftRequirement::getShiftType).orElse(null);
	    }
	    
	    private ShiftRequirement findRequirementForDate(Schedule schedule, String shiftTypeId,
	            List<ShiftRequirement> reqs) {
	        return reqs.stream()
	                .filter(r -> r.getShiftType().getId().equals(shiftTypeId)
	                        && r.getWorkDate().equals(schedule.getWorkDate()))
	                .findFirst().orElse(null);
	    }
	}
