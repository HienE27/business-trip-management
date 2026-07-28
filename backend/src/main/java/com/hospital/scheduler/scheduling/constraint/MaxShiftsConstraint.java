package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * BR-06 — enforce {@code staff.maxShiftsPerMonth} (default 5).
 *
 * <p>Each staff member above their cap adds a penalty.
 * When {@code globalMaxCap > 0}, also enforces a global max cap across all staff
 * — this allows the runtime config {@code maxShiftsPerStaff} to apply to V10
 * search. Fix: V10-global-cap.
 */
public class MaxShiftsConstraint implements Constraint {

    /** Global cap applied to ALL staff (0 = disabled, use per-staff cap only). */
    private final int globalMaxCap;

    public MaxShiftsConstraint() {
        this(0);
    }

    public MaxShiftsConstraint(int globalMaxCap) {
        this.globalMaxCap = globalMaxCap;
    }

    @Override
    public String id() {
        return "BR-06:MaxShifts";
    }

    @Override
    public boolean isHard() {
        return false; // soft — per-staff caps are advisory; the search prefers balanced loads
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
            int cap;
            if (globalMaxCap > 0) {
                cap = globalMaxCap;
            } else {
                Integer entityCap = s.getMaxShiftsPerMonth();
                cap = (entityCap != null && entityCap > 0) ? entityCap : Integer.MAX_VALUE;
            }
            if (cap >= Integer.MAX_VALUE) continue;
            int actual = counts.getOrDefault(s.getId(), 0);
            if (actual > cap) totalOver += (actual - cap);
        }
        return new ScoreDelta(0, 0, 0, 0, 0, totalOver, 0);
    }
}