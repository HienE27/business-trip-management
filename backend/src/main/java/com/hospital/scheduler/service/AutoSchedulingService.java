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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final AlgorithmMetricsRepository metricsRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final AuditHistoryService auditHistoryService;
    private final CompensationDateCalculator compensationDateCalculator;
    private final NotificationService notificationService;
    private final AlgorithmConfigService algorithmConfigService;
    private final HolidayRepository holidayRepository;
    private final ShiftTypeRepository shiftTypeRepository;

    // Thread-local so concurrent requests don't share state
    private final ThreadLocal<Map<String, Set<String>>> inMemoryAssignments = ThreadLocal.withInitial(HashMap::new);
    private final ThreadLocal<Set<String>> inMemoryCompensationShiftDates = ThreadLocal.withInitial(HashSet::new);
    private final ThreadLocal<Set<String>> allCompensationShiftDates = ThreadLocal.withInitial(HashSet::new);

    // Batch-loaded conflict data for a single scheduling run (avoid N+1)
    private record BatchConflictData(
            Set<Integer> onLeaveStaffIds,
            Set<Integer> onCompDayStaffIds,
            Map<Integer, List<Schedule>> daySchedulesByStaff,
            Set<Integer> hasAdjacentSchedule
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

            // Pre-check the in-loop assignments so we never save a sibling conflict
            // (e.g. L01 then L02 in the same preview for the same staff+date).
            if (hasInLoopConflict(inApplyLoop, staff.getId(), workDate, shiftType.getId())) {
                throw new ConflictException("Xung đột trong bản nháp: nhân sự " + staff.getId()
                        + " đã được phân công ca xung khắc ngày " + workDate);
            }

            // Re-validate against persisted state. The auto-scheduling algorithm may have
            // produced a preview minutes ago — DB state can have changed since then
            // (other managers added schedules, leave requests were approved, etc.).
            // Pass skipCompensationDay=false and skipShiftTypeConflict=false to keep
            // all hard constraints enforced.
            conflictDetectionService.validateAndThrow(
                    staff.getId(), workDate, shiftType.getId(), null, period.getId());

            Schedule schedule = Schedule.builder()
                    .period(period)
                    .staff(staff)
                    .shiftType(shiftType)
                    .workDate(workDate)
                    .requirement(requirement)
                    .hasConflict(false)
                    .build();

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

        int totalRequired = request.getSchedules().size();
        BigDecimal coverageRate = totalRequired > 0
                ? BigDecimal.valueOf((double) savedSchedules.size() / totalRequired * 100)
                : BigDecimal.ZERO;
        BigDecimal balanceScore = calculateBalanceScore(savedSchedules, (int) savedSchedules.stream()
                .map(s -> s.getStaff().getId())
                .distinct()
                .count());

        long executionTime = System.currentTimeMillis() - startTime;

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
                .executedAt(LocalDateTime.now())
                .build();
    }

    private AutoScheduleResponse runScheduling(AutoScheduleRequestDTO request, boolean save) {
        long startTime = System.currentTimeMillis();

        SchedulePeriod period = periodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kỳ lịch với ID: " + request.getPeriodId()));

        if (period.getStatus() != SchedulePeriod.PeriodStatus.DRAFT) {
            throw new BadRequestException("Chỉ có thể xếp lịch tự động khi kỳ lịch ở trạng thái DRAFT");
        }

        List<Staff> activeStaff = staffRepository.findByIsActiveTrue();
        if (request.getExcludedStaffIds() != null && !request.getExcludedStaffIds().isEmpty()) {
            Set<Integer> excluded = new HashSet<>(request.getExcludedStaffIds());
            activeStaff = activeStaff.stream()
                    .filter(s -> !excluded.contains(s.getId()))
                    .collect(Collectors.toList());
        }

        // Auto-generate requirements if enabled (per M07 spec)
        List<ShiftRequirement> requirements;
        List<AutoScheduleResponse.GeneratedRequirementInfo> generatedRequirements = null;
        if (Boolean.TRUE.equals(request.getAutoGenerateRequirements())) {
            var autoGenConfig = algorithmConfigService.getAutoGenConfig();
            if (autoGenConfig.isPresent() && autoGenConfig.get().enabled()) {
                requirements = generateRequirementsForPeriod(period, autoGenConfig.get(), activeStaff);
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
            createdSchedules = runRoundRobin(period, requirements, activeStaff, save,
                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
        } else if ("BACKTRACKING".equals(algorithmType)) {
            createdSchedules = runBacktracking(period, requirements, activeStaff, save,
                    request.getMaxIterations() != null ? request.getMaxIterations() : 1000,
                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
        } else {
            createdSchedules = runGreedy(period, requirements, activeStaff, save,
                    request.getExcludedStaffIds() != null ? new HashSet<>(request.getExcludedStaffIds()) : null);
        }
        List<String> warnings = buildWarnings(requirements, createdSchedules);

        long executionTime = System.currentTimeMillis() - startTime;
        int totalRequired = requirements.size();
        BigDecimal coverageRate = totalRequired > 0
                ? BigDecimal.valueOf((double) createdSchedules.size() / totalRequired * 100)
                : BigDecimal.ZERO;
        int distinctStaffAssigned = (int) createdSchedules.stream().map(s -> s.getStaff().getId()).distinct().count();
        int staffCount = distinctStaffAssigned > 0 ? distinctStaffAssigned : 1;
        BigDecimal balanceScore = calculateBalanceScore(createdSchedules, staffCount);

        if (save) {
            saveMetrics(period, algorithmType, (int) executionTime, coverageRate, balanceScore, warnings.size());
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
                                     Set<Integer> excludedStaffIds) {
        List<Schedule> createdSchedules = new ArrayList<>();
        Map<LocalDate, List<ShiftRequirement>> requirementsByDate = groupRequirementsByDate(requirements);

        // Pre-load ALL conflict data upfront to avoid N+1 queries
        Map<LocalDate, BatchConflictData> conflictDataByDate = new HashMap<>();
        LocalDate periodEnd = period.getEndDate();
        for (LocalDate date = period.getStartDate(); !date.isAfter(periodEnd); date = date.plusDays(1)) {
            conflictDataByDate.put(date, loadBatchConflictData(date));
        }

        LocalDate currentDate = period.getStartDate();
        while (!currentDate.isAfter(periodEnd)) {
            List<ShiftRequirement> todayReqs = sortRequirementsByPriority(
                    requirementsByDate.getOrDefault(currentDate, Collections.emptyList()));

            BatchConflictData todayConflicts = conflictDataByDate.get(currentDate);
            Set<Integer> assignedStaffIds = new HashSet<>();
            for (ShiftRequirement req : todayReqs) {
                final LocalDate workDate = currentDate;
                List<Staff> eligibleStaff = filterAndSortEligibleStaffBatch(
                        activeStaff, req, excludedStaffIds, assignedStaffIds, todayConflicts, !save,
                        Comparator.comparingLong(s -> scheduleRepository.countByStaffIdAndPeriodId(s.getId(), period.getId())));

                int toAssign = Math.min(req.getRequiredStaffCount(), eligibleStaff.size());
                for (int i = 0; i < toAssign; i++) {
                    Staff staff = eligibleStaff.get(i);
                    Schedule saved = buildAndSaveSchedule(period, staff, req, workDate, save, createdSchedules);
                    if (saved == null) continue;
                    trackAssignment(staff, workDate, req.getShiftType().getId());
                    assignedStaffIds.add(staff.getId());
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
                                          Set<Integer> excludedStaffIds) {
        List<Schedule> createdSchedules = new ArrayList<>();
        Map<Integer, Integer> staffRotationIndex = new HashMap<>();
        for (Staff staff : activeStaff) {
            staffRotationIndex.put(staff.getId(), 0);
        }

        Map<LocalDate, List<ShiftRequirement>> requirementsByDate = groupRequirementsByDate(requirements);

        LocalDate currentDate = period.getStartDate();
        while (!currentDate.isAfter(period.getEndDate())) {
            List<ShiftRequirement> todayReqs = sortRequirementsByPriority(
                    requirementsByDate.getOrDefault(currentDate, Collections.emptyList()));

            Set<Integer> assignedStaffIds = new HashSet<>();
            for (ShiftRequirement req : todayReqs) {
                final LocalDate workDate = currentDate;
                // Pre-filter: remove staff already assigned to another requirement today
                final Set<Integer> finalAssigned = assignedStaffIds;
                List<Staff> availablePool = activeStaff.stream()
                        .filter(s -> !finalAssigned.contains(s.getId()))
                        .collect(Collectors.toList());
                List<Staff> eligibleStaff = filterAndSortEligibleStaff(availablePool, req, excludedStaffIds, !save,
                        Comparator.comparingInt(s -> staffRotationIndex.getOrDefault(s.getId(), 0)));

                int toAssign = Math.min(req.getRequiredStaffCount(), eligibleStaff.size());
                for (int i = 0; i < toAssign; i++) {
                    Staff staff = eligibleStaff.get(i);
                    Schedule saved = buildAndSaveSchedule(period, staff, req, workDate, save, createdSchedules);
                    trackAssignment(staff, workDate, req.getShiftType().getId());
                    assignedStaffIds.add(staff.getId());
                    staffRotationIndex.merge(staff.getId(), 1, Integer::sum);
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
                                            Set<Integer> excludedStaffIds) {
        List<Schedule> bestSolution = new ArrayList<>();
        List<Schedule> currentSolution = new ArrayList<>();
        // Track in-memory assignments for backtracking: staffId_workDate -> set of shiftTypeIds
        List<Map<String, Set<String>>> assignmentHistory = new ArrayList<>();
        assignmentHistory.add(new HashMap<>());

        List<ShiftRequirement> sortedRequirements = requirements.stream()
                .sorted(Comparator.comparingInt((ShiftRequirement r) -> {
                    String id = r.getShiftType().getId();
                    if (ConflictDetectionService.SHIFT_TYPE_L01.equals(id)) return 0;
                    if (ConflictDetectionService.SHIFT_TYPE_L02.equals(id)) return 1;
                    if (ConflictDetectionService.SHIFT_TYPE_L03.equals(id)) return 2;
                    if (ConflictDetectionService.SHIFT_TYPE_L04.equals(id)) return 3;
                    return 4;
                })
                        .thenComparing((ShiftRequirement r) -> r.getSpecialty() != null ? 0 : 1)
                        .thenComparing(r -> r.getRequiredStaffCount())
                        .reversed())
                .collect(Collectors.toList());

        backtrack(period, sortedRequirements, activeStaff, 0, currentSolution, bestSolution,
                  new HashMap<>(), assignmentHistory, maxIterations, excludedStaffIds, save);

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

    private void backtrack(SchedulePeriod period, List<ShiftRequirement> requirements,
                           List<Staff> activeStaff, int index,
                           List<Schedule> currentSolution, List<Schedule> bestSolution,
                           Map<Integer, Integer> staffWorkload,
                           List<Map<String, Set<String>>> assignmentHistory, int maxIterations,
                           Set<Integer> excludedStaffIds, boolean save) {

        if (maxIterations <= 0) return;

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
                    maxIterations - 1, excludedStaffIds, save);

            assignmentHistory.remove(assignmentHistory.size() - 1);
            staffWorkload.merge(staff.getId(), -1, (oldVal, ignore) -> oldVal <= 0 ? 0 : oldVal);
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
            data.put("workloadPercentage", schedules.isEmpty() ? 0.0 :
                    Math.round((double) staffSchedules.size() / schedules.size() * 10000.0) / 100.0);

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

        double avgWorkload = schedules.isEmpty() ? 0.0 :
                Math.round((double) schedules.size() / activeStaff.size() * 100.0) / 100.0;
        // If filtered by shift type, average is relative to participating staff
        if (shiftTypeId != null && !shiftTypeId.isBlank() && !staffWorkloadData.isEmpty()) {
            avgWorkload = Math.round((double) schedules.size() / staffWorkloadData.size() * 100.0) / 100.0;
        }

        long minWorkload = staffWorkloadData.stream()
                .mapToLong(m -> ((Number) m.get("totalShifts")).longValue())
                .min().orElse(0);
        long maxWorkload = staffWorkloadData.stream()
                .mapToLong(m -> ((Number) m.get("totalShifts")).longValue())
                .max().orElse(0);

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

        compensationDayRepository.save(compDay);
    }

    private BigDecimal calculateBalanceScore(List<Schedule> schedules, int totalStaff) {
        if (schedules.isEmpty()) return BigDecimal.ZERO;

        Map<Integer, Long> staffScheduleCount = schedules.stream()
                .collect(Collectors.groupingBy(s -> s.getStaff().getId(), Collectors.counting()));

        if (staffScheduleCount.size() <= 1) return BigDecimal.valueOf(100);

        double avg = (double) schedules.size() / totalStaff;
        double variance = staffScheduleCount.values().stream()
                .mapToDouble(Long::doubleValue)
                .map(count -> (count - avg) * (count - avg))
                .average()
                .orElse(0);

        double stdDev = Math.sqrt(variance);
        double cv = avg > 0 ? (stdDev / avg) * 100 : 0;

        return BigDecimal.valueOf(Math.max(0, 100 - cv)).setScale(2, RoundingMode.HALF_UP);
    }

    private void saveMetrics(SchedulePeriod period, String algorithmType, int executionTime,
                             BigDecimal coverageRate, BigDecimal balanceScore, int conflictCount) {
        AlgorithmMetrics metrics = AlgorithmMetrics.builder()
                .period(period)
                .algorithmType(algorithmType)
                .executionTimeMs(executionTime)
                .coverageRate(coverageRate)
                .balanceScore(balanceScore)
                .conflictCount(conflictCount)
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

    private AlgorithmMetricsDTO metricsToDTO(AlgorithmMetrics m) {
        return AlgorithmMetricsDTO.builder()
                .id(m.getId())
                .algorithmType(m.getAlgorithmType())
                .executionTimeMs(m.getExecutionTimeMs())
                .coverageRate(m.getCoverageRate())
                .balanceScore(m.getBalanceScore())
                .conflictCount(m.getConflictCount())
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
                                                    Set<Integer> excludedStaffIds, boolean skipCompensationCheck,
                                                    Comparator<Staff> sortComparator) {
        return pool.stream()
                .filter(s -> excludedStaffIds == null || !excludedStaffIds.contains(s.getId()))
                .filter(s -> {
                    if (req.getSpecialty() != null && (s.getSpecialty() == null || !s.getSpecialty().getId().equals(req.getSpecialty().getId()))) {
                        return false;
                    }
                    if (conflictDetectionService.hasAnyConflict(s.getId(), req.getWorkDate(), req.getShiftType().getId(), null, skipCompensationCheck)) {
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

            // L04: 2 người/ngày (skip nếu holiday)
            if (!isHoliday || !"SKIP".equals(config.holidayMode())) {
                ShiftRequirement reqL04 = ShiftRequirement.builder()
                        .period(period)
                        .shiftType(l04)
                        .workDate(date)
                        .specialty(null)
                        .requiredStaffCount(config.l04RequiredPerDay())
                        .note("AUTO:L04:" + date)
                        .build();
                generated.add(reqL04);
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

        Set<Integer> adjacent = new HashSet<>();
        for (Schedule s : scheduleRepository.findByStaffIdAndDateRange(null, prevDay, prevDay)) {
            adjacent.add(s.getStaff().getId());
        }
        for (Schedule s : scheduleRepository.findByStaffIdAndDateRange(null, nextDay, nextDay)) {
            adjacent.add(s.getStaff().getId());
        }

        return new BatchConflictData(onLeave, onComp, daySchedules, adjacent);
    }

    /**
     * Batch-aware version of filterAndSortEligibleStaff that uses pre-loaded conflict data
     * instead of making per-staff DB queries for leave/compensation/shift-type conflicts.
     * Only falls back to the DB when checking max shifts (per-staff quota).
     */
    private List<Staff> filterAndSortEligibleStaffBatch(
            List<Staff> pool,
            ShiftRequirement req,
            Set<Integer> excludedStaffIds,
            Set<Integer> assignedStaffIds,
            BatchConflictData batchData,
            boolean skipCompensationCheck,
            Comparator<Staff> sortComparator) {

        ShiftType shiftType = req.getShiftType();
        String shiftTypeId = shiftType.getId();
        boolean isOvernight = Boolean.TRUE.equals(shiftType.getIsOvernight());

        List<Staff> eligible = new ArrayList<>();
        for (Staff staff : pool) {
            if (excludedStaffIds != null && excludedStaffIds.contains(staff.getId())) continue;
            // Skip staff already assigned to another requirement on the same day
            if (assignedStaffIds != null && assignedStaffIds.contains(staff.getId())) continue;

            // 1. Check max shifts (only L01)
            if (ConflictDetectionService.SHIFT_TYPE_L01.equals(shiftTypeId)) {
                if (conflictDetectionService.hasExceededMaxShifts(staff.getId(), req.getPeriod().getId(), ConflictDetectionService.SHIFT_TYPE_L01)) {
                    continue;
                }
            }

            // 2. Check specialty
            if (req.getSpecialty() != null && (staff.getSpecialty() == null
                    || !staff.getSpecialty().getId().equals(req.getSpecialty().getId()))) {
                continue;
            }

            // 3. In-memory assignment conflict (from this scheduling run)
            if (hasInMemoryConflict(staff.getId(), req.getWorkDate(), shiftTypeId)) {
                continue;
            }

            // 4. Use batch-loaded data instead of per-staff queries
            if (batchData.onLeaveStaffIds().contains(staff.getId())) continue;

            if (!skipCompensationCheck && batchData.onCompDayStaffIds().contains(staff.getId())) continue;

            if (batchData.hasAdjacentSchedule().contains(staff.getId())) continue;

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

            eligible.add(staff);
        }

        eligible.sort(sortComparator);
        return eligible;
    }
}
