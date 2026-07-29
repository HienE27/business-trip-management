package com.hospital.scheduler.scheduling.alns;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Built-in destroy operators — random, worst, related, shaw. Each removes a
 * batch of slots; the slot list is rebuilt each call so the operator doesn't
 * need to track state.
 */
public final class DestroyOperators {

    private DestroyOperators() {}

    /** Random removal — picks {@code removeCount} random slots to unassign. */
    public static DestroyOperator random() {
        return new DestroyOperator() {
            private final Random rng = new Random();
            @Override public String name() { return "random"; }
            @Override public int destroy(WorkingSolution solution, int removeCount) {
                int total = solution.getAssignments().size();
                int removed = 0;
                List<Integer> all = new ArrayList<>(total);
                for (int i = 0; i < total; i++) all.add(i);
                java.util.Collections.shuffle(all, rng);
                for (int idx : all) {
                    if (removed >= removeCount) break;
                    var a = solution.getAssignment(idx);
                    if (a != null && a.staffId > 0) {
                        solution.unassign(a.slotId);
                        removed++;
                    }
                }
                return removed;
            }
        };
    }

    /** Worst removal — unassigns slots with the highest cost contribution. */
    public static DestroyOperator worst(java.util.function.IntUnaryOperator slotCost) {
        return new DestroyOperator() {
            @Override public String name() { return "worst"; }
            @Override public int destroy(WorkingSolution solution, int removeCount) {
                int removed = 0;
                List<Integer> indices = new ArrayList<>(solution.getAssignments().size());
                for (int i = 0; i < solution.getAssignments().size(); i++) {
                    var a = solution.getAssignments().get(i);
                    if (a != null && a.staffId > 0) indices.add(i);
                }
                indices.sort((x, y) -> {
                    int byCost = Integer.compare(slotCost.applyAsInt(y), slotCost.applyAsInt(x));
                    return byCost != 0 ? byCost : Integer.compare(x, y);
                });
                for (int idx : indices) {
                    if (removed >= removeCount) break;
                    var a = solution.getAssignments().get(idx);
                    if (a != null && a.staffId > 0) {
                        solution.unassign(a.slotId);
                        removed++;
                    }
                }
                return removed;
            }
        };
    }

    /** Related removal — unassigns slots whose staff have many other shifts. */
    public static DestroyOperator related() {
        return new DestroyOperator() {
            @Override public String name() { return "related"; }
            @Override public int destroy(WorkingSolution solution, int removeCount) {
                int removed = 0;
                var descriptor = solution.getDescriptor();
                int[] load = new int[descriptor.staffCount()];
                for (var a : solution.getAssignments()) {
                    if (a.staffId > 0) {
                        int idx = descriptor.staffIndex(a.staffId);
                        if (idx >= 0) load[idx]++;
                    }
                }
                List<Integer> indices = new ArrayList<>(solution.getAssignments().size());
                for (int i = 0; i < solution.getAssignments().size(); i++) {
                    var a = solution.getAssignment(i);
                    if (a != null && a.staffId > 0) indices.add(i);
                }
                indices.sort((x, y) -> {
                    int xLoad = load[descriptor.staffIndex(solution.getAssignment(x).staffId)];
                    int yLoad = load[descriptor.staffIndex(solution.getAssignment(y).staffId)];
                    return Integer.compare(yLoad, xLoad);
                });
                for (int idx : indices) {
                    if (removed >= removeCount) break;
                    var a = solution.getAssignment(idx);
                    if (a != null && a.staffId > 0) {
                        solution.unassign(a.slotId);
                        removed++;
                    }
                }
                return removed;
            }
        };
    }

    /** Shaw removal — unassigns clusters of shifts belonging to the same staff/day. */
    public static DestroyOperator shaw() {
        return new DestroyOperator() {
            @Override public String name() { return "shaw"; }
            @Override public int destroy(WorkingSolution solution, int removeCount) {
                int removed = 0;
                var descriptor = solution.getDescriptor();
                // Group assigned slots by staff
                java.util.Map<Integer, List<Integer>> byStaff = new java.util.HashMap<>();
                for (int i = 0; i < solution.getAssignments().size(); i++) {
                    var a = solution.getAssignment(i);
                    if (a == null || a.staffId <= 0) continue;
                    byStaff.computeIfAbsent(a.staffId, k -> new ArrayList<>()).add(i);
                }
                for (var entry : byStaff.entrySet()) {
                    if (removed >= removeCount) break;
                    for (int idx : entry.getValue()) {
                        if (removed >= removeCount) break;
                        var a = solution.getAssignment(idx);
                        if (a != null && a.staffId > 0) {
                            solution.unassign(a.slotId);
                            removed++;
                        }
                    }
                }
                return removed;
            }
        };
    }
}