package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.move.Move.MoveType;
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

    @Override
    public void apply(Move move, WorkingSolution solution) {
        int[] slots = move.affectedSlotIndices();
        int[] staff = move.affectedStaffIndices();
        for (int i = 0; i < slots.length && i < staff.length; i++) {
            int staffIdx = staff[i];
            if (staffIdx < 0) continue;
            MutableAssignment a = solution.getAssignment(slots[i]);
            if (a != null && a.staffId > 0) {
                shiftCountByStaff.merge(staffIdx, 1, Integer::sum);
            }
        }
    }

    @Override
    public void undo(Move move, WorkingSolution solution) {
        int[] slots = move.affectedSlotIndices();
        int[] staff = move.affectedStaffIndices();
        for (int i = 0; i < slots.length && i < staff.length; i++) {
            int staffIdx = staff[i];
            if (staffIdx < 0) continue;
            MutableAssignment a = solution.getAssignment(slots[i]);
            if (a != null && a.staffId > 0) {
                shiftCountByStaff.merge(staffIdx, -1, Integer::sum);
                Integer v = shiftCountByStaff.get(staffIdx);
                if (v != null && v <= 0) shiftCountByStaff.remove(staffIdx);
            }
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