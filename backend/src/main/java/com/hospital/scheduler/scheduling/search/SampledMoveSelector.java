package com.hospital.scheduler.scheduling.search;

import com.hospital.scheduler.scheduling.config.SchedulingConfig;
import com.hospital.scheduler.scheduling.domain.ShiftRequirementInfo;
import com.hospital.scheduler.scheduling.domain.SolutionDescriptor;
import com.hospital.scheduler.scheduling.move.AssignMove;
import com.hospital.scheduler.scheduling.move.Move;
import com.hospital.scheduler.scheduling.move.OrOptMove;
import com.hospital.scheduler.scheduling.move.SwapMove;
import com.hospital.scheduler.scheduling.move.TwoOptMove;
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
                // L04 strict-specialty không có hard-fence nên swap đưa bs sai khoa
                // vào slot L04 phải bị loại ngay tại selector.
                List<Integer> slotList = new ArrayList<>(assignedSlots);
                SwapMove bestSwap = null;
                for (int attempt = 0; attempt < Math.min(5, slotList.size()); attempt++) {
                    int a = slotList.get(random.nextInt(slotList.size()));
                    int b = slotList.get(random.nextInt(slotList.size()));
                    if (a == b) continue;
                    SwapMove candidate = new SwapMove(a, b);
                    if (!wouldCreateBR04Violation(solution, a, b)
                            && !wouldBreakL04Specialty(solution, a, b)) {
                        bestSwap = candidate;
                        break;
                    }
                }
                if (bestSwap != null) {
                    moves.add(bestSwap);
                } else {
                    // No BR-04-safe swap found — add a random one (still L04-specialty-safe);
                    // hard-fence will reject BR-04 violations.
                    int a = slotList.get(random.nextInt(slotList.size()));
                    int b = slotList.get(random.nextInt(slotList.size()));
                    if (a != b && !wouldBreakL04Specialty(solution, a, b)) {
                        moves.add(new SwapMove(a, b));
                    }
                }
            } else if (r < 0.975 && !assignedSlots.isEmpty()) {
                // TWO-OPT (OPT-001 #2): reverse a subsequence of L01 slots for one staff.
                // Picks a random staff with ≥2 L01 slots and tries a validated reversal.
                TwoOptMove twoOpt = generateTwoOptMove(solution);
                if (twoOpt != null) {
                    moves.add(twoOpt);
                }
            } else if (r < 0.99 && !assignedSlots.isEmpty()) {
                // OR-OPT (OPT-001 #3): relocate a chain of 1-3 consecutive L01 slots.
                // Picks a random staff with ≥1 L01 slot and tries a validated relocation.
                OrOptMove orOpt = generateOrOptMove(solution);
                if (orOpt != null) {
                    moves.add(orOpt);
                }
            } else {
                // UNASSIGN: clear an assigned slot — but ONLY an L04 slot.
                // BUGFIX (M08-PRIORITY-V10): the spec runs the algorithm in
                // priority order L01→L02→L03→L04 (M07-B3). Letting the search
                // unassign L01/L02/L03 made it "spend" staff on L04 instead —
                // preview collapsed L01=15 / L02=10 vs ~145 demanded. L04 is
                // the lowest-priority buffer type; churn there never steals a
                // higher-priority slot, while AssignMove can still top up L04.
                java.util.List<Integer> l04Slots = new ArrayList<>();
                for (Integer s : assignedSlots) {
                    ShiftRequirementInfo req = descriptor.getProblem()
                            .getRequirementsById().get(s);
                    if (req != null && "L04".equals(req.shiftTypeId())) {
                        l04Slots.add(s);
                    }
                }
                if (!l04Slots.isEmpty()) {
                    moves.add(new UnassignMove(l04Slots.get(random.nextInt(l04Slots.size()))));
                }
            }
        }
        return moves;
    }

    /**
     * Pick a staff for {@code slot} from {@code candidates} while avoiding BR-04
     * violations (adjacent L01) and preferring fatigue-aware selection
     * (staff with a rest day before the requirement date).
     *
     * <p>Sort priority:
     * <ol>
     *   <li>BR-04 safe (non-adjacent L01)</li>
     *   <li>Fatigue: has rest day before req date (gap >= 1)</li>
     *   <li>Lowest workload</li>
     * </ol>
     * If all candidates would create a BR-04 violation, falls back to the
     * least-loaded candidate — hard-fence in search loop rejects bad moves.
     */
    private int pickStaffForSlot(WorkingSolution solution, int slotId,
                                 ShiftRequirementInfo req, List<Integer> candidates) {
        boolean isL01 = "L01".equals(req.shiftTypeId());
        LocalDate reqDate = req.date();

        int bestStaff = -1;
        long bestScore = Long.MAX_VALUE;
        int fallbackStaff = -1;
        long fallbackScore = Long.MAX_VALUE;

        for (int staffId : candidates) {
            // BUGFIX (M08-COMPDAY-V10): a staff whose L01 placed earlier in
            // this run earns a comp day on reqDate cannot work any shift then.
            // Filter here so the hard-fence is rarely hit, not never relied on.
            if (solution.isOnDerivedCompDay(staffId, reqDate)) continue;

            int load = solution.getShiftCount(staffId);
            boolean adjacent = false;
            boolean consecutive = false; // no rest day before req date

            if (isL01) {
                for (int otherSlot : solution.getSlotsAssignedTo(staffId)) {
                    MutableAssignment other = solution.getAssignment(otherSlot);
                    if (other == null || other.date == null || reqDate == null) continue;
                    long gap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(other.date, reqDate));

                    if ("L01".equals(other.shiftTypeId) && gap <= 1) {
                        adjacent = true;
                        // continue scanning for fatigue even if adjacent L01 found
                    }
                    if (gap == 0 || gap == 1) {
                        consecutive = true; // working consecutive days (any shift type)
                    }
                }
            } else {
                // Non-L01: check consecutive days
                for (int otherSlot : solution.getSlotsAssignedTo(staffId)) {
                    MutableAssignment other = solution.getAssignment(otherSlot);
                    if (other == null || other.date == null || reqDate == null) continue;
                    long gap = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(other.date, reqDate));
                    if (gap == 0 || gap == 1) {
                        consecutive = true;
                        break;
                    }
                }
            }

            // Score: lower is better
            // BR-04 violation → highest penalty (100_000)
            // consecutive (no rest) → medium penalty (10_000)
            // then tiebreak by load
            long adjPenalty = adjacent ? 100_000L : 0L;
            long fatiguePenalty = (!adjacent && consecutive) ? 10_000L : 0L;
            long score = adjPenalty + fatiguePenalty + load;

            if (!adjacent && score < bestScore) {
                bestStaff = staffId;
                bestScore = score;
            } else if (adjacent && score < fallbackScore) {
                fallbackStaff = staffId;
                fallbackScore = score;
            }
        }

        return bestStaff > 0 ? bestStaff : fallbackStaff;
    }

    /**
     * True nếu swap giữa slotA/slotB đặt một nhân sự không đúng chuyên khoa
     * vào slot L04. L04 luôn strict-specialty (cross-specialty đã gỡ) nhưng
     * việc này không nằm trong hard-fence của search loop nên phải loại ngay
     * tại selector để KPI "lệch chuyên khoa" không bao giờ dương.
     */
    private boolean wouldBreakL04Specialty(WorkingSolution solution, int slotA, int slotB) {
        MutableAssignment a = solution.getAssignment(slotA);
        MutableAssignment b = solution.getAssignment(slotB);
        if (a == null || b == null || a.staffId <= 0 || b.staffId <= 0) return false;
        var problem = descriptor.getProblem();
        // Sau swap: slotA nhận staff của slotB, slotB nhận staff của slotA.
        if ("L04".equals(a.shiftTypeId) && !problem.isStrictSpecialtyMatch(slotA, b.staffId)) return true;
        if ("L04".equals(b.shiftTypeId) && !problem.isStrictSpecialtyMatch(slotB, a.staffId)) return true;
        return false;
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

    // ── OPT-001: TWO-OPT move generation ─────────────────────────────────────

    /**
     * Generate a 2-opt move: reverse a subsequence of L01 slots for one staff.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Pick a random staff who has at least 2 L01 slots.</li>
     *   <li>Collect all their L01 slots and pick two distinct endpoints
     *       (by date) to define the reversal window.</li>
     *   <li>Call {@link TwoOptMove#buildValidated} to confirm the reversal
     *       does not create a BR-04 violation.</li>
     * </ol>
     *
     * <p>Returns null if no valid 2-opt move can be generated.
     */
    private TwoOptMove generateTwoOptMove(WorkingSolution solution) {
        // Build a list of staff who have ≥2 L01 slots
        List<Integer> staffWithL01 = new ArrayList<>();
        for (var a : solution.getAssignments()) {
            if (a.staffId <= 0) continue;
            if (!"L01".equals(a.shiftTypeId)) continue;
            if (!staffWithL01.contains(a.staffId)) {
                // Count L01 slots for this staff
                int count = 0;
                for (int slotId : solution.getSlotsAssignedTo(a.staffId)) {
                    MutableAssignment ma = solution.getAssignment(slotId);
                    if (ma != null && "L01".equals(ma.shiftTypeId)) count++;
                }
                if (count >= 2) staffWithL01.add(a.staffId);
            }
        }
        if (staffWithL01.isEmpty()) return null;

        int staffId = staffWithL01.get(random.nextInt(staffWithL01.size()));

        // Collect L01 slots for this staff, sorted by date
        List<MutableAssignment> l01Slots = new ArrayList<>();
        for (int slotId : solution.getSlotsAssignedTo(staffId)) {
            MutableAssignment ma = solution.getAssignment(slotId);
            if (ma != null && "L01".equals(ma.shiftTypeId) && ma.date != null) {
                l01Slots.add(ma);
            }
        }
        if (l01Slots.size() < 2) return null;
        l01Slots.sort(java.util.Comparator.comparing(a -> a.date));

        // Pick two distinct endpoints
        int i = random.nextInt(l01Slots.size());
        int j;
        do { j = random.nextInt(l01Slots.size()); } while (j == i);

        LocalDate start = l01Slots.get(Math.min(i, j)).date;
        LocalDate end = l01Slots.get(Math.max(i, j)).date;

        return TwoOptMove.buildValidated(solution, staffId, start, end);
    }

    // ── OPT-001: OR-OPT move generation ──────────────────────────────────────

    /**
     * Generate an Or-opt move: relocate a chain of 1-3 consecutive L01 slots
     * from one gap position to another within the same staff's schedule.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Pick a random staff who has at least 1 L01 slot.</li>
     *   <li>Call {@link OrOptMove#buildValidated} which searches for a
     *       source L01 and a valid target gap for the chain.</li>
     * </ol>
     *
     * <p>Returns null if no valid Or-opt move can be generated.
     */
    private OrOptMove generateOrOptMove(WorkingSolution solution) {
        // Pick a random staff with at least 1 L01 slot
        List<Integer> staffWithL01 = new ArrayList<>();
        for (var a : solution.getAssignments()) {
            if (a.staffId <= 0) continue;
            if (!"L01".equals(a.shiftTypeId)) continue;
            if (!staffWithL01.contains(a.staffId)) {
                staffWithL01.add(a.staffId);
            }
        }
        if (staffWithL01.isEmpty()) return null;

        int staffId = staffWithL01.get(random.nextInt(staffWithL01.size()));

        // Let OrOptMove.buildValidated do the heavy lifting
        return OrOptMove.buildValidated(solution, staffId, -1);
    }
}