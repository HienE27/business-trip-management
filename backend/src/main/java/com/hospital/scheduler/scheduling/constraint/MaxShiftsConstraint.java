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
        // V10-HARDCAP: a runtime global cap (max_shifts_per_staff) is a HARD
        // ceiling — same semantics as Greedy's cap check. Per-staff entity caps
        // stay soft (advisory; the search prefers balanced loads).
        return globalMaxCap > 0;
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
        // V10-HARDCAP: over the HARD global cap → hardDelta so the search's
        // RULE 1 hard-fence rejects the move; per-staff overage stays a soft
        // gap penalty for balance.
        return globalMaxCap > 0
                ? new ScoreDelta(totalOver, 0, 0, 0, 0, 0, 0)
                : new ScoreDelta(0, 0, 0, 0, 0, totalOver, 0);
    }
}