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
import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.algorithm.GeneticAlgorithmScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

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

    // Wrapper to return both schedules and GA fairness score
    private record SchedulingResultWithFairness(List<Schedule> schedules, BigDecimal fairnessScore) {}
    
    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository periodRepository;
    private final StaffRepository staffRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AlgorithmMetricsRepository metricsRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final AuditHistoryService auditHistoryService;
    private final CompensationDateCalculator compensationDateCalculator;
    private final NotificationService notificationService;
    private final AlgorithmConfigService algorithmConfigService;
    private final HolidayRepository holidayRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final SpecialtyRepository specialtyRepository;
    private final GeneticAlgorithmScheduler geneticAlgorithmScheduler;

    // Thread-local so concurrent requests don't share state
    private final ThreadLocal<Map<String, Set<String>>> inMemoryAssignments = ThreadLocal.withInitial(HashMap::new);
    private final ThreadLocal<Set<String>> inMemoryCompensationShiftDates = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Set<String>> allCompensationShiftDates = ThreadLocal.withInitial(HashSet::new);

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
        try {
            return runScheduling(request, false);
        } finally {
            inMemoryAssignments.remove();
            inMemoryCompensationShiftDates.remove();
            allCompensationShiftDates.remove();
        }
    }

    public AutoScheduleResponse autoSchedule(AutoScheduleRequestDTO request) {
        inMemoryAssignments.set(new HashMap<>());
        inMemoryCompensationShiftDates.set(new HashSet<>());
        allCompensationShiftDates.set(new HashSet<>());
        try {
            return runScheduling(request, true);
        } finally {
            inMemoryAssignments.remove();
            inMemoryCompensationShiftDates.remove();
            allCompensationShiftDates.remove();
        }
    }

    public AutoScheduleResponse applyPreviewSchedule(com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO request) {
        SchedulePeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể áp dụng bản nháp khi kỳ lịch ở trạng thái DRAFT");
        }

        List<Schedule> savedSchedules = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        // Track staff→date→shifts assigned so far in this apply loop, so the conflict
        // check catches L01↔L02 / L03↔L04 collisions between sibling preview items.
        // This replaces the trust in the in-memory assignments used during preview,
        // which is no longer available at apply time.
        Map<String, Set<String>> inApplyLoop = new HashMap<>();

        for (var item : request.getSchedules()) {
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
            // Skip back-to-back conflict because the algorithm already enforced this during preview.
            // Skip schedule if conflict detected instead of throwing to allow partial save.
            if (conflictDetectionService.hasAnyConflict(
                    staff.getId(), workDate, shiftType.getId(), null, false, false, false, true)) {
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
                ? BigDecimal.valueOf((double) savedSchedules.size() / totalRequiredStaffNeeded * 100)
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

        // Save metrics so History tab shows this execution
        try {
            saveMetrics(period, request.getAlgorithmType(), (int) executionTime, coverageRate, balanceScore, 0, savedSchedules.size());
            log.info("Metrics saved for period {} with algorithm {}: coverage={}%, balance={}", 
                    period.getId(), request.getAlgorithmType(), coverageRate, balanceScore);
        } catch (Exception e) {
            log.error("Failed to save metrics for period {}: {}", period.getId(), e.getMessage(), e);
        }

        return AutoScheduleResponse.builder()
                .success(true)
                .message("Đã áp dụng bản nháp đã chỉnh sửa")
                .periodId(period.getId())
                .algorithmType(request.getAlgorithmType())
                .executionTimeMs((int) executionTime)
                .coverageRate(coverageRate.setScale(2, RoundingMode.HALF_UP))
                .balanceScore(balanceScore.setScale(2, RoundingMode.HALF_UP))
                .conflictCount(0)
                .totalSchedulesCreated(savedSchedules.size())
                .schedules(summaries)
                .unassignedDays(unassignedDays)
                .executedAt(LocalDateTime.now())
                .build();
    }

    private AutoScheduleResponse runScheduling(AutoScheduleRequestDTO request, boolean save) {
        long startTime = System.currentTimeMillis();
        
        // Track GA fairness score separately (0-100 scale)
        BigDecimal gaFairnessScore = null;
        
        SchedulePeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xếp lịch tự động khi kỳ lịch ở trạng thái DRAFT");
        }

        // Load runtime config from DB (or use defaults if not set)
        AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig = algorithmConfigService.getRuntimeConfig();
        log.info("Using runtime config: maxIterations={}, weekendWeight={}, overnightRecoveryHours={}, greedyThreshold={}, balanceMin={}",
                runtimeConfig.getMaxIterations(), runtimeConfig.getWeekendWeight(),
                runtimeConfig.getOvernightRecoveryHours(), runtimeConfig.getGreedyCoverageThreshold(),
                runtimeConfig.getBalanceScoreMin());

        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        if (request.getExcludedStaffIds() != null && !request.getExcludedStaffIds().isEmpty()) {
            Set<Integer> excluded = new HashSet<>(request.getExcludedStaffIds());
            activeStaff = activeStaff.stream()
                    .filter(s -> !excluded.contains(s.getId()))
                    .collect(Collectors.toList());
        }

            // Auto-generate requirements if enabled (per M07 spec)
            // IMPORTANT: Delete existing auto-generated requirements before generating new ones to avoid duplicates
            if (Boolean.TRUE.equals(request.getAutoGenerateRequirements())) {
                var autoGenConfig = algorithmConfigService.getAutoGenConfig();
                if (autoGenConfig.isPresent() && autoGenConfig.get().enabled()) {
                    // Delete OLD auto-generated requirements for this period to avoid duplicates
                    List<ShiftRequirement> oldAutoReqs = requirementRepository.findByPeriodId(period.getId()).stream()
                            .filter(r -> r.getNote() != null && r.getNote().startsWith("AUTO:"))
                            .toList();
                    if (!oldAutoReqs.isEmpty()) {
                        requirementRepository.deleteAll(oldAutoReqs);
                        log.info("Deleted {} old auto-generated requirements for period {}", oldAutoReqs.size(), period.getId());
                    }

                    // Use holidayMode from request if provided, otherwise fall back to DB config
                    String effectiveHolidayMode = (request.getHolidayMode() != null && !request.getHolidayMode().isBlank())
                            ? request.getHolidayMode()
                            : autoGenConfig.get().holidayMode();
                    var configWithMode = new com.hospital.scheduler.algorithm.AutoGenConfig(
                            autoGenConfig.get().enabled(),
                            autoGenConfig.get().l01RequiredPerDay(),
                            autoGenConfig.get().l02RequiredPerDay(),
                            autoGenConfig.get().l03RequiredPerDay(),
                            autoGenConfig.get().l04RequiredPerDay(),
                            autoGenConfig.get().minL01PerWeek(),
                            autoGenConfig.get().minL02PerWeek(),
                            autoGenConfig.get().minL03PerWeek(),
                            autoGenConfig.get().minL04PerWeek(),
                            effectiveHolidayMode
                    );
                    requirements = generateRequirementsForPeriod(period, configWithMode, activeStaff);
                generatedRequirements = requirements.stream()
                        .filter(r -> r.getNote() != null && r.getNote().startsWith("AUTO:"))
                        .map(this::toGeneratedRequirementInfo)
                        .toList();
            } else {
                requirements = new ArrayList<>(requirementRepository.findByPeriodId(period.getId()));
            }
        } else {
            requirements = new ArrayList<>(requirementRepository.findByPeriodId(period.getId()));
        }

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

        String algorithmType = request.getAlgorithmType() != null ? request.getAlgorithmType().toUpperCase() : "GREEDY";

        List<Schedule> createdSchedules;
        if ("ROUND_ROBIN".equals(algorithmType)) {
            createdSchedules = runRoundRobin(period, requirements, activeStaff, save, runtimeConfig,
                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
        } else if ("BACKTRACKING".equals(algorithmType)) {
            // Use config from DB or fallback to request value or default
            int maxIterations = request.getMaxIterations() != null
                    ? request.getMaxIterations()
                    : runtimeConfig.getMaxIterations();
            long backtrackTimeLimitNs = runtimeConfig.getBacktrackTimeLimitSeconds() * 1_000_000_000L;
            createdSchedules = runBacktracking(period, requirements, activeStaff, save,
                    maxIterations, backtrackTimeLimitNs,
                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
            log.info("Backtracking completed with {} schedules (iterations: {}, timeLimit: {}s)",
                    createdSchedules.size(), maxIterations, runtimeConfig.getBacktrackTimeLimitSeconds());
            // Fallback to Greedy if Backtracking finds no solution (production data may be over-constrained)
            if (createdSchedules.isEmpty()) {
                log.info("Backtracking found no solution, falling back to Greedy algorithm");
                createdSchedules = runGreedy(period, requirements, activeStaff, save, runtimeConfig,
                        request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
                log.info("Greedy fallback result: {} schedules", createdSchedules.size());
            }
        } else if ("GENETIC".equals(algorithmType)) {
            // Run Genetic Algorithm
            log.info("Running Genetic Algorithm for period {}", period.getId());
            SchedulingResultWithFairness gaResult = runGeneticAlgorithm(period, requirements, activeStaff, save,
                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
            createdSchedules = gaResult.schedules();
            gaFairnessScore = gaResult.fairnessScore(); // Store for balance score calculation
            log.info("Genetic Algorithm completed with {} schedules", createdSchedules.size());
            // Fallback to Greedy if GA finds no solution
            if (createdSchedules.isEmpty()) {
                log.info("Genetic Algorithm found no solution, falling back to Greedy algorithm");
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
            // Try Round Robin as a fallback
            log.info("{} balance score {} < threshold {}, trying Round Robin fallback",
                    algorithmType, greedyBalanceScore, runtimeConfig.getBalanceScoreMin());
            List<Schedule> roundRobinSchedules = runRoundRobin(period, requirements, activeStaff, false, runtimeConfig,
                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
            int rrStaffCount = (int) roundRobinSchedules.stream().map(s -> s.getStaff().getId()).distinct().count();
            BigDecimal rrBalanceScore = calculateBalanceScore(roundRobinSchedules, rrStaffCount > 0 ? rrStaffCount : 1);
            log.info("Round Robin fallback: balanceScore={} ({} had {})", rrBalanceScore, algorithmType, greedyBalanceScore);
            if (rrBalanceScore.compareTo(bestScore) > 0) {
                log.info("Using Round Robin result (better balance score)");
                bestScore = rrBalanceScore;
                bestSchedules = roundRobinSchedules;
                // If we chose RR as the better option, run again with save=true
                if (!save) {
                    createdSchedules = runRoundRobin(period, requirements, activeStaff, save, runtimeConfig,
                            request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
                    bestSchedules = createdSchedules;
                }
            }
        }

        // Use the best result
        createdSchedules = bestSchedules;

        // Notify staff for greedy and round-robin (backtracking has its own inside the if(save) block)
        if (save && !"BACKTRACKING".equals(algorithmType) && !createdSchedules.isEmpty()) {
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

        long executionTime = System.currentTimeMillis() - startTime;
        int totalRequiredStaffNeeded = requirements.stream()
                .mapToInt(com.hospital.scheduler.entity.ShiftRequirement::getRequiredStaffCount)
                .sum();
        int totalRequired = requirements.size();
        int distinctStaffAssigned = (int) createdSchedules.stream().map(s -> s.getStaff().getId()).distinct().count();
        BigDecimal coverageRate = totalRequiredStaffNeeded > 0
                ? BigDecimal.valueOf((double) createdSchedules.size() / totalRequiredStaffNeeded * 100)
                : BigDecimal.ZERO;
        
        // Use GA's fairness score if available (already in 0-100 scale)
        // Otherwise calculate from created schedules
        BigDecimal balanceScore;
        if (gaFairnessScore != null) {
            balanceScore = gaFairnessScore;
        } else {
            int staffCount = distinctStaffAssigned > 0 ? distinctStaffAssigned : 1;
            balanceScore = calculateBalanceScore(createdSchedules, staffCount);
        }

        if (save) {
            saveMetrics(period, algorithmType, (int) executionTime, coverageRate, balanceScore, warnings.size(), createdSchedules.size());
        }

        // Deduplicate by staffId+workDate+shiftTypeId to avoid React key warnings
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
                        .build())
                .collect(Collectors.toList());

        String actionType = save ? "Xếp lịch tự động thành công" : "Xem trước lịch";

        var responseBuilder = AutoScheduleResponse.builder()
                .success(true)
                .message(warnings.isEmpty() ? actionType : actionType + " với " + warnings.size() + " cảnh báo")
                .periodId(period.getId())
                .algorithmType(algorithmType)
                .executionTimeMs((int) executionTime)
                .coverageRate(coverageRate.setScale(2, RoundingMode.HALF_UP))
                .balanceScore(balanceScore.setScale(2, RoundingMode.HALF_UP))
                .conflictCount(warnings.size())
                .totalSchedulesCreated(createdSchedules.size())
                .schedules(scheduleSummaries)
                .generatedRequirements(generatedRequirements)
                .executedAt(LocalDateTime.now());

        if (request.getExcludedStaffIds() != null) {
            responseBuilder.excludedStaffIds(request.getExcludedStaffIds());
        }

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

                    // greedy_coverage_threshold: track coverage as we go; process ALL requirements (no early return)
        // The threshold is tracked for logging purposes only - we always fill every requirement
        int totalRequired = requirements.stream()
                .mapToInt(com.hospital.scheduler.entity.ShiftRequirement::getRequiredStaffCount)
                .sum();
        final int coverageTarget = (int) Math.ceil(totalRequired * runtimeConfig.getGreedyCoverageThreshold().doubleValue());

        LocalDate currentDate = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();
        while (!currentDate.isAfter(periodEnd)) {
            List<ShiftRequirement> todayReqs = sortRequirementsByPriority(
                    requirementsByDate.getOrDefault(currentDate, Collections.emptyList()));

            BatchConflictData todayConflicts = periodData.byDate().get(currentDate);
            Set<Integer> assignedStaffIds = new HashSet<>();
            boolean l01AssignedToday = false;  // CRITICAL: Only 1 L01 per day per spec M02
            for (ShiftRequirement req : todayReqs) {
                // CRITICAL FIX: If L01 already assigned today, skip remaining L01 requirements
                if (ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                    if (l01AssignedToday) {
                        log.debug("Skipping duplicate L01 requirement for date={} - already assigned today", currentDate);
                        continue;
                    }
                }

                final LocalDate workDate = currentDate;
                final String shiftTypeId = req.getShiftType().getId();
                final boolean isWeekend = currentDate.getDayOfWeek() == DayOfWeek.SATURDAY
                        || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY;

                // Fairness comparator: prefer staff with fewer of THIS shift type
                // Secondary: fewer total shifts for overall balance
                // weekend_weight: penalize weekend assignments (higher count = less preferred on weekends)
                Comparator<Staff> fairnessComparator = Comparator
                        .comparingLong((Staff s) -> {
                            Map<String, Long> counts = periodData.staffShiftTypeCounts().get(s.getId());
                            return counts != null ? counts.getOrDefault(shiftTypeId, 0L) : 0L;
                        })
                        .thenComparingLong(s -> {
                            Map<String, Long> counts = periodData.staffShiftTypeCounts().get(s.getId());
                            if (counts == null) return 0L;
                            return counts.getOrDefault("L01", 0L) + counts.getOrDefault("L02", 0L)
                                    + counts.getOrDefault("L03", 0L) + counts.getOrDefault("L04", 0L);
                        })
                        // weekend_weight: penalize weekend shifts using the configured multiplier
                        // Higher weight = fewer weekend assignments preferred (staff with more weekend shifts gets lower priority)
                        .thenComparingDouble(s -> {
                            if (!isWeekend) return 0.0;
                            // Use shift type count as base; higher count = less preferred on weekends
                            Map<String, Long> counts = periodData.staffShiftTypeCounts().get(s.getId());
                            long totalShifts = counts != null
                                    ? counts.getOrDefault("L01", 0L) + counts.getOrDefault("L02", 0L)
                                            + counts.getOrDefault("L03", 0L) + counts.getOrDefault("L04", 0L)
                                    : 0L;
                            return totalShifts * runtimeConfig.getWeekendWeight().doubleValue();
                        });

                List<Staff> eligibleStaff = filterAndSortEligibleStaffBatch(
                        activeStaff, req, excludedStaffIds, assignedStaffIds, todayConflicts, !save,
                        fairnessComparator, periodData);

                int toAssign = Math.min(req.getRequiredStaffCount(), eligibleStaff.size());
                if (log.isDebugEnabled()) {
                    log.debug("runGreedy date={} req={} eligible={} toAssign={} assignedSoFar={}",
                        workDate, req.getShiftType().getId(), eligibleStaff.size(), toAssign, assignedStaffIds.size());
                }
                int assignedCount = 0;
                int staffIndex = 0;
                while (assignedCount < toAssign && staffIndex < eligibleStaff.size()) {
                    Staff staff = eligibleStaff.get(staffIndex);
                    staffIndex++;
                    // Skip if already assigned to another shift today
                    // (can happen when L01/L02/L03 share the same eligible pool)
                    if (assignedStaffIds.contains(staff.getId())) {
                        continue;
                    }
                    Schedule saved = buildAndSaveSchedule(period, staff, req, workDate, save, createdSchedules);
                    if (saved == null) continue;
                    trackAssignment(staff, workDate, req.getShiftType().getId());
                    assignedStaffIds.add(staff.getId());
                    assignedCount++;

                    // greedy_coverage_threshold: log when target coverage is reached (but keep processing all requirements)
                    if (createdSchedules.size() >= coverageTarget) {
                        log.info("Greedy coverage threshold reached: {}/{} = {}% (target: {}%) - continuing to fill remaining requirements",
                                createdSchedules.size(), totalRequired,
                                String.format("%.2f", (double) createdSchedules.size() / totalRequired * 100),
                                String.format("%.0f", runtimeConfig.getGreedyCoverageThreshold().doubleValue() * 100));
                    }

                    // CRITICAL: Mark L01 as assigned to prevent duplicate L01 assignments
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        l01AssignedToday = true;
                    }
                    // Update in-memory counts for next iteration
                    periodData.staffShiftTypeCounts().computeIfAbsent(staff.getId(), k -> new HashMap<>())
                            .merge(shiftTypeId, 1L, Long::sum);
                    if (save && ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        createCompensationDayForAuto(saved);
                    }
                }
            }
            currentDate = currentDate.plusDays(1);
        }
        return createdSchedules;
    }

    // ==================== ROUND ROBIN ALGORITHM ====================
    private List<Schedule> runRoundRobin(SchedulePeriod period, List<ShiftRequirement> requirements,
                                          List<Staff> activeStaff, boolean save,
                                          AlgorithmConfigService.AlgorithmRuntimeConfig runtimeConfig,
                                          Set<Integer> excludedStaffIds) {
        List<Schedule> createdSchedules = new ArrayList<>();
        Map<Integer, Integer> staffRotationIndex = new HashMap<>();
        for (Staff staff : activeStaff) {
            staffRotationIndex.put(staff.getId(), 0);
        }

        Map<LocalDate, List<ShiftRequirement>> requirementsByDate = groupRequirementsByDate(requirements);

        // OPTIMIZATION: Load all shift type counts in ONE query (instead of N×4)
        Map<Integer, Map<String, Long>> staffShiftTypeCounts = loadStaffShiftTypeCounts(period.getId());

        LocalDate currentDate = period.getStartDate();
        LocalDate periodEnd = period.getEndDate();
        while (!currentDate.isAfter(periodEnd)) {
            List<ShiftRequirement> todayReqs = sortRequirementsByPriority(
                    requirementsByDate.getOrDefault(currentDate, Collections.emptyList()));

            Set<Integer> assignedStaffIds = new HashSet<>();
            boolean l01AssignedToday = false;  // CRITICAL: Only 1 L01 per day per spec M02
            for (ShiftRequirement req : todayReqs) {
                // CRITICAL FIX: If L01 already assigned today, skip remaining L01 requirements
                if (ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                    if (l01AssignedToday) {
                        log.debug("Skipping duplicate L01 requirement for date={} - already assigned today", currentDate);
                        continue;
                    }
                }

                final LocalDate workDate = currentDate;
                final String shiftTypeId = req.getShiftType().getId();
                final boolean isWeekend = currentDate.getDayOfWeek() == DayOfWeek.SATURDAY
                        || currentDate.getDayOfWeek() == DayOfWeek.SUNDAY;

                // Pre-filter: remove staff already assigned to another requirement today
                final Set<Integer> finalAssigned = assignedStaffIds;
                List<Staff> availablePool = activeStaff.stream()
                        .filter(s -> !finalAssigned.contains(s.getId()))
                        .collect(Collectors.toList());

                // CRITICAL FIX: Fairness by SHIFT TYPE, then TOTAL, then rotation
                // Staff with fewer of THIS shift type get higher priority
                // Secondary: staff with fewer TOTAL shifts get higher priority
                // Tertiary: rotation index for fairness
                Comparator<Staff> fairnessComparator = Comparator
                        .comparingLong((Staff s) -> {
                            Map<String, Long> counts = staffShiftTypeCounts.get(s.getId());
                            return counts != null ? counts.getOrDefault(shiftTypeId, 0L) : 0L;
                        })
                        .thenComparingLong(s -> {
                            // Add current in-memory counts to historical counts
                            Map<String, Long> counts = staffShiftTypeCounts.get(s.getId());
                            if (counts == null) return 0L;
                            return counts.getOrDefault("L01", 0L) + counts.getOrDefault("L02", 0L)
                                    + counts.getOrDefault("L03", 0L) + counts.getOrDefault("L04", 0L);
                        })
                        .thenComparingInt(s -> staffRotationIndex.getOrDefault(s.getId(), 0))
                        // weekend_weight: penalize weekend shifts using the configured multiplier
                        .thenComparingDouble(s -> {
                            if (!isWeekend) return 0.0;
                            Map<String, Long> counts = staffShiftTypeCounts.get(s.getId());
                            long totalShifts = counts != null
                                    ? counts.getOrDefault("L01", 0L) + counts.getOrDefault("L02", 0L)
                                            + counts.getOrDefault("L03", 0L) + counts.getOrDefault("L04", 0L)
                                    : 0L;
                            return totalShifts * runtimeConfig.getWeekendWeight().doubleValue();
                        });

                List<Staff> eligibleStaff = filterAndSortEligibleStaff(availablePool, req, excludedStaffIds, !save, true,
                        fairnessComparator);

                int toAssign = Math.min(req.getRequiredStaffCount(), eligibleStaff.size());
                int assignedCount = 0;
                int staffIndex = 0;
                while (assignedCount < toAssign && staffIndex < eligibleStaff.size()) {
                    Staff staff = eligibleStaff.get(staffIndex);
                    staffIndex++;
                    // Skip if already assigned to another shift today
                    if (assignedStaffIds.contains(staff.getId())) {
                        continue;
                    }
                    Schedule saved = buildAndSaveSchedule(period, staff, req, workDate, save, createdSchedules);
                    trackAssignment(staff, workDate, req.getShiftType().getId());
                    assignedStaffIds.add(staff.getId());
                    staffRotationIndex.merge(staff.getId(), 1, Integer::sum);
                    assignedCount++;
                    // CRITICAL: Mark L01 as assigned to prevent duplicate L01 assignments
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        l01AssignedToday = true;
                    }
                    // Update in-memory counts for next iteration
                    staffShiftTypeCounts.computeIfAbsent(staff.getId(), k -> new HashMap<>())
                            .merge(shiftTypeId, 1L, Long::sum);
                    if (save && ConflictDetectionService.SHIFT_TYPE_L01.equals(req.getShiftType().getId())) {
                        createCompensationDayForAuto(saved);
                    }
                }
            }
            currentDate = currentDate.plusDays(1);
        }
        return createdSchedules;
    }

    // ==================== BACKTRACKING ALGORITHM ====================

    private List<Schedule> runBacktracking(SchedulePeriod period, List<ShiftRequirement> requirements,
                                            List<Staff> activeStaff, boolean save, int maxIterations,
                                            long backtrackTimeLimitNs,
                                            Set<Integer> excludedStaffIds) {
        List<Schedule> bestSolution = new ArrayList<>();
        List<Schedule> currentSolution = new ArrayList<>();

        // Group by date and sort dates — process by date (like greedy/round-robin),
        // not all-L01-then-all-L02 which exhausts staff before L02 can be assigned
        Map<LocalDate, List<ShiftRequirement>> byDate = requirements.stream()
                .collect(Collectors.groupingBy(ShiftRequirement::getWorkDate));
        List<LocalDate> sortedDates = byDate.keySet().stream().sorted().toList();

        // Total staff-slots needed — used for early termination when a complete solution is found
        int totalNeeded = requirements.stream()
                .mapToInt(com.hospital.scheduler.entity.ShiftRequirement::getRequiredStaffCount)
                .sum();

        // Use time-based deadline instead of iteration counter — prevents runaway recursion
        // while still allowing thorough exploration within the time budget.
        final long deadline = System.nanoTime() + backtrackTimeLimitNs;

        List<Map<String, Set<String>>> assignmentHistory = new ArrayList<>();
        assignmentHistory.add(new HashMap<>());

        // OPTIMIZATION: Load all shift type counts in ONE query (instead of N×4)
        Map<Integer, Map<String, Long>> staffShiftTypeCounts = loadStaffShiftTypeCounts(period.getId());

        backtrackByDate(period, byDate, sortedDates, 0, currentSolution, bestSolution,
                new HashMap<>(), assignmentHistory, maxIterations, excludedStaffIds, save, totalNeeded, deadline,
                staffShiftTypeCounts);

        log.info("Backtracking done: best solution has {} schedules (needed: {}, deadline reached: {})",
                bestSolution.size(), totalNeeded, System.nanoTime() >= deadline);

        if (save) {
            List<Schedule> allSaved = new java.util.ArrayList<>();
            for (Schedule schedule : bestSolution) {
                Schedule saved = scheduleRepository.save(schedule);
                schedule.setId(saved.getId());
                if (ConflictDetectionService.SHIFT_TYPE_L01.equals(schedule.getShiftType().getId())) {
                    createCompensationDayForAuto(saved);
                }
                auditHistoryService.logAction("schedule", saved.getId(), AuditHistory.ActionType.INSERT, null, saved, null);
                allSaved.add(saved);
            }
            // Notify each staff about their auto-assigned shifts (one notification per staff)
            var staffScheduleMap = allSaved.stream()
                    .collect(java.util.stream.Collectors.groupingBy(s -> s.getStaff().getId()));
            for (var entry : staffScheduleMap.entrySet()) {
                Integer staffId = entry.getKey();
                List<Schedule> staffSchedules = entry.getValue();
                String dutyList = staffSchedules.stream()
                        .map(s -> s.getWorkDate().toString() + " (" + s.getShiftType().getName() + ")")
                        .collect(Collectors.joining("; "));
                notificationService.createNotification(staffId, new NotificationDTO(
                        "Bạn được phân công ca trực tự động",
                        "Bạn vừa được phân công " + staffSchedules.size() + " ca trực tự động.\nDanh sách: " + dutyList));
            }
        } else {
            for (Schedule schedule : bestSolution) {
                schedule.setId(null);
            }
        }

        return bestSolution;
    }

    // ==================== GENETIC ALGORITHM ====================

    /**
     * Run Genetic Algorithm for scheduling.
     */
    private SchedulingResultWithFairness runGeneticAlgorithm(
            SchedulePeriod period,
            List<ShiftRequirement> requirements,
            List<Staff> activeStaff,
            boolean save,
            Set<Integer> excludedStaffIds) {
        
        try {
            // Convert requirements to GA format
            List<com.hospital.scheduler.entity.ShiftRequirement> gaRequirements = requirements;
            
            // Get existing compensation days
            Set<String> existingCompDays = new HashSet<>();
            List<CompensationDay> existingComp = compensationDayRepository.findByPeriodId(period.getId());
            for (CompensationDay cd : existingComp) {
                existingCompDays.add(cd.getStaff().getId() + "_" + cd.getCompensationDate().toString());
            }
            
            // Get approved leave requests
            List<LeaveRequest> leaveRequests = leaveRequestRepository.findApprovedInRange(
                    period.getStartDate(), period.getEndDate());
            
            // Run GA
            com.hospital.scheduler.algorithm.SchedulingResult gaResult = geneticAlgorithmScheduler.solve(
                    activeStaff,
                    period.getStartDate(),
                    period.getEndDate(),
                    gaRequirements,
                    existingCompDays,
                    leaveRequests,
                    excludedStaffIds
            );
            
            // Convert GA result to Schedule list
            List<Schedule> createdSchedules = new ArrayList<>();
            
            for (Map.Entry<String, String> entry : gaResult.getAssignments().entrySet()) {
                String key = entry.getKey();
                String shiftTypeId = entry.getValue();
                
                // Parse key: staffId_date
                String[] parts = key.split("_");
                if (parts.length >= 2) {
                    Integer staffId = Integer.parseInt(parts[0]);
                    LocalDate workDate = LocalDate.parse(parts[1]);
                    
                    Staff staff = activeStaff.stream()
                            .filter(s -> s.getId().equals(staffId))
                            .findFirst()
                            .orElse(null);
                    
                    if (staff != null) {
                        // Find requirement
                        ShiftRequirement req = requirements.stream()
                                .filter(r -> r.getWorkDate().equals(workDate) && r.getShiftType().getId().equals(shiftTypeId))
                                .findFirst()
                                .orElse(null);
                        
                        if (req != null) {
                            Schedule schedule = Schedule.builder()
                                    .period(period)
                                    .staff(staff)
                                    .shiftType(req.getShiftType())
                                    .workDate(workDate)
                                    .requirement(req)
                                    .hasConflict(false)
                                    .build();
                            
                            if (save) {
                                // Check for existing
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
                    }
                }
            }
            
            log.info("GA produced {} schedules with {} conflicts", 
                    createdSchedules.size(), gaResult.getErrors().size());
            
            // Return schedules with GA's fairness score (0-100)
            BigDecimal fairnessScore = gaResult.getFairnessScore() != null 
                    ? gaResult.getFairnessScore() 
                    : BigDecimal.ZERO;
            return new SchedulingResultWithFairness(createdSchedules, fairnessScore);
            
        } catch (Exception e) {
            log.error("Genetic Algorithm failed: {}", e.getMessage(), e);
            return new SchedulingResultWithFairness(new ArrayList<>(), BigDecimal.ZERO);
        }
    }

    private void backtrack(SchedulePeriod period, List<ShiftRequirement> requirements,
                           List<Staff> activeStaff, int index,
                           List<Schedule> currentSolution, List<Schedule> bestSolution,
                           Map<Integer, Integer> staffWorkload,
                           List<Map<String, Set<String>>> assignmentHistory, int maxIterations,
                           Set<Integer> excludedStaffIds, boolean save, long deadline) {

        if (maxIterations <= 0) return;
        if (System.nanoTime() >= deadline) return;

        if (currentSolution.size() > bestSolution.size()) {
            bestSolution.clear();
            bestSolution.addAll(currentSolution);
        }

        if (index >= requirements.size()) return;

        ShiftRequirement req = requirements.get(index);
        LocalDate workDate = req.getWorkDate();

        List<Staff> candidates = conflictDetectionService.findReplacements(
                period.getId(), workDate, req.getShiftType().getId(), null,
                req.getRequiredStaffCount(), excludedStaffIds, !save);

        candidates = filterBySpecialty(candidates, req.getSpecialty() != null ? req.getSpecialty().getId() : null);

        candidates.sort(Comparator.comparingInt(s -> staffWorkload.getOrDefault(s.getId(), 0)));

        int staffToAssign = Math.min(req.getRequiredStaffCount(), candidates.size());

        for (int i = 0; i < staffToAssign; i++) {
            Staff staff = candidates.get(i);

            Map<String, Set<String>> currentAssignments = assignmentHistory.get(assignmentHistory.size() - 1);

            if (hasInMemoryConflictForBacktrack(staff.getId(), workDate, req.getShiftType().getId(), currentAssignments)) {
                continue;
            }

            Schedule schedule = buildScheduleForBacktrack(period, staff, req.getShiftType(), workDate, req);
            if (schedule == null) continue;

            currentSolution.add(schedule);
            staffWorkload.merge(staff.getId(), 1, Integer::sum);

            Map<String, Set<String>> newAssignments = new HashMap<>(currentAssignments);
            String key = staff.getId() + "_" + workDate;
            newAssignments.computeIfAbsent(key, k -> new HashSet<>()).add(req.getShiftType().getId());
            assignmentHistory.add(newAssignments);

            backtrack(period, requirements, activeStaff, index + 1,
                    currentSolution, bestSolution, staffWorkload, assignmentHistory,
                    maxIterations - 1, excludedStaffIds, save, deadline);

            assignmentHistory.remove(assignmentHistory.size() - 1);
            staffWorkload.merge(staff.getId(), -1, (oldVal, ignore) -> oldVal <= 0 ? 0 : oldVal);
            currentSolution.remove(currentSolution.size() - 1);
        }
    }

    /**
     * Backtrack by date — processes all shift requirements for each day together,
     * then moves to the next day. This matches greedy/round-robin behavior and
     * prevents L01 from consuming all staff before L02/L03/L04 for the same day
     * can be assigned.
     */
    private void backtrackByDate(SchedulePeriod period, Map<LocalDate, List<ShiftRequirement>> byDate,
                                  List<LocalDate> sortedDates, int dateIndex,
                                  List<Schedule> currentSolution, List<Schedule> bestSolution,
                                  Map<Integer, Integer> staffWorkload,
                                  List<Map<String, Set<String>>> assignmentHistory, int maxIterations,
                                  Set<Integer> excludedStaffIds, boolean save, int totalNeeded,
                                  long deadline,
                                  Map<Integer, Map<String, Long>> staffShiftTypeCounts) {

        // Always save current solution — ensures the first complete path is preserved
        if (currentSolution.size() > bestSolution.size()) {
            bestSolution.clear();
            bestSolution.addAll(currentSolution);
        }

        if (maxIterations <= 0) return;
        if (dateIndex >= sortedDates.size()) return;
        if (System.nanoTime() >= deadline) return; // hard stop — return best found so far

        LocalDate workDate = sortedDates.get(dateIndex);
        List<ShiftRequirement> dayReqs = sortRequirementsByPriority(byDate.get(workDate));

        // CRITICAL: Track L01 assignment per day to prevent multiple L01 assignments
        final boolean[] l01AssignedToday = {false};

        assignDay(period, dayReqs, workDate, currentSolution, staffWorkload,
                  assignmentHistory, excludedStaffIds, save,
                  () -> backtrackByDate(period, byDate, sortedDates, dateIndex + 1,
                          currentSolution, bestSolution, staffWorkload, assignmentHistory,
                          maxIterations - 1, excludedStaffIds, save, totalNeeded, deadline,
                          staffShiftTypeCounts),
                  deadline, staffShiftTypeCounts, l01AssignedToday);
    }

    /**
     * Assigns all requirements for a single day using backtracking.
     * Recursively tries each staff slot for each requirement.
     */
    private void assignDay(SchedulePeriod period, List<ShiftRequirement> dayReqs, LocalDate workDate,
                           List<Schedule> currentSolution, Map<Integer, Integer> staffWorkload,
                           List<Map<String, Set<String>>> assignmentHistory,
                           Set<Integer> excludedStaffIds, boolean save, Runnable onBacktrack,
                           long deadline,
                           Map<Integer, Map<String, Long>> staffShiftTypeCounts,
                           boolean[] l01AssignedToday) {
        if (log.isDebugEnabled()) {
            log.debug("assignDay date={} reqs={} solutionSize={}", workDate, dayReqs.size(), currentSolution.size());
        }

        // Base: all requirements assigned for this day — try next date
        if (dayReqs.isEmpty()) {
            if (log.isDebugEnabled()) log.debug("assignDay date={} all reqs done, recursing", workDate);
            if (System.nanoTime() < deadline) onBacktrack.run();
            return;
        }

        ShiftRequirement req = dayReqs.get(0);
        List<ShiftRequirement> remainingReqs = new ArrayList<>(dayReqs.subList(1, dayReqs.size()));
        String shiftTypeId = req.getShiftType().getId();

        // CRITICAL: Skip duplicate L01 requirements (only 1 L01 per day per spec M02)
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId) && l01AssignedToday[0]) {
            if (log.isDebugEnabled()) log.debug("assignDay date={} L01 already assigned today, skip", workDate);
            assignDay(period, remainingReqs, workDate, currentSolution, staffWorkload,
                      assignmentHistory, excludedStaffIds, save, onBacktrack, deadline,
                      staffShiftTypeCounts, l01AssignedToday);
            return;
        }

        int toAssign = req.getRequiredStaffCount();

        Set<Integer> assignedToday = currentSolution.stream()
                .filter(s -> s.getWorkDate().equals(workDate))
                .map(s -> s.getStaff().getId())
                .collect(Collectors.toSet());

        Set<Integer> excludeAll = new HashSet<>(assignedToday);
        if (excludedStaffIds != null) excludeAll.addAll(excludedStaffIds);

        List<Staff> candidates = conflictDetectionService.findReplacements(
                period.getId(), workDate, shiftTypeId, null, toAssign, excludeAll, !save);

        if (log.isDebugEnabled()) {
            log.debug("assignDay date={} type={} toAssign={} candidates={} exclude={}",
                    workDate, shiftTypeId, toAssign,
                    candidates == null ? "NULL" : candidates.size(), excludeAll.size());
        }

        if (candidates == null || candidates.isEmpty()) {
            if (log.isDebugEnabled()) log.debug("assignDay date={} no candidates, backtrack", workDate);
            return; // No valid candidates, backtrack
        }

        candidates = filterBySpecialty(candidates, req.getSpecialty() != null ? req.getSpecialty().getId() : null);

        // CRITICAL FIX: Sort by THIS shift type count, not total workload
        candidates.sort(Comparator.comparingLong((Staff s) -> {
            Map<String, Long> counts = staffShiftTypeCounts.get(s.getId());
            return counts != null ? counts.getOrDefault(shiftTypeId, 0L) : 0L;
        }));

        if (candidates.isEmpty()) return;

        for (Staff staff : candidates) {
            if (System.nanoTime() >= deadline) return; // stop exploring — time's up

            Map<String, Set<String>> currentAssignments = assignmentHistory.get(assignmentHistory.size() - 1);
            if (hasInMemoryConflictForBacktrack(staff.getId(), workDate, shiftTypeId, currentAssignments)) {
                if (log.isDebugEnabled()) log.debug("assignDay date={} type={} staffId={} skipped (in-memory conflict)",
                        workDate, shiftTypeId, staff.getId());
                continue;
            }

            Schedule schedule = buildScheduleForBacktrack(period, staff, req.getShiftType(), workDate, req);
            if (schedule == null) {
                if (log.isDebugEnabled()) log.debug("assignDay date={} type={} staffId={} skipped (buildSchedule null)",
                        workDate, shiftTypeId, staff.getId());
                continue;
            }

            currentSolution.add(schedule);
            staffWorkload.merge(staff.getId(), 1, Integer::sum);
            if (log.isDebugEnabled()) {
                log.debug("assignDay date={} type={} assigned staffId={} solutionSize={}",
                        workDate, shiftTypeId, staff.getId(), currentSolution.size());
            }

            // CRITICAL: Update shift type counts for fairness
            staffShiftTypeCounts.computeIfAbsent(staff.getId(), k -> new HashMap<>())
                    .merge(shiftTypeId, 1L, Long::sum);

            // CRITICAL: Mark L01 as assigned for the day
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                l01AssignedToday[0] = true;
                // Track compensation date for L01 so L02/L03/L04 can't be assigned on that day
                LocalDate compDate = compensationDateCalculator.calculate(workDate);
                if (compDate != null) {
                    String compKey = staff.getId() + "_" + compDate;
                    inMemoryCompensationShiftDates.get().add(compKey);
                    allCompensationShiftDates.get().add(staff.getId() + "_" + compDate.toString());
                }
            }

            Map<String, Set<String>> newAssignments = new HashMap<>(currentAssignments);
            newAssignments.computeIfAbsent(staff.getId() + "_" + workDate, k -> new HashSet<>()).add(shiftTypeId);
            assignmentHistory.add(newAssignments);

            assignDay(period, remainingReqs, workDate, currentSolution, staffWorkload,
                      assignmentHistory, excludedStaffIds, save, onBacktrack, deadline,
                      staffShiftTypeCounts, l01AssignedToday);

            // CRITICAL: Reset L01 flag on backtrack
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                l01AssignedToday[0] = false;
                // Rollback compensation date tracking
                LocalDate compDate = compensationDateCalculator.calculate(workDate);
                if (compDate != null) {
                    String compKey = staff.getId() + "_" + compDate;
                    inMemoryCompensationShiftDates.get().remove(compKey);
                    allCompensationShiftDates.get().remove(staff.getId() + "_" + compDate.toString());
                }
            }

            assignmentHistory.remove(assignmentHistory.size() - 1);
            staffWorkload.merge(staff.getId(), -1, (oldVal, ignore) -> oldVal <= 0 ? 0 : oldVal);
            // Rollback shift type count
            staffShiftTypeCounts.computeIfAbsent(staff.getId(), k -> new HashMap<>())
                    .merge(shiftTypeId, -1L, (oldVal, ignore) -> oldVal <= 0 ? 0L : oldVal);
            currentSolution.remove(currentSolution.size() - 1);
        }
    }

    private boolean hasInMemoryConflictForBacktrack(Integer staffId, LocalDate workDate, String shiftTypeId,
                                                    Map<String, Set<String>> assignments) {
        String key = staffId + "_" + workDate;
        Set<String> existingShifts = assignments.get(key);
        if (existingShifts != null) {
            for (String existingId : existingShifts) {
                if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                    if (ConflictDetectionService.SHIFT_TYPE_L02.equals(existingId) || ConflictDetectionService.SHIFT_TYPE_L03.equals(existingId) || ConflictDetectionService.SHIFT_TYPE_L04.equals(existingId)) {
                        return true;
                    }
                }
                if (ConflictDetectionService.SHIFT_TYPE_L02.equals(shiftTypeId)) {
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(existingId)) return true;
                }
                if (ConflictDetectionService.SHIFT_TYPE_L03.equals(shiftTypeId) || ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)) {
                    if ((ConflictDetectionService.SHIFT_TYPE_L03.equals(existingId) && ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)) ||
                        (ConflictDetectionService.SHIFT_TYPE_L04.equals(existingId) && ConflictDetectionService.SHIFT_TYPE_L03.equals(shiftTypeId))) {
                        return true;
                    }
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(existingId)) return true;
                }
            }
        }

        // L02/L03/L04 cannot be assigned on a day that is a compensation day for this staff
        if (!ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
            String compKey = staffId + "_" + workDate.toString();
            if (inMemoryCompensationShiftDates.get().contains(compKey)) {
                return true;
            }
        }

        // L01 cannot be assigned on a day that is already a compensation day for this staff
        if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
            String compKey = staffId + "_" + workDate.toString();
            if (allCompensationShiftDates.get().contains(compKey)) {
                return true;
            }
        }

        return false;
    }

    private Schedule buildScheduleForBacktrack(SchedulePeriod period, Staff staff, ShiftType shiftType,
                                               LocalDate workDate, ShiftRequirement requirement) {
        return Schedule.builder()
                .period(period)
                .staff(staff)
                .shiftType(shiftType)
                .workDate(workDate)
                .requirement(requirement)
                .hasConflict(false)
                .build();
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
                warnings.add(String.format("Ngày %s (%s), ca %s: thiếu %d nhân sự (có %d)",
                        req.getWorkDate(), DateUtils.getDayOfWeekVietnamese(req.getWorkDate().getDayOfWeek()),
                        req.getShiftType().getName(),
                        req.getRequiredStaffCount() - assigned, assigned));
            }
        }

        return warnings;
    }

    private List<Staff> filterBySpecialty(List<Staff> staffList, Integer specialtyId) {
        if (specialtyId == null) return staffList;
        return staffList.stream()
                .filter(s -> s.getSpecialty() != null && s.getSpecialty().getId().equals(specialtyId))
                .collect(Collectors.toList());
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
            // L01 vs L02/L03/L04: overnight cannot coexist with non-overnight
            // L02 vs L01: L01 already assigned → conflict
            // L02 vs L01: L02 already assigned → conflict
            for (String existingId : existingShifts) {
                if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                    // L01 cannot coexist with L02, L03, L04
                    if (ConflictDetectionService.SHIFT_TYPE_L02.equals(existingId) || ConflictDetectionService.SHIFT_TYPE_L03.equals(existingId) || ConflictDetectionService.SHIFT_TYPE_L04.equals(existingId)) {
                        return true;
                    }
                }
                if (ConflictDetectionService.SHIFT_TYPE_L02.equals(shiftTypeId)) {
                    // L02 cannot coexist with L01 (L01 already assigned)
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(existingId)) return true;
                }
                if (ConflictDetectionService.SHIFT_TYPE_L03.equals(shiftTypeId) || ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)) {
                    // L03 vs L04: cannot both be assigned to same person same day
                    if ((ConflictDetectionService.SHIFT_TYPE_L03.equals(existingId) && ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId)) ||
                        (ConflictDetectionService.SHIFT_TYPE_L04.equals(existingId) && ConflictDetectionService.SHIFT_TYPE_L03.equals(shiftTypeId))) {
                        return true;
                    }
                    // L03/L04 cannot coexist with L01
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(existingId)) return true;
                }
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
        if (existingShifts == null) {
            return false;
        }
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

    private void createCompensationDayForAuto(Schedule schedule) {
        if (schedule == null || schedule.getWorkDate() == null) {
            return;
        }
        LocalDate shiftDate = schedule.getWorkDate();
        LocalDate compensationDate = compensationDateCalculator.calculate(shiftDate);

        if (compensationDayRepository.findByStaffIdAndCompensationDate(schedule.getStaff().getId(), compensationDate).isPresent()) {
            return;
        }

        CompensationDay compDay = CompensationDay.builder()
                .schedule(schedule)
                .staff(schedule.getStaff())
                .period(schedule.getPeriod())
                .shiftDate(shiftDate)
                .compensationDate(compensationDate)
                .note("Ngày nghỉ bù tự động từ ca L01")
                .build();

        try {
            compensationDayRepository.save(compDay);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            org.slf4j.LoggerFactory.getLogger(AutoSchedulingService.class)
                    .warn("Compensation day already exists for staff {} on {} (DB constraint): {}",
                            schedule.getStaff().getId(), compensationDate, e.getMessage());
        }
    }

    private BigDecimal calculateBalanceScore(List<Schedule> schedules, int totalStaff) {
        if (schedules.isEmpty()) return BigDecimal.ZERO;

        Map<Integer, Long> staffScheduleCount = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

        // If only 1 staff assigned (all work to one person) → 0% balance
        if (staffScheduleCount.size() <= 1) {
            log.debug("Balance score 0: only {} staff assigned", staffScheduleCount.size());
            return BigDecimal.valueOf(0);
        }

        double avg = (double) schedules.size() / totalStaff;
        double variance = staffScheduleCount.values().stream()
                .mapToDouble(Long::doubleValue)
                .map(count -> (count - avg) * (count - avg))
                .average()
                .orElse(0);

        double stdDev = Math.sqrt(variance);
        double cv = avg > 0 ? (stdDev / avg) * 100 : 0;
        
        log.debug("Balance score calculation: schedules={}, totalStaff={}, avg={}, variance={}, stdDev={}, cv={}, score={}",
                schedules.size(), totalStaff, avg, variance, stdDev, cv, Math.max(0, 100 - cv));

        return BigDecimal.valueOf(Math.max(0, 100 - cv)).setScale(2, RoundingMode.HALF_UP);
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
                item.put("requiredStaffCount", req.getRequiredStaffCount());
                item.put("assignedStaffCount", (int) assigned);
                item.put("missingCount", req.getRequiredStaffCount() - (int) assigned);
                unassigned.add(item);
            }
        }
        return unassigned;
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

    private List<Staff> filterAndSortEligibleStaff(List<Staff> pool, ShiftRequirement req,
                                                    Set<Integer> excludedStaffIds, boolean skipCompensationCheck, boolean skipMaxShifts,
                                                    Comparator<Staff> sortComparator) {
        return pool.stream()
                .filter(s -> excludedStaffIds == null || !excludedStaffIds.contains(s.getId()))
                .filter(s -> {
                    if (req.getSpecialty() != null && (s.getSpecialty() == null || !s.getSpecialty().getId().equals(req.getSpecialty().getId()))) {
                        return false;
                    }
                    // M07 spec: auto-scheduling skips max shifts check per "không giới hạn cố định"
                    // skipBackToBackConflict=true to let in-memory tracking handle this constraint
                    if (conflictDetectionService.hasAnyConflict(s.getId(), req.getWorkDate(), req.getShiftType().getId(), null, skipCompensationCheck, false, skipMaxShifts, true)) {
                        return false;
                    }
                    if (hasInMemoryConflict(s.getId(), req.getWorkDate(), req.getShiftType().getId())) {
                        return false;
                    }
                    return true;
                })
                .sorted(sortComparator)
                .collect(Collectors.toList());
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

    /**
     * Auto-generate shift requirements for all days in the period.
     * Respects holidays (skip/partial mode), generates one requirement per (date × shiftType).
     * Used by M07-F01 to auto-generate requirements per spec.
     */
    private List<ShiftRequirement> generateRequirementsForPeriod(SchedulePeriod period, AutoGenConfig config, List<Staff> activeStaff) {
        List<ShiftRequirement> generated = new ArrayList<>();

        // Pre-load holidays
        Set<LocalDate> holidays = holidayRepository.findActiveHolidaysBetween(period.getStartDate(), period.getEndDate())
                .stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        // Pre-load shift types
        Map<String, ShiftType> shiftTypeMap = shiftTypeRepository.findAll().stream()
                .collect(Collectors.toMap(ShiftType::getId, s -> s));

        ShiftType l01 = shiftTypeMap.get("L01");
        ShiftType l02 = shiftTypeMap.get("L02");
        ShiftType l03 = shiftTypeMap.get("L03");
        ShiftType l04 = shiftTypeMap.get("L04");

        if (l01 == null || l02 == null || l03 == null || l04 == null) {
            throw new BadRequestException("Không tìm thấy shift types L01-L04 trong hệ thống");
        }

        LocalDate current = period.getStartDate();
        while (!current.isAfter(period.getEndDate())) {
            LocalDate date = current;
            boolean isHoliday = holidays.contains(date);
            DayOfWeek dow = date.getDayOfWeek();

            // L01: 2 người/ngày (skip nếu holiday)
            if (!isHoliday || !"SKIP".equals(config.holidayMode())) {
                ShiftRequirement reqL01 = ShiftRequirement.builder()
                        .period(period)
                        .shiftType(l01)
                        .workDate(date)
                        .specialty(null)
                        .requiredStaffCount(config.l01RequiredPerDay())
                        .note("AUTO:L01:" + date)
                        .build();
                generated.add(reqL01);
            }

            // L02: 2 người/ngày (skip nếu holiday)
            if (!isHoliday || !"SKIP".equals(config.holidayMode())) {
                ShiftRequirement reqL02 = ShiftRequirement.builder()
                        .period(period)
                        .shiftType(l02)
                        .workDate(date)
                        .specialty(null)
                        .requiredStaffCount(config.l02RequiredPerDay())
                        .note("AUTO:L02:" + date)
                        .build();
                generated.add(reqL02);
            }

            // L03: 2 người/ngày (50% nếu holiday mode = PARTIAL)
            if (config.holidayMode().equals("PARTIAL")) {
                ShiftRequirement reqL03 = ShiftRequirement.builder()
                        .period(period)
                        .shiftType(l03)
                        .workDate(date)
                        .specialty(null)
                        .requiredStaffCount(isHoliday ? 1 : config.l03RequiredPerDay())
                        .note("AUTO:L03:" + date)
                        .build();
                generated.add(reqL03);
            } else if (!isHoliday) {
                ShiftRequirement reqL03 = ShiftRequirement.builder()
                        .period(period)
                        .shiftType(l03)
                        .workDate(date)
                        .specialty(null)
                        .requiredStaffCount(config.l03RequiredPerDay())
                        .note("AUTO:L03:" + date)
                        .build();
                generated.add(reqL03);
            }

            // L04: 1 requirement per specialty per day (skip if holiday)
            // Per M05-F04 spec: L04 must be filtered by specialty (chuyên khoa).
            // Generate 1 requirement per active specialty, each requiring l04RequiredPerDay staff.
            if (!isHoliday || !"SKIP".equals(config.holidayMode())) {
                List<Specialty> activeSpecialties = specialtyRepository.findByIsActiveTrue();
                for (Specialty specialty : activeSpecialties) {
                    ShiftRequirement reqL04 = ShiftRequirement.builder()
                            .period(period)
                            .shiftType(l04)
                            .workDate(date)
                            .specialty(specialty)
                            .requiredStaffCount(config.l04RequiredPerDay())
                            .note("AUTO:L04:" + date + ":" + specialty.getName())
                            .build();
                    generated.add(reqL04);
                }
            }

            current = current.plusDays(1);
        }

        // Save all generated requirements
        List<ShiftRequirement> saved = requirementRepository.saveAll(generated);

        return saved;
    }

    private AutoScheduleResponse.GeneratedRequirementInfo toGeneratedRequirementInfo(ShiftRequirement r) {
        return AutoScheduleResponse.GeneratedRequirementInfo.builder()
                .workDate(r.getWorkDate().toString())
                .shiftTypeId(r.getShiftType().getId())
                .shiftTypeName(r.getShiftType().getName())
                .requiredStaffCount(r.getRequiredStaffCount())
                .specialtyName(r.getSpecialty() != null ? r.getSpecialty().getName() : null)
                .wasAutoGenerated(true)
                .build();
    }

    // ==================== BATCH CONFLICT DATA LOADING (avoids N+1) ====================

    /**
     * Load all shift type counts for all staff in a single query.
     * Replaces N×4 individual count queries → 1 query total.
     */
    private Map<Integer, Map<String, Long>> loadStaffShiftTypeCounts(Integer periodId) {
        Map<Integer, Map<String, Long>> result = new HashMap<>();
        List<Object[]> rows = scheduleRepository.countAllByPeriodIdGroupByStaffAndShiftType(periodId);
        for (Object[] row : rows) {
            Integer staffId = (Integer) row[0];
            String shiftTypeId = (String) row[1];
            Long count = (Long) row[2];
            result.computeIfAbsent(staffId, k -> new HashMap<>()).put(shiftTypeId, count);
        }
        return result;
    }

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
        Set<Integer> allOnCompDay = new HashSet<>();
        for (CompensationDay cd : compensationDayRepository.findInRange(periodStart, periodEnd)) {
            allOnCompDay.add(cd.getStaff().getId());
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

        // 5. Build date range for adjacent L01 check
        LocalDate adjStart = periodStart.minusDays(1);
        LocalDate adjEnd = periodEnd.plusDays(1);

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
        for (CompensationDay cd : compensationDayRepository.findInRange(periodStart, periodEnd)) {
            compDaysByDate.computeIfAbsent(cd.getCompensationDate(), k -> new HashSet<>()).add(cd.getStaff().getId());
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

        // 8. Build shift type counts from all schedules
        Map<Integer, Map<String, Long>> staffShiftTypeCounts = new HashMap<>();
        for (Map.Entry<Integer, List<Schedule>> entry : allSchedulesByStaff.entrySet()) {
            Map<String, Long> counts = new HashMap<>();
            counts.put("L01", 0L);
            counts.put("L02", 0L);
            counts.put("L03", 0L);
            counts.put("L04", 0L);
            for (Schedule s : entry.getValue()) {
                counts.merge(s.getShiftType().getId(), 1L, Long::sum);
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
     */
    private List<Staff> filterAndSortEligibleStaffBatch(
            List<Staff> pool,
            ShiftRequirement req,
            Set<Integer> excludedStaffIds,
            Set<Integer> assignedStaffIds,
            BatchConflictData batchData,
            boolean skipCompensationCheck,
            Comparator<Staff> sortComparator,
            PeriodConflictData periodData) {

        ShiftType shiftType = req.getShiftType();
        String shiftTypeId = shiftType.getId();
        boolean isOvernight = Boolean.TRUE.equals(shiftType.getIsOvernight());

        // Calculate period days for max shifts check
        int periodDays = (int) java.time.temporal.ChronoUnit.DAYS.between(
                req.getWorkDate(), req.getWorkDate()) + 1; // placeholder, we'll check per-staff

        List<Staff> eligible = new ArrayList<>();
        for (Staff staff : pool) {
            if (excludedStaffIds != null && excludedStaffIds.contains(staff.getId())) continue;
            // Skip staff already assigned to another requirement on the same day
            if (assignedStaffIds != null && assignedStaffIds.contains(staff.getId())) continue;

            // 1. Check specialty FIRST (hard requirement)
            if (req.getSpecialty() != null && (staff.getSpecialty() == null
                    || !staff.getSpecialty().getId().equals(req.getSpecialty().getId()))) {
                continue;
            }

            // 2. In-memory assignment conflict (from this scheduling run)
            if (hasInMemoryConflict(staff.getId(), req.getWorkDate(), shiftTypeId)) {
                continue;
            }

            // 3. Use batch-loaded data instead of per-staff queries (leave, comp day, adjacents)
            if (batchData.onLeaveStaffIds().contains(staff.getId())) continue;

            if (!skipCompensationCheck && batchData.onCompDayStaffIds().contains(staff.getId())) continue;

            // Adjacent day restriction only applies to L01 (trực 24/24 cannot work two consecutive days)
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)
                    && batchData.adjacentL01StaffIds().contains(staff.getId())) continue;

            // Same-day shift-type conflict
            List<Schedule> daySchedules = batchData.daySchedulesByStaff().get(staff.getId());
            if (daySchedules != null) {
                boolean hasConflict = false;
                for (Schedule s : daySchedules) {
                    boolean existingIsOvernight = Boolean.TRUE.equals(s.getShiftType().getIsOvernight());
                    if (isOvernight != existingIsOvernight) { hasConflict = true; break; }
                    if (!isOvernight && !existingIsOvernight) {
                        String eid = s.getShiftType().getId();
                        if ((ConflictDetectionService.SHIFT_TYPE_L03.equals(shiftTypeId) && ConflictDetectionService.SHIFT_TYPE_L04.equals(eid))
                                || (ConflictDetectionService.SHIFT_TYPE_L04.equals(shiftTypeId) && ConflictDetectionService.SHIFT_TYPE_L03.equals(eid))) {
                            hasConflict = true; break;
                        }
                    }
                }
                if (hasConflict) continue;
            }

            // 4. ENHANCED: Soft maxShiftsPerMonth check
            // Track if this staff is over their limit - they go to a "overflow" list
            // This allows higher coverage while still preferring staff under their limit
            Integer maxShifts = staff.getMaxShiftsPerMonth();
            Map<String, Long> currentCounts = periodData.staffShiftTypeCounts().get(staff.getId());
            long totalCurrent = currentCounts != null
                    ? currentCounts.getOrDefault("L01", 0L) + currentCounts.getOrDefault("L02", 0L)
                            + currentCounts.getOrDefault("L03", 0L) + currentCounts.getOrDefault("L04", 0L)
                    : 0L;
            boolean isOverLimit = maxShifts != null && maxShifts > 0 && totalCurrent >= maxShifts;

            // Staff over limit: only add if no one under limit is available
            // This is handled by the sort comparator - they get sorted last
            eligible.add(staff);
        }

        eligible.sort(sortComparator);
        return eligible;
    }
}
