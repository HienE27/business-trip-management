package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks weekend shift count per staff.
 *
 * <p>Used to enforce the BR-06 (fair weekend distribution) soft constraint —
 * staff with many weekends get a penalty so the search loop evens them out.
 */
@Getter
public class WeekendStatistics implements StatisticsModule {

    private final SolutionDescriptor descriptor;
    /** staffIndex → weekend-shift count */
    private final Map<Integer, Integer> weekendCountByStaff = new HashMap<>();

    public WeekendStatistics(SolutionDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public int getWeekendCount(int staffIndex) {
        return weekendCountByStaff.getOrDefault(staffIndex, 0);
    }

    @Override
    public void apply(Move move, WorkingSolution solution) {
        int[] slots = move.affectedSlotIndices();
        int[] staff = move.affectedStaffIndices();
        for (int i = 0; i < slots.length; i++) {
            MutableAssignment a = solution.getAssignment(slots[i]);
            if (a == null || a.staffId <= 0 || !a.isWeekend) continue;
            int idx = descriptor.staffIndex(a.staffId);
            if (idx >= 0) weekendCountByStaff.merge(idx, 1, Integer::sum);
        }
        for (int i = slots.length; i < staff.length; i++) {
            int oldStaffId = staff[i];
            if (oldStaffId <= 0) continue;
            int idx = descriptor.staffIndex(oldStaffId);
            if (idx < 0) continue;
            MutableAssignment a = solution.getAssignment(slots[0]);
            if (a != null && a.isWeekend) {
                weekendCountByStaff.merge(idx, -1, Integer::sum);
                Integer v = weekendCountByStaff.get(idx);
                if (v != null && v <= 0) weekendCountByStaff.remove(idx);
            }
        }
    }

    @Override
    public void undo(Move move, WorkingSolution solution) {
        int[] slots = move.affectedSlotIndices();
        int[] staff = move.affectedStaffIndices();
        for (int i = 0; i < slots.length; i++) {
            MutableAssignment a = solution.getAssignment(slots[i]);
            if (a == null || a.staffId <= 0 || !a.isWeekend) continue;
            int idx = descriptor.staffIndex(a.staffId);
            if (idx < 0) continue;
            weekendCountByStaff.merge(idx, -1, Integer::sum);
            Integer v = weekendCountByStaff.get(idx);
            if (v != null && v <= 0) weekendCountByStaff.remove(idx);
        }
        for (int i = slots.length; i < staff.length; i++) {
            int prevStaffId = staff[i];
            if (prevStaffId <= 0) continue;
            int idx = descriptor.staffIndex(prevStaffId);
            if (idx < 0) continue;
            MutableAssignment a = solution.getAssignment(slots[0]);
            if (a != null && a.isWeekend) {
                weekendCountByStaff.merge(idx, 1, Integer::sum);
            }
        }
    }

    @Override
    public void reset(WorkingSolution solution) {
        weekendCountByStaff.clear();
        // Re-scan every slot — happens once at construction so O(n) is fine.
        for (MutableAssignment a : solution.getAssignments()) {
            if (a.staffId > 0 && a.isWeekend) {
                int staffIdx = descriptor.staffIndex(a.staffId);
                if (staffIdx >= 0) {
                    weekendCountByStaff.merge(staffIdx, 1, Integer::sum);
                }
            }
        }
    }

    /** Total weekend shifts assigned across all staff. */
    public int totalWeekendShifts() {
        int sum = 0;
        for (int v : weekendCountByStaff.values()) sum += v;
        return sum;
    }
}