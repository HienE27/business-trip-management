package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.time.LocalDate;

/**
 * BR-07 — same staff cannot be assigned the same shift-type twice on the
 * same date (defense-in-depth even though DB has a UNIQUE constraint).
 */
public class DuplicateShiftConstraint implements Constraint {

    @Override
    public String id() {
        return "BR-07:DuplicateShift";
    }

    @Override
    public boolean isHard() {
        return true;
    }

    @Override
    public double weight() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public ScoreDelta evaluate(WorkingSolution solution) {
        java.util.Map<Long, Integer> seen = new java.util.HashMap<>();
        int violations = 0;
        for (MutableAssignment a : solution.getAssignments()) {
            if (a.staffId <= 0 || a.date == null || a.shiftTypeId == null) continue;
            long key = key(a.staffId, a.date, a.shiftTypeId);
            int prior = seen.merge(key, 1, Integer::sum);
            if (prior > 1) violations++;
        }
        return new ScoreDelta(violations, 0, 0, 0, 0, 0, 0);
    }

    private static long key(int staffId, LocalDate date, String shiftTypeId) {
        long d = date.toEpochDay();
        return ((long) staffId << 40) | (d << 8) | (long) shiftTypeId.hashCode();
    }
}