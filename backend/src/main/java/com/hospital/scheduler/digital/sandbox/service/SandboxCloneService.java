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
import com.hospital.scheduler.repository.ScheduleRepository;
import com.hospital.scheduler.service.ShiftRequirementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for cloning period schedules into sandbox.
 *
 * <p>Design principle: Only clone MUTABLE data (schedules).
 * Reference data (Staff, ShiftType, etc.) is shared read-only.
 *
 * <p>Optimization:
 * <ul>
 *   <li>Bulk insert for performance</li>
 *   <li>Denormalized staff name for faster display</li>
 *   <li>Snapshot at iteration 0 with full state</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SandboxCloneService {

    private final SandboxSessionRepository sessionRepository;
    private final SandboxSnapshotRepository snapshotRepository;
    private final SandboxAssignmentRepository assignmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final ShiftRequirementService shiftRequirementService;

    /**
     * Clone schedules from a period into a new sandbox session.
     *
     * <p>Steps:
     * <ol>
     *   <li>Create session with CLONING status</li>
     *   <li>Clone schedules as sandbox assignments</li>
     *   <li>Create initial snapshot (iteration 0)</li>
     *   <li>Update session to READY status</li>
     * </ol>
     *
     * @param periodId Source period ID
     * @param profileId Profile ID to use
     * @param createdBy User creating the sandbox
     * @param name Optional session name
     * @param ttlHours TTL in hours
     * @return Created sandbox session
     */
    @Transactional
    public SandboxSession clonePeriodToSandbox(
            Integer periodId,
            Long profileId,
            String createdBy,
            String name,
            Integer ttlHours
    ) {
        log.info("Cloning period {} to sandbox for user {}", periodId, createdBy);

        // 1. Create session
        SandboxSession session = SandboxSession.builder()
                .sessionKey(UUID.randomUUID().toString())
                .name(name != null ? name : "Sandbox " + LocalDateTime.now().toString().substring(0, 16))
                .status(SandboxStatus.CLONING)
                .sourcePeriodId(periodId)
                .profileId(profileId)
                .createdBy(createdBy)
                .expiresAt(LocalDateTime.now().plusHours(ttlHours != null ? ttlHours : 24))
                .ttlHours(ttlHours != null ? ttlHours : 24)
                .build();
        session = sessionRepository.save(session);

        try {
            // 2. Clone schedules
            int clonedCount = cloneSchedules(session, periodId);

            // 3. Create initial snapshot
            createInitialSnapshot(session);

            // 4. Update status
            session.setStatus(SandboxStatus.READY);
            session = sessionRepository.save(session);

            log.info("Cloned {} schedules to sandbox session {}", clonedCount, session.getSessionKey());
            return session;

        } catch (Exception e) {
            log.error("Failed to clone period {} to sandbox", periodId, e);
            session.setStatus(SandboxStatus.FAILED);
            session.setErrorMessage(e.getMessage());
            sessionRepository.save(session);
            throw new RuntimeException("Clone failed: " + e.getMessage(), e);
        }
    }

    /**
     * Clone schedules from a period into sandbox assignments.
     *
     * <p>Only mutable data is cloned:
     * <ul>
     *   <li>Schedule assignments</li>
     *   <li>Slot IDs</li>
     *   <li>Work dates</li>
     *   <li>Shift types</li>
     * </ul>
     *
     * <p>Reference data is NOT cloned (shared read-only):
     * <ul>
     *   <li>Staff info</li>
     *   <li>ShiftType info</li>
     *   <li>Period info</li>
     * </ul>
     */
    private int cloneSchedules(SandboxSession session, Integer periodId) {
        List<Schedule> schedules = scheduleRepository.findByPeriodId(periodId);

        List<SandboxAssignment> assignments = schedules.stream()
                .map(schedule -> toSandboxAssignment(session, schedule))
                .toList();

        assignmentRepository.saveAll(assignments);
        return assignments.size();
    }

    /**
     * Convert Schedule entity to SandboxAssignment.
     */
    private SandboxAssignment toSandboxAssignment(SandboxSession session, Schedule schedule) {
        Integer slotId = schedule.getRequirement() != null
                ? schedule.getRequirement().getId()
                : null;

        String staffName = schedule.getStaff() != null
                ? schedule.getStaff().getFullName()
                : null;

        return SandboxAssignment.builder()
                .session(session)
                .slotId(slotId)
                .workDate(schedule.getWorkDate().atStartOfDay())
                .shiftTypeId(schedule.getShiftType().getId())
                .staffId(schedule.getStaff() != null ? schedule.getStaff().getId() : null)
                .staffName(staffName)
                .isSimulated(false) // From original schedule
                .hasConflict(schedule.getHasConflict())
                .scoreContribution(0.0) // Will be calculated during simulation
                .iterationChanged(0) // Initial state
                .moveType(null) // No move yet
                .build();
    }

    /**
     * Create initial snapshot with full state.
     */
    private void createInitialSnapshot(SandboxSession session) {
        SandboxSnapshot snapshot = SandboxSnapshot.builder()
                .session(session)
                .iteration(0)
                .score(0.0)
                .coverageRate(calculateCoverageRate(session))
                .fairnessCv(0.0)
                .violations(0)
                .moveType(null)
                .accepted(null)
                .isCheckpoint(true) // Always checkpoint at iteration 0
                .build();

        // Store initial state as JSON for replay
        snapshot.setStateJson(buildInitialStateJson(session));

        snapshotRepository.save(snapshot);
        session.setCurrentSnapshotId(snapshot.getId());
    }

    /**
     * Calculate initial coverage rate.
     */
    private double calculateCoverageRate(SandboxSession session) {
        long total = assignmentRepository.countBySession(session);
        long assigned = assignmentRepository.findBySessionAndStaffId(session, null).size();
        // This is a simplified calculation; actual implementation depends on requirements
        return 100.0; // TODO: Calculate based on requirements
    }

    /**
     * Build JSON representation of initial state.
     */
    private String buildInitialStateJson(SandboxSession session) {
        // This would serialize the initial assignment state
        // For now, return a placeholder
        return """
            {
                "type": "initial_state",
                "session": "%s",
                "timestamp": "%s"
            }
            """.formatted(
                session.getSessionKey(),
                LocalDateTime.now().toString()
        );
    }

    /**
     * Get schedules from source period.
     */
    @Transactional(readOnly = true)
    public List<Schedule> getSourceSchedules(Integer periodId) {
        return scheduleRepository.findByPeriodId(periodId);
    }
}
