package com.hospital.scheduler.scheduling.telemetry;

import com.hospital.scheduler.scheduling.domain.SchedulingProblem;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.domain.StaffNode;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Builds a 2D {@code staff x day} heatmap of load intensity for a working
 * solution. Supports three metrics: {@code LOAD} (shift count),
 * {@code WEEKEND} (weekend shift count), {@code CONSECUTIVE} (longest
 * consecutive-days streak).
 *
 * <p>Each cell value is normalized to {@code [0, 1]} so the FE widget can map
 * it directly to a color/bucket without re-running the max calculation.
 */
public class HeatmapBuilder {

    public enum Metric {
        LOAD,
        WEEKEND,
        CONSECUTIVE
    }

    /** Single staff row in the heatmap. */
    public record StaffRow(
            int staffId,
            String displayName,
            double[] intensities,
            int rawTotal
    ) {}

    public record Heatmap(
            Metric metric,
            int periodDays,
            LocalDate startDate,
            LocalDate endDate,
            List<StaffRow> rows,
            double maxRaw
    ) {}

    public Heatmap build(WorkingSolution solution, Metric metric) {
        SolutionDescriptor descriptor = solution.getDescriptor();
        SchedulingProblem problem = descriptor.getProblem();
        int staffCount = descriptor.staffCount();

        // Determine period bounds by scanning requirements
        TreeSet<LocalDate> dates = new TreeSet<>();
        for (var req : problem.getRequirements()) {
            if (req.date() != null) dates.add(req.date());
        }
        LocalDate start = dates.isEmpty() ? LocalDate.now() : dates.first();
        LocalDate end = dates.isEmpty() ? start : dates.last();
        int dayCount = (int) (end.toEpochDay() - start.toEpochDay()) + 1;
        if (dayCount <= 0) dayCount = 1;

        List<StaffRow> rows = new ArrayList<>(staffCount);
        double maxRaw = 0.0;
        int[] rawTotals = new int[staffCount];

        switch (metric) {
            case LOAD -> {
                int[] counts = new int[staffCount];
                double[] cells = new double[staffCount * dayCount];
                for (var a : solution.getAssignments()) {
                    if (a.staffId <= 0 || a.date == null) continue;
                    int dayIdx = (int) (a.date.toEpochDay() - start.toEpochDay());
                    if (dayIdx < 0 || dayIdx >= dayCount) continue;
                    int staffIdx = descriptor.staffIndex(a.staffId);
                    if (staffIdx < 0) continue;
                    counts[staffIdx]++;
                    cells[staffIdx * dayCount + dayIdx] = 1.0;
                }
                maxRaw = max(counts);
                for (int s = 0; s < staffCount; s++) {
                    StaffNode staff = problem.getStaffList().get(s);
                    double[] normalized = new double[dayCount];
                    System.arraycopy(cells, s * dayCount, normalized, 0, dayCount);
                    rawTotals[s] = counts[s];
                    rows.add(new StaffRow(staff.getId(), staff.getFullName(), normalized, counts[s]));
                }
            }
            case WEEKEND -> {
                int[] counts = new int[staffCount];
                double[] cells = new double[staffCount * dayCount];
                Set<DayOfWeek> weekend = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
                for (var a : solution.getAssignments()) {
                    if (a.staffId <= 0 || a.date == null) continue;
                    int dayIdx = (int) (a.date.toEpochDay() - start.toEpochDay());
                    if (dayIdx < 0 || dayIdx >= dayCount) continue;
                    if (!weekend.contains(a.date.getDayOfWeek())) continue;
                    int staffIdx = descriptor.staffIndex(a.staffId);
                    if (staffIdx < 0) continue;
                    counts[staffIdx]++;
                    cells[staffIdx * dayCount + dayIdx] = 1.0;
                }
                maxRaw = max(counts);
                for (int s = 0; s < staffCount; s++) {
                    StaffNode staff = problem.getStaffList().get(s);
                    double[] normalized = new double[dayCount];
                    System.arraycopy(cells, s * dayCount, normalized, 0, dayCount);
                    rawTotals[s] = counts[s];
                    rows.add(new StaffRow(staff.getId(), staff.getFullName(), normalized, counts[s]));
                }
            }
            case CONSECUTIVE -> {
                boolean[][] inStreak = new boolean[staffCount][dayCount];
                for (int s = 0; s < staffCount; s++) {
                    int staffId = problem.getStaffList().get(s).getId();
                    int longest = computeLongestStreak(solution, staffId, start, dayCount, inStreak[s]);
                    rawTotals[s] = longest;
                }
                maxRaw = max(rawTotals);
                for (int s = 0; s < staffCount; s++) {
                    StaffNode staff = problem.getStaffList().get(s);
                    double[] normalized = new double[dayCount];
                    for (int d = 0; d < dayCount; d++) {
                        normalized[d] = inStreak[s][d] ? 1.0 : 0.0;
                    }
                    rows.add(new StaffRow(staff.getId(), staff.getFullName(), normalized, rawTotals[s]));
                }
            }
        }

        return new Heatmap(metric, dayCount, start, end, rows, maxRaw);
    }

    private int computeLongestStreak(WorkingSolution solution,
                                      int staffId,
                                      LocalDate start,
                                      int dayCount,
                                      boolean[] inStreak) {
        boolean[] hasShift = new boolean[dayCount];
        for (var a : solution.getAssignments()) {
            if (a.staffId != staffId || a.date == null) continue;
            int dayIdx = (int) (a.date.toEpochDay() - start.toEpochDay());
            if (dayIdx >= 0 && dayIdx < dayCount) hasShift[dayIdx] = true;
        }
        int longest = 0;
        int current = 0;
        int bestStart = -1;
        int currentStart = -1;
        for (int d = 0; d < dayCount; d++) {
            if (hasShift[d]) {
                if (current == 0) currentStart = d;
                current++;
                if (current > longest) {
                    longest = current;
                    bestStart = currentStart;
                }
            } else {
                current = 0;
            }
        }
        if (longest > 0 && bestStart >= 0) {
            for (int d = bestStart; d < bestStart + longest && d < dayCount; d++) {
                inStreak[d] = true;
            }
        }
        return longest;
    }

    private double max(int[] arr) {
        double m = 0.0;
        for (int v : arr) if (v > m) m = v;
        return m;
    }
}
