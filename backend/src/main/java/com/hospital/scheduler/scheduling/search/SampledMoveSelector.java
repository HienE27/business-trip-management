package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.AssignMove;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.move.SwapMove;
import com.hospital.scheduler.scheduling.move.UnassignMove;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

import java.time.LocalDate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Sampled move selector — picks moves from a randomized subset of the
 * neighborhood. Each iteration, sample {@code batchSize} candidate moves
 * from a mix of:
 * <ul>
 *   <li>AssignMove — put a BR-04-aware eligible staff into a random unassigned slot</li>
 *   <li>ChangeStaffMove — replace the staff of a random assigned slot</li>
 *   <li>SwapMove — swap two random assigned slots' staff</li>
 *   <li>UnassignMove — remove staff from a random assigned slot</li>
 * </ul>
 *
 * <p>BR-04 aware: when selecting staff for an L01 slot, filters out candidates
 * who already have L01 on the day before or after. This prevents the search
 * from creating new adjacent-L01 violations while trying to fix existing ones.
 *
 * <p>Eligible-staff lookup is delegated to {@code SchedulingProblem.getEligibleStaff}.
 */
public class SampledMoveSelector implements MoveSelector {

    private final SolutionDescriptor descriptor;
    private final SchedulingConfig config;
    private final Random random = new Random(42);

    public SampledMoveSelector(SolutionDescriptor descriptor, SchedulingConfig config) {
        this.descriptor = descriptor;
        this.config = config;
    }

    @Override
    public List<Move> select(WorkingSolution solution, int batchSize) {
        List<Move> moves = new ArrayList<>();
        if (batchSize <= 0) return moves;

        Set<Integer> unassignedSlots = new HashSet<>();
        Set<Integer> assignedSlots = new HashSet<>();
        for (var a : solution.getAssignments()) {
            if (a.staffId > 0) assignedSlots.add(a.slotId);
            else unassignedSlots.add(a.slotId);
        }

        for (int i = 0; i < batchSize; i++) {
            double r = random.nextDouble();
            if (r < 0.5 && !unassignedSlots.isEmpty()) {
                // ASSIGN: pick an unassigned slot + a BR-04-aware eligible staff
                int slot = pickRandom(unassignedSlots);
                List<Integer> eligible = descriptor.getProblem().getEligibleStaff(slot);
                if (!eligible.isEmpty()) {
                    ShiftRequirementInfo req = descriptor.getProblem()
                            .getRequirementsById().get(slot);
                    int staff = pickStaffForSlot(solution, slot, req, eligible);
                    if (staff > 0) {
                        moves.add(new AssignMove(slot, staff));
                    }
                }
            } else if (r < 0.85 && !assignedSlots.isEmpty()) {
                // CHANGE-STAFF: pick an assigned slot and reassign BR-04-aware
                int slot = pickRandom(assignedSlots);
                List<Integer> eligible = descriptor.getProblem().getEligibleStaff(slot);
                if (!eligible.isEmpty()) {
                    ShiftRequirementInfo req = descriptor.getProblem()
                            .getRequirementsById().get(slot);
                    int staff = pickStaffForSlot(solution, slot, req, eligible);
                    if (staff > 0 && staff != solution.getAssignedStaff(slot)) {
                        moves.add(new com.hospital.scheduler.scheduling.move.ChangeStaffMove(slot, staff));
                    }
                }
            } else if (r < 0.95 && assignedSlots.size() >= 2) {
                // SWAP: pick two assigned slots, preferring BR-04-safe swaps.
                // When no BR-04-safe swap exists, pick any random swap — the hard-fence
                // in the search loop will reject it if it creates violations.
                List<Integer> slotList = new ArrayList<>(assignedSlots);
                SwapMove bestSwap = null;
                for (int attempt = 0; attempt < Math.min(5, slotList.size()); attempt++) {
                    int a = slotList.get(random.nextInt(slotList.size()));
                    int b = slotList.get(random.nextInt(slotList.size()));
                    if (a == b) continue;
                    SwapMove candidate = new SwapMove(a, b);
                    if (!wouldCreateBR04Violation(solution, a, b)) {
                        bestSwap = candidate;
                        break;
                    }
                }
                if (bestSwap != null) {
                    moves.add(bestSwap);
                } else {
                    // No BR-04-safe swap found — add a random one; hard-fence will reject it
                    int a = slotList.get(random.nextInt(slotList.size()));
                    int b = slotList.get(random.nextInt(slotList.size()));
                    if (a != b) moves.add(new SwapMove(a, b));
                }
            } else if (!assignedSlots.isEmpty()) {
                // UNASSIGN: clear an assigned slot
                int slot = pickRandom(assignedSlots);
                moves.add(new UnassignMove(slot));
            }
        }
        return moves;
    }

