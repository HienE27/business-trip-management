package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

/**
 * BR-04 — adjacent L01 shifts (24/24 duty on consecutive days) are allowed
 * but penalized because each L01 triggers a compensation day, which can
 * cascade into rest-day violations downstream.
 *
 * <p>Penalty = count of (L01_today, L01_tomorrow) pairs per staff.
 */
public class AdjacentL01Constraint implements Constraint {

    @Override
    public String id() {
        return "BR-04:AdjacentL01";
    }

    @Override
    public boolean isHard() {
        return false;
    }

    @Override
    public double weight() {
        return 50.0;
    }

    @Override
    public ScoreDelta evaluate(WorkingSolution solution) {
        java.util.Map<Integer, java.util.Set<java.time.LocalDate>> byStaff = new java.util.HashMap<>();
        for (MutableAssignment a : solution.getAssignments()) {
            if (a.staffId <= 0 || a.date == null) continue;
            if (!"L01".equals(a.shiftTypeId)) continue;
            byStaff.computeIfAbsent(a.staffId, k -> new java.util.HashSet<>()).add(a.date);
        }
        int violations = 0;
        for (java.util.Set<java.time.LocalDate> dates : byStaff.values()) {
            java.util.TreeSet<java.time.LocalDate> sorted = new java.util.TreeSet<>(dates);
            java.time.LocalDate prev = null;
            for (java.time.LocalDate d : sorted) {
                if (prev != null && d.minusDays(1).equals(prev)) violations++;
                prev = d;
            }
        }
        return new ScoreDelta(0, 0, 0, 0, violations, 0, 0);
    }
}