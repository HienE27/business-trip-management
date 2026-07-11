package com.hospital.scheduler.algorithm;

import com.hospital.scheduler.util.CompensationDateCalculator;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hospital.scheduler.algorithm.CspConstants.DIRECT_24H;
import static com.hospital.scheduler.algorithm.CspConstants.SHIFT_ORDER;
import static com.hospital.scheduler.algorithm.CspConstants.conflicts;

/**
 * Backtracking search with MRV heuristic, AC-3 propagation, and nogood
 * learning. Returns a {@link Result} that the orchestrator can hand to the
 * result builder.
 */
@Component
@RequiredArgsConstructor
class CspSearchEngine {

    private static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final CompensationDateCalculator compensationDateCalculator;
    private final CspNogoodStore nogoodStore;

    /**
     * Solve with the default 30s timeout (production / auto-schedule path).
     */
    Result solve(ProblemData data, long startTime) {
        return solve(data, startTime, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Solve with a caller-supplied timeout. Used by the preview path so
     * that preview responses come back fast (typically &lt; 8s) even when
     * the full run would have taken the full 30s budget. The preview
     * contract is "show a feasible plan quickly"; if a tighter timeout
     * exhausts the search the orchestrator still gets a partial / empty
     * result and can surface a "preview unavailable" message without
     * blocking the user for 30s.
     */
    Result solve(ProblemData data, long startTime, long timeoutMs) {
        BitSet[] domains = copyDomains(data);
        int[] assignment = new int[data.numVars];
        java.util.Arrays.fill(assignment, -1);
        int[] staffWorkload = new int[data.numStaff];
        // Per-type workload: staffShiftWorkload[staffIdx][shiftIdx] = how many of that type assigned
        int[][] staffShiftWorkload = new int[data.numStaff][data.numShifts];
        BitSet[] restDays = new BitSet[data.numStaff];
        for (int i = 0; i < data.numStaff; i++) {
            restDays[i] = new BitSet(data.numDays);
        }

        // Gap 1: week-bucketed per-(staff, shift) counter for under-min biasing.
        // numWeeks = ceil(numDays / 7) so the last partial week is still tracked.
        int numWeeks = data.dayToWeek == null ? 0
                : (data.dayToWeek.length == 0 ? 0 : data.dayToWeek[data.dayToWeek.length - 1] + 1);
        CspConstraints.MinWeekTracker weekTracker = (numWeeks > 0)
                ? new CspConstraints.MinWeekTracker(data.numStaff, data.numShifts, numWeeks,
                        data.minShiftsPerWeekByShift)
                : null;

        int maxTrail = data.numVars * data.numStaff + 1000;
        int[] trailVar = new int[maxTrail];
        int[] trailStaff = new int[maxTrail];
        int[] trailPtr = {0};

        boolean found = search(domains, assignment, staffWorkload, staffShiftWorkload, restDays,
                trailVar, trailStaff, trailPtr, data, startTime, weekTracker, timeoutMs);
        if (!found) {
            return Result.builder().valid(false).errors(List.of("Không tìm được lịch hợp lệ")).build();
        }

        Map<String, Boolean> result = new HashMap<>();
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] >= 0) {
                result.put(assignment[v] + "|" + data.varDay[v] + "|" + data.varShift[v], true);
            }
        }
        return Result.builder().valid(true).assignment(result).build();
    }

    private boolean search(
            BitSet[] domains, int[] assignment, int[] staffWorkload, int[][] staffShiftWorkload,
            BitSet[] restDays, int[] trailVar, int[] trailStaff, int[] trailPtr,
            ProblemData data, long startTime, CspConstraints.MinWeekTracker weekTracker,
            long timeoutMs) {

        if (System.currentTimeMillis() - startTime > timeoutMs) return false;
        if (isGoal(domains, assignment, data)) return true;

        int var = selectMRV(domains, assignment, data);
        if (var < 0) return true;

        int dayIdx = data.varDay[var];
        int shiftIdx = data.varShift[var];
        String shiftType = SHIFT_ORDER[shiftIdx];

        // Sort candidates: fewest of THIS shift type first, then fewest total — ensures even per-type distribution
        List<Integer> candidates = getCandidates(domains[var], staffWorkload, staffShiftWorkload, shiftIdx,
                weekTracker, dayIdx, shiftIdx);

        for (int staffIdx : candidates) {
            if (nogoodStore.violatesNogood(assignment, var, staffIdx, data.numVars)) continue;
            if (!isConsistent(staffIdx, var, assignment, restDays, staffWorkload, data)) continue;

            int trailBefore = trailPtr[0];
            assignment[var] = staffIdx;
            staffWorkload[staffIdx]++;
            staffShiftWorkload[staffIdx][shiftIdx]++;
            int week = data.dayToWeek == null ? -1 : data.dayToWeek[dayIdx];
            if (weekTracker != null) weekTracker.increment(staffIdx, shiftIdx, week);

            if (shiftType.equals(DIRECT_24H)) {
                int compDayIdx = getCompensationDayIdx(dayIdx, data);
                if (compDayIdx >= 0 && compDayIdx < data.numDays) restDays[staffIdx].set(compDayIdx);
            }

            if (propagate(staffIdx, var, domains, restDays, assignment,
                    trailVar, trailStaff, trailPtr, data)) {

                if (search(domains, assignment, staffWorkload, staffShiftWorkload, restDays,
                        trailVar, trailStaff, trailPtr, data, startTime, weekTracker, timeoutMs)) {
                    return true;
                }
            }

            // Learn from failure before rolling back
            Set<int[]> conflict = new java.util.HashSet<>();
            conflict.add(new int[]{var, staffIdx});
            for (int i = 0; i < trailPtr[0]; i++) {
                int tVar = trailVar[i];
                if (tVar >= 0 && tVar < data.numVars && assignment[tVar] >= 0) {
                    conflict.add(new int[]{tVar, assignment[tVar]});
                }
            }
            if (!conflict.isEmpty()) {
                nogoodStore.addNogood(conflict, "Domain wipeout or constraint violation");
            }

            // Rollback
            while (trailPtr[0] > trailBefore) {
                trailPtr[0]--;
                domains[trailVar[trailPtr[0]]].set(trailStaff[trailPtr[0]]);
            }
            assignment[var] = -1;
            staffWorkload[staffIdx]--;
            staffShiftWorkload[staffIdx][shiftIdx]--;
            if (weekTracker != null) weekTracker.decrement(staffIdx, shiftIdx, week);
            if (shiftType.equals(DIRECT_24H)) {
                int compDayIdx = getCompensationDayIdx(dayIdx, data);
                if (compDayIdx >= 0 && compDayIdx < data.numDays) restDays[staffIdx].clear(compDayIdx);
            }
        }
        return false;
    }

    private boolean propagate(
            int staffIdx, int var, BitSet[] domains, BitSet[] restDays, int[] assignment,
            int[] trailVar, int[] trailStaff, int[] trailPtr, ProblemData data) {

        int dayIdx = data.varDay[var];
        int shiftIdx = data.varShift[var];
        String shiftType = SHIFT_ORDER[shiftIdx];

        // 1. BR-01/02: same-day conflicting shifts — iterate only neighbors via constraintGraph
        for (int neighbor : data.constraintGraph[var]) {
            if (assignment[neighbor] >= 0) continue;
            if (data.varDay[neighbor] != dayIdx) continue;
            String otherShiftType = SHIFT_ORDER[data.varShift[neighbor]];
            if (conflicts(shiftType, otherShiftType) && domains[neighbor].get(staffIdx)) {
                domains[neighbor].clear(staffIdx);
                trailVar[trailPtr[0]] = neighbor;
                trailStaff[trailPtr[0]] = staffIdx;
                trailPtr[0]++;
            }
        }

        // 2. BR-03: rest day on compensation day — only iterate vars on the
        // specific comp-day, not the full var set.
        int compDayIdx = getCompensationDayIdx(dayIdx, data);
        if (compDayIdx >= 0 && compDayIdx < data.numDays && data.varsByDay != null) {
            for (int v : data.varsByDay[compDayIdx]) {
                if (v == var || assignment[v] >= 0) continue;
                if (domains[v].get(staffIdx)) {
                    domains[v].clear(staffIdx);
                    trailVar[trailPtr[0]] = v;
                    trailStaff[trailPtr[0]] = staffIdx;
                    trailPtr[0]++;
                }
            }
        }

        // 3. Detect failure: any unassigned variable we just touched has an
        // empty domain. We only need to check vars whose domains were modified
        // during this propagate() — anything else was already verified during
        // earlier propagate calls or AC-3.
        for (int i = 0; i < trailPtr[0]; i++) {
            int v = trailVar[i];
            if (v >= 0 && v < data.numVars && assignment[v] < 0 && domains[v].isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean isConsistent(
            int staffIdx, int var, int[] assignment, BitSet[] restDays,
            int[] staffWorkload, ProblemData data) {

        int dayIdx = data.varDay[var];
        int shiftIdx = data.varShift[var];
        String shiftType = SHIFT_ORDER[shiftIdx];

        if (restDays[staffIdx].get(dayIdx)) return false;
        if (!data.leaveMatrix[staffIdx][dayIdx]) return false;
        if (data.holidayDays[dayIdx]) return false;
        if (staffWorkload[staffIdx] >= data.staffMaxShifts[staffIdx]) return false;

        // BR-01/02: same-day conflicting shifts — check via constraintGraph neighbors
        for (int neighbor : data.constraintGraph[var]) {
            if (assignment[neighbor] == staffIdx) {
                String otherShiftType = SHIFT_ORDER[data.varShift[neighbor]];
                if (conflicts(shiftType, otherShiftType)) return false;
            }
        }

        // BR-06: at most one L01 per STAFF per day (slots on the same shift-
        // type same-day for different staff are allowed when requiredStaffCount > 1).
        // Earlier revisions of this check forgot the staff equality and
        // rejected every candidate whenever any L01 var on the same day was
        // already assigned — making CSP fail as soon as it assigned the very
        // first L01 slot and silently dropping that requirement. The staff
        // equality restores the intended semantics.
        if (shiftType.equals(DIRECT_24H) && data.varsByDay != null) {
            for (int v : data.varsByDay[dayIdx]) {
                if (v != var && assignment[v] == staffIdx
                        && SHIFT_ORDER[data.varShift[v]].equals(DIRECT_24H)) {
                    return false;
                }
            }
        }

        // Gap 2: BR-04 — adjacent L01 (cùng nhân sự, 2 ngày liên tiếp có L01).
        // Lookup is O(adjacentPairs) per check which is small (≤ 2*K where K is
        // number of L01 pairs in the period). Without this guard the CSP could
        // pick two adjacent L01s and rely on the post-hoc scorer to flag the
        // violation, which is far more expensive than a single early reject.
        if (shiftType.equals(DIRECT_24H) && data.adjacentL01Pairs != null) {
            for (int p = 0; p < data.adjacentL01PairCount; p++) {
                int base = p * 2;
                int v1 = data.adjacentL01Pairs[base];
                int v2 = data.adjacentL01Pairs[base + 1];
                int otherVar = (v1 == var) ? v2 : (v2 == var ? v1 : -1);
                if (otherVar >= 0 && assignment[otherVar] == staffIdx) {
                    return false;
                }
            }
        }
        return true;
    }

    private int selectMRV(BitSet[] domains, int[] assignment, ProblemData data) {
        int bestVar = -1;
        int minSize = Integer.MAX_VALUE;
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] >= 0) continue;
            int size = domains[v].cardinality();
            if (size == 0) return -1;
            if (size < minSize) {
                minSize = size;
                bestVar = v;
            }
        }
        return bestVar;
    }

    /**
     * Sort candidates for a variable:
     * Tier-1: fewest of THIS shift type → guarantees even per-type distribution
     * Tier-2: fewest total shifts → secondary tiebreak for overall balance
     *
     * This two-level sort prevents the CSP from repeatedly assigning the same
     * staff to L01 (or any other heavy type) simply because they have the
     * lowest total workload, while other staff accumulate only light shifts.
     */
    private List<Integer> getCandidates(BitSet domain, int[] staffWorkload,
                                        int[][] staffShiftWorkload, int shiftIdx,
                                        CspConstraints.MinWeekTracker weekTracker,
                                        int dayIdx, int dayShiftIdx) {
        List<Integer> list = new ArrayList<>();
        for (int s = domain.nextSetBit(0); s >= 0; s = domain.nextSetBit(s + 1)) {
            list.add(s);
        }
        final int week = dayIdx < 0 ? -1 : dayIdx / 7; // mirror CspConstraints.buildWeekBuckets
        list.sort(Comparator
                .comparingInt((Integer s) -> staffShiftWorkload[s][shiftIdx])  // per-type first
                .thenComparingInt(s -> staffWorkload[s])                        // total as tiebreak
                .thenComparingInt(s -> weekTracker == null
                        ? 0
                        : -weekTracker.underMinScore(s, shiftIdx, week)));      // Gap 1: under-min first
        return list;
    }

    private boolean isGoal(BitSet[] domains, int[] assignment, ProblemData data) {
        for (int v = 0; v < data.numVars; v++) {
            if (assignment[v] < 0) return false;
        }
        return true;
    }

    /**
     * Compensation day index for a given work-day index, or -1 if the
     * compensation day falls outside the active period.
     */
    int getCompensationDayIdx(int dayIdx, ProblemData data) {
        LocalDate workDate = data.baseDate.plusDays(dayIdx);
        LocalDate compDate = compensationDateCalculator.calculate(workDate);
        long offset = ChronoUnit.DAYS.between(data.baseDate, compDate);
        if (offset >= 0 && offset < data.numDays) return (int) offset;
        return -1;
    }

    private BitSet[] copyDomains(ProblemData data) {
        BitSet[] copy = new BitSet[data.numVars];
        for (int v = 0; v < data.numVars; v++) {
            copy[v] = new BitSet(data.numStaff);
            copy[v].or(data.domains[v]);
        }
        return copy;
    }

    @Builder
    @Getter
    static class Result {
        boolean valid;
        Map<String, Boolean> assignment;
        List<String> errors;
    }
}
