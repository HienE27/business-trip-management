package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.entity.*;
import com.hospital.scheduler.algorithm.CSPScheduler;
import com.hospital.scheduler.algorithm.ScheduleChange;
import com.hospital.scheduler.algorithm.SchedulingResult;
import com.hospital.scheduler.algorithm.ShiftRequirementInfo;
import com.hospital.scheduler.repository.*;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Plain class (NOT a Spring bean) that holds the CSP scheduling adapter methods
 * extracted from AutoSchedulingService. Instantiated via @PostConstruct in
 * AutoSchedulingService.
 */
@Slf4j
public class CspSchedulerAdapter {

    // Wrapper to return both schedules and the fairness score for downstream
    // metrics. Kept as a record because callers in the CSP path also need
    // to know whether the plan was a partial timeout result so the Greedy
    // fallback can take over.
    public record SchedulingResultWithFairness(List<Schedule> schedules, BigDecimal fairnessScore, boolean cspPartial) {
        public SchedulingResultWithFairness(List<Schedule> schedules, BigDecimal fairnessScore) {
            this(schedules, fairnessScore, false);
        }
    }

    private final AutoSchedulingService autoSchedulingService;
    private final CSPScheduler cspScheduler;
    private final ScheduleRepository scheduleRepository;
    private final SchedulePeriodRepository periodRepository;
    private final CompensationDayRepository compensationDayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ScheduleConflictRepository scheduleConflictRepository;
    private final EntityManager entityManager;
    private final AuditHistoryService auditHistoryService;
    private final ConflictDetectionService conflictDetectionService;
    private final ShiftTypeRepository shiftTypeRepository;
    private final AlgorithmConfigService algorithmConfigService;
    private final ShiftRequirementRepository requirementRepository;
    private final StaffRepository staffRepository;

