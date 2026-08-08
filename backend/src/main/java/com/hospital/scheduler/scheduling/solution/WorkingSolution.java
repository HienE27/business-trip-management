package com.hospital.scheduler.scheduling.solution;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable solution being optimized.
 *
 * <p>Holds:
 * <ul>
 *   <li>{@link #assignmentsBySlot} — slotId → {@link MutableAssignment}</li>
 *   <li>{@link #assignments} — insertion-ordered list (used to enumerate assignments)</li>
 *   <li>Inverse indices for fast lookup (staff → slots assigned, etc.)</li>
 * </ul>
 *
 * <p>Mutation methods ({@link #assign}, {@link #unassign}, {@link #swap}) are
 * the canonical entry points. The statistics hub is updated by the search
 * loop AFTER each mutation, NOT from inside this class — keeping the model
 * passive avoids double-bookkeeping.
 */
@Getter
public class WorkingSolution {

    private final SchedulingConfig config;
    private final SolutionDescriptor descriptor;
    private final SchedulingProblem problem;

    /** slotId → MutableAssignment (assigned OR sentinel for empty slot). */
    private final Map<Integer, MutableAssignment> assignmentsBySlot = new HashMap<>();

    /** Insertion-ordered list of all slot assignments (mirrors assignmentsBySlot). */
    private final List<MutableAssignment> assignments = new ArrayList<>();

    /** staffId → list of slots assigned to that staff. */
    private final Map<Integer, List<Integer>> slotsByStaff = new HashMap<>();

    public WorkingSolution(SchedulingConfig config, SolutionDescriptor descriptor) {
        this.config = config;
        this.descriptor = descriptor;
        this.problem = descriptor.getProblem();
    }

    // ── Mutation operations ─────────────────────────────────────────────────

    /**
     * Assign {@code staffId} to {@code slotId}. Replaces any prior assignment.
     * Caller MUST call {@code IncrementalStatisticsHub.apply(move, this)} after.
     */
    public void assign(int slotId, int staffId) {
        if (staffId <= 0) {
            throw new IllegalArgumentException("staffId must be positive, got " + staffId);
        }
        MutableAssignment existing = assignmentsBySlot.get(slotId);
        if (existing == null) {
            // New slot — load requirement data and create assignment
            ShiftRequirementInfo req = problem.getRequirementsById().get(slotId);
            if (req == null) {
                throw new IllegalArgumentException("Unknown slotId: " + slotId);
            }
            MutableAssignment ma = new MutableAssignment();
            ma.slotId = slotId;
            ma.staffId = staffId;
            ma.date = req.date();
            ma.shiftTypeId = req.shiftTypeId();
            ma.hours = hoursFor(req.shiftTypeId());
            ma.isWeekend = ma.date != null
                    && (ma.date.getDayOfWeek().getValue() >= 6);
            ma.isHoliday = problem.isHoliday(ma.date);
            assignmentsBySlot.put(slotId, ma);
            assignments.add(ma);
        } else {
            // Reassignment — update staffId and inverse index
            int oldStaffId = existing.staffId;
            if (oldStaffId == staffId) return;
            if (oldStaffId > 0) {
                removeFromStaffIndex(oldStaffId, slotId);
            }
            existing.staffId = staffId;
        }
        slotsByStaff.computeIfAbsent(staffId, k -> new ArrayList<>()).add(slotId);
    }

    /** Remove any staff from {@code slotId}. */
    public void unassign(int slotId) {
        MutableAssignment existing = assignmentsBySlot.get(slotId);
        if (existing == null || existing.staffId <= 0) {
            return; // already unassigned
        }
        removeFromStaffIndex(existing.staffId, slotId);
        existing.staffId = -1;
    }

    /**
     * Swap the staff between two slots. If either slot is unassigned, the
     * unassigned side becomes -1 (no swap partner).
     */
    public void swap(int slotA, int slotB) {
        if (slotA == slotB) return;
        int staffA = getAssignedStaff(slotA);
        int staffB = getAssignedStaff(slotB);
        if (staffA > 0) {
            unassign(slotA);
            assign(slotA, staffB);
        }
        if (staffB > 0 && staffA > 0) {
            assign(slotB, staffA);
        } else if (staffB > 0) {
            assign(slotB, staffA);
        }
    }

    // ── Read accessors ──────────────────────────────────────────────────────

    /** Staff id assigned to {@code slotId}, or -1 if unassigned. */
    public int getAssignedStaff(int slotId) {
        MutableAssignment a = assignmentsBySlot.get(slotId);
        return a != null ? a.staffId : -1;
    }

    /** Returns the {@link MutableAssignment} for {@code slotId}, or null. */
    public MutableAssignment getAssignment(int slotId) {
        return assignmentsBySlot.get(slotId);
    }

    /** Number of slots currently assigned to {@code staffId}. */
    public int getShiftCount(int staffId) {
        List<Integer> slots = slotsByStaff.get(staffId);
        return slots != null ? slots.size() : 0;
    }

    /** Number of {@code shiftType} slots currently assigned to {@code staffId}. */
    public int getShiftCountOfType(int staffId, String shiftType) {
        List<Integer> slots = slotsByStaff.get(staffId);
        if (slots == null || slots.isEmpty()) return 0;
        int count = 0;
        for (int slotId : slots) {
            MutableAssignment a = assignmentsBySlot.get(slotId);
            if (a != null && shiftType.equals(a.shiftTypeId)) count++;
        }
        return count;
    }

    /**
     * Total hours currently scheduled for {@code staffId}. L01 = 24h (overnight
     * duty), L02/L03/L04 = 4h (in-clinic shift). Used by the hours-based cap
     * check in {@code LocalSearchScheduler.buildInitialSolution} so L02/L03/L04
     * don't starve when a staff hits a shift-count cap after a few L01s
     * (e.g. cap=20 shifts but 4 L01s already consume 96h).
     */
    public int getTotalHours(int staffId) {
        int hours = 0;
        Integer l01 = getShiftCountOfType(staffId, "L01");
        Integer l02 = getShiftCountOfType(staffId, "L02");
        Integer l03 = getShiftCountOfType(staffId, "L03");
        Integer l04 = getShiftCountOfType(staffId, "L04");
        hours += l01 * 24 + (l02 + l03 + l04) * 4;
        return hours;
    }

    /**
     * BUGFIX (M08-BALANCE-V10): per-staff MIX deviation for the priority types
     * L01/L02/L03 — sum over types and staff of |count(staff, type) − avg(type)|,
     * where avg(type) = total(type) / staffCount. Lower = every staff carries a
     * similar L01:L02:L03 mix (doc M07-F01 "phân bổ đều số ngày cho 20 nhân
     * sự", M07-F02 "số ngày trực đều nhau"). L04 is deliberately excluded —
     * it is the residual buffer type filled after L01/L02/L03 per M07-B3, so
     * its spread is feasibility-driven, not a fairness target.
     *
     * <p>Replaces the earlier balanceGap() (per-type max−min), which balanced
     * each type's range independently but allowed compensating mixes — the
     * preview showed corr(L01,L02) = −0.67 (staff with more L01 had fewer L02).
     *
     * <p>Used by the search acceptance rule as a soft tiebreak: a move that
     * keeps hard=0 and coverage unchanged but narrows this deviation is
     * "improving". O(types × staff × slots-per-staff) — trivial.
     */
    public double mixDeviation() {
        var staffList = problem.getStaffList();
        int n = staffList.size();
        if (n == 0) return 0;
        double dev = 0;
        for (String type : new String[]{"L01", "L02", "L03"}) {
            int total = 0;
            for (var s : staffList) {
                total += getShiftCountOfType(s.getId(), type);
            }
            double avg = (double) total / n;
            for (var s : staffList) {
                dev += Math.abs(getShiftCountOfType(s.getId(), type) - avg);
            }
        }
        return dev;
    }

    /**
     * BUGFIX (M08-COMPDAY-V10): true if {@code date} is a compensation day
     * derived from an L01 slot assigned to {@code staffId} in THIS solution.
     * Lets the greedy, the move selector and the BR-03 constraint block shifts
     * on comp days the moment the L01 is placed — the previous code only knew
     * about comp days that existed in the DB before the run.
     */
    public boolean isOnDerivedCompDay(int staffId, LocalDate date) {
        if (date == null) return false;
        List<Integer> slots = slotsByStaff.get(staffId);
        if (slots == null || slots.isEmpty()) return false;
        for (int slotId : slots) {
            MutableAssignment a = assignmentsBySlot.get(slotId);
            if (a == null || !"L01".equals(a.shiftTypeId) || a.date == null) continue;
            LocalDate comp = problem.compDayOf(a.date);
            if (comp != null && comp.equals(date)) return true;
        }
        return false;
    }

    /** All slot ids assigned to {@code staffId}. */
    public List<Integer> getSlotsAssignedTo(int staffId) {
        return slotsByStaff.getOrDefault(staffId, List.of());
    }

    /**
     * True if {@code staffId} currently holds ANY assignment on {@code date}.
     * Used by the L01 reverse-comp-day check: placing L01 on a duty date whose
     * compensation day falls on a date the staff already works would silently
     * turn that existing assignment into a BR-03 violation.
     */
    public boolean hasAssignmentOnDate(int staffId, LocalDate date) {
        if (date == null) return false;
        List<Integer> slots = slotsByStaff.get(staffId);
        if (slots == null || slots.isEmpty()) return false;
        for (int slotId : slots) {
            MutableAssignment a = assignmentsBySlot.get(slotId);
            if (a != null && a.staffId > 0 && date.equals(a.date)) return true;
        }
        return false;
    }

    /** True if {@code staffId} holds an assignment of {@code shiftTypeId} on {@code date}. */
    public boolean hasShiftOnDate(int staffId, String shiftTypeId, LocalDate date) {
        if (date == null || shiftTypeId == null) return false;
        List<Integer> slots = slotsByStaff.get(staffId);
        if (slots == null || slots.isEmpty()) return false;
        for (int slotId : slots) {
            MutableAssignment a = assignmentsBySlot.get(slotId);
            if (a != null && a.staffId > 0 && shiftTypeId.equals(a.shiftTypeId) && date.equals(a.date)) return true;
        }
        return false;
    }

    /**
     * Coverage as a fraction in [0, 1]. Equals
     * {@code (assigned slot count) / (total slot count)}.
     */
    public double getCoverage() {
        if (assignments.isEmpty()) return 0.0;
        int assigned = 0;
        for (MutableAssignment a : assignments) {
            if (a.staffId > 0) assigned++;
        }
        return (double) assigned / assignments.size();
    }

    // ── Factory ─────────────────────────────────────────────────────────────

    /**
     * Build an empty {@code WorkingSolution} from a problem descriptor.
     * All slots start unassigned.
     */
    public static WorkingSolution fromProblem(SchedulingConfig config,
                                              SolutionDescriptor descriptor) {
        WorkingSolution sol = new WorkingSolution(config, descriptor);
        for (ShiftRequirementInfo req : descriptor.getProblem().getRequirements()) {
            MutableAssignment ma = new MutableAssignment();
            ma.slotId = req.id();
            ma.staffId = -1;
            ma.date = req.date();
            ma.shiftTypeId = req.shiftTypeId();
            ma.hours = hoursFor(req.shiftTypeId());
            ma.isWeekend = ma.date != null
                    && ma.date.getDayOfWeek().getValue() >= 6;
            ma.isHoliday = descriptor.getProblem().isHoliday(ma.date);
            sol.assignmentsBySlot.put(req.id(), ma);
            sol.assignments.add(ma);
        }
        return sol;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void removeFromStaffIndex(int staffId, int slotId) {
        List<Integer> slots = slotsByStaff.get(staffId);
        if (slots != null) {
            slots.remove(Integer.valueOf(slotId));
            if (slots.isEmpty()) {
                slotsByStaff.remove(staffId);
            }
        }
    }

    private static int hoursFor(String shiftTypeId) {
        if (shiftTypeId == null) return 0;
        return switch (shiftTypeId) {
            case "L01" -> 24;
            case "L02" -> 4;
            case "L03" -> 4;
            case "L04" -> 4;
            default -> 0;
        };
    }
}