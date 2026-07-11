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
    private final AlgorithmMetricsRepository metricsRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final AuditHistoryService auditHistoryService;
    private final CompensationDateCalculator compensationDateCalculator;
    private final NotificationService notificationService;
    private final AlgorithmConfigService algorithmConfigService;
    private final HolidayRepository holidayRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SpecialtyRepository specialtyRepository;
    private final CSPScheduler cspScheduler;
    private final EntityManager entityManager;
    private final ScheduleConflictRepository scheduleConflictRepository;
    private final PreviewConflictCheckService previewConflictCheckService;
    private final AlgorithmProgressTracker progressTracker;
    private final ScheduleQualityScorer scheduleQualityScorer;

    // Thread-local so concurrent requests don't share state
    private final ThreadLocal<Map<String, Set<String>>> inMemoryAssignments = ThreadLocal.withInitial(HashMap::new);
    private final ThreadLocal<Set<String>> inMemoryCompensationShiftDates = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Set<String>> allCompensationShiftDates = ThreadLocal.withInitial(HashSet::new);
    // Swap request priority: Set of staff IDs who should be PREFERRED (those whose swap partner was assigned)
    private final ThreadLocal<Set<Integer>> swapPriorityStaffIds = ThreadLocal.withInitial(HashSet::new);

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
            Set<Integer> allL01StaffIdsInRange,
            Map<Integer, Staff> staffMap  // For accessing maxShiftsPerMonth
    ) {}

    public AutoScheduleResponse previewSchedule(AutoScheduleRequestDTO request) {
        inMemoryAssignments.set(new HashMap<>());
        inMemoryCompensationShiftDates.set(new HashSet<>());
        allCompensationShiftDates.set(new HashSet<>());
        swapPriorityStaffIds.set(new HashSet<>());
        try {
            return runScheduling(request, false);
        } finally {
            inMemoryAssignments.remove();
            inMemoryCompensationShiftDates.remove();
            allCompensationShiftDates.remove();
            swapPriorityStaffIds.remove();
        }
    }

    public AutoScheduleResponse autoSchedule(AutoScheduleRequestDTO request) {
        inMemoryAssignments.set(new HashMap<>());
        inMemoryCompensationShiftDates.set(new HashSet<>());
        allCompensationShiftDates.set(new HashSet<>());
        swapPriorityStaffIds.set(new HashSet<>());
        try {
            return runScheduling(request, true);
        } finally {
            inMemoryAssignments.remove();
            inMemoryCompensationShiftDates.remove();
            allCompensationShiftDates.remove();
            swapPriorityStaffIds.remove();
        }
    }

    public AutoScheduleResponse applyPreviewSchedule(com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO request) {
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
            allCompensationShiftDates.get().add(compKey);
        }
        log.info("Loaded {} existing compensation days for period {} into memory cache", 
                existingCompDays.size(), period.getId());

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
            ShiftRequirement requirement = requirementRepository.findByPeriodId(period.getId()).stream()
                    .filter(req -> req.getWorkDate().toString().equals(item.getWorkDate()))
                    .filter(req -> req.getShiftType().getId().equals(item.getShiftTypeId()))
                    .findFirst()
                    .orElse(null);
            ShiftType shiftType = requirement != null ? requirement.getShiftType() : null;
            if (shiftType == null) {
                throw new BadRequestException("Không tìm thấy ca trực phù hợp cho ngày " + item.getWorkDate());
            }

            LocalDate workDate = LocalDate.parse(item.getWorkDate());

            // Check if workDate is a holiday
            if (holidayRepository.existsByHolidayDate(workDate)) {
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
        List<Map<String, Object>> unassignedDays = buildUnassignedDays(periodRequirements, savedSchedules);

        int distinctStaffAssigned = (int) savedSchedules.stream()
                .map(s -> s.getStaff().getId())
                .distinct()
                .count();
        int staffCount = distinctStaffAssigned > 0 ? distinctStaffAssigned : 1;
        BigDecimal balanceScore = calculateBalanceScore(savedSchedules, staffCount);

        long executionTime = System.currentTimeMillis() - startTime;

        // Save metrics so History tab shows this execution. The conflictCount we record
        // is filled in below after we re-check the persisted state — leaves placeholders
        // in the metrics call site so the value stays consistent with what the monthly-schedule
        // page will show.
        try {
            saveMetrics(period, request.getAlgorithmType(), (int) executionTime, coverageRate, balanceScore, 0, savedSchedules.size());
            log.info("Metrics saved for period {} with algorithm {}: coverage={}%, balance={}",
                    period.getId(), request.getAlgorithmType(), coverageRate, balanceScore);
        } catch (Exception e) {
            log.error("Failed to save metrics for period {}: {}", period.getId(), e.getMessage(), e);
        }

        // Re-check conflicts after persistence so the response carries the same value the
        // monthly-schedule page will surface. Without this, the response always reports 0
        // and the manager has no signal that the apply produced violations.
        int appliedConflictCount = 0;
        try {
            appliedConflictCount = conflictDetectionService.checkPeriodConflicts(period.getId()).getTotalConflicts();
        } catch (Exception e) {
            log.error("Failed to check period conflicts after apply for period {}: {}", period.getId(), e.getMessage(), e);
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
        allCompensationShiftDates.get().clear();
        log.info("Cleared in-memory compensation day cache for period {}", period.getId());

        // Load runtime config from DB (or use defaults if not set)
        AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig = algorithmConfigService.getRuntimeConfig();
        log.info("Using runtime config: maxIterations={}, weekendWeight={}, overnightRecoveryHours={}, greedyThreshold={}, balanceMin={}, maxShiftsPerStaff={}",
                runtimeConfig.getMaxIterations(), runtimeConfig.getWeekendWeight(),
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

        // Always generate requirements from algorithm config (no manual requirements page)
        var autoGenConfig = algorithmConfigService.getAutoGenConfig();
        if (autoGenConfig.isEmpty() || !autoGenConfig.get().enabled()) {
            throw new BadRequestException(
                    "Cấu hình auto-gen chưa được bật. Vui lòng bật auto_generate_requirements trong cấu hình thuật toán.");
        }
        // CRITICAL: Re-sync existing requirements with current config so changes to min/max per day
        // take effect on the next preview run. Without this, the scheduler would re-use stale
        // requiredCount values persisted by a previous run with older config.
        syncExistingRequirementsWithConfig(period, autoGenConfig.get(), activeStaff);
        requirements = generateRequirementsFromConfig(period, autoGenConfig.get(), activeStaff);
        requirements = persistRequirementsIfTransient(requirements);
        log.info("Generated {} requirements from config for period {}", requirements.size(), period.getId());

        // Pre-load existing compensation days from the same period so greedy doesn't assign L01 on a day
        // that is already someone's compensation day (confirmed day off — cannot assign L01)
        List<CompensationDay> existingCompDays = compensationDayRepository.findByPeriodId(period.getId());
        for (CompensationDay cd : existingCompDays) {
            allCompensationShiftDates.get().add(cd.getStaff().getId() + "_" + cd.getCompensationDate().toString());
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
                    allCompensationShiftDates.get().add(existing.getStaff().getId() + "_" + compDate.toString());
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
                : "CSP_MRV_FC";

        List<Schedule> createdSchedules;
        if ("ROUND_ROBIN".equals(algorithmType)
                || "FAIR_ROUND_ROBIN".equals(algorithmType)
                || "FAIR".equals(algorithmType)
                || "FAIR_GREEDY".equals(algorithmType)) {
            createdSchedules = runFairGreedy(period, requirements, activeStaff, save, runtimeConfig,
                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
        } else if ("CSP_MRV_FC".equals(algorithmType) || "CSP".equals(algorithmType)) {
            // Run CSP-MRV-FC (Constraint Satisfaction with MRV + Forward Checking).
            // The CSP scheduler is the recommended default per spec: it propagates
            // arc-consistency (AC-3) before search and uses learned nogoods, so it
            // can produce a feasible solution for over-constrained periods where
            // Greedy / Round-Robin fail.
            log.info("Running CSP-MRV-FC for period {}", period.getId());
            SchedulingResultWithFairness cspResult = runCsp(period, requirements, activeStaff, save,
                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
            createdSchedules = cspResult.schedules();
            lastFairnessScore = cspResult.fairnessScore();
            log.info("CSP-MRV-FC completed with {} schedules", createdSchedules.size());
            if (createdSchedules.isEmpty()) {
                // Fall back to Greedy so the UI never shows "0% coverage" when a
                // feasible plan exists via a different algorithm. CSP-MRV-FC can
                // fail on over-constrained periods (e.g. period 5 with very few
                // Mắt/Răng staff) even though FAIR_GREEDY finds 300+ schedules,
                // and the production UX must keep showing the user a usable plan.
                // Preview also benefits: a slower but populated result is more
                // useful than an empty coverage chart. Also triggered when CSP
                // returned a *partial* plan under timeout (the partial record
                // was discarded so Greedy can re-cover from scratch).
                log.warn("CSP-MRV-FC returned 0 schedules / partial for period {} — falling back to Greedy. Check CspSearchEngine logs for INCONSISTENT result.", period.getId());
                createdSchedules = runGreedy(period, requirements, activeStaff, save, runtimeConfig,
                        request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
                log.info("Greedy fallback result: {} schedules", createdSchedules.size());
            }
        } else {
            createdSchedules = runGreedy(period, requirements, activeStaff, save, runtimeConfig,
                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
        }
        int greedyStaffCount = (int) createdSchedules.stream().map(s -> s.getStaff().getId()).distinct().count();
        BigDecimal greedyBalanceScore = calculateBalanceScore(createdSchedules, greedyStaffCount > 0 ? greedyStaffCount : 1);

        // balance_score_min: if balance is below threshold, try alternatives and pick the best
        // This applies to ALL algorithms, not just GREEDY
        BigDecimal bestScore = greedyBalanceScore;
        List<Schedule> bestSchedules = createdSchedules;

        if (greedyBalanceScore.compareTo(runtimeConfig.getBalanceScoreMin()) < 0 && !activeStaff.isEmpty()) {
            // Preview path: skip Fair Greedy fallback so the user gets a fast response.
            // The Fair Greedy fallback is a heavy second pass that adds minutes on
            // a 1-month period with 23 staff — preview is about showing the user the
            // CSP plan quickly, not about finding the global optimum.
            if (!save) {
                log.info("{} balance score {} < threshold {} (preview) — skipping Fair Greedy fallback",
                        algorithmType, greedyBalanceScore, runtimeConfig.getBalanceScoreMin());
            } else {
                // Try Fair Greedy as a fallback
                log.info("{} balance score {} < threshold {}, trying Fair Greedy fallback",
                        algorithmType, greedyBalanceScore, runtimeConfig.getBalanceScoreMin());
                List<Schedule> fairGreedySchedules = runFairGreedy(period, requirements, activeStaff, false, runtimeConfig,
                        request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
                int fgStaffCount = (int) fairGreedySchedules.stream().map(s -> s.getStaff().getId()).distinct().count();
                BigDecimal fgBalanceScore = calculateBalanceScore(fairGreedySchedules, fgStaffCount > 0 ? fgStaffCount : 1);
                log.info("Fair Greedy fallback: balanceScore={} ({} had {})", fgBalanceScore, algorithmType, greedyBalanceScore);
                if (fgBalanceScore.compareTo(bestScore) > 0) {
                    log.info("Using Fair Greedy result (better balance score)");
                    bestScore = fgBalanceScore;
                    bestSchedules = fairGreedySchedules;
                    // If we chose FG as the better option, run again with save=true
                    if (!save) {
                        createdSchedules = runFairGreedy(period, requirements, activeStaff, save, runtimeConfig,
                                request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
                        bestSchedules = createdSchedules;
                    }
                }
            }
        }

        // Use the best result
        createdSchedules = bestSchedules;

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
        List<Schedule> createdSchedules = new ArrayList<>();
        Map<LocalDate, List<ShiftRequirement>> requirementsByDate = groupRequirementsByDate(requirements);

        // OPTIMIZATION 1: Load all conflict data for entire period in ONE pass (instead of per-day)
        // OPTIMIZATION 2: Load all shift type counts in ONE query (instead of N×4 queries)
        PeriodConflictData periodData = loadPeriodConflictData(period, requirements, activeStaff);

        // FAIRNESS: Pre-compute fair share per shift type = ceil(totalDemand[type] / eligiblePool)
        // L04 uses per-specialty pool (spec M05); L01/L02/L03 use full staffPool.
        int staffPool = Math.max(1, activeStaff.size());
        Map<String, Integer> fairSharePerType = computeFairSharePerTypeWithStaff(requirements, staffPool, activeStaff);

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

            List<ShiftRequirement> todayReqs = sortRequirementsByPriority(
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
                        .comparingDouble((Staff s) -> swapPriorityStaffIds.get().contains(s.getId()) ? 0.0 : 1.0)
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
                        });

                List<Staff> eligibleStaff = filterAndSortEligibleStaffBatch(
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
                    eligibleStaff = filterAndSortEligibleStaffBatch(
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
                    Schedule saved = buildAndSaveSchedule(period, staff, req, workDate, save, createdSchedules);
                    if (saved == null) continue;
                    // DEBUG: verify adjacentL01 blocking worked for L01 assignments
                    if (log.isInfoEnabled() && ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        log.info("Greedy L01 SAVED: staff={} date={} (adjPrev={} blocked)", staff.getId(), workDate, adjacentL01FromPrev.size());
                    }
                    trackAssignment(staff, workDate, req.getShiftType().getId());
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
                        createCompensationDayForAuto(saved);
                    }
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
    private List<Schedule> runFairGreedy(SchedulePeriod period, List<ShiftRequirement> requirements,
                                          List<Staff> activeStaff, boolean save,
                                          AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
                                          Set<Integer> excludedStaffIds) {
        List<Schedule> createdSchedules = new ArrayList<>();
        // Per-type rotation index — same structure as Greedy's shiftTypeRotationIndex so that
        // Fair Greedy also rotates each staff through each shift type independently.
        final Map<String, Map<Integer, Integer>> fgShiftTypeRotationIndex = new HashMap<>();

        Map<LocalDate, List<ShiftRequirement>> requirementsByDate = groupRequirementsByDate(requirements);

        // FAIRNESS: Compute fair-share cap per shift type from actual demand.
        // L04 uses per-specialty pool (spec M05 — chuyên gia phải đúng chuyên khoa).
        // L01/L02/L03 use full staff pool.
        final int fgStaffPool = Math.max(1, activeStaff.size());
        final Map<String, Integer> fgFairSharePerType =
                computeFairSharePerTypeWithStaff(requirements, fgStaffPool, activeStaff);
        log.info("FG fairSharePerType: L01={} L02={} L03={} L04={}",
                fgFairSharePerType.get(ConflictDetectionService.SHIFT_TYPE_L01),
                fgFairSharePerType.get(ConflictDetectionService.SHIFT_TYPE_L02),
                fgFairSharePerType.get(ConflictDetectionService.SHIFT_TYPE_L03),
                fgFairSharePerType.get(ConflictDetectionService.SHIFT_TYPE_L04));

        // OPTIMIZATION: Load ALL conflict data in ONE query (same as Greedy)
        PeriodConflictData periodData = loadPeriodConflictData(period, requirements, activeStaff);

        // Track L01 assignments by date for adjacent-day back-to-back checking (same as Greedy)
        Map<LocalDate, Set<Integer>> l01AssignmentsByDate = new HashMap<>();

        // Track compensation days created during this run (same as Greedy)
        // When L01 is created on day N, staff cannot work any shift on their compensation day
        Map<LocalDate, Set<Integer>> compensationDaysByDate = new HashMap<>();

        // Track assignments created during this run so fairness decisions see current in-memory load.
        // Keys are plain shift type (L01/L02/L03) or L04 per-specialty (L04:<specialtyId>).
        Map<Integer, Map<String, Long>> fgRunningCounts = new HashMap<>();

        // Track per-type weekly counts for enforcing l0XMaxPerWeek (per-type weekly cap from config).
        // Key: staffId, Value: Map<shiftTypeId, weeklyCount>
        Map<Integer, Map<String, Integer>> fgWeeklyCounts = new HashMap<>();

        LocalDate currentDate = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();
        int fgCurrentWeekNumber = currentDate.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        int fgCurrentWeekYear = currentDate.get(java.time.temporal.WeekFields.ISO.weekBasedYear());
        while (!currentDate.isAfter(periodEnd)) {
            // Check if we've moved to a new week — reset weekly counts for l0XMaxPerWeek enforcement
            int newWeekNumber = currentDate.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            int newWeekYear = currentDate.get(java.time.temporal.WeekFields.ISO.weekBasedYear());
            if (newWeekNumber != fgCurrentWeekNumber || newWeekYear != fgCurrentWeekYear) {
                fgWeeklyCounts.clear();
                fgCurrentWeekNumber = newWeekNumber;
                fgCurrentWeekYear = newWeekYear;
            }

            List<ShiftRequirement> todayReqs = sortRequirementsByPriority(
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

                // Per-type rotation index for this shift type (use fairShareKey for L04 per-specialty)
                final Map<Integer, Integer> fgRotationForType = fgShiftTypeRotationIndex.computeIfAbsent(
                        fgFairShareKey, k -> new HashMap<>());
                final String fgCapturedKey = fgFairShareKey;
                final Map<Integer, Map<String, Long>> capturedFgCounts = fgRunningCounts;
                Comparator<Staff> fairnessComparator = Comparator
                        // Tier 1: SWAP PRIORITY
                        .comparingDouble((Staff s) -> swapPriorityStaffIds.get().contains(s.getId()) ? 0.0 : 1.0)
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
                        .thenComparingInt(s -> fgRotationForType.getOrDefault(s.getId(), 0))
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
                        fgGlobalMaxRR, fgShiftTypeMax, fgFairShareKey, fgRunningCounts, fgWeeklyCounts, runtimeConfig, activeStaff);

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
                            Integer.MAX_VALUE, fallbackCap, fgFairShareKey, fgRunningCounts, fgWeeklyCounts, runtimeConfig, activeStaff);
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
                    Schedule saved = buildAndSaveSchedule(period, staff, req, workDate, save, createdSchedules);
                    if (saved == null) continue;
                    trackAssignment(staff, workDate, req.getShiftType().getId());
                    // Update weekly count for this shift type (for l0XMaxPerWeek enforcement)
                    fgWeeklyCounts.computeIfAbsent(staff.getId(), k -> new HashMap<>())
                            .merge(shiftTypeId, 1, Integer::sum);
                    assignedStaffIds.add(staff.getId());
                    // Per-type rotation: increment rotation index for this specific shift type
                    fgShiftTypeRotationIndex.computeIfAbsent(shiftTypeId, k -> new HashMap<>())
                            .merge(staff.getId(), 1, Integer::sum);
                    assignedCount++;

                    // FIX: Track L01 assignment for adjacent-day checking (same as Greedy)
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        // Track for adjacent-day back-to-back check
                        l01AssignmentsByDate.computeIfAbsent(workDate, k -> new HashSet<>()).add(staff.getId());
                        // FIX: Track compensation day — staff cannot work any shift on their compensation day
                        LocalDate compDate = compensationDateCalculator.calculate(workDate);
                        if (compDate != null && !compDate.isBefore(period.getStartDate()) && !compDate.isAfter(period.getEndDate())) {
                            compensationDaysByDate.computeIfAbsent(compDate, k -> new HashSet<>()).add(staff.getId());
                        }
                    }
                    // Update in-memory counts for the current run so the next assignment sees fresh load.
                    // L04 is tracked per specialty; L01/L02/L03 use plain type keys.
                    fgRunningCounts
                            .computeIfAbsent(staff.getId(), k -> new HashMap<>())
                            .merge(fgFairShareKey, 1L, Long::sum);

                    if (save && ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        log.debug("Creating compensation day for auto-scheduled L01: staff={}, date={}", staff.getId(), workDate);
                        createCompensationDayForAuto(saved);
                    }
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
            Set<Integer> excludedStaffIds) {

        try {
            // Translate DB requirements -> algorithm DTO
            List<ShiftRequirementInfo> cspRequirements = requirements.stream()
                    .map(req -> new ShiftRequirementInfo(
                            req.getShiftType().getId(),
                            req.getWorkDate(),
                            req.getRequiredStaffCount(),
                            req.getSpecialty() != null ? req.getSpecialty().getId() : null))
                    .toList();

            // Existing compensation days (across the period, to avoid
            // creating overlapping days when we map back to Schedule).
            Set<String> existingCompDays = new HashSet<>();
            for (CompensationDay cd : compensationDayRepository.findByPeriodId(period.getId())) {
                // Use underscore separator for consistency with GA and in-memory cache
                String compKey = cd.getStaff().getId() + "_" + cd.getCompensationDate().toString();
                existingCompDays.add(compKey);
                // CRITICAL: Also add to in-memory cache to prevent duplicate compensation day creation
                allCompensationShiftDates.get().add(compKey);
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
            SchedulingResult cspResult = save
                    ? cspScheduler.solve(
                            activeStaff,
                            period.getStartDate(),
                            period.getEndDate(),
                            cspRequirements,
                            existingCompDays,
                            leaveRequests,
                            excludedStaffIds,
                            l04Allowed)
                    : cspScheduler.solveForPreview(
                            activeStaff,
                            period.getStartDate(),
                            period.getEndDate(),
                            cspRequirements,
                            existingCompDays,
                            leaveRequests,
                            excludedStaffIds,
                            l04Allowed);

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
                log.info("CSP-MRV-FC returned a partial plan for period {} ({} assignments) — falling back to Greedy to top up",
                        period.getId(), cspResult.getScheduleCount());
                return new SchedulingResultWithFairness(new ArrayList<>(), BigDecimal.ZERO, true);
            }

            // Convert domain assignments -> Schedule entities
            List<Schedule> createdSchedules = new ArrayList<>();
            Set<String> allCompensationDays = new HashSet<>(existingCompDays);

            // First pass: collect every L01 assignment's auto-comp day so the
            // second pass can skip them (mirrors the L01 post-processing in
            // runGeneticAlgorithm).
            for (Map.Entry<String, String> entry : cspResult.getAssignments().entrySet()) {
                if (!ConflictDetectionService.SHIFT_TYPE_L01.equals(entry.getValue())) continue;
                String[] parts = entry.getKey().split("\\|");
                if (parts.length != 2) continue;
                Integer staffId = Integer.parseInt(parts[0]);
                LocalDate workDate = LocalDate.parse(parts[1]);
                LocalDate compDate = compensationDateCalculator.calculate(workDate);
                if (compDate != null) {
                    allCompensationDays.add(staffId + "_" + compDate);
                }
            }

            // Second pass: persist
            // Requirements are pre-persisted in runScheduling() so the FK on
            // schedule.requirement_id resolves to a managed entity.
            for (Map.Entry<String, String> entry : cspResult.getAssignments().entrySet()) {
                String[] parts = entry.getKey().split("\\|");
                if (parts.length != 2) continue;
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
                            createCompensationDayForAuto(saved);
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


    // ==================== M07-F06: Báo cáo ngày chưa phân công ====================
    public Map<String, Object> getUnassignedDaysReport(Integer periodId) {
        SchedulePeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + periodId));

        List<ShiftRequirement> requirements = requirementRepository.findByPeriodId(periodId);
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        Map<String, List<ShiftRequirement>> requirementsByDateAndShift = new LinkedHashMap<>();
        for (ShiftRequirement req : requirements) {
            String key = req.getWorkDate() + "_" + req.getShiftType().getId();
            requirementsByDateAndShift.computeIfAbsent(key, k -> new ArrayList<>()).add(req);
        }

        Map<String, List<Schedule>> schedulesByDateAndShift = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            String key = s.getWorkDate() + "_" + s.getShiftType().getId();
            schedulesByDateAndShift.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<Map<String, Object>> unassignedDays = new ArrayList<>();

        for (Map.Entry<String, List<ShiftRequirement>> entry : requirementsByDateAndShift.entrySet()) {
            String key = entry.getKey();
            List<ShiftRequirement> reqs = entry.getValue();
            List<Schedule> assigned = schedulesByDateAndShift.getOrDefault(key, Collections.emptyList());

            for (ShiftRequirement req : reqs) {
                int required = req.getRequiredStaffCount();
                int assignedCount = (int) assigned.stream()
                        .filter(s -> s.getShiftType().getId().equals(req.getShiftType().getId()))
                        .count();

                if (assignedCount < required) {
                    Map<String, Object> dayInfo = new LinkedHashMap<>();
                    dayInfo.put("workDate", req.getWorkDate());
                    dayInfo.put("dayOfWeek", DateUtils.getDayOfWeekVietnamese(req.getWorkDate().getDayOfWeek()));
                    dayInfo.put("shiftTypeId", req.getShiftType().getId());
                    dayInfo.put("shiftTypeName", req.getShiftType().getName());
                    dayInfo.put("specialty", req.getSpecialty() != null ? req.getSpecialty().getName() : null);
                    dayInfo.put("requiredStaffCount", required);
                    dayInfo.put("assignedStaffCount", assignedCount);
                    dayInfo.put("missingCount", required - assignedCount);
                    dayInfo.put("reason", buildUnassignedReason(req, assignedCount));
                    dayInfo.put("reasonCode", buildUnassignedReasonCode(req, assignedCount));
                    dayInfo.put("severity", buildUnassignedSeverity(required, assignedCount));
                    unassignedDays.add(dayInfo);
                }
            }
        }

        // Sort: 1. missingCount DESC (most understaffed first), 2. workDate ASC (earliest first)
        unassignedDays.sort(Comparator
                .comparing((Map<String, Object> m) -> -((Number) m.get("missingCount")).intValue())
                .thenComparing((Map<String, Object> m) -> (LocalDate) m.get("workDate")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodId", periodId);
        result.put("periodName", period.getPeriodName());
        result.put("startDate", period.getStartDate());
        result.put("endDate", period.getEndDate());
        result.put("totalUnassignedDays", unassignedDays.size());
        result.put("unassignedDays", unassignedDays);

        return result;
    }

    // ==================== M07-F08: Đề xuất người thay thế ====================
    public Map<String, Object> suggestReplacements(Integer scheduleId) {
        return suggestReplacements(scheduleId, null);
    }

    /**
     * Suggest replacement staff for a given schedule, optionally excluding a list of
     * staff IDs (e.g. managers who want to re-run the suggestion with the previously
     * suggested-but-rejected staff filtered out).
     */
    public Map<String, Object> suggestReplacements(Integer scheduleId, Set<Integer> excludedStaffIds) {
        Schedule original = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch với ID: " + scheduleId));

        List<Staff> allStaff = staffRepository.findByIsActiveTrue();

        List<Map<String, Object>> suggestions = new ArrayList<>();

        for (Staff candidate : allStaff) {
            if (candidate.getId().equals(original.getStaff().getId())) continue;
            if (excludedStaffIds != null && excludedStaffIds.contains(candidate.getId())) continue;

            if (original.getStaff().getSpecialty() != null) {
                if (candidate.getSpecialty() == null ||
                        !candidate.getSpecialty().getId().equals(original.getStaff().getSpecialty().getId())) {
                    continue;
                }
            }

            // Skip the compensation-day check so staff on a day off can still be
            // surfaced as a replacement. This is consistent with the new
            // findReplacements(..., skipCompensationDay=true) flow.
            List<String> conflicts = conflictDetectionService.detectAllConflicts(
                    candidate.getId(), original.getWorkDate(), original.getShiftType().getId(),
                    scheduleId, true);

            if (conflicts.isEmpty()) {
                long currentWorkload = scheduleRepository.countByStaffIdAndPeriodId(
                        candidate.getId(), original.getPeriod().getId());

                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("staffId", candidate.getId());
                suggestion.put("staffName", candidate.getFullName());
                suggestion.put("specialty", candidate.getSpecialty() != null ? candidate.getSpecialty().getName() : null);
                suggestion.put("currentWorkload", currentWorkload);
                suggestion.put("conflicts", conflicts);
                suggestion.put("isAvailable", true);
                suggestion.put("reason", "Không có xung đột");
                suggestions.add(suggestion);
            } else {
                Map<String, Object> suggestion = new LinkedHashMap<>();
                suggestion.put("staffId", candidate.getId());
                suggestion.put("staffName", candidate.getFullName());
                suggestion.put("specialty", candidate.getSpecialty() != null ? candidate.getSpecialty().getName() : null);
                suggestion.put("currentWorkload", scheduleRepository.countByStaffIdAndPeriodId(
                        candidate.getId(), original.getPeriod().getId()));
                suggestion.put("conflicts", conflicts);
                suggestion.put("isAvailable", false);
                suggestion.put("reason", String.join(", ", conflicts));
                suggestions.add(suggestion);
            }
        }

        suggestions.sort(Comparator.comparing(m -> !(Boolean) m.get("isAvailable")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalScheduleId", scheduleId);
        result.put("originalStaffId", original.getStaff().getId());
        result.put("originalStaffName", original.getStaff().getFullName());
        result.put("workDate", original.getWorkDate());
        result.put("shiftTypeId", original.getShiftType().getId());
        result.put("shiftTypeName", original.getShiftType().getName());
        result.put("totalCandidates", suggestions.size());
        result.put("availableCount", (int) suggestions.stream().filter(m -> (Boolean) m.get("isAvailable")).count());
        result.put("suggestions", suggestions);

        return result;
    }

    // ==================== M07-F09: Data biểu đồ cân bằng tải ====================
    public Map<String, Object> getWorkloadChartData(Integer periodId) {
        return getWorkloadChartData(periodId, null);
    }

    public Map<String, Object> getWorkloadChartData(Integer periodId, String shiftTypeId) {
        SchedulePeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + periodId));

        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        // Filter schedules by shift type if specified (M04-F05 / M05-F05)
        if (shiftTypeId != null && !shiftTypeId.isBlank()) {
            schedules = schedules.stream()
                    .filter(s -> shiftTypeId.equals(s.getShiftType().getId()))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> staffWorkloadData = new ArrayList<>();

        for (Staff staff : activeStaff) {
            List<Schedule> staffSchedules = schedules.stream()
                    .filter(s -> s.getStaff().getId().equals(staff.getId()))
                    .collect(Collectors.toList());

            long L01Count = staffSchedules.stream().filter(s -> ConflictDetectionService.SHIFT_TYPE_L01.equals(s.getShiftType().getId())).count();
            long L02Count = staffSchedules.stream().filter(s -> ConflictDetectionService.SHIFT_TYPE_L02.equals(s.getShiftType().getId())).count();
            long L03Count = staffSchedules.stream().filter(s -> ConflictDetectionService.SHIFT_TYPE_L03.equals(s.getShiftType().getId())).count();
            long L04Count = staffSchedules.stream().filter(s -> ConflictDetectionService.SHIFT_TYPE_L04.equals(s.getShiftType().getId())).count();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("staffId", staff.getId());
            data.put("staffName", staff.getFullName());
            data.put("specialty", staff.getSpecialty() != null ? staff.getSpecialty().getName() : null);
            data.put("totalShifts", staffSchedules.size());
            data.put(ConflictDetectionService.SHIFT_TYPE_L01, L01Count);
            data.put(ConflictDetectionService.SHIFT_TYPE_L02, L02Count);
            data.put(ConflictDetectionService.SHIFT_TYPE_L03, L03Count);
            data.put(ConflictDetectionService.SHIFT_TYPE_L04, L04Count);
            double workloadPct;
            Integer maxShifts = staff.getMaxShiftsPerMonth();
            if (maxShifts != null && maxShifts > 0) {
                // Utilization = staff shifts / max shifts per month * 100
                workloadPct = Math.round((double) staffSchedules.size() / maxShifts * 10000.0) / 100.0;
            } else if (!schedules.isEmpty()) {
                // Fallback: share of total schedules
                workloadPct = Math.round((double) staffSchedules.size() / schedules.size() * 10000.0) / 100.0;
            } else {
                workloadPct = 0.0;
            }
            data.put("workloadPercentage", workloadPct);

            staffWorkloadData.add(data);
        }

        // Only include staff with at least one shift of the target type
        if (shiftTypeId != null && !shiftTypeId.isBlank()) {
            staffWorkloadData = staffWorkloadData.stream()
                    .filter(m -> ((Number) m.get("totalShifts")).longValue() > 0)
                    .collect(Collectors.toList());
        }

        staffWorkloadData.sort((a, b) -> {
            int t1 = ((Number) a.get("totalShifts")).intValue();
            int t2 = ((Number) b.get("totalShifts")).intValue();
            return Integer.compare(t2, t1);
        });

        // Calculate avg utilization percentage across all staff with maxShiftsPerMonth
        double avgWorkload = 0.0;
        if (!activeStaff.isEmpty()) {
            double totalUtil = staffWorkloadData.stream()
                    .mapToDouble(m -> ((Number) m.get("workloadPercentage")).doubleValue())
                    .sum();
            avgWorkload = Math.round(totalUtil / activeStaff.size() * 100.0) / 100.0;
        }

        // maxWorkload and minWorkload in percentage terms (utilization)
        long maxWorkload = (long) Math.round(staffWorkloadData.stream()
                .mapToDouble(m -> ((Number) m.get("workloadPercentage")).doubleValue())
                .max().orElse(0.0));
        long minWorkload = (long) Math.round(staffWorkloadData.stream()
                .mapToDouble(m -> ((Number) m.get("workloadPercentage")).doubleValue())
                .min().orElse(0.0));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("periodId", periodId);
        result.put("periodName", period.getPeriodName());
        result.put("startDate", period.getStartDate());
        result.put("endDate", period.getEndDate());
        result.put("totalSchedules", schedules.size());
        result.put("totalStaff", shiftTypeId != null && !shiftTypeId.isBlank()
                ? staffWorkloadData.size() : activeStaff.size());
        result.put("shiftTypeId", shiftTypeId);
        result.put("averageWorkload", avgWorkload);
        result.put("minWorkload", minWorkload);
        result.put("maxWorkload", maxWorkload);
        result.put("staffWorkloadData", staffWorkloadData);

        return result;
    }

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
     */
    private int guaranteeMinimumShifts(List<Schedule> schedules,
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

                    if (!StaffShiftTypeEligibility.isEligible(staff, typeId, specId, getNonL04AllowedSpecialties(typeId))) {
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

                    // Check L01 adjacent constraint (no back-to-back L01)
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeId)) {
                        LocalDate prevDate = date.minusDays(1);
                        LocalDate nextDate = date.plusDays(1);
                        Set<Integer> prevAssigned = assignedByDate.getOrDefault(prevDate, Set.of());
                        Set<Integer> nextAssigned = assignedByDate.getOrDefault(nextDate, Set.of());
                        if (prevAssigned.contains(staff.getId()) || nextAssigned.contains(staff.getId())) {
                            // Check if those were L01
                            boolean hasAdjL01 = false;
                            for (Schedule s : schedules) {
                                if (s.getStaff().getId().equals(staff.getId())) {
                                    if ((s.getWorkDate().equals(prevDate) || s.getWorkDate().equals(nextDate))
                                            && ConflictDetectionService.SHIFT_TYPE_L01.equals(s.getShiftType().getId())) {
                                        hasAdjL01 = true;
                                        break;
                                    }
                                }
                            }
                            if (hasAdjL01) continue;
                        }
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

    private Schedule buildNewSchedule(Staff staff, ShiftRequirement req, LocalDate workDate) {
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

    private Map<String, Map<Integer, Long>> buildSafeRebalanceCounts(List<Schedule> schedules, List<Staff> activeStaff) {
        Map<String, Map<Integer, Long>> counts = new LinkedHashMap<>();
        for (Schedule schedule : schedules) {
            String typeId = schedule.getShiftType().getId();
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeId)) {
                continue;
            }
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

    private RebalanceMove findBestSafeRebalanceMove(List<Schedule> schedules,
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
        if (allCompensationShiftDates.get().contains(compKey) || inMemoryCompensationShiftDates.get().contains(compKey)) {
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

    private boolean isBusinessShiftConflict(String typeA, String typeB) {
        return (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L02.equals(typeB))
                || (ConflictDetectionService.SHIFT_TYPE_L02.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L01.equals(typeB))
                || (ConflictDetectionService.SHIFT_TYPE_L03.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L04.equals(typeB))
                || (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeA) && ConflictDetectionService.SHIFT_TYPE_L03.equals(typeB));
    }

    private record RebalanceMove(Schedule schedule, Staff toStaff) {}

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

    private String buildUnassignedReason(ShiftRequirement req, long assigned) {
        if (assigned == 0) {
            if (ConflictDetectionService.SHIFT_TYPE_L04.equals(req.getShiftType().getId()) && req.getSpecialty() != null) {
                return "Không còn nhân sự hợp lệ cho chuyên khoa " + req.getSpecialty().getName()
                        + " sau khi áp dụng nghỉ phép, nghỉ bù và xung đột.";
            }
            return "Không còn nhân sự hợp lệ sau khi áp dụng nghỉ phép, nghỉ bù và xung đột ca.";
        }
        return "Mục tiêu phân bổ từ cấu hình cao hơn số nhân sự hợp lệ còn lại; phần thiếu cần quản lý xử lý thủ công.";
    }

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

    private String buildUnassignedSeverity(int required, int assigned) {
        if (assigned <= 0) return "critical";
        double missingRatio = (double) (required - assigned) / Math.max(1, required);
        return missingRatio >= 0.5 ? "warning" : "info";
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

    /**
     * Get L04 cross-specialty config from algorithmConfigService.
     * Returns a simple record with enabled, ratio, and allowedSpecialties.
     */
    private static record CrossSpecialtyConfig(boolean enabled, float ratio, List<String> allowedSpecialties) {}

    private CrossSpecialtyConfig getL04CrossSpecialtyConfig() {
        return algorithmConfigService.getAutoGenConfig()
                .map(cfg -> new CrossSpecialtyConfig(cfg.l04CrossSpecialty(), cfg.l04CrossSpecialtyRatio(), cfg.l04AllowedSpecialties()))
                .orElse(new CrossSpecialtyConfig(false, 0.3f, List.of())); // Default: all specialties
    }

    /**
     * Trả về danh sách specialties được phép gán cho L01/L02/L03.
     * Đọc từ algorithm_config; null/empty → StaffShiftTypeEligibility sẽ fallback về CORE (Ngoại, Nội).
     */
    private java.util.List<String> getNonL04AllowedSpecialties(String shiftTypeId) {
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

    private boolean hasInMemoryConflict(Integer staffId, LocalDate workDate, String shiftTypeId) {
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
            if (allCompensationShiftDates.get().contains(compKey)) {
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
        inMemoryAssignments.get().computeIfAbsent(key, k -> new HashSet<>()).add(shiftTypeId);
        // Also track compensation day if this is L01, so later L02/L03/L04 can't be assigned on that day
        // AND so we know not to assign L01 on this staff's compensation day
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
            LocalDate compDate = compensationDateCalculator.calculate(workDate);
            if (compDate != null) {
                String compKey = staff.getId() + "_" + compDate;
                inMemoryCompensationShiftDates.get().add(compKey);
                allCompensationShiftDates.get().add(staff.getId() + "_" + compDate.toString());
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

    private void createCompensationDayForAuto(Schedule schedule) {
        if (schedule == null || schedule.getWorkDate() == null) {
            log.warn("createCompensationDayForAuto: schedule or workDate is null");
            return;
        }
        LocalDate shiftDate = schedule.getWorkDate();
        LocalDate compensationDate = compensationDateCalculator.calculate(shiftDate);
        
        log.info("createCompensationDayForAuto: staffId={}, shiftDate={}, compDate={}", 
                schedule.getStaff().getId(), shiftDate, compensationDate);

        String compKey = schedule.getStaff().getId() + "_" + compensationDate.toString();
        
        // Check in-memory cache first (for current run)
        if (allCompensationShiftDates.get().contains(compKey)) {
            log.debug("Compensation day already tracked in memory for {}", compKey);
            return;
        }
        
        // CRITICAL FIX: Also check database for existing compensation day
        // This prevents duplicate entries when re-running the algorithm
        if (compensationDayRepository.existsByStaffIdAndCompensationDate(
                schedule.getStaff().getId(), compensationDate)) {
            log.warn("Compensation day already exists in DB for staff {} on {}", 
                    schedule.getStaff().getId(), compensationDate);
            // Add to in-memory cache to prevent duplicate checks
            allCompensationShiftDates.get().add(compKey);
            return;
        }
        
        // Also check if this schedule already has a compensation day (by schedule_id)
        if (schedule.getId() != null && compensationDayRepository.existsByScheduleId(schedule.getId())) {
            log.warn("Schedule {} already has a compensation day", schedule.getId());
            allCompensationShiftDates.get().add(compKey);
            return;
        }

        // Use INSERT IGNORE to avoid duplicate key errors
        // This is the proper fix from commit 5d080c1 - prevents Hibernate assertion failures
        try {
            int inserted = compensationDayRepository.insertIgnoreCompensationDay(
                    schedule.getStaff().getId(),
                    schedule.getPeriod().getId(),
                    schedule.getId(),
                    shiftDate,
                    compensationDate,
                    "Ngày nghỉ bù tự động từ ca L01"
            );
            if (inserted > 0) {
                log.info("Compensation day INSERTED via INSERT IGNORE: staffId={}, compDate={}",
                        schedule.getStaff().getId(), compensationDate);
                allCompensationShiftDates.get().add(compKey);
            } else {
                log.debug("Compensation day already existed (INSERT IGNORE): staffId={}, compDate={}",
                        schedule.getStaff().getId(), compensationDate);
                allCompensationShiftDates.get().add(compKey);
            }
        } catch (Exception e) {
            log.warn("Failed to insert compensation day for staff {} on {}: {}",
                    schedule.getStaff().getId(), compensationDate, e.getMessage());
            // Still add to cache to prevent further attempts
            allCompensationShiftDates.get().add(compKey);
        }
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

        Map<Integer, Long> staffScheduleCount = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

        if (staffScheduleCount.size() <= 1) {
            log.debug("Balance score 0: only {} staff assigned", staffScheduleCount.size());
            return BigDecimal.valueOf(0);
        }

        // CRITICAL FIX: filter active staff by L01/L02/L03 eligibility (Bác sĩ + Điều dưỡng) so that
        // KTV/Dược/Răng (only eligible for L04) are NOT included in the denominator for L01/L02/L03.
        // Otherwise CV is artificially inflated because those staff always have 0 L01/L02/L03.
        Set<Integer> lxxEligibleStaffIds = staffScheduleCount.keySet().stream()
                .filter(id -> {
                    Schedule s0 = schedules.stream().filter(s -> s.getStaff().getId().equals(id)).findFirst().orElse(null);
                    if (s0 == null) return false;
                    return com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility
                            .isEligible(s0.getStaff(), ConflictDetectionService.SHIFT_TYPE_L01, null);
                })
                .collect(Collectors.toSet());

        // Per-type CV: tính riêng từng loại L01/L02/L03/L04 theo spec M07-F02 (phân bổ đều từng loại).
        // CRITICAL FIX for L04: M05 spec requires L04 to be specialty-bound, so CV must be
        // computed per-specialty, not globally. All eligible staff from each specialty are
        // included in that specialty's CV (staff with 0 L04 get counted as 0 — that's fair).
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

                    // We need the pool size for this specialty — infer from schedules.
                    // Staff who have at least 1 L04 assignment in this specialty are in the pool.
                    // But we need ALL staff in the specialty (including those with 0 L04).
                    // Approximation: totalStaffPerSpecialty from activeStaff is not available here.
                    // Use the max assignment count across all staff in this specialty as proxy.
                    // Actually, the most accurate approach is to use the specialty staff pool
                    // from the requirements data. We compute the per-specialty CV using only
                    // the staff who appear in the schedules for this specialty.
                    //
                    // The pool size = number of unique staff who have ANY schedule in this specialty.
                    // This is the "eligible pool" for L04 in this specialty.
                    // Staff not in this specialty's schedules have 0 L04 but should not be penalized
                    // because they are not eligible for this specialty's L04.
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
                    // All staff in the pool had 0 initially — add zero-variance contribution for them
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

            // L01/L02/L03: use eligibility-filtered pool size so KTV/Dược (only L04 eligible) don't
            // inflate the variance with their guaranteed-zero count for these types.
            int effectiveTotalStaff = Math.max(lxxEligibleStaffIds.size(), 1);
            Map<Integer, Long> perTypeCount = schedules.stream()
                    .filter(s -> typeId.equals(s.getShiftType().getId()))
                    .filter(s -> lxxEligibleStaffIds.contains(s.getStaff().getId()))
                    .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

            if (perTypeCount.isEmpty()) continue;
            typesWithDemand++;

            // Pad với 0 cho staff chưa được phân công loại này
            int staffWithType = perTypeCount.size();
            long totalType = perTypeCount.values().stream().mapToLong(Long::longValue).sum();
            double avgType = (double) totalType / effectiveTotalStaff;

            if (avgType <= 0) continue;

            // Tính variance có tính cả staff eligible = 0 (quan trọng: phát hiện dồn ca).
            // Variance đếm từ eligible staff count = 0, KHÔNG phải toàn bộ totalStaff.
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

        // Cảnh báo nếu phân bổ lệch lớn — spec M02-F05, M07-F09
        if (avgCv > 30) {
            log.warn("Balance WARNING: avg per-type CV={}% > 30% — phân bổ lệch lớn giữa các nhân sự",
                    String.format("%.2f", avgCv));
        }

        log.info("Balance score: avgPerTypeCv={}% typesWithDemand={} totalStaff={} score={}",
                String.format("%.2f", avgCv), typesWithDemand, totalStaff, String.format("%.2f", score));

        return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    private void saveMetrics(SchedulePeriod period, String algorithmType, int executionTime,
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

    public List<AlgorithmMetricsDTO> getMetricsByPeriod(Integer periodId) {
        return metricsRepository.findByPeriodId(periodId).stream()
                .map(this::metricsToDTO)
                .toList();
    }

    public List<AlgorithmMetricsDTO> getAllMetrics() {
        return metricsRepository.findAll().stream()
                .map(this::metricsToDTO)
                .toList();
    }

    /**
     * Server-paginated variant of getAllMetrics / getMetricsByPeriod,
     * used by the auto-scheduling history page's &lt;Pagination&gt; widget.
     */
    public Page<AlgorithmMetricsDTO> getMetricsPage(Integer periodId, Pageable pageable) {
        Page<AlgorithmMetrics> page = (periodId == null)
                ? metricsRepository.findAll(pageable)
                : metricsRepository.findByPeriodId(periodId, pageable);
        return page.map(this::metricsToDTO);
    }

    private List<Map<String, Object>> buildUnassignedDays(List<ShiftRequirement> requirements, List<Schedule> schedules) {
        Map<String, Long> assignedCount = schedules.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getWorkDate() + "_" + s.getShiftType().getId(),
                        Collectors.counting()));

        List<Map<String, Object>> unassigned = new ArrayList<>();
        for (ShiftRequirement req : requirements) {
            String key = req.getWorkDate() + "_" + req.getShiftType().getId();
            long assigned = assignedCount.getOrDefault(key, 0L);
            if (assigned < req.getRequiredStaffCount()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("workDate", req.getWorkDate());
                item.put("dayOfWeek", DateUtils.getDayOfWeekVietnamese(req.getWorkDate().getDayOfWeek()));
                item.put("shiftTypeId", req.getShiftType().getId());
                item.put("shiftTypeName", req.getShiftType().getName());
                item.put("specialty", req.getSpecialty() != null ? req.getSpecialty().getName() : null);
                item.put("requiredStaffCount", req.getRequiredStaffCount());
                item.put("assignedStaffCount", (int) assigned);
                item.put("missingCount", req.getRequiredStaffCount() - (int) assigned);
                item.put("reason", buildUnassignedReason(req, assigned));
                item.put("reasonCode", buildUnassignedReasonCode(req, assigned));
                item.put("severity", buildUnassignedSeverity(req.getRequiredStaffCount(), (int) assigned));
                unassigned.add(item);
            }
        }
        return unassigned;
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

    private AlgorithmMetricsDTO metricsToDTO(AlgorithmMetrics m) {
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

    private List<ShiftRequirement> sortRequirementsByPriority(List<ShiftRequirement> requirements) {
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

    private Map<LocalDate, List<ShiftRequirement>> groupRequirementsByDate(List<ShiftRequirement> requirements) {
        return requirements.stream().collect(Collectors.groupingBy(ShiftRequirement::getWorkDate));
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
    private Map<String, Integer> computeFairSharePerType(List<ShiftRequirement> requirements, int staffPool) {
        return computeFairSharePerTypeWithStaff(requirements, staffPool, null);
    }

    private Map<String, Integer> computeFairSharePerTypeWithStaff(
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
                // L04 với cross-specialty: dùng toàn bộ staff pool để tăng coverage
                // Khi cross-specialty bật, staff từ specialty khác có thể được gán, nên pool phải rộng hơn
                var crossConfig = getL04CrossSpecialtyConfig();
                boolean crossEnabled = crossConfig.enabled();

                Set<Integer> l04SpecialtyIds = requirements.stream()
                        .filter(r -> typeId.equals(r.getShiftType().getId()) && r.getSpecialty() != null)
                        .map(r -> r.getSpecialty().getId())
                        .collect(Collectors.toSet());

                if (!l04SpecialtyIds.isEmpty()) {
                    // Count eligible L04 staff (only Bác sĩ, Điều dưỡng)
                    int totalEligibleL04Staff = (int) safeActiveStaff.stream()
                            .filter(s -> s.getSpecialty() != null
                                    && StaffShiftTypeEligibility.ELIGIBLE_SPECIALTY_NAMES.contains(s.getSpecialty().getName()))
                            .count();

                    if (crossEnabled) {
                        // Cross-specialty BẬT: dùng toàn bộ eligible staff (Bác sĩ/Điều dưỡng) làm pool
                        // Staff từ specialty khác có thể được gán, nên pool rộng hơn
                        effectivePool = Math.max(1, totalEligibleL04Staff);
                        log.info("L04 cross-specialty ENABLED: using eligible staff pool (size={}, total={})",
                                totalEligibleL04Staff, safeActiveStaff.size());
                    } else {
                        // Cross-specialty TẮT: chỉ dùng staff cùng specialty
                        long eligibleL04Count = safeActiveStaff.stream()
                                .filter(s -> s.getSpecialty() != null && l04SpecialtyIds.contains(s.getSpecialty().getId()))
                                .count();
                        effectivePool = Math.max(1, (int) eligibleL04Count);
                    }

                    // Per-specialty fairShare: tính theo specialty pool để đảm bảo công bằng
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
                            // Cross-specialty tắt: dùng specialty pool
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

            // ceil(demand / pool) — không cộng buffer cứng để tránh lệch
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

    private Schedule buildAndSaveSchedule(SchedulePeriod period, Staff staff, ShiftRequirement req,
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
    private PeriodConflictData loadPeriodConflictData(SchedulePeriod period, List<ShiftRequirement> requirements, List<Staff> activeStaff) {
        LocalDate periodStart = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();

        // 1. Load all approved leaves in the period (single query)
        Set<Integer> allOnLeave = new HashSet<>();
        for (LeaveRequest lr : leaveRequestRepository.findApprovedInRange(periodStart, periodEnd)) {
            allOnLeave.add(lr.getStaff().getId());
        }

        // 2. Load all compensation days in the period (single query)
        // CRITICAL: Also add to in-memory cache to prevent duplicate compensation day creation
        Set<Integer> allOnCompDay = new HashSet<>();
        for (CompensationDay cd : compensationDayRepository.findInRange(periodStart, periodEnd)) {
            allOnCompDay.add(cd.getStaff().getId());
            String compKey = cd.getStaff().getId() + "_" + cd.getCompensationDate().toString();
            allCompensationShiftDates.get().add(compKey);
        }

        // 3. Load all schedules for the period with details (single query)
        Map<Integer, List<Schedule>> allSchedulesByStaff = new HashMap<>();
        for (Schedule s : scheduleRepository.findByPeriodId(period.getId())) {
            allSchedulesByStaff.computeIfAbsent(s.getStaff().getId(), k -> new ArrayList<>()).add(s);
        }

        // 4. Pre-compute for each date: who is on leave, who is on comp day, who has schedules today
        // Collect all unique dates from requirements + existing schedules
        Set<LocalDate> allDates = new HashSet<>();
        for (ShiftRequirement req : requirements) {
            allDates.add(req.getWorkDate());
        }
        for (List<Schedule> staffSchedules : allSchedulesByStaff.values()) {
            for (Schedule s : staffSchedules) {
                allDates.add(s.getWorkDate());
            }
        }

        // 5. Build date range for adjacent L01 check (+2 so compensation days on day N+2 are blocked)
        LocalDate adjStart = periodStart.minusDays(1);
        LocalDate adjEnd = periodEnd.plusDays(2);

        // 6. Pre-load all L01 schedules in adjacent range (single query for prev/next day)
        Set<Integer> allL01StaffIds = new HashSet<>();
        for (Schedule s : scheduleRepository.findL01SchedulesInRange(adjStart, adjEnd)) {
            allL01StaffIds.add(s.getStaff().getId());
        }

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
        // FIX: Expand range by ±1 day to catch compensation days that fall on the boundary day
        // before or after the current period. Example: L01 on Friday (prev period) generates
        // compensation on Tuesday (start of new period) — Tuesday must be blocked.
        for (CompensationDay cd : compensationDayRepository.findInRange(periodStart.minusDays(1), periodEnd.plusDays(1))) {
            compDaysByDate.computeIfAbsent(cd.getCompensationDate(), k -> new HashSet<>()).add(cd.getStaff().getId());
            // Also add to in-memory cache (HashSet handles duplicates)
            String compKey = cd.getStaff().getId() + "_" + cd.getCompensationDate().toString();
            allCompensationShiftDates.get().add(compKey);
        }

        // Pre-load all L01 schedules for adjacent range (already done above, reuse)
        // Build prev/next L01 lookup per date
        Map<LocalDate, Set<Integer>> adjacentL01ByDate = new HashMap<>();
        for (Schedule s : scheduleRepository.findL01SchedulesInRange(adjStart, adjEnd)) {
            LocalDate adj = s.getWorkDate();
            adjacentL01ByDate.computeIfAbsent(adj.minusDays(1), k -> new HashSet<>()).add(s.getStaff().getId());
            adjacentL01ByDate.computeIfAbsent(adj.plusDays(1), k -> new HashSet<>()).add(s.getStaff().getId());
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
                // Check if staff is at least in ELIGIBLE_SPECIALTY_NAMES (Bác sĩ, Điều dưỡng)
                if (staff.getSpecialty() != null && StaffShiftTypeEligibility.ELIGIBLE_SPECIALTY_NAMES
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
    private long getStaffCountForKey(Integer staffId, String countKey,
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

    private long getTotalStaffCount(Integer staffId,
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

    private boolean isStrictMatchForStaff(Staff staff, ShiftRequirement req) {
        return req.getSpecialty() != null
                && staff.getSpecialty() != null
                && staff.getSpecialty().getId().equals(req.getSpecialty().getId());
    }

    // ==================== REQUIREMENTS GENERATION FROM CONFIG ====================

    /**
     * Re-sync persisted requirements for a period with the current auto-gen config.
     * Existing ShiftRequirement rows keep their id (FK safety with schedule rows) but get their
     * requiredStaffCount updated to match the latest min/max per day from the config.
     * Without this, previous runs' stale requiredCount values would persist and ignore config changes.
     */
    private void syncExistingRequirementsWithConfig(SchedulePeriod period, AutoGenConfig config, List<Staff> activeStaff) {
        List<ShiftRequirement> existing = requirementRepository.findByPeriodId(period.getId());
        if (existing == null || existing.isEmpty()) return;

        Set<LocalDate> holidays = holidayRepository.findActiveHolidaysBetween(period.getStartDate(), period.getEndDate())
                .stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        int generalPoolSize = Math.max(1, activeStaff.size());
        boolean skipL03OnHoliday = !"PARTIAL".equalsIgnoreCase(config.holidayMode());

        boolean anyChanged = false;
        for (ShiftRequirement req : existing) {
            if (req.getWorkDate() == null || req.getShiftType() == null) continue;
            boolean isHoliday = holidays.contains(req.getWorkDate());
            int newTarget;
            String typeId = req.getShiftType().getId();
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(typeId)) {
                newTarget = resolveSoftDailyTarget(config.l01MinPerDay(), config.l01MaxPerDay(), generalPoolSize);
            } else if (ConflictDetectionService.SHIFT_TYPE_L02.equals(typeId)) {
                newTarget = resolveSoftDailyTarget(config.l02MinPerDay(), config.l02MaxPerDay(), generalPoolSize);
            } else if (ConflictDetectionService.SHIFT_TYPE_L03.equals(typeId)) {
                int min = (isHoliday && skipL03OnHoliday) ? 0 : config.l03MinPerDay();
                newTarget = resolveSoftDailyTarget(min, config.l03MaxPerDay(), generalPoolSize);
            } else if (ConflictDetectionService.SHIFT_TYPE_L04.equals(typeId)) {
                int specialtyPoolSize = config.l04CrossSpecialty()
                        ? generalPoolSize
                        : countActiveStaffBySpecialty(activeStaff, req.getSpecialty() != null ? req.getSpecialty().getId() : null);
                newTarget = resolveSoftDailyTarget(config.l04MinPerDay(), config.l04MaxPerDay(), specialtyPoolSize);
            } else {
                continue;
            }
            if (req.getRequiredStaffCount() != newTarget) {
                req.setRequiredStaffCount(newTarget);
                anyChanged = true;
            }
        }
        if (anyChanged) {
            requirementRepository.saveAll(existing);
            entityManager.flush();
            log.info("Synced {} requirements with current config for period {}", existing.size(), period.getId());
        }
    }

    private List<ShiftRequirement> generateRequirementsFromConfig(SchedulePeriod period, AutoGenConfig config, List<Staff> activeStaff) {
        List<ShiftRequirement> generated = new ArrayList<>();
        Set<String> removedShiftTypes = config.removedShiftTypes() == null
                ? Set.of()
                : config.removedShiftTypes().stream().map(String::toUpperCase).collect(Collectors.toSet());

        Set<LocalDate> holidays = holidayRepository.findActiveHolidaysBetween(period.getStartDate(), period.getEndDate())
                .stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        Map<String, ShiftType> shiftTypeMap = shiftTypeRepository.findAll().stream()
                .collect(Collectors.toMap(ShiftType::getId, s -> s));

        ShiftType l01 = shiftTypeMap.get("L01");
        ShiftType l02 = shiftTypeMap.get("L02");
        ShiftType l03 = shiftTypeMap.get("L03");
        ShiftType l04 = shiftTypeMap.get("L04");

        if (l01 == null || l02 == null || l03 == null || l04 == null) {
            throw new BadRequestException("Không tìm thấy shift types L01-L04 trong hệ thống");
        }

        int generalPoolSize = Math.max(1, activeStaff.size());
        List<Specialty> activeSpecialties = specialtyRepository.findByIsActiveTrue();
        LocalDate current = period.getStartDate();
        while (!current.isAfter(period.getEndDate())) {
            LocalDate date = current;
            boolean isHoliday = holidays.contains(date);
            boolean shouldGenerateFullDay = !isHoliday || "PARTIAL".equalsIgnoreCase(config.holidayMode());

            if (shouldGenerateFullDay && !removedShiftTypes.contains("L01")) {
                generated.add(buildAutoRequirement(period, l01, date, null,
                        resolveSoftDailyTarget(config.l01MinPerDay(), config.l01MaxPerDay(), generalPoolSize),
                        "AUTO_SOFT_TARGET:L01:" + date));
            }
            if (shouldGenerateFullDay && !removedShiftTypes.contains("L02")) {
                generated.add(buildAutoRequirement(period, l02, date, null,
                        resolveSoftDailyTarget(config.l02MinPerDay(), config.l02MaxPerDay(), generalPoolSize),
                        "AUTO_SOFT_TARGET:L02:" + date));
            }

            if (!removedShiftTypes.contains("L03")) {
                if ("PARTIAL".equalsIgnoreCase(config.holidayMode())) {
                    generated.add(buildAutoRequirement(period, l03, date, null,
                            resolveSoftDailyTarget(isHoliday ? 1 : config.l03MinPerDay(), config.l03MaxPerDay(), generalPoolSize),
                            "AUTO_SOFT_TARGET:L03:" + date));
                } else if (!isHoliday) {
                    generated.add(buildAutoRequirement(period, l03, date, null,
                            resolveSoftDailyTarget(config.l03MinPerDay(), config.l03MaxPerDay(), generalPoolSize),
                            "AUTO_SOFT_TARGET:L03:" + date));
                }
            }

            if (shouldGenerateFullDay && !removedShiftTypes.contains("L04")) {
                for (Specialty specialty : activeSpecialties) {
                    int specialtyPoolSize = config.l04CrossSpecialty()
                            ? generalPoolSize
                            : countActiveStaffBySpecialty(activeStaff, specialty.getId());
                    int target = resolveSoftDailyTarget(config.l04MinPerDay(), config.l04MaxPerDay(), specialtyPoolSize);
                    generated.add(buildAutoRequirement(period, l04, date, specialty, target,
                            "AUTO_SOFT_TARGET:L04:" + date + ":" + specialty.getName()));
                }
            }

            current = current.plusDays(1);
        }

        Map<String, ShiftRequirement> uniqueReqs = new LinkedHashMap<>();
        for (ShiftRequirement r : generated) {
            String key = period.getId() + "_" + r.getWorkDate() + "_" + r.getShiftType().getId()
                    + "_" + (r.getSpecialty() != null ? r.getSpecialty().getId() : "null");
            uniqueReqs.putIfAbsent(key, r);
        }
        List<ShiftRequirement> deduplicated = new ArrayList<>(uniqueReqs.values());
        log.info("Generated {} soft-target requirements from auto config for period {}", deduplicated.size(), period.getId());
        return deduplicated;
    }

    private ShiftRequirement buildAutoRequirement(
            SchedulePeriod period,
            ShiftType shiftType,
            LocalDate workDate,
            Specialty specialty,
            int targetStaffCount,
            String note) {
        return ShiftRequirement.builder()
                .period(period)
                .shiftType(shiftType)
                .workDate(workDate)
                .specialty(specialty)
                .requiredStaffCount(targetStaffCount)
                .note(note)
                .build();
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
        int target;
        if (preferredMax > 0) {
            target = Math.min(preferredMax, eligiblePoolSize);  // Start from max, cap at pool
            target = Math.max(target, preferredMin);            // Ensure at least min
        } else {
            target = Math.max(preferredMin, 1);                  // max=0 means unlimited, use min
        }
        return Math.min(target, Math.max(1, eligiblePoolSize));
    }

    private int countActiveStaffBySpecialty(List<Staff> activeStaff, Integer specialtyId) {
        long count = activeStaff.stream()
                .filter(s -> s.getSpecialty() != null && Objects.equals(s.getSpecialty().getId(), specialtyId))
                .count();
        return Math.max(1, (int) count);
    }

    private List<ShiftRequirement> persistRequirementsIfTransient(List<ShiftRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) return requirements;

        List<ShiftRequirement> toSave = requirements.stream()
                .filter(r -> r != null && r.getId() == null)
                .collect(Collectors.toList());
        if (toSave.isEmpty()) return requirements;

        Map<String, ShiftRequirement> existing = new HashMap<>();
        for (ShiftRequirement req : requirementRepository.findByPeriodId(toSave.get(0).getPeriod().getId())) {
            String key = req.getWorkDate() + "|" + req.getShiftType().getId() + "|"
                    + (req.getSpecialty() != null ? req.getSpecialty().getId() : "null");
            existing.putIfAbsent(key, req);
        }

        List<ShiftRequirement> merged = new ArrayList<>(requirements.size());
        for (ShiftRequirement req : requirements) {
            if (req == null) { merged.add(null); continue; }
            if (req.getId() != null) { merged.add(req); continue; }
            String key = req.getWorkDate() + "|" + req.getShiftType().getId() + "|"
                    + (req.getSpecialty() != null ? req.getSpecialty().getId() : "null");
            ShiftRequirement already = existing.get(key);
            merged.add(already != null ? already : req);
        }

        List<ShiftRequirement> toInsert = merged.stream()
                .filter(r -> r != null && r.getId() == null)
                .collect(Collectors.toList());
        if (!toInsert.isEmpty()) {
            Map<String, ShiftRequirement> dedup = new LinkedHashMap<>();
            for (ShiftRequirement r : toInsert) {
                String key = r.getWorkDate() + "|" + r.getShiftType().getId() + "|"
                        + (r.getSpecialty() != null ? r.getSpecialty().getId() : "null");
                dedup.putIfAbsent(key, r);
            }
            List<ShiftRequirement> saved = requirementRepository.saveAll(new ArrayList<>(dedup.values()));
            for (int i = 0; i < merged.size(); i++) {
                ShiftRequirement cur = merged.get(i);
                if (cur != null && cur.getId() == null) {
                    for (ShiftRequirement s : saved) {
                        if (s.getWorkDate().equals(cur.getWorkDate())
                                && s.getShiftType().getId().equals(cur.getShiftType().getId())
                                && Objects.equals(
                                        s.getSpecialty() != null ? s.getSpecialty().getId() : null,
                                        cur.getSpecialty() != null ? cur.getSpecialty().getId() : null)) {
                            merged.set(i, s);
                            break;
                        }
                    }
                }
            }
        }
        return merged;
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
        inMemoryAssignments.set(new HashMap<>());
        inMemoryCompensationShiftDates.set(new HashSet<>());
        allCompensationShiftDates.set(new HashSet<>());
        swapPriorityStaffIds.set(new HashSet<>());
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
                result = runCspWithResult(cspScheduler.reSolve(previous, changes, activeStaff, toRequirementInfos(requirements), leaveRequests), period);
                usedIncremental = true;
            } else {
                log.info("Reschedule period {} via full CSP solve (incremental not applicable: previous={}, canReSolve={})",
                        periodId, previous != null,
                        previous != null && cspScheduler.canReSolveIncrementally(changes));
                result = runCsp(period, requirements, activeStaff, false, excludedStaffIds);
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
                if (algorithmConfigService.getRuntimeConfig() != null
                        && algorithmConfigService.getRuntimeConfig().isAutoCompensationEnabled()) {
                    createCompensationDaysForL01InPeriod(periodId);
                }
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
            inMemoryAssignments.remove();
            inMemoryCompensationShiftDates.remove();
            allCompensationShiftDates.remove();
            swapPriorityStaffIds.remove();
        }
    }

    /** Re-hydrate a raw {@link SchedulingResult} from the incremental path into Schedule entities.
     * The incremental resolver returns assignments keyed by "staffId_workDate" — we map them back
     * to Schedule entities so the existing persistence pipeline can be reused unchanged. */
    private SchedulingResultWithFairness runCspWithResult(SchedulingResult result, SchedulePeriod period) {
        if (result == null || !result.isValid() || result.getAssignments() == null || result.getAssignments().isEmpty()) {
            return new SchedulingResultWithFairness(List.of(), BigDecimal.ZERO);
        }
        Map<Integer, Staff> staffById = new HashMap<>();
        for (Staff s : staffRepository.findByIsActiveTrue()) {
            staffById.put(s.getId(), s);
        }
        List<Schedule> rehydrated = new ArrayList<>();
        for (Map.Entry<String, String> e : result.getAssignments().entrySet()) {
            String[] parts = e.getKey().split("_", 2);
            if (parts.length != 2) continue;
            try {
                Integer staffId = Integer.parseInt(parts[0]);
                LocalDate workDate = LocalDate.parse(parts[1]);
                Staff staff = staffById.get(staffId);
                if (staff == null) continue;
                ShiftType shiftType = shiftTypeRepository.findById(e.getValue()).orElse(null);
                if (shiftType == null) continue;
                rehydrated.add(Schedule.builder()
                        .period(period)
                        .staff(staff)
                        .workDate(workDate)
                        .shiftType(shiftType)
                        .build());
            } catch (Exception parseErr) {
                log.warn("Skipping malformed assignment key during incremental rehydrate: {}", e.getKey());
            }
        }
        return new SchedulingResultWithFairness(rehydrated,
                result.getFairnessScore() != null ? result.getFairnessScore() : BigDecimal.ZERO);
    }

    static List<ShiftRequirementInfo> toRequirementInfos(List<ShiftRequirement> requirements) {
        return requirements.stream()
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
}
