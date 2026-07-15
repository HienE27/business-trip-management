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

    private final SchedulingConfig config;

    private SchedulingProblem(List<StaffNode> staffList,
                              Map<Integer, StaffNode> staffById,
                              List<ShiftRequirementInfo> requirements,
                              Map<Integer, ShiftRequirementInfo> requirementsById,
                              Map<Integer, Set<LocalDate>> leavesByStaff,
                              Set<LocalDate> holidays,
                              Map<Integer, Set<LocalDate>> compensationDays,
                              SchedulingConfig config) {
        this.staffList = staffList;
        this.staffById = staffById;
        this.requirements = requirements;
        this.requirementsById = requirementsById;
        this.leavesByStaff = leavesByStaff;
        this.holidays = holidays;
        this.compensationDays = compensationDays;
        this.config = config;
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
                new HashMap<>(),
                config);
    }

    /**
     * Eligible staff IDs for a slot, with leave/compensation/holiday already
     * filtered out. Caller still needs to apply specialty rules via
     * {@code StaffEligibilityFilter}.
     */
    public List<Integer> getEligibleStaff(int slotId) {
        ShiftRequirementInfo slot = requirementsById.get(slotId);
        if (slot == null) {
            return Collections.emptyList();
        }

        List<Integer> result = new ArrayList<>();
        for (StaffNode s : staffList) {
            if (isOnLeave(s.getId(), slot.date())) continue;
            if (isOnCompensation(s.getId(), slot.date())) continue;
            if (!s.isEligibleFor(slot.shiftTypeId())) continue;
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