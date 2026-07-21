package com.hospital.scheduler.scheduling.constraint;

import com.hospital.scheduler.scheduling.score.ScoreDelta;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * BR-01 / BR-02 — no staff can work two different shift types on the same day.
 *
 * <p>Specifically:
 * <ul>
 *   <li>BR-01: L01 (24/24 duty) and L02 (thong tam) on the same day → forbidden</li>
 *   <li>BR-02: L03 (clinic dich vu) and L04 (clinic chuyen gia) on the same day → forbidden</li>
 * </ul>
 *
 * <p>Hard constraint — any violation makes the score "infinitely bad".
 */
public class ShiftConflictConstraint implements Constraint {

    @Override
    public String id() {
        return "BR-01:ShiftConflict";
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
        // staffId → set of shift-type-ids worked on each date
        Map<Integer, Map<LocalDate, Set<String>>> byStaffDate = new HashMap<>();
        int violations = 0;
        for (MutableAssignment a : solution.getAssignments()) {
            if (a.staffId <= 0 || a.date == null || a.shiftTypeId == null) continue;
            Map<LocalDate, Set<String>> byDate = byStaffDate.computeIfAbsent(
                    a.staffId, k -> new HashMap<>());
            Set<String> types = byDate.computeIfAbsent(a.date, k -> new HashSet<>());
            types.add(a.shiftTypeId);
        }
        for (Map<LocalDate, Set<String>> byDate : byStaffDate.values()) {
            for (Set<String> types : byDate.values()) {
                if (types.contains("L01") && types.contains("L02")) violations++;
                if (types.contains("L03") && types.contains("L04")) violations++;
            }
        }
        return new ScoreDelta(violations, 0, 0, 0, 0, 0, 0);
    }
}