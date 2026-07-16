package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

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
        Map<Object, Integer> seen = new HashMap<>();
        int violations = 0;
        for (MutableAssignment a : solution.getAssignments()) {
            if (a.staffId <= 0 || a.date == null || a.shiftTypeId == null) continue;
            Object key = new Key(a.staffId, a.date, a.shiftTypeId);
            int prior = seen.merge(key, 1, Integer::sum);
            if (prior > 1) violations++;
        }
        return new ScoreDelta(violations, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Compound key for (staffId, date, shiftTypeId). Avoids bit-packing
     * collisions seen when staffId × epochDay × hashCode overlap in a 64-bit
     * word (the previous implementation produced false positives when two
     * different dates yielded the same packed long).
     */
    private record Key(int staffId, LocalDate date, String shiftTypeId) {}
}