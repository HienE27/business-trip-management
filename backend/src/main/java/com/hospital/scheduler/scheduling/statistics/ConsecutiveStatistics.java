package com.hospital.scheduler.scheduling.statistics;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import lombok.Getter;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the longest consecutive-days-worked run per staff.
 *
 * <p>Used to penalize solutions where one staff works too many days in a
 * row (BR-04 — max 6 consecutive days per {@code QuanLyLichCongTac_v5.md}).
 */
@Getter
public class ConsecutiveStatistics implements StatisticsModule {

    private final SolutionDescriptor descriptor;

    /** staffIndex → sorted set of assigned dates (mutated incrementally) */
    private final Map<Integer, java.util.TreeSet<LocalDate>> datesByStaff = new HashMap<>();

    public ConsecutiveStatistics(SolutionDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * Longest consecutive-days run for {@code staffIndex}. O(k) where k is the
     * number of days the staff works (typically small).
     */
    public int getMaxConsecutiveDays(int staffIndex) {
        java.util.TreeSet<LocalDate> dates = datesByStaff.get(staffIndex);
        if (dates == null || dates.isEmpty()) return 0;
        int max = 1;
        int current = 1;
        LocalDate prev = null;
        for (LocalDate d : dates) {
            if (prev != null && d.minusDays(1).equals(prev)) {
                current++;
                if (current > max) max = current;
            } else {
                current = 1;
            }
            prev = d;
        }
        return max;
    }

    @Override
    public void apply(Move move, WorkingSolution solution) {
        int[] slots = move.affectedSlotIndices();
        int[] staff = move.affectedStaffIndices();
        for (int i = 0; i < slots.length; i++) {
            MutableAssignment a = solution.getAssignment(slots[i]);
            if (a == null || a.staffId <= 0 || a.date == null) continue;
            int idx = descriptor.staffIndex(a.staffId);
            if (idx >= 0) datesByStaff.computeIfAbsent(idx, k -> new java.util.TreeSet<>()).add(a.date);
        }
        for (int i = slots.length; i < staff.length; i++) {
            int oldStaffId = staff[i];
            if (oldStaffId <= 0) continue;
            int idx = descriptor.staffIndex(oldStaffId);
            if (idx < 0) continue;
            MutableAssignment a = solution.getAssignment(slots[0]);
            if (a != null && a.date != null) {
                java.util.TreeSet<LocalDate> set = datesByStaff.get(idx);
                if (set != null) set.remove(a.date);
            }
        }
    }

    @Override
    public void undo(Move move, WorkingSolution solution) {
        int[] slots = move.affectedSlotIndices();
        int[] staff = move.affectedStaffIndices();
        for (int i = 0; i < slots.length; i++) {
            MutableAssignment a = solution.getAssignment(slots[i]);
            if (a == null || a.staffId <= 0 || a.date == null) continue;
            int idx = descriptor.staffIndex(a.staffId);
            if (idx < 0) continue;
            java.util.TreeSet<LocalDate> set = datesByStaff.get(idx);
            if (set != null) set.remove(a.date);
        }
        for (int i = slots.length; i < staff.length; i++) {
            int prevStaffId = staff[i];
            if (prevStaffId <= 0) continue;
            int idx = descriptor.staffIndex(prevStaffId);
            if (idx < 0) continue;
            MutableAssignment a = solution.getAssignment(slots[0]);
            if (a != null && a.date != null) {
                datesByStaff.computeIfAbsent(idx, k -> new java.util.TreeSet<>()).add(a.date);
            }
        }
    }

    @Override
    public void reset(WorkingSolution solution) {
        datesByStaff.clear();
        for (MutableAssignment a : solution.getAssignments()) {
            if (a.staffId > 0 && a.date != null) {
                int staffIdx = descriptor.staffIndex(a.staffId);
                if (staffIdx >= 0) {
                    datesByStaff.computeIfAbsent(staffIdx, k -> new java.util.TreeSet<>()).add(a.date);
                }
            }
        }
    }
}