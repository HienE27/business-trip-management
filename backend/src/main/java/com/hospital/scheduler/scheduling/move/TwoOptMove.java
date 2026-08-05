package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 2-opt move: reverses a subsequence of L01 assignments for a single staff
 * between two dates (inclusive) on the same staff.
 *
 * <p>Standard 2-opt on TSP routes: pick two edges and reverse the path
 * between them. Applied to scheduling, this means: for a given staff,
 * take all L01 slots on dates in [startDate, endDate] and reverse their
 * sequence. This rearranges which staff works which L01 duty within the
 * chosen window while keeping the total count per staff unchanged.
 *
 * <p>BR-04 validation (OPT-001 #2):
 * After reversal, check every affected staff's L01 pattern. If the
 * reversal creates any adjacent-L01 pair (gap = 1 day) for any staff,
 * the move is rejected by the hard-fence in the search loop — the
 * validator returns false and no state is modified.
 *
 * <p>This move is most effective when L01 gaps are uneven (gap=3 vs gap=6)
 * and reversing a subsequence equalises them. It does NOT change coverage.
 */
public class TwoOptMove implements Move {

    private final int staffId;
    private final LocalDate startDate;
    private final LocalDate endDate;

    /** Reversal candidates: slotIds within [startDate, endDate] that are L01-assigned */
    private int[] reversedSlots;

    /** Snapshot of pre-move staffIds for undo */
    private int[] preMoveStaffIds;

    /**
     * @param staffId  the staff whose L01 subsequence is reversed
     * @param startDate  inclusive lower bound
     * @param endDate    inclusive upper bound
     */
    public TwoOptMove(int staffId, LocalDate startDate, LocalDate endDate) {
        if (staffId <= 0) throw new IllegalArgumentException("staffId must be positive");
        if (startDate == null || endDate == null) throw new IllegalArgumentException("dates must not be null");
        if (endDate.isBefore(startDate)) throw new IllegalArgumentException("endDate must not be before startDate");
        this.staffId = staffId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * Build a validated 2-opt move: collect L01 slots of {@code staffId} within
     * [startDate, endDate] and validate that reversal would NOT create a BR-04
     * violation for any staff.
     *
     * <p>Returns null if no valid reversal exists (fewer than 2 L01 slots in range,
     * or reversal would violate BR-04). The caller (SampledMoveSelector) treats
     * null as "skip this move".
     *
     * @param solution  current working solution
     * @param staffId   staff whose L01 slots are reversed
     * @param startDate inclusive lower bound
     * @param endDate   inclusive upper bound
     * @return a validated TwoOptMove, or null if invalid
     */
    public static TwoOptMove buildValidated(WorkingSolution solution,
                                            int staffId,
                                            LocalDate startDate,
                                            LocalDate endDate) {
        TwoOptMove move = new TwoOptMove(staffId, startDate, endDate);
        if (!move.prepare(solution)) return null;
        return move;
    }

    /**
     * Collect L01 slots within [startDate, endDate] and snapshot their staff.
     * Returns false if fewer than 2 slots are eligible (nothing to reverse)
     * or if the reversal would create BR-04 violations.
     *
     * <p>After this call, {@code reversedSlots} holds the slotIds in the
     * reversal window, in REVERSED order. {@code preMoveStaffIds} holds the
     * original assignments.
     */
    boolean prepare(WorkingSolution solution) {
        List<Integer> slotsInWindow = new ArrayList<>();

        for (int slotId : solution.getSlotsAssignedTo(staffId)) {
            MutableAssignment a = solution.getAssignment(slotId);
            if (a == null || a.staffId <= 0) continue;
            if (!"L01".equals(a.shiftTypeId)) continue;
            if (a.date == null) continue;
            if (!a.date.equals(startDate) && !a.date.equals(endDate)
                    && (a.date.isBefore(startDate) || a.date.isAfter(endDate))) continue;
            slotsInWindow.add(slotId);
        }

        if (slotsInWindow.size() < 2) return false;

        // Collect assignments and sort by date
        List<MutableAssignment> assignments = new ArrayList<>();
        for (int slotId : slotsInWindow) {
            MutableAssignment a = solution.getAssignment(slotId);
            if (a != null) assignments.add(a);
        }
        assignments.sort(Comparator.comparing(a -> a.date));

        // Snapshot original staff assignments for undo
        preMoveStaffIds = new int[assignments.size()];
        reversedSlots = new int[assignments.size()];
        for (int i = 0; i < assignments.size(); i++) {
            preMoveStaffIds[i] = assignments.get(i).staffId;
            reversedSlots[i] = assignments.get(i).slotId;
        }

        // BR-04 validation: simulate reversal and check each staff's L01 pattern
        // After reversal, slot[i] gets staff from slot[assignments.size()-1-i]
        int n = assignments.size();
        // Build a date→staff snapshot for affected slots (old → new)
        java.util.Map<LocalDate, Integer> simulatedNewAssignments = new java.util.HashMap<>();
        for (int i = 0; i < n; i++) {
            MutableAssignment orig = assignments.get(i);
            MutableAssignment counterpart = assignments.get(n - 1 - i);
            simulatedNewAssignments.put(orig.date, counterpart.staffId);
        }

        // Build a complete picture of each affected staff's L01 dates after the reversal
        java.util.Map<Integer, List<LocalDate>> staffToL01Dates = new java.util.HashMap<>();
        for (MutableAssignment a : assignments) {
            int newStaff = simulatedNewAssignments.get(a.date);
            staffToL01Dates.computeIfAbsent(newStaff, k -> new ArrayList<>()).add(a.date);
        }
        // Add unaffected L01 slots for each staff
        for (int sid : staffToL01Dates.keySet()) {
            for (int slotId : solution.getSlotsAssignedTo(sid)) {
                MutableAssignment a = solution.getAssignment(slotId);
                if (a == null || a.staffId <= 0) continue;
                if (!"L01".equals(a.shiftTypeId)) continue;
                if (!simulatedNewAssignments.containsKey(a.date)) {
                    // This slot is not being reversed — add to unaffected set
                    staffToL01Dates.computeIfAbsent(sid, k -> new ArrayList<>()).add(a.date);
                }
            }
        }

        // Check each staff for adjacent-L01 after reversal
        for (java.util.Map.Entry<Integer, List<LocalDate>> e : staffToL01Dates.entrySet()) {
            List<LocalDate> dates = e.getValue();
            dates.sort(null);
            for (int i = 1; i < dates.size(); i++) {
                if (dates.get(i).minusDays(1).equals(dates.get(i - 1))) {
                    return false; // reversal would create adjacent L01 → BR-04 violation
                }
            }
        }

        return true;
    }

    @Override
    public void doMove(WorkingSolution solution) {
        if (reversedSlots == null || preMoveStaffIds == null) return;
        int n = reversedSlots.length;
        // reversedSlots[i] → preMoveStaffIds[n-1-i]
        for (int i = 0; i < n; i++) {
            int targetSlot = reversedSlots[i];
            int newStaff = preMoveStaffIds[n - 1 - i];
            if (newStaff > 0) {
                solution.assign(targetSlot, newStaff);
            }
        }
    }

    @Override
    public void undo(WorkingSolution solution) {
        if (reversedSlots == null || preMoveStaffIds == null) return;
        int n = reversedSlots.length;
        // Undo: assign each slot back to its original staff
        for (int i = 0; i < n; i++) {
            int slot = reversedSlots[i];
            int origStaff = preMoveStaffIds[i];
            if (origStaff > 0) {
                solution.assign(slot, origStaff);
            } else {
                solution.unassign(slot);
            }
        }
    }

    @Override
    public MoveType type() {
        return MoveType.TWO_OPT;
    }

    @Override
    public int[] affectedStaffIndices() {
        if (preMoveStaffIds == null) return new int[0];
        return preMoveStaffIds.clone();
    }

    @Override
    public int[] affectedSlotIndices() {
        if (reversedSlots == null) return new int[0];
        return reversedSlots.clone();
    }

    public int staffId() { return staffId; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
}
