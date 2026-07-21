package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * BR-03 — no staff should work more than 6 consecutive days.
 *
 * <p>From {@code QuanLyLichCongTac_v5.md}:
 * <blockquote>
 * Trong tuần, mỗi nhân viên không trực liên tục quá 6 ngày.
 * </blockquote>
 *
 * <p>Soft constraint — penalty = (consecutive_run - 6) for each offending staff.
 * Hard would block legal scheduling where leaves force the issue.
 */
public class RestDayConstraint implements Constraint {

    private static final int MAX_CONSECUTIVE = 6;

    @Override
    public String id() {
        return "BR-03:RestDay";
    }

    @Override
    public boolean isHard() {
        return false;
    }

    @Override
    public double weight() {
        return 100.0;
    }

    @Override
    public ScoreDelta evaluate(WorkingSolution solution) {
        // Build per-staff sorted date set
        java.util.Map<Integer, Set<LocalDate>> byStaff = new java.util.HashMap<>();
        for (MutableAssignment a : solution.getAssignments()) {
            if (a.staffId <= 0 || a.date == null) continue;
            byStaff.computeIfAbsent(a.staffId, k -> new HashSet<>()).add(a.date);
        }
        int totalOver = 0;
        for (Set<LocalDate> dates : byStaff.values()) {
            if (dates.isEmpty()) continue;
            java.util.TreeSet<LocalDate> sorted = new java.util.TreeSet<>(dates);
            int run = 1;
            LocalDate prev = null;
            int worst = 0;
            for (LocalDate d : sorted) {
                if (prev != null && d.minusDays(1).equals(prev)) {
                    run++;
                } else {
                    run = 1;
                }
                if (run > worst) worst = run;
                prev = d;
            }
            if (worst > MAX_CONSECUTIVE) totalOver += (worst - MAX_CONSECUTIVE);
        }
        return new ScoreDelta(0, 0, 0, 0, totalOver, 0, 0);
    }
}