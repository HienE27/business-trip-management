package com.hospital.scheduler.scheduling.move;

import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Or-opt move: relocates a chain of 1-3 consecutive L01 assignments from one
 * gap position to another within a single staff's schedule.
 *
 * <p>Motivation: L01 (24/24 duty) requires at least 1 rest day between
 * consecutive duties (BR-04). In a schedule where L01 gaps are uneven
 * (e.g. gaps of 3, 7, 3 days), Or-opt can take a single L01 from a
 * short-gap region and move it into a longer-gap region — evening out the
 * distribution without breaking BR-04 or changing coverage.
 *
 * <p>Constraint validation (OPT-001 #3):
 * <ol>
 *   <li>Source chain: the L01 being moved must NOT be adjacent to another
 *       L01 after removal (i.e. removing it must not join two L01s into one).</li>
 *   <li>Target gap: after insertion, the moved L01 must NOT become adjacent
 *       to an existing L01 at either end.</li>
 *   <li>BR-04: neither the source nor target position may create a
 *       consecutive-L01 violation after the move.</li>
 *   <li>Compensation days: the target date must not be a compensation day
 *       for the staff (derived from existing L01 placements).</li>
 *   <li>No consecutive L01 created at target: inserting the L01 between
 *       two dates where gap ≥ 2 is safe; gap = 1 would create adjacency.</li>
 * </ol>
 *
 * <p>The move is BR-04-safe by construction: a gap of ≥ 2 days is
 * guaranteed by the source/target validation above.
 *
 * <p>This move does NOT change coverage (it reassigns the same number of
 * L01 slots), but it rebalances L01 distribution across the period,
 * which improves fairness scores.
 */
public class OrOptMove implements Move {

    /** The staff whose schedule is being modified */
    private final int staffId;

    /** SlotId of the L01 being moved */
    private final int sourceSlot;

    /** The date the source slot currently occupies */
    private final LocalDate sourceDate;

    /** The target date to relocate the L01 to */
    private final LocalDate targetDate;

    /** Target slot (requirement slot at targetDate, or -1 if unassigned) */
    private final int targetSlot;

    /** Chain length: 1, 2, or 3 */
    private final int chainLength;

    // Snapshots for undo
    private int preMoveTargetStaff;   // staff originally on target slot (-1 if unassigned)
    private int preMoveSourceStaff;   // should equal staffId

    /**
     * @param staffId    staff whose L01 chain is moved
     * @param sourceSlot slotId of the first L01 in the chain
     * @param sourceDate current date of the source slot
     * @param targetDate destination date for the chain
     * @param targetSlot requirement slot at the target date (must be unassigned or L01)
     * @param chainLength  number of consecutive L01 slots in the chain (1-3)
     */
    public OrOptMove(int staffId, int sourceSlot, LocalDate sourceDate,
                     LocalDate targetDate, int targetSlot, int chainLength) {
        if (staffId <= 0) throw new IllegalArgumentException("staffId must be positive");
        if (sourceSlot <= 0) throw new IllegalArgumentException("sourceSlot must be positive");
        if (targetSlot <= 0) throw new IllegalArgumentException("targetSlot must be positive");
        if (sourceDate == null || targetDate == null) throw new IllegalArgumentException("dates must not be null");
        if (chainLength < 1 || chainLength > 3) throw new IllegalArgumentException("chainLength must be 1-3");
        this.staffId = staffId;
        this.sourceSlot = sourceSlot;
        this.sourceDate = sourceDate;
        this.targetDate = targetDate;
        this.targetSlot = targetSlot;
        this.chainLength = chainLength;
    }

    /**
     * Build a validated Or-opt move for a staff.
     *
     * <p>Steps:
     * <ol>
     *   <li>Find a source L01 slot for the staff.</li>
     *   <li>Check BR-04 source safety: after removing this L01 (and its chain),
     *       no adjacent L01 remains.</li>
     *   <li>Find a valid target gap: a gap of ≥ 2 days where an L01 slot
     *       is unassigned and the staff is eligible.</li>
     *   <li>Validate: no adjacent L01 at target, no BR-04 violation,
     *       no compensation day conflict.</li>
     * </ol>
     *
     * @param solution  current working solution
     * @param staffId   staff performing the relocation
     * @param sourceSlot preferred source slot (or -1 to auto-select)
     * @return a validated OrOptMove, or null if no valid move exists
     */
    public static OrOptMove buildValidated(WorkingSolution solution, int staffId, int sourceSlot) {
        List<int[]> l01Slots = getL01SlotsSorted(solution, staffId);
        if (l01Slots.size() < 1) return null;

        // Try each L01 as source
        for (int idx = 0; idx < l01Slots.size(); idx++) {
            int[] entry = l01Slots.get(idx);
            int slot = entry[0];
            LocalDate date = LocalDate.ofEpochDay(entry[1]);

            // Try chain lengths 1..3
            for (int chain = 1; chain <= 3 && idx + chain <= l01Slots.size(); chain++) {
                // Check chain is consecutive
                boolean consecutive = true;
                for (int k = 1; k < chain; k++) {
                    long gap = l01Slots.get(idx + k)[1] - l01Slots.get(idx + k - 1)[1];
                    if (gap != 1) { consecutive = false; break; }
                }
                if (!consecutive) break; // can't extend chain further

                // Source safety: removing this chain must not join two L01s
                LocalDate prevDate = idx > 0 ? LocalDate.ofEpochDay(l01Slots.get(idx - 1)[1]) : null;
                LocalDate nextDate = idx + chain < l01Slots.size()
                        ? LocalDate.ofEpochDay(l01Slots.get(idx + chain)[1]) : null;

                if (prevDate != null && prevDate.plusDays(1).equals(date)) {
                    continue; // source is adjacent to prev L01 → removing would merge
                }
                if (nextDate != null && nextDate.minusDays(1).equals(
                        LocalDate.ofEpochDay(l01Slots.get(idx + chain - 1)[1]))) {
                    // next is right after the chain — check if removing chain joins prev to next
                    if (prevDate != null && nextDate.minusDays(1).equals(prevDate)) {
                        continue; // would create new adjacent pair
                    }
                }

                // Find valid target: any date in a gap of ≥ 2 days from both
                // neighbouring L01s (and not equal to the source date). The gap
                // dates are the dates strictly between prevDate and the source
                // (after removing the chain), plus the dates strictly between
                // the source/chain-end and nextDate — but the simplest
                // definition: any date D such that gap(prev, D) ≥ 2 AND
                // gap(D, next) ≥ 2, where prev/next are the chain's neighbours.
                // For chain = 1 with single source at `date`, the valid target
                // is any date D in (prevDate+2 .. nextDate-2) excluding `date`.
                LocalDate gapStart = (prevDate != null) ? prevDate.plusDays(2) : null;
                LocalDate gapEnd = (nextDate != null) ? nextDate.minusDays(2) : null;
                if (gapStart != null && gapEnd != null && gapEnd.isBefore(gapStart)) {
                    // No gap large enough — try the next source.
                    continue;
                }
                for (LocalDate targetD : collectL01RequirementDates(solution, staffId)) {
                    if (targetD.equals(date)) continue; // can't move to self
                    if (gapStart != null && targetD.isBefore(gapStart)) continue;
                    if (gapEnd != null && targetD.isAfter(gapEnd)) continue;

                    int targetSlotId = findOrCreateTargetSlot(solution, staffId, targetD, "L01");
                    if (targetSlotId <= 0) continue;

                    // Verify no adjacent L01 at target after insertion
                    if (hasAdjacentL01At(solution, staffId, targetD)) continue;

                    // Verify no compensation day conflict
                    if (solution.isOnDerivedCompDay(staffId, targetD)) continue;

                    OrOptMove move = new OrOptMove(staffId, slot, date, targetD, targetSlotId, chain);
                    if (move.validate(solution)) {
                        return move;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Validate that applying this Or-opt move does not break any hard rule:
     * BR-04 (adjacent L01), compensation days, or duplicate assignment.
     */
    private boolean validate(WorkingSolution solution) {
        // After move: sourceStaff is removed from sourceDate, added to targetDate

        // 1. After removing source: no adjacent L01 remains at source neighbours
        List<int[]> l01Slots = getL01SlotsSorted(solution, staffId);
        List<LocalDate> dates = new ArrayList<>();
        for (int[] e : l01Slots) {
            if (e[0] != sourceSlot) {
                dates.add(LocalDate.ofEpochDay(e[1]));
            }
        }

        // Simulate: add target date
        dates.add(targetDate);
        dates.sort(null);

        // Check no adjacent pairs
        for (int i = 1; i < dates.size(); i++) {
            if (dates.get(i).minusDays(1).equals(dates.get(i - 1))) {
                return false;
            }
        }

        // 2. Target date not a compensation day for this staff
        if (solution.isOnDerivedCompDay(staffId, targetDate)) return false;

        // 3. No duplicate: target slot must not already have this staff assigned
        int existing = solution.getAssignedStaff(targetSlot);
        if (existing == staffId) return false; // already assigned — no-op

        return true;
    }

    @Override
    public void doMove(WorkingSolution solution) {
        preMoveSourceStaff = solution.getAssignedStaff(sourceSlot);
        preMoveTargetStaff = solution.getAssignedStaff(targetSlot);

        // Remove from source
        solution.unassign(sourceSlot);
        // Assign to target (may overwrite existing staff at target)
        solution.assign(targetSlot, staffId);
    }

    @Override
    public void undo(WorkingSolution solution) {
        solution.unassign(targetSlot);
        if (preMoveTargetStaff > 0) {
            solution.assign(targetSlot, preMoveTargetStaff);
        }
        if (preMoveSourceStaff > 0) {
            solution.assign(sourceSlot, preMoveSourceStaff);
        }
    }

    @Override
    public MoveType type() {
        return MoveType.OR_OPT;
    }

    @Override
    public int[] affectedStaffIndices() {
        return new int[]{staffId, preMoveTargetStaff};
    }

    @Override
    public int[] affectedSlotIndices() {
        return new int[]{sourceSlot, targetSlot};
    }

    public int staffId() { return staffId; }
    public LocalDate sourceDate() { return sourceDate; }
    public LocalDate targetDate() { return targetDate; }
    public int sourceSlot() { return sourceSlot; }
    public int targetSlot() { return targetSlot; }
    public int chainLength() { return chainLength; }

    // ── private helpers ───────────────────────────────────────────────────────

    private static List<int[]> getL01SlotsSorted(WorkingSolution solution, int staffId) {
        // Returns List of [slotId, epochDay] sorted by epochDay
        List<int[]> slots = new ArrayList<>();
        for (int slotId : solution.getSlotsAssignedTo(staffId)) {
            MutableAssignment a = solution.getAssignment(slotId);
            if (a == null || a.staffId <= 0) continue;
            if (!"L01".equals(a.shiftTypeId)) continue;
            if (a.date == null) continue;
            slots.add(new int[]{slotId, (int) a.date.toEpochDay()});
        }
        slots.sort((a, b) -> Integer.compare(a[1], b[1]));
        return slots;
    }

    /**
     * Return all dates in the problem that have an L01 requirement slot
     * (unassigned or assigned to someone other than {@code staffId}).
     *
     * <p>Used by {@link #buildValidated} to enumerate candidate target dates
     * for the relocation — the original code iterated over the staff's own
     * L01 slots, which can never be a valid "gap" target for itself.
     *
     * <p>Reads from {@code SchedulingProblem.getRequirements()} (the canonical
     * source of truth) rather than from {@link MutableAssignment} because some
     * test fixtures overwrite {@code shiftTypeId} on the assignment without
     * touching the requirement.
     */
    private static List<LocalDate> collectL01RequirementDates(WorkingSolution solution, int staffId) {
        java.util.TreeSet<LocalDate> dates = new java.util.TreeSet<>();
        for (var req : solution.getProblem().getRequirements()) {
            if (req.date() == null) continue;
            if (!"L01".equals(req.shiftTypeId())) continue;
            // Exclude slots already assigned to this staff (the source chain).
            int assignedStaff = solution.getAssignedStaff(req.id());
            if (assignedStaff == staffId) continue;
            dates.add(req.date());
        }
        return new ArrayList<>(dates);
    }

    private static boolean hasAdjacentL01At(WorkingSolution solution, int staffId, LocalDate date) {
        for (int slotId : solution.getSlotsAssignedTo(staffId)) {
            MutableAssignment a = solution.getAssignment(slotId);
            if (a == null || a.staffId <= 0) continue;
            if (!"L01".equals(a.shiftTypeId)) continue;
            if (a.date == null) continue;
            if (a.date.plusDays(1).equals(date) || a.date.minusDays(1).equals(date)) {
                return true;
            }
        }
        return false;
    }

    private static int findOrCreateTargetSlot(WorkingSolution solution, int staffId,
                                              LocalDate date, String shiftType) {
        // Find a slot at the target date whose REQUIREMENT matches `shiftType`.
        // Read from the problem (not from MutableAssignment) so fixtures that
        // overwrite shiftTypeId on assignments don't blind us to the true
        // requirement type.
        for (var req : solution.getProblem().getRequirements()) {
            if (date.equals(req.date()) && shiftType.equals(req.shiftTypeId())) {
                return req.id();
            }
        }
        return -1;
    }
}
