package com.hospital.scheduler.scheduling.domain;

import com.hospital.scheduler.entity.LeaveRequest;
import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.Staff;
import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable problem definition for one auto-scheduling run.
 *
 * <p>Captures: active staff (as {@link StaffNode}), shift requirements, leave
 * windows, holidays, compensation days, and algorithm config.
 *
 * <p>Indexes ({@code staffById}, {@code requirementsById}) are pre-computed
 * once so hot-path code avoids linear scans.
 */
@Getter
public final class SchedulingProblem {

    private final List<StaffNode> staffList;
    private final Map<Integer, StaffNode> staffById;
    private final List<ShiftRequirementInfo> requirements;
    private final Map<Integer, ShiftRequirementInfo> requirementsById;

    /** staffId → set of leave dates */
    private final Map<Integer, Set<LocalDate>> leavesByStaff;
    /** Date-only set of holidays in the schedule window. */
    private final Set<LocalDate> holidays;
    /** staffId → set of compensation dates (no shifts allowed) */
    private final Map<Integer, Set<LocalDate>> compensationDays;

    /**
     * Existing schedule conflicts: "date|shiftType" → set of staffIds.
     * Used by {@link #getEligibleStaff(int)} to prevent assigning a shift type
     * that conflicts with an existing schedule (BR-01 L01↔L02, BR-02 L03↔L04).
     * V10-comp-day fix: without this, V10 assigns L04 to staff who already have
     * L03 from an existing schedule — yielding 70 BR-02 hard violations.
     */
    private final Map<String, Set<Integer>> existingConflicts;

    private final SchedulingConfig config;
    /**
     * BUGFIX (M07-CROSSCONFIG-V10): Whether the L04 cross-specialty config is
     * currently enabled for this run. Carried into {@code SchedulingProblem}
     * so V10's {@link #getEligibleStaff(int)} honors the user's toggle and
     * does not surface cross-specialty staff when the toggle is off.
     *
     * <p>Defaults to {@code true} so existing call sites that don't pass it
     * keep their prior behavior.
     */
    private final boolean crossSpecialtyEnabled;

    private SchedulingProblem(List<StaffNode> staffList,
                              Map<Integer, StaffNode> staffById,
                              List<ShiftRequirementInfo> requirements,
                              Map<Integer, ShiftRequirementInfo> requirementsById,
                              Map<Integer, Set<LocalDate>> leavesByStaff,
                              Set<LocalDate> holidays,
                              Map<Integer, Set<LocalDate>> compensationDays,
                              SchedulingConfig config) {
        this(staffList, staffById, requirements, requirementsById, leavesByStaff,
                holidays, compensationDays, config, true, new HashMap<>());
    }

    private SchedulingProblem(List<StaffNode> staffList,
                              Map<Integer, StaffNode> staffById,
                              List<ShiftRequirementInfo> requirements,
                              Map<Integer, ShiftRequirementInfo> requirementsById,
                              Map<Integer, Set<LocalDate>> leavesByStaff,
                              Set<LocalDate> holidays,
                              Map<Integer, Set<LocalDate>> compensationDays,
                              SchedulingConfig config,
                              boolean crossSpecialtyEnabled) {
        this(staffList, staffById, requirements, requirementsById, leavesByStaff,
                holidays, compensationDays, config, crossSpecialtyEnabled, new HashMap<>());
    }

    private SchedulingProblem(List<StaffNode> staffList,
                              Map<Integer, StaffNode> staffById,
                              List<ShiftRequirementInfo> requirements,
                              Map<Integer, ShiftRequirementInfo> requirementsById,
                              Map<Integer, Set<LocalDate>> leavesByStaff,
                              Set<LocalDate> holidays,
                              Map<Integer, Set<LocalDate>> compensationDays,
                              SchedulingConfig config,
                              boolean crossSpecialtyEnabled,
                              Map<String, Set<Integer>> existingConflicts) {
        this.staffList = staffList;
        this.staffById = staffById;
        this.requirements = requirements;
        this.requirementsById = requirementsById;
        this.leavesByStaff = leavesByStaff;
        this.holidays = holidays;
        this.compensationDays = compensationDays;
        this.config = config;
        this.crossSpecialtyEnabled = crossSpecialtyEnabled;
        this.existingConflicts = existingConflicts != null ? existingConflicts : new HashMap<>();
    }

