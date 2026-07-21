package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.AssignMove;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.move.SwapMove;
import com.hospital.scheduler.scheduling.move.UnassignMove;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;

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
 *   <li>AssignMove — put a random eligible staff into a random unassigned slot</li>
 *   <li>ChangeStaffMove — replace the staff of a random assigned slot</li>
 *   <li>SwapMove — swap two random assigned slots' staff</li>
 *   <li>UnassignMove — remove staff from a random assigned slot</li>
 * </ul>
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
                // ASSIGN: pick an unassigned slot + an eligible staff
                int slot = pickRandom(unassignedSlots);
                List<Integer> eligible = descriptor.getProblem().getEligibleStaff(slot);
                if (!eligible.isEmpty()) {
                    int staff = eligible.get(random.nextInt(eligible.size()));
                    moves.add(new AssignMove(slot, staff));
                }
            } else if (r < 0.85 && !assignedSlots.isEmpty()) {
                // CHANGE-STAFF: pick an assigned slot and reassign
                int slot = pickRandom(assignedSlots);
                List<Integer> eligible = descriptor.getProblem().getEligibleStaff(slot);
                if (!eligible.isEmpty()) {
                    int staff = eligible.get(random.nextInt(eligible.size()));
                    if (staff != solution.getAssignedStaff(slot)) {
                        moves.add(new com.hospital.scheduler.scheduling.move.ChangeStaffMove(slot, staff));
                    }
                }
            } else if (r < 0.95 && assignedSlots.size() >= 2) {
                // SWAP: pick two assigned slots
                List<Integer> slotList = new ArrayList<>(assignedSlots);
                int a = slotList.get(random.nextInt(slotList.size()));
                int b = slotList.get(random.nextInt(slotList.size()));
                if (a != b) moves.add(new SwapMove(a, b));
            } else if (!assignedSlots.isEmpty()) {
                // UNASSIGN: clear an assigned slot
                int slot = pickRandom(assignedSlots);
                moves.add(new UnassignMove(slot));
            }
        }
        return moves;
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