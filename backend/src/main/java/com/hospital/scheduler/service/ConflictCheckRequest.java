package com.hospital.scheduler.service;

import java.time.LocalDate;

/**
 * Parameter object for single-schedule conflict checks.
 *
 * <p>Replaces the 4-7 parameter overloads of
 * {@code ConflictDetectionService.validateAndThrow / detectAllConflicts / hasAnyConflict}
 * so callers compose the inputs once and pass the typed request instead.</p>
 *
 * <p>Added in SERVICE_AUDIT.md P2. The legacy overloads still exist for backward
 * compatibility and existing tests; new callers should prefer the
 * {@code (ConflictCheckRequest)} overloads. See
 * {@link ConflictDetectionService#validateAndThrow(ConflictCheckRequest)}.</p>
 *
 * <p>Field semantics mirror the original parameters:</p>
 * <ul>
 *   <li>{@code staffId} — staff being scheduled (required)</li>
 *   <li>{@code workDate} — date of the shift (required)</li>
 *   <li>{@code shiftTypeId} — e.g. "L01", "L02" (required)</li>
 *   <li>{@code excludeScheduleId} — schedule id to ignore (used for updates —
 *       the schedule being updated shouldn't count against itself)</li>
 *   <li>{@code periodId} — optional period scope; passed for richer conflict
 *       messages and back-to-back L01 detection</li>
 *   <li>{@code skipCompensationDay} — when true, a compensation day on
 *       {@code workDate} does NOT count as a conflict. Used by leave/auto-schedule
 *       paths that already compensate manually.</li>
 *   <li>{@code skipShiftTypeConflict} — when true, same-day L01↔L02 / L03↔L04
 *       conflicts are ignored. Used by preview paths where the user wants to
 *       see all options first.</li>
 * </ul>
 */
public record ConflictCheckRequest(
        Integer staffId,
        LocalDate workDate,
        String shiftTypeId,
        Integer excludeScheduleId,
        Integer periodId,
        boolean skipCompensationDay,
        boolean skipShiftTypeConflict
) {

    /**
     * Most common shape — single-schedule check, no flags, no period scope.
     */
    public static ConflictCheckRequest of(Integer staffId, LocalDate workDate, String shiftTypeId) {
        return new ConflictCheckRequest(staffId, workDate, shiftTypeId, null, null, false, false);
    }

    /**
     * Update path — exclude the schedule being modified so it doesn't conflict
     * with itself.
     */
    public static ConflictCheckRequest forUpdate(Integer staffId, LocalDate workDate,
                                                 String shiftTypeId, Integer excludeScheduleId,
                                                 Integer periodId) {
        return new ConflictCheckRequest(staffId, workDate, shiftTypeId,
                excludeScheduleId, periodId, false, false);
    }

    /**
     * Preview / human-reassignment path — comp day and shift-type conflicts are
     * bypassed so the user sees every option before they decide.
     */
    public static ConflictCheckRequest forPreview(Integer staffId, LocalDate workDate,
                                                  String shiftTypeId, Integer periodId) {
        return new ConflictCheckRequest(staffId, workDate, shiftTypeId, null,
                periodId, true, true);
    }

    /**
     * Auto-schedule path — comp day conflicts are skipped because the scheduler
     * has its own compensation-day tracking. Shift-type conflicts remain because
     * auto-scheduling must still respect L01↔L02.
     */
    public static ConflictCheckRequest forAutoSchedule(Integer staffId, LocalDate workDate,
                                                      String shiftTypeId, Integer periodId) {
        return new ConflictCheckRequest(staffId, workDate, shiftTypeId, null,
                periodId, true, false);
    }
}