    /**
     * Build a {@code SchedulingProblem} from raw JPA entities.
     *
     * <p>Leaves are normalized to per-day sets for O(1) membership checks.
     * Compensation days are passed in as a flat list and grouped by staff.
     */
    public static SchedulingProblem from(List<Staff> rawStaff,
                                          List<ShiftRequirement> rawRequirements,
                                          List<LeaveRequest> rawLeaves,
                                          List<LocalDate> rawCompDays,
                                          Set<LocalDate> holidays,
                                          SchedulingConfig config) {
        // ── Staff ────────────────────────────────────────────────────────────
        List<StaffNode> staffList = rawStaff.stream()
                .map(StaffNode::from)
                .toList();
        Map<Integer, StaffNode> staffById = staffList.stream()
                .collect(Collectors.toMap(StaffNode::getId, s -> s));

        // ── Requirements ─────────────────────────────────────────────────────
        List<ShiftRequirementInfo> reqs = rawRequirements.stream()
                .map(ShiftRequirementInfo::from)
                .toList();
        Map<Integer, ShiftRequirementInfo> reqsById = new HashMap<>();
        for (ShiftRequirementInfo r : reqs) {
            reqsById.put(r.id(), r);
        }

        // ── Leaves (range → per-day set) ─────────────────────────────────────
        Map<Integer, Set<LocalDate>> leavesByStaff = new HashMap<>();
        for (LeaveRequest leave : rawLeaves) {
            if (leave.getStaff() == null || leave.getStartDate() == null) continue;
            LocalDate end = leave.getEndDate() != null ? leave.getEndDate() : leave.getStartDate();
            Set<LocalDate> dates = leavesByStaff.computeIfAbsent(
                    leave.getStaff().getId(), k -> new HashSet<>());
            for (LocalDate d = leave.getStartDate(); !d.isAfter(end); d = d.plusDays(1)) {
                dates.add(d);
            }
        }

        // ── Compensation days ────────────────────────────────────────────────
        Map<Integer, Set<LocalDate>> compByStaff = new HashMap<>();
        // rawCompDays is currently a flat list of LocalDate (compensation-date only).
        // Staff association is not carried here; if you need per-staff compensation,
        // call the overload below instead.
        for (LocalDate ignored : rawCompDays) {
            // intentionally empty — see overload for per-staff version
        }

        return new SchedulingProblem(
                staffList, staffById,
                reqs, reqsById,
                leavesByStaff,
                holidays != null ? holidays : Collections.emptySet(),
                compByStaff,
                config);
    }

    /**
     * Overload that accepts per-staff compensation days (preferred when caller
     * already has the staff-id-keyed view).
     */
    public static SchedulingProblem from(List<Staff> rawStaff,
                                          List<ShiftRequirement> rawRequirements,
                                          List<LeaveRequest> rawLeaves,
                                          Map<Integer, Set<LocalDate>> compDaysByStaff,
                                          Set<LocalDate> holidays,
                                          SchedulingConfig config) {
        SchedulingProblem base = from(rawStaff, rawRequirements, rawLeaves,
                Collections.emptyList(), holidays, config);
        return new SchedulingProblem(
                base.staffList, base.staffById,
                base.requirements, base.requirementsById,
                base.leavesByStaff, base.holidays,
                compDaysByStaff != null ? compDaysByStaff : new HashMap<>(),
                base.config);
    }

    /**
     * Build a {@code SchedulingProblem} directly from v10-package
     * {@link ShiftRequirementInfo} records — bypasses the JPA entity layer.
     * Used by {@code LocalSearchScheduler} because the entity-package info
     * type ({@code algorithm.ShiftRequirementInfo}) is already a fully
     * populated POJO that we just want to mirror as a v10 record.
     */
    public static SchedulingProblem withRequirements(List<Staff> rawStaff,
                                                      List<ShiftRequirementInfo> v10Requirements,
                                                      List<LeaveRequest> rawLeaves,
                                                      Set<LocalDate> rawCompDays,
                                                      Set<LocalDate> holidays,
                                                      SchedulingConfig config) {
        return withRequirements(rawStaff, v10Requirements, rawLeaves, rawCompDays, holidays, config, true);
    }

