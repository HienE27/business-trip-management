package com.hospital.scheduler.scheduling.alns;

import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import java.util.List;

/**
 * Built-in repair operators — greedy, regret, perturbed.
 */
public final class RepairOperators {

    private RepairOperators() {}

    /** Greedy insert — for each unassigned slot, pick the least-loaded eligible staff. */
    public static RepairOperator greedy() {
        return new RepairOperator() {
            @Override public String name() { return "greedy"; }
            @Override public int repair(WorkingSolution solution, int expectedInsertions) {
                int inserted = 0;
                var descriptor = solution.getDescriptor();
                var problem = descriptor.getProblem();
                int[] load = new int[descriptor.staffCount()];
                for (var a : solution.getAssignments()) {
                    if (a.staffId > 0) {
                        int idx = descriptor.staffIndex(a.staffId);
                        if (idx >= 0) load[idx]++;
                    }
                }
                for (int i = 0; i < solution.getAssignments().size() && inserted < expectedInsertions; i++) {
                    var a = solution.getAssignment(i);
                    if (a == null || a.staffId > 0) continue;
                    List<Integer> eligible = problem.getEligibleStaff(a.slotId);
                    if (eligible.isEmpty()) continue;
                    int bestStaff = -1;
                    int bestLoad = Integer.MAX_VALUE;
                    for (int sId : eligible) {
                        int idx = descriptor.staffIndex(sId);
                        if (idx < 0) continue;
                        if (load[idx] < bestLoad) {
                            bestLoad = load[idx];
                            bestStaff = sId;
                        }
                    }
                    if (bestStaff > 0) {
                        solution.assign(a.slotId, bestStaff);
                        int idx = descriptor.staffIndex(bestStaff);
                        if (idx >= 0) load[idx]++;
                        inserted++;
                    }
                }
                return inserted;
            }
        };
    }

    /** Regret insert — picks the unassigned slot with the largest "second-best minus best" cost. */
    public static RepairOperator regret() {
        return new RepairOperator() {
            @Override public String name() { return "regret"; }
            @Override public int repair(WorkingSolution solution, int expectedInsertions) {
                int inserted = 0;
                var descriptor = solution.getDescriptor();
                var problem = descriptor.getProblem();
                int[] load = new int[descriptor.staffCount()];
                for (var a : solution.getAssignments()) {
                    if (a.staffId > 0) {
                        int idx = descriptor.staffIndex(a.staffId);
                        if (idx >= 0) load[idx]++;
                    }
                }
                while (inserted < expectedInsertions) {
                    int pickSlot = -1;
                    int pickStaff = -1;
                    double bestRegret = Double.NEGATIVE_INFINITY;
                    for (int i = 0; i < solution.getAssignments().size(); i++) {
                        var a = solution.getAssignment(i);
                        if (a == null || a.staffId > 0) continue;
                        List<Integer> eligible = problem.getEligibleStaff(a.slotId);
                        if (eligible.size() < 2) continue;
                        int bestIdx = -1;
                        int bestLoad = Integer.MAX_VALUE;
                        int secondLoad = Integer.MAX_VALUE;
                        for (int sId : eligible) {
                            int idx = descriptor.staffIndex(sId);
                            if (idx < 0) continue;
                            int l = load[idx];
                            if (l < bestLoad) {
                                secondLoad = bestLoad;
                                bestLoad = l;
                                bestIdx = sId;
                            } else if (l < secondLoad) {
                                secondLoad = l;
                            }
                        }
                        double regret = secondLoad == Integer.MAX_VALUE ? 0 : secondLoad - bestLoad;
                        if (bestIdx > 0 && regret > bestRegret) {
                            bestRegret = regret;
                            pickSlot = a.slotId;
                            pickStaff = bestIdx;
                        }
                    }
                    if (pickStaff <= 0) break;
                    solution.assign(pickSlot, pickStaff);
                    int idx = descriptor.staffIndex(pickStaff);
                    if (idx >= 0) load[idx]++;
                    inserted++;
                }
                return inserted;
            }
        };
    }

    /** Perturbed insert — greedy but picks the top-3 least-loaded then randomises. */
    public static RepairOperator perturbed(java.util.Random rng) {
        return new RepairOperator() {
            @Override public String name() { return "perturbed"; }
            @Override public int repair(WorkingSolution solution, int expectedInsertions) {
                int inserted = 0;
                var descriptor = solution.getDescriptor();
                var problem = descriptor.getProblem();
                int[] load = new int[descriptor.staffCount()];
                for (var a : solution.getAssignments()) {
                    if (a.staffId > 0) {
                        int idx = descriptor.staffIndex(a.staffId);
                        if (idx >= 0) load[idx]++;
                    }
                }
                for (int i = 0; i < solution.getAssignments().size() && inserted < expectedInsertions; i++) {
                    var a = solution.getAssignment(i);
                    if (a == null || a.staffId > 0) continue;
                    List<Integer> eligible = problem.getEligibleStaff(a.slotId);
                    if (eligible.isEmpty()) continue;
                    java.util.Collections.shuffle(eligible, rng);
                    int pick = eligible.get(0);
                    solution.assign(a.slotId, pick);
                    int idx = descriptor.staffIndex(pick);
                    if (idx >= 0) load[idx]++;
                    inserted++;
                }
                return inserted;
            }
        };
    }
}