package com.hospital.scheduler.scheduling.solution;

import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks running statistics about the current {@link WorkingSolution}
 * without scanning it on every query.
 *
 * <p>Counters are updated via {@link #apply}/{@link #undo} when the search
 * loop applies a {@code Move}. Derived metrics (mean, variance, CV, gap)
 * are recomputed lazily on read.
 */
@Getter
public class IncrementalState {

    private final SolutionDescriptor descriptor;

    /** staffIndex → number of assigned slots */
    private final Map<Integer, Integer> shiftCountByStaff = new HashMap<>();
    /** staffIndex → number of assigned weekend slots */
    private final Map<Integer, Integer> weekendCountByStaff = new HashMap<>();
    /** staffIndex → number of assigned holiday slots */
    private final Map<Integer, Integer> holidayCountByStaff = new HashMap<>();
    /** Map key = staffIndex*1000 + week → hours in that week */
    private final Map<Long, Integer> hoursByStaffWeek = new HashMap<>();

    // Running mean/variance updated as we add/remove shifts.
    private double mean = 0;
    private double variance = 0;
    private int totalShifts = 0;

    public IncrementalState(SolutionDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    // ── Mutation ────────────────────────────────────────────────────────────

    /** Apply a {@link MutableAssignment} update for a staff at a given index. */
    public void apply(MutableAssignment a, int staffIndex) {
        if (a == null || staffIndex < 0) return;
        if (a.staffId <= 0) return;
        incCount(shiftCountByStaff, staffIndex);
        if (a.isWeekend) incCount(weekendCountByStaff, staffIndex);
        if (a.isHoliday) incCount(holidayCountByStaff, staffIndex);
        int week = a.getWeek();
        if (week != Integer.MIN_VALUE) {
            long key = ((long) staffIndex << 32) | (week & 0xFFFFFFFFL);
            hoursByStaffWeek.merge(key, a.hours, Integer::sum);
        }
        totalShifts++;
        recomputeMeanAndVariance();
    }

    /** Undo a {@link MutableAssignment} update. */
    public void undo(MutableAssignment a, int staffIndex) {
        if (a == null || staffIndex < 0) return;
        if (a.staffId <= 0) return;
        decCount(shiftCountByStaff, staffIndex);
        if (a.isWeekend) decCount(weekendCountByStaff, staffIndex);
        if (a.isHoliday) decCount(holidayCountByStaff, staffIndex);
        int week = a.getWeek();
        if (week != Integer.MIN_VALUE) {
            long key = ((long) staffIndex << 32) | (week & 0xFFFFFFFFL);
            hoursByStaffWeek.merge(key, -a.hours, Integer::sum);
        }
        totalShifts = Math.max(0, totalShifts - 1);
        recomputeMeanAndVariance();
    }

    /**
     * Update counters when {@code assignment} changes from {@code oldStaffId}
     * to {@code newStaffId}. {@code newStaffIndex} is the new staff index.
     */
    public void changeStaff(MutableAssignment a, int oldStaffId, int newStaffId, int newStaffIndex) {
        if (oldStaffId == newStaffId) return;
        if (oldStaffId > 0) {
            int oldIdx = descriptor.staffIndex(oldStaffId);
            if (oldIdx >= 0) undo(a, oldIdx);
        }
        if (newStaffId > 0 && newStaffIndex >= 0) {
            apply(a, newStaffIndex);
        }
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    /** Mean shifts per staff across all staff in the problem. */
    public double getMean() {
        return mean;
    }

    /** Coefficient of variation = stddev / mean (NaN if mean = 0). */
    public double getCV() {
        if (mean <= 0) return 0;
        return Math.sqrt(variance) / mean;
    }

    /** Largest shift-count gap between any two staff. */
    public int getGap() {
        if (shiftCountByStaff.isEmpty()) return 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int v : shiftCountByStaff.values()) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        return max - min;
    }

    /** Total hours for {@code staffIndex} in {@code week}. */
    public int getHours(int staffIndex, int week) {
        if (week == Integer.MIN_VALUE) return 0;
        long key = ((long) staffIndex << 32) | (week & 0xFFFFFFFFL);
        return hoursByStaffWeek.getOrDefault(key, 0);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static void incCount(Map<Integer, Integer> map, int idx) {
        map.merge(idx, 1, Integer::sum);
    }

    private static void decCount(Map<Integer, Integer> map, int idx) {
        map.merge(idx, -1, Integer::sum);
        Integer v = map.get(idx);
        if (v != null && v <= 0) map.remove(idx);
    }

    private void recomputeMeanAndVariance() {
        int n = descriptor.staffCount();
        if (n == 0) {
            mean = 0;
            variance = 0;
            return;
        }
        double sum = 0;
        for (int v : shiftCountByStaff.values()) sum += v;
        mean = sum / n;
        double sq = 0;
        for (int v : shiftCountByStaff.values()) {
            double d = v - mean;
            sq += d * d;
        }
        variance = sq / n;
    }
}