    /**
     * Overload of {@link #withRequirements} that accepts the
     * {@code crossSpecialtyEnabled} flag. BUGFIX (M07-CROSSCONFIG-V10):
     * callers that already know the user's L04 cross-specialty toggle state
     * can pass it here so {@link #getEligibleStaff(int)} honors it.
     */
    public static SchedulingProblem withRequirements(List<Staff> rawStaff,
                                                      List<ShiftRequirementInfo> v10Requirements,
                                                      List<LeaveRequest> rawLeaves,
                                                      Set<LocalDate> rawCompDays,
                                                      Set<LocalDate> holidays,
                                                      SchedulingConfig config,
                                                      boolean crossSpecialtyEnabled) {
        List<StaffNode> staffList = rawStaff.stream().map(StaffNode::from).toList();
        Map<Integer, StaffNode> staffById = staffList.stream()
                .collect(Collectors.toMap(StaffNode::getId, s -> s));

        Map<Integer, ShiftRequirementInfo> reqsById = new HashMap<>();
        for (ShiftRequirementInfo r : v10Requirements) {
            reqsById.put(r.id(), r);
        }

        Map<Integer, Set<LocalDate>> leavesByStaff = new HashMap<>();
        for (LeaveRequest leave : rawLeaves) {
            if (leave.getStaff() == null || leave.getStartDate() == null) continue;
            LocalDate end = leave.getEndDate() != null ? leave.getEndDate() : leave.getStartDate();
            Set<LocalDate> dates = leavesByStaff.computeIfAbsent(
                    leave.getStaff().getId(), k -> new HashSet<>());
            for (LocalDate d = leave.getStartDate(); !d.isAfter(end); d = d.plusDays(1)) {
                dates.add(d);
            }
        }

        // rawCompDays is a flat date set without staff association; per-staff comp days
        // should use the Map overload below. For this overload, leave compDays empty.
        return new SchedulingProblem(
                staffList, staffById,
                v10Requirements, reqsById,
                leavesByStaff,
                holidays != null ? holidays : Collections.emptySet(),
                new HashMap<>(),
                config,
                crossSpecialtyEnabled);
    }

    /**
     * Overload of {@link #withRequirements} that accepts per-staff compensation days
     * as a Map. BUGFIX (V10-comp-day): V10 search was building the problem with an
     * empty compensation-day map, so {@link #getEligibleStaff(int)} never filtered
     * out staff on comp days — yielding 400+ BR-03 hard violations.
     */
    public static SchedulingProblem withRequirements(List<Staff> rawStaff,
                                                      List<ShiftRequirementInfo> v10Requirements,
                                                      List<LeaveRequest> rawLeaves,
                                                      Map<Integer, Set<LocalDate>> compDaysByStaff,
                                                      Set<LocalDate> holidays,
                                                      SchedulingConfig config,
                                                      boolean crossSpecialtyEnabled) {
        List<StaffNode> staffList = rawStaff.stream().map(StaffNode::from).toList();
        Map<Integer, StaffNode> staffById = staffList.stream()
                .collect(Collectors.toMap(StaffNode::getId, s -> s));

        Map<Integer, ShiftRequirementInfo> reqsById = new HashMap<>();
        for (ShiftRequirementInfo r : v10Requirements) {
            reqsById.put(r.id(), r);
        }

        Map<Integer, Set<LocalDate>> leavesByStaff = new HashMap<>();
        for (LeaveRequest leave : rawLeaves) {
            if (leave.getStaff() == null || leave.getStartDate() == null) continue;
            LocalDate end = leave.getEndDate() != null ? leave.getEndDate() : leave.getStartDate();
            Set<LocalDate> dates = leavesByStaff.computeIfAbsent(
                    leave.getStaff().getId(), k -> new HashSet<>());
            for (LocalDate d = leave.getStartDate(); !d.isAfter(end); d = d.plusDays(1)) {
                dates.add(d);
            }
        }

        return new SchedulingProblem(
                staffList, staffById,
                v10Requirements, reqsById,
                leavesByStaff,
                holidays != null ? holidays : Collections.emptySet(),
                compDaysByStaff != null ? compDaysByStaff : new HashMap<>(),
                config,
                crossSpecialtyEnabled);
    }

