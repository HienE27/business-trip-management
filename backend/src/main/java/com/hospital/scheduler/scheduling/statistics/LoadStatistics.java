package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks shift count per staff incrementally.
 *
 * <p>Used by the score layer to compute "average shifts per staff" and to
 * identify under-loaded vs over-loaded staff for rebalancing moves.
 */
@Getter
public class LoadStatistics implements StatisticsModule {

    private final SolutionDescriptor descriptor;
    /** staffIndex → count of assigned shifts */
    private final Map<Integer, Integer> shiftCountByStaff = new HashMap<>();

    public LoadStatistics(SolutionDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public int getShiftCount(int staffIndex) {
        return shiftCountByStaff.getOrDefault(staffIndex, 0);
    }

    /**
     * Apply the staff-count delta for a move.
     *
     * <p>{@link Move#affectedStaffIndices()} may include both the new staff
     * and the previous staff for the same slot (e.g. {@code AssignMove}).
     * We walk slot-by-slot to add +1 for the staff currently assigned, then
     * walk the "previous staff" tail (entries beyond {@code slots.length})
     * to subtract 1 each — those slots were unassigned from those staff by
     * this move.
     */
    @Override
    public void apply(Move move, WorkingSolution solution) {
        int[] slots = move.affectedSlotIndices();
        int[] staff = move.affectedStaffIndices();
        for (int i = 0; i < slots.length; i++) {
            MutableAssignment a = solution.getAssignment(slots[i]);
            if (a == null || a.staffId <= 0) continue;
            int idx = descriptor.staffIndex(a.staffId);
            if (idx >= 0) shiftCountByStaff.merge(idx, 1, Integer::sum);
        }
        for (int i = slots.length; i < staff.length; i++) {
            int oldStaffId = staff[i];
            if (oldStaffId <= 0) continue;
            int idx = descriptor.staffIndex(oldStaffId);
            if (idx < 0) continue;
            shiftCountByStaff.merge(idx, -1, Integer::sum);
            Integer v = shiftCountByStaff.get(idx);
            if (v != null && v <= 0) shiftCountByStaff.remove(idx);
        }
    }

    /**
     * Undo the staff-count delta. Mirrors {@link #apply}: subtract 1 from the
     * staff currently on the slot (they lose it after undo) and add 1 back to
     * the previous staff (slot returns to them).
     */
    @Override
    public void undo(Move move, WorkingSolution solution) {
        int[] slots = move.affectedSlotIndices();
        int[] staff = move.affectedStaffIndices();
        for (int i = 0; i < slots.length; i++) {
            MutableAssignment a = solution.getAssignment(slots[i]);
            if (a == null || a.staffId <= 0) continue;
            int idx = descriptor.staffIndex(a.staffId);
            if (idx < 0) continue;
            shiftCountByStaff.merge(idx, -1, Integer::sum);
            Integer v = shiftCountByStaff.get(idx);
            if (v != null && v <= 0) shiftCountByStaff.remove(idx);
        }
        for (int i = slots.length; i < staff.length; i++) {
            int prevStaffId = staff[i];
            if (prevStaffId <= 0) continue;
            int idx = descriptor.staffIndex(prevStaffId);
            if (idx >= 0) shiftCountByStaff.merge(idx, 1, Integer::sum);
        }
    }

    @Override
    public void reset(WorkingSolution solution) {
        shiftCountByStaff.clear();
        for (int staffIdx = 0; staffIdx < descriptor.staffCount(); staffIdx++) {
            shiftCountByStaff.put(staffIdx, solution.getShiftCount(descriptor.getProblem()
                    .getStaffList().get(staffIdx).getId()));
        }
    }

    /** Total shifts assigned across all staff. */
    public int totalShifts() {
        int sum = 0;
        for (int v : shiftCountByStaff.values()) sum += v;
        return sum;
    }
}