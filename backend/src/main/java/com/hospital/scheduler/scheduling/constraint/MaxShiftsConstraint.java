package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * BR-06 — enforce {@code staff.maxShiftsPerMonth} (default 5).
 *
 * <p>Each staff member above their cap adds a soft penalty.
 * Hard when {@code maxShiftsPerMonth} is null (no limit).
 */
public class MaxShiftsConstraint implements Constraint {

    @Override
    public String id() {
        return "BR-06:MaxShifts";
    }

    @Override
    public boolean isHard() {
        return false;
    }

    @Override
    public double weight() {
        return 30.0;
    }

    @Override
    public ScoreDelta evaluate(WorkingSolution solution) {
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (MutableAssignment a : solution.getAssignments()) {
            if (a.staffId <= 0) continue;
            counts.merge(a.staffId, 1, Integer::sum);
        }
        int totalOver = 0;
        var staffList = solution.getDescriptor().getProblem().getStaffList();
        for (var s : staffList) {
            Integer cap = s.getMaxShiftsPerMonth();
            if (cap == null || cap <= 0) continue;
            int actual = counts.getOrDefault(s.getId(), 0);
            if (actual > cap) totalOver += (actual - cap);
        }
        return new ScoreDelta(0, 0, 0, 0, 0, totalOver, 0);
    }
}