    /**
     * Pick a staff for {@code slot} from {@code candidates} while avoiding BR-04
     * violations (adjacent L01). If all candidates would create a BR-04 violation,
     * returns the least-loaded candidate anyway — the hard-fence in the search
     * loop will reject any move that increases hard violations.
     */
    private int pickStaffForSlot(WorkingSolution solution, int slotId,
                                 ShiftRequirementInfo req, List<Integer> candidates) {
        boolean isL01 = "L01".equals(req.shiftTypeId());
        LocalDate reqDate = req.date();

        int bestStaff = -1;
        int bestLoad = Integer.MAX_VALUE;
        int fallbackStaff = -1;
        int fallbackLoad = Integer.MAX_VALUE;

        for (int staffId : candidates) {
            int load = solution.getShiftCount(staffId);

            if (isL01) {
                boolean adjacent = false;
                for (int otherSlot : solution.getSlotsAssignedTo(staffId)) {
                    MutableAssignment other = solution.getAssignment(otherSlot);
                    if (other != null && "L01".equals(other.shiftTypeId)
                            && other.date != null && reqDate != null) {
                        if (other.date.plusDays(1).equals(reqDate)
                                || other.date.minusDays(1).equals(reqDate)) {
                            adjacent = true;
                            break;
                        }
                    }
                }
                if (!adjacent && load < bestLoad) {
                    bestStaff = staffId;
                    bestLoad = load;
                } else if (adjacent && load < fallbackLoad) {
                    fallbackStaff = staffId;
                    fallbackLoad = load;
                }
            } else {
                if (load < bestLoad) {
                    bestStaff = staffId;
                    bestLoad = load;
                }
            }
        }

        return bestStaff > 0 ? bestStaff : fallbackStaff;
    }

    /**
     * Check if swapping staff between slotA and slotB would create a BR-04 violation
     * (adjacent L01) for either staff after the swap.
     *
     * <p>After swapping: slotA gets staff from slotB, slotB gets staff from slotA.
     * We check whether either staff would have two L01 slots within 1 day of each other.
     */
    private boolean wouldCreateBR04Violation(WorkingSolution solution, int slotA, int slotB) {
        MutableAssignment a = solution.getAssignment(slotA);
        MutableAssignment b = solution.getAssignment(slotB);
        if (a == null || b == null) return false;

        int staffA = a.staffId;
        int staffB = b.staffId;
        LocalDate dateA = a.date;
        LocalDate dateB = b.date;
        String typeA = a.shiftTypeId;
        String typeB = b.shiftTypeId;

        // If either slot is not L01, swapping cannot create BR-04 for that slot's staff
        // (swapping L02→L01 for staffB doesn't directly create BR-04, it's about their own L01s)

        // Check staffA: after swap, staffA gets slotB's date/type. Does staffA already have
        // an L01 adjacent to dateB?
        if ("L01".equals(typeB) && staffA > 0 && dateB != null) {
            for (int otherSlot : solution.getSlotsAssignedTo(staffA)) {
                if (otherSlot == slotA || otherSlot == slotB) continue;
                MutableAssignment other = solution.getAssignment(otherSlot);
                if (other != null && "L01".equals(other.shiftTypeId)
                        && other.date != null) {
                    if (other.date.plusDays(1).equals(dateB)
                            || other.date.minusDays(1).equals(dateB)) {
                        return true;
                    }
                }
            }
        }

        // Check staffB: after swap, staffB gets slotA's date/type.
        if ("L01".equals(typeA) && staffB > 0 && dateA != null) {
            for (int otherSlot : solution.getSlotsAssignedTo(staffB)) {
                if (otherSlot == slotA || otherSlot == slotB) continue;
                MutableAssignment other = solution.getAssignment(otherSlot);
                if (other != null && "L01".equals(other.shiftTypeId)
                        && other.date != null) {
                    if (other.date.plusDays(1).equals(dateA)
                            || other.date.minusDays(1).equals(dateA)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private int pickRandom(Set<Integer> set) {
        int idx = random.nextInt(set.size());
        int i = 0;
        for (Integer v : set) {
            if (i++ == idx) return v;
        }
        return -1;
    }
}