    public CspSchedulerAdapter(AutoSchedulingService autoSchedulingService,
                                CSPScheduler cspScheduler,
                                ScheduleRepository scheduleRepository,
                                SchedulePeriodRepository periodRepository,
                                CompensationDayRepository compensationDayRepository,
                                LeaveRequestRepository leaveRequestRepository,
                                ScheduleConflictRepository scheduleConflictRepository,
                                EntityManager entityManager,
                                AuditHistoryService auditHistoryService,
                                ConflictDetectionService conflictDetectionService,
                                ShiftTypeRepository shiftTypeRepository,
                                AlgorithmConfigService algorithmConfigService,
                                ShiftRequirementRepository requirementRepository,
                                StaffRepository staffRepository) {
        this.autoSchedulingService = autoSchedulingService;
        this.cspScheduler = cspScheduler;
        this.scheduleRepository = scheduleRepository;
        this.periodRepository = periodRepository;
        this.compensationDayRepository = compensationDayRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.scheduleConflictRepository = scheduleConflictRepository;
        this.entityManager = entityManager;
        this.auditHistoryService = auditHistoryService;
        this.conflictDetectionService = conflictDetectionService;
        this.shiftTypeRepository = shiftTypeRepository;
        this.algorithmConfigService = algorithmConfigService;
        this.requirementRepository = requirementRepository;
        this.staffRepository = staffRepository;
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
     * (e.g. {@code L01}, {@code L02}, ...). We translate that into JPA
     * {@link Schedule} entities, including the L01 compensation-day derivation
     * so the saved plan stays consistent with the compensation rules.
     */
    public SchedulingResultWithFairness runCsp(
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
                autoSchedulingService.compensationDayAutoService.addToCache(compKey);
            }

            // Approved leave requests in the window — CSP encodes them as
            // hard domain-pruning constraints in CspDataBuilder.
            List<LeaveRequest> leaveRequests = leaveRequestRepository.findApprovedInRange(
                    period.getStartDate(), period.getEndDate());

            // Run CSP. Thread the L04 allowed-specialties from AutoGenConfig so
            // the CSP's domain pruning uses the same definition as
            // StaffShiftTypeEligibility / ScheduleQualityScorer — otherwise the
            // search and the scoring would silently disagree on who is eligible
            // for L04 (and the earlier hardcoded "Bac si / Dieu duong" check in
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
                // CSP returned a partial plan under timeout pressure
                log.info("CSP-MRV-FC returned a partial plan for period {} ({} assignments) — Greedy will top up missing slots",
                        period.getId(), cspResult.getScheduleCount());
                return new SchedulingResultWithFairness(cspPartialToSchedules(cspResult, period, requirements, activeStaff),
                        cspResult.getFairnessScore() != null ? cspResult.getFairnessScore() : BigDecimal.ZERO,
                        true);
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
                LocalDate compDate = autoSchedulingService.compensationDateCalculator.calculate(workDate);
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
                            autoSchedulingService.createCompensationDayForAuto(saved);
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
            log.error("CSP-MRV-FC failed for period {}: {}", period.getId(), e.getMessage(), e);
            return new SchedulingResultWithFairness(new ArrayList<>(), BigDecimal.ZERO);
        }
    }

    /**
     * Convert the CSP partial assignment map into transient {@link Schedule}
     * entities that the orchestrator can merge with the Greedy top-up.
     */
    public List<Schedule> cspPartialToSchedules(
            SchedulingResult cspResult,
            SchedulePeriod period,
            List<ShiftRequirement> requirements,
            List<Staff> activeStaff) {

        Set<String> compDays = new HashSet<>();
        // First pass: collect comp days generated by L01 assignments in the
        // partial plan so the second pass can skip them.
        for (Map.Entry<String, String> entry : cspResult.getAssignments().entrySet()) {
            if (!ConflictDetectionService.SHIFT_TYPE_L01.equals(entry.getValue())) continue;
            String[] parts = entry.getKey().split("\\|");
            if (parts.length != 2) continue;
            Integer staffId = Integer.parseInt(parts[0]);
            LocalDate workDate = LocalDate.parse(parts[1]);
            LocalDate compDate = autoSchedulingService.compensationDateCalculator.calculate(workDate);
            if (compDate != null) {
                compDays.add(staffId + "_" + compDate);
            }
        }

        List<Schedule> partial = new ArrayList<>();
        for (Map.Entry<String, String> entry : cspResult.getAssignments().entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            if (parts.length != 2) continue;
            Integer staffId = Integer.parseInt(parts[0]);
            LocalDate workDate = LocalDate.parse(parts[1]);
            String shiftTypeId = entry.getValue();

            if (compDays.contains(staffId + "_" + workDate)) continue;

            Staff staff = activeStaff.stream()
                    .filter(s -> s.getId().equals(staffId))
                    .findFirst().orElse(null);
            if (staff == null) continue;

            ShiftRequirement req = requirements.stream()
                    .filter(r -> r.getWorkDate().equals(workDate)
                            && r.getShiftType().getId().equals(shiftTypeId))
                    .findFirst().orElse(null);
            if (req == null) continue;

            partial.add(Schedule.builder()
                    .period(period)
                    .staff(staff)
                    .shiftType(req.getShiftType())
                    .workDate(workDate)
                    .requirement(req)
                    .hasConflict(false)
                    .build());
        }
        log.info("Converted CSP partial: {} assignments kept ({} were skipped on comp days or unmatched)",
                partial.size(), cspResult.getScheduleCount() - partial.size());
        return partial;
    }

    // ==================== INCREMENTAL RE-SCHEDULE ====================

    /**
     * Re-solve a period using CSP's incremental path when the delta supports it,
     * otherwise fall back to a full CSP solve.
     */
    public AutoScheduleResponse reschedulePeriodIncremental(Integer periodId, ScheduleChange changes, boolean save) {
        autoSchedulingService.inMemoryAssignments.set(new HashMap<>());
        autoSchedulingService.inMemoryCompensationShiftDates.set(new HashSet<>());
        autoSchedulingService.compensationDayAutoService.getAllCompensationShiftDates().set(new HashSet<>());
        autoSchedulingService.swapPriorityStaffIds.set(new HashSet<>());
        try {
            SchedulePeriod period = periodRepository.findById(periodId)
                    .orElseThrow(() -> new com.hospital.scheduler.exception.ResourceNotFoundException("Khong tim thay ky lich voi ID: " + periodId));

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

            Set<Integer> excludedStaffIds = null;

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
                    autoSchedulingService.createCompensationDaysForL01InPeriod(periodId);
                }
                log.info("Reschedule persisted {} schedules for period {}", persisted.size(), periodId);
            }

            Map<LocalDate, List<ShiftRequirement>> reqsByDate = requirements.stream()
                    .collect(Collectors.groupingBy(ShiftRequirement::getWorkDate));
            int totalRequiredSlots = reqsByDate.values().stream().mapToInt(List::size).sum() * 4;
            int coverage = totalRequiredSlots == 0 ? 100
                    : Math.min(100, persisted.size() * 100 / Math.max(1, totalRequiredSlots));
            BigDecimal balanceScore = autoSchedulingService.calculateBalanceScore(persisted,
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
                            ? "Da tai xep lich bang CSP incremental"
                            : "Da tai xep lich bang CSP full solve (delta qua lon)")
                    .periodId(periodId)
                    .algorithmType("CSP_MRV_FC")
                    .coverageRate(BigDecimal.valueOf(coverage))
                    .balanceScore(balanceScore)
                    .totalSchedulesCreated(persisted.size())
                    .schedules(summaries)
                    .executedAt(LocalDateTime.now())
                    .build();
        } finally {
            autoSchedulingService.inMemoryAssignments.remove();
            autoSchedulingService.inMemoryCompensationShiftDates.remove();
            autoSchedulingService.compensationDayAutoService.removeThreadLocal();
            autoSchedulingService.swapPriorityStaffIds.remove();
        }
    }

    /** Re-hydrate a raw {@link SchedulingResult} from the incremental path into Schedule entities. */
    public SchedulingResultWithFairness runCspWithResult(SchedulingResult result, SchedulePeriod period) {
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

    public static List<ShiftRequirementInfo> toRequirementInfos(List<ShiftRequirement> requirements) {
        return requirements.stream()
                .map(r -> new ShiftRequirementInfo(
                        r.getShiftType().getId(),
                        r.getWorkDate(),
                        r.getRequiredStaffCount(),
                        r.getSpecialty() != null ? r.getSpecialty().getId() : null))
                .toList();
    }

    public static int countChanges(ScheduleChange changes) {
        if (changes == null) return 0;
        return changes.getAdded().size() + changes.getRemoved().size()
                + changes.getModified().size() + changes.getAddedLeaves().size()
                + changes.getRemovedLeaves().size();
    }
}
