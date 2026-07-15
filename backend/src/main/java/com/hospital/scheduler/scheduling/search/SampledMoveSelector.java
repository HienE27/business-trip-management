package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.StaffNode;
import com.hospital.scheduler.scheduling.move.AssignMove;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.move.SwapMove;
import com.hospital.scheduler.scheduling.solution.MutableAssignment;
import com.hospital.scheduler.scheduling.solution.WorkingSolution;
import com.hospital.scheduler.scheduling.statistics.LoadStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Sampled move selector that generates moves from a critical neighborhood.
 * 
 * <p>Instead of enumerating all possible moves, this selector:
 * <ul>
 *   <li>Finds top K overloaded and bottom K underloaded staff</li>
 *   <li>Generates assign moves from overloaded to underloaded</li>
 *   <li>Generates swap moves between critical staff</li>
 *   <li>Adds random moves for diversification</li>
 * </ul>
 */
@Slf4j
@Component
public class SampledMoveSelector implements MoveSelector {

    private final SchedulingConfig config;
    private final Random random = new Random();

    public SampledMoveSelector(SchedulingConfig config) {
        this.config = config;
    }

    @Override
    public List<Move> select(SearchState state, SelectionContext context) {
        List<Move> moves = new ArrayList<>();
        WorkingSolution solution = context.solution();

        // 1. Find critical staff
        int k = config.getSearch().getNeighborhoodSize();
        int[] overloaded = findTopKOverloaded(context, k);
        int[] underloaded = findBottomKUnderloaded(context, k);

        // 2. Generate assign moves: from overloaded to underloaded
        for (int overIdx : overloaded) {
            int overStaffId = context.descriptor().getStaffId(overIdx);
            List<MutableAssignment> assignments = solution.getAssignmentsByStaff(overStaffId);

            for (MutableAssignment a : assignments) {
                for (int underIdx : underloaded) {
                    int underStaffId = context.descriptor().getStaffId(underIdx);
                    
                    if (overStaffId == underStaffId) continue;
                    
                    if (isEligible(underStaffId, a, context)) {
                        moves.add(new AssignMove(
                                a.slotId,
                                overStaffId,
                                underStaffId,
                                context.descriptor()
                        ));
                    }
                }
            }
        }

        // 3. Generate swap moves between overloaded and underloaded
        if (moves.size() < config.getSearch().getCandidateListSize() / 2) {
            for (int i = 0; i < Math.min(overloaded.length, underloaded.length); i++) {
                int staffA = context.descriptor().getStaffId(overloaded[i]);
                int staffB = context.descriptor().getStaffId(underloaded[i]);

                List<MutableAssignment> assignmentsA = solution.getAssignmentsByStaff(staffA);
                List<MutableAssignment> assignmentsB = solution.getAssignmentsByStaff(staffB);

                if (!assignmentsA.isEmpty() && !assignmentsB.isEmpty()) {
                    MutableAssignment a = assignmentsA.get(random.nextInt(assignmentsA.size()));
                    MutableAssignment b = assignmentsB.get(random.nextInt(assignmentsB.size()));
                    
                    if (isCompatibleSwappable(a, b, context)) {
                        moves.add(new SwapMove(a.slotId, b.slotId, context.descriptor()));
                    }
                }
            }
        }

        // 4. Random sampling if not enough moves
        int targetSize = config.getSearch().getCandidateListSize();
        if (moves.size() < targetSize) {
            moves.addAll(generateRandomMoves(solution, context, targetSize - moves.size()));
        }

        // 5. Limit and shuffle
        if (moves.size() > targetSize) {
            Collections.shuffle(moves, random);
            moves = moves.subList(0, targetSize);
        }

        return moves;
    }

    private int[] findTopKOverloaded(SelectionContext context, int k) {
        LoadStatistics stats = context.statistics().get(LoadStatistics.class);
        if (stats == null) return new int[0];
        return stats.getTopKOverloaded(k);
    }

    private int[] findBottomKUnderloaded(SelectionContext context, int k) {
        LoadStatistics stats = context.statistics().get(LoadStatistics.class);
        if (stats == null) return new int[0];
        return stats.getBottomKUnderloaded(k);
    }

    private boolean isEligible(int staffId, MutableAssignment assignment, SelectionContext context) {
        // Check leave
        if (context.problem().hasLeave(staffId, assignment.date)) {
            return false;
        }

        // Check compensation day
        if (context.problem().isCompensationDay(staffId, assignment.date)) {
            return false;
        }

        // Check holiday
        if (context.problem().isHoliday(assignment.date)) {
            return false;
        }

        // Check eligibility
        StaffNode staff = context.problem().getStaff(staffId);
        if (staff == null) return false;

        ShiftRequirementInfo req = context.problem().getRequirement(assignment.slotId);
        if (req == null) return false;

        return staff.isEligibleFor(req.getShiftTypeId(), req.getSpecialtyId());
    }

    private boolean isCompatibleSwappable(MutableAssignment a, MutableAssignment b,
                                         SelectionContext context) {
        // For swap, we need to check if swapping is feasible
        // i.e., if each staff is eligible for the other's slot

        StaffNode staffA = context.problem().getStaff(a.staffId);
        StaffNode staffB = context.problem().getStaff(b.staffId);

        if (staffA == null || staffB == null) return false;

        ShiftRequirementInfo reqA = context.problem().getRequirement(a.slotId);
        ShiftRequirementInfo reqB = context.problem().getRequirement(b.slotId);

        if (reqA == null || reqB == null) return false;

        // Check if staffA is eligible for slot B and vice versa
        boolean aToB = staffA.isEligibleFor(reqB.getShiftTypeId(), reqB.getSpecialtyId())
                && !context.problem().hasLeave(a.staffId, reqB.getDate())
                && !context.problem().isCompensationDay(a.staffId, reqB.getDate());

        boolean bToA = staffB.isEligibleFor(reqA.getShiftTypeId(), reqA.getSpecialtyId())
                && !context.problem().hasLeave(b.staffId, reqA.getDate())
                && !context.problem().isCompensationDay(b.staffId, reqA.getDate());

        return aToB && bToA;
    }

    private List<Move> generateRandomMoves(WorkingSolution solution, SelectionContext context, int count) {
        List<Move> moves = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            List<ShiftRequirementInfo> requirements = context.problem().getRequirements();
            if (requirements.isEmpty()) continue;

            ShiftRequirementInfo req = requirements.get(random.nextInt(requirements.size()));
            List<StaffNode> staff = context.problem().getStaffList();
            if (staff.isEmpty()) continue;

            StaffNode targetStaff = staff.get(random.nextInt(staff.size()));

            if (isEligible(targetStaff.getId(), solution.getAssignment(req.getSlotId()), context)) {
                MutableAssignment existing = solution.getAssignment(req.getSlotId());
                int oldStaffId = existing != null ? existing.staffId : -1;

                moves.add(new AssignMove(
                        req.getSlotId(),
                        oldStaffId,
                        targetStaff.getId(),
                        context.descriptor()
                ));
            }
        }

        return moves;
    }
}
