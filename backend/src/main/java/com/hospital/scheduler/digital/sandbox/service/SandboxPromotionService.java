package com.hospital.scheduler.digital.sandbox.service;

import com.hospital.scheduler.digital.sandbox.domain.SandboxStatus;
import com.hospital.scheduler.digital.sandbox.entity.SandboxAssignment;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSession;
import com.hospital.scheduler.digital.sandbox.entity.SandboxSnapshot;
import com.hospital.scheduler.digital.sandbox.repository.SandboxAssignmentRepository;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSessionRepository;
import com.hospital.scheduler.digital.sandbox.repository.SandboxSnapshotRepository;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.repository.StaffRepository;
import com.hospital.scheduler.repository.ShiftTypeRepository;
import com.hospital.scheduler.repository.ShiftRequirementRepository;
import com.hospital.scheduler.repository.SchedulePeriodRepository;
import com.hospital.scheduler.service.AuditHistoryService;
import com.hospital.scheduler.service.ConflictDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for promoting sandbox results to production.
 *
 * <p>Enterprise-grade promotion flow:
 * <pre>
 * 1. Diff - Compare sandbox vs production
 * 2. Validate - Check for conflicts/errors
 * 3. Lock Period - Prevent concurrent modifications
 * 4. Apply - Apply changes to production
 * 5. Audit - Log the promotion
 * </pre>
 *
 * <p>Design principles:
 * <ul>
 *   <li>Always validate before apply</li>
 *   <li>Track diff for rollback</li>
 *   <li>Audit trail for compliance</li>
 *   <li>Atomic transactions</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxPromotionService {

    private final SandboxSessionRepository sessionRepository;
    private final SandboxSnapshotRepository snapshotRepository;
    private final SandboxAssignmentRepository assignmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final StaffRepository staffRepository;
    private final ShiftTypeRepository shiftTypeRepository;
    private final ShiftRequirementRepository requirementRepository;
    private final SchedulePeriodRepository periodRepository;
    private final ConflictDetectionService conflictDetectionService;
    private final AuditHistoryService auditHistoryService;

    // ─── Promotion Flow ─────────────────────────────────────────────────────

    /**
     * Generate diff between sandbox and production.
     */
    @Transactional(readOnly = true)
    public PromotionDiff generateDiff(String sessionKey) {
        SandboxSession session = getOrThrow(sessionKey);
        if (!session.canPromote()) {
            throw new IllegalStateException("Cannot promote session in status: " + session.getStatus());
        }

        Integer periodId = session.getSourcePeriodId();
        List<Schedule> productionSchedules = scheduleRepository.findByPeriodId(periodId);
        List<SandboxAssignment> sandboxAssignments = assignmentRepository.findBySession(session);

        // Build maps for comparison
        Map<String, Schedule> productionBySlot = buildProductionMap(productionSchedules);
        Map<String, SandboxAssignment> sandboxBySlot = buildSandboxMap(sandboxAssignments);

        // Calculate diffs
        List<PromotionDiff.Change> added = new ArrayList<>();
        List<PromotionDiff.Change> removed = new ArrayList<>();
        List<PromotionDiff.Change> modified = new ArrayList<>();

        // Find added and modified
        for (Map.Entry<String, SandboxAssignment> entry : sandboxBySlot.entrySet()) {
            String slotKey = entry.getKey();
            SandboxAssignment sandbox = entry.getValue();

            if (!productionBySlot.containsKey(slotKey)) {
                // Added in sandbox
                added.add(toChange("ADD", sandbox));
            } else {
                // Check if modified
                Schedule production = productionBySlot.get(slotKey);
                if (!Objects.equals(production.getStaff() != null ? production.getStaff().getId() : null,
                        sandbox.getStaffId())) {
                    modified.add(toChange("MODIFY", sandbox));
                }
            }
        }

        // Find removed
        for (Map.Entry<String, Schedule> entry : productionBySlot.entrySet()) {
            String slotKey = entry.getKey();
            if (!sandboxBySlot.containsKey(slotKey)) {
                removed.add(toChange("REMOVE", entry.getValue()));
            }
        }

        return new PromotionDiff(
                sessionKey,
                periodId,
                added,
                removed,
                modified
        );
    }

    /**
     * Validate promotion before applying.
     */
    @Transactional(readOnly = true)
    public PromotionValidation validate(String sessionKey) {
        PromotionDiff diff = generateDiff(sessionKey);
        SandboxSession session = getOrThrow(sessionKey);

        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Check if period is locked
        SchedulePeriod period = periodRepository.findById(session.getSourcePeriodId()).orElse(null);
        if (period != null && "PUBLISHED".equals(period.getStatus())) {
            errors.add("Cannot promote to a published period. Please archive it first.");
        }

        // Check for conflicts in sandbox
        List<SandboxAssignment> assignments = assignmentRepository.findBySession(session);
        long conflicts = assignments.stream().filter(SandboxAssignment::getHasConflict).count();
        if (conflicts > 0) {
            warnings.add("Sandbox contains " + conflicts + " assignments with conflicts");
        }

        // BUGFIX: was N+1 — one existsById per change. Now we collect all distinct
        // staff IDs and call findAllById in a single round-trip.
        List<Integer> distinctStaffIds = diff.getAllChanges().stream()
                .map(PromotionDiff.Change::staffId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<Integer> validStaffIds = distinctStaffIds.isEmpty()
                ? java.util.Collections.emptySet()
                : new HashSet<>(staffRepository.findAllById(distinctStaffIds).stream()
                        .map(Staff::getId)
                        .toList());
        for (PromotionDiff.Change change : diff.getAllChanges()) {
            if (change.staffId() != null && !validStaffIds.contains(change.staffId())) {
                errors.add("Invalid staff ID: " + change.staffId());
            }
        }

        // Check for schedule limits
        if (diff.getTotalChanges() > 100) {
            warnings.add("Large number of changes (" + diff.getTotalChanges() + "). Consider reviewing in detail.");
        }

        boolean isValid = errors.isEmpty();

        return new PromotionValidation(
                sessionKey,
                isValid,
                errors,
                warnings,
                diff
        );
    }

    /**
     * Promote sandbox to production.
     *
     * <p>Steps:
     * <ol>
     *   <li>Lock period</li>
     *   <li>Apply changes</li>
     *   <li>Detect conflicts</li>
     *   <li>Mark session as promoted</li>
     *   <li>Audit log</li>
     * </ol>
     */
    @Transactional
    public PromotionResult promote(String sessionKey, String promotedBy) {
        SandboxSession session = getOrThrow(sessionKey);

        // 1. Validate
        PromotionValidation validation = validate(sessionKey);
        if (!validation.isValid()) {
            throw new IllegalStateException("Promotion validation failed: " + String.join(", ", validation.getErrors()));
        }

        // 2. Get diff
        PromotionDiff diff = validation.getDiff();

        // 3. Lock period (mark as processing)
        Integer periodId = session.getSourcePeriodId();

        // BUGFIX: was N+1 — applyAdd/applyModify/applyRemove each re-loaded
        // Staff, ShiftType, ShiftRequirement, and the period for every change.
        // Now we resolve all distinct IDs up front and hand the per-change
        // methods an in-memory lookup map. With 50 changes that was 150–200
        // SELECTs; it is now 3–4.
        Set<Integer> staffIds = diff.getAllChanges().stream()
                .map(PromotionDiff.Change::staffId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> shiftIds = diff.getAllChanges().stream()
                .map(PromotionDiff.Change::shiftTypeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Set<Integer> slotIds = diff.getAllChanges().stream()
                .map(PromotionDiff.Change::slotId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        SchedulePeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new IllegalArgumentException("Period not found: " + periodId));
        Map<Integer, Staff> staffById = staffRepository.findAllById(staffIds).stream()
                .collect(Collectors.toMap(Staff::getId, s -> s));
        Map<String, ShiftType> shiftById = shiftTypeRepository.findAllById(shiftIds).stream()
                .collect(Collectors.toMap(ShiftType::getId, s -> s));
        Map<Integer, ShiftRequirement> reqById = slotIds.isEmpty()
                ? Map.of()
                : requirementRepository.findAllById(slotIds).stream()
                        .collect(Collectors.toMap(ShiftRequirement::getId, r -> r));

        // 4. Apply changes
        int addedCount = 0, modifiedCount = 0, removedCount = 0;

        try {
            // Add new schedules
            for (PromotionDiff.Change change : diff.getAdded()) {
                applyAdd(period, staffById, shiftById, reqById, change);
                addedCount++;
            }

            // Modify existing schedules
            for (PromotionDiff.Change change : diff.getModified()) {
                applyModify(periodId, staffById, change);
                modifiedCount++;
            }

            // Remove deleted schedules
            for (PromotionDiff.Change change : diff.getRemoved()) {
                applyRemove(periodId, change);
                removedCount++;
            }

            // 5. Detect conflicts
            conflictDetectionService.checkPeriodConflicts(periodId);

            // 6. Mark session as promoted
            session.setStatus(SandboxStatus.PROMOTED);
            sessionRepository.save(session);

            // 7. Audit log
            auditHistoryService.logAction(
                    "SandboxSession",
                    session.getId(),
                    com.hospital.scheduler.entity.AuditHistory.ActionType.UPDATE,
                    null,
                    "Promoted to production",
                    null
            );

            log.info("Promoted sandbox {} to production. Changes: +{}/~{}/-{}",
                    sessionKey, addedCount, modifiedCount, removedCount);

            return new PromotionResult(
                    true,
                    sessionKey,
                    addedCount,
                    modifiedCount,
                    removedCount,
                    null,
                    LocalDateTime.now()
            );

        } catch (Exception e) {
            log.error("Promotion failed for session {}", sessionKey, e);
            session.setStatus(SandboxStatus.FAILED);
            session.setErrorMessage("Promotion failed: " + e.getMessage());
            sessionRepository.save(session);

            return new PromotionResult(
                    false,
                    sessionKey,
                    null,
                    null,
                    null,
                    e.getMessage(),
                    null
            );
        }
    }

    // ─── Apply Changes ──────────────────────────────────────────────────────

    private void applyAdd(SchedulePeriod period,
                          Map<Integer, Staff> staffById,
                          Map<String, ShiftType> shiftById,
                          Map<Integer, ShiftRequirement> reqById,
                          PromotionDiff.Change change) {
        Staff staff = staffById.get(change.staffId());
        if (staff == null) {
            throw new IllegalArgumentException("Staff not found: " + change.staffId());
        }

        ShiftType shiftType = shiftById.get(change.shiftTypeId());
        if (shiftType == null) {
            throw new IllegalArgumentException("ShiftType not found: " + change.shiftTypeId());
        }

        ShiftRequirement requirement = change.slotId() != null ? reqById.get(change.slotId()) : null;

        Schedule schedule = Schedule.builder()
                .period(period)
                .workDate(change.workDate().toLocalDate())
                .staff(staff)
                .shiftType(shiftType)
                .requirement(requirement)
                .hasConflict(false)
                .isPreview(false)
                .build();

        scheduleRepository.save(schedule);
    }

    private void applyModify(Integer periodId,
                             Map<Integer, Staff> staffById,
                             PromotionDiff.Change change) {
        Schedule schedule = scheduleRepository
                .findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(periodId, change.staffId(), change.shiftTypeId(), change.workDate().toLocalDate())
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found for modification"));

        Staff staff = staffById.get(change.staffId());
        if (staff == null) {
            throw new IllegalArgumentException("Staff not found: " + change.staffId());
        }

        schedule.setStaff(staff);
        schedule.setHasConflict(false);
        scheduleRepository.save(schedule);
    }

    private void applyRemove(Integer periodId, PromotionDiff.Change change) {
        Optional<Schedule> schedule = scheduleRepository
                .findByPeriodIdAndStaffIdAndShiftTypeIdAndWorkDate(periodId, null, change.shiftTypeId(), change.workDate().toLocalDate());

        schedule.ifPresent(scheduleRepository::delete);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private Map<String, Schedule> buildProductionMap(List<Schedule> schedules) {
        return schedules.stream()
                .collect(Collectors.toMap(
                        s -> buildSlotKey(s),
                        s -> s,
                        (a, b) -> a
                ));
    }

    private Map<String, SandboxAssignment> buildSandboxMap(List<SandboxAssignment> assignments) {
        return assignments.stream()
                .filter(a -> a.getStaffId() != null)
                .collect(Collectors.toMap(
                        a -> buildSlotKey(a),
                        a -> a,
                        (a, b) -> a
                ));
    }

    private String buildSlotKey(Schedule s) {
        return s.getWorkDate() + "_" + s.getShiftType().getId();
    }

    private String buildSlotKey(SandboxAssignment a) {
        return a.getWorkDate().toLocalDate() + "_" + a.getShiftTypeId();
    }

    private PromotionDiff.Change toChange(String type, SandboxAssignment a) {
        return new PromotionDiff.Change(
                type,
                a.getSlotId(),
                a.getWorkDate(),
                a.getShiftTypeId(),
                a.getStaffId(),
                a.getStaffName()
        );
    }

    private PromotionDiff.Change toChange(String type, Schedule s) {
        return new PromotionDiff.Change(
                type,
                s.getRequirement() != null ? s.getRequirement().getId() : null,
                s.getWorkDate().atStartOfDay(),
                s.getShiftType().getId(),
                s.getStaff() != null ? s.getStaff().getId() : null,
                s.getStaff() != null ? s.getStaff().getFullName() : null
        );
    }

    private SandboxSession getOrThrow(String sessionKey) {
        return sessionRepository.findBySessionKey(sessionKey)
                .orElseThrow(() -> new IllegalArgumentException("Sandbox session not found: " + sessionKey));
    }

    // ─── DTOs ─────────────────────────────────────────────────────────────

    @lombok.Getter
    public static class PromotionDiff {
        private final String sessionKey;
        private final Integer periodId;
        private final List<Change> added;
        private final List<Change> removed;
        private final List<Change> modified;

        public PromotionDiff(String sessionKey, Integer periodId, List<Change> added, List<Change> removed, List<Change> modified) {
            this.sessionKey = sessionKey;
            this.periodId = periodId;
            this.added = added;
            this.removed = removed;
            this.modified = modified;
        }

        public int getTotalChanges() {
            return added.size() + removed.size() + modified.size();
        }

        public List<Change> getAllChanges() {
            List<Change> all = new ArrayList<>();
            all.addAll(added);
            all.addAll(removed);
            all.addAll(modified);
            return all;
        }

        public record Change(
                String type,
                Integer slotId,
                LocalDateTime workDate,
                String shiftTypeId,
                Integer staffId,
                String staffName
        ) {}
    }

    @lombok.Getter
    public static class PromotionValidation {
        private final String sessionKey;
        private final boolean isValid;
        private final List<String> errors;
        private final List<String> warnings;
        private final PromotionDiff diff;

        public PromotionValidation(String sessionKey, boolean isValid, List<String> errors, List<String> warnings, PromotionDiff diff) {
            this.sessionKey = sessionKey;
            this.isValid = isValid;
            this.errors = errors;
            this.warnings = warnings;
            this.diff = diff;
        }
    }

    @lombok.Getter
    public static class PromotionResult {
        private final boolean success;
        private final String sessionKey;
        private final Integer added;
        private final Integer modified;
        private final Integer removed;
        private final String error;
        private final LocalDateTime promotedAt;

        public PromotionResult(boolean success, String sessionKey, Integer added, Integer modified, Integer removed, String error, LocalDateTime promotedAt) {
            this.success = success;
            this.sessionKey = sessionKey;
            this.added = added;
            this.modified = modified;
            this.removed = removed;
            this.error = error;
            this.promotedAt = promotedAt;
        }
    }
}