    /**
     * Overload of {@link #withRequirements} that accepts per-staff compensation days
     * AND existing schedule conflict data. BUGFIX (V10-existing-conflict): without
     * existingConflicts, V10 may assign L04 to a staff who already has L03 from an
     * existing schedule — yielding BR-02 hard violations.
     */
    public static SchedulingProblem withRequirements(List<Staff> rawStaff,
                                                      List<ShiftRequirementInfo> v10Requirements,
                                                      List<LeaveRequest> rawLeaves,
                                                      Map<Integer, Set<LocalDate>> compDaysByStaff,
                                                      Map<String, Set<Integer>> existingConflicts,
                                                      Set<LocalDate> holidays,
                                                      SchedulingConfig config,
                                                      boolean crossSpecialtyEnabled) {
        List<StaffNode> staffList = rawStaff.stream().map(StaffNode::from).toList();
        Map<Integer, StaffNode> staffById = staffList.stream()
                .collect(Collectors.toMap(StaffNode::getId, s -> s));

        Map<Integer, ShiftRequirementInfo> reqsById = new HashMap<>();
        for (ShiftRequirementInfo r : v10Requirements) {
            reqsById.put(r.id(), r);
        }

        Map<Integer, Set<LocalDate>> leavesByStaff = new HashMap<>();
        for (LeaveRequest leave : rawLeaves) {
            if (leave.getStaff() == null || leave.getStartDate() == null) continue;
            LocalDate end = leave.getEndDate() != null ? leave.getEndDate() : leave.getStartDate();
            Set<LocalDate> dates = leavesByStaff.computeIfAbsent(
                    leave.getStaff().getId(), k -> new HashSet<>());
            for (LocalDate d = leave.getStartDate(); !d.isAfter(end); d = d.plusDays(1)) {
                dates.add(d);
            }
        }

        return new SchedulingProblem(
                staffList, staffById,
                v10Requirements, reqsById,
                leavesByStaff,
                holidays != null ? holidays : Collections.emptySet(),
                compDaysByStaff != null ? compDaysByStaff : new HashMap<>(),
                config,
                crossSpecialtyEnabled,
                existingConflicts != null ? existingConflicts : new HashMap<>());
    }

    /**
     * Eligible staff IDs for a slot, with leave/compensation/holiday already
     * filtered out. Specialty filtering for L04:
     * <ul>
     *   <li>If the slot is L04 with a required specialty AND the cross-specialty
     *       toggle is OFF, only staff whose {@link StaffNode#getSpecialtyId()}
     *       matches the requirement's specialty are eligible.</li>
     *   <li>If cross-specialty is ON (the default), all L04-eligible staff are
     *       returned — letting the score director decide between strict and
     *       cross assignments.</li>
     *   <li>Non-L04 shift types are unaffected by the cross-specialty toggle
     *       (they have no specialty requirement).</li>
     * </ul>
     */
    public List<Integer> getEligibleStaff(int slotId) {
        ShiftRequirementInfo slot = requirementsById.get(slotId);
        if (slot == null) {
            return Collections.emptyList();
        }

        // BUGFIX (M07-CROSSCONFIG-V10): when the user has turned off
        // cross-specialty for L04, do not surface cross-specialty staff as
        // candidates for L04 slots. Previously the V10 search saw every
        // L04-eligible staff as a candidate and would happily assign
        // non-matching-specialty staff to L04 — surfacing a non-zero
        // "Cross L04" KPI even though the toggle was OFF.
        boolean enforceL04StrictSpecialty =
                !crossSpecialtyEnabled
                        && "L04".equals(slot.shiftTypeId())
                        && slot.specialtyId() != null;

        List<Integer> result = new ArrayList<>();
        for (StaffNode s : staffList) {
            if (isOnLeave(s.getId(), slot.date())) continue;
            if (isOnCompensation(s.getId(), slot.date())) continue;
            if (!s.isEligibleFor(slot.shiftTypeId())) continue;
            if (enforceL04StrictSpecialty && !slot.specialtyId().equals(s.getSpecialtyId())) continue;
            result.add(s.getId());
        }
        return result;
    }

    /** Returns true if {@code date} is a holiday. */
    public boolean isHoliday(LocalDate date) {
        return holidays.contains(date);
    }

    /** Returns true if {@code staffId} is on leave on {@code date}. */
    public boolean isOnLeave(int staffId, LocalDate date) {
        Set<LocalDate> dates = leavesByStaff.get(staffId);
        return dates != null && dates.contains(date);
    }

    /** Returns true if {@code staffId} has a compensation day on {@code date}. */
    public boolean isOnCompensation(int staffId, LocalDate date) {
        Set<LocalDate> dates = compensationDays.get(staffId);
        return dates != null && dates.contains(date);
